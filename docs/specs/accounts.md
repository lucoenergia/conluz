# Spec — Accounts

| | |
| --- | --- |
| **Prefix** | `ACC` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tags `Authentication`, `Users`; operations `login`, `requestPasswordReset`, `resetPassword`, `acceptInvitation`, `getCurrentUser`, `updateProfile`, `init`, `registerMember`, `inviteCommunityAdmin`, `importMembers`, `enableUser`, `disableUser`, `deleteUser`, `getAllUsers`, `getUserById`, `getSuppliesByUserId`, `changePassword`, `getPlatformAdmins`, `revokePlatformAdmin`, `invitePlatformAdmin`, `resendPlatformAdminInvitation`, `revokePlatformAdminInvitation`, `inspectInvitation`, `acceptInvitationAsCurrentUser` |
| **Related ADRs** | ADR-0003 (`docs/architecture/adr/0003-separate-person-accounts-from-community-member-records.md`), ADR-0004 (`docs/architecture/adr/0004-grant-platform-admin-only-through-an-accepted-invitation-confirmed-out-of-band.md`) |
| **Last reviewed against code** | #447 |

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
| Existing creation path | Creating the default admin (`init`), the one creation path that takes a password. Creating a user (`createUser`) and importing users (`createUsersWithFile`) took one too, until #447 removed them. |
| Member registration | A community admin registering a member by personal ID and member record (`registerMember`), which finds the account with that personal ID or creates one (ADR-0003 §3). |
| Platform-admin invitation | An invitation to the platform-admin role (ADR-0004 §2): the account, the destination email and the full name the inviter typed, who invited and when, when it was accepted, the hash of its confirmation code and its count of failed code attempts. Pending until accepted. |
| Confirmation code | The 8-character code a platform-admin invitation requires on acceptance, which the inviter gives the person by another channel (ADR-0004 §4). |
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
  record it as no email; any other value MUST be a valid email address. The existing creation path (`init`) MUST
  record an empty or blank email as no email.
- **Rationale:** Email is optional for members (ADR-0003 §8). One spelling of "no email" spares every reader from
  checking two.
- **Source:** #389, ADR-0003 §8. #447 removed the other existing creation paths, `createUser` and
  `createUsersWithFile`.

### ACC-004 — Only the default admin is created with a password, and a password is then set only by its owner

- **Status:** Active
- **Rule:** Creating the default admin (`init`) MUST check the password of the account it creates against the
  password policy, MUST refuse to create an account without a password or without a number, and MUST then store
  nothing. It MUST be the only path that creates an account with a password. Afterwards, an account's password MUST be
  written only by changing it (`changePassword`), by resetting it (`resetPassword`), or by accepting an invitation
  (ACC-007); no path MUST set a password chosen by someone else.
- **Rationale:** The default admin types their own password into `init`; everyone else is registered or invited
  without one and chooses it on acceptance (ACC-006, ACC-007). A password chosen by whoever created the account is
  known to two people, which is what creating and importing users with a password did. `init` has always required a
  password and a number, and keeps requiring them now that the database accepts accounts without either.
- **Source:** #389, #392, ADR-0003 §7. #447 removed creating a user (`createUser`) and importing users
  (`createUsersWithFile`), the two paths that set a password chosen by someone else, and left `init` as the only
  creation path with a password.

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
  community admin uses (MEM-020), and inviting a platform admin (ACC-017), which creates the account the same way
  with the full name and email the inviter typed, MUST be the only paths that create an account without a
  password.
- **Rationale:** The person sets their first password by accepting the invitation (ADR-0003 §6, §7). The member
  number belongs to each community's member record, not to the account (ADR-0003 §1). Changing an existing
  account would let one community overwrite another's view of the person, and would reveal that the account
  existed.
- **Source:** #392, #395, ADR-0003 §1, §3 and §7. #452 added inviting a platform admin, through the same find or
  create step, ADR-0004 §2.

### ACC-007 — An account's first password is set only by accepting an invitation

- **Status:** Active
- **Rule:** The only way to give a passwordless account a password MUST be accepting one of its invitations
  without logging in (`acceptInvitation`, MEM-016), a community invitation or a platform-admin invitation. Accepting
  MUST also set the account's email, when the account has none, to the accepted member record's contact email, or
  to a platform-admin invitation's destination email, and MUST NOT change an email the account already has.
- **Rationale:** Login, recovery and reset refuse a passwordless account (ACC-001, ACC-002, ACC-005); the
  invitation link, sent to the member record's contact email, is what proves the person (ADR-0003 §6, §7). Without
  an email the person could not recover the password they just chose. Whoever accepts first takes the account
  over, an accepted risk recorded in ADR-0003.
- **Source:** #394, ADR-0003 §6 and §7. #452 added platform-admin invitations, ADR-0004 §6.

### ACC-008 — Nobody can change another user's account

- **Status:** Active
- **Rule:** Every caller, a platform admin included, MUST be refused with 403 when they enable (`enableUser`),
  disable (`disableUser`) or delete (`deleteUser`) the account of another user they can see, whatever their role in
  that user's communities, and nothing MUST be written. A user they cannot see MUST keep being answered 404. A caller
  MUST also be refused enabling, disabling or deleting their own account. The three operations on another user's
  account have no caller left (ACC-020). No operation MUST change an account's name, personal ID or number once it is
  created: a person changes only their own contact details (`updateProfile`), and a community changes its member
  record (#416). Disabling a platform admin MUST be refused with 409 `USER_LAST_PLATFORM_ADMIN` exactly when ACC-013's
  last-platform-admin rule would refuse revoking their role: only an **enabled** platform admin while no more than
  one platform admin is enabled. A disabled platform admin MAY be disabled again whatever the number of enabled
  platform admins. A member's data in a community is
  changed on their member record (#416); a member is suspended in a community by disabling their membership
  (MEM-027).
- **Rationale:** An account belongs to the person, and the same account can belong to several communities
  (ADR-0003 §1). Its email receives the password recovery link, so a community admin who could change it could
  take the account over, together with its access to every other community. Disabling or deleting it would
  remove the person from every community, not only the admin's.
- **Source:** #415, ADR-0003 §1. #452 applied ACC-013's last-platform-admin rule, which until then refused any
  platform admin while at most one was enabled, whoever the target was. #446 withdrew the platform admin's access to
  the four operations (ACC-020); until then it was the only caller allowed them. #447 removed editing a user
  (`updateUser`), which replaced the name, personal ID and number, and which a community admin could still use on
  their own account.

### ACC-009 — Deleting an account deletes its memberships, and is refused for a supply owner

- **Status:** Active
- **Rule:** Deleting a user (`deleteUser`) MUST, in one transaction, delete every membership of the user, in every
  community, with its member record and its tokens, and then the account with its remaining tokens (OTT-005). A
  user who owns a supply in any community, enabled or not, MUST be refused with 409 `USER_OWNS_SUPPLIES`, and
  nothing MUST be deleted. Deleting a platform admin MUST be refused with 409 `USER_LAST_PLATFORM_ADMIN`, and nothing
  deleted, exactly when ACC-013's last-platform-admin rule would refuse revoking their role: only an **enabled**
  platform admin while no more than one platform admin is enabled; a disabled platform admin MAY be deleted. Who may
  delete a user is ACC-008's: since #446, nobody over the API, so these rules apply to the deletion itself, wherever
  it is started from. Memberships the user invited MUST be kept, with no inviter
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
- **Source:** #412, ADR-0003 §1. #452 applied ACC-013's last-platform-admin rule, which until then refused any
  platform admin while at most one was enabled, whoever the target was.

### ACC-010 — Member registration, admin invitations and the member import accept only a valid DNI, NIE or NIF

- **Status:** Active
- **Rule:** Member registration (`registerMember`), inviting a community admin (`inviteCommunityAdmin`), inviting a
  platform admin (`invitePlatformAdmin`) and the member import (`importMembers`) MUST accept a personal ID only if,
  once normalised, it is one of:
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
  (MEM-007). The existing creation path (`init`) MUST NOT apply this check, and the personal IDs already stored MUST
  NOT be checked or changed.
- **Rationale:** Since #392, #395 and #397, community admins and the platform admin type other people's
  identifiers. A mistyped one creates a second account, which later duplicates the person when the right one is
  registered, and breaks Datadis, which needs the holder's correct NIF; the control character catches almost every
  single typing error. Checking only the normalised value keeps spaces, dots, hyphens and lower case harmless.
  Refusing before any lookup, with one answer for every rule, keeps the refusal from revealing whether an account
  exists. `init` was left as it was while the other existing creation paths were being replaced, and checking stored
  identifiers would lock out accounts that already work.
- **Source:** #398. Accepting the NIF of a natural person with `K`, `L` or `M`, and the CIF letters that require a
  digit, require a letter or accept either, were decided by the maintainer in the plan of #398. #452 added
  `invitePlatformAdmin`. #447 removed the existing creation paths `createUser` and `createUsersWithFile`.

### ACC-011 — Only a platform admin may list the platform admins and the pending invitations to the role

- **Status:** Active
- **Rule:** Listing the platform admins (`getPlatformAdmins`) MUST be allowed only to a platform admin; any other
  caller MUST be refused with 403, and an anonymous one with 401. It MUST list every account holding the
  platform-admin role, enabled or not, by full name and then id, followed by every pending platform-admin invitation
  whose account does not hold the role, by the full name the inviter typed and then id, and MUST cost the same
  number of statements whatever the number of rows. Each row MUST carry exactly `user` (the account's id), `fullName`,
  `email`, `enabled`, `revocable`, `invitation` and `capabilities` (`canRevoke`, `canResendInvitation`,
  `canRevokeInvitation`); it MUST NOT carry the personal ID, anything from a member record, any membership, or a
  confirmation code.
  - On a platform admin's row, `fullName`, `email` (NULL when it has none) and `enabled` MUST be the account's, and
    `invitation` MUST be NULL. `revocable` MUST be false exactly when ACC-013's last-platform-admin rule would refuse
    revoking that row, that is only for an enabled platform admin while no more than one platform admin is enabled;
    a disabled platform admin's row MUST be revocable.
  - On a pending invitation's row, `fullName` and `email` MUST be the ones the inviter typed, `enabled` and
    `revocable` MUST be NULL, and `invitation` MUST hold when it was sent and when its link expires, NULL when it
    has no valid link: expired, or revoked. Nothing on it MUST come from the account, so that it is the same whether
    or not the account existed (ACC-017).
  - `capabilities.canRevoke` MUST be decided by the same policy as the guard of `revokePlatformAdmin`, so it is false
    on the caller's own row. `canResendInvitation` and `canRevokeInvitation` MUST be decided by the same policy as
    the guard of the invitation endpoints (ACC-018, ACC-019), on every row: they say whether the caller may ask, not
    whether the row has an invitation.
- **Rationale:** The users page is being removed from the web client and the platform admin's access to the users
  listing is being closed (#329), which would leave nobody able to see who the platform admins are or find the one
  to revoke. Platform admins are identified by their own account, not by any community's record, so only the account
  is shown, and only what identifies it. Whether the caller may revoke is authorization and whether the revoke would
  be accepted is state; reporting both, from the rules the revoke applies, keeps the client from re-deriving either.
- **Source:** #451. Reporting the last-platform-admin rule as the row's `revocable`, apart from
  `capabilities.canRevoke`, was decided by the maintainer in the plan of #451. #456 made `revocable` per row, with
  ACC-013. #452 added the pending invitations (ADR-0004 §7); making `enabled` and `revocable` NULL on their rows was
  decided by the maintainer in the plan of #452.

### ACC-012 — Only a platform admin may grant the platform-admin role

- **Status:** Removed in #447
- **Rule:** Granting the platform-admin role (`grantPlatformAdmin`) MUST be allowed only to a platform admin; any
  other caller MUST be refused with 403, and an anonymous one with 401. An unknown user MUST be answered 404. Granting
  the role to a user who already holds it MUST succeed and change nothing.
- **Rationale:** Granting the role by identifier was ADR-0004's transitional exception, kept only while the web
  client still used it. The web client no longer calls it (conluz-web#285, conluz-web#288), so the operation is
  removed and the role is granted only by accepting a platform-admin invitation (ACC-014).
- **Source:** #447, ADR-0004

### ACC-013 — Revoking the platform-admin role checks the target and keeps an enabled platform admin

- **Status:** Active
- **Rule:** Revoking the platform-admin role (`revokePlatformAdmin`) MUST be allowed only to a platform admin, and
  never on their own account; any other caller MUST be refused with 403, and an anonymous one with 401. The checks
  MUST run in this order, after authorization:
  1. an unknown user MUST be answered 404;
  2. a user who does not hold the role MUST be answered 409 `USER_NOT_PLATFORM_ADMIN`, and nothing MUST change;
  3. revoking the role from an **enabled** platform admin while no more than one platform admin is enabled MUST be
     answered 409 `USER_LAST_PLATFORM_ADMIN`. Revoking the role from a **disabled** platform admin MUST be allowed
     whatever the number of enabled platform admins, since it leaves that number as it is.

  Otherwise the role MUST be revoked. Because a disabled account cannot authenticate and the caller may not revoke
  themselves, the third check can only refuse a revoke that runs at the same time as another one; it MUST still be
  applied.
- **Rationale:** Revoking a role nobody holds is a mistake the caller should hear about rather than a silent success,
  and an unknown user is a 404 regardless of how many platform admins there are. The last-platform-admin rule exists
  to keep someone able to administer the platform: only enabled platform admins can, so only they count, and a
  disabled platform admin must not be impossible to clean up just because one other admin is enabled.
- **Source:** #456. The target checks and the exception for disabled platform admins were decided by the
  maintainer; until then revoke refused whenever at most one platform admin was enabled, whoever the target was.
### ACC-014 — The platform-admin role is granted only by accepting an invitation with its confirmation code

- **Status:** Active
- **Rule:** Inviting a person (`invitePlatformAdmin`) MUST NOT grant the role. Accepting a platform-admin invitation
  (`acceptInvitation`, `acceptInvitationAsCurrentUser`) with its valid link and its confirmation code MUST, in one
  transaction under the lock order user row, invitation row, token rows: use up the token, record the acceptance
  instant, grant the role, set the account's email to the invitation's destination email when the account has none,
  and, on `acceptInvitation`, set the first password as MEM-016 does. A disabled account MUST be able to accept: it
  is granted the role and still cannot log in. A pending invitation MUST NOT count as a platform admin: not in
  `revocable`, not in the last-platform-admin rule (ACC-013), nor anywhere else; every count reads only the role. The
  role MUST be held only by the default admin created by `init` and by people who accepted a platform-admin
  invitation with its confirmation code: no operation grants it by identifier.
- **Rationale:** Granting by typing an identifier would hand platform-wide access to whatever account it names,
  including a mistyped one; acceptance proves the person, and the code proves more than control of the mailbox
  (ADR-0004 §1, §4). Refusing a disabled account would reveal that it exists and is disabled. A role not yet
  accepted must not keep the platform administrable on paper only.
- **Source:** #452, ADR-0004 §1, §4, §5 and "What the observable events now mean". #447 removed granting the role
  by identifier (ACC-012), ADR-0004's transitional exception, so that AC15 of #329 holds.

### ACC-015 — The confirmation code is shown only in the response that generated it

- **Status:** Active
- **Rule:** Inviting and resending MUST each generate a new confirmation code of 8 characters from the Crockford
  base32 alphabet (`0`-`9`, `A`-`Z` without `I`, `L`, `O` and `U`), drawn from a cryptographic random source, and
  MUST return it only in that response. The code MUST NOT be emailed, logged, stored in clear, returned by any read
  endpoint (`getPlatformAdmins`, `inspectInvitation`) or put in any exception message; only its hash MUST be stored,
  and a typed code MUST be compared with it in constant time.
- **Rationale:** The code protects only while it travels by another channel than the link (ADR-0004 §4). An
  unambiguous alphabet lets it be read out over the phone.
- **Source:** #452, ADR-0004 §4

### ACC-016 — A missing or wrong code is counted, and the fifth one revokes the link

- **Status:** Active
- **Rule:** On either accept endpoint, a platform-admin invitation's code is checked after the checks of MEM-016 and
  MEM-017, and is read in any case and without spaces or hyphens. A missing or wrong code MUST be answered 400
  `PLATFORM_ADMIN_INVITATION_CODE_INVALID`, MUST grant nothing and change nothing but the invitation's count of
  failed code attempts, which MUST be incremented and persisted although the request answers an error, and MUST
  count as one failure against the client address (MEM-015), logged with the client address only. Concurrent wrong
  codes MUST each be counted. The fifth one on the same invitation MUST revoke its link and be answered 400
  `PLATFORM_ADMIN_INVITATION_CODE_ATTEMPTS_EXHAUSTED`; from then on the link MUST be refused like any unusable token
  (MEM-015). Resending resets the count (ACC-019).
- **Rationale:** Five tries let a person mistype; more would let someone holding the link guess the code. A count
  that rolled back with the error would never reach five (ADR-0004 §4).
- **Source:** #452, ADR-0004 §4

### ACC-017 — Inviting a personal ID with an account answers and logs as for a new one, and never writes it

- **Status:** Active
- **Rule:** Inviting a platform admin MUST find the account with the personal ID, or create one without a password
  with the full name and email the inviter typed (ACC-006). An existing account, with or without a password, MUST NOT
  be written. Apart from the user id and the code, the response, and the log lines, MUST be the same whether or not
  the account existed: the invitation's row carries what the inviter typed and nothing from the account (ACC-011).
- **Rationale:** No answer may tell the inviter that an account exists, nor overwrite a name or email a person
  chose (ADR-0003, ADR-0004).
- **Source:** #452, ADR-0003 §3, ADR-0004 §2

### ACC-018 — Only a platform admin may invite a platform admin, and the refusals come in order

- **Status:** Active
- **Rule:** Inviting a platform admin (`invitePlatformAdmin`) MUST be allowed only to a platform admin; any other
  caller MUST be refused with 403, and an anonymous one with 401. The personal ID, the full name and the email are
  required. The checks MUST run in this order:
  1. a server that cannot send invitations MUST answer 503 `PLATFORM_ADMIN_INVITATION_UNAVAILABLE` before anything is
     looked up or written;
  2. an invalid personal ID MUST be refused as ACC-010 says;
  3. the account is found or created (ACC-017) and locked;
  4. an account holding the role MUST be answered 409 `USER_ALREADY_PLATFORM_ADMIN`;
  5. an account with a pending invitation MUST be answered 409 `PLATFORM_ADMIN_ALREADY_INVITED`.

  Otherwise it MUST create a pending invitation, issue its link (OTT-011) and its code (ACC-015), answer the
  invitation's row as the list shows it with the code, and email the link to the destination email once the
  transaction commits. The email is in Spanish, tells the person that they will need the code from whoever invited
  them, and MUST NOT contain the code.
- **Rationale:** A platform-wide role is offered only by someone who holds it. Refusing before anything is written
  keeps an invitation from being recorded without being sent. One pending invitation per account keeps one code in
  play (ADR-0004 §2).
- **Source:** #452, ADR-0004 §2, §3 and §4

### ACC-019 — A platform admin resends and revokes a pending invitation with the community limits

- **Status:** Active
- **Rule:** Resending (`resendPlatformAdminInvitation`) and revoking (`revokePlatformAdminInvitation`) a
  platform-admin invitation MUST be allowed only to a platform admin; any other caller MUST be refused with 403, and
  an anonymous one with 401. An unknown user MUST be answered 404, and a user without a pending invitation 409
  `PLATFORM_ADMIN_NOT_INVITED`. Both lock the user row, then the invitation row, then the token rows.
  - Resending MUST answer 503 `PLATFORM_ADMIN_INVITATION_UNAVAILABLE` when the server cannot send, before anything is
    written; MUST allow at most 4 sends per invitation in any 24 hours, the first one included, and answer 429
    `PLATFORM_ADMIN_INVITATION_RESEND_LIMIT_REACHED` with `Retry-After` beyond them, as MEM-010 does; and otherwise
    MUST revoke the previous link, issue a new one and a new code, reset the count of failed code attempts, answer
    the row with the new code, and email the new link once the transaction commits. The previous link and the
    previous code MUST stop working.
  - Revoking MUST make the invitation's link unusable and keep the invitation pending, listed without a valid link.
- **Rationale:** The person may lose the email or the code, or the link may have been revoked after five wrong
  codes; the same limits as for community invitations keep the inbox from being flooded (ADR-0003 §5, ADR-0004 §4).
- **Source:** #452, ADR-0003 §5, ADR-0004 §2 and §4

### ACC-020 — A platform admin does not reach another user's account, except to manage the role

- **Status:** Active
- **Rule:** Being a platform admin MUST NOT, by itself, let a caller read, list or change another user's account. A
  platform admin who is not an active community admin of any of the target's communities MUST be answered as anyone
  who cannot see the target: 404 on reading the user (`getUserById`), listing their supplies
  (`getSuppliesByUserId`), enabling (`enableUser`), disabling (`disableUser`) and deleting (`deleteUser`) them.
  Listing users (`getAllUsers`) MUST be refused with 403 to a caller who administers no community, a platform admin
  included, and its rows MUST be only the caller and the users with an active membership in a community the caller
  administers. A platform admin who is a community admin MUST get exactly what a community admin gets. The platform
  capability `canListUsers` MUST follow these rules, and a user's `canRead`, `canDelete`, `canEnable`, `canDisable`
  and `canListSupplies` likewise. Revoking the role (`revokePlatformAdmin`, ACC-013) keeps its own rules, and the
  platform admin's own account keeps every self-service operation (`getCurrentUser`, `updateProfile`,
  `changePassword`).
- **Rationale:** The platform admin administers the platform, not the people in it: the account is the person's,
  shared by every community they belong to, and the member data a community holds is that community's (ADR-0003 §1
  and §3). The web client no longer reaches users as a platform admin (conluz-web#285); the backend must not keep
  serving them to any client that asks.
- **Source:** #446, ADR-0003 §3, #329. #447 removed creating, importing and editing users and granting the role by
  identifier, with the platform and community capability `canCreateUsers` and the user capabilities `canEdit` and
  `canGrantPlatformAdmin`.

## Impossible states

- An account whose email is an empty or blank string — prevented by ACC-003 and by the migration that turned the
  existing ones into NULL.
- A membership whose account no longer exists — prevented by ACC-009, and by the foreign key
  `community_memberships_user_id_fk`, which has no `ON DELETE` rule: a deletion that skipped ACC-009 would fail
  rather than leave the membership behind.
- Two pending platform-admin invitations for one account — prevented by ACC-018, and by the
  `platform_admin_invitation_user_id_pending_uq` partial unique index.
- A platform-admin invitation whose account no longer exists — prevented by `ON DELETE CASCADE` on
  `platform_admin_invitation_user_id_fk`.
- A supply whose owner's account no longer exists — prevented by ACC-009, and by the foreign key
  `supplies_user_id_fk`.
