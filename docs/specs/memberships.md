# Spec — Memberships

| | |
| --- | --- |
| **Prefix** | `MEM` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tags `Memberships`, `Users`; operations `createMembership`, `createUser`, `createUsersWithFile` |
| **Related ADRs** | ADR-0003 (`docs/architecture/adr/0003-separate-person-accounts-from-community-member-records.md`) |
| **Last reviewed against code** | #388 |

## Purpose

A membership links a person's account to one community. It also holds that community's own record
of the member, and the state of the link: whether it has been accepted, and who invited it and
when. This spec covers how the existing creation paths fill the record and that state; the rest of
the capability is specified lazily, as issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Member record | The data a community holds about one of its members: full name, member number, contact email, address and phone. It belongs to the membership, not to the account, so the same person can have a different record in each community (ADR-0003 §1). |
| Accepted membership | A membership with an acceptance instant. A membership without one is pending (ADR-0003 §2). |
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
  unchanged (ADR-0003 §1). A user with no email holds an empty string; "no contact email" is the
  member record's own way of saying the same thing (ADR-0003 §8).
- **Source:** #388, ADR-0003 §1 and §8

### MEM-003 — Deleting the inviter keeps the membership

- **Status:** Active
- **Rule:** Deleting the user recorded as a membership's inviter MUST NOT delete the membership; the
  membership MUST then record no inviter, and keep its invitation instant.
- **Rationale:** Who sent an invitation is a historical detail of the membership; the membership
  itself, and the access it grants, belong to the member and the community.
- **Source:** #388, ADR-0003 §2

## Impossible states

- A membership without a full name — prevented by the NOT NULL constraint on
  `community_memberships.full_name`.
- A membership whose `invited_by` refers to a user that no longer exists (a dangling reference)
  prevented by the foreign key `community_memberships_invited_by_fk`, 
  which sets `invited_by` to NULL when that user is deleted (MEM-003).
