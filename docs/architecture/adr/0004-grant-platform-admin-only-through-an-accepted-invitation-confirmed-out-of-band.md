# ADR-0004 — Grant the platform-admin role only through an accepted invitation, confirmed with a code given out of band

- **Status:** Accepted
- **Date:** 2026-10-19
- **Deciders:** Víctor Cañizares (maintainer)
- **Applies to:** `lucoenergia/conluz` (platform-admin role, one-time tokens, invitation acceptance) and `lucoenergia/conluz-web` (acceptance screen, platform-admin page)

## Context

The platform-admin role is granted with `POST /api/v1/users/{userId}/grant-platform-admin`, which takes a user id. Today the platform admin finds that id on the users page, through `GET /api/v1/users`. #329 removes that page and closes the platform admin's access to users and to community rosters. Afterwards, the only user ids a platform admin sees are those of community admins (`GET /api/v1/communities/{communityId}/admins`, #395) and of other platform admins (`GET /api/v1/platform-admins`, #451). `/init` creates the first platform admin and works only once. A platform operator who administers no community therefore cannot be appointed from the app.

Community admins and members are never appointed directly. Under ADR-0003:
- the account is found or created silently by its personal ID;
- the person accepts an emailed, single-use link valid for 7 days, setting a first password if the account has none, or logging in if it has one;
- the access takes effect only on acceptance.

The pending state lives on the membership (`accepted_at`, `invited_by`, `invited_at`, the contact email of the record), and invitation tokens in `one_time_token` are bound to the membership (#391). Acceptance goes through `POST /api/v1/invitations/inspect`, `POST /api/v1/invitations/accept` and `POST /api/v1/users/current/invitations/accept` (#394), and the web client's single acceptance screen (conluz-web#274).

ADR-0003 accepts one risk: whoever first accepts an invitation for an account without a password takes that account over. For a membership the damage is bounded to one community. For the platform-admin role it is not: the role reaches every community's configuration and every admin. An invitation can reach the wrong person through a mistyped email, or a compromised mailbox. And the account it names may be a real person's, for example a member who was registered but never accepted.

## Decision

1. **Grant the role on acceptance only.** Inviting a person grants nothing. The role is granted in the transaction that accepts the invitation, and a pending invitation never counts as a platform admin, including for the last-platform-admin rule.

2. **Keep the pending state in a platform-admin invitation record.** The record holds:
    - the account;
    - the destination email;
    - the full name typed by the inviter, which the pending row of the list shows, since an existing account is never written;
    - who invited, and when;
    - when it was accepted;
    - the hash of the confirmation code;
    - the count of failed code attempts.

   At most one pending invitation exists per account. The record plays, for the platform, the part the membership plays for a community: it is the single source for the pending list, resend and revoke.

3. **Reuse one-time tokens for the link.** The link uses `one_time_token` and its code (hashing, expiry, single use, cleanup), with a new purpose, `PLATFORM_ADMIN_INVITATION`, valid for 7 days. The token is bound to the invitation record, as community tokens are bound to the membership. At most one active token exists per invitation. The lock order is account → invitation → token, as for memberships.

4. **Require a confirmation code given out of band.**
    - Inviting, and every resend, generates a short random code. The inviter sees it **once**, in that response, and passes it to the person by another channel (phone, in person).
    - The code is never sent by email, never logged, and only its hash is stored.
    - Accepting requires both the link's token and the code.
    - Each wrong code counts against the acceptance throttling (#394) and against the invitation. After 5 wrong codes the invitation's token is revoked, and the inviter has to resend.
    - Resending replaces both the token and the code.

   This decision only works together with decision 1. Without the code, acceptance proves only control of the mailbox. Without acceptance, the code would protect nothing, because the role would already be granted.

5. **Extend the existing acceptance flow.**
    - The inspection answers what the invitation is for (a community or the platform), and whether a confirmation code is required.
    - Both accept endpoints take the code when it is required.
    - The web client keeps one acceptance screen, with its existing cases (no password, password, another account's session, forced password change).

6. **Set the account email on acceptance only if the account has none,** as #394 does. An account email the person already chose is never overwritten.

7. **List pending invitations with the platform admins.** `GET /api/v1/platform-admins` shows pending invitations with their state (never invited is not possible here; link active until, link expired or revoked), as `GET …/admins` does. The list never shows the confirmation code.

## What the observable events now mean

- **A pending row in the platform-admin list** means the role has not been granted. That person cannot administer the platform until they accept.
- **"Invitation revoked" after wrong codes** means 5 wrong codes were tried with a valid link. It means either the person mistyped repeatedly, or someone holding the link tried to guess. Resending issues a new code; if the person did not try, treat the mailbox as compromised.
- **A disabled account accepting** gets the role and still cannot log in. Refusing it would reveal that the account exists and is disabled.

## Alternatives considered

**Grant the role on invitation.** It is the simplest option, and grant-by-id already does it. It was rejected because a mistyped identifier would hand platform-wide access to whatever account that identifier names, before anyone has proved who they are, and nothing in the system could notice.

**Columns on `users` for the pending state.** This needs no new table. It was rejected because the state belongs to a process, not to the person: it would mix an operator workflow into the account, and every account read would carry it.

**Tokens bound to the user, with no invitation record.** This adds no new table at all. It was rejected because a token stops counting as active once it expires or is revoked, so the pending list could not show lapsed invitations. It also leaves no clean place for the destination email, and it shares the user's token space with password recovery.

**Refuse to invite an existing account without a password.** This removes the takeover case. It was rejected because the refusal reveals to the inviter that the account exists and has no password, and it leaves a registered member with no path to becoming an operator.

**Mandatory two-factor authentication (TOTP) for platform admins.** It protects the account in every case, not only at acceptance. It was rejected as the way to close this risk because it is an epic of its own (enrolment, recovery, devices). It remains the natural next step; see "Revisit if".

**Send the code by SMS.** It would remove the manual step. It was rejected because the platform has no SMS infrastructure, and a phone number is not on every account.

**Separate acceptance endpoints for the platform.** They would leave the community contract untouched. They were rejected because the web client would need a second acceptance screen duplicating every case, and a second throttled public surface.

## Consequences

**Positive**
- A platform operator who administers no community can be appointed from the app, without the platform admin seeing any user list.
- Taking an account over through a platform-admin invitation requires controlling the destination mailbox **and** knowing a code that never travels by email.
- No response reveals whether the invited personal ID already had an account.
- The pending state, resend and revoke behave as they do for community admins, so the web client reuses its states and actions.

**Negative**
- Accepting needs a second channel: the inviter must deliver the code, and a lost code means a resend.
- The inspection response gains a kind, and its community becomes nullable for platform invitations. The web client must handle both.
- **Transitional exception:** `POST /api/v1/users/{userId}/grant-platform-admin` still grants the role directly, because the web client in production uses it and its replacement ships after this backend. It remains until the web client stops using it, and is then removed with the legacy endpoints (#329). Until then it bypasses acceptance, and a pending invitation for the same account stays in place, hidden from the list.
- **Obligation:** any future invitation to a role with platform-wide reach must require an out-of-band confirmation as well, or record why not in an ADR.
- **Obligation:** the confirmation code must never be logged, emailed, returned by any read endpoint, or stored in clear. It appears only in the response that generated it.
- **Obligation:** a pending invitation must never count as a platform admin, in the last-platform-admin rule or anywhere else.

**Operational**
- A new table and a new foreign key on `one_time_token`, through Liquibase.
- The invitation email must not include the code, and must tell the person that they will need the code from whoever invited them.

## Revisit if

- Two-factor authentication is introduced for platform admins: the confirmation code could then be replaced by enrolment during acceptance.
- The platform gains a verified second channel (for example SMS), which could deliver the code automatically.
- The web client no longer calls `POST /api/v1/users/{userId}/grant-platform-admin`: the transitional exception can then be removed.
- Platform-admin invitations become frequent enough that the manual delivery of codes is a real burden (for example, more than a few per month).

## References

- ADR-0002 (one-time tokens), ADR-0003 (accounts, member records and invitations)
- #391 (tokens bound to memberships), #394 (acceptance), #395 (community admin management), #451 (list of platform admins), #452 (this decision's implementation)
- conluz-web#274 (acceptance screen)
- `docs/specs/accounts.md`, `docs/specs/one-time-tokens.md`