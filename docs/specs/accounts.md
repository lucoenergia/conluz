# Spec — Accounts

| | |
| --- | --- |
| **Prefix** | `ACC` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tags `Authentication`, `Users`; operations `login`, `requestPasswordReset`, `resetPassword`, `acceptInvitation`, `getCurrentUser`, `updateProfile`, `createUser`, `createUsersWithFile`, `init`, `registerMember` |
| **Related ADRs** | ADR-0003 (`docs/architecture/adr/0003-separate-person-accounts-from-community-member-records.md`) |
| **Last reviewed against code** | #394 |

## Purpose

An account is a person's identity and credentials: the personal ID they log in with, their password and their
email address. This spec covers accounts without a password or without an email, how they are created, and how the
existing paths treat them; the rest of the capability is specified lazily, as issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Passwordless account | An account with no password: registered by a community and never accepted (ADR-0003 §7). |
| No email | An account without an email address, recorded as NULL. |
| Blank | Made only of space, tab, line feed, carriage return, form feed and vertical tab. |
| Existing creation path | Creating a user (`createUser`), importing users (`createUsersWithFile`) and creating the default admin (`init`). Each of them takes a password. |
| Member registration | A community admin registering a member by personal ID and member record (`registerMember`), which finds the account with that personal ID or creates one (ADR-0003 §3). |

## Rules

### ACC-001 — A passwordless account cannot log in, and its refusal looks like a wrong password

- **Status:** Active
- **Rule:** A login to a passwordless account MUST be refused with the same response as a wrong password, after
  one BCrypt comparison against a hash no password can match, and MUST count as a failure for bad credentials in
  the throttling of failed logins. No log line MUST tell it apart from a wrong password.
- **Rationale:** A faster answer, a different response or a distinct log line would reveal which personal IDs were
  registered by a community but never accepted. The hash is computed from a random secret so that no typed
  password, including a well-known one, can open the account.
- **Source:** #389, ADR-0003 §7

### ACC-002 — Password recovery refuses a passwordless account

- **Status:** Active
- **Rule:** A recovery request for a passwordless account MUST be answered exactly like any other recovery request,
  MUST NOT issue a token or send an email, and MUST be logged with the outcome `NO_PASSWORD`. The account is
  checked after the disabled check and before the email check, before any lock, count or token.
- **Rationale:** A passwordless account gets its first password by accepting an invitation; recovery would be a
  second way in that skips the acceptance.
- **Source:** #389, ADR-0003 §7

### ACC-003 — "No email" is NULL

- **Status:** Active
- **Rule:** An account without an email address MUST hold NULL, never an empty or blank string, and MUST be
  returned with a null email. The profile edit (`updateProfile`) MUST accept a missing, empty or blank email and
  record it as no email; any other value MUST be a valid email address. The existing creation paths MUST record an
  empty or blank email as no email, without changing which of them require one.
- **Rationale:** Email is optional for members (ADR-0003 §8). One spelling of "no email" spares every reader from
  checking two.
- **Source:** #389, ADR-0003 §8

### ACC-004 — Every account created through a creation path that takes a password has one

- **Status:** Active
- **Rule:** Creating a user (`createUser`), importing users (`createUsersWithFile`) and creating the default admin
  (`init`) MUST check the password of every account they create against the password policy, MUST refuse to create
  an account without a password or without a number, and MUST then store nothing.
- **Rationale:** These paths have always required a password and a number, and keep requiring them now that the
  database accepts accounts without either. Passwordless accounts without a number are created only by member
  registration (ACC-006), which takes no password at all.
- **Source:** #389, #392, ADR-0003 §7

### ACC-005 — Password reset refuses a passwordless account

- **Status:** Active
- **Rule:** A password reset (`resetPassword`) presenting a valid `PASSWORD_RESET` token of a passwordless account
  MUST be answered exactly like an unusable token (`USER_PASSWORD_RESET_TOKEN_INVALID`), and logged like one, before
  the new password is checked against the policy. It MUST NOT set a password or use up the token.
- **Rationale:** A passwordless account gets its first password only by accepting an invitation. ACC-002 already
  issues no token to one; the reset checks again on its own, so that no token that reaches such an account can
  become a second way to set the first password.
- **Source:** #392, #389, ADR-0003 §7

### ACC-006 — Member registration creates accounts without a password, and never writes an existing one

- **Status:** Active
- **Rule:** When no account has the personal ID entered, member registration MUST create one without a password,
  without a number, without having to change its password, with the member record's full name as its name and
  the record's contact email as its email (no email when the record has none). When an account has that personal
  ID, with or without a password, member registration MUST NOT write it. Member registration MUST be the only
  path that creates an account without a password.
- **Rationale:** The person sets their first password by accepting the invitation (ADR-0003 §6, §7). The member
  number belongs to each community's member record, not to the account (ADR-0003 §1). Changing an existing
  account would let one community overwrite another's view of the person, and would reveal that the account
  existed.
- **Source:** #392, ADR-0003 §1, §3 and §7

### ACC-007 — An account's first password is set only by accepting an invitation

- **Status:** Active
- **Rule:** The only way to give a passwordless account a password MUST be accepting one of its invitations
  without logging in (`acceptInvitation`, MEM-016). Accepting MUST also set the account's email to the accepted
  member record's contact email when the account has none, and MUST NOT change an email the account already has.
- **Rationale:** Login, recovery and reset refuse a passwordless account (ACC-001, ACC-002, ACC-005); the
  invitation link, sent to the member record's contact email, is what proves the person (ADR-0003 §6, §7). Without
  an email the person could not recover the password they just chose. Whoever accepts first takes the account
  over, an accepted risk recorded in ADR-0003.
- **Source:** #394, ADR-0003 §6 and §7

## Impossible states

- An account whose email is an empty or blank string — prevented by ACC-003 and by the migration that turned the
  existing ones into NULL.
