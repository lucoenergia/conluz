# Spec — Contact channels

| | |
| --- | --- |
| **Prefix** | `CTC` |
| **Status** | Active |
| **Contract** | OpenAPI snapshot → tag `Communities`; operations `getCommunityContact`, `setCommunityContact`; tag `Info`; operation `getOperatorContact` |
| **Related ADRs** | — |
| **Last reviewed against code** | #463 |

## Purpose

How the members of a community can reach it: the ordered list of contact channels its community admins
publish; and, for whoever has no community to turn to, the contact of the organisation operating the instance.
This spec covers who may read and replace the list, what a list may hold, and how the operator contact is
configured and published. The community's
legal address and the contact details a community records for each member are different concepts and
are not covered here.

## Glossary

| Term | Meaning |
| --- | --- |
| Contact channel | One way of contacting a community: a phone number, an email address or a postal address, with a label and, optionally, a schedule and notes. |
| Member of a community | A user with an active membership in it, whatever its role: accepted and enabled. A pending or disabled membership does not count. |
| Operator contact | How to reach the organisation operating the instance: a name and an email address, and optionally a phone number, a schedule and notes. Set per deployment, not per community. |

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

### CTC-004 — The operator contact is public instance configuration

- **Status:** Active
- **Rule:** The operator contact MUST be instance configuration exposed by an unauthenticated endpoint, which MUST
  return an explicit null contact when it is not configured.
- **Rationale:** Signed-out users have no community context; third-party deployments may not configure an operator.
- **Source:** #463

**Scenarios**

```gherkin
Given an instance that configures no operator contact
When anyone reads the operator contact, without signing in
Then they receive 200 with a null contact

Given an instance that configures an operator contact
When anyone reads it with an expired or malformed token
Then they receive 200 with the contact: the token is ignored
```

### CTC-005 — A partial operator contact prevents startup

- **Status:** Active
- **Rule:** A partially configured operator contact (missing name or email, or invalid values) MUST prevent the
  application from starting.
- **Rationale:** A silently broken fallback would go unnoticed in third-party deployments.
- **Source:** #463

**Scenarios**

```gherkin
Given an operator contact email set without a name
When the application starts
Then it fails, naming the missing variable

Given every operator contact variable unset or blank
When the application starts
Then it starts with no operator contact
```

## Impossible states

- Two channels of a community in the same position — prevented by the unique constraint
  `community_contact_channels_community_id_position_uq`.
- A channel of a type other than `PHONE`, `EMAIL` or `ADDRESS` — prevented by CTC-003 and the check
  constraint `community_contact_channels_type_check`.
