# Saving and publishing an experience with gaps

As of 2026-10-14. All paths start with `/api/v1`.

## What a gap is

A gap is a choice the curator hasn't made yet:

- the start scene (`isStart: true`) has no start card (`initCardTypeId`);
- an `IMAGE`, `ILLUSTRATION`, `VIDEO`, `SCREEN`, `AUDIO` or `BOX_VIDEO` block has no
  `mediaId`;
- a `SCAN` rule has no card (`scanObjectTypeId`);
- a `VIDEO_ENDED` rule is in a scene that plays no video. "Plays a video" means a
  `VIDEO` or `BOX_VIDEO` block, or a `SCREEN` block showing a video file;
- a `BOX_AUDIO` effect has no sound (`url`).

The editor's to-fix banner should check the same things.

## When gaps are refused

| The experience is | Saving with gaps (`PUT /projects/{id}/experience`) | Publishing (`PUT /organisations/{orgId}/boxes/{boxId}/projects/{id}`) |
|---|---|---|
| not on any box | allowed | refused |
| on at least one box | **refused** | refused |

A box plays the latest save of an experience, so once an experience is on a box, every
save has to keep it complete. Saves without gaps work as usual.

Both refusals give the same response, `422`, with the gaps in `details`:

```json
{
  "code": "EXPERIENCE_NOT_READY",
  "message": "The experience is published on box Hall A, so it can't be saved with gaps: fix them, or unpublish it first",
  "details": [
    "scene '1' (Welcome) is the start scene but has no start card",
    "block '4' has no file chosen"
  ],
  "requestId": "..."
}
```

When a save is refused, nothing is saved, and the version doesn't change.

## What the frontend should do

1. **Show that the experience is published** while it's being edited, and on which
   boxes. To find out, check the experience's id in each box's list:
   `GET /organisations/{orgId}/boxes/{boxId}/projects`.
2. **Before saving a published experience**, check the to-fix banner. If it has
   anything, don't send the save. Tell the user to fix the gaps first, or offer to
   unpublish. Unpublishing is
   `DELETE /organisations/{orgId}/boxes/{boxId}/projects/{id}`, once per box. Only
   managers can do it.
3. **If a save or a publish still gets `422 EXPERIENCE_NOT_READY`**, show `details` as
   the list of things to fix. The backend's check is the final one, so handle this even
   if step 2 found nothing.

## Related: the start card

Visitors pick an experience by placing its start card on the box, so two experiences on
the same box can't share a start card. Publishing, or saving a published experience
with a start card another experience on the same box already uses, gives
`409 START_CARD_IN_USE`. The `message` names the other experience or the box.
