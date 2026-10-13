# Spec — Accounts

| | |
| --- | --- |
| **Prefix** | `ACC` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tags `Authentication`, `Users`; operations `login`, `requestPasswordReset`, `getCurrentUser`, `updateProfile`, `createUser`, `createUsersWithFile`, `init` |
| **Related ADRs** | ADR-0003 (`docs/architecture/adr/0003-separate-person-accounts-from-community-member-records.md`) |
| **Last reviewed against code** | #389 |

## Purpose

An account is a person's identity and credentials: the personal ID they log in with, their password and their
email address. This spec covers accounts without a password or without an email, and how the existing paths treat
them; the rest of the capability is specified lazily, as issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Passwordless account | An account with no password: registered by a community and never accepted (ADR-0003 §7). |
| No email | An account without an email address, recorded as NULL. |
| Blank | Made only of space, tab, line feed, carriage return, form feed and vertical tab. |
| Existing creation path | Creating a user (`createUser`), importing users (`createUsersWithFile`) and creating the default admin (`init`). |

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

### ACC-004 — Every account created through an existing creation path has a password

- **Status:** Active
- **Rule:** An existing creation path MUST refuse to create an account without a password, through the password
  policy, and MUST store nothing.
- **Rationale:** Passwordless accounts are created only by registration through an invitation; the existing paths
  keep requiring the password they always did.
- **Source:** #389, ADR-0003 §7

## Impossible states

- An account whose email is an empty or blank string — prevented by ACC-003 and by the migration that turned the
  existing ones into NULL.
