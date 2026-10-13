# Spec — Memberships

| | |
| --- | --- |
| **Prefix** | `MEM` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tags `Memberships`, `Users`, `Communities`; operations `createMembership`, `createUser`, `createUsersWithFile`, `getMemberships`, `getCurrentUser`, `getAllCommunities`, `getCommunityById` |
| **Related ADRs** | ADR-0003 (`docs/architecture/adr/0003-separate-person-accounts-from-community-member-records.md`) |
| **Last reviewed against code** | #390 |

## Purpose

A membership links a person's account to one community. It also holds that community's own record
of the member, and the state of the link: whether it has been accepted, and who invited it and
when. This spec covers how the existing creation paths fill the record and that state, and which
memberships grant access; the rest of the capability is specified lazily, as issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Member record | The data a community holds about one of its members: full name, member number, contact email, address and phone. It belongs to the membership, not to the account, so the same person can have a different record in each community (ADR-0003 §1). |
| Accepted membership | A membership with an acceptance instant. A membership without one is pending (ADR-0003 §2). |
| Disabled membership | A membership whose `enabled` flag is false. Independent of acceptance. |
| Active membership | A membership that is both accepted and enabled (ADR-0003 §2). |
| Inviter | The user recorded as having invited a membership. |
| Existing creation path | Adding a user to a community (`createMembership`), creating a user in a community (`createUser` with a community) and importing users into a community (`createUsersWithFile`). |

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

## Impossible states

- A membership without a full name — prevented by the NOT NULL constraint on
  `community_memberships.full_name`.
- A membership whose `invited_by` refers to a user that no longer exists (a dangling reference)
  prevented by the foreign key `community_memberships_invited_by_fk`, 
  which sets `invited_by` to NULL when that user is deleted (MEM-003).
