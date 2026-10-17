# Spec — Memberships

| | |
| --- | --- |
| **Prefix** | `MEM` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tags `Memberships`, `Users`, `Communities`; operations `createMembership`, `registerMember`, `sendInvitation`, `resendInvitation`, `revokeInvitation`, `sendInvitations`, `inspectInvitation`, `acceptInvitation`, `acceptInvitationAsCurrentUser`, `inviteCommunityAdmin`, `getCommunityAdmins`, `resendCommunityAdminInvitation`, `revokeCommunityAdminInvitation`, `enableCommunityAdmin`, `disableCommunityAdmin`, `enableMembership`, `disableMembership`, `updateMemberRecord`, `updateMembershipRole`, `deleteMembership`, `createUser`, `getAllUsers`, `getUserById`, `updateUser`, `getSupply`, `getAllSupplies`, `getSuppliesByUserId`, `createSupply`, `updateSupply`, `enableSupply`, `disableSupply`, `createUsersWithFile`, `getMemberships`, `getCurrentUser`, `getAllCommunities`, `getCommunityById` |
| **Related ADRs** | ADR-0003 (`docs/architecture/adr/0003-separate-person-accounts-from-community-member-records.md`) |
| **Last reviewed against code** | #412 |

## Purpose

A membership links a person's account to one community. It also holds that community's own record
of the member, and the state of the link: whether it has been accepted, and who invited it and
when. This spec covers how the existing creation paths and member registration fill the record and that state, how
a registered member is invited, re-invited and has an invitation withdrawn, how an invitation is inspected and accepted, how the platform admin invites, lists, re-invites, enables and disables a community's admins, how a community admin enables and disables its members' memberships and edits member records, what giving a membership the admin role requires, which memberships grant access, and where responses read member data from; the rest of the capability is specified
lazily, as issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Member record | The data a community holds about one of its members: full name, member number, contact email, address and phone. It belongs to the membership, not to the account, so the same person can have a different record in each community (ADR-0003 §1). |
| Accepted membership | A membership with an acceptance instant. A membership without one is pending (ADR-0003 §2). |
| Disabled membership | A membership whose `enabled` flag is false. Independent of acceptance. |
| Active membership | A membership that is both accepted and enabled (ADR-0003 §2). |
| Inviter | The user recorded as having invited a membership. |
| Existing creation path | Adding a user to a community (`createMembership`), creating a user in a community (`createUser` with a community) and importing users into a community (`createUsersWithFile`). |
| Member registration | A community admin registering a member by personal ID and member record (`registerMember`). It is not an existing creation path. |
| Contact email | The email address in a member record. Distinct from the account's email. |
| Usable invitation token | A valid `INVITATION` token (OTT-008) whose membership is still pending and whose account is enabled. Whether the membership is enabled does not matter (MEM-018). |
| Awaiting acceptance | A membership that is pending and enabled: one a community still wants the member to accept. |
| Invitation link | The `INVITATION` token sent to a member, and the link that carries it. Its expiry is that of the membership's valid `INVITATION` token (OTT-002). |

## Rules

### MEM-001 — A membership created through an existing creation path is accepted on creation

- **Status:** Active
- **Rule:** A membership created through an existing creation path MUST be accepted at the instant it
  is created, and MUST record no inviter and no invitation instant.
- **Rationale:** These paths create memberships that grant access straight away, so they must keep
  doing so once access is based on acceptance (ADR-0003 §2). None of them is an invitation, so
  nothing about one is recorded. Memberships that existed before #388 were accepted at the migration
  instant, which keeps their behaviour unchanged.
- **Source:** #388, ADR-0003 §2

### MEM-002 — A membership created through an existing creation path holds a copy of the user's data

- **Status:** Active
- **Rule:** A membership created through an existing creation path MUST hold, as its member record,
  the full name, member number, address and phone the user has at creation time, and the user's
  email as its contact email. An empty or blank email MUST be recorded as no contact email.
- **Rationale:** Until the flows of #329 let a community enter its own record, the only data a
  community has about a member is the user's, so the record starts as a copy of it and behaviour is
  unchanged (ADR-0003 §1). A user with no email holds NULL (ACC-003), and so does a member record
  with no contact email (ADR-0003 §8).
- **Source:** #388, #389, ADR-0003 §1 and §8

### MEM-003 — Deleting the inviter keeps the membership

- **Status:** Active
- **Rule:** Deleting the user recorded as a membership's inviter MUST NOT delete the membership; the
  membership MUST then record no inviter, and keep its invitation instant.
- **Rationale:** Who sent an invitation is a historical detail of the membership; the membership
  itself, and the access it grants, belong to the member and the community.
- **Source:** #388, ADR-0003 §2

### MEM-004 — Only an active membership grants access

- **Status:** Active
- **Rule:** A membership MUST grant its holder access to its community only while it is active.
  Every access decision that depends on the caller's membership, including the `X-Community-Id`
  header and the single-membership default, MUST answer the holder of a pending or disabled
  membership exactly as it answers someone with no membership in that community. Being visible to a
  community admin through the user endpoints follows the same rule. Whether a membership is active
  MUST be decided only by `CommunityMembership.isActive()`, and in queries by
  `ActiveMembershipJpql.ACTIVE_MEMBERSHIP`. Owning a supply is not access: any membership in the
  supply's community counts, pending or disabled included.
- **Rationale:** An invitation is a pending membership, so a person must not reach a community before
  accepting (ADR-0003 §2). One definition keeps every check in step; before #390, "is an active
  member" was written in ten places, each testing `enabled` alone. A community can operate a member
  who has not accepted yet, so supply ownership does not depend on the state (#341).
- **Source:** #390, #341, ADR-0003 §2

### MEM-005 — Pending and disabled are independent, and the roster shows both

- **Status:** Active
- **Rule:** Whether a membership has been accepted and whether it is enabled MUST be recorded and
  reported separately, never combined into one state. The roster (`getMemberships`) MUST
  list every membership of the community, pending and disabled included, with both its acceptance
  instant (null while pending) and its `enabled` flag.
- **Rationale:** A membership can be pending, disabled, both, or neither, and each combination means
  something different (ADR-0003, "What states mean"). A community admin needs to see who has not
  accepted yet and who has been suspended.
- **Source:** #390, ADR-0003 §2

### MEM-006 — Counts, admin names and the caller's own memberships include only active memberships

- **Status:** Active
- **Rule:** A community's member count and its admin names MUST count and name only active
  memberships. The current user's memberships (`getCurrentUser`) MUST list only their active
  memberships.
- **Rationale:** These values describe who takes part in the community and where the caller has
  access. A pending or disabled membership does neither (MEM-004).
- **Source:** #390, ADR-0003 §2

### MEM-007 — Member registration creates a pending membership holding the record as entered

- **Status:** Active
- **Rule:** Only an active community admin of the community MAY register a member in it; a platform admin MUST NOT,
  unless they are one. Member registration MUST create a membership with the role `COMMUNITY_MEMBER`, enabled,
  pending, with the registering community admin as its inviter, and MUST issue no invitation token and send
  nothing: the invitation is a separate action (MEM-009). A personal ID that is empty once normalised (made
  only of whitespace, dots and hyphens) MUST be refused with 400 before any account is looked up or written. Its
  member record MUST be the values entered, untrimmed, with an empty or blank optional value recorded as none. If
  the person already has a membership in the community, pending, active or disabled, registration MUST answer 409
  `MEMBERSHIP_ALREADY_EXISTS` and change nothing.
- **Rationale:** The community enters its own record of the member (ADR-0003 §1), and the person accepts before
  the membership grants access (ADR-0003 §2, MEM-004). A platform admin administers the platform, not the personal
  data of a community's members. A 409 for someone already in the community reveals nothing the community admin
  cannot see in the roster (ADR-0003 §3). An existing account is found silently, so every registration of a
  personal ID that normalises to nothing would land on one shared account with an empty personal ID. Registering
  sends nothing so that a community can record its members and get ready before inviting them, as the CSV import
  already does (ADR-0003 §5).
- **Source:** #392, ADR-0003 §1, §2 and §3

### MEM-008 — Member registration never reveals whether the person had an account

- **Status:** Active
- **Rule:** The response to member registration MUST be the same whether or not an account with the personal ID
  existed, and MUST carry only the membership, its state, its capabilities, the member record, the personal ID as
  entered, normalised, and a reference to the account holding only its id: never another field of the account.
  Two concurrent registrations of the same new personal ID MUST both succeed, with one account. Registration MUST
  log one line with the masked personal ID and the community, and nothing that tells whether the account existed
  or any email address.
- **Rationale:** Registering an existing personal ID finds its account silently (ADR-0003 §3); a different answer,
  account data or a distinct log line would tell a community admin that the person is registered elsewhere. A
  concurrent registration that lost the race to create the account must not answer differently either. The
  account's id is random, so it reads the same for a new and an existing account, and it is the `{userId}` the
  membership's endpoints take.
- **Source:** #392, ADR-0003 §1 and §3

### MEM-009 — Inviting a registered member sends one invitation to the member record's contact email

- **Status:** Active
- **Rule:** Only an active community admin of the community MAY invite one of its members; a platform admin MUST
  NOT, unless they are one. Inviting MUST issue an `INVITATION` token for the membership, record the invitation
  instant and, once the request commits, email the member record's contact email, never the account's email, a link
  `<public web URL>/accept-invitation#<token>` with the token only in the fragment. It MUST refuse, issuing and
  sending nothing: an accepted membership with 409 `MEMBERSHIP_ALREADY_EXISTS`; a membership already invited with
  409 `MEMBERSHIP_ALREADY_INVITED`; a member record without a contact email with 400
  `MEMBERSHIP_WITHOUT_CONTACT_EMAIL`; a disabled membership as MEM-025 says. When the server cannot send it, because
  the public web URL is not configured or email sending is disabled, it MUST refuse with 503 `MEMBERSHIP_INVITATION_UNAVAILABLE` before locking or
  issuing anything, leave the invitation instant empty, and log one warning naming only the email category. It
  MUST log one line with the user and the community, and no email address.
- **Rationale:** The contact email is the community's own way to reach the member (ADR-0003 §1, §8); the account's
  email may have been entered by another community. The fragment keeps the token out of every access log
  (ADR-0002). An accepted member is already a member, so there is nothing to invite them to. Sending again is a
  resend, limited per membership and day (ADR-0003 §5, MEM-010). Inviting exists only to send the email: an
  invitation recorded but never sent would tell the community admin the member was invited, and leave the
  membership "already invited" with nothing in the member's inbox.
- **Source:** #392, #395, ADR-0003 §1, §5, §6 and §8, ADR-0002

### MEM-010 — Resending an invitation sends a new link, at most 4 sends per membership in 24 hours

- **Status:** Active
- **Rule:** The callers allowed by MEM-009 MAY resend the invitation of a membership that has already been
  invited, whether its link is valid, expired or revoked. Resending MUST issue a new `INVITATION` token, which
  revokes the previous one, record the resend's instant as the invitation instant and, once the request commits,
  email the member record's current contact email the same text and link as MEM-009. It MUST refuse, issuing and
  sending nothing: an accepted membership with 409 `MEMBERSHIP_ALREADY_EXISTS`; a membership never invited with
  409 `MEMBERSHIP_NOT_INVITED`; a member record without a contact email with 400
  `MEMBERSHIP_WITHOUT_CONTACT_EMAIL`; a disabled membership as MEM-025 says; and a membership with 4 or more
  `INVITATION` tokens issued in the last 24 hours, whatever their state, with 429
  `MEMBERSHIP_INVITATION_RESEND_LIMIT_REACHED` and a `Retry-After` header, also in the error's `retryAfterSeconds`
  parameter, of the whole seconds until the oldest of them is more than 24 hours old. The count MUST be taken under the membership's lock. When the server cannot send, it MUST refuse
  exactly as MEM-009 does, before locking or issuing anything. It MUST log one line with the user, the community
  and the outcome, and no email address.
- **Rationale:** A link expires after 7 days (OTT-002) and an email can be lost after commit, so a member must be
  reachable again. ADR-0003 §5 allows one send plus 3 resends per membership and day, counted from the token store
  so the limit needs no column of its own (OTT-006); counting under the lock keeps two concurrent resends from
  both passing at the limit. A 429 with `Retry-After` is how the API already answers a limit (#332). A send stops
  counting only once it is more than 24 hours old, so a wait of a whole number of seconds is followed by one more.
  The current contact email is used because correcting a wrong address is a reason to resend. A membership never
  invited is invited with `sendInvitation`, which records the first send.
- **Source:** #393, #395, ADR-0003 §5

### MEM-011 — Revoking an invitation withdraws its link and keeps the membership

- **Status:** Active
- **Rule:** The callers allowed by MEM-009 MAY revoke the invitation of a pending membership. Revoking MUST revoke
  the membership's active `INVITATION` token (OTT-007), and MUST leave the membership pending, with its invitation
  instant as it was. With no active token, it MUST change nothing. In both cases it MUST answer the member. It MUST
  refuse an accepted membership with 409 `MEMBERSHIP_ALREADY_EXISTS`. It MUST NOT depend on whether the server can
  send. It MUST log one line with the user, the community and whether a token was revoked.
- **Rationale:** A link sent to a wrong address must be withdrawn without losing the member record; removing the
  membership is a separate action (ADR-0003 §5). The invitation instant records the last send, which revoking does
  not undo, so the membership can be resent (MEM-010) and counts toward its limit. Withdrawing a link sends
  nothing, so it must work when sending does not.
- **Source:** #393, ADR-0003 §5

### MEM-012 — Bulk sending invites up to 50 members awaiting acceptance and never invited

- **Status:** Active
- **Rule:** The callers allowed by MEM-009 MAY send the first invitation to the memberships of a community that are
  awaiting acceptance, have a contact email and have never been invited, in one request. A request MUST invite at
  most 50 of them, chosen and locked in ascending user id, and MUST re-check each one under its lock, skipping it
  if it is no longer eligible, so that no membership is invited twice. Each one invited MUST be invited as MEM-009
  specifies, in a transaction of its own. Every other membership, including a disabled one, MUST be skipped, not
  refused. The request MUST answer how many it sent and how many eligible memberships remain. When the server
  cannot send, it MUST refuse exactly as MEM-009 does, before selecting or issuing anything. It MUST log one line
  with the community and the counts. Whether a membership is awaiting acceptance MUST be decided only by
  `CommunityMembership.isAwaitingAcceptance()`, and in queries by `ActiveMembershipJpql.AWAITING_ACCEPTANCE`.
- **Rationale:** Members registered without an invitation, such as an import, are invited by an explicit action
  bounded at 50 per request, so it cannot overflow the email queue (ADR-0003 §5). A disabled membership grants
  nothing even once accepted (MEM-004), so inviting it in bulk would ask the member to accept something
  useless. One transaction per membership holds one lock pair at a time, starts each email once it is committed,
  and keeps the invitations already made when a later one fails; the next request continues where it stopped. A
  single order of locks keeps two concurrent requests from deadlocking. One spelling of "awaiting acceptance", as
  for "active" (MEM-004), keeps the code and the queries in step.
- **Source:** #393, ADR-0003 §5

### MEM-013 — A member shows when its invitation link expires

- **Status:** Active
- **Rule:** Every response that shows a membership MUST show when its invitation link expires: the expiry of its
  valid `INVITATION` token, or null when it has none, including after a revoke and once it has expired. The roster
  MUST read the expiries of all its memberships in one query.
- **Rationale:** A community admin decides from it whether a resend is needed. One query keeps the roster's cost
  independent of its size.
- **Source:** #393

### MEM-014 — Inspecting an invitation tells the community and whether the person must log in, and writes nothing

- **Status:** Active
- **Rule:** Anyone holding a usable invitation token MAY inspect it without authentication (`inspectInvitation`).
  The answer MUST be 200 with the membership's community (id and name) and `loginRequired`: true when the account
  has a password, false when it has none. Inspecting MUST NOT use up the token and MUST NOT write anything.
- **Rationale:** The acceptance screen must know, before asking anything, whether to ask for a first password or
  to send the person to log in (ADR-0003 §6). Only the holder of the link learns the community and the account's
  password state; the token is the only credential.
- **Source:** #394, ADR-0003 §6

### MEM-015 — Every unusable invitation token is answered the same

- **Status:** Active
- **Rule:** On `inspectInvitation`, `acceptInvitation` and `acceptInvitationAsCurrentUser`, a token that is not a
  usable invitation token — unknown, malformed, expired, used or revoked; of a membership that is no longer
  pending; of a disabled account; or, on `acceptInvitationAsCurrentUser`, of another account's membership — MUST be
  answered 400 `MEMBERSHIP_INVITATION_TOKEN_INVALID`, identically in every case, and MUST change nothing. Each such
  answer MUST count as one failure against the client address (#332) and MUST be logged as one warning with the
  client address only. Over the address's limit, the three endpoints MUST answer 429 with `Retry-After` before the
  token is looked at.
- **Rationale:** The reason a link is unusable must not be learnt by trying tokens, and guessing tokens must be
  slowed down like guessing passwords (ADR-0002 §6). A token of someone else's membership proves nothing about
  the caller.
- **Source:** #394, ADR-0002 §6, ADR-0003 §6

### MEM-016 — Accepting an invitation without logging in sets the account's first password

- **Status:** Active
- **Rule:** `acceptInvitation` takes a token and a new password, without authentication. With a usable invitation
  token, in this order:
  1. if the account has a password, it MUST answer 409 `MEMBERSHIP_INVITATION_LOGIN_REQUIRED`, without using up
     the token or touching the password;
  2. a new password that breaks the password policy (#330) MUST be refused as on a password change, without using
     up the token;
  3. otherwise it MUST use up the token, set the password (clearing the "must change password" flag, with the
     change instant from the clock session tokens are compared with), set the account's email to the member
     record's contact email when the account has no email and the record has one, mark the membership accepted,
     and answer 204 with no body and no session. Neither refusal counts against the client address.
- **Rationale:** An account without a password proves the person by choosing its first password through the link
  sent to the member record's contact email (ADR-0003 §6, §7). An account with a password proves the person by
  logging in, and the link never sets or changes an existing password. The policy is checked before the token is
  used up so that a rejected password does not cost the link (ADR-0002 §6). The contact email becomes the
  account's so that the person can recover the password they just chose.
- **Source:** #394, ADR-0002 §6, ADR-0003 §6 and §7

### MEM-017 — Accepting an invitation as the logged-in user accepts only the caller's own membership

- **Status:** Active
- **Rule:** `acceptInvitationAsCurrentUser` takes a token from an authenticated caller. With a usable invitation
  token of one of the caller's own memberships, it MUST use up the token, mark the membership accepted, and answer
  204. It MUST NOT read or change the password. A caller who must change their password MUST be refused with 403
  `USER_PASSWORD_CHANGE_REQUIRED` before the token is looked at, so the link stays usable once the password has
  been changed.
- **Rationale:** Logging in proves the person; the token then only says which membership to accept (ADR-0003 §6).
- **Source:** #394, ADR-0003 §6

### MEM-018 — A disabled membership can be accepted, and still grants nothing

- **Status:** Active
- **Rule:** Both acceptance endpoints MUST accept a pending membership whether or not it is enabled. A disabled
  membership, once accepted, MUST still grant no access until it is enabled (MEM-004).
- **Rationale:** Pending and disabled are independent (ADR-0003 §2, "What states mean"): accepting records that
  the person took up the invitation, and enabling is the community's separate decision.
- **Source:** #394, ADR-0003 §2

### MEM-019 — Accepting locks the user, then the membership, then the token, and is logged

- **Status:** Active
- **Rule:** An acceptance MUST find the token's membership without locking, lock the user row, then the membership
  row, read both again under the locks, and only then use up the token, continuing only if it belongs to the same
  membership (OTT-004). The acceptance instant MUST come from the application clock. Each acceptance MUST be
  logged as one line with the user, the community and the endpoint, and no log line, response or exception MUST
  contain the token, its hash, a password or an email address.
- **Rationale:** Resending, revoking and issuing take the same locks in the same order, so an acceptance
  concurrent with any of them cannot deadlock, and its checks see the state the write will change.
- **Source:** #394, ADR-0003 §4, OTT-004

### MEM-020 — Inviting a community admin registers and invites in one operation

- **Status:** Active
- **Rule:** Only a platform admin MAY invite a community admin; a community admin MUST NOT, of this community or any
  other. The invitation takes a personal ID, a full name, a contact email, all required, and an optional phone. It
  MUST first check that the server can send, and refuse with 503 `MEMBERSHIP_INVITATION_UNAVAILABLE` before
  creating anything when it cannot, as MEM-009 does. It MUST then, in one transaction, register the person as
  MEM-007 and MEM-008 say, with the role `COMMUNITY_ADMIN`, the platform admin as the inviter and the record as
  entered (no member number, no address), and send the invitation as MEM-009 does. A person who already has a
  membership in the community, whatever its role or state, MUST be refused with 409 `MEMBERSHIP_ALREADY_EXISTS`,
  creating and sending nothing. The answer and the log MUST be the same whether or not the account existed. It MUST
  log one line with the user and the community, and no email address or unmasked personal ID.
- **Rationale:** ADR-0003 §3 gives members and community admins one flow, so the same registration creates both.
  A platform admin appoints a community's first admin, who has no one to send them the invitation later, so the
  invitation is part of the operation, and refusing before registering keeps an admin from being created who
  cannot be told. An admin is identified and contacted through the contact email (ADR-0003 §8), so it is required.
- **Source:** #395, ADR-0003 §3 and §8

### MEM-021 — The admin list shows each admin membership's record and the personal ID, and nothing else

- **Status:** Active
- **Rule:** Only a platform admin MAY list a community's admins. The list MUST hold every membership of the
  community with the role `COMMUNITY_ADMIN`, in every state, and no other. Each entry MUST show the full name,
  contact email and phone from that membership's member record, the personal ID from the account, whether the
  membership is accepted and enabled, when it was last invited and when its invitation link expires. It MUST NOT
  show anything else from the account, nor anything from any other membership of the same person. The link
  expiries MUST be read in one query for the whole list.
- **Rationale:** ADR-0003 §3 lets the platform admin identify and contact every community's admins, and its
  obligations limit that to admin memberships: a person who administers two communities appears in each with that
  community's record, and their membership elsewhere stays the other community's business. Reading the expiries
  per entry would cost a query per admin.
- **Source:** #395, ADR-0003 §3

### MEM-022 — The platform admin resends and revokes an admin's invitation with the member rules

- **Status:** Active
- **Rule:** Only a platform admin MAY resend or revoke the invitation of a community admin's membership through the
  admin endpoints. Resending MUST follow MEM-010 and revoking MUST follow MEM-011, with their refusals, limit and
  codes. A membership without the role `COMMUNITY_ADMIN` MUST be answered 404, as one that does not exist. Each
  MUST log one line with the user, the community and the outcome, and no email address.
- **Rationale:** An admin's invitation is a member's invitation sent by someone else, so the same rules apply. The
  admin endpoints are scoped to admin memberships, so they reveal nothing about the community's members.
- **Source:** #395, ADR-0003 §3 and §5

### MEM-023 — Enabling and disabling an admin membership writes only that membership

- **Status:** Active
- **Rule:** Only a platform admin MAY enable or disable a community admin's membership. It MUST write only that
  membership's `enabled` flag, never the account, and MUST take the user, then the membership lock. Setting the
  state the membership already has MUST change nothing and succeed. A membership without the role
  `COMMUNITY_ADMIN` MUST be answered 404, as one that does not exist. Once disabled, the membership MUST grant
  nothing from the admin's next request (MEM-004), while their other memberships keep granting what they did. It
  MUST log one line with the user, the community and the operation.
- **Rationale:** Suspending an admin of one community must not lock them out of the others (ADR-0003 §2); the
  account's flag would. Memberships are read again on every request (#390), so no session needs revoking.
  Disabling a member's membership is out of scope (#395).
- **Source:** #395, ADR-0003 §2 and §3

### MEM-024 — Giving a membership the admin role requires a contact email

- **Status:** Active
- **Rule:** Changing a membership's role to `COMMUNITY_ADMIN` through `updateMembershipRole` MUST be refused with
  400 `MEMBERSHIP_WITHOUT_CONTACT_EMAIL`, changing nothing, when its member record has no contact email. A
  membership that already has the role is not refused.
- **Rationale:** ADR-0003 §8 makes the contact email required for admins: it is how the platform admin contacts
  them (MEM-021). An existing admin without one is left as it is, so the rule does not refuse a request that
  changes nothing.
- **Source:** #395, ADR-0003 §8

### MEM-025 — A disabled membership is not sent an invitation

- **Status:** Active
- **Rule:** Sending (MEM-009) or resending (MEM-010) the invitation of a pending membership that is disabled MUST be
  refused with 409 `MEMBERSHIP_DISABLED`, issuing and sending nothing, whatever the membership's role. The state
  MUST be read under the membership's lock. Revoking its invitation MUST still work (MEM-011). Bulk sending already
  skips it (MEM-012).
- **Rationale:** A disabled membership grants nothing (MEM-004), so an invitation would ask someone to join a
  community they cannot use. Reading the state under the lock keeps a membership disabled meanwhile from being sent
  a link. Withdrawing a link sends nothing, so it stays available.
- **Source:** #395, ADR-0003 §2

### MEM-026 — Community responses, and user responses read by a community admin, show the member record

- **Status:** Active
- **Rule:** A user embedded in a response scoped to one community — the user of a membership
  (`getMemberships`, `createMembership`, `updateMembershipRole`) and the owner of a supply (`getSupply`,
  `getAllSupplies`, `getSuppliesByUserId`, `createSupply`, `updateSupply`, `enableSupply`, `disableSupply`) — MUST
  show the full name, number, email, address and phone of their member record in that community: the path
  community for memberships, the supply's community for supplies, whoever the caller is. A community's admin
  names (`getAllCommunities`, `getCommunityById`) MUST be the full names of the admins' member records in that
  community. When a supply's owner has no membership in the supply's community, those five fields MUST be blank
  (an empty full name, the other four null), never the account's. A user read through `getAllUsers`,
  `getUserById` or returned by `updateUser` MUST show the account's data to the user themselves and to the
  platform admin; to any other caller, the five fields MUST come from the user's member record in a community
  the caller administers through an active admin membership and, when there are several, in the one whose id is
  the lowest in the canonical text form of the id (the order PostgreSQL gives `uuid`). Every other field MUST
  keep coming from the account.
- **Rationale:** ADR-0003 §1 obliges every response built for a community admin to read member data from the
  member record. Since member registration finds an existing account silently (MEM-008), the account may hold
  what another community or the person entered: showing it would disclose that data and reveal that the person
  already had an account. Choosing one record deterministically keeps repeated reads consistent; both
  candidates belong to communities the caller administers, so the choice reveals nothing. A supply whose owner
  left its community has no record to show, and falling back to the account would leak it. Records of accounts
  with a single membership are copied again from the account once, so that edits made to the account since the
  copy of #388 still show.
- **Source:** #396, ADR-0003 §1

### MEM-027 — A community admin enables and disables the memberships of their community's members

- **Status:** Active
- **Rule:** Only an active community admin of the community MAY enable or disable a membership in it; a platform
  admin who is not one MUST be refused with 403. Only a membership with the role `COMMUNITY_MEMBER`, pending or
  accepted, MAY be enabled or disabled this way: a membership with any other role MUST be answered 404, as one
  that does not exist, and is managed by the platform admin (MEM-023). It MUST write only that membership's
  `enabled` flag, never the account, and MUST take the user, then the membership lock. Setting the state the
  membership already has MUST change nothing and succeed. Once disabled, the membership MUST grant nothing from
  the member's next request (MEM-004), while their other memberships keep granting what they did, and it is not
  sent an invitation (MEM-025). It MUST log one line with the user, the community and the operation.
- **Rationale:** A community admin cannot enable or disable the account, which is shared by every community the
  person belongs to (ACC-008); suspending a member is a decision of one community, and its effect must stay in
  it (ADR-0003 §2). Admin memberships stay with the platform admin, who appoints and suspends admins (#395).
  Memberships are read again on every request (#390), so no session needs revoking.
- **Source:** #415, #395, ADR-0003 §1 and §2

### MEM-028 — A community admin edits the member records of their community

- **Status:** Active
- **Rule:** Only an active community admin of the community MAY change a member record in it through
  `updateMemberRecord`; a platform admin who is not one MUST be refused with 403. Any membership of the community
  MAY be edited, whatever its role and state, the caller's own included; a user with no membership in it MUST be
  answered 404. The request replaces the whole record: a contact email, address or phone number that is missing,
  empty or blank, and a missing member number, MUST be stored as none, and nothing is trimmed. A membership with
  the role `COMMUNITY_ADMIN` whose new record has no contact email MUST be refused with 400
  `MEMBERSHIP_WITHOUT_CONTACT_EMAIL`, changing nothing. The change MUST write only the record's full name, member
  number, contact email, address and phone number, never the account, the role, the state or the invitation, and
  MUST take the user, then the membership lock. An invitation link already sent stays valid; the next send or
  resend goes to the new contact email (MEM-009, MEM-010). It MUST log one line with the user and the community,
  and never an email address.
- **Rationale:** The account belongs to the person and is shared by every community they belong to; what a
  community knows about a member is its own record (ADR-0003 §1), so correcting it must not reach the account
  or another community (ACC-008). Admins need a contact email (ADR-0003 §8, MEM-024), so it cannot be removed
  from an admin's record. A blank email is "no email", as everywhere else (ACC-003).
- **Source:** #416, ADR-0003 §1 and §8

### MEM-029 — The membership of a supply owner in the community is not deleted

- **Status:** Active
- **Rule:** Deleting a membership (`deleteMembership`) whose user owns a supply in that community, enabled or not,
  MUST be refused with 409 `MEMBERSHIP_OWNS_SUPPLIES`, and nothing MUST be deleted. Supplies the user owns in other
  communities MUST NOT prevent it. Otherwise the membership MUST be deleted with its member record and its tokens
  (OTT-005), and the account kept. The deletion MUST lock the user, then the membership, before checking for
  supplies; supply creation locks the user too (SUP-002), so a supply created at the same time either refuses the
  deletion or finds the membership gone. Who may delete a membership MUST NOT change.
- **Rationale:** The owner of a supply belongs to the supply's community (#341, SUP-002). Since #415, deleting the
  membership is how a community admin removes someone from their community, and it used to leave supplies whose
  owner was no longer a member. Suspending the member, by disabling the membership (MEM-027), withdraws their
  access and keeps their supplies and records; deleting the membership is possible once their supplies in the
  community are deleted (SUP-001).
- **Source:** #412, #341, #415

## Impossible states

- A membership without a full name — prevented by the NOT NULL constraint on
  `community_memberships.full_name`.
- A membership whose `invited_by` refers to a user that no longer exists (a dangling reference)
  prevented by the foreign key `community_memberships_invited_by_fk`, 
  which sets `invited_by` to NULL when that user is deleted (MEM-003).
- A supply whose owner has no membership in the supply's community — prevented by SUP-002 when the supply is
  created and by MEM-029 when the membership is deleted, except for data written before them (see Accepted
  exceptions).

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| Supplies whose owner has no membership in the supply's community, left by membership deletions before MEM-029 | They cannot be fixed automatically: whether to register the owner again or delete the supply is a decision for the community | The query in the pull request of #412 returns no rows in every deployment | #412 |
