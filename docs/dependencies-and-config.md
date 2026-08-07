# Dependencies & Configuration

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
| `org.postgresql:postgresql` | JDBC driver | `spring.datasource.*` | Βλ. παρακάτω ("Γιατί PostgreSQL")                                                                                                                                                                                          | Απαραίτητο τώρα |
| `org.liquibase:liquibase-core` | Owns the schema — DDL μέσω versioned changelogs αντί για `ddl-auto=update` | `db/changelog/*`, τρέχει αυτόματα στο startup πριν το Hibernate | `ddl-auto=update` σε production δεν έχει ιστορικό/rollback — βλ. `security-and-errors.md` open items | Απαραίτητο τώρα |
| `lombok` | Παράγει getters/setters/no-args constructor στα entities | Όλα τα `domain/*` entities (`@Getter @Setter @NoArgsConstructor`) | Μειώνει boilerplate σε JPA entities | Απαραίτητο τώρα, αλλά μόνο σε compile-time, ποτέ runtime |
| `springdoc-openapi-starter-webmvc-ui` | Αυτόματο OpenAPI spec + Swagger UI (`/api-docs`, `/swagger-ui.html`) | Καμία άμεση αναφορά στον κώδικα — δουλεύει reflectively πάνω στα `@RestController`/DTOs | Δίνει στο frontend team (και σε οποιονδήποτε manual tester) ζωντανό API contract χωρίς να τον γράφουμε με το χέρι                                                                                                                    | Απαραίτητο τώρα, θα παραμείνει — off by default σε production πλέον (βλ. ενότητα 2 παρακάτω, `springdoc.*.enabled`). |
| `spring-boot-devtools` | Live restart/reload σε local dev | — | `optional` + `scope=runtime`: το Spring Boot repackaging plugin το αγνοεί αυτόματα στο τελικό jar, άρα ποτέ δεν "μπαίνει" σε production                                                                                              | Μόνο για local dev, ποτέ production |
| `spring-boot-starter-test` (test scope) | JUnit 5, Mockito, AssertJ, MockMvc | Χρησιμοποιείται σε όλα τα test classes κάτω από `src/test/java` | Standard Spring Boot testing stack                                                                                                                                                                                                   | Απαραίτητο για τα tests που προστίθενται τώρα |
| `spring-security-test` (test scope) | Helpers όπως `SecurityMockMvcRequestPostProcessors.jwt()` για να στήνουμε authenticated test requests | Security integration tests | Χωρίς αυτό θα έπρεπε να φτιάχνουμε το `Authentication`/`SecurityContext` με το χέρι σε κάθε test                                                                                                                                     | Απαραίτητο για τα tests που προστίθενται τώρα |

### Γιατί PostgreSQL

Η τρέχουσα κατάσταση του repo είναι **PostgreSQL**, όχι MySQL. Λόγοι:

1. Το `Block.content` (μελλοντικό entity στο roadmap — σκηνές/κανόνες της εμπειρίας,
   δεν υπάρχει ακόμα σε αυτό το repo) σχεδιάζεται ως JSON column. Το native `JSONB`
   της Postgres ταιριάζει καλύτερα σε αυτή την ανάγκη από το `JSON` type της MySQL
   (indexing/query capabilities).
2. Το `UNICHEcatalogue` (αδερφό service της πλατφόρμας UNICHE) τρέχει ήδη PostgreSQL —
   για λόγους συνέπειας είναι καλύτερο να τρέχουν το ίδιο σύστημα βάσεων.

---

## 2. Application properties

| Property | Τι ελέγχει | Γιατί χρειάζεται | Default | Πού επιτρέπεται default                                                                                                                                                      | Υποχρεωτικό σε prod; | Secret; | Startup validation |
|---|---|---|---|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---|---|---|
| `server.port` | HTTP port | Βασικό network binding | `8080` | Local/dev· σε prod συνήθως ορίζεται από orchestrator env                                                                                                                     | Όχι απαραίτητα (default ok) | Όχι | Κανένα ρητό — Spring απλά αποτυγχάνει αν το port είναι κατειλημμένο |
| `server.servlet.context-path` | Fixed path prefix `/museotekbox` | Πλατφόρμα-wide σύμβαση path per tool | `/museotekbox` (hardcoded, χωρίς env override) | Όλα — δεν είναι per-environment axis                                                                                                                                         | Ναι, όπως είναι | Όχι | — |
| `spring.application.name` | Λογικό όνομα για logging/actuator | Identification | `museotek-box-backend` (hardcoded) | Όλα                                                                                                                                                                          | Ναι | Όχι | — |
| `spring.datasource.url` | JDBC connection string | Ποια DB | `jdbc:postgresql://localhost:5432/museotekbox` | Μόνο local dev                                                                                                                                                               | **Ναι, πρέπει να οριστεί ρητά** | Όχι (host μόνο, χαμηλή ευαισθησία) | Fail-fast αν η DB δεν είναι reachable (connection pool init στο startup) |
| `spring.datasource.username` | DB user | Αυθεντικοποίηση DB | κενό | Μόνο local dev με trust/peer auth                                                                                                                                            | **Ναι** | Οριακά (username, όχι μυστικό αυτό καθαυτό) | Fail-fast (άδειο username θα αποτύχει connection) |
| `spring.datasource.password` | DB password | Αυθεντικοποίηση DB | κενό | **Μόνο local dev**                                                                                                                                                           | **Ναι, υποχρεωτικό** | **Ναι, μυστικό** | Fail-fast (θα αποτύχει το connection) |
| `spring.datasource.driver-class-name` | JDBC driver class | Πρέπει να ταιριάζει με το URL scheme | `org.postgresql.Driver` (hardcoded) | Όλα                                                                                                                                                                          | Ναι | Όχι | — |
| `spring.jpa.hibernate.ddl-auto` | Αν το Hibernate αλλάζει το schema αυτόματα | Το Liquibase είναι πλέον ο μόνος owner του schema — το Hibernate μόνο επιβεβαιώνει ότι τα mappings ταιριάζουν | `validate` (hardcoded, χωρίς env override) | Όλα, χωρίς εξαίρεση — ούτε τα tests χρησιμοποιούν `update` πλέον | Ναι, όπως είναι | Όχι | Fail-fast στο boot: αν το changelog δεν ταιριάζει με τα entity mappings, το context startup αποτυγχάνει με `SchemaManagementException` |
| `spring.liquibase.enabled` | Αν τρέχει το Liquibase migration στο startup | Χωρίς αυτό, το `ddl-auto=validate` θα απέτυχε σε άδεια DB — καμία λύση δεν φτιάχνει το schema | `true` (hardcoded) | Όλα | Ναι | Όχι | — |
| `spring.liquibase.change-log` | Πού βρίσκεται το master changelog | Entry point του migration tree | `classpath:db/changelog/db.changelog-master.xml` (hardcoded) | Όλα | Ναι | Όχι | Fail-fast αν το path δεν υπάρχει στο classpath |
| `spring.liquibase.contexts` | Ποια Liquibase changesets τρέχουν (context-based filtering) | Δεν υπάρχει ακόμα κανένα changeset με context tag — προστέθηκε ως μελλοντικό hook, όχι επειδή χρειάζεται σήμερα | `${LIQUIBASE_CONTEXTS:}` (κενό = όλα τα changesets τρέχουν) | Όλα | Όχι | Όχι | — |
| `spring.jpa.open-in-view` | Open-session-in-view pattern | Ρητά off — σωστό default, αποφεύγει lazy-loading εκτός transaction | `false` | Όλα                                                                                                                                                                          | Ναι | Όχι | — |
| `spring.jpa.show-sql` | SQL logging στο stdout | Debugging | `false` (hardcoded) | Σωστό για prod· τοπικά flip χειροκίνητα μέσω command-line flag αντί για env var                                                                                              | Ναι, όπως είναι | Όχι | — |
| `spring.jpa.properties.hibernate.dialect` | SQL dialect | Πρέπει να ταιριάζει με τη DB | `PostgreSQLDialect` (hardcoded) | Όλα                                                                                                                                                                          | Ναι | Όχι | — |
| `spring.jpa.properties.hibernate.format_sql` | Pretty-print SQL όταν είναι ενεργό το show-sql | Αναγνωσιμότητα σε dev | `true` | Αδιάφορο (no-op αν show-sql=false)                                                                                                                                           | Ναι, όπως είναι | Όχι | — |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | Ποιο Keycloak realm εκδίδει έμπιστα tokens | Θεμέλιο της αυθεντικοποίησης | κενό (**όχι πλέον production URL**) | Δεν δείχνει πια σιωπηλά σε production | **Ναι, πάντα ρητά** | Όχι (public URL) | **Fail-fast στο boot**: `RequiredPlatformPropertiesCheck` (`EnvironmentPostProcessor`, βλ. παρακάτω) |
| `uniche.catalogue.base-url` | Ποιο Catalogue instance καλείται | Θεμέλιο του org/project/authorization integration | κενό (**όχι πλέον production URL**) | Δεν δείχνει πια σιωπηλά σε production | **Ναι, πάντα ρητά** | Όχι | **Fail-fast στο boot**: `RequiredPlatformPropertiesCheck` (`EnvironmentPostProcessor`, βλ. παρακάτω) |
| `uniche.tool.slug` | Δηλώνει το tool slug αυτού του backend στην πλατφόρμα — το `OrganisationController` το injects (`@Value`) και το χρησιμοποιεί ως `toolSlug` σε κάθε `CatalogueCreateProjectRequest`, αντί να το δέχεται από τον client | Ο server ξέρει ήδη ποιο tool είναι — δεν έχει νόημα να το ρωτά τον client σε κάθε request | `museotekbox` (χωρίς παύλα — πρέπει να ταιριάζει ακριβώς με το Catalogue `authoring_tools` seed) | Όλα | Ναι, αν διαφέρει ανά environment/instance | Όχι | Κανένα ρητό — λάθος τιμή αποτυγχάνει αργότερα, στο πρώτο `createProject` call, ως Catalogue 404 (βλ. ιστορικό bug στο `security-and-errors.md`) |
| `museotek.cors.allowed-origins` | Ποια origins επιτρέπονται (CORS) | Το Vue frontend πρέπει να μπορεί να καλέσει το API από browser | `http://localhost:5173` (Vite dev) | Μόνο local dev                                                                                                                                                               | **Ναι, πάντα ρητά ανά environment** | Όχι | Κανένα — δεν ελέγχεται π.χ. format (scheme/trailing slash), λάθος τιμή αποτυγχάνει σιωπηλά μόνο στο runtime browser request, όχι στο startup |
| `springdoc.api-docs.enabled` / `springdoc.swagger-ui.enabled` | Αν το OpenAPI JSON / Swagger UI εξυπηρετούνται καθόλου | Έλεγχος έκθεσης του API schema ανά environment | `${SWAGGER_ENABLED:false}` (**off αν δεν οριστεί**) | Ρητά `SWAGGER_ENABLED=true` μόνο local/dev· prod αφήνεται στο fail-safe default | **Ναι, πρέπει να μείνει false/άσχετο σε prod** | Όχι | Κανένα ρητό — αν μείνει ασυμπλήρωτο, απλά δεν εξυπηρετούνται τα endpoints (404), όχι startup failure |
| `springdoc.api-docs.path` / `springdoc.swagger-ui.path` / `springdoc.swagger-ui.try-it-out-enabled` | Πού ζει το OpenAPI JSON / Swagger UI, αν επιτρέπεται live "try it out" | Dev/QA convenience, API contract visibility | `/api-docs`, `/swagger-ui.html`, `true` | Ίδιο σε όλα τα environments — αδιάφορο πλέον όταν το `enabled` παραπάνω είναι false, μιας και τα endpoints δεν υπάρχουν καν | — | Όχι | — |
| `management.endpoints.web.exposure.include` | Ποια actuator endpoints εκτίθενται | Ops/monitoring χωρίς να εκτεθούν επικίνδυνα endpoints (`env`, `beans`, `heapdump`) | `health,info` (σωστά συντηρητικό) | Όλα — σκόπιμα fixed, όχι per-environment axis (είναι security control)                                                                                                       | Ναι, όπως είναι | Όχι | — |
| `logging.pattern.level` | Injects το `%X{requestId}` (MDC, βλ. `CorrelationIdFilter` στο `security-and-errors.md`, ενότητα 1) σε κάθε log line | Log-to-request correlation χωρίς distributed tracing | `%5p [reqId=%X{requestId}]` (hardcoded) | Όλα | Ναι, όπως είναι | Όχι | — |

### `RequiredPlatformPropertiesCheck` — fail-fast στο boot

`infrastructure/config/RequiredPlatformPropertiesCheck` υλοποιεί
`EnvironmentPostProcessor` και τρέχει **πριν φτιαχτεί οποιοδήποτε bean**, άρα και πριν
το datasource. Ελέγχει ότι το `issuer-uri` και το `uniche.catalogue.base-url` έχουν μη
κενή τιμή· αν όχι, πετάει `IllegalStateException` με το όνομα του env var που λείπει.
Χωρίς αυτό, ένα deployment χωρίς `IDP_ISSUER_URI`/`CATALOGUE_BASE_URL` είτε θα
αποτύγχανε αργότερα με ένα άσχετο DB μήνυμα (αν η datasource init προηγηθεί), είτε —
χειρότερα — θα ξεκινούσε κανονικά και θα απέτυχε μόνο lazily, στην πρώτη πραγματική
κλήση προς Catalogue/Keycloak. Καταχωρείται μέσω
`META-INF/spring.factories` (το μόνο σημείο του classpath που το Spring Boot διαβάζει
πριν υπάρχει `ApplicationContext`).
