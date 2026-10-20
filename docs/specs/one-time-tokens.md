# Spec — One-time tokens

| | |
| --- | --- |
| **Prefix** | `OTT` |
| **Status** | Active |
| **Contract** | None: the token store has no operation of its own. Password recovery (`requestPasswordReset`, `resetPassword`) and membership invitations (`sendInvitation`, `resendInvitation`, `revokeInvitation`, `sendInvitations`, and the invitation expiry on membership responses) and platform-admin invitations (`invitePlatformAdmin`, `resendPlatformAdminInvitation`, `revokePlatformAdminInvitation`, `getPlatformAdmins`) use it, and invitation acceptance (`inspectInvitation`, `acceptInvitation`, `acceptInvitationAsCurrentUser`) consumes it. |
| **Related ADRs** | ADR-0002 (`docs/architecture/adr/0002-recover-passwords-with-single-use-hashed-tokens-emailed-after-commit.md`), ADR-0003 (`docs/architecture/adr/0003-separate-person-accounts-from-community-member-records.md`), ADR-0004 (`docs/architecture/adr/0004-grant-platform-admin-only-through-an-accepted-invitation-confirmed-out-of-band.md`) |
| **Last reviewed against code** | #452 |

## Purpose

One-time tokens are secret, single-use, expiring tokens issued for one purpose: a password reset for a user, an
invitation for a membership, or a platform-admin invitation. This spec covers tokens bound to a membership or to a
platform-admin invitation and how they live alongside the tokens bound only to a user; the rest of the capability (hashing, single use, cleanup, from #361) is specified lazily, as
issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Purpose | What a token is issued for. `PASSWORD_RESET` is bound only to a user; `INVITATION` is bound to a membership; `PLATFORM_ADMIN_INVITATION` is bound to a platform-admin invitation. |
| Scope | What a purpose's tokens are bound to besides their user: nothing (user), a membership, or a platform-admin invitation. |
| Active token | A token that is neither used nor revoked. It may have expired. |
| Valid token | An active token whose expiry is after the current instant. |
| User-scoped operation | Issuing, finding the owner of, consuming or counting a token by user. |
| Membership-scoped operation | Issuing, finding the membership of, consuming, counting, finding the earliest issued, finding the valid expiry of, or revoking a token by membership. |
| Invitation-scoped operation | Issuing, finding the platform-admin invitation of, consuming, counting, finding the earliest issued, or revoking a token by platform-admin invitation. |

## Rules

### OTT-001 — A membership has at most one active token, independently of the user's other tokens

- **Status:** Active
- **Rule:** Issuing a token for a membership MUST revoke that membership's earlier active token and no other.
  The tokens of the user's other memberships, and the user's tokens bound only to them, MUST stay as they are. A
  token cannot be issued for a membership that does not exist.
- **Rationale:** Inviting the same person to a second community must not revoke the first invitation's link, and
  each invitation has its own resend budget. `PASSWORD_RESET` keeps its one active token per user.
- **Source:** #391, ADR-0003 §4

### OTT-002 — An invitation token is valid for 7 days

- **Status:** Active
- **Rule:** An `INVITATION` or `PLATFORM_ADMIN_INVITATION` token MUST be accepted until 7 days after it was issued,
  and refused from that instant on.
- **Rationale:** Members read email rarely; the link must survive a week. Expiry removes only the token: the
  account and the pending membership or platform-admin invitation remain, and can be invited again.
- **Source:** #391, ADR-0003 §4. #452 added `PLATFORM_ADMIN_INVITATION`, ADR-0004 §3.

### OTT-003 — A purpose is used only with the operations of its scope

- **Status:** Active
- **Rule:** Each purpose has one scope, and an operation MUST refuse a purpose of another scope than its own before it
  reads or writes any token: a user-scoped operation accepts only a purpose bound only to a user, a
  membership-scoped operation only a purpose bound to a membership, and an invitation-scoped operation only a
  purpose bound to a platform-admin invitation. A token MUST be bound to at most one of a membership and a
  platform-admin invitation (`one_time_token_single_binding_check`).
- **Rationale:** A `PASSWORD_RESET` token issued for a membership, or an `INVITATION` token without one, would
  escape the invariant of its purpose. The scope is a property of the purpose, so adding a purpose of an existing
  scope needs no migration.
- **Source:** #391. #452 added the platform-admin invitation scope and the single-binding check, ADR-0004 §3.

### OTT-004 — Locks are taken user row, then membership or invitation row, then token rows

- **Status:** Active
- **Rule:** Every flow that touches a user, a membership and tokens MUST lock in the order user row, membership
  row, token rows, and every flow that touches a user, a platform-admin invitation and tokens in the order user row,
  invitation row, token rows. A flow that consumes such a token and then writes the user, the membership or the
  invitation MUST find the token's membership or invitation without locking, lock the user, then the membership or
  invitation, then consume, and continue only if the consumed token belongs to the same membership or invitation.
- **Rationale:** Issuing and revoking take the locks in that order; a flow that takes them in another order can
  deadlock against them.
- **Source:** #391, ADR-0002 §3, ADR-0003 §4. #452 added the platform-admin invitation row, ADR-0004 §3.

### OTT-005 — A membership's or invitation's tokens go with it

- **Status:** Active
- **Rule:** Deleting a membership MUST delete its tokens, and no other; deleting a platform-admin invitation MUST
  delete its tokens, and no other. Deleting a user MUST delete every token of that user, and their platform-admin
  invitations.
- **Rationale:** A token for a membership or an invitation that no longer exists must not remain usable.
- **Source:** #391, ADR-0003 §4. #452 added platform-admin invitations, ADR-0004 §3.

### OTT-006 — Tokens are counted per membership, within the retention

- **Status:** Active
- **Rule:** The count of tokens issued for a membership since an instant MUST include every token of that
  membership and purpose created at or after it, whatever its state, and no token of another membership. A count
  that looks back further than the retention of finished tokens MUST be refused.
- **Rationale:** The sending limit is per membership (ADR-0003 §5). Finished tokens are deleted after the
  retention, so an older window would count short.
- **Source:** #391, ADR-0003 §5

### OTT-007 — Revoking a membership's token

- **Status:** Active
- **Rule:** Revoking a membership's token of a purpose MUST make its active token unusable and MUST leave every
  other token as it is. When the membership has no active token of that purpose, revoking MUST change nothing and
  report that nothing was revoked. Revoking for a membership that does not exist is refused.
- **Rationale:** Revoking an invitation revokes its token while the pending membership remains; removing the
  membership is a separate action.
- **Source:** #391, ADR-0003 §5

### OTT-008 — A membership's token is consumed once, and invalid tokens are indistinguishable

- **Status:** Active
- **Rule:** Consuming a valid token issued for a membership MUST mark it used and return its membership; of two
  concurrent consumptions, exactly one MUST succeed. Finding a token's membership MUST NOT consume it. Every token
  that is not valid for the purpose — unknown, malformed, expired, used, revoked or of another purpose — MUST give
  the same empty result.
- **Rationale:** A token is single-use, and the answer must not reveal why a token was refused.
- **Source:** #391, ADR-0002 §2

### OTT-009 — The earliest token issued for a membership since an instant, within the retention

- **Status:** Active
- **Rule:** Finding the earliest token issued for a membership since an instant MUST return the creation instant of
  the oldest token of that membership and purpose created at or after it, whatever its state, or nothing when there
  is none. It MUST consider the same tokens as the count (OTT-006), and MUST refuse a window that looks back further
  than the retention.
- **Rationale:** A refused resend tells the caller when the oldest counted send leaves the window (MEM-010). It
  must be computed over the same tokens the limit counted, and an older window would miss deleted ones.
- **Source:** #393, ADR-0003 §5

### OTT-010 — The expiry of a membership's valid token, for one membership or a whole community

- **Status:** Active
- **Rule:** Finding the expiry of a membership's token of a purpose MUST return the expiry of its valid token, or
  nothing when it has none: a used, revoked or expired token has no expiry to show. Finding them for every
  membership of a community MUST return, in one query, the expiry of each membership of that community that has a
  valid token of the purpose, and no membership of another community.
- **Rationale:** Membership responses show when the invitation link expires (MEM-013); a roster reads all of them
  at once, so its cost does not grow with the number of members.
- **Source:** #393
### OTT-011 — A platform-admin invitation has at most one active token, consumed once

- **Status:** Active
- **Rule:** Issuing a token for a platform-admin invitation MUST lock the user row, then the invitation row, and
  revoke that invitation's earlier active token and no other. Consuming a valid token issued for an invitation MUST
  mark it used and return its invitation; of two concurrent consumptions, exactly one MUST succeed, and finding a
  token's invitation MUST NOT consume it. Revoking an invitation's token MUST make its active token unusable and
  leave every other token as it is. The count of tokens issued for an invitation since an instant, and the earliest
  of them, MUST include every token of that invitation and purpose created at or after it, whatever its state, and
  MUST refuse a window that looks back further than the retention.
- **Rationale:** Each invitation has its own link and its own resend budget (ADR-0004 §3, §4), as each membership
  has (OTT-001, OTT-006).
- **Source:** #452, ADR-0004 §3

## Impossible states

- Two active tokens for one membership — prevented by OTT-001 and the `one_time_token_membership_one_active_uq`
  partial unique index.
- Two active `PASSWORD_RESET` tokens for one user — prevented by the `one_time_token_one_active_uq` partial unique
  index, which covers tokens without a membership and without a platform-admin invitation.
- Two active tokens for one platform-admin invitation — prevented by OTT-011 and the
  `one_time_token_platform_admin_invitation_one_active_uq` partial unique index.
- A token bound to both a membership and a platform-admin invitation — prevented by
  `one_time_token_single_binding_check`.
- A token for a deleted platform-admin invitation — prevented by OTT-005 (`ON DELETE CASCADE` on
  `platform_admin_invitation_id`).
- A token for a deleted membership — prevented by OTT-005 (`ON DELETE CASCADE` on `membership_id`).

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| The database does not check that a token's purpose matches whether it has a membership. | #361 decided that adding a purpose needs no migration; OTT-003 is enforced by the token services. | A writer other than the token services stores tokens. | #391 |
