# Spec — Accounts

| | |
| --- | --- |
| **Prefix** | `ACC` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tags `Authentication`, `Users`; operations `login`, `requestPasswordReset`, `resetPassword`, `acceptInvitation`, `getCurrentUser`, `updateProfile`, `createUser`, `createUsersWithFile`, `init`, `registerMember`, `inviteCommunityAdmin`, `importMembers`, `updateUser`, `enableUser`, `disableUser`, `deleteUser` |
| **Related ADRs** | ADR-0003 (`docs/architecture/adr/0003-separate-person-accounts-from-community-member-records.md`) |
| **Last reviewed against code** | #398 |

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
| Normalised personal ID | A personal ID with its whitespace, dots and hyphens removed and its letters upper-cased, the form accounts are stored and found by (#331). |

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
  ID, with or without a password, member registration MUST NOT write it. Member registration, which inviting a
  community admin uses (MEM-020), MUST be the only path that creates an account without a password.
- **Rationale:** The person sets their first password by accepting the invitation (ADR-0003 §6, §7). The member
  number belongs to each community's member record, not to the account (ADR-0003 §1). Changing an existing
  account would let one community overwrite another's view of the person, and would reveal that the account
  existed.
- **Source:** #392, #395, ADR-0003 §1, §3 and §7

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

### ACC-008 — A community admin cannot change another user's account

- **Status:** Active
- **Rule:** A caller who is not a platform admin MUST be refused with 403 when they edit (`updateUser`), enable
  (`enableUser`), disable (`disableUser`) or delete (`deleteUser`) the account of another user they can see,
  whatever their role in that user's communities, and nothing MUST be written. A user they cannot see MUST keep
  being answered 404. A community admin MAY still edit their own account through `updateUser`, and MUST still be
  refused enabling, disabling or deleting it. The platform admin's access to the four operations, and the refusal
  to disable or delete the last enabled platform admin, MUST NOT change. A member's data in a community is
  changed on their member record (#416); a member is suspended in a community by disabling their membership
  (MEM-027).
- **Rationale:** An account belongs to the person, and the same account can belong to several communities
  (ADR-0003 §1). Its email receives the password recovery link, so a community admin who could change it could
  take the account over, together with its access to every other community. Disabling or deleting it would
  remove the person from every community, not only the admin's.
- **Source:** #415, ADR-0003 §1

### ACC-009 — Deleting an account deletes its memberships, and is refused for a supply owner

- **Status:** Active
- **Rule:** Deleting a user (`deleteUser`) MUST, in one transaction, delete every membership of the user, in every
  community, with its member record and its tokens, and then the account with its remaining tokens (OTT-005). A
  user who owns a supply in any community, enabled or not, MUST be refused with 409 `USER_OWNS_SUPPLIES`, and
  nothing MUST be deleted. The refusal to delete the last enabled platform admin (409 `USER_LAST_PLATFORM_ADMIN`)
  and who may delete a user (ACC-008) MUST NOT change. Memberships the user invited MUST be kept, with no inviter
  (MEM-003); sharing agreements and distributor files the user created, edited or uploaded MUST be kept, with no
  creator, editor or uploader. The deletion MUST lock the user row before checking for supplies; supply creation
  locks the same row (SUP-002), so a supply created at the same time either refuses the deletion or finds the user
  gone. A deletion MUST never fail with an unhandled database error.
- **Rationale:** A membership cannot exist without its account, and since #388 every registered user has one, so
  deleting the account alone always failed on the membership's foreign key. A supply carries the community's
  energy data and its sharing agreements, so it is removed deliberately (SUP-001), never as a side effect of
  deleting its owner; a supply owner's access is withdrawn by disabling the account (`disableUser`). Who invited a
  member, or created, edited or uploaded an agreement's records, is a historical detail; the records themselves
  belong to the community and must outlive the person.
- **Source:** #412, ADR-0003 §1

### ACC-010 — Member registration, admin invitation and the member import accept only a valid DNI, NIE or NIF

- **Status:** Active
- **Rule:** Member registration (`registerMember`), inviting a community admin (`inviteCommunityAdmin`) and the
  member import (`importMembers`) MUST accept a personal ID only if, once normalised, it is one of:
  - a DNI: 8 digits and a control letter, the letter of the number modulo 23 in `TRWAGMYFPDXBNJZSQVHLCKE`;
  - an NIE: `X`, `Y` or `Z`, 7 digits and a control letter, computed as a DNI's with the prefix read as 0, 1 or 2;
  - a NIF of a natural person starting with `K`, `L` or `M`: the letter, 7 digits and a control letter, computed as a
    DNI's on the 7 digits;
  - a CIF: an organisation letter (`A`, `B`, `C`, `D`, `E`, `F`, `G`, `H`, `J`, `N`, `P`, `Q`, `R`, `S`, `U`, `V`,
    `W`), 7 digits and a control character, which MUST be the control digit for `A`, `B`, `E` and `H`, the control
    letter (`JABCDEFGHI` indexed by the digit) for `N`, `P`, `Q`, `R`, `S` and `W`, and MAY be either for `C`, `D`,
    `F`, `G`, `J`, `U` and `V`.

  The check MUST run on the normalised value, so any typing variant of a valid identifier is accepted, and before
  any community or account is looked up or anything is written. Any other personal ID MUST be refused with 400
  `USER_PERSONAL_ID_INVALID`; in the import, its row MUST fail with that code and the one error `INVALID_PERSONAL_ID`
  of the column `personalId` (MEM-030), and the other rows MUST be processed. The refusal MUST be the same whether
  or not an account holds that value, MUST NOT tell which rule the value breaks, and neither the answer nor any log
  line MUST hold the value unmasked. A personal ID made only of whitespace, dots and hyphens keeps its own refusal
  (MEM-007). The existing creation paths (`createUser`, `createUsersWithFile`, `init`) MUST NOT apply this check,
  and the personal IDs already stored MUST NOT be checked or changed.
- **Rationale:** Since #392, #395 and #397, community admins and the platform admin type other people's
  identifiers. A mistyped one creates a second account, which later duplicates the person when the right one is
  registered, and breaks Datadis, which needs the holder's correct NIF; the control character catches almost every
  single typing error. Checking only the normalised value keeps spaces, dots, hyphens and lower case harmless.
  Refusing before any lookup, with one answer for every rule, keeps the refusal from revealing whether an account
  exists. The existing creation paths are left as they are because they are being replaced, and checking stored
  identifiers would lock out accounts that already work.
- **Source:** #398. Accepting the NIF of a natural person with `K`, `L` or `M`, and the CIF letters that require a
  digit, require a letter or accept either, were decided by the maintainer in the plan of #398.

## Impossible states

- An account whose email is an empty or blank string — prevented by ACC-003 and by the migration that turned the
  existing ones into NULL.
- A membership whose account no longer exists — prevented by ACC-009, and by the foreign key
  `community_memberships_user_id_fk`, which has no `ON DELETE` rule: a deletion that skipped ACC-009 would fail
  rather than leave the membership behind.
- A supply whose owner's account no longer exists — prevented by ACC-009, and by the foreign key
  `supplies_user_id_fk`.
