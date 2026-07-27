## Base template

- **What exactly will be implemented.**
- **Which business requirement it covers.**
- **Which files/components will be added or changed.**
- **What changes will be made to the database** (migration, new table, new column, index).
- **What API contracts will be added or changed** (new endpoint, request/response schema change, breaking vs non-breaking).
- **What's needed from the frontend** (new call, change to an existing one, UI implications).
- **What dependencies exist on Catalogue, the Identity Provider, or other services** (new Catalogue endpoint that must exist first? new Keycloak claim/scope?).
- **Which technical decisions need to be made** (e.g. sync vs async, new library, caching strategy).
- **What the risks and edge cases are.**
- **Which tests will be added.**

## Commit breakdown

Every proposal comes with a proposed commit split, e.g.:
1. Migration / schema change (if any) — DDL only, no logic applied.
2. Domain entity + repository.
3. Application layer (use case/query) + companion-sync if needed.
4. Web layer (controller + request/response DTOs).
5. Tests (may be folded into each commit instead of separate, depending on size).

## Extra per category (only when relevant to the specific scope)

### Per dependency
- What it's for.
- Where in the code it's used.
- Why it was chosen over alternatives.
- Whether it's needed now or intended for future use.
- Version policy and upgrade process.

### Per application class/use case
- What responsibility it has.
- Which endpoint calls it.
- What inputs/outputs it has.
- Which external resources/repositories it affects.
- Possible failure modes.
- How Catalogue ↔ local database consistency is ensured.
- Why it needs to be a separate class (doesn't belong to another layer).
- Unit/integration tests for: success, validation failures, Catalogue 401/403/404/5xx,
  Catalogue timeout/unavailability, local DB failure after a successful Catalogue
  request, concurrent requests/retries, create/update/delete/restore sync.

### Per security class
- What responsibility it has.
- Where in the filter chain it executes.
- What assumptions it makes about JWT/Identity Provider.
- How it fails, what HTTP response is returned.
- How service accounts are handled.
- Why JIT provisioning happens on every request (if relevant).
- Tests for: missing/malformed token, wrong issuer, wrong/missing audience, expired
  token, user vs service-account token, public vs protected endpoints, concurrent
  first requests for the same user, DB failure during JIT provisioning.

### Error handling
- Common error response contract.
- How validation/authentication/authorization/not-found errors are translated.
- How Catalogue errors are translated.
- When 400/401/403/404/409/422/500/502/503/504 apply.
- What gets logged, what must not be exposed.
- Correlation/tracing strategy.
- Tests per error category.

### Per application property
- What it controls.
- Why it's needed.
- Default value.
- In which environments a default is allowed.
- Mandatory in production?
- Does it contain a secret?
- What validation happens at startup.

---

See [`architecture-qa.md`](./architecture-qa.md) for a complete example of applying
this template to the current state of the backend.
