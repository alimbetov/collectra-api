# PF3 OpenAPI migration decision

Status: APPROVED BY PF3 IMPLEMENTATION SPEC

## Context

PF3 (`docs/specs/platform-final-03-users.md`) explicitly closes two unsafe/unbounded platform administration contracts that already exist under `/api/v1`.

The affected APIs are platform-super-admin-only endpoints and are consumed by the bundled Collectra platform frontend in the same release.

## Intentional v1 contract changes

### `GET /api/v1/platform/administrators`

Before PF3 the endpoint returned an unbounded JSON array.

PF3 changes the response to a bounded page with `items`, `page`, `size`, `totalElements` and `totalPages`.

It also adds server-side `search`, `status`, `page`, `size` and `sort` query parameters.

Reason: the PF3 specification explicitly forbids retaining the unbounded in-memory list.

### `PATCH /api/v1/platform/administrators/{id}/status`

The request now requires both `active` and `revision`.

Reason: stale concurrent platform-administrator mutations must fail through optimistic concurrency instead of silently overwriting a newer state.

### `DELETE /api/v1/platform/administrators/{id}/role`

The command now requires the `revision` query parameter for the same optimistic-concurrency guarantee.

## Migration

The React platform console is migrated in the same change set and consumes the paged administrator response.

Callers of the previous administrator list must switch from iterating the response root array to `response.items` and must respect pagination.

Callers performing administrator status or role-removal mutations must first use the revision returned by the administrator registry and submit that revision with the command. A stale revision returns `VERSION_CONFLICT`.

## Compatibility-gate decision

These changes are deliberate requirements of PF3 rather than accidental API drift. The reviewed `/api/v1` OpenAPI baseline is therefore regenerated after the PF3 backend, frontend and integration tests are in place.

All unrelated `/api/v1` compatibility checks remain active.
