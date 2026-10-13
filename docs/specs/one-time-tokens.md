# Spec — One-time tokens

| | |
| --- | --- |
| **Prefix** | `OTT` |
| **Status** | Active |
| **Contract** | None: the token store has no operation of its own. Password recovery (`requestPasswordReset`, `resetPassword`) uses it. |
| **Related ADRs** | ADR-0002 (`docs/architecture/adr/0002-recover-passwords-with-single-use-hashed-tokens-emailed-after-commit.md`), ADR-0003 (`docs/architecture/adr/0003-separate-person-accounts-from-community-member-records.md`) |
| **Last reviewed against code** | #391 |

## Purpose

One-time tokens are secret, single-use, expiring tokens issued for one purpose: a password reset for a user, or an
invitation for a membership. This spec covers tokens bound to a membership and how they live alongside the tokens
bound only to a user; the rest of the capability (hashing, single use, cleanup, from #361) is specified lazily, as
issues touch it.

## Glossary

| Term | Meaning |
| --- | --- |
| Purpose | What a token is issued for. `PASSWORD_RESET` is bound only to a user; `INVITATION` is bound to a membership. |
| Active token | A token that is neither used nor revoked. It may have expired. |
| Valid token | An active token whose expiry is after the current instant. |
| User-scoped operation | Issuing, finding the owner of, consuming or counting a token by user. |
| Membership-scoped operation | Issuing, finding the membership of, consuming, counting or revoking a token by membership. |

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
- **Rule:** An `INVITATION` token MUST be accepted until 7 days after it was issued, and refused from that instant
  on.
- **Rationale:** Members read email rarely; the link must survive a week. Expiry removes only the token: the
  account and the pending membership remain, and can be invited again.
- **Source:** #391, ADR-0003 §4

### OTT-003 — A purpose is used only with the operations of its scope

- **Status:** Active
- **Rule:** A user-scoped operation MUST refuse a purpose bound to a membership, and a membership-scoped operation
  MUST refuse a purpose bound only to a user, before it reads or writes any token.
- **Rationale:** A `PASSWORD_RESET` token issued for a membership, or an `INVITATION` token without one, would
  escape the invariant of its purpose. The scope is a property of the purpose, so adding a purpose needs no
  migration.
- **Source:** #391

### OTT-004 — Locks are taken user row, then membership row, then token rows

- **Status:** Active
- **Rule:** Every flow that touches a user, a membership and tokens MUST lock in the order user row, membership
  row, token rows. A flow that consumes a membership's token and then writes the user or the membership MUST find
  the token's membership without locking, lock the user, then the membership, then consume, and continue only if
  the consumed token belongs to the same membership.
- **Rationale:** Issuing and revoking take the locks in that order; a flow that takes them in another order can
  deadlock against them.
- **Source:** #391, ADR-0002 §3, ADR-0003 §4

### OTT-005 — A membership's tokens go with the membership

- **Status:** Active
- **Rule:** Deleting a membership MUST delete its tokens, and no other. Deleting a user MUST delete every token of
  that user.
- **Rationale:** A token for a membership that no longer exists must not remain usable.
- **Source:** #391, ADR-0003 §4

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

## Impossible states

- Two active tokens for one membership — prevented by OTT-001 and the `one_time_token_membership_one_active_uq`
  partial unique index.
- Two active `PASSWORD_RESET` tokens for one user — prevented by the `one_time_token_one_active_uq` partial unique
  index, which covers tokens without a membership.
- A token for a deleted membership — prevented by OTT-005 (`ON DELETE CASCADE` on `membership_id`).

## Accepted exceptions

| Exception | Reason | Expires when | Source |
| --- | --- | --- | --- |
| The database does not check that a token's purpose matches whether it has a membership. | #361 decided that adding a purpose needs no migration; OTT-003 is enforced by the token services. | A writer other than the token services stores tokens. | #391 |
