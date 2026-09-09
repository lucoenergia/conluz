# [conluz-XXX] Move Huawei config endpoints to follow plant sub-resource URL pattern

## Problem

The Huawei configuration endpoints use a flat URL that does not match any established
pattern in the codebase:

| Method | Current URL |
|--------|-------------|
| GET    | `/api/v1/production/huawei/config/{plantId}` |
| PUT    | `/api/v1/production/huawei/config/{plantId}` |

This path is anomalous:
- It lives under `/api/v1/production/`, which is normally reserved for **community-scoped**
  production queries (e.g. `/api/v1/communities/{communityId}/production/hourly`).
- Plant sub-resources elsewhere consistently use `/api/v1/plants/{plantId}/...` — for example,
  sharing agreements are at `/api/v1/plants/{plantId}/sharing-agreements`.

## Proposed change

Move to plant sub-resource pattern with a `production` segment, consistent with the rest
of the API:

| Method | New URL |
|--------|---------|
| GET    | `/api/v1/plants/{plantId}/production/huawei/config` |
| PUT    | `/api/v1/plants/{plantId}/production/huawei/config` |

## Breaking change

This is a **breaking API change**. All clients (frontend, integrations) referencing the old
URLs must be updated.

## Implementation plan

### 1. Controllers

- **`GetHuaweiConfigController.java`**
  - Change `@RequestMapping` value from
    `/api/v1/production/huawei/config` to
    `/api/v1/plants/{plantId}/production/huawei/config`
  - Change `@GetMapping("/{plantId}")` to `@GetMapping` (path is now class-level)

- **`SetHuaweiConfigController.java`**
  - Same `@RequestMapping` value change
  - Change `@PutMapping("/{plantId}")` to `@PutMapping`

### 2. Tests

- **`GetHuaweiConfigControllerTest.java`**
  - Update `URL_TEMPLATE` from `/api/v1/production/huawei/config/%s`
    to `/api/v1/plants/%s/production/huawei/config`

- **`SetHuaweiConfigControllerTest.java`** (file named `SetDatadisConfigControllerTest.java`)
  - Same `URL_TEMPLATE` update

### 3. Error messages

- **`messages.properties`** — update the `error.huawei.disabled` message URL
- **`messages_es.properties`** — same update for the Spanish translation

## Verification

1. `./gradlew test` — all tests pass
2. `./gradlew build` — full build succeeds
3. Swagger UI shows updated paths at `/api-docs/swagger-ui/index.html`

## Client-side follow-up

The frontend/client repository needs a separate update to all references to the old
Huawei config endpoint URLs.
