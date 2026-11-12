# How the Box, the screen and the tool talk to each other

This describes every connection between the parts of MuseotekBox, and what goes over it. Parts marked **(built)**
exist in code today; everything else is the agreed design and may still change in detail (message names, fields).

Decided 2026-11-11: the player is **not a separate app**. It is a package inside this backend (`com.museotek.box.player`),
kept apart from the authoring code by a test (see "The player package" below). This replaces the earlier design with
a separate `museotek-player` app and a `/player/v1` REST API between the two; `docs/proposal-player-app.md` and
`docs/proposal-device-api.md` still describe that earlier design and are kept as the record of why.

## The parts

- **Tool** (museotek-tool-backend, this project): one app at our hosting, with two sides:
  - **authoring**: where curators build experiences and publish them, and where managers register Boxes and add
    screens. Curators log in with Keycloak. It owns all data that must last;
  - **the player package**: connects a museum's Box with a screen and runs the visits. It reads what it needs
    through authoring's queries and use cases, as plain Java calls.
- **Box**: the device in the museum with the RFID reader. A visitor places a card on it; it reports the card. It has
  no keyboard.
- **Screen**: a museum PC with the tool's screen web page open full screen in its browser. It shows the scenes and
  plays videos and sounds, doing what the player package tells it. A manager registers each screen in the tool
  ("Add screen"). The page is served from our hosting but runs in the museum's browser, so it talks to the tool
  over the internet, through a websocket, and must prove which screen it is.

```
             our hosting                                      the museum
┌──────────────────────────────────────────────┐   ┌────────────────────────────────┐
│ museotek-tool-backend (one app)              │   │                                │
│                                              │   │                                │
│ ┌────────────┐  Java   ┌────────────────┐    │   │                                │
│ │ authoring  │◄─calls──│ player package │◄───┼───┼── websocket ── screen          │
│ │            │         │   (runtime)    │◄───┼───┼── websocket ── Box             │
│ └────────────┘         └────────────────┘    │   │                                │
│       ▲                                      │   │                                │
└───────┼──────────────────────────────────────┘   └────────────────────────────────┘
        │
   curators (CMS, Keycloak login)
```

Words used below:

- **Experience**: what a curator builds (scenes, cards, rules). **Published copy**: a frozen version of it, made when
  the curator publishes. The player only ever plays published copies.
- **Output**: a setting on each experience. **DISPLAY** = needs a Box and a screen, runs in the player package.
  **BOX** = needs only the Box, runs on the Box itself, also without internet.
- **Visit**: one visitor going through one experience, from scanning its start card to the end or the Exit card.
  A **DISPLAY experience visit** runs in the player package and is shown on the screen; a **BOX experience visit**
  runs on the Box itself.
- **Websocket**: a connection that stays open, so both sides can send messages at any time.

## Ground rules

1. **Everything in the museum only connects outward,** to the tool: the Box and the screens. Nothing connects into
   the museum, so museums need no firewall changes.
2. **The Box and the screens never talk to each other.** Everything goes through the player package.
3. **The screen picks the Box; a Box never picks a screen.** A Box only reports scans. Until a screen has picked it,
   its scans start nothing on any screen (only BOX experiences, on the Box itself).
4. **Every connection proves who it is:** curators with Keycloak, screens with their key, Boxes with their serial
   number. Each has its own security setup; none can reach the others' endpoints.
5. **The player package never reads tables directly.** It calls authoring's queries and use cases, which do the
   access and org checks. A test enforces this (below).

## How each part proves who it is

| Who | How it logs in | Built? |
|---|---|---|
| Curator / manager | Keycloak login in the CMS (JWT), roles from Catalogue. | built |
| Screen | A **screen key**: a long random secret the screen got once in exchange for a setup code (below) and keeps in its browser. Sent as the first websocket message, because browsers can't set websocket headers. | key handling built, socket planned |
| Box | Its **serial number**, sent as the first websocket message. The serial is treated as a secret: never printed on the device or logged. A factory secret per Box may come later. | planned |

The tool stores screen keys and setup codes only as fingerprints (SHA-256), so a database leak doesn't reveal them.

## Step by step

### 1. Adding a screen (once per museum PC)

1. A museum manager clicks "Add screen" in the tool. The tool shows a one-time **setup code**, e.g. `K7QF-M2XR`,
   valid for 3 days. **(built)**
2. Someone opens the tool's screen address in a browser on the museum PC. The tool sends the screen web page, which
   asks for the code.
3. Screen → tool: `SETUP {setupCode}`.
4. The player package redeems the code (`RedeemScreenSetupCodeUseCase`): it uses up the code, creates the screen and
   makes its key. **(built)**
5. Tool → screen: the key, the only time it is ever sent. The screen stores it in its browser and uses it from now
   on.

A wrong, used, cancelled or expired code is refused (`SETUP_CODE_NOT_FOUND`) and the screen shows "code not valid".
A code works only once, even on a double click. The key never expires; the screen only needs a new code if its
browser data is cleared, it moves to another browser or PC, or a manager removes it. (So museum PCs must not run the
browser in a mode that wipes site data on close.)

### 2. A screen connects (every start, every reconnect)

1. Screen → tool: `AUTH {screenKey}`.
2. The player package checks the key (`VerifyScreenKeyUseCase`) and learns the screen and its museum (org); the
   screen's "last seen" is updated. **(built)** A removed or unknown key (`SCREEN_KEY_INVALID`) → the connection is
   closed.
3. When a manager removes a screen, its connection is closed straight away (same app, no waiting for a re-check).
4. The tool also sets a cookie for its own address. The screen loads videos and sounds from that same address, so
   the browser sends the cookie with them automatically and only a logged-in screen can load media.
5. If the screen remembers a Box from before, it asks for it again (step 4). Otherwise it shows the Box picker.

### 3. A Box connects

The Box only sends `HELLO`, and later `SCAN`. It calls no HTTP endpoint.

1. Box → tool: `HELLO {serialNumber}`.
2. The player package finds the Box by its serial (`IdentifyBoxQuery`). An unknown serial → the connection is
   closed. **(built)**
3. It records the Box as online (`ReportBoxStatusUseCase`), shown to curators in the tool. **(built)**
4. Box and tool send ping/pong every 30 seconds, so a dead connection is noticed.

### 4. A screen picks a Box

1. The player package lists the screen's museum's Boxes (`ListOrgBoxesForPlayerQuery`), with the org from the
   screen's key. A screen only ever sees its own museum's Boxes. **(built)**
2. Screen → tool: `PICK_BOX {boxId}`. The player package gives the screen the Box if no other screen holds it, after
   checking the Box belongs to the screen's museum. If another screen holds it, the screen asks "Box in use, take
   over?" and sends `PICK_BOX {boxId, takeOver: true}`.
3. The lock is kept in memory: it lasts while the screen is connected and is freed when the connection closes. One
   Box per screen, one screen per Box.
4. Tool → screen: `SHOW_IDLE {experiences}`, the idle view with the Box's DISPLAY experiences and their start cards
   (`GetBoxForPlayerQuery`). If the Box isn't connected: `BOX_OFFLINE`.

### 5. A DISPLAY experience visit

1. The visitor places a start card. Box → tool: `SCAN {tag}`. The same tag read again within 1–2 seconds counts
   once (readers repeat reads).
2. The player package turns the tag into a card type, finds the experience with that start card and loads its
   latest published copy (`GetExperienceForPlayerQuery`). The visit keeps that copy in memory until it ends. The
   Box's status now shows the running experience and scene.
3. Tool → screen: `SHOW_SCENE {sceneKey, blocks, mediaUrls}`. The screen loads the media from the tool, with its
   cookie (step 2.4).
4. Each further scan is checked against the current scene's rules. The player package sends the screen what the
   rules say: `SHOW_REPLY {color, text, mediaUrl}`, `PLAY_AUDIO {mediaUrl}` / `STOP_AUDIO`, the next `SHOW_SCENE`.
   Timers run in the player package. When a video ends, screen → tool: `VIDEO_ENDED {sceneKey}`. Each scene change
   updates the Box's status.
5. The visit ends at an END rule or when the Exit card is scanned. Tool → screen: `SHOW_IDLE`. The Box's status
   shows no running experience.

The visit lives in the player package; the screen only shows it. During a visit, other experiences' start cards are
ignored: the visitor must exit first. If the screen's page reloads, it sends `READY` and gets the current scene
again; the visit carries on.

### 6. A curator publishes a new version

1. The curator publishes in the tool.
2. A running visit finishes on the copy it loaded. The next visit loads the latest copy, so the new version is used
   from the next visit on. No polling and no copying are needed.
3. Idle screens get a new `SHOW_IDLE` when the Box's list of experiences changes (publish, assign, unassign).

### 7. A BOX experience visit (runs on the Box)

1. While online, the Box gets its BOX experiences (published copies and sounds) from the tool, through its own
   connection.
2. The Box runs these visits itself, also offline. The screen, if there is one, stays idle.
3. When it is online, the Box tells the tool what it is doing, and the Box's status is updated.

The messages for this are not designed yet: they wait for the Box team to say what the Box can run and what outputs
(sound, lights) it has. Visit reports (statistics of finished visits) are postponed until someone asks for
statistics; the Box should keep a local record of finished visits until then.

### 8. When something drops

| What happens | Result |
|---|---|
| Screen loses its connection | It reconnects by itself (with increasing waits and a random delay), sends `AUTH`, asks for its Box again. If another screen took the Box meanwhile, it goes back to the picker. |
| Box loses its connection | Its status turns offline; the screen shows `BOX_OFFLINE`. A visit pauses and resumes if the Box is back within about a minute, otherwise it ends. |
| Screen PC switched off | Its connection closes, so its Box is free for another screen. |
| Tool restarts (deploy, crash) | Running visits end; visitors scan the start card again. Screens and Boxes reconnect after a random delay of 0–10 s and screens get their Box back. Deploys happen outside museum hours. |
| Tool fails for longer | A standby copy takes over (only one copy is active at a time, because visits and locks live in memory). Until then, DISPLAY visits can't run; BOX experiences on the Box carry on. |
| Manager removes a screen | Disconnected at once. |

## Limits against abuse

Never per IP address, because all PCs in a museum usually share one.

- **Setup codes:** 5 wrong codes on one connection → the connection is closed. At most 100 wrong codes per hour in
  total; above that, setup codes are refused for 10 minutes for everyone.
- **Screen keys:** 3 wrong keys on one connection → closed. More than 10 reconnects per minute from one screen →
  blocked for a minute.

## The player package

- **Where:** `com.museotek.box.player` and below: the websocket handlers (its front door, like controllers are for
  HTTP), the runtime (rules, timers, visits), Box locks and the rate limits.
- **What it may use:** authoring's queries and use cases (`application/...`) and the views they return, plus domain
  exceptions. **Never repositories or entities.** Those queries and use cases do the org and access checks and
  return only the fields meant to go out (a `Box` entity, for example, holds the serial number).
- **Who may use it:** nothing outside `com.museotek.box.player`. Authoring never depends on the player.
- **How it is enforced:** an ArchUnit test (`PlayerBoundaryTest`) fails the build when a class breaks either rule.
- **Why a package and not a separate app:** one deploy, no secret and no REST API between the two, no copies to keep
  in sync. The boundary keeps a later split possible: the queries and use cases it calls would become an API again.

## Reference

### Box ↔ tool (websocket, planned)

| Direction | Message | Meaning |
|---|---|---|
| Box → tool | `HELLO {serialNumber}` | First message. Unknown serial → closed. |
| Box → tool | `SCAN {tag}` | A card was placed. |
| both | ping / pong every 30 s | Detects a dead connection. |
| — | BOX experience download and status | To design with the Box team (step 7). |

### Screen ↔ tool (websocket, planned)

| Direction | Message | Meaning |
|---|---|---|
| screen → tool | `SETUP {setupCode}` | First message on a new screen. Answered with its key. |
| screen → tool | `AUTH {screenKey}` | First message from then on. |
| screen → tool | `PICK_BOX {boxId, takeOver}` | Pick a Box (or take it over). |
| screen → tool | `READY` | (Re)connected, wants the current view. |
| screen → tool | `VIDEO_ENDED {sceneKey}` | For the "video ended" trigger. |
| tool → screen | `SHOW_IDLE {experiences}` | Idle view with start cards. |
| tool → screen | `SHOW_SCENE {sceneKey, blocks, mediaUrls}` | Show this scene. |
| tool → screen | `SHOW_REPLY {color, text, mediaUrl}` | Reply overlay after a scan. |
| tool → screen | `PLAY_AUDIO {mediaUrl}` / `STOP_AUDIO` | Sounds. |
| tool → screen | `BOX_OFFLINE` | The picked Box isn't connected. |
| tool → screen | `error {code, message, requestId}` | Something was refused; codes in `docs/error-codes.md`. |

### What the player package calls (built, in `application/player` and `application/screen`)

| Call | What for |
|---|---|
| `RedeemScreenSetupCodeUseCase` | Trade a setup code for a new screen and its key. |
| `VerifyScreenKeyUseCase` | Check a screen key: screen, org; updates "last seen". |
| `IdentifyBoxQuery` | Which Box a serial belongs to. |
| `ListOrgBoxesForPlayerQuery` | A museum's Boxes, for the picker. |
| `GetBoxForPlayerQuery` | A Box's experiences (with versions), cards, card types. |
| `GetExperienceForPlayerQuery` | The latest published copy plus its media list. |
| `GetMediaForPlayerQuery` | A media file for the screen. |
| `ReportBoxStatusUseCase` | What the Box is doing now. A late report never overwrites a newer one. |

The `/player/v1` REST endpoints in front of these (built for the separate app) are removed once the websockets use
them directly.

## Still open

- **Pairing a screen with its Box in the tool** instead of picking on site: possible now that it's one app; not
  decided.
- **Media cookie details** (step 2.4): HttpOnly, Secure, SameSite=Strict, a few hours, renewed while connected.
- **The Box:** what it can run itself, its outputs, a factory secret, and the BOX-experience messages.
- **Runtime rules** (order of rule checks, effects, timers): Part 3 of `proposal-player-app.md`, waiting for the
  frontend developer's yes.
- **Message names and fields** above are a first draft; they are fixed when each socket is built.
