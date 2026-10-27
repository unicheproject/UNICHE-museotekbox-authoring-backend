Based on mobics-guidelines (no tag yet, commit 1854078)

# MuseotekBox Tool Backend: Project Source of Truth

The authoring API of MuseotekBox, an RFID museum experience platform on UNICHE: curators build experiences (scenes,
blocks, rules) that a physical box plays. If the code, the chat history or other documents disagree with this file, fix
the disagreement here first (or ask), then change the code. A CLAUDE.md in a sub-folder adds rules for that folder and
must not contradict this file. Status as of 2026-10-23: development.

## 1. Identity
- Stack: Java 21, Spring Boot 3.4.7, Maven (wrapper pinned), PostgreSQL 16 (Docker image `postgres:16.6-bookworm`)
- Base package: `com.museotek.box`, layered then by feature: `domain` → `application` → `web` / `infrastructure`.
  `README.md` explains the layers, the Catalogue integration, JIT provisioning and companion rows. Read it first.
- Frontend repo: `UNICHE-museotekbox-authoring-frontend` (Vue)
- UI language: Greek and English (the frontend's). Code, identifiers, commits, DB names and API error messages are English.
- Auth: OAuth2 resource server (Keycloak JWT). Organisations, projects and permissions belong to Catalogue and are
  checked live through it; this backend keeps only its own data plus companion rows.

## 2. Who does what
- **The assistant** writes code and tests, runs the tests, does the review in section 3 and drafts the commit message.
  It never commits, never pushes and never creates branches unless the owner asks for that commit. It never connects
  to servers: the owner runs remote commands and pastes the output.
- **The owner** reviews the code, commits (often amending or folding), pushes, and decides open design questions.
- **Agree before writing:** a new pattern (convention, naming, a new kind of class) is agreed first, written into
  section 7 or 8, and every later piece follows it. A feature that touches DB + API + frontend starts with a short
  proposal in `docs/proposal-<topic>.md` (format: `docs/decision-template.md`).
- **Attribution:** no Co-Authored-By trailer in drafted commit messages.

## 3. Working cycle
1. **Plan:** agree scope, files, tests, open decisions and the commit split before any code.
2. **Code** in small pieces, each one a commit that compiles, passes and can be tried in Postman.
3. **Test:** `./mvnw spotless:apply`, then `./mvnw test` is green, with tests that prove the new behaviour.
4. **Security review** when the change touches anything on the trigger list in section 6.
5. **Review** with the checklist below. Fix what is cheap, discuss the rest, report it to the owner.
6. **Hand over:** list the changed files, the test count and a drafted commit message.

Review checklist:
1. Can the class be tested without Spring or a database? If not, why?
2. Can invalid state be built (a wrong pair of columns, swappable same-typed arguments, a half-changed object)?
3. Do names read like the domain, and does every public method have a caller?
4. Is each rule in the layer that owns it (entity: own consistency, use case: business rules, controller: input shape)?
5. Do the tests prove behaviour and rejections, in the same style as the existing ones?
6. Does anything copy a weaker pattern that later pieces would inherit?
7. Did anything on the security trigger list change, and was the checklist run?

## 4. Git
- Remote: GitHub (`unicheproject/UNICHE-museotekbox-authoring-backend`). This project: direct pushes to `master` by
  the owner, no merge requests yet. `master` always builds and passes its tests.
- Commit messages: short lowercase past tense, e.g. `added comment delete endpoints`, with a short body for the why
  when it isn't obvious. One purpose per commit.
- Never force-push shared branches. Never commit secrets, IDE files, `target/`, `uploads/`, `.env*` (except
  `.env.example`) or local config. Commit lockfiles and wrappers.
- Files in `docs/` are not maintained or mentioned in commits unless the owner asks. The exception is
  `docs/error-codes.md`, which changes with every new error code. `docs/proposal-*.md` are audit
  documents: write them, don't commit them unless asked.

## 5. Configuration and secrets
- `application.properties` is committed and lists every setting as `${ENV_VAR}`. Local values live in the
  git-ignored `.env` (template: `.env.example`). Never read or print `.env`.
- Credentials, tokens, hostnames and IPs never go into Git, into this file or into any document.

## 6. Security and personal data
**Baseline:** OWASP Top 10. Authorization is enforced on the server and denies by default. Queries are parameterised,
input is validated at the API boundary, dependencies are kept current. The API is stateless (JWT, no session, no CSRF
token needed).

**Access checks (mandatory):**
- Anything scoped by a project calls `ProjectAccessGuard.requireAccess(projectId)` first, even when its data is local.
- Anything scoped by an organisation calls `OrgAccessGuard.requireAccess(orgId)` (read) or `requireManager(orgId)`
  (manager-only writes) first.
- Skipping either is a cross-tenant bypass: nothing else checks it. Every role rule has a test.

**Personal data (GDPR):** we store the JWT subject, email, username, display name and first/last seen
time of users (`users`), the author's subject and name on comments (`comments.author_subject`, `author_name`), and the
subject of whoever resolved a comment, uploaded a media file or created a template (`comments.resolved_by`,
`media.uploaded_by`, `experience_templates.created_by`). Export and erase: `scripts/gdpr/` (update it when a column like
these is added).
No personal data in logs, URLs or error messages; no production data in development or tests. Say so whenever a change
adds personal data or a new purpose. This is engineering practice, not legal advice.

**Triggers for a security review:** authentication, roles and access guards, input validation, file handling (media
uploads), queries, secrets or configuration, logging, personal data, dependencies or the build, the API contract. Then
go through: every endpoint authenticated unless explicitly public; every role rule covered by a test; error responses
leak nothing (no SQL, stack traces, exception messages, rejected values); logs free of secrets and personal data;
uploads limited by size, type and content (magic bytes); deliberate CORS. Report the result to the owner.

## 7. Architecture and Java conventions
- **Layers:** controller (`web/<feature>`, HTTP only, own request/response records) → `application/<feature>`
  (`*Query` = read, `*UseCase` = write, `@Transactional`) → repository (`infrastructure/repository`, flat). Entities
  never leave the API. Controllers never call Catalogue or a repository directly.
- **Time:** always `Instant.now(clock)` with the injected `Clock` bean (`ClockConfig`), never `Instant.now()`.
  Tests pass `Clock.fixed(...)`.
- **Style:** constructor injection, one short Javadoc line per class, explicit `if` null checks, comments only where the why is not obvious.
- **Lombok:** entities only (`@Getter`, `@Setter`, `@NoArgsConstructor`). Nowhere else.
- **Entities:** explicit `@Column(name, nullable, length)` matching the SQL, lazy `@ManyToOne` only, no collections
  "just in case", no business rules and no equals/hashCode.
- **Errors:** one `GlobalExceptionHandler` (`web/error`) maps every exception to `ErrorEnvelope(code, message, details,
  requestId)` with a stable `code` (e.g. `TEMPLATE_NOT_FOUND`). Each feature throws its own domain exception; nothing
  builds an error body itself. Unexpected errors return 500 `INTERNAL_ERROR`, logged with the request id. Never leak
  SQL, stack traces or exception messages. A new `code` is added to `docs/error-codes.md`
  in the same commit. See section 12 about RFC 9457.
- **Validation:** every column length has a matching check at the API boundary (a `*Rules`/`*Names` helper in the
  feature's `application` package). The database error is a last line of defence, never the user feedback.
- **Logging:** SLF4J, levels with context, no personal data and no secrets, expected user errors not at ERROR.

## 8. Database
- Schema only through Liquibase (`ddl-auto=validate`). Changesets are formatted SQL in
  `db/changelog/sql/NN-<topic>.sql`, included from `db.changelog-master.xml`, ids `museotekbox:NNNN-verb-noun`
  (running number), each with an explicit `--rollback`. **A merged changeset is never edited**: a fix is a new one.
- Named constraints `uk_`, `fk_`, `ix_` + table + column (existing prefix `uk_` kept for unique). UUID primary keys.
  `TIMESTAMP(6) WITH TIME ZONE` for events, `created_at` / `updated_at` on every table.
- The database enforces what must always hold (unique, foreign keys). The application mirrors those rules to give
  friendly messages. JSON documents that the frontend owns go in `JSONB` and are checked only for shape and size.

## 9. Testing
- Test what matters: rules, rejections, access checks, conflicts. No tests for getters or framework plumbing.
- `<Class>Test`, method names are behaviour sentences (`theOrgsOwnTemplate_isDeleted`), one behaviour per test,
  AssertJ, test package mirrors main.
- Use cases: JUnit + Mockito, mocking repositories and the access guards.
- Controllers: MockMvc `standaloneSetup` with `GlobalExceptionHandler`, use cases mocked.
- Queries and mappings: `@DataJpaTest` on Testcontainers Postgres with `@ActiveProfiles("test")`, never an in-memory
  database.
- Count tests after `rm -rf target/surefire-reports`, or stale reports are counted twice.

## 10. Commands
```
docker compose up -d                     # local Postgres (needs .env)
./mvnw spring-boot:run                   # app, with the .env variables set (or run from IntelliJ)
./mvnw test                              # all tests (Docker must be running for Testcontainers)
./mvnw -Dtest=Class#method test          # one test
./mvnw package                           # build
./mvnw spotless:apply                    # sort imports, drop unused ones, trim whitespace (CI runs spotless:check)
```

## 11. Documents
Only documents marked living are trusted; a snapshot must be checked against the code. If a document and this file
disagree, this file wins and the document is fixed.
- `README.md` (living): what the system is, Catalogue integration, package structure, adding a feature
- `docs/architecture-and-classes.md`, `docs/security-and-errors.md`, `docs/dependencies-and-config.md` (snapshot)
- `docs/error-codes.md` (living): every error `code`, its status and when it happens. Update it with each new code
- `docs/experience-api-flow.md`, `docs/frontend-*.md` (snapshot): what the frontend needs to know
- `docs/proposal-*.md` (snapshot): why a feature was built the way it was

## 12. Decisions and open points
**Decisions (confirmed by the owner)**, one line each, newest last: `<YYYY-MM-DD> <decision>. Why: <reason>.`
- 2026-10-14 Save (`PUT /projects/{id}/experience`) and publish (`PUT /boxes/{boxId}/projects/{projectId}`) stay
  separate endpoints. Why: different permissions, If-Match on save only, many boxes per experience.
- 2026-10-14 The visitor picks the experience on a box by scanning its start card (model A). Why: no staff step on site.
- 2026-10-21 Experience templates are "half": types are a Java enum and the compiler stays in the frontend; presets
  are rows (global via Liquibase only, the org's own via manager CRUD). Why: a new type always needs frontend code.

**Kept as is from the guidelines audit (2026-10-23, do not "fix" in passing):**
- Error body stays `ErrorEnvelope`, not RFC 9457 problem details. Why: the format changes together with the frontend
  (frontend reads both first, then the backend switches); not started.
- Liquibase changesets are SQL files, not `NNN-verb-noun.yaml`; unique prefix stays `uk_`. Why: consistency with 01-07.
- Primary keys are mixed: `BIGINT` identity on the tables of changesets 01-03 (boxes, scan objects, scenes, blocks,
  rules, users), `UUID` from 04 on (media, comments, templates), Catalogue's UUID for `projects`. New tables use
  `UUID`. Why: changing existing keys would break every URL and foreign key; access is checked per org either way.
- No `CHECK` on enum columns, also for new tables. Why: trigger, block and template types grow often, and a `CHECK`
  would make each new value a migration; Hibernate maps the enum by name and the API rejects unknown values.
- Queries are not `@Transactional(readOnly = true)`. Why: almost every query calls an access guard first, which is an
  HTTP call to Catalogue; a transaction around it would hold a database connection for that whole call.
- Existing entities keep class-level Lombok `@Setter` and their current timestamp columns. New or reworked entities
  follow section 7 (required-field constructor, setters only for fields that change) and get `created_at` /
  `updated_at`. Why: MIGRATION.md, no refactor only for the guidelines.
- Use-case tests mock our own repositories and guards. Why: they test rules and access checks without a database;
  queries are covered by `@DataJpaTest`.
- The JWT subject may appear in logs (e.g. JIT provisioning failures), never in responses. Why: it is a pseudonymous
  id and the only way to trace a user's error; names and emails stay out of logs.
- Dependency vulnerabilities: GitHub Dependabot (`.github/dependabot.yml`); alerts are switched on in the repo settings.

**Open points: do not guess, ask.**
- GDPR export/erase: SQL scripts in `scripts/gdpr/` (drafted 2026-10-26). Still waiting on the platform team: is erasure
  coordinated across UNICHE tools, and should comment bodies be kept once the author is anonymised?
- Static analysis is not configured. Secret detection runs in CI (gitleaks, `secrets` job in `ci.yml`).
- Should platform admins be able to edit or delete global templates?
- Box device API (box auth, downloading published experiences and media, rule evaluation order) is not designed yet.

## 13. Known gotchas (verified in code, dated)
- 2026-10-14 `@DataJpaTest` needs `@ActiveProfiles("test")`, otherwise it does not pick up the Testcontainers setup.
- 2026-10-14 Testcontainers 1.20.x fails on this machine's Docker Engine (native in WSL2); 1.21.4+ works.
- 2026-10-14 `web/box/BoxController.java` can show as modified with only CRLF changes; the repo is LF, restore it.
- 2026-10-22 Global templates have `org_id` NULL; update and delete of one returns 403 `TEMPLATE_NOT_EDITABLE` on purpose.
