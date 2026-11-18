# How the Box, its display and the tool talk to each other

This describes every connection between the parts of MuseotekBox and what goes over it. Everything here is built
(2026-11-18). Error codes are listed in `docs/error-codes.md`.

The design comes from the proposal "Box, Server, Display communication protocol" (2026-11-16). It replaced an earlier
design in which the tool itself ran DISPLAY experiences and screens paired with a setup code;
`docs/proposal-player-app.md` and `docs/proposal-device-api.md` still describe that earlier design and are kept as
the record of why.

## The parts

- **The tool** (this backend, one app at our hosting). It has two sides:
  - the **CMS side**, where curators build and publish experiences and managers set up Boxes and displays. People
    log in with Keycloak;
  - the **player package** (`com.museotek.box.player`), which keeps the websocket connections of Boxes and displays
    and passes messages between them.
- **The Box**: the device in the museum with the RFID reader. It downloads every experience assigned to it and runs
  all of them itself, including their rules and timers. A visitor places a card on it, and the Box decides what
  happens next.
- **The display**: a TV or monitor next to one Box. It is a page of the CMS frontend (`/display/{orgId}/{boxId}`),
  shown full screen. It only draws what the Box tells it, and reports back when a scene is on screen or a video has
  ended.

```
  Box  ── REST: download experiences and media ──▶  tool  ◀── REST: idle screen, media ──  display
  Box  ◀════════════ websocket /ws/box ═══════════▶  tool  ◀═══════ websocket /ws/display ═══▶  display
```

## Ground rules

- **The Box decides, the display draws.** The display never picks the next scene. So a slow or missing display can't
  put the two out of step.
- **The tool relays and remembers.** It does not run experiences. It passes the Box's latest picture (a `state`
  snapshot) to the display and keeps a copy, so a display that reloads or connects late gets it at once. It passes
  the display's reports to the Box.
- **Every device is tied to one Box.** A display's token and a Box's key each belong to exactly one Box. The tool
  takes the Box (and its organisation) from the token, never from a message or a URL. So a device can't reach another
  Box or another museum.
- **One connection per Box and side.** At most one Box connection and one display connection per Box. A newer
  connection replaces the older one ("the newest wins"); the older one is told (`session.replaced`) and closed.

## How each part proves who it is

| Who | With what | Made where | Sent how |
|---|---|---|---|
| Curators, managers (CMS) | Keycloak login | Keycloak | `Authorization: Bearer <JWT>` on `/api/v1/**` |
| Display | display token | CMS, by a manager: "Open display" | `Authorization: Bearer <token>` on `/display/**`; `hello` on `/ws/display` |
| Box | Box key | CMS, by a manager: "Make key" | `Authorization: Bearer <key>` on `/box/**`; `hello` on `/ws/box` |

Display tokens and Box keys are long random strings (43 characters). The tool shows each one once, when it is made,
and stores only its SHA-256 fingerprint. A display token stops working after 30 days unused (each use moves that
forward), or when a manager revokes it. A Box has one key; making a new one stops the old one at once, and a manager
can also remove it (for example for a stolen Box). Deleting a Box ends its display tokens and its key. The Box's
serial number is only a label, never a login.

A token or key is never put in a URL, because URLs end up in logs. On a websocket it travels in the first message.

## Step by step

### 1. Setting up a Box (once)

1. A manager creates the Box in the CMS and assigns experiences to it.
2. On the Box's page the manager presses "Make key": `POST /api/v1/organisations/{orgId}/boxes/{boxId}/key`. The
   answer holds the key, this one time.
3. The key is installed on the Box. (How is up to the Box team; see "Still open".)

### 2. Opening a display (once per TV)

1. On the Box's page a manager presses "Open display". A new tab opens `/display/{orgId}/{boxId}`.
2. That page calls `POST /api/v1/organisations/{orgId}/boxes/{boxId}/display-sessions` with the manager's login. The
   answer holds a display token, this one time.
3. The page keeps the token in the browser's storage and from then on uses only the token. The manager's own login
   can expire; the display keeps working. Managers see the open displays on the Box's page and can revoke any of them.

### 3. The Box connects (every start, every reconnect)

1. The Box downloads what it plays: `GET /box/sync` lists itself, its experiences with their versions, the card
   types and which RFID tag is which card. For each experience whose version changed it calls
   `GET /box/experiences/{projectId}`, and for each media file it doesn't have yet `GET /box/media/{mediaId}/content`.
2. It opens `/ws/box` and sends `hello` with its key within 5 seconds. The tool answers `hello.ok`, marks the Box
   online, and tells it whether its display is there (`display.presence`). The display, if connected, hears
   `box.presence` with `online: true`.
3. The Box sends its current `state`, as it does after every connect.

### 4. The display connects (every page load, every reconnect)

1. The page calls `GET /display/idle` with its token: the organisation's name, the Box's name and status, and the
   experiences a visitor can start there, each with its start card.
2. It opens `/ws/display` and sends `hello` with its token within 5 seconds. The tool answers `hello.ok`, then
   `box.presence` (is the Box online), then the Box's latest `state` if it has one. The Box hears
   `display.presence` with `connected: true`.

### 5. A visit

1. A visitor places a start card on the Box. The Box starts the experience and sends a `state` with mode `PLAYING`
   (it uses the display) or `PLAYING_ON_BOX` (it doesn't).
2. The tool keeps the snapshot, writes down what the Box plays (`boxes.current_project_id`, `current_scene_key`)
   when that changed, and passes the snapshot to the display. To it, the tool adds `mediaLinks`: a signed link for
   every `mediaId` in the snapshot (see "Media for the display").
3. The display draws the scene and answers `scene.shown`. When a video ends it sends `video.ended`; when a file won't
   load, `media.failed`. The tool passes these to the Box.
4. The Box runs its rules (a card, a timer, a video ending) and sends a new `state` whenever the picture changes. A
   reply is a new snapshot of the same scene with an `overlay` added; the display restarts media only when
   `scene.instance` changes, so the video underneath keeps playing.
5. When the experience ends, the Box sends a `state` with mode `IDLE` and the display goes back to its idle screen.

### 6. A curator changes what a Box plays

When an experience on a Box is published again, assigned, unassigned, deleted or restored, the tool tells that Box
`sync.required` and its display `idle.changed`, after the change is saved. The Box downloads again (step 3.1); the
display fetches `GET /display/idle` again.

### 7. When something drops

- **The display reloads or reconnects:** it gets the stored snapshot and draws it; a video starts again from the
  beginning.
- **The Box drops:** the tool marks it offline and tells the display (`box.presence`, `online: false`). The display
  keeps the current picture with a "reconnecting" mark. When the Box comes back it sends its current `state`.
- **The tool restarts:** every connection drops, every Box is marked offline, and the stored snapshots and media-link
  keys are gone. Both sides reconnect, and the Box's first `state` restores the picture with fresh media links.
- **A connection goes quiet:** the tool pings every connection every 25 seconds (a websocket ping, which browsers and
  websocket libraries answer by themselves) and closes one that hasn't answered for 60 seconds, with code 4500.
- **A manager revokes the display, replaces or removes the Box key, or deletes the Box:** that connection gets
  `session.revoked` and is closed with code 1008.

A device reconnects after any close except one with code 1008: that means "don't retry", and the close reason says
why (`docs/error-codes.md`).

## The messages

Every websocket message, in both directions, has the same envelope:

```json
{ "v": 1, "type": "state", "seq": 42, "sentAt": "2026-11-18T10:15:30Z", "payload": { } }
```

`seq` counts up per sender and per connection. The tool reads only `type` and `payload` from devices; its own
messages carry all five fields.

### Logging in (both sockets)

| Direction | type | payload |
|---|---|---|
| display → tool | `hello` | `{ "token": "…" }` |
| Box → tool | `hello` | `{ "key": "…" }` |
| tool → display | `hello.ok` | `{ "sessionId", "boxId", "orgId" }` |
| tool → Box | `hello.ok` | `{ "boxId", "orgId" }` |

### From the Box

`state` is the only message a Box sends after `hello`. It is a full snapshot of what the display should show now:

```json
{
  "mode": "PLAYING",
  "session": { "id": "c0ffee…", "projectId": "…", "publishedVersion": 7, "name": "Art out of the box" },
  "scene": {
    "instance": 12,
    "sceneKey": "4",
    "blocks": [ { "blockKey": "9", "type": "VIDEO", "content": { "mediaId": "6f1c2a…" } } ]
  },
  "overlay": { "type": "REPLY", "color": "green", "text": "Correct!", "mediaId": null }
}
```

- `mode` is `IDLE`, `PLAYING` (an experience that uses the display) or `PLAYING_ON_BOX` (one that doesn't). It is the
  only field the tool checks.
- `scene.blocks` are the published blocks exactly as they are, so the display needs no copy of the experience.
- `scene.instance` goes up every time a scene is entered, also when the same scene is entered again.
- The tool reads `session.projectId` and `scene.sceneKey` to write down what the Box plays. It stores them only for
  an experience that really is on that Box.

### From the display

| type | payload | means |
|---|---|---|
| `scene.shown` | `{ "sessionId", "instance" }` | the scene is on screen and its media can play |
| `video.ended` | `{ "sessionId", "instance", "blockKey" }` | fires the Box's `VIDEO_ENDED` rule |
| `media.failed` | `{ "sessionId", "instance", "blockKey" }` | a file won't load; the Box can move on |

`sessionId` is text and `instance` a whole number; the tool checks that and passes the message to the Box. The Box
ignores any whose `sessionId` or `instance` is not current, so a late `video.ended` from a scene the visitor already
left does nothing. If the Box is not connected, the message is dropped.

### From the tool itself

| to | type | payload | when |
|---|---|---|---|
| display | `state` | the Box's snapshot plus `mediaLinks` | the Box sent one; and right after the display connects |
| display | `box.presence` | `{ "online" }` | after `hello`, and whenever the Box connects or drops |
| display | `idle.changed` | `{}` | what the Box plays changed: fetch `/display/idle` again |
| Box | `display.presence` | `{ "connected" }` | after `hello`, and whenever the display connects or leaves |
| Box | `sync.required` | `{}` | what the Box plays changed: download again |
| both | `session.replaced` | `{}` | a newer connection took over; then closed (1008) |
| both | `session.revoked` | `{}` | the token or key stopped working; then closed (1008) |
| both | `error` | `{ "code", "message", "details", "requestId" }` | see `docs/error-codes.md` |

## Media for the display

A `<video>` or `<img>` can't send the display token as a header, so the display loads media through signed links:
`/display/media/{mediaId}/content?session=…&expires=…&signature=…`, relative to the API root. The `signature` is an
HMAC over the media, the display session and the expiry. The tool puts one link per `mediaId` into each snapshot it
sends the display, under `mediaLinks` (`{ "<mediaId>": "<link>" }`).

A link works for 12 hours, only while its display is not revoked, and only for media used by an experience on that
display's Box. The signing key is random at every start of the tool, so a restart ends all links; displays get new
ones with the next snapshot.

The Box downloads media with its key instead: `GET /box/media/{mediaId}/content`.

## REST endpoints

For managers in the CMS (Keycloak login, manager of the Box's organisation; listing displays is open to members):

| Method | Path | What |
|---|---|---|
| `POST` | `/api/v1/organisations/{orgId}/boxes/{boxId}/display-sessions` | open a display; returns its token once |
| `GET` | `/api/v1/organisations/{orgId}/boxes/{boxId}/display-sessions` | the Box's open displays |
| `DELETE` | `/api/v1/organisations/{orgId}/boxes/{boxId}/display-sessions/{id}` | revoke a display |
| `POST` | `/api/v1/organisations/{orgId}/boxes/{boxId}/key` | make (or replace) the Box key; returns it once |
| `DELETE` | `/api/v1/organisations/{orgId}/boxes/{boxId}/key` | remove the Box key |

For the display (display token):

| Method | Path | What |
|---|---|---|
| `GET` | `/display/idle` | organisation name, Box name and status, experiences with their start cards |
| `GET` | `/display/media/{mediaId}/content?session&expires&signature` | a media file, by signed link (no token) |

For the Box (Box key):

| Method | Path | What |
|---|---|---|
| `GET` | `/box/sync` | the Box, its experiences (latest version each), card types, which tag is which card |
| `GET` | `/box/experiences/{projectId}` | one experience's published content and its media list (with SHA-256) |
| `GET` | `/box/media/{mediaId}/content` | a media file used on this Box |

All paths are under the context path `/museotekbox`.

## Limits

- `hello` within 5 seconds of connecting, or the connection is closed.
- More than 10 logins of one display or Box within a minute blocks it for a minute (a reconnect loop). Never counted
  per IP address, because a museum's devices usually share one. Wrong tokens aren't counted: guessing 256 random bits
  is hopeless.
- A websocket message may be up to 65536 characters.
- The display socket accepts only the CMS's Origins (`museotek.player.socket.allowed-origins`); the Box socket any,
  because the Box is a device, and its key is not a cookie a foreign page could ride on.

All numbers are settings (`museotek.player.socket.*`, `museotek.display.*` in `application.properties`).

## The player package

`com.museotek.box.player.socket` holds the two websockets: `DeviceSocketHandler` (login, pings) with
`DisplaySocketHandler` and `BoxSocketHandler` on top, and `Connections`, which knows who is connected for each Box,
relays between them and keeps the latest snapshots. It uses only queries and use cases from `application.player`,
never repositories or entities; `PlayerBoundaryTest` (ArchUnit) enforces that, and that nothing outside the package
depends on it. The REST endpoints for devices are in `web.display` and `web.boxapi`; their security is in
`PlayerSecurityConfig` (one `DeviceTokenFilter` for both kinds of device).

Connections, snapshots and media-link keys live in memory, so only one copy of the app may run at a time.

## Still open

- **How a key gets onto a Box.** The CMS shows it once; installing it is up to the Box team.
- **A display gone during a video.** `video.ended` then never arrives, and the Box would wait. The Box could time out
  on its own, or treat `display.presence` with `connected: false` as the video having ended.
- **Card edits.** Changing cards or card types doesn't send `sync.required` yet; a connected Box picks them up on its
  next sync (for example after reconnecting).
- **Start card colour.** `/display/idle` gives each experience's start card by card type name; card types have no
  colour of their own.
