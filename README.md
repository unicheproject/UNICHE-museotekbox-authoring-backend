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

### What lives where

Catalogue holds only platform-level data: the concepts every tool shares (orgs,
projects, people, permissions). It knows nothing about boxes, scenes or scan objects.
Everything specific to MuseotekBox lives only in our database.

| Data | Catalogue | Our DB |
|---|---|---|
| Organisations, managers | yes | — (our rows store only the org's UUID) |
| Projects: name, status, members, invitations | yes | only the companion row: id, a copy of the name, the experience's version and key counters (see "Project vs Experience") |
| Boxes | — | `boxes` |
| Which experiences are on which box | — | `box_projects` |
| Experience content: scenes, blocks, rules | — | `scenes`, `blocks`, `rules` |
| Scan objects and their types | — | `scan_objects` (+ one table per kind: `coloured_cards`, `printed_images`, `three_d_printed_objects`, `drafts`), `scan_object_types` |
| Users | its own copy | its own copy (`users`, created from the token, see JIT) |

What this means in practice:

- **Catalogue down:** our data is still there, but the API refuses requests with
  503/504. Every endpoint checks permission through Catalogue first, even the ones
  whose data is purely local, and we refuse rather than guess.
- **Backups:** boxes, experience content and scan objects exist only in our
  database. Backing up our database is what protects them. Catalogue can't restore
  any of it.
- **Linked by UUID only:** our rows point to Catalogue with plain UUIDs (`orgId` on
  boxes and scan objects, and the project id through the companion row). There is no
  database-level link between the two systems. If a project is deleted in Catalogue,
  its scenes, blocks, rules and box assignments stay in our database. Only the
  companion row gets marked deleted, the next time someone opens it.

## Project vs Experience — one thing, two jobs

**A project and an experience are the same thing.** "Project" is the UNICHE platform's
word: Catalogue, the Portal and every tool use it. "Experience" is MuseotekBox's word
for what a curator builds inside a project: its scenes, blocks and rules. The frontend
and the user stories say "experience" throughout. The API and the code say "project"
wherever the platform is involved.

There is one project per experience, with one id. That id is Catalogue's project UUID,
and it is used everywhere: in Catalogue, in our `projects` table, and in every URL.

### Two jobs, two sets of endpoints

The same project is handled by two separate parts of the code, because they do
different jobs:

| | The project as an item | The experience content |
|---|---|---|
| What it is | The item in a list: name, status, dates, members, which boxes it is on | What's inside it: scenes, blocks, rules |
| Endpoints | `GET/POST /organisations/{orgId}/projects`, `GET/PATCH/DELETE /projects/{id}`, `POST /projects/{id}/restore`, `GET /projects/{id}/members`, `/organisations/{orgId}/boxes/{boxId}/projects` | `GET/PUT /projects/{id}/experience` |
| Code | `web/project`, `application/project`, `web/box` + `application/box` for box assignment | `web/experience`, `application/experience` |
| Where the data lives | Catalogue (we forward the call) | Our database only |
| Main concern | Forwarding to Catalogue and handling its errors | Validating the scene/block/rule graph and saving it safely (version check, 409 on conflict) |

Rule of thumb: **anything that treats the experience as one item goes through
`/projects`. Anything that opens it up and edits scenes, blocks or rules goes through
`/projects/{id}/experience`.** The experience URL sits under the project's id on
purpose. It isn't a separate resource, it's the project's content.

How to read `/projects/{id}/experience`:

- **It is singular.** Every project has exactly one experience. There is no
  `/experiences` collection, no experience id, and no way to create a second one.
  Compare `/boxes/{boxId}/projects`, which is plural because a box can hold many
  projects.
- **"Experience" here means the content.** In the frontend, "experience" means the
  whole thing a curator works on. In this URL it means only what's inside the project:
  its scenes, blocks and rules. The experience comes into existence with its project
  (`POST /organisations/{orgId}/projects`, starting empty) and follows it on delete
  and restore. `DELETE /projects/{id}` is a soft delete: the project disappears from
  lists and the experience can no longer be opened, but its scene/block/rule rows stay
  in our database. `POST /projects/{id}/restore` brings both back.
- **It does not mean "a project contains experiences."** It means "this project's
  content", one to one.

Typical frontend flow:

1. The experiences list page calls `GET /organisations/{orgId}/projects`.
2. "New experience" calls `POST /organisations/{orgId}/projects`. The project starts
   with empty content.
3. Opening the editor calls `GET /projects/{id}/experience`, which returns the scenes,
   blocks, rules and a version.
4. Save calls `PUT /projects/{id}/experience` with `If-Match` set to that version. A
   409 means someone else saved first.
5. Renaming it from the list calls `PATCH /projects/{id}`. This never touches the
   content.

### Two tables, different columns

The project is stored in two databases. The two rows share an id but hold mostly
different data. Each side stores only what its own service needs:

| Field | Catalogue `projects` | Our `projects` (`domain/project/Project`) |
|---|---|---|
| `id` | yes | yes, the same UUID |
| `orgId`, `name` | yes | yes, a copy |
| `slug`, `status`, tool, created/updated dates | yes | — |
| members, invitations | yes (own tables) | — |
| `deletedAt` | yes, the real delete | yes, a local mirror of it |
| `docVersion`, `nextSceneSeq`/`nextBlockSeq`/`nextRuleSeq` | — | yes, experience-only |

Catalogue owns the project as a platform item. Our row, called the **companion row**,
exists for two reasons:

- Our own tables need something to point to with a foreign key: `scenes`,
  `box_projects` and `boxes.current_project_id` all reference it.
- It holds the experience's own bookkeeping: the document version used for the save
  conflict check, and the counters used to hand out new scene/block/rule keys.

The companion row is created and kept up to date automatically (see JIT below): every
time a project is opened through `ProjectAccessGuard`. The copied `name` can briefly be
out of date after a rename in the Portal. That is harmless, because every list and
details call reads the name live from Catalogue, never from our copy.

There is no `Experience` entity. The experience is the `Scene`, `Block` and `Rule` rows
that point to a `Project`.

### Which DTO describes what

| Part | Coming in | Going out to the frontend |
|---|---|---|
| Project as an item | `CatalogueProjectDto` (Catalogue's answer) | `ProjectResponse` |
| Experience content | `ExperienceWriteRequest` → `ExperienceDocument` (save) | `ExperienceView` → `ExperienceResponse` (load) |

No field appears in both `ProjectResponse` and `ExperienceResponse`. They describe
different parts of the same project.

### Who sees what

Both parts follow the same access rule, and Catalogue decides it (nothing local does):

- **Lists**, of the org's projects or a box's projects: a manager or platform admin gets
  every project in the org, an author only the ones they were invited to.
- **One project**, its details or its experience content: `ProjectAccessGuard` asks
  Catalogue whether the caller can access that project. If not, it returns Catalogue's
  403 or 404.

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

`projectaccess/` is a shared, cross-cutting `application/` subpackage — the
`application/`-layer equivalent of `web/error/` below — holding `ProjectAccessGuard`,
the mandatory entry point for any project-scoped operation. Any `application/<feature>`
class whose data is scoped by `projectId` must call
`ProjectAccessGuard.requireAccess(projectId)` before reading or writing that data, even
if the feature's own storage is 100% local with no direct Catalogue call of its own —
this is what stands between a local repository query and a cross-tenant authorization
bypass, since project-level authorization is Catalogue's responsibility, not this
backend's.

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
- If the new entity is scoped by `projectId` (i.e. it belongs to a project, the way
  `Box` already does): every `application/<feature>` use case/query that reads or
  writes it must call `application/projectaccess/ProjectAccessGuard.requireAccess
  (projectId)` as its first step, even though entities like `Box` never call
  Catalogue directly themselves. Skipping this is a real cross-tenant authorization
  bypass, not a style nit — nothing else in the request path checks project-level
  authorization for local-only data.
