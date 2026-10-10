# ADR-0003 — Separate person accounts from community member records, and model invitations as pending memberships

- **Status:** Accepted
- **Date:** 2026-10-09
- **Deciders:** Víctor Cañizares
- **Applies to:** `lucoenergia/conluz`: users (`users`), memberships (`community_memberships`), access policies (`CallerMemberships` and every policy built on it), one-time tokens (`one_time_token`), member registration and invitation flows

## Context

Epic #329 moves onboarding from "the platform admin creates every user" to invitations:
- the platform admin invites a community's admins;
- each community admin registers and invites the community's members.

The goal is that the platform admin never sees members' personal data, and that each community, as the data controller, sees only the data it entered.

**How the system works today** (inventory of `main` at `eab839b6`):

- **One global row per person.** Each person is one row in `users`, shared by every community: `personal_id` (unique, normalised), `number`, `password`, `full_name`, `address`, `phone_number`, `email`, `enabled`. `number`, the member number, is global although its meaning is per community. `password` and `email` are NOT NULL; "no email" is stored as the empty string by the CSV import.
- **Memberships have no state beyond `enabled`.** `community_memberships` holds `user_id`, `community_id`, `role`, `enabled` and `investment_eur`, with no timestamps and no authorship. `enabled` is never written false by production code, so every "enabled" filter in the policies has never guarded a real state.
- **"Is this an active member?" is answered in ten places.** Six `CallerMemberships` predicates carry it, and it is re-spelled inline in `UserAccessPolicy.administersACommunityOf`, `UserScope.includes`, `UserRepository.findAllVisible` (JPQL) and, twice, `CommunityContextFilter`.
- **Responses ignore membership state.** `CurrentUserResponse.memberships`, `UserResponse.memberships`, `CommunityResponse.memberCount` and `CommunityResponse.adminNames` all include every membership.
- **Member data travels as the global user.** `MembershipResponse`, `SupplyResponse` and the user endpoints embed a full `UserResponse` (`personalId`, `number`, `fullName`, `address`, `email`, `phoneNumber`, `enabled`, `isPlatformAdmin`).
- **One-time tokens are scoped to the user.** The table from #361 enforces one active token per `(user_id, purpose)`, and revokes and counts on that pair.
- **A passwordless login would be a timing oracle.** With a NULL password hash, `BCryptPasswordEncoder.matches(raw, null)` returns without running BCrypt. A passwordless login would therefore answer faster than any other failed login.
- **The email queue is small.** `AfterCommitEmailSender` has a queue of 100, and drops on overflow with a WARN only.

**What made the problem hard to see.** Registering a member whose DNI already exists must not reveal that the person existed (#329), so the existing account is found silently and only a membership is created. But with one global row, the registering community would then see the data another community entered: the creation response, `GET /users/{userId}` and the roster all return the stored row. Silent find and "each community sees only its own data" cannot both hold while member data lives on the person.

## Decision

### 1. An account belongs to the person; a member record belongs to the community

- **`users` becomes the account:** `personal_id`, `password` (nullable), an account email (nullable), `enabled`, `is_platform_admin`, `must_change_password`, `password_changed_at`, `disabled_at`.
- **Each membership carries the community's member record:** full name, member number (optional), contact email (optional), address and phone, exactly as that community entered them.
- **A community admin reads member data only from member records** of their own community. No response built for a community admin carries account fields beyond the identity of the link itself.
- **The same person in two communities has two member records,** which may differ, and neither community sees the other's.
- **Existing data:** the migration copies each user's current data into every membership they hold, so behaviour is unchanged on day one.

**Why both parts.** Silent find without the split leaks another community's data; the split without silent find would have to reveal existing DNIs. Only together do they meet #329.

### 2. An invitation is a pending membership

- **New membership columns:** `accepted_at` (NULL = pending), `invited_by` and `invited_at`. There is **no invitation table.**
- **Pending and disabled are independent.** "Active" means accepted **and** enabled. Only active memberships grant access.
- **One predicate decides "active"** (in `CallerMemberships`), and every re-spelling listed in the Context is replaced by it.
- **Counts and names filter on it:** `memberCount`, `adminNames` and the current user's memberships count only active memberships.
- **The community admin sees pending members** in their roster, with the state visible.
- **A pending member can own supplies** (#341: any membership counts), so a community can operate a member who has not accepted yet, or never will.
- **Existing memberships** are marked accepted at the migration instant.

### 3. One flow for members and community admins

- **A community admin registers a member** with their DNI and member record. **A platform admin invites a community admin** with their DNI, name and email.
- **In both cases** the account is created, or the existing one is found silently, and a pending membership is created with the entered member record.
- **Registering someone already in the same community** answers 409 `MEMBERSHIP_ALREADY_EXISTS`. It reveals nothing: the community admin already sees that member.
- **The platform admin can identify and contact each community's admins.** For every membership with the admin role, and only for those, it sees:
  - the full name, contact email and phone from that community's member record;
  - the DNI, from the account.

  A person who administers two communities appears once per community, each entry with that community's member record. If the same person is a plain member elsewhere, the platform admin sees nothing of that other membership.

### 4. Invitation tokens are bound to the membership

- **The one-time token store gains a membership reference,** with a cascade on delete. Invitation tokens are unique per active membership, so inviting the same person to two communities revokes nothing.
- **`PASSWORD_RESET` tokens keep their per-user invariant** unchanged.
- **New purpose `INVITATION`,** valid for 7 days. Expiry removes only the token: the account and the pending membership remain, and can be invited again.
- **Lock order:** user row, then membership row, then token rows, extending ADR-0002's rule.

### 5. Sending is bounded per membership

- **Per membership and day:** one initial send plus at most 3 resends.
- **Revoking an invitation** revokes its token; the pending membership remains. Removing the membership is a separate action.
- **The CSV import registers members without sending.** Invitations are then sent by an explicit action that handles at most 50 memberships per request, so a large import cannot overflow the email queue.
- **There is no per-community daily cap** (accepted risk, below).

### 6. Accepting proves the person, never the link alone

- **The link** is `<CONLUZ_PUBLIC_WEB_URL>/accept-invitation#<token>`, with the token in the fragment, as in ADR-0002.
- **An account without a password** accepts through a public endpoint that sets its first password (the #330 policy) and marks the membership accepted.
- **An account with a password** accepts through an authenticated endpoint, after logging in. The link never sets or changes an existing password.
- **An account with `must_change_password`** changes its password first, then opens the link again: it remains valid.

### 7. Accounts without a password

- **They cannot log in.** Login compares against a dummy hash, so that a passwordless account costs the same as any other failed login.
- **Password recovery refuses them.** The same 202 is returned, with its own outcome in the log. Their first password is set only by accepting an invitation.

### 8. Email and member numbers

- **Email is optional for members and required for admins.** The contact email in the member record is optional; giving a membership the admin role requires one.
- **Empty strings become NULL** in the migration. "No email" is NULL.
- **The member number is optional:** a platform admin inviting a community admin does not know it.

### 9. Transition

The change is introduced additively. The remaining steps are tracked in #329:
- **Existing endpoints stay as they are.** `POST /api/v1/users` and the current CSV import keep creating active memberships until they are retired, and the new flows live on new endpoints.
- **Responses expose the member record alongside the embedded user,** until the web client reads member records.
- **The embedded user is then removed** from community-admin responses, together with the platform admin's access to users and memberships.

## What states mean after this decision

- **A pending membership** (`accepted_at` NULL): the community has registered the person. It may manage their supplies and data, but the person has no access to that community yet. Normal for any member who never opened an invitation.
- **An accepted, disabled membership:** the person accepted, and the community or the platform admin has since suspended their access. Their supplies remain theirs.
- **An account without a password:** a person registered by a community who has never accepted any invitation. "Cannot log in" and "cannot recover the password" are expected for them, not defects.
- **Different data for the same person in two communities:** expected. Each community's member record is independent; the account holds only identity and credentials.
- **A failed login, or a recovery request answering 202,** does not distinguish a passwordless account from any other case, by design.

## Alternatives considered

**Keep one global user and accept the leak.** This is the smallest change. It was rejected because a community admin would read data another community entered, which is the failure #329 exists to remove.

**A separate invitation table with its own token.** It was rejected because it would be a second source of truth for "is this person invited to this community", able to drift from the membership that actually grants access, and it would duplicate the token store from #361. Once the platform admin enters the DNI, the pending membership already holds everything an invitation needs.

**A placeholder account for invitees without a DNI.** It would have kept the original "name and email only" invitation. It was rejected because a placeholder would need merging with an existing account when the invitee's DNI turned out to exist, which is error-prone and can itself reveal existence.

**One invitation token per user.** It is the existing per-user invariant of the token store. It was rejected because inviting a person to a second community would silently revoke the first invitation's link.

**An unusable password hash instead of a NULL password.** It would avoid a schema change and keep BCrypt on every path. It was rejected because it hides a real state ("never accepted") inside a credential, which every flow would have to recognise by convention.

**Member records in their own table.** It would allow a member record without a membership. It was rejected for now: every member record belongs to exactly one membership, so columns on the membership are simpler. This is revisited below.

**A per-community daily cap on invitations.** It would limit a misused community admin account. It was rejected for now, because each community has a single trusted admin and the per-membership limit already applies.

**A last-community-admin rule.** It would prevent a community from being left without an admin. It was rejected for now, because the platform admin can always invite another one.

## Consequences

**Positive**

- A community sees only the data it entered, and registering an existing person reveals nothing.
- Communities can operate members who never accept (supplies, Datadis, sharing agreements).
- One predicate decides access, replacing ten. The "disabled" state, which existed only in tests, becomes a real, tested one.
- Invitations to several communities are independent of each other.
- The platform admin can identify and contact every community's admins, while its remaining paths to members' personal data are reduced to those that #329 removes.

**Negative**

- A large refactor: member data moves out of `UserResponse` into member records, which touches many responses, services and hundreds of tests.
- The same person can carry inconsistent data across communities, by design.
- The platform admin sees community admins' identification and contact data, DNI included. #329 originally limited it to names and emails.
- **Accepted risk:** without a per-community cap, a misused community admin account could exhaust the shared email quota (about 500 a day), delaying other emails such as password recovery for everyone.
- **Obligation:** every check of "is an active member" goes through the single predicate. No new code may test `enabled` or `accepted_at` inline.
- **Obligation:** responses built for a community admin read member data from the member record, never from the account.
- **Obligation:** every flow that touches an account, a membership and tokens takes the locks in the order user → membership → token.
- **Obligation:** giving a membership the admin role requires a contact email on its member record.
- **Obligation:** the platform admin sees personal data only for memberships with the admin role: name, contact email and phone from that membership's member record, and the DNI from the account. Nothing from members, and nothing from any other membership of the same person.

**Operational**

- The migration copies user data into every membership, marks existing memberships accepted, and turns empty emails into NULL. It must run before any new flow is used.
- The web client keeps reading the embedded user, and the platform admin keeps its existing access, until the steps tracked in #329 are done.
- The invitation email depends on `CONLUZ_PUBLIC_WEB_URL` and on email sending being enabled (ADR-0002).

## Revisit if

- Data must belong to the person across communities (for example, a person-level contact address). Reconsider which fields live on the account.
- A member record needs to exist without a membership. Move member records into their own table.
- Invitation sending is misused, or the shared email quota is reached because of invitations. Add a per-community daily cap.
- A community is left without an admin and the platform admin cannot repair it quickly enough. Add a last-community-admin rule.
- A second backend instance is deployed: the email queue and throttling counters are per instance.

## References

- `lucoenergia/conluz`:
  - Epic #329; #331 (normalised `personalId`), #341 (supply owners must be members), #361 (one-time tokens), #362 (password recovery).
  - ADR-0002 (tokens and transactional email).
  - `users`, `community_memberships` and `one_time_token`, with their changesets.
  - `CallerMemberships`, `UserAccessPolicy`, `UserScope`, `UserRepository.findAllVisible`, `CommunityContextFilter`, `MembershipAccessPolicy`, `CommunityAccessGuardHelper`.
  - `MembershipResponse`, `UserResponse`, `CurrentUserResponse`, `CommunityResponse`.
  - `CreateUserServiceImpl`, `CreateMembershipService`, `CreateOneTimeTokenService`, `GetOneTimeTokenService`, `ConsumeOneTimeTokenService`, `LockUserRepository`.
  - `docs/security/authorization-policy.md`, `docs/security/authentication-throttling.md`.
- `lucoenergia/conluz-web`: the screens that move to member records and the invitation acceptance screen (`/accept-invitation`), tracked in #329.