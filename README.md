# MuseotekBox Tool Backend

## Vision

MuseotekBox is an RFID-based interactive museum experience platform. A physical box
(Raspberry Pi) sits in a museum; visitors scan physical objects (coloured cards,
printed images, 3D-printed replicas) with RFID tags, and the box reacts — switching
scenes, showing content, playing audio. Authors build an "Experience" as a sequence
of scenes, each with rules that say what a given scanned object should trigger. This
repository is the **backend only**: the authoring API that lets authors build and
manage that content, plus the runtime data the physical box reads. It has no UI of
its own — a separate frontend (Vue) is the authoring client.

## Platform integration: Catalogue

This backend is a pure OAuth2 resource server on the **UNICHE** platform — it does
not implement its own authentication or own organisation/authorization data. Two
platform pieces it depends on:

- **Keycloak** issues the JWTs every request carries.
- **Catalogue** is the platform's service of record for organisations, projects, and
  authorization — every tool on the platform (this one included) defers to Catalogue
  rather than keeping its own copy of that data.

Concretely: this backend validates the JWT (audience/issuer), then for anything
about organisations/projects/authorization it calls out to Catalogue live rather than
storing its own version — except where it has to keep a local mirror, which is what
JIT below is for.

## JIT (just-in-time) patterns

Two independent JIT mechanisms recur through this codebase. Neither uses an explicit
registration/import step — both materialise local state lazily, the first time it's
needed, from data that already exists elsewhere:

1. **JIT user provisioning** (`infrastructure/security`) — every authenticated
   request auto-creates/updates a local `User` row keyed by the JWT subject. There's
   no separate "sign up" step; identity is derived from the token on demand.
2. **Companion-row sync**, a.k.a. "lazy-JIT create-up" (`application/<feature>`) —
   used when some local entity needs a real foreign key into a resource that
   Catalogue actually owns (JPA can't FK across services, so a local mirror row has
   to exist). The mirror is kept honest by: upserting it right after every write
   Catalogue confirms, upserting it opportunistically on read too, and soft-deleting
   it if a read comes back 404 — that last case is the platform's only reconciliation
   signal today, since there's no background sweep yet for resources deleted through
   some other client.

## Request procedure

Roughly how one request flows through the system. (JWTs are obtained by the client
directly from Keycloak via Authorization Code + PKCE — this backend is never
involved in login and has no session of its own.)

1. JWT arrives at the resource server and is validated (`infrastructure/security`).
2. The JIT user-provisioning filter provisions/refreshes the local `User` row from
   the JWT subject.
3. A controller (`web/<feature>`) parses the request into its own DTO and delegates
   to an `application/<feature>` use case or query — it never talks to Catalogue or
   a repository directly.
4. That class calls `infrastructure/catalogue` for anything Catalogue owns, and/or
   `infrastructure/repository` for anything fully local.
5. If the feature keeps a companion row, the same class also invokes its
   companion-sync service so the local mirror stays consistent with whatever
   Catalogue just confirmed.
6. The controller maps the result to its own response DTO. Exceptions are mapped to
   HTTP responses centrally, not per-controller.

## Package structure

Layout is `domain` → `application` → `web` / `infrastructure`, subdivided by feature
within each layer — the same feature name can appear under multiple layers (e.g. under
both `domain` and `application`), since each layer represents a different facet of that
feature (its data shape, its business logic, its HTTP surface), not a different concept.

### `domain/`
JPA entities only. No Spring service logic, no HTTP awareness, no knowledge of
Catalogue. One subpackage per entity/feature.

### `application/`
Business/orchestration logic that ties `domain` and `infrastructure` together for
`web` to call. Never talks HTTP directly — that's what keeps `web` from having to
import Catalogue/repository code straight from a controller. One subpackage per
feature. Naming conventions:

- `*Query` — a read operation
- `*UseCase` — a write operation
- `*PassthroughQuery` — a read that does not touch any local state. Use this suffix
  when a package's other classes *do* maintain local state (e.g. a companion row,
  see JIT above) so the exception is visible without opening the file.

Companion-row sync logic (see JIT above) lives in a dedicated service in the owning
feature's `application/` subpackage, called from every use case/query that touches
that resource — it's a per-feature exception, not what every `application/`
subpackage does. A feature with nothing to sync (no local mirror needed) stays as
thin passthroughs.

### `web/`
The HTTP layer. One subpackage per feature, holding that feature's controller *and*
its own request/response DTOs — deliberately separate records from any external
system's DTOs, even where the shape currently matches, so this service's public API
contract doesn't shift just because an upstream one does. A shared subpackage (e.g.
`error/`) holds cross-cutting concerns like the global exception-to-HTTP mapping.

### `infrastructure/`
Adapters to the outside world, plus framework wiring. A subpackage appears here when
there's a *cluster* of related files that only make sense together — a whole external
integration (client + DTOs + exceptions), or a cohesive technical concern like
security config. It's not one folder per entity: a single repository interface has no
natural companion files, so all JPA repositories sit flat in one `repository/`
package regardless of how many entities exist.

- `catalogue/` (or equivalent per external system) — REST client + that system's own
  wire DTOs and exceptions.
- `repository/` — Spring Data JPA repositories, one flat file per entity.
- `security/` — auth/resource-server config, plus JIT user provisioning (see above).
- `config/` — general Spring bean wiring.

## Adding a new entity/feature

- Always needed: `domain/<feature>/` for the entity, `infrastructure/repository/` for
  its `JpaRepository`.
- If it needs its own REST endpoints: `web/<feature>/` (controller + request/response
  DTOs) and `application/<feature>/` (use cases/queries calling the repository).
- Only if it's actually an externally-owned resource being locally mirrored: add a
  companion-sync service in `application/<feature>/`, plus a DTO/client method in the
  relevant `infrastructure/<external-system>/` package. Don't add this for purely
  local entities — there's nothing external to sync against.
