# ADR-0002 — Recover passwords with single-use hashed tokens, emailed after commit

- **Status:** Accepted
- **Date:** 2026-10-07
- **Deciders:** Víctor Cañizares
- **Applies to:** `lucoenergia/conluz`: password recovery (`RequestPasswordResetServiceImpl`, `ResetPasswordServiceImpl`), one-time account tokens (`domain/admin/user/token`), transactional email (`domain/shared/email`, `infrastructure/shared/email`)

## Context

Until #360–#362, a member who forgot their password was locked out for good:
- no endpoint sent a recovery email or accepted a reset;
- no admin-initiated reset existed;
- the backend could not send email at all (`spring-boot-starter-mail` was declared but unused).

Forced password changes (#342) made forgetting more consequential.

The constraints that shaped the design:
- **Members are rural, often older, and read email rarely.** A recovery link that expires in an hour would often be dead by the time it is opened.
- **A recovery endpoint is an account-enumeration oracle by default.** Its answer, or its response time, can reveal whether a `personalId` is registered, has an email, or is enabled. The `personalId` is a NIF, which is semi-public.
- **Whoever holds a valid recovery token can take over the account.** Tokens therefore must not appear in server logs, proxy access logs or the database in usable form.
- **Conluz is open source and deployed by others.** A safe configuration must not depend on rules outside this repository.
- **The deployment is a single backend instance.** In-memory state is lost on restart. #332's throttling counters already accept this.
- **Two clocks coexist.** JWT `iat` and `password_changed_at` come from the JVM clock (`Instant.now()`), while throttling uses an injected `Clock` that tests replace with a `MutableClock`. Comparing values from the two sources breaks silently (#355).
- **The provider for the MVP is Gmail SMTP,** which limits sending to about 500 emails a day.

## Decision

### 1. Transactional email goes through a port, after commit, off the request thread

- A domain port (`EmailSender`) sends one plain-text UTF-8 email. Its SMTP adapter is configured by `CONLUZ_MAIL_*` variables.
- **Sending is disabled by default.** An enabled but misconfigured setup still starts, and warns once.
- **Connection hardening:**
    - STARTTLS is required, and `mail.smtp.ssl.checkserveridentity=true` is set explicitly;
    - connect, read and write timeouts are 5 s each;
    - JavaMail debug output is off;
    - the mail health indicator is disabled, so an unreachable SMTP server never marks the application DOWN.
- **An email requested inside a transaction is handed to a private, bounded executor only after the transaction commits.** The executor has 2 threads and a queue of 100, and is deliberately not a Spring bean.
- **Failures never reach the request.** A failed or dropped email is logged as one WARN line with a category label and the exception type, never the recipient, subject or body. There is no retry and no persistent queue.

**After commit and asynchronous only work together.** After commit alone would keep the SMTP round trip inside the HTTP response, and the response time of the recovery request would then reveal whether an email was sent. Asynchronous alone would send emails for transactions that roll back.

### 2. One-time tokens are random, hashed and single-use

- **Generation:** at least 256 bits from `SecureRandom`, encoded URL-safe Base64 without padding. Only the SHA-256 hash is stored, with a unique constraint, and every lookup is by hash. The raw token is returned once, and its value type redacts itself in `toString`.
- **Purposes** form a closed set, each with its own lifetime. `PASSWORD_RESET` lasts **1 day**.
- **One active token per user and purpose.** Issuing revokes every earlier unused token, under a lock on the user row, with a partial unique index as a database backstop.
- **Consumption** is one conditional update, so two concurrent consumptions cannot both succeed. Every invalid case gives the same empty result.
- **Finished tokens** (expired, used or revoked) are deleted after a **7-day retention**. Counting refuses any window older than that, so counts stay exact.
- **All token times come from the injected `Clock`.** They are never compared with JWT `iat`.

### 3. The user row is locked before that user's token rows

Every flow that touches both a user and their tokens locks the user row first (`FOR NO KEY UPDATE`). A flow that consumes a token and then writes the user must:
1. find the token's owner without locking (`findOwner`);
2. lock that user's row;
3. consume, and continue only if the result equals the owner.

The opposite order deadlocks against a concurrent issue for the same user, which the tests reproduce.

### 4. A recovery request never reveals the account

- Recovery is requested by `personalId`, normalised as everywhere else. The email goes to the address stored for that user.
- **Every outcome answers the same 202 with an empty body:** unknown user, disabled user, no email, over the daily limit, unconfigured URL, sending disabled or failing.
- **At most 3 recovery emails per user per day.** The limit is counted from the token store in the database, so it survives restarts. It is counted **after** locking the user row, so concurrent requests cannot exceed it.
- Every request also counts against #332's per-IP throttle, shared with login and password change.
- If the public web URL is not configured, no token is issued, so an earlier link keeps working and the daily quota is not spent.

### 5. The token travels in the URL fragment

The emailed link is `<CONLUZ_PUBLIC_WEB_URL>/reset-password#<token>`. Browsers never send the fragment to any server, so the token cannot appear in Caddy's access log or in a `Referer` header. The web client reads it from the fragment, removes it from the address bar, and posts it in a request body, which is never logged.

### 6. A reset changes the password and ends every session, without logging in

- The reset runs in one transaction, following §3.
- **The password policy and the current-password reuse check run before the token is consumed,** so a rejected password does not use up the link.
- On success:
    - the password is stored and `must_change_password` is cleared;
    - `password_changed_at` is set from the **JVM clock** (`Instant.now()`), because it is compared with JWT `iat`, which invalidates every earlier session;
    - the account's failed-attempt counter is reset.
- **The user is not logged in.** The response is 204 with no body and no cookie.
- Every unusable token (unknown, malformed, expired, used, revoked, a disabled user's, or a consume mismatch) answers the same 400 `USER_PASSWORD_RESET_TOKEN_INVALID`, and counts against the per-IP throttle.

### 7. Emails are plain text, in Spanish, informal register

No HTML and no template engine. Subjects and bodies come from `messages_es.properties`, in the informal register ("tú"), matching the web client.

## What the recovery outcomes mean

These are the facts someone needs when a member says "I did not get the email":

- **The 202 proves nothing about delivery.** It is the answer for every outcome.
- **The INFO line `Password reset requested: account=***XYZ, ip=…, outcome=…` says what happened:**
    - `SENT` means the email was handed to the sender, not that it was delivered.
    - `NO_EMAIL`: the user has no email (today, an empty value).
    - `UNKNOWN`: no user has that `personalId`, after normalisation.
    - `DISABLED`: the user is disabled.
    - `OVER_LIMIT`: 3 emails were already sent in the last 24 hours.
    - `FAILED`: the public web URL is not configured.
- **A delivery failure after `SENT`** appears separately, as one WARN line from the email sender: `Email PASSWORD_RESET not sent: <ExceptionType>`. Without one, the email left the server: check the member's spam folder.
- **`USER_PASSWORD_RESET_TOKEN_INVALID` does not say why.** The link may have expired (after 1 day), been used, been replaced by a newer request, belong to a user disabled since, or have been copied incompletely. In every case, the member requests a new one.
- **A member who clicks an old link after requesting a new one** gets the invalid-token error. This is expected: only the newest link works.

## Alternatives considered

**Put the token in the URL path (`/forgot-password/<token>`).** This is the route the web client had. It was rejected because the browser requests the full path from the server, so Caddy's access log, and every backup of it, would hold usable tokens.

**Store raw tokens, or use signed stateless tokens (a JWT) for resets.** Raw tokens in the database would let anyone with read access take over accounts. A signed stateless token needs no table, but it cannot be single-use, cannot be revoked by a newer request, and cannot be counted for the daily limit without extra state, which would bring back the table anyway.

**A shorter lifetime (1 hour, as many services use).** It narrows the window for a forwarded or exposed email. It was rejected because members often open email hours later. The longer lifetime is mitigated by single use and by revocation on every new request.

**Request recovery by email address instead of `personalId`.** Email is not unique and is optional for members, while `personalId` is the login identifier members already know. Asking for the email would also let anyone probe which addresses are registered.

**Keep the daily limit in memory, like #332's counters.** It would be simpler, but a restart would reset it, and so would every deployment. Counting from the token store costs one indexed query and needs no new state.

**`@TransactionalEventListener` with `@Async`.** This is the conventional Spring approach. It was rejected because it needs `@EnableAsync` application-wide and an executor bean, which makes Spring Boot drop its own `applicationTaskExecutor`. A full queue would also throw from the after-commit phase into a caller whose transaction has already committed.

**A persistent queue with retries.** It would survive crashes and transient SMTP failures. It was rejected for the MVP: a member whose email is lost simply requests another, and a persistent queue means more state, more code and its own failure modes.

**HTML emails with a template engine.** They would look better. They were rejected for the MVP because plain text is simpler, renders everywhere, and is less likely to be filtered as spam.

**Log the user in after a reset.** It saves one step. It was rejected because the reset endpoint is public: issuing a session there would turn a leaked link directly into a session, bypassing #332's login throttling. Sending the user to the login page keeps one way in.

**An admin-initiated reset for members without email.** It would cover members recovery cannot reach. It was rejected for now. It adds a privileged path that the epic is removing from the platform admin, and members without email are an accepted gap.

## Consequences

**Positive**

- Members can recover their own password, on their own schedule, without an administrator.
- No usable token exists in the database, the logs, the proxy logs or a `Referer` header.
- The recovery request reveals nothing about the account in its response, and nearly nothing in its timing.
- The email and token infrastructure is reusable: Phase 2 invitations add a purpose and a lifetime, nothing more.
- A recovery invalidates every earlier session and clears forced changes, so it doubles as a "log me out everywhere" for a member who suspects their account was used.

**Negative**

- Members without email cannot recover their password.
- An email lost after `SENT` (SMTP failure, crash or shutdown with a non-empty queue) is not retried.
- A link stays valid for a day: a forwarded or exposed email gives an attacker that long, unless the member requests another.
- The request endpoint's timing is not perfectly uniform: an eligible user costs a lock, a count and an insert that other cases do not (accepted).
- **Obligation:** every flow that touches a user and their tokens locks the user row first. A flow that consumes a token before locking the user can deadlock.
- **Obligation:** `password_changed_at` (and any other value compared with JWT `iat`) is written from the JVM clock, while token times use the injected `Clock`, until #355 unifies them. Never mix the two in one comparison.
- **Obligation:** no email body, subject, recipient, raw token or token hash is ever logged. Email bodies carry tokens.
- **Obligation:** a new email type gets a category label and a plain-text body in `messages_es.properties`, in the informal register.
- **Obligation:** a new public flow that can reveal whether an account exists answers identically in every case and counts against the per-IP throttle.

**Operational**

- **Release order:**
    1. backend and infrastructure, with `CONLUZ_MAIL_ENABLED=false`;
    2. the web client's forgot-password and reset-password screens;
    3. `CONLUZ_MAIL_ENABLED=true`.

  Enabling sending earlier emails links to a screen that does not exist.
- `CONLUZ_PUBLIC_WEB_URL` must be the public address of the web client, with `https` and no trailing slash. Without it, no recovery email is sent.
- Gmail's limit of about 500 emails a day is not tracked by the application.
- On shutdown, queued emails get up to 20 s to be sent; anything left is lost.

## Revisit if

- Sending volume approaches Gmail's daily limit, or deliverability problems appear (emails landing in spam). Consider a transactional email provider and a custom sending domain.
- Phase 2 makes `users.email` nullable. The "no email" check must then treat empty and null alike.
- A second backend instance is deployed: the in-memory email queue and #332's counters are per instance.
- Members without email become a significant share of a community's members. Reconsider an admin-initiated reset.
- An email is reported lost often enough to matter. Reconsider a persistent queue with retries.
- #355 unifies the clocks. The JVM-clock obligation above then ends.

## References

- `lucoenergia/conluz`:
    - #360 (email), #361 (one-time tokens), #362 (password recovery); #330, #331, #332, #342, #347 (building blocks); #355 (single clock).
    - `domain/shared/email/` (`EmailSender`, `Email`, `EmailCategory`) and `infrastructure/shared/email/` (`AfterCommitEmailSender`, `EmailConfiguration`, `EmailProperties`).
    - `domain/admin/user/token/` (`CreateOneTimeTokenService`, `GetOneTimeTokenService`, `ConsumeOneTimeTokenService`, `OneTimeTokenPurpose`, `OneTimeTokenRetention`) and `infrastructure/admin/user/token/`.
    - `RequestPasswordResetServiceImpl`, `ResetPasswordServiceImpl`, `PasswordResetEmailFactory`, `LockUserRepository`.
    - Changeset `create_one_time_token_table`.
    - `docs/security/authentication-throttling.md` (the password recovery section).
- `lucoenergia/conluz-web`: the forgot-password and reset-password screens (`/forgot-password`, `/reset-password`).
- `lucoenergia/conluz-infra`: `CONLUZ_MAIL_*` and `CONLUZ_PUBLIC_WEB_URL`.