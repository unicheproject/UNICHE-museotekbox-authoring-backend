# MuseotekBox experience API flow

As of 2026-10-01.

The experience is saved and loaded through two endpoints, `GET` and
`PUT /api/v1/projects/{id}/experience`. Every save sends the whole experience: where it
plays (`output`), the wizard's source (`flow`), the variables, and the scenes with their
blocks and rules.

## Endpoint flow

The calls the frontend makes, in order, from creating an experience to putting it on a
Box. All paths start with `/api/v1`.

| Step | The user | Call |
|---|---|---|
| 1 | Opens "Create an experience" and needs the card list | `GET /organisations/{orgId}/scan-object-types` |
| 2 | Clicks **Create experience** (name) | `POST /organisations/{orgId}/projects` → returns the project `id` |
| 3 | First save: where it plays, the starting flow, the first activity | `PUT /projects/{id}/experience` with `If-Match: "0"` |
| 4 | Opens the editor later | `GET /projects/{id}/experience` → body + `ETag` |
| 5 | Edits Start, Move on, activities or End, then saves | `PUT /projects/{id}/experience` with `If-Match` = the last `ETag` |
| 6 | Someone else saved first | the same `PUT` → **409** with the current document |
| 7 | A manager puts it on a Box | `PUT /organisations/{orgId}/boxes/{boxId}/projects/{id}` |

Steps 4–6 repeat for as long as the curator edits. Every save sends the whole
experience, never one panel at a time.

## Example first save

A complete body for step 3 on a new project. It can be sent as-is: plain JSON, no
comments. Keys start at `"1"` because a new project's counters start at 1.

```
PUT /api/v1/projects/{id}/experience
If-Match: "0"
Content-Type: application/json
```

```json
{
  "output": "DISPLAY",
  "flow": {
    "schemaVersion": 1,
    "content": { "details": { "startNext": { "how": "time", "seconds": 5 } }, "islands": [] }
  },
  "variables": [
    { "key": "correct", "kind": "NUMBER", "initial": "0" }
  ],
  "scenes": [
    {
      "sceneKey": "1", "name": "Welcome", "position": 0, "isStart": true,
      "blocks": [ { "blockKey": "1", "type": "TEXT", "position": 0, "content": { "text": "Welcome" } } ],
      "rules": [
        { "ruleKey": "1", "position": 0,
          "trigger": { "type": "TIMER_ELAPSED", "seconds": 5 },
          "condition": null, "effects": [],
          "destination": { "type": "GO_TO", "targetSceneKey": "2" } }
      ]
    },
    {
      "sceneKey": "2", "name": "Question 1", "position": 1, "isStart": false,
      "blocks": [ { "blockKey": "2", "type": "TEXT", "position": 0, "content": { "text": "Is this a kouros?" } } ],
      "rules": [
        { "ruleKey": "2", "position": 0,
          "trigger": { "type": "SCAN_OTHER" },
          "condition": null,
          "effects": [ { "type": "REPLY", "color": "green", "text": "Correct!" },
                       { "type": "ADD_NUMBER", "variable": "correct", "amount": 1 } ],
          "destination": { "type": "END" } }
      ]
    }
  ]
}
```

The response is `200` with `ETag: "1"`: the same document as stored, plus `version` and
the next free keys (`nextSceneSeq`, `nextBlockSeq`, `nextRuleSeq`). To test a `SCAN`
rule, use a real `scanObjectTypeId` from step 1.

## Media in an experience

Pictures, videos and sounds come from the organisation's media library. An experience
points to them **by media id**, never by URL or filename.

| Where | Field | Needs a |
|---|---|---|
| `IMAGE` block | `content.mediaId` | picture |
| `ILLUSTRATION` block | `content.mediaId` | picture |
| `VIDEO` block | `content.mediaId` | video |
| `SCREEN` block (question preset) | `content.mediaId`, optional | picture or video |
| `AUDIO` block | `content.mediaId` | sound |
| `BOX_VIDEO` block (the Box's own display) | `content.mediaId` | video |
| `REPLY` effect | `media` (picture instead of the colour) | picture |
| `REPLY` effect | `audio` | sound |
| `REPLY` effect | `boxVideo` | video |
| `BOX_AUDIO` effect | `url` | sound |

Example block: `{ "blockKey": "3", "type": "IMAGE", "position": 0, "content": { "mediaId": "6f1c2a…" } }`

- Leaving a media field empty is fine while the curator hasn't picked a file yet.
- A save is refused (`400`) if a media id isn't a valid id, doesn't exist in this
  organisation's media library, or is the wrong kind (for example a picture in `audio`).
- A media file can't be deleted (`409 MEDIA_IN_USE`) while any experience still uses it.

**Box-only experiences** (`"output": "BOX"`) have no screen:

- Blocks that need a screen (`TEXT`, `IMAGE`, `ILLUSTRATION`, `VIDEO`, `SCREEN`) are
  refused (`400`). Use `AUDIO` and `BOX_VIDEO`.
- A `REPLY` is just its sound: `color` is optional, and `media` (a picture) is refused.
- With a screen (`"output": "DISPLAY"`), every `REPLY` needs a `color`.

The media ids come from the media library endpoints:

| Call | Does |
|---|---|
| `POST /organisations/{orgId}/media` (multipart, field `file`) | upload. Returns the item with its `id` |
| `GET /organisations/{orgId}/media?kind=IMAGE` | list for a picker. `kind` is `IMAGE`, `VIDEO` or `AUDIO` |
| `GET /organisations/{orgId}/media/{mediaId}/content` | the file itself, for previews |
| `DELETE /organisations/{orgId}/media/{mediaId}` | delete |

The content URL needs the bearer token like every call, so `<img src>` can't load it
directly. Fetch it with the token and show it from a blob URL:

```ts
const response = await fetch(apiBaseUrl + item.contentUrl, { headers: { Authorization: 'Bearer ' + token } })
const objectUrl = URL.createObjectURL(await response.blob())   // <img :src="objectUrl">
```

## Versions and conflicts

Every save must say which version it is based on, so two editors can't silently
overwrite each other.

### What `ETag` and `If-Match` are

Both are standard HTTP headers. They travel next to the body, not inside it.

- **`ETag`** is a header the **server sends** in its response. It is a label for the
  version of the data you just received. Here it is simply the experience's version
  number in quotes, e.g. `ETag: "3"` means "this is version 3".
- **`If-Match`** is a header the **client sends** with a request that changes data. It
  means "only do this if the data is still at this version". Here the client sends back
  the `ETag` it last received, e.g. `If-Match: "3"` means "save this, but only if the
  experience is still at version 3".

If the version on the server is no longer the one the client names, someone else saved
in the meantime. The server then refuses the save instead of overwriting their work.
This pattern is called optimistic locking: nobody locks the experience while editing,
and a conflict is only detected at the moment of saving.

### Example: two editors at once

| Time | Anna | Nikos | Version on the server |
|---|---|---|---|
| 1 | opens the editor: `GET` → `ETag: "3"` | | 3 |
| 2 | | opens the editor: `GET` → `ETag: "3"` | 3 |
| 3 | saves: `PUT` with `If-Match: "3"` → `200`, `ETag: "4"` | | 4 |
| 4 | | saves: `PUT` with `If-Match: "3"` → **`409`** | still 4 |
| 5 | | reloads from the 409 body (version 4, with Anna's changes), redoes his edit, saves with `If-Match: "4"` → `200`, `ETag: "5"` | 5 |

Without `If-Match`, Nikos's save at time 4 would have silently wiped out Anna's changes.

### The rules

- Every `GET` and every successful `PUT` returns an `ETag` header, e.g. `"3"`.
- The next `PUT` sends that exact value as `If-Match`. A new project's first save
  sends `"0"`. Sending it without the quotes (`3`) also works.
- `If-Match` is required on every `PUT`. Without it the request gets `400`.
- Each successful save increases the version by 1.
- If someone saved in between, the `PUT` gets **409** and nothing is written. The body
  is the current document, so the editor can reload from it.

| Response | Means | Next step |
|---|---|---|
| `200` | saved | keep the new `ETag` for the next save |
| `400` | the document is invalid (keys, references, start scene, variables, rules, or a changed `output`) | fix the listed errors and send again |
| `403` / `404` | no access to this project, or it was deleted | stop |
| `409` | stale `If-Match`: someone else saved first | reload from the response body, then save again |

## More detail

Field-by-field details are in Swagger UI at `/swagger-ui.html`, under "Experience
content". Swagger is off by default; ask the backend team to enable it on your
environment (`SWAGGER_ENABLED=true`).
