# Architecture Q&A

## 1. Dependencies

Όλες οι εκδόσεις (εκτός από `springdoc.version`) διαχειρίζονται από το BOM του
`spring-boot-starter-parent:3.4.7`.

**Version/upgrade policy:** αναβάθμιση του `spring-boot-starter-parent` γίνεται ως ένα
ενιαίο commit (bump στο `<parent><version>`), μετά τρέχει όλο το test suite· minor/major
bumps ελέγχονται πρώτα στα release notes για breaking changes. Το `springdoc.version`
αναβαθμίζεται ξεχωριστά, με έλεγχο του συμβατότητας πίνακα springdoc↔Spring Boot (βλ.
https://springdoc.org compatibility matrix) γιατί δεν ακολουθεί αυτόματα το Spring Boot BOM.

| Dependency | Τι εξυπηρετεί | Πού χρησιμοποιείται | Γιατί αυτό αντί εναλλακτικού                                                                                                                                                                                                         | Απαραίτητο τώρα ή μελλοντικό; |
|---|---|---|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---|
| `spring-boot-starter-web` | REST API πάνω σε embedded Tomcat + Jackson (JSON) | Όλα τα `web/*Controller` | Πυρήνας του project — resource server API, όχι κάτι εναλλακτικό                                                                                                                                                                      | Απαραίτητο τώρα |
| `spring-boot-starter-data-jpa` | JPA/Hibernate + Spring Data repositories | Όλα τα `domain/*` entities, `infrastructure/repository/*` | Τυπική επιλογή για relational persistence με Spring                                                                                                                                                                                  | Απαραίτητο τώρα |
| `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server` | OAuth2 resource-server: JWT decoding/validation, security filter chain | `infrastructure/security/SecurityConfig`, `AudienceValidator` | Αυτό το backend **δεν κάνει δικό του login** — είναι καθαρός resource server πάνω στο κοινό Keycloak του UNICHE. Αυτά τα δύο starters είναι η επίσημη Spring λύση για επικύρωση ενός Bearer JWT   | Απαραίτητο τώρα |
| `spring-boot-starter-validation` | Bean Validation (`@NotBlank` κ.λπ.) πάνω στα request DTOs | `web/project/CreateProjectRequest`, `UpdateProjectRequest` | Standard τρόπος να μπει server-side validation χωρίς χειρωνακτικά if/throw σε κάθε controller                                                                                                                                        | Απαραίτητο τώρα |
| `spring-boot-starter-actuator` | `/actuator/health`, `/actuator/info` | Permit-all endpoint στο `SecurityConfig` (health checks) | Χρειάζεται για liveness/readiness probes σε deployment (k8s/Docker)                                                                                                                                                                  | Απαραίτητο τώρα (ops) |
| `org.postgresql:postgresql` | JDBC driver | `spring.datasource.*` | Βλ. ενότητα 2 παρακάτω ("Γιατί PostgreSQL")                                                                                                                                                                                          | Απαραίτητο τώρα |
| `lombok` | Παράγει getters/setters/no-args constructor στα entities | Όλα τα `domain/*` entities (`@Getter @Setter @NoArgsConstructor`) | Μειώνει boilerplate σε JPA entities | Απαραίτητο τώρα, αλλά μόνο σε compile-time, ποτέ runtime |
| `springdoc-openapi-starter-webmvc-ui` | Αυτόματο OpenAPI spec + Swagger UI (`/api-docs`, `/swagger-ui.html`) | Καμία άμεση αναφορά στον κώδικα — δουλεύει reflectively πάνω στα `@RestController`/DTOs | Δίνει στο frontend team (και σε οποιονδήποτε manual tester) ζωντανό API contract χωρίς να τον γράφουμε με το χέρι                                                                                                                    | Απαραίτητο τώρα, θα παραμείνει — αλλά **ανοιχτό θέμα**: αυτά τα endpoints είναι `permitAll()` σήμερα, άρα το πλήρες API schema είναι δημόσια ορατό ακόμα και σε production. |
| `spring-boot-devtools` | Live restart/reload σε local dev | — | `optional` + `scope=runtime`: το Spring Boot repackaging plugin το αγνοεί αυτόματα στο τελικό jar, άρα ποτέ δεν "μπαίνει" σε production                                                                                              | Μόνο για local dev, ποτέ production |
| `spring-boot-starter-test` (test scope) | JUnit 5, Mockito, AssertJ, MockMvc | Θα χρησιμοποιηθεί σε όλα τα νέα test classes (ενότητα 8 παρακάτω) | Standard Spring Boot testing stack                                                                                                                                                                                                   | Απαραίτητο για τα tests που προστίθενται τώρα |
| `spring-security-test` (test scope) | Helpers όπως `SecurityMockMvcRequestPostProcessors.jwt()` για να στήνουμε authenticated test requests | Security integration tests | Χωρίς αυτό θα έπρεπε να φτιάχνουμε το `Authentication`/`SecurityContext` με το χέρι σε κάθε test                                                                                                                                     | Απαραίτητο για τα tests που προστίθενται τώρα |

### Γιατί PostgreSQL

Η τρέχουσα κατάσταση του repo είναι **PostgreSQL**, όχι MySQL. Λόγοι:

1. Το `Block.content` (μελλοντικό entity στο roadmap — σκηνές/κανόνες της εμπειρίας,
   δεν υπάρχει ακόμα σε αυτό το repo) σχεδιάζεται ως JSON column. Το native `JSONB`
   της Postgres ταιριάζει καλύτερα σε αυτή την ανάγκη από το `JSON` type της MySQL
   (indexing/query capabilities).
2. Το `UNICHEcatalogue` (αδερφό service της πλατφόρμας UNICHE) τρέχει ήδη PostgreSQL —
   για λόγους συνέπειας είναι καλύτερο να τρέχουν το ίδιο σύστημα βάσεων.

### Γιατί Java 21 και όχι Java 25

Το `spring-boot-starter-parent` που χρησιμοποιούμε είναι η 3.4.7. Σταθερή υποστήριξη
Java 25 υπάρχει μόνο από Spring Boot 4.x (4.0 GA ~Νοέμβριος 2025, 4.1 GA ~Ιούνιος 2026) —
δηλαδή θα σήμαινε major-version άλμα Spring Boot, όχι απλά bump του JDK. Αυτό
εξετάστηκε ρητά και αναβλήθηκε ελλείψει πραγματικής ανάγκης αυτή τη στιγμή. Η Java 21
είναι LTS (υποστήριξη έως αρχές 2030s), άρα δεν υπάρχει πίεση χρόνου να γίνει το άλμα
τώρα. Όταν αποφασίσουμε να πάμε Spring Boot 4, το JDK bump θα είναι μέρος του ίδιου
migration, όχι ξεχωριστό commit πριν από αυτό.

---

## 2. Authorization & Catalogue

### Πώς λειτουργεί

Η πλατφόρμα UNICHE έχει **ένα κοινό Keycloak** (identity) και **ένα κοινό Catalogue
service** (single source of truth για organisations/projects/authorization) που το
χρησιμοποιούν όλα τα tools της πλατφόρμας (π.χ. και το MuseotekBox). Αυτό το backend:

- **Δεν κάνει δικό του login.** Ο client (Vue frontend) παίρνει το JWT απευθείας από
  το Keycloak μέσω Authorization Code + PKCE (Proof Key for Code Exchange) — το backend ποτέ δεν βλέπει password ή
  login flow.
- **Δεν κρατάει δικό του αντίγραφο** των organisations/projects/authorization data. Για
  οτιδήποτε αφορά αυτά, καλεί ζωντανά το Catalogue (`infrastructure/catalogue/CatalogueClient`)
  σε κάθε request — δεν τα cache-άρει μόνιμα (εξαίρεση: το `GET /me/authorization` έχει
  ένα short-TTL in-memory cache, βλ. παρακάτω).
- Η μοναδική τοπική κατάσταση που κρατάει σχετίζεται με **JIT (just-in-time)** —
  δύο ανεξάρτητοι μηχανισμοί:
  1. **JIT user provisioning**: σε κάθε authenticated request, δημιουργεί/ανανεώνει μια
     τοπική γραμμή `User` από το JWT subject (`JitUserProvisioningFilter` +
     `JitUserProvisioningService`). Δεν υπάρχει ξεχωριστό βήμα εγγραφής.
  2. **Companion-row sync** ("lazy-JIT" / "create-up"): όταν κάτι τοπικό χρειάζεται ένα
     πραγματικό foreign key προς πόρο που ανήκει στο Catalogue (JPA δεν μπορεί να κάνει
     FK cross-service), κρατάμε μια τοπική "καθρέφτης" γραμμή keyed by το ίδιο UUID που
     δίνει το Catalogue. Σήμερα αυτό αφορά μόνο `Project` (`ProjectCompanionSyncService`).
     Η γραμμή γίνεται upsert αμέσως μετά από κάθε write που επιβεβαιώνει το Catalogue,
     upsert ευκαιριακά και σε κάθε read, και soft-delete αν ένα read γυρίσει 404 από το
     Catalogue (το μόνο reconciliation signal που υπάρχει σήμερα — δεν υπάρχει ακόμα
     background "reconcile sweep", βλ. gap παρακάτω).
- Authorization ελέγχεται **στο Catalogue, όχι εδώ.** Το backend δεν έχει δικό του RBAC (Role-Based Access Control)
  μοντέλο (κανένα `Role`/`Permission` entity τοπικά) — απλά προωθεί το Bearer token στο
  Catalogue σε κάθε κλήση, και το Catalogue αποφασίζει αν ο χρήστης έχει δικαίωμα
  (π.χ. 403 όταν δεν είναι manager ενός org). Το `GET /me/authorization` είναι ο τρόπος
  να μάθει ο client *ποια* δικαιώματα έχει ο τρέχων χρήστης, ώστε να προσαρμόσει το UI —
  δεν είναι enforcement mechanism.

### `GET /me/authorization` cache

Το `CatalogueClient.getMeAuthorization()` κρατάει ένα in-memory `ConcurrentHashMap`
(keyed by JWT subject) με TTL όσο λέει το Catalogue (`ttlSeconds` στο response DTO), και
κάνει conditional refresh με `If-None-Match` όταν λήξει το TTL (304 → κρατάει το ίδιο DTO,
απλά ανανεώνει τη λήξη). **Δεν είναι distributed cache** — ζει μόνο στο process memory·
σε πολλαπλά instances/restart, χειρότερη περίπτωση είναι ένα επιπλέον round-trip στο
Catalogue, όχι stale δεδομένα cross-instance (κάθε instance κρατάει το δικό του, με το
ίδιο TTL contract).

### Πώς θα τα χρησιμοποιούμε στο backend

Κάθε νέο feature που αγγίζει organisation/project/authorization data πρέπει να περνάει
από `infrastructure/catalogue/CatalogueClient`. Αν το feature χρειάζεται πραγματικό local FK προς ένα Catalogue-owned
resource, ακολουθεί το ίδιο "companion-row" pattern με το `Project` — δική του
sync service στο `application/<feature>/`, καλούμενη από κάθε use case/query που αγγίζει
τον πόρο. Αν είναι απλό passthrough read χωρίς local state, ονομάζεται
`*PassthroughQuery` ρητά (naming convention ήδη σε ισχύ, βλ. README) ώστε να φαίνεται
αμέσως ότι δεν γράφει τίποτα τοπικά.

### Τι θα χρειάζεται από το frontend

- Να αποκτά το JWT μόνο του από το Keycloak (Authorization Code + PKCE) — το backend
  ποτέ δεν εκδίδει ή ανανεώνει token.
- Να στέλνει `Authorization: Bearer <jwt>` σε **κάθε** κλήση προς αυτό το backend (δεν
  υπάρχει session/cookie mechanism — `SessionCreationPolicy.STATELESS`).
- Να καλεί `GET /api/v1/me/authorization` νωρίς (π.χ. στο app bootstrap) για να ξέρει τι
  να δείξει/κρύψει στο UI (managed orgs, project roles, platform-admin flag) — αλλά να
  ξέρει ότι αυτό είναι *μόνο* για UI-level decisions, ο πραγματικός enforcement
  παραμένει server-side (Catalogue θα γυρίσει 403 όπως και να 'χει αν προσπαθήσει κάτι
  μη επιτρεπτό).
- Να χειρίζεται explicit τα HTTP status codes που ήδη υπάρχουν (400 validation, 403
  forbidden, 404 not found, 500 unexpected) βάσει του `ErrorEnvelope` σχήματος (ενότητα
  5) — και να ξέρει ότι κάποια codes που ίσως περιμένει (401 σε λάθος/ληγμένο token, 409,
  422, 502/503/504 από αργό/κατεβασμένο Catalogue) δεν είναι ακόμα ρητά διαχωρισμένα
  server-side (βλ. gaps στην ενότητα 5) — μέχρι να κλείσει αυτό, οτιδήποτε από αυτά τα
  σενάρια μπορεί να εμφανιστεί σαν γενικό 500 στο frontend.
- CORS: μόνο τα origins στο `museotek.cors.allowed-origins` γίνονται δεκτά (comma-separated
  λίστα) — αν το frontend τρέχει σε νέο domain/port, χρειάζεται να προστεθεί εκεί.

---

## 3. Application classes

Layout: `web/<feature>` (controller) → `application/<feature>` (use case/query) →
`infrastructure/catalogue` και/ή `infrastructure/repository`. Κανένας controller δεν
μιλάει απευθείας σε Catalogue/repository.

### `GetMyAuthorizationQuery`
- **Ευθύνη:** διαβάζει το authorization view του τρέχοντος χρήστη από το Catalogue.
- **Endpoint:** `GET /api/v1/me/authorization` (`MeController`).
- **Input:** κανένα ρητό — παίρνει το subject έμμεσα από το JWT στο security context (μέσα στο `CatalogueClient`).
- **Output:** `CatalogueMeAuthorizationDto` → μετατρέπεται σε `MeAuthorizationResponse`.
- **Εξωτερικοί πόροι:** μόνο Catalogue (read, με cache). Καμία τοπική εγγραφή.
- **Πιθανές αποτυχίες:** αν δεν υπάρχει JWT στο context, το `CatalogueClient` πετάει
  `IllegalStateException` — στην πράξη αυτό δεν πρέπει να συμβεί ποτέ γιατί το
  `SecurityConfig` απαιτεί authentication σε όλα τα non-permitAll endpoints (defensive
  guard, όχι πραγματικό reachable path).
- **Συνέπεια Catalogue/τοπική βάση:** δεν αφορά — δεν υπάρχει τοπικό mirror γι' αυτό το
  resource.
- **Γιατί ξεχωριστή class:** κρατάει τον controller λεπτό (thin), και δίνει ένα ξεχωριστό
  σημείο επέκτασης αν χρειαστεί ποτέ π.χ. rate-limiting ή δικό του caching layer.

### `GetOrganisationQuery`
- **Ευθύνη:** διαβάζει στοιχεία ενός organisation από το Catalogue.
- **Endpoint:** `GET /api/v1/organisations/{orgId}` (`OrganisationController`).
- **Input:** `orgId` (UUID από path).
- **Output:** `CatalogueOrganisationDto` → `OrganisationResponse`.
- **Εξωτερικοί πόροι:** μόνο Catalogue. Δεν υπάρχει καθόλου `Organisation` entity/table
  τοπικά σε αυτό το backend — καθαρό passthrough.
- **Πιθανές αποτυχίες:** Catalogue 404 → `CatalogueNotFoundException` → 404 στον client.
- **Συνέπεια:** δεν αφορά (τίποτα τοπικό).
- **Γιατί ξεχωριστή class:** ίδιος λόγος με παραπάνω — thin controller, ξεχωριστό seam.

### CRUD use cases — `CreateProjectUseCase` / `DeleteProjectUseCase` / `RestoreProjectUseCase` / `UpdateProjectUseCase`
- **Κοινό μοτίβο:** και οι 4 classes κάνουν write στο Catalogue πρώτα, μετά συγχρονίζουν
  το τοπικό companion row μέσω του `ProjectCompanionSyncService` (upsert ή soft-delete).
  Η συνέπεια Catalogue/τοπικής βάσης δεν είναι transactional cross-system (δεν μπορεί να
  είναι — διαφορετικά resources/DBs)· αν το τοπικό write αποτύχει μετά από επιτυχές
  Catalogue write, δεν υπάρχει compensating-action/retry εδώ και τώρα — το επόμενο
  `GetProjectQuery` θα κάνει upsert ή soft-delete το companion row ώστε να το διορθώσει.
- **Γιατί ξεχωριστές classes:** κάθε μία διαχειρίζεται δύο resources (Catalogue + τοπική
  βάση) — δεν είναι δουλειά ούτε του controller (HTTP concern) ούτε του `CatalogueClient`
  (καθαρός HTTP adapter, δεν ξέρει τίποτα για τοπική βάση). Κάθε business action
  (create/delete/restore/update) έχει και το δικό της failure mode.

| Class | Endpoint | Input → Output | Ό,τι διαφέρει |
|---|---|---|---|
| `CreateProjectUseCase` | `POST /api/v1/organisations/{orgId}/projects` | `orgId` + `CatalogueCreateProjectRequest(name, slug, toolSlug)` → 201 `ProjectResponse` | Catalogue 403 αν ο χρήστης δεν είναι manager του org |
| `DeleteProjectUseCase` | `DELETE /api/v1/projects/{id}` | `id` → 204 No Content | Αν το Catalogue delete πετύχει αλλά το τοπικό soft-delete αποτύχει, μένει παράθυρο ασυνέπειας (η γραμμή φαίνεται ακόμα ενεργή τοπικά) μέχρι το επόμενο `GetProjectQuery` |
| `RestoreProjectUseCase` | `POST /api/v1/projects/{id}/restore` | `id` → `CatalogueProjectDto` | Καθαρίζει το `deletedAt` στο τοπικό row |
| `UpdateProjectUseCase` | `PATCH /api/v1/projects/{id}` | `id` + `CatalogueUpdateProjectRequest(name)` → `CatalogueProjectDto` | Το δικό μας public API είναι PATCH, αλλά το πραγματικό Catalogue endpoint είναι PUT (ρητό comment στο `CatalogueClient.updateProject` — σκόπιμο, όχι bug) |

### `GetProjectQuery`
- **Ευθύνη:** διαβάζει project· σε επιτυχία κάνει upsert το companion row· σε Catalogue
  404 κάνει soft-delete το companion row πριν αφήσει το 404 να προχωρήσει. Ονομάζουμε
  αυτό το soft-delete βήμα **"cleanup"** στην υπόλοιπη ενότητα ("lazy-JIT" reconciliation
  — το **μοναδικό** σημείο του κώδικα που το κάνει).
- **Endpoint:** `GET /api/v1/projects/{id}` (`ProjectController`).
- **Input:** `id`. **Output:** `CatalogueProjectDto`.
- **Εξωτερικοί πόροι:** Catalogue (read) + τοπικός πίνακας `projects` (write και στα δύο branches).
- **Πιθανές αποτυχίες:** Catalogue 404 → πιάνεται εδώ ρητά για το cleanup, μετά
  re-thrown· οποιοδήποτε άλλο exception (π.χ. Catalogue 5xx) περνάει χωρίς cleanup.
- **Συνέπεια:** αυτή είναι η class που **υλοποιεί** το reconciliation — τα άλλα use
  cases βασίζονται σε αυτήν έμμεσα (κάθε φορά που κάποιος ανοίγει ξανά ένα project,
  αυτό το path τρέχει και διορθώνει τυχόν ασυνέπεια).
- **Γιατί ξεχωριστή class:** το reconciliation logic (`try/catch` γύρω από το read) είναι
  αρκετά σημαντικό/ιδιαίτερο ώστε να μη θέλουμε να χαθεί μέσα σε γενικότερο κώδικα.

### `ListProjectsForOrgPassthroughQuery` / `ListDeletedProjectsForOrgPassthroughQuery`
- **Ευθύνη:** καθαρό passthrough listing από το Catalogue — καμία τοπική εγγραφή.
- **Endpoints:** `GET /api/v1/organisations/{orgId}/projects` και `.../projects/deleted`
  (`OrganisationController`).
- **Input:** `orgId`. **Output:** `List<CatalogueProjectDto>`.
- **Εξωτερικοί πόροι:** μόνο Catalogue.
- **Πιθανές αποτυχίες:** `listDeletedProjectsForOrg` έχει ρητό 403 mapping· το plain
  `listProjectsForOrg` δεν έχει κανένα `.onStatus()` intercept σήμερα — οποιοδήποτε 4xx/5xx
  από το Catalogue θα περάσει ως raw `RestClientResponseException`, όχι σαν το δικό μας
  τυποποιημένο exception (gap, βλ. ενότητα 6).
- **Συνέπεια:** δεν αφορά — καμία τοπική εγγραφή.
- **Γιατί ξεχωριστή class / γιατί `*PassthroughQuery` suffix:** το suffix υπάρχει ρητά
  ως naming convention (βλ. README) ώστε να φαίνεται αμέσως, χωρίς να ανοίξεις το αρχείο,
  ότι αυτή η class δεν συντηρεί κανένα τοπικό state — σε αντίθεση με τα αδέρφια της στο
  ίδιο package που κάνουν companion-sync.

### `ProjectCompanionSyncService`
- **Ευθύνη:** οι δύο ακατέργαστες (raw) λειτουργίες πάνω στο companion row —
  `upsert(id, orgId, name)` και `softDelete(id)` — χωρίς καμία λογική για το πότε πρέπει
  να κληθεί η καθεμία. Δεν είναι Query/UseCase το ίδιο, είναι το shared collaborator που
  καλούν τα 5 παραπάνω use cases/queries (εκτός `GetMyAuthorizationQuery`,
  `GetOrganisationQuery`, και τα δύο passthrough queries).
- **Endpoint:** κανένα άμεσα — εσωτερικό collaborator.
- **Input/Output:** `upsert` — id/orgId/name, void. `softDelete` — id, void.
- **Εξωτερικοί πόροι:** μόνο τοπικός πίνακας `projects` (ποτέ Catalogue).
- **Πιθανές αποτυχίες:** DB unavailable → exception διαδίδεται στο caller (δες πάνω πώς
  το χειρίζεται κάθε caller — στην ουσία, καθόλου ρητά σήμερα).
- **Συνέπεια:** αυτή η class *είναι* ο μηχανισμός συνέπειας, αλλά μόνο ως προς το read/write
  στο τοπικό row· η λογική "πότε να τα καλέσω" ζει αποκλειστικά στους callers.
- **Γιατί ξεχωριστή class:** μοιράζεται από 5 διαφορετικά use cases/queries — μονό σημείο
  αλλαγής για το companion-row semantics, και τεκμηριώνει το JIT/create-up pattern σε ένα
  σημείο (ρητά αναφέρεται στο README).

---

## 4. Security classes

### `SecurityConfig`
- **Ευθύνη:** ορίζει ολόκληρο το filter chain — CORS, CSRF disabled (stateless JWT API,
  δεν υπάρχουν cookies να προστατευτούν), stateless sessions, JWT decoder με
  issuer+audience validation, ποια endpoints είναι public (`permitAll`), εγγραφή του
  `JitUserProvisioningFilter` στο chain.
- **Θέση στο filter chain:** το `JitUserProvisioningFilter` μπαίνει ρητά
  `addFilterAfter(jitFilter, BearerTokenAuthenticationFilter.class)` — δηλαδή τρέχει
  **μόνο αφού** το Spring Security OAuth2 resource-server filter έχει ήδη επικυρώσει το
  JWT και γεμίσει το `SecurityContext`. Ένα request με άκυρο/απόν token ποτέ δεν φτάνει
  στο δικό μας filter.
- **JWT assumptions:** issuer πρέπει να ταιριάζει ακριβώς με
  `spring.security.oauth2.resourceserver.jwt.issuer-uri` (το Keycloak realm URL)·
  audience πρέπει να περιέχει `"uniche-platform"` (ελέγχεται από `AudienceValidator`).
- **Πώς αποτυγχάνει / HTTP response:** άκυρο/ληγμένο/λάθος issuer ή audience token →
  αυτό γίνεται χειρισμό **από το ίδιο το Spring Security OAuth2 resource server**, όχι
  από το `GlobalExceptionHandler` — ο δικός μας exception handler πιάνει μόνο ό,τι
  πετάγεται μέσα από την εκτέλεση ενός controller method (μετά το `DispatcherServlet`).
  Η default συμπεριφορά είναι 401 με header
  `WWW-Authenticate: Bearer error="invalid_token", error_description="..."`.
- **Service accounts:** το `SecurityConfig` δεν κάνει καμία διάκριση — οποιοδήποτε valid
  JWT (ανθρώπου ή service account) περνάει το `.anyRequest().authenticated()` κανόνα. Η
  διάκριση γίνεται στο `JitUserProvisioningFilter`, όχι εδώ.
- **Γιατί JIT σε κάθε request:** δεν υπάρχει ξεχωριστό registration/signup βήμα στην
  πλατφόρμα — η ταυτότητα προέρχεται από το Keycloak token κατ' απαίτηση. Δεν υπάρχει
  κανένα login hook εδώ (το login γίνεται 100% στο Keycloak, το backend δεν το βλέπει
  ποτέ) — άρα το μόνο σωστό σημείο να κρατηθεί ενήμερη η τοπική γραμμή `User` είναι κάθε
  request, όχι ένα εφάπαξ login event.

### `AudienceValidator`
- **Ευθύνη:** επιβάλλει την παρουσία του απαιτούμενου audience `"uniche-platform"` στο
  `aud` claim, ως δεύτερος validator μέσω `DelegatingOAuth2TokenValidator` δίπλα στον
  default issuer validator.
- **Θέση στο filter chain:** εκτελείται **μέσα** στο `NimbusJwtDecoder`, κατά το decode
  του token — δηλαδή πριν καν μπει το `Authentication` στο security context, άρα πριν
  από ΟΛΟ το υπόλοιπο chain (και πριν το `JitUserProvisioningFilter`).
- **JWT assumptions:** το `aud` claim υπάρχει και είναι λίστα — αν ο Keycloak
  audience-mapper δεν είναι σωστά ρυθμισμένος, το `aud` μπορεί να είναι κενό και κάθε
  token θα αποτυγχάνει.
- **Πώς αποτυγχάνει / HTTP response:** γυρίζει `OAuth2TokenValidatorResult.failure(...)`
  → ο decoder πετάει `JwtValidationException` → 401 `invalid_token` με το μήνυμα
  "Missing required audience: uniche-platform" στο `WWW-Authenticate` header.
- **Service accounts:** καμία διάκριση — το audience requirement ισχύει για όλα τα tokens.
- **Σημείωση:** package-private class (όχι `public`) — φτιάχνεται μόνο μέσα στο
  `SecurityConfig`, δεν προορίζεται για επαναχρησιμοποίηση αλλού.

### `CurrentPrincipal`
- **Ευθύνη:** static utility για να πάρεις το τρέχον JWT/subject από το
  `SecurityContextHolder`, και να κρίνεις αν ένα JWT ανήκει σε service account.
- **Θέση στο filter chain:** δεν είναι filter το ίδιο — είναι helper που καλείται ΑΠΟ
  filters/services/τον `CatalogueClient`.
- **JWT assumptions:** έχει νόημα μόνο αφού το Spring Security έχει ήδη βάλει ένα
  `JwtAuthenticationToken` στο context. Σε `permitAll()` endpoints (swagger,
  actuator/health) δεν υπάρχει token, άρα `jwt()` γυρίζει `Optional.empty()` — γι' αυτό
  ακριβώς το `JitUserProvisioningFilter` το καλεί μέσα σε `.ifPresent(...)`.
- **Service account detection:** το `preferred_username` claim ξεκινά με
  `"service-account-"` (σύμβαση του Keycloak για client-credentials-flow tokens) — αυτό
  είναι **naming-convention-based**, όχι ένα ρητό claim/scope. Αν ποτέ αλλάξει η σύμβαση
  ονοματοδοσίας στο Keycloak, αυτός ο έλεγχος σπάει σιωπηλά.
- **Γιατί ξεχωριστή class:** κεντρικοποιεί δύο πράγματα (πάρε το JWT, ξεχώρισε service
  account) που αλλιώς θα επαναλαμβάνονταν σε κάθε filter/service που τα χρειάζεται.

### `JitUserProvisioningFilter`
- **Ευθύνη:** `OncePerRequestFilter` — αν υπάρχει JWT και ΔΕΝ είναι service account,
  καλεί `JitUserProvisioningService.provision(jwt)` πριν συνεχίσει το chain.
- **Θέση στο filter chain:** αμέσως μετά το `BearerTokenAuthenticationFilter` (βλ.
  `SecurityConfig` πάνω) — άρα πριν φτάσει το request στο `DispatcherServlet`/controller.
- **JWT assumptions:** τα ίδια με το `CurrentPrincipal` (βασίζεται πάνω του).
- **Πώς αποτυγχάνει / HTTP response:** το filter τυλίγει το
  `provisioningService.provision()` σε δικό του try/catch — αν πετάξει (π.χ. DB down), το
  exception καταγράφεται (`log.error`) και ο client δεν το βλέπει καθόλου: το chain
  συνεχίζει κανονικά. Δεν χρειάζεται να φτάσει ποτέ στο `GlobalExceptionHandler` (που
  ούτως ή άλλως δεν θα το έπιανε — `@RestControllerAdvice` ισχύει μόνο για ό,τι φτάνει
  μέσα από κανονική εκτέλεση controller, όχι για filters πριν το `DispatcherServlet`).
  Σκόπιμη επιλογή: η τοπική γραμμή `User` είναι best-effort mirror που κανένα άλλο
  feature δεν διαβάζει σήμερα — δεν αξίζει να μπλοκάρει/αποτύχει ένα κατά τα άλλα valid
  request.
- **Service accounts:** ρητά παραλείπονται (`if (!isServiceAccount) provision()`) — η
  πλατφόρμα δεν θέλει τοπική γραμμή `User` για κάθε service-account subject κάθε tool,
  μόνο για ανθρώπους.

### `JitUserProvisioningService`
- **Ευθύνη:** idempotent upsert της τοπικής γραμμής `User` keyed by JWT `sub` —
  ανανεώνει email/preferred_username/display_name/lastSeenAt σε κάθε κλήση,
  `firstSeenAt` μόνο στη δημιουργία. Σκόπιμα **όχι** `@Transactional` (βλ. concurrency
  παρακάτω).
- **Θέση στο filter chain:** δεν είναι filter η ίδια — καλείται ΑΠΟ το
  `JitUserProvisioningFilter`.
- **Πώς αποτυγχάνει / HTTP response:** ίδιο με πάνω — ό,τι exception δεν πιάσει η ίδια
  εσωτερικά, το πιάνει (και το καταπίνει) το `JitUserProvisioningFilter`.
- **Concurrency:** το `findBySubject` → `saveAndFlush` είναι ένα check-then-act race — δύο
  ταυτόχρονα *πρώτα* requests για το ίδιο νέο subject μπορούν και τα δύο να μην βρουν
  υπάρχουσα γραμμή και να προσπαθήσουν να κάνουν insert. Ο χαμένος πιάνεται εδώ ρητά
  (`catch (DataIntegrityViolationException)`) και κάνει fallback: re-fetch τη γραμμή που
  μόλις δημιούργησε ο νικητής και update πάνω σε αυτήν, αντί να αποτύχει το request.
  Χρειάζεται να **μην** είναι `@Transactional` η μέθοδος γι' αυτό ακριβώς: αν το αρχικό
  `saveAndFlush` χάσει το race, το Hibernate μαρκάρει το session unusable για
  οποιοδήποτε άλλο flush· κρατώντας τις δύο προσπάθειες σε ξεχωριστά (per-repository-call)
  transactions, το fallback save τρέχει σε καθαρό session και πετυχαίνει. Καλυμμένο από
  τα tests `concurrentFirstRequest_fallsBackToUpdatingWinnerRowOnUniqueConstraintViolation`
  / `concurrentFirstRequest_secondSaveFailureIsNotSwallowedSilently`.

---

## 5. Error handling

### Κοινό error response contract

```json
{ "code": "NOT_FOUND", "message": "...", "details": [] }
```

`ErrorEnvelope(code, message, details)` — `code` σταθερό string identifier,
`message` ανθρώπινο μήνυμα, `details` λίστα strings (γεμίζει μόνο στα validation errors,
ένα string ανά field, μορφή `"<field>: <default message>"` — π.χ. `"name: must not be
blank"`. Σκόπιμα **δεν** χρησιμοποιείται πλέον `FieldError::toString()` (η default
Spring αναπαράσταση), γιατί αυτή περιλαμβάνει και το rejected value μέσα στο string· η
τρέχουσα υλοποίηση εκθέτει μόνο το όνομα του field και το validation message, ποτέ την
τιμή που στάλθηκε — ακόμα κι αν κάποτε προστεθεί sensitive πεδίο (π.χ. password) με
validation, δεν θα διαρρεύσει στο response.

### Πώς μεταφράζονται σήμερα

| Exception | HTTP status | code |
|---|---|---|
| `CatalogueNotFoundException` | 404 | `NOT_FOUND` |
| `CatalogueForbiddenException` | 403 | `FORBIDDEN` |
| `MethodArgumentNotValidException` (bean validation) | 400 | `VALIDATION_ERROR` |
| οτιδήποτε άλλο (`Exception.class` catch-all) | 500 | `INTERNAL_ERROR` |

**401 (authentication):** δεν παράγεται ποτέ από το `GlobalExceptionHandler` — είναι
εξ ολοκλήρου του Spring Security OAuth2 resource server (missing/invalid/expired token),
ξεχωριστός μηχανισμός, δεν περνάει ποτέ από αυτή τη class.

### ⚠️ Codes που δεν έχουν υλοποιηθεί ακόμα (400/401/403/404/409/422/500/502/503/504)

- **409 (conflict):** δεν μοντελοποιείται καθόλου σήμερα. Αν το Catalogue γυρίσει π.χ.
  409 για duplicate slug, ο `CatalogueClient` δεν το intercept-άρει ρητά (`.onStatus()`
  υπάρχει μόνο για 404/403 ανά μέθοδο) — πέφτει στο default `retrieve()` behavior που
  πετάει `HttpClientErrorException`, την οποία πιάνει ο γενικός `Exception` handler →
  **λάθος 500 αντί για το σωστό status**.
- **422 (unprocessable entity):** δεν μοντελοποιείται καθόλου.
- **502/503/504 (Catalogue unreachable/αργό/5xx):** ο `RestClient` έχει πλέον ρητό
  connect timeout (5s) και read timeout (10s) στο `CatalogueClient` constructor
  (`JdkClientHttpRequestFactory` πάνω σε explicit `HttpClient`) — ένα κρεμασμένο
  Catalogue call **δεν μπλοκάρει πια το request thread επ' αόριστον**, αποτυγχάνει σε
  ~10s. **Παραμένει ανοιχτό** όμως το status code mapping: το timeout exception
  (`ResourceAccessException`) δεν έχει δικό του `.onStatus()`/handler σήμερα, άρα πέφτει
  στον γενικό `Exception` handler → **500** αντί για 504. Ομοίως αν το Catalogue
  απαντήσει με 5xx, δεν υπάρχει `.onStatus()` intercept — πέφτει στον γενικό handler →
  500 (χάνεται το πραγματικό status/detail του Catalogue, μόνο ένα γενικό "unexpected
  error" logάρεται/επιστρέφεται). Η αργή απάντηση τώρα αποτυγχάνει γρήγορα· ο σωστός
  status code (502 vs 503 vs 504 vs upstream status) δεν έχει ακόμα αποφασιστεί/υλοποιηθεί.
- Αυτά τα τρία σημεία (409/422/502-504) είναι ανοιχτά θέματα, όχι
  υλοποιημένη συμπεριφορά — χρειάζονται ρητή απόφαση πριν προστεθούν οι αντίστοιχοι έλεγχοι.

### Logging

Σήμερα μόνο ο γενικός catch-all handler κάνει `log.error("Unhandled exception", e)`
(πλήρες stack trace, χωρίς redaction, χωρίς request id). Τα ειδικά mapped exceptions
(404/403/400) δεν logάρονται καθόλου σήμερα — λογικό για 404/400 (αναμενόμενη κίνηση),
αλλά το 403 (forbidden) πιθανώς αξίζει logging για security auditing.

### Correlation / tracing

**Δεν υπάρχει τίποτα υλοποιημένο σήμερα** — κανένα request-id/trace-id στο
`ErrorEnvelope`, κανένα MDC, καμία distributed tracing βιβλιοθήκη στο classpath
(δεν υπάρχει Micrometer Tracing). Το Actuator εκθέτει μόνο `health,info`.

---

## 6. Application properties

| Property | Τι ελέγχει | Γιατί χρειάζεται | Default | Πού επιτρέπεται default                                                                                                                                                      | Υποχρεωτικό σε prod; | Secret; | Startup validation |
|---|---|---|---|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---|---|---|
| `server.port` | HTTP port | Βασικό network binding | `8080` | Local/dev· σε prod συνήθως ορίζεται από orchestrator env                                                                                                                     | Όχι απαραίτητα (default ok) | Όχι | Κανένα ρητό — Spring απλά αποτυγχάνει αν το port είναι κατειλημμένο |
| `server.servlet.context-path` | Fixed path prefix `/museotekbox` | Πλατφόρμα-wide σύμβαση path per tool | `/museotekbox` (hardcoded, χωρίς env override) | Όλα — δεν είναι per-environment axis                                                                                                                                         | Ναι, όπως είναι | Όχι | — |
| `spring.application.name` | Λογικό όνομα για logging/actuator | Identification | `museotek-box-backend` (hardcoded) | Όλα                                                                                                                                                                          | Ναι | Όχι | — |
| `spring.datasource.url` | JDBC connection string | Ποια DB | `jdbc:postgresql://localhost:5432/museotekbox` | Μόνο local dev                                                                                                                                                               | **Ναι, πρέπει να οριστεί ρητά** | Όχι (host μόνο, χαμηλή ευαισθησία) | Fail-fast αν η DB δεν είναι reachable (connection pool init στο startup) |
| `spring.datasource.username` | DB user | Αυθεντικοποίηση DB | κενό | Μόνο local dev με trust/peer auth                                                                                                                                            | **Ναι** | Οριακά (username, όχι μυστικό αυτό καθαυτό) | Fail-fast (άδειο username θα αποτύχει connection) |
| `spring.datasource.password` | DB password | Αυθεντικοποίηση DB | κενό | **Μόνο local dev**                                                                                                                                                           | **Ναι, υποχρεωτικό** | **Ναι, μυστικό** | Fail-fast (θα αποτύχει το connection) |
| `spring.datasource.driver-class-name` | JDBC driver class | Πρέπει να ταιριάζει με το URL scheme | `org.postgresql.Driver` (hardcoded) | Όλα                                                                                                                                                                          | Ναι | Όχι | — |
| `spring.jpa.hibernate.ddl-auto` | Αν το Hibernate αλλάζει το schema αυτόματα | Σήμερα η **μόνη** πηγή schema evolution (δεν υπάρχει Liquibase/Flyway ακόμα) | `update` (hardcoded, χωρίς env override) | ⚠️ Σκόπιμα σήμερα εφαρμόζεται σε όλα τα environments — χρειάζεται μετάβαση σε migration tool, ή τουλάχιστον `validate` σε prod με schema εφαρμοσμένο εκτός εφαρμογής         | — | — | Κανένα |
| `spring.jpa.open-in-view` | Open-session-in-view pattern | Ρητά off — σωστό default, αποφεύγει lazy-loading εκτός transaction | `false` | Όλα                                                                                                                                                                          | Ναι | Όχι | — |
| `spring.jpa.show-sql` | SQL logging στο stdout | Debugging | `false` (hardcoded) | Σωστό για prod· τοπικά flip χειροκίνητα μέσω command-line flag αντί για env var                                                                                              | Ναι, όπως είναι | Όχι | — |
| `spring.jpa.properties.hibernate.dialect` | SQL dialect | Πρέπει να ταιριάζει με τη DB | `PostgreSQLDialect` (hardcoded) | Όλα                                                                                                                                                                          | Ναι | Όχι | — |
| `spring.jpa.properties.hibernate.format_sql` | Pretty-print SQL όταν είναι ενεργό το show-sql | Αναγνωσιμότητα σε dev | `true` | Αδιάφορο (no-op αν show-sql=false)                                                                                                                                           | Ναι, όπως είναι | Όχι | — |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | Ποιο Keycloak realm εκδίδει έμπιστα tokens | Θεμέλιο της αυθεντικοποίησης | `https://idp.uniche-eccch.eu/realms/uniche` (**το πραγματικό production URL**) |                                                                                                                                                                              | **Ναι, πάντα ρητά** | Όχι (public URL) | Κανένα σήμερα |
| `uniche.catalogue.base-url` | Ποιο Catalogue instance καλείται | Θεμέλιο του org/project/authorization integration | `https://catalogue.uniche-eccch.eu` (**production**) | Ίδιο ρίσκο με πάνω — default σιωπηλά δείχνει σε production Catalogue                                                                                                         | **Ναι, πάντα ρητά** | Όχι | Κανένα σήμερα |
| `uniche.tool.slug` | (προορίζεται να δηλώνει το tool slug αυτού του backend στην πλατφόρμα) | — | `museotek-box` | —                                                                                                                                                                            | — | Όχι | **σήμερα dead config**: δηλώνεται στο properties αλλά δεν γίνεται `@Value`-inject πουθενά στον κώδικα |
| `museotek.cors.allowed-origins` | Ποια origins επιτρέπονται (CORS) | Το Vue frontend πρέπει να μπορεί να καλέσει το API από browser | `http://localhost:5173` (Vite dev) | Μόνο local dev                                                                                                                                                               | **Ναι, πάντα ρητά ανά environment** | Όχι | Κανένα — δεν ελέγχεται π.χ. format (scheme/trailing slash), λάθος τιμή αποτυγχάνει σιωπηλά μόνο στο runtime browser request, όχι στο startup |
| `springdoc.api-docs.path` / `springdoc.swagger-ui.path` / `springdoc.swagger-ui.try-it-out-enabled` | Πού ζει το OpenAPI JSON / Swagger UI, αν επιτρέπεται live "try it out" | Dev/QA convenience, API contract visibility | `/api-docs`, `/swagger-ui.html`, `true` | Ίδιο σε όλα τα environments σήμερα — **ανοιχτό θέμα**: αυτά είναι `permitAll()` στο `SecurityConfig`, άρα ολόκληρο το API schema είναι δημόσια ορατό ακόμα και σε production | Απόφαση εκκρεμεί | Όχι | — |
| `management.endpoints.web.exposure.include` | Ποια actuator endpoints εκτίθενται | Ops/monitoring χωρίς να εκτεθούν επικίνδυνα endpoints (`env`, `beans`, `heapdump`) | `health,info` (σωστά συντηρητικό) | Όλα — σκόπιμα fixed, όχι per-environment axis (είναι security control)                                                                                                       | Ναι, όπως είναι | Όχι | — |

---

## Περίληψη ανοιχτών θεμάτων

1. Swagger UI/OpenAPI public σε production — ναι/όχι, ή περιορισμένο;
2. `ddl-auto=update` σε production — μετάβαση σε Liquibase/Flyway, ή τουλάχιστον `validate`;
3. Τα defaults του `issuer-uri` και `uniche.catalogue.base-url` δείχνουν σε production —
   να αλλάξουν σε κάτι ασφαλές/άκυρο by default;
4. 409/422/502/503/504 status-code mapping — ποια ακριβώς σενάρια πρέπει να μπουν, και
   ποιο upstream/timeout status μεταφράζεται σε ποιο από αυτά (το hang-forever μέρος
   λύθηκε, το σωστό status code παραμένει ανοιχτό — βλ. ενότητα 5);
5. Correlation/tracing id — να προστεθεί, και πού (MDC + log pattern; στο ErrorEnvelope;).
6. `uniche.tool.slug` property — dead config, να αφαιρεθεί ή να γίνει wire-up;

**Λυμένα:**
- ~~Race condition στο `JitUserProvisioningService.provision()`~~ — λυμένο: όχι πλέον
  `@Transactional`, catch+retry στο `DataIntegrityViolationException` (βλ. ενότητα 4).
- ~~Exception μέσα στο `JitUserProvisioningFilter` δεν παίρνει το `ErrorEnvelope`
  σχήμα~~ — λυμένο διαφορετικά: το filter τυλίγει το `provision()` call σε try/catch
  δικό του, log και συνέχεια του chain· δεν χρειάζεται πια να φτάσει στο
  `GlobalExceptionHandler` (βλ. ενότητα 4).
- ~~`ErrorEnvelope.details` εκθέτει το rejected value μέσω `FieldError::toString()`~~ —
  λυμένο: πλέον μόνο `"<field>: <default message>"`, καμία τιμή δεν εμφανίζεται (βλ.
  ενότητα 5).
- ~~`CatalogueClient`'s `RestClient` χωρίς connect/read timeout~~ — μερικώς λυμένο: 5s
  connect / 10s read timeout προστέθηκαν, άρα δεν μπλοκάρει πια το thread επ' αόριστον
  (βλ. ενότητα 5). Το status-code mapping του resulting timeout παραμένει στο item 4
  παραπάνω.
