# Spec — Contact channels

| | |
| --- | --- |
| **Prefix** | `CTC` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Communities`; operations `getCommunityContact`, `setCommunityContact` |
| **Related ADRs** | — |
| **Last reviewed against code** | #462 |

## Purpose

How the members of a community can reach it: the ordered list of contact channels its community admins
publish. This spec covers who may read and replace the list and what a list may hold. The community's
legal address and the contact details a community records for each member are different concepts and
are not covered here.

## Glossary

| Term | Meaning |
| --- | --- |
| Contact channel | One way of contacting a community: a phone number, an email address or a postal address, with a label and, optionally, a schedule and notes. |
| Member of a community | A user with an active membership in it, whatever its role: accepted and enabled. A pending or disabled membership does not count. |

## Rules

### CTC-001 — Members and platform admins read the channels

- **Status:** Active
- **Rule:** A community's contact channels MUST be readable only by members of that community and by
  platform admins; any other caller MUST receive 404.
- **Rationale:** Channels may contain volunteers' personal data. Platform admins are an explicit
  exception: as the last-resort contact for users who cannot sign in, the operator needs the channels
  to route those users to their community. A 404 avoids revealing that the community exists.
- **Source:** #462

### CTC-002 — Only community admins replace the channels, as a whole

- **Status:** Active
- **Rule:** A community's contact channels MUST be writable only by community admins of that
  community, as a full replacement of the ordered list.
- **Rationale:** Contact details are a community responsibility, not a platform one.
- **Source:** #462

**Scenarios**

```gherkin
Given a member of community X who is not one of its community admins
When they replace X's contact channels
Then they receive 403 and X's channels are unchanged

Given a caller who is not a member of community X, a platform admin included
When they replace X's contact channels
Then they receive 404 and X's channels are unchanged
```

### CTC-003 — What a list of channels may hold

- **Status:** Active
- **Rule:** A community MUST NOT have more than 10 contact channels; each channel MUST have a type
  (`PHONE`, `EMAIL`, `ADDRESS`), a non-blank label and a non-blank value, and MAY have a free-text
  schedule and notes.
- **Rationale:** Keeps the page scannable on mobile; schedule and notes belong to the channel they
  describe.
- **Source:** #462

## Impossible states

- Two channels of a community in the same position — prevented by the unique constraint
  `community_contact_channels_community_id_position_uq`.
- A channel of a type other than `PHONE`, `EMAIL` or `ADDRESS` — prevented by CTC-003 and the check
  constraint `community_contact_channels_type_check`.
