# Write-model comparison — per-entity REST vs. document model

Status: **approved — document model chosen.** Companion to the two full proposals —
this document doesn't repeat their content, it weighs them against each other:
[`proposal-scene-block-rule.md`](./proposal-scene-block-rule.md) (per-entity REST) and
[`proposal-scene-block-rule-document-model.md`](./proposal-scene-block-rule-document-model.md)
(document model).

**Known frontend plan, factored in below:** the authoring UI will batch edits locally
(e.g. drag 3 blocks around, add 2 rules) and persist them with a single "Save" action,
not save each small edit immediately and not autosave continuously.

## Per-entity REST

**Pros**
- Matches every existing feature in this backend (Project, Box, ScanObject) exactly —
  same guard pattern, same test shape, same controller/DTO conventions. No new
  concepts for whoever picks up this code later.
- Small, targeted requests. A single rename or a single new rule is a small,
  independent call.
- Simpler to reason about: one endpoint, one entity, one operation. No document-wide
  validation pass to write.

**Cons**
- Block/Rule endpoints only get `sceneId` from the URL, so each request needs an
  extra DB read to resolve which project it belongs to before the permission check
  can run (already called out as an implementation note in the proposal itself).

**Risks, specifically because of the batched-save plan**
- **No atomicity across the batch.** If a Save triggers 5 requests and the 3rd fails,
  the experience is left half-committed — the frontend has to detect this and either
  roll back the ones that succeeded or reconcile the draft against a partially-saved
  server state. Nothing in this proposal handles that; it would need to be designed
  separately, on the frontend.
- **Ordering/identity problem for new rows.** A rule created in the same Save as the
  block it targets can't be sent until the block's request has returned a real
  server-assigned id — the frontend must sequence "create block" before "create rule
  that targets it," rather than sending both in one shot. This gets harder, not
  easier, as batches grow (a scene, its blocks, and rules that reference each other,
  all new in one Save).
- **No built-in conflict detection.** Two overlapping Saves (e.g. two browser tabs,
  or a slow first Save followed by a second one before it returns) silently
  interleave writes across several independent requests, with no version check
  anywhere to catch it.

## Document model

**Pros**
- **Matches the planned frontend UX directly.** One Save action maps to exactly one
  `PUT`, carrying everything that changed. No client-side sequencing logic needed.
- **Atomic by construction.** The whole write happens in one database transaction —
  no partially-committed batch is possible; it's all-or-nothing exactly the way a
  "Save" button implies.
- **Solves the new-row ordering problem for free.** Client-invented keys
  (`scene_key`/`block_key`/`rule_key`) let a new rule reference a new block in the
  same payload, since identity doesn't depend on a server round trip.
- **Built-in conflict detection.** `If-Match`/version catches the overlapping-Saves
  case the per-entity model has no answer for.
- No project-lookup indirection for Block/Rule writes — every request is already
  scoped by `projectId` in the URL.

**Cons**
- New concepts nothing else in this backend uses yet: stable string keys separate
  from numeric ids, a document-wide validate/diff/apply write algorithm, per-project
  sequence counters, optimistic concurrency via `If-Match`. More to build and more
  for a future contributor to learn than reusing an established pattern.
- Reference validation (cross-project, cross-organisation) is resolved against an
  in-request document graph instead of simple FK lookups — genuinely different code
  from the per-entity model's version, not a small variation of it.

**Risks**
- **Deletion policy is still undecided** (flagged as open in that proposal itself) —
  whether a delete is blocked outright (matching the per-entity model) or allowed
  with the experience left in an "incomplete" state, flagged separately. This has to
  be settled before the write algorithm's diff/apply step (removing a row whose key
  disappears from the document) can be fully specified.
- **Payload size grows with the whole experience, on every Save**, not just with what
  changed. Believed to be a non-issue today because a project is expected to have
  only a small number of scenes (per the per-entity proposal's own reasoning about
  `Scene.position`), but that assumption hasn't been tested against a real, large
  experience.
- **Autosave is off the table**, not just unaddressed — a whole-document `PUT` on
  every few seconds of typing would be expensive and would race far more than a
  per-entity `PATCH` would. This is fine as long as the frontend plan really is
  batch-then-Save and never grows an autosave feature later; if that changes, this
  model would need rework (e.g. saving only a diff, not the whole document).

## What actually decides this

The batched-Save frontend plan removes the document model's biggest cost (new
machinery for a problem — atomic multi-row writes — that wouldn't exist under a
one-edit-at-a-time UX) while exposing a real, concrete gap in the per-entity model
(no atomicity, no ordering story, no conflict detection across a multi-request
batch) that would otherwise have to be reinvented on the frontend. Weighed against
that, the per-entity model's advantage — reusing this backend's existing pattern
exactly — is a one-time cost (new concepts to build once), not a recurring one.

**Decision:** document model, chosen for the reasons above.

**Still open:**
- `scan_object_type` granularity (needs product input, per both proposals).
