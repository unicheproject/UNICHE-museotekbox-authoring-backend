# Security & Error Handling

## 1. Security classes

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

### `CorrelationIdFilter`
- **Ευθύνη:** δίνει σε κάθε request ένα correlation id — χρησιμοποιεί το `X-Request-Id`
  header αν το στείλει ο client, αλλιώς φτιάχνει νέο `UUID`· το βάζει σε `MDC` (key
  `requestId`) και το επιστρέφει πάντα ως response header.
- **Θέση στο filter chain:** `@Order(Ordered.HIGHEST_PRECEDENCE)` — τρέχει **πρώτο από
  όλα**, πριν καν το Spring Security δικό του filter chain, ώστε ΚΑΙ τα logs που βγάζει
  το ίδιο το Spring Security (π.χ. σε ένα άκυρο token) να έχουν το ίδιο id.
- **Δεν είναι** ούτε `infrastructure/security`, γιατί δεν κάνει authentication/
  authorization — ζει στο δικό του package `infrastructure/logging`, ονομασμένο από τον
  σκοπό του (όχι `infrastructure/web`, που θα συγκρουόταν με το top-level `web/` — αυτό
  εννοεί κάτι διαφορετικό, το inbound HTTP business layer των controllers).
- **Cleanup:** το `MDC.remove(...)` γίνεται σε `finally`, ρητά, επειδή τα servlet threads
  είναι pooled — χωρίς αυτό, ένα thread θα μπορούσε να logάρει ένα επόμενο, άσχετο
  request κάτω από παλιό id.
- **Πώς αποτυγχάνει:** δεν πιάνει exceptions από το `chain.doFilter(...)` το ίδιο (αυτό
  θα σκότωνε το real error) — μόνο εγγυάται ότι το MDC καθαρίζει ακόμα κι όταν το chain
  πετάξει.

---

## 2. Error handling

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
| `CatalogueConflictException` | 409 | `CONFLICT` |
| `CatalogueUnprocessableException` | 422 | `UPSTREAM_VALIDATION_ERROR` |
| `CatalogueTimeoutException` | 504 | `UPSTREAM_TIMEOUT` |
| `CatalogueUnavailableException` | 503 | `UPSTREAM_UNAVAILABLE` |
| `CatalogueBadResponseException` | 502 | `UPSTREAM_INVALID_RESPONSE` |
| `BoxNotFoundException` | 404 | `BOX_NOT_FOUND` |
| `DuplicateSerialNumberException` | 409 | `DUPLICATE_SERIAL_NUMBER` |
| `ScanObjectNotFoundException` | 404 | `SCAN_OBJECT_NOT_FOUND` |
| `DuplicateRfidTagException` | 409 | `DUPLICATE_RFID_TAG` |
| `ScanObjectTypeNotFoundException` | 404 | `SCAN_OBJECT_TYPE_NOT_FOUND` |
| `MethodArgumentNotValidException` (bean validation) | 400 | `VALIDATION_ERROR` |
| `MethodArgumentTypeMismatchException` (π.χ. μη-UUID path variable) | 400 | `INVALID_PARAMETER` |
| οτιδήποτε άλλο (`Exception.class` catch-all) | 500 | `INTERNAL_ERROR` |

`BoxNotFoundException`/`DuplicateSerialNumberException` (`domain/box/`) είναι τα πρώτα
exceptions σε αυτόν τον πίνακα που δεν προέρχονται από το Catalogue καθόλου — 100%
τοπικά, πετιούνται από `application/box/` use cases/queries πάνω σε δεδομένα που δεν
έχουν καμία σχέση με Catalogue call. Τα τρία `ScanObject*`/`DuplicateRfidTagException`
(`domain/scanobject/`) ακολουθούν το ίδιο μοτίβο — 100% τοπικά, πετιούνται από
`application/scanobject/` (βλ. `architecture-and-classes.md`, ενότητα 2). Το
`ScanObjectNotFoundException` καλύπτει και την περίπτωση "βρέθηκε scan object, αλλά δεν
είναι το αναμενόμενο subtype για αυτό το endpoint" — treated ως 404, όχι 500, ώστε να μη
διαρρεύσει το πραγματικό subtype ενός id σε λάθος endpoint.

**401 (authentication):** δεν παράγεται ποτέ από το `GlobalExceptionHandler` — είναι
εξ ολοκλήρου του Spring Security OAuth2 resource server (missing/invalid/expired token),
ξεχωριστός μηχανισμός, δεν περνάει ποτέ από αυτή τη class.

### `CatalogueClient` — ενιαίο status mapping αντί για ανά-μέθοδο

Ο `CatalogueClient` καταχωρεί πλέον ένα `.defaultStatusHandler(...)` **μία φορά**, στο
`RestClient.Builder`, αντί για ξεχωριστό `.onStatus()` σε κάθε μέθοδο. Αυτό δεν είναι
στιλιστική προτίμηση: το ανά-μέθοδο μοντέλο ήταν η ρίζα του πραγματικού bug που έδειξε
ότι το `createProject` mapάριζε ρητά μόνο το 403 και ξέχναγε το 404 — ένα άκυρο
`toolSlug` (Catalogue: "Authoring tool not found") διέφευγε σαν raw
`HttpClientErrorException` στον γενικό `Exception` handler αντί για καθαρό
`CatalogueNotFoundException`. Ο ενιαίος handler (`mapError`) καλύπτει πλέον κάθε κλήση
αυτόματα:

- **403 → `CatalogueForbiddenException`**, **404 → `CatalogueNotFoundException`**, **409
  → `CatalogueConflictException`**, **422 → `CatalogueUnprocessableException`**,
  **408/504 → `CatalogueTimeoutException`**.
- Οποιοδήποτε άλλο 5xx → `CatalogueUnavailableException`· οποιοδήποτε άλλο 4xx (π.χ.
  token που το Catalogue απορρίπτει) → `CatalogueBadResponseException`, με
  `log.error` γιατί σημαίνει misconfiguration εδώ, όχι λάθος του caller.
- Transport-level αποτυχίες (connection refused, connect/read timeout που δεν έφτασε
  καν σε HTTP status — `ResourceAccessException`) μεταφράζονται ξεχωριστά: αν η αιτία
  είναι `HttpTimeoutException` → `CatalogueTimeoutException`, αλλιώς →
  `CatalogueUnavailableException`. Ο διαχωρισμός timeout/unavailable κρατιέται ξεχωριστός
  μέχρι το 504/503 status code και τα logs — **όχι επειδή υπάρχει ήδη retry**, καμία
  retry λογική δεν υπάρχει πουθενά στο codebase σήμερα· ο διαχωρισμός απλώς αφήνει
  περιθώριο για μελλοντικό retry-on-timeout, χωρίς να τον υλοποιεί.

### Logging

Ο γενικός catch-all handler κάνει `log.error("Unhandled exception", e)` (πλήρες stack
trace, χωρίς redaction). Το `CatalogueForbiddenException` handler κάνει πλέον
`log.warn("Forbidden: {}", ...)` — security-audit trail για 403. Τα 404/400 παραμένουν
χωρίς logging, σκόπιμα (αναμενόμενη κίνηση, όχι κάτι ασυνήθιστο).

### Correlation / tracing

**Request-id υπάρχει πλέον, distributed tracing όχι.** `CorrelationIdFilter`
(`infrastructure/logging`, `@Order(HIGHEST_PRECEDENCE)` — τρέχει πριν από ΟΛΟ το chain,
ακόμα και πριν το Spring Security's δικό του): διαβάζει το `X-Request-Id` header αν
υπάρχει, αλλιώς φτιάχνει ένα νέο `UUID`, το βάζει σε `MDC` (καθαρίζεται σε `finally`,
γιατί τα servlet threads είναι pooled/επαναχρησιμοποιούνται) και το επιστρέφει πάντα ως
response header. Το `logging.pattern.level` property injects το `%X{requestId}` σε κάθε
log line. Το `GlobalExceptionHandler` διαβάζει το ίδιο MDC value και το βάζει στο
`ErrorEnvelope.requestId` — οπότε ένα error response και τα logs του ίδιου request
μπορούν να συνδεθούν με το ίδιο id, χωρίς να χρειάζεται καμία distributed tracing
βιβλιοθήκη. **Δεν υπάρχει ακόμα** πραγματική distributed tracing (καμία Micrometer
Tracing/OpenTelemetry εξάρτηση στο classpath) — αυτό θα χρειαστεί μόνο αν/όταν
χρειαστεί να συνδεθεί ένα request cross-service (π.χ. μέχρι το Catalogue). Το Actuator
εκθέτει ακόμα μόνο `health,info`.

---

## Περίληψη ανοιχτών θεμάτων

**Αναβλήθηκαν σκόπιμα (deferred), με αιτιολόγηση — όχι ξεχασμένα:**

- Πραγματικό distributed tracing (Micrometer Tracing/OpenTelemetry). Το request-id
  correlation (βλ. ενότητα 2 παραπάνω) καλύπτει ήδη το single-service use case, αλλά
  δεν προπαγάρεται cross-service. Δεν είναι κάτι που μπορεί να κλείσει μονομερώς εδώ:
  για να έχει νόημα ένα πραγματικό trace ανάμεσα σε MuseotekBox και Catalogue, πρέπει
  να μπει το ίδιο instrumentation (Micrometer Tracing + tracing backend) και στο
  Catalogue — αλλιώς το trace σταματάει στα όρια του MuseotekBox και δεν λέει τίποτα
  παραπάνω από το υπάρχον request-id. Εξαρτάται δηλαδή από αλλαγή σε άλλο repo/service,
  όχι μόνο σε τεχνικό κόστος εδώ.
- Το `ProjectAccessGuard`-invariant (κάθε project-scoped local feature πρέπει να το
  καλεί πρώτο, βλ. `architecture-and-classes.md`, ενότητα 2) δεν έχει σήμερα κανέναν
  automated enforcement μηχανισμό (π.χ. ArchUnit rule) — στηρίζεται αποκλειστικά στο
  README/code review. Εξετάστηκαν και απορρίφθηκαν ρητά δύο εναλλακτικές: (α) ένα
  ArchUnit test που να επιβεβαιώνει ότι κάθε `application/<feature>` class που αγγίζει
  ένα project-scoped repository καλεί και το `ProjectAccessGuard` — εφικτό ήδη, αλλά
  ατελές (naming-heuristic, όχι πραγματικός έλεγχος)· (β) δομική αλλαγή ώστε κάθε
  project-scoped repository πρόσβαση να περνάει υποχρεωτικά μέσα από το guard (compile
  error αν παραλειφθεί) — πιο σωστό μακροπρόθεσμα, αλλά δεν υπάρχει ακόμα κανένα
  πραγματικό **project**-scoped local feature πάνω στο οποίο να σχεδιαστεί σωστά· το
  `Box` (`web/box/`+`application/box/`, δες `architecture-and-classes.md`, ενότητα 2)
  απέκτησε πλέον δικό του layer, αλλά είναι **org**-scoped (μέσω του νέου
  `OrgAccessGuard`, όχι το `ProjectAccessGuard`) — άρα δεν αλλάζει αυτό το item. Θα
  επανεξεταστεί όταν προστεθεί το πρώτο πραγματικό project-scoped feature (π.χ. η
  ανάθεση project σε ένα `Box`, βλ. `architecture-and-classes.md`).

**Λυμένα:**
- ~~Κανένας έλεγχος για org-scoped, καθαρά τοπικά write paths~~ — λυμένο: μέχρι το
  `Box` (`application/box/`, βλ. `architecture-and-classes.md`, ενότητα 2), κάθε
  org/project-scoped endpoint ήταν authorized μόνο επειδή προωθούσε το JWT στο Catalogue
  σε κάθε κλήση και άφηνε το δικό του 403 να κάνει τη δουλειά· ένα org-scoped endpoint
  πάνω σε 100% τοπικά δεδομένα (χωρίς καμία κλήση Catalogue στη ροή του) θα ήταν το
  πρώτο πραγματικά ανεξέλεγκτο local write path. Νέο `OrgAccessGuard`
  (`application/orgaccess/`) κλείνει αυτό ρητά: καλεί `CatalogueClient.getOrganisation
  (orgId)` (ήδη access-checked ανά caller στο ίδιο το Catalogue) και αφήνει 403/404 να
  προχωρήσουν αμετάβλητα — πραγματικό authorization boundary, όχι cosmetic. Το `Box`
  είναι ο πρώτος (και μοναδικός, σήμερα) caller του.
- ~~`ddl-auto=update` σε production~~ — λυμένο: το Liquibase (`org.liquibase:liquibase-core`)
  είναι πλέον ο owner του schema, με ένα hand-written baseline changelog
  (`db/changelog/sql/01-initial-schema.sql`, formatted-SQL style, ένα changeset ανά
  table) που καλύπτει και τα 10 υπάρχοντα tables (incl. το `box_projects` join table
  και τα 4 JOINED-inheritance subtype tables του `ScanObject`). `ddl-auto` έγινε
  `validate` παντού, incl. τα tests — βλ. `dependencies-and-config.md`, ενότητα 2, για
  τα νέα properties. Καμία αλλαγή συμπεριφοράς σκόπιμα: το FK από κάθε subtype table
  προς `scan_objects` **δεν** έχει `ON DELETE CASCADE`, ταιριάζοντας με ό,τι το
  Hibernate `ddl-auto=update` παρήγαγε ήδη — ένα cascade-delete θα ήταν πραγματική
  αλλαγή συμπεριφοράς, εκτός scope εδώ, ξεχωριστή απόφαση αν χρειαστεί ποτέ.
- ~~`uniche.tool.slug` property ήταν dead config~~ — λυμένο: το `OrganisationController`
  το κάνει πλέον `@Value`-inject και το χρησιμοποιεί ως `toolSlug` σε κάθε
  `CatalogueCreateProjectRequest`, αντί να το δέχεται ως πεδίο από τον client
  (`CreateProjectRequest.toolSlug` αφαιρέθηκε). Αυτό δεν είναι απλά wiring ενός dead
  property — κλείνει δομικά την ίδια κατηγορία bug με το toolSlug mismatch bug που
  αναφέρεται πάνω σε αυτή τη σελίδα (frontend hardcoded λάθος slug): ο client δεν
  μπορεί πλέον να στείλει λάθος τιμή, γιατί δεν στέλνει καμία. Βρέθηκε και διορθώθηκε
  παράλληλα ότι το default του ίδιου property ήταν λάθος (`museotek-box` αντί για το
  σωστό, χωρίς παύλα, `museotekbox` — το ίδιο σωστό value που ήδη χρησιμοποιεί το
  Catalogue `authoring_tools` seed).
- ~~Swagger UI/OpenAPI public σε production~~ — λυμένο: `springdoc.api-docs.enabled` /
  `springdoc.swagger-ui.enabled` πλέον `${SWAGGER_ENABLED:false}` — off by default,
  ρητά `SWAGGER_ENABLED=true` μόνο σε local/dev (βλ. `dependencies-and-config.md`,
  ενότητα 2). Admin-only access εξετάστηκε και απορρίφθηκε: το `platformAdmin` flag ζει
  μόνο στο Catalogue (`CatalogueMeAuthorizationDto`), όχι σαν JWT claim, άρα θα
  χρειαζόταν είτε ένα live Catalogue round-trip σε κάθε Swagger request είτε ένα νέο
  Keycloak realm-role claim μόνο γι' αυτό — disproportionate σε σχέση με το πρόβλημα
  (public API-schema disclosure, όχι auth bypass, αφού το "try it out" πάντα χρειάζεται
  πραγματικό bearer token για να πετύχει έναντι του `anyRequest().authenticated()`).
- ~~Race condition στο `JitUserProvisioningService.provision()`~~ — λυμένο: όχι πλέον
  `@Transactional`, catch+retry στο `DataIntegrityViolationException` (βλ. ενότητα 1
  παραπάνω).
- ~~Exception μέσα στο `JitUserProvisioningFilter` δεν παίρνει το `ErrorEnvelope`
  σχήμα~~ — λυμένο διαφορετικά: το filter τυλίγει το `provision()` call σε try/catch
  δικό του, log και συνέχεια του chain· δεν χρειάζεται πια να φτάσει στο
  `GlobalExceptionHandler` (βλ. ενότητα 1 παραπάνω).
- ~~`ErrorEnvelope.details` εκθέτει το rejected value μέσω `FieldError::toString()`~~ —
  λυμένο: πλέον μόνο `"<field>: <default message>"`, καμία τιμή δεν εμφανίζεται (βλ.
  ενότητα 2 παραπάνω).
- ~~`CatalogueClient`'s `RestClient` χωρίς connect/read timeout~~ — μερικώς λυμένο: 5s
  connect / 10s read timeout προστέθηκαν, άρα δεν μπλοκάρει πια το thread επ' αόριστον
  (βλ. ενότητα 2 παραπάνω). Το status-code mapping του resulting timeout είναι πλέον
  λυμένο — δες το ~~409/422/502/503/504 status-code mapping~~ bullet παρακάτω.
- ~~Κανένα request-id/correlation~~ — λυμένο: `CorrelationIdFilter` (βλ. ενότητα 1
  παραπάνω) + `logging.pattern.level` (βλ. `dependencies-and-config.md`, ενότητα 2) +
  `ErrorEnvelope.requestId` (βλ. ενότητα 2 παραπάνω).
- ~~`uniche.catalogue.base-url` default δείχνει σε production~~ — λυμένο: πλέον κενό
  default (βλ. `dependencies-and-config.md`, ενότητα 2).
- ~~403 (forbidden) δεν logάρεται~~ — λυμένο: `GlobalExceptionHandler.handleForbidden`
  κάνει πλέον `log.warn` (βλ. ενότητα 2 παραπάνω).
- ~~Το default του `issuer-uri` δείχνει ακόμα σε production~~ — λυμένο: πλέον κενό
  default, ίδιο pattern με το `uniche.catalogue.base-url`. Επιπλέον προστέθηκε
  `RequiredPlatformPropertiesCheck` (`EnvironmentPostProcessor`), που κάνει fail-fast
  στο boot — πριν από οποιοδήποτε bean — αν λείπει `issuer-uri` ή
  `uniche.catalogue.base-url` (βλ. `dependencies-and-config.md`, ενότητα 2).
- ~~409/422/502/503/504 status-code mapping~~ — λυμένο: `CatalogueClient` καταχωρεί πλέον
  ένα ενιαίο `.defaultStatusHandler(...)` (αντί για ανά-μέθοδο `.onStatus()`, που ήταν
  η ρίζα του πραγματικού bug στο `createProject`/toolSlug — βλ. ενότητα 2 παραπάνω) που
  μεταφράζει 409 → `CatalogueConflictException`, 422 → `CatalogueUnprocessableException`,
  408/504 → `CatalogueTimeoutException`, άλλα 5xx → `CatalogueUnavailableException`,
  άλλα 4xx → `CatalogueBadResponseException`· και transport-level timeouts/outages
  (`ResourceAccessException` χωρίς καν HTTP status) ξεχωριστά. Το 422 ήταν ξεχωριστό
  item επειδή σημασιολογικά διαφέρει από bean-validation 400 (`VALIDATION_ERROR`) — το
  Catalogue απορρίπτει ένα syntactically-valid αίτημα για λόγους business-rule, όχι
  malformed payload· γι' αυτό πήρε δικό του exception/code (`UPSTREAM_VALIDATION_ERROR`)
  αντί να μπερδευτεί με το τοπικό 400.
- ~~Malformed path variable (π.χ. `orgId=1` αντί για UUID) γινόταν 500 αντί για 400~~ —
  λυμένο: `GlobalExceptionHandler` πλέον πιάνει το
  `MethodArgumentTypeMismatchException` (πετιέται πριν φτάσει καν σε controller code) και
  το μεταφράζει σε 400 `INVALID_PARAMETER`. Το μήνυμα αναφέρει το όνομα του parameter
  και τον αναμενόμενο τύπο (π.χ. `"Invalid value for parameter 'orgId': expected UUID"`)
  αλλά ποτέ την ίδια την τιμή που στάλθηκε — ίδιο σκεπτικό με το `ErrorEnvelope.details`
  fix παραπάνω (δεν εκτίθεται rejected value).
