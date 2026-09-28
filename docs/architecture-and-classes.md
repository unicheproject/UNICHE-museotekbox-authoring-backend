# Architecture & Application Classes

## 1. Authorization & Catalogue

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
  forbidden, 404 not found, 409 conflict, 422 unprocessable, 502/503/504 από αργό/
  κατεβασμένο Catalogue, 500 unexpected) βάσει του `ErrorEnvelope` σχήματος
  (`security-and-errors.md`, ενότητα 2).
  Το μόνο που απομένει ρητά μη διαχωρισμένο είναι το 401 σε λάθος/ληγμένο token, το οποίο
  ούτως ή άλλως δεν περνάει ποτέ από τον `GlobalExceptionHandler` (βλ. `security-and-errors.md`, ενότητα 2).
- CORS: μόνο τα origins στο `museotek.cors.allowed-origins` γίνονται δεκτά (comma-separated
  λίστα) — αν το frontend τρέχει σε νέο domain/port, χρειάζεται να προστεθεί εκεί.

---

## 2. Application classes

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

### `ProjectAccessGuard` (application/projectaccess/)
- **Ευθύνη:** το μοναδικό, υποχρεωτικό entry point για οποιαδήποτε project-scoped
  λειτουργία, σημερινή ή μελλοντική. Διαβάζει project από το Catalogue· σε επιτυχία
  κάνει upsert το companion row· σε Catalogue 404 κάνει soft-delete το companion row
  πριν αφήσει το 404 να προχωρήσει. Ονομάζουμε αυτό το soft-delete βήμα **"cleanup"**
  στην υπόλοιπη ενότητα ("lazy-JIT" reconciliation — το **μοναδικό** σημείο του κώδικα
  που το κάνει).
- **Καλείται από:** `GetProjectQuery` (thin delegate, δες παρακάτω). Οποιοδήποτε
  μελλοντικό `application/<feature>` που διαβάζει/γράφει τοπικά δεδομένα scoped by
  `projectId` (π.χ. ένα μελλοντικό `Scene`/`Rule`, ή το ήδη υπάρχον `Box` όταν αποκτήσει
  δικό του `web/`+`application/` layer) **πρέπει** να καλέσει `requireAccess(projectId)`
  πριν αγγίξει το repository του — βλ. README, ενότητα "Adding a new entity/feature".
- **Input:** `projectId`. **Output:** `CatalogueProjectDto`.
- **Εξωτερικοί πόροι:** Catalogue (read) + τοπικός πίνακας `projects` (write και στα δύο branches).
- **Πιθανές αποτυχίες:** Catalogue 404 → πιάνεται εδώ ρητά για το cleanup, μετά
  re-thrown· οποιοδήποτε άλλο exception (π.χ. Catalogue 5xx) περνάει χωρίς cleanup.
- **Γιατί ξεχωριστή class / γιατί σε δικό της package:** το reconciliation logic ήταν
  πριν μέσα στο `GetProjectQuery`, αλλά μετακινήθηκε σε shared package ώστε να μπορεί
  να κληθεί από οποιοδήποτε μελλοντικό project-scoped feature, όχι μόνο από το read
  endpoint του project. `application/projectaccess/` είναι το αντίστοιχο του
  `web/error/` μέσα στο `application/` layer — ένα shared, cross-cutting subpackage,
  όχι ένα per-feature subpackage.
- **Γιατί ΔΕΝ γίνεται retrofit στα `Update`/`Delete`/`RestoreProjectUseCase`:** τα
  PUT/DELETE/restore endpoints του Catalogue κάνουν ήδη πλήρη authorization enforcement
  μόνα τους· ένα προκαταρκτικό `requireAccess` πριν από αυτά θα ήταν επιπλέον round-trip
  χωρίς πραγματικό security όφελος, αφού η δική τους companion-sync λογική καλύπτει ήδη
  το reconciliation. Σκόπιμη απόκλιση από το αντίστοιχο pattern του iGuide (το reference
  implementation platform tool που έχει ήδη αυτό το gateway pattern).

### `GetProjectQuery`
- **Ευθύνη:** thin delegate προς `ProjectAccessGuard.requireAccess(id)` — υπάρχει
  ξεχωριστά μόνο για να κρατήσει το 1:1 naming convention endpoint↔class
  (`GET /api/v1/projects/{id}` ↔ `GetProjectQuery`, ίδιο pattern με
  `GetOrganisationQuery`/`GetMyAuthorizationQuery`).
- **Endpoint:** `GET /api/v1/projects/{id}` (`ProjectController`).
- **Input:** `id`. **Output:** `CatalogueProjectDto`.
- **Συνέπεια/reconciliation:** δες `ProjectAccessGuard` παραπάνω — αυτή η class δεν έχει
  πλέον δική της λογική.

### `ListProjectsForOrgPassthroughQuery` / `ListDeletedProjectsForOrgPassthroughQuery`
- **Ευθύνη:** καθαρό passthrough listing από το Catalogue — καμία τοπική εγγραφή.
- **Endpoints:** `GET /api/v1/organisations/{orgId}/projects` και `.../projects/deleted`
  (`OrganisationController`).
- **Input:** `orgId`. **Output:** `List<CatalogueProjectDto>`.
- **Εξωτερικοί πόροι:** μόνο Catalogue.
- **Πιθανές αποτυχίες:** `listDeletedProjectsForOrg` έχει ρητό 403 mapping· το plain
  `listProjectsForOrg` περνάει από το ίδιο ενιαίο `CatalogueClient` status mapping με
  όλες τις άλλες κλήσεις (βλ. `security-and-errors.md`, ενότητα 2).
- **Συνέπεια:** δεν αφορά — καμία τοπική εγγραφή.
- **Γιατί ξεχωριστή class / γιατί `*PassthroughQuery` suffix:** το suffix υπάρχει ρητά
  ως naming convention (βλ. README) ώστε να φαίνεται αμέσως, χωρίς να ανοίξεις το αρχείο,
  ότι αυτή η class δεν συντηρεί κανένα τοπικό state — σε αντίθεση με τα αδέρφια της στο
  ίδιο package που κάνουν companion-sync.

### `OrgAccessGuard` (application/orgaccess/)
- **Ευθύνη:** το αντίστοιχο του `ProjectAccessGuard`, αλλά για org-scoped δεδομένα που
  είναι 100% τοπικά (καμία δική τους κλήση προς το Catalogue). Καλεί
  `CatalogueClient.getOrganisation(orgId)` και αφήνει ό,τι exception πετάξει (403/404)
  να προχωρήσει αμετάβλητο — καμία δική του λογική.
- **Γιατί είναι πραγματικό authorization boundary, όχι cosmetic:** το `GET
  /organisations/{orgId}` του Catalogue είναι ήδη access-checked ανά caller
  (`organisationService.get(subject(), orgId)` στο Catalogue, τεκμηριωμένο ως "Get an
  organisation the caller can reach") — άρα το να προωθείς απλά σε αυτό και να αφήνεις
  το exception να περάσει είναι αρκετό, ίδιο μοτίβο με το `ProjectAccessGuard`.
- **Γιατί δεν κάνει companion-sync (σε αντίθεση με το `ProjectAccessGuard`):** δεν
  υπάρχει καθόλου τοπικός πίνακας `Organisation` σε αυτό το backend — δεν υπάρχει τίποτα
  να γίνει sync. Καθαρός access check.
- **Γιατί χρειάστηκε τώρα:** το `Box` (`application/box/`, δες παρακάτω) είναι το πρώτο
  τοπικό entity που γράφεται/διαβάζεται μέσω δικού του `web/`+`application/` layer χωρίς
  καμία κλήση Catalogue στη ροή του. Πριν από αυτή τη class, ένα org-scoped endpoint
  πάνω σε καθαρά τοπικά δεδομένα θα ήταν το πρώτο πραγματικά ανεξέλεγκτο local write path
  στο codebase — ένα raw `@PathVariable orgId` χωρίς κανέναν έλεγχο.
- **Καλείται από:** `CreateBoxUseCase`, `ListBoxesForOrgQuery`, `GetBoxQuery` (πάντα ως
  πρώτο βήμα). Οποιοδήποτε μελλοντικό org-scoped, καθαρά τοπικό feature πρέπει να το
  καλεί το ίδιο, πριν αγγίξει το repository του.
- **Input:** `orgId`. **Output:** `CatalogueOrganisationDto` (σήμερα ο caller δεν τον
  χρησιμοποιεί — το ενδιαφέρον είναι το side effect του exception αν δεν υπάρχει access).

### `CreateBoxUseCase` / `ListBoxesForOrgQuery` / `GetBoxQuery` (application/box/)
- **Ευθύνη:** το πρώτο πραγματικό feature πάνω στο `Box` entity — μέχρι τώρα υπήρχε μόνο
  το domain skeleton (`domain/box/Box.java`, `BoxStatus`) και ένα γυμνό
  `BoxRepository`, χωρίς κανένα endpoint.
- **Endpoints:** `POST` / `GET` / `GET /{boxId}` κάτω από
  `/api/v1/organisations/{orgId}/boxes` (`BoxController`).
- **Κοινό μοτίβο:** και οι 3 καλούν `OrgAccessGuard.requireAccess(orgId)` πρώτα, μετά
  αγγίζουν το `BoxRepository` — καμία απευθείας κλήση Catalogue, το `Box` δεν είναι
  Catalogue-owned resource, άρα δεν υπάρχει `*PassthroughQuery` naming (αυτό το suffix
  έχει νόημα μόνο μέσα σε package όπου κάποιες classes κάνουν companion-sync και άλλες
  όχι — εδώ καμία δεν κάνει).
- **`CreateBoxUseCase`:** ελέγχει πρώτα `findBySerialNumber` και πετάει
  `DuplicateSerialNumberException` (409) αν υπάρχει ήδη — αποφεύγει να διαρρεύσει raw
  `DataIntegrityViolationException`/500 από το DB unique constraint
  (`uk_boxes_serial_number`) στην προφανή real-world περίπτωση ενός λάθος/επαναλαμβανόμενου
  serial number.
- **`GetBoxQuery`:** χρησιμοποιεί `BoxRepository.findByIdAndOrgId(id, orgId)` αντί για
  `findById` + σύγκριση μετά — σκόπιμο: το `Box.id` είναι sequential `Long` (όχι UUID
  σαν το `Project`), άρα μια λάθος-org μαντεψιά πρέπει να πιάνεται στο ίδιο το query
  (404), όχι μέσω ενός post-fetch check που θα μπορούσε να διαρρεύσει πληροφορία.
- **Εκτός scope αυτού του πρώτου slice (σκόπιμα, όχι ξεχασμένα):** Update/Delete στο
  `Box`, και τα `currentProject`/`assignedProjects` M:N assignment endpoints — αυτά θα
  χρειαστούν *δύο* guards μαζί (`OrgAccessGuard` για το ίδιο το Box + `ProjectAccessGuard`
  για το project που ανατίθεται), ξεχωριστό design pass.

### `ScanObjectSupport` / list-get-delete / per-subtype create-update use cases (application/scanobject/)
- **Ευθύνη:** το πρώτο πραγματικό feature πάνω στο `ScanObject` entity family (βάση +
  4 subtypes μέσω JOINED inheritance — `ColouredCard`/`PrintedImage`/
  `ThreeDPrintedObject`/`Draft`) και το `ScanObjectType` — μέχρι τώρα υπήρχαν μόνο τα
  domain entities και δύο γυμνά repositories, χωρίς κανένα endpoint.
- **Endpoints:** `GET` / `GET /{scanObjectId}` / `DELETE /{scanObjectId}` (γενικά, πάνω
  στη βάση `ScanObject`), και `POST`/`PATCH` ανά subtype (`.../coloured-cards`,
  `.../printed-images`, `.../three-d-printed-objects`, `.../drafts`) κάτω από
  `/api/v1/organisations/{orgId}/scan-objects` (`ScanObjectController`).
- **Γιατί ένα endpoint ανά subtype για create/update, όχι ένα κοινό με discriminator
  field:** αποφεύγει polymorphic JSON deserialization, κάτι που δεν χρησιμοποιείται
  πουθενά αλλού στο codebase. Το GET/LIST/DELETE μένουν γενικά πάνω στη βάση, αφού το
  Hibernate ξέρει να φορτώνει το σωστό subtype από το discriminator column (`kind`)
  αυτόματα.
- **Κοινό μοτίβο:** όλα καλούν `OrgAccessGuard.requireAccess(orgId)` πρώτα — ίδιο μοτίβο
  με το `Box`, αφού το `ScanObject` έχει δικό του `orgId` column (όχι project-scoped),
  άρα ξαναχρησιμοποιεί το `OrgAccessGuard` αντί για νέο guard.
- **`ScanObjectSupport`:** shared collaborator (όχι Query/UseCase) που καλούν όλα τα
  create/update use cases — δύο μέθοδοι: `ensureRfidTagAvailable(rfidTag, excludeId)`
  (no-op αν `rfidTag == null`, αφού το `Draft` μπορεί να μην έχει καθόλου tag· αλλιώς
  ελέγχει μοναδικότητα, εξαιρώντας το ίδιο το entity σε update) και
  `resolveType(orgId, scanObjectTypeId)` (`null` αν δεν δόθηκε id, αλλιώς
  `ScanObjectTypeNotFoundException` αν το type δεν υπάρχει/δεν ανήκει στο org). Φτιάχτηκε
  ξεχωριστή class ώστε αυτοί οι δύο έλεγχοι να μη γράφονται 8 φορές (μία ανά
  create/update use case).
- **Update use cases:** μετά το `findByIdAndOrgId`, ελέγχουν ρητά ότι το entity που
  βρέθηκε είναι το αναμενόμενο subtype (π.χ. `instanceof ColouredCard`) — ένα
  `PATCH /coloured-cards/{id}` πάνω σε id που ανήκει σε άλλο subtype γυρίζει
  `ScanObjectNotFoundException` (404), όχι 500/`ClassCastException`.
- **`ScanObjectType` handling:** δεν αποκτά δικό του `web/`/`application/` layer σε αυτό
  το slice — μόνο ο παραπάνω inline έλεγχος μέσω `ScanObjectSupport.resolveType`.
  Παραμένει μη-δημιουργήσιμο μέσω API προς το παρόν.

| Subtype | Extra field | Create/Update endpoints |
|---|---|---|
| `ColouredCard` | `colour` (`CardColour` enum) | `POST`/`PATCH /coloured-cards` |
| `PrintedImage` | `imageUrl` (String) | `POST`/`PATCH /printed-images` |
| `ThreeDPrintedObject` | `modelRef` (String) | `POST`/`PATCH /three-d-printed-objects` |
| `Draft` | καμία — μόνο τα κοινά πεδία | `POST`/`PATCH /drafts` |

- **Εκτός scope αυτού του slice (σκόπιμα, όχι ξεχασμένα):** `ScanObjectType` δεν
  αποκτά δικό του CRUD layer· χρησιμοποιείται μόνο μέσω του inline
  `ScanObjectSupport.resolveType` ελέγχου.

### `GetExperienceQuery` / `ExperienceDocumentValidator` / `SaveExperienceUseCase` (application/experience/)
- **Ευθύνη:** το πρώτο πραγματικό feature πάνω στο Scene/Block/Rule μοντέλο — η
  "εμπειρία" ενός project εκτίθεται ως **ένα** έγγραφο (document model), όχι per-entity
  CRUD. Μέχρι τώρα υπήρχαν μόνο τα domain entities (`Scene`/`Block`/`Rule`) χωρίς κανένα
  endpoint. Η επιλογή document-model έναντι per-entity REST έγινε ρητά — βλ.
  `docs/scene-block-rule-write-model-comparison.md` — κυρίως λόγω του planned frontend
  UX (batched edits, ένα Save button, όχι autosave), που κάνει το atomicity/ordering/
  conflict-detection του document model να αξίζει το επιπλέον κόστος.
- **Endpoints:** `GET`/`PUT /api/v1/projects/{projectId}/experience` (`ExperienceController`).
- **`GetExperienceQuery`:** διαβάζει όλα τα scenes ενός project (με τα blocks/rules τους
  ομαδοποιημένα από κάτω) plus το τρέχον `docVersion` και τα τρία running counters
  (`nextSceneSeq`/`nextBlockSeq`/`nextRuleSeq`, πεδία στο `Project`) — αυτά επιστρέφονται
  ώστε ο client να μπορεί να επινοήσει ένα valid νέο key χωρίς να μαντεύει από τα ήδη
  υπάρχοντα. Το `version` επιστρέφεται και ως response body field και ως `ETag` header.
- **`ExperienceDocumentValidator`:** καθαρή υλοποίηση (καμία repository access) των
  βημάτων 1-3 του write algorithm (βλ. `docs/proposal-scene-block-rule-document-model.md`):
  κάθε key well-formed & unique σε **όλο** το έγγραφο (project-wide counter ανά τύπο key,
  όχι per-scene, παρόλο που το DB unique constraint είναι scene-scoped) · κάθε
  `target_scene_key` λύνεται σε οποιαδήποτε σκηνή του εγγράφου, ενώ `target_block_key`/
  `trigger_block_key` λύνονται μόνο μέσα στη **δική τους** σκηνή · ακριβώς μία σκηνή
  `is_start` · freshness (ένα καινούριο key πρέπει να είναι `>=` το αντίστοιχο project
  counter, ένα ήδη υπάρχον key εξαιρείται από αυτόν τον έλεγχο). Παίρνει σαν input ένα
  `ExperienceWriteContext` (existing keys + τα τρία counters, snapshot που φτιάχνει ο
  caller) — δεν αγγίζει repository το ίδιο.
- **`SaveExperienceUseCase`:** τα βήματα 4-5 (diff+apply, bump version), μέσα σε ένα
  `@Transactional`. Σειρά: (1) `ProjectAccessGuard.requireAccess` (ίδιο πρώτο βήμα με
  κάθε project-scoped feature) · (2) `If-Match` έναντι του `Project.docVersion` — 409 +
  το τρέχον document αν stale (βλ. `security-and-errors.md`, ενότητα 2, γιατί αυτό ΔΕΝ
  περνάει από τον `GlobalExceptionHandler`) · (3) ο validator παραπάνω, plus ένα δικό
  του επιπλέον έλεγχο well-formedness για τα `BlockType`/`RuleEventType`/`RuleAction`
  enum strings (βλ. παρακάτω γιατί χρειάζεται) · (4) διαγραφή ό,τι λείπει από το
  έγγραφο, **παιδιά πριν γονείς** (rules/blocks πριν scenes — το FK constraint υπάρχει
  μόνο scene→blocks/rules, όχι ανάμεσα σε rules/blocks) · (5) upsert το καθένα by key ·
  (6) bump `docVersion` +1 και τα τρία counters με `Math.max` έναντι του ήδη υπάρχοντος
  (ποτέ προς τα πίσω — μια write που δεν εισάγει νέα keys αφήνει τα counters αμετάβλητα).
- **Γιατί το `ExperienceDocument`/`SceneDocument`/`BlockDocument`/`RuleDocument` κρατάνε
  `type`/`eventType`/`action` ως plain `String`, όχι domain enum:** σκόπιμο — κρατάει τον
  `ExperienceDocumentValidator` entity-independent, και αφήνει το `SaveExperienceUseCase`
  να κάνει το δικό του `Enum.valueOf(...)` έλεγχο πριν εφαρμόσει οτιδήποτε στη βάση, ώστε
  μια λάθος τιμή (π.χ. `"NOT_A_TYPE"`) να γυρίσει καθαρό 400
  (`ExperienceValidationException`) αντί για raw `IllegalArgumentException`/500 στη μέση
  του apply.
- **`ScanObjectSupport.resolveType` επαναχρησιμοποιείται** για το `Scene.initCardType`
  και το `Rule.scanObjectType` — ίδιο collaborator με το `ScanObject` feature (βλ.
  παραπάνω στην ίδια ενότητα), όχι νέος κώδικας.
- **Εξωτερικοί πόροι:** `ProjectAccessGuard` (άρα έμμεσα Catalogue, μία φορά ανά write) +
  τοπικά `scenes`/`blocks`/`rules`/`projects`. Καμία άλλη απευθείας κλήση Catalogue.
- **Concurrency:** optimistic, μέσω `If-Match`/`docVersion` — όχι pessimistic
  lock/`SELECT ... FOR UPDATE`.
- **Deletion policy — block-the-whole-Save:** αν ένα key λείπει από το νέο έγγραφο αλλά
  κάτι άλλο ακόμα το target-άρει (`target_scene_key`/`target_block_key`/
  `trigger_block_key`), ο validator (βήμα 2, μέσα στο "validate graph") απορρίπτει
  ολόκληρο το write πριν εφαρμοστεί οτιδήποτε — καμία partial apply, κανένα nulled
  dangling reference. Ίδια πολιτική και στα δύο proposal docs.
- **Γιατί ξεχωριστές classes:** ο validator (καθαρή λογική, τεστάρεται χωρίς Spring
  context/repository mocks) και το use case (persistence-owning, transactional) έχουν
  ξεκάθαρα διαφορετική ευθύνη — το ίδιο διαχωρισμό step 1-3 vs step 4-5 προτείνει και το
  ίδιο το proposal doc.

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
