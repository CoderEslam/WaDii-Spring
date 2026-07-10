# LiveKit Backend Integration Guide

Audience: backend team. Goal: implement one REST endpoint (`POST /livekit/token`) that mints
LiveKit access tokens for 1:1 calls. Everything else (call ringing/accept/reject/end) already
rides over the existing chat websocket and needs **no changes** — it's included here only for
context so the token endpoint's validation rules make sense.

---

## 1. Concepts (LiveKit vs. what you already know from Agora)

If you've already built the Agora token endpoint (`agora-mobile.md`), LiveKit is the same shape
of problem with different vocabulary:

| Agora | LiveKit | Notes |
|---|---|---|
| App ID | — | LiveKit has no public app id; only API Key/Secret (server-side only) |
| App Certificate | API Secret | Used to *sign* tokens, never shipped to clients |
| — | API Key | Public-ish identifier paired with the secret, embedded in the token's `iss` claim |
| Channel | Room | Same concept: an isolated call session both participants join |
| UID (Int) | Identity (String) | LiveKit participants are identified by an arbitrary string, not a numeric uid |
| Token (per uid+channel) | Access Token (JWT) | Same purpose: short-lived, scoped credential handed to the client |
| Agora Cloud region endpoint | LiveKit server URL | LiveKit needs an explicit `wss://…` URL — see §2 |

A LiveKit **access token is a JWT** signed with your API Secret (HS256). It encodes *who* the
participant is and *what room* they may join with *what permissions* (a "video grant"). The
LiveKit server (or LiveKit Cloud) validates that JWT when the client calls `Room.connect(url,
token)` — your backend never talks to LiveKit directly for a normal call setup; it only signs a
token and hands it to the mobile client, which connects directly to the LiveKit server/cloud.

---

## 2. Prerequisites

You need three secrets/config values, provided by whoever owns the LiveKit deployment (LiveKit
Cloud project, or a self-hosted LiveKit server):

```
LIVEKIT_API_KEY=API...
LIVEKIT_API_SECRET=...............................
LIVEKIT_URL=wss://<your-project>.livekit.cloud      # or wss://your-self-hosted-host:7880
```

- `LIVEKIT_API_KEY` / `LIVEKIT_API_SECRET` — never sent to the mobile client. Used only to sign
  tokens server-side.
- `LIVEKIT_URL` — this **is** sent to the client (as `url` in the response below). It's the
  address the LiveKit client SDK connects its websocket/media transport to. Use `wss://` in
  production.

If nobody has stood up a LiveKit deployment yet: the fastest path is a free LiveKit Cloud
project (https://cloud.livekit.io) — it gives you the URL + key/secret immediately with no
infra to run. Self-hosting is also fine (single Go binary + Redis), but that's a separate
ops conversation, not required for this endpoint's contract.

---

## 3. Endpoint to implement: `POST /livekit/token`

Full URL as called by the mobile client: `{BASE_URL}/livekit/token` (same base URL/versioning
as every other endpoint in this API, currently `http://{IP}:8080/livekit/token`).

### Auth

Same as every other authenticated endpoint in this API — the client sends its normal session
`Authorization` header. **Do not skip this.** The request body's `identity` field is
client-supplied and must not be trusted blindly — see §6 (Security).

### Request body

```json
{
  "roomName": "3_7",
  "identity": "3",
  "participantName": "Oumnia Chaara"
}
```

| Field | Type | Meaning |
|---|---|---|
| `roomName` | string | The LiveKit room to join. Always `sorted([userIdA, userIdB]).join("_")` — see §5. |
| `identity` | string | The calling user's own id, stringified. Must equal the authenticated user's id (§6). |
| `participantName` | string | Display name to attach to the participant (shown to the other side, e.g. via `participant.name`). |

Kotlin shape on the client (for reference, do not implement — this is what serializes to the
JSON above):

```kotlin
@Serializable
data class LiveKitTokenRequest(
    val roomName: String = "",
    val identity: String = "",
    val participantName: String = ""
)
```

### Response body

Wrap the payload in this API's standard envelope (same one every other endpoint uses):

```json
{
  "data": {
    "url": "wss://your-project.livekit.cloud",
    "token": "eyJhbGciOiJIUzI1NiIs..."
  },
  "message": "OK",
  "statusCode": 200,
  "timestamp": "2026-07-08T10:15:30Z"
}
```

| `data` field | Type | Meaning |
|---|---|---|
| `url` | string | Your `LIVEKIT_URL` (§2), passed straight through unchanged. |
| `token` | string | The signed JWT access token (§4). |

Client-side shape this deserializes into:

```kotlin
@Serializable
data class LiveKitTokenResponse(
    val url: String = "",
    val token: String = ""
)
```

The client passes `url` and `token` directly into the LiveKit client SDK's connect call — it
does not construct or interpret either value. **Errors must be a non-2xx HTTP status** (the
client's HTTP layer maps that to a generic error state); don't return `200` with an error
message nested inside `data`.

---

## 4. Generating the token (the actual JWT)

Use LiveKit's official server SDK for your backend language — don't hand-roll the JWT unless
you have a strong reason to. All server SDKs share the same conceptual API: construct an
`AccessToken` with the identity/name, attach a **video grant**, serialize to a JWT.

### Video grant fields you need for a 1:1 call

```
roomJoin: true
room: <roomName from the request>
canPublish: true
canSubscribe: true
canPublishData: true        // optional: enables data-channel messages if you ever want them
```

Do **not** grant `roomCreate`, `roomAdmin`, `roomList`, or `recorder` — this token is scoped to
one participant joining one specific room, nothing more. LiveKit auto-creates the room on first
join when `roomJoin` is granted, so no separate "create room" call is required for a basic 1:1
call (see §7 if you want custom room lifecycle settings).

### Token claims

| Claim | Value |
|---|---|
| `iss` (issuer) | `LIVEKIT_API_KEY` — set automatically by the SDK |
| `sub` (subject) / identity | request's `identity` field |
| `name` | request's `participantName` field |
| `video` | the grant object above |
| `exp` | short TTL — recommend **10 minutes**. The token only needs to survive the initial `Room.connect()`; once connected, the session isn't re-validated against the JWT's expiry mid-call. Don't reuse/cache tokens across calls. |

### Node.js (`livekit-server-sdk`)

```js
import { AccessToken } from 'livekit-server-sdk';

async function mintLiveKitToken({ roomName, identity, participantName }) {
  const at = new AccessToken(
    process.env.LIVEKIT_API_KEY,
    process.env.LIVEKIT_API_SECRET,
    { identity, name: participantName, ttl: '10m' }
  );
  at.addGrant({
    roomJoin: true,
    room: roomName,
    canPublish: true,
    canSubscribe: true,
    canPublishData: true,
  });
  return {
    url: process.env.LIVEKIT_URL,
    token: await at.toJwt(),
  };
}
```

### Python (`livekit-api`)

```python
from livekit import api
import os

def mint_livekit_token(room_name: str, identity: str, participant_name: str) -> dict:
    token = (
        api.AccessToken(os.environ["LIVEKIT_API_KEY"], os.environ["LIVEKIT_API_SECRET"])
        .with_identity(identity)
        .with_name(participant_name)
        .with_ttl(timedelta(minutes=10))
        .with_grants(api.VideoGrants(
            room_join=True,
            room=room_name,
            can_publish=True,
            can_subscribe=True,
            can_publish_data=True,
        ))
    )
    return {"url": os.environ["LIVEKIT_URL"], "token": token.to_jwt()}
```

### Java / Kotlin (`livekit-server-sdk-kotlin`, if the backend is JVM-based)

```kotlin
val token = AccessToken(apiKey, apiSecret).apply {
    identity = request.identity
    name = request.participantName
    addGrants(RoomJoin(true), RoomName(request.roomName), CanPublish(true), CanSubscribe(true))
    ttl = Duration.ofMinutes(10)
}
val jwt = token.toJwt()
```

(Exact class names vary slightly by SDK version — check whichever server SDK you actually add
to the project; the shape above is representative of all of them.)

---

## 5. Room naming — must match the client exactly

The client always derives the room name from both participants' numeric user ids, sorted
ascending and joined with `_`:

```kotlin
object CallChannel {
    fun name(userId: Int, contactId: Int): String =
        listOf(userId, contactId).sorted().joinToString("_")
}
```

So a call between user `7` and user `3` always uses room name `"3_7"` — regardless of who
called whom. This same value is used both as `LiveKitTokenRequest.roomName` (the REST request in
this doc) and as `CallSignal.channelName` (the existing ring/accept/reject/end websocket
signal). **Validate that the request's `roomName` matches this exact format** and that the
authenticated user's id is one of the two ids embedded in it (§6) — don't accept an arbitrary
string as the room name.

---

## 6. Security checklist

The client-supplied `identity` and `roomName` fields are just what's convenient for the client
to send — they are **not** trusted input. Before minting a token:

1. **Verify `identity` matches the authenticated user.** Derive the caller's id from their auth
   session/JWT, not from the request body. If `request.identity != String(session.userId)`,
   reject with `403`.
2. **Verify the requester is a participant in `roomName`.** Parse `roomName` as
   `"{idA}_{idB}"`, confirm both are valid user ids and that the authenticated user's id is one
   of them. Otherwise a user could request a token for someone else's room and eavesdrop.
3. **Keep the grant minimal** — `roomJoin` + `canPublish` + `canSubscribe` only, as in §4. No
   admin/create/list grants.
4. **Short TTL** (§4) — 10 minutes is plenty; the client fetches a fresh token every time it
   joins (see §8, it calls this endpoint right after `CALL_ACCEPT`, not once per app session).
5. Never log or return `LIVEKIT_API_SECRET`. Only `LIVEKIT_API_KEY`/`LIVEKIT_URL` are
   non-secret; the secret must stay server-side only.

---

## 7. Optional: explicit room configuration

If you want auto-cleanup behavior beyond LiveKit's defaults (e.g. cap at 2 participants, or tear
down an abandoned room faster), you can explicitly create the room via the server SDK's
`RoomService` **before** minting the token, instead of relying on implicit creation from the
`roomJoin` grant:

```js
import { RoomServiceClient } from 'livekit-server-sdk';

const roomService = new RoomServiceClient(LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET);

await roomService.createRoom({
  name: roomName,
  emptyTimeout: 60,        // seconds: destroy room if it stays empty this long
  departureTimeout: 20,    // seconds: destroy room this long after the last participant leaves
  maxParticipants: 2,      // hard cap — enforces "1:1 only" at the LiveKit level too
});
```

This is optional for a first pass — implicit room creation via the grant works fine for MVP —
but worth adding once the endpoint is otherwise working, since `maxParticipants: 2` gives you a
server-enforced guarantee that matches the product's 1:1 calling model.

---

## 8. How this fits into the existing call flow (context, not new work)

No changes needed here — this section exists so the validation rules in §6 make sense in
context. The existing websocket signaling (`ws://{IP}:8080/web-socket/{userId}?token={jwt}`)
already carries the ring/accept/reject/end handshake as JSON frames shaped
`{"event": "<SocketEvent>", "data": <CallSignal>}`:

```kotlin
enum class SocketEvent { MESSAGE, PRESENCE, CALL_INVITE, CALL_ACCEPT, CALL_REJECT, CALL_END }

@Serializable
data class CallSignal(
    val channelName: String = "",
    val callType: String = "VIDEO",
    val fromUserId: Int = 0,
    val toUserId: Int = 0,
    val fromUserName: String = "",
    val fromUserImage: String? = null,
    val provider: String = "AGORA"   // "AGORA" or "LIVEKIT" — same signal shape for both
)
```

Sequence for a LiveKit call specifically:

1. Caller sends `CALL_INVITE` (with `provider: "LIVEKIT"`) over the existing socket — unchanged.
2. Callee sends `CALL_ACCEPT` back over the same socket — unchanged.
3. **Only now** does either side call `POST /livekit/token` (this doc) to get a room URL+token,
   then connects to LiveKit directly with the LiveKit client SDK.
4. `CALL_END`/`CALL_REJECT` over the socket tear down app-level call state — unchanged. LiveKit's
   own room teardown (§7) is independent of this and just cleans up the underlying media room.

So: the backend's websocket relay logic doesn't need to know or care whether `provider` is
`"AGORA"` or `"LIVEKIT"` — it should already be forwarding `CallSignal` opaquely regardless of
that field's value. The only *new* backend work is the REST endpoint in §3.

---

## 9. Optional: webhooks for reliability

LiveKit can POST webhook events (`room_started`, `room_finished`, `participant_joined`,
`participant_left`, `track_published`, etc.) to a URL you configure in your LiveKit
project/deployment settings. Not required for MVP — the app already manages call lifecycle via
`CALL_END`/`CALL_REJECT` on the app's own socket — but useful defense-in-depth: if a client
crashes or loses connectivity without sending `CALL_END`, a `participant_left`/`room_finished`
webhook lets the backend clean up any server-side call state independently of the client. Worth
revisiting once the basic token endpoint is live and working.

---

## 10. Quick manual test

Once the endpoint is deployed, sanity-check it directly (bypassing the mobile app) before
wiring up a real call:

```bash
curl -X POST http://{IP}:8080/livekit/token \
  -H "Authorization: Bearer <a real session token for user 3>" \
  -H "Content-Type: application/json" \
  -d '{"roomName": "3_7", "identity": "3", "participantName": "Test User"}'
```

Expect a `200` with `data.url` = your `LIVEKIT_URL` and `data.token` = a JWT. You can decode the
JWT at https://jwt.io to eyeball the `video` grant and confirm `room` matches `"3_7"` and
`sub`/identity matches `"3"`. Then confirm a request with `identity: "99"` (not the
authenticated user) is rejected per §6, rule 1.
