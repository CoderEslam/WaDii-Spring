# Agora Voice/Video Calling — Backend Implementation Spec

This document describes everything the backend must implement to support 1:1 voice/video
calling in WaDii via **Agora RTC**, matching the frontend contract that already exists in
this repo (Kotlin/JS, Compose for Web). The frontend is fully implemented and calling this
spec's endpoints/events already — only the backend side is missing.

Backend stack assumed to match the rest of this project's API (adjust naming if different):
**Kotlin + Ktor**, JWT bearer auth, existing `/web-socket/{userId}` endpoint used for chat.

---

## 1. Overview

Two users can start a 1:1 audio or video call. Flow:

1. Caller requests a channel join via signaling over an **existing WebSocket connection**
   (`CALL_INVITE`) — this is just a ring, no Agora token yet.
2. Callee's client shows an incoming-call overlay. On accept, both peers request an
   **Agora RTC token** from a new REST endpoint (`POST /agora/token`) and join the same
   Agora channel.
3. Call teardown (hang up / reject / busy) is also relayed via the WebSocket.

Agora itself (media transport, SFU) is entirely handled client-side by the Agora Web SDK
(`AgoraRTC.createClient(...)`, already integrated in `data/agora/AgoraCallClient.kt`). The
backend's only two responsibilities are:

- **Mint short-lived Agora RTC tokens** (App ID + App Certificate must never reach the
  client in raw certificate form — only a signed token + the public App ID).
- **Relay call signaling messages** (invite/accept/reject/end) between the two users' active
  WebSocket connections, the same way chat messages are already relayed.

No call state needs to be persisted (no "calls" table is required for MVP) — signaling is
purely a real-time relay. If call history/logging is wanted later, see §5 (optional).

---

## 2. REST endpoint: `POST /agora/token`

### Route
```
POST /agora/token
Authorization: Bearer <JWT>
Content-Type: application/json
```

### Request body
```json
{
  "channelName": "3_7",
  "uid": 3
}
```

Kotlin shape already used by the frontend (`domain/model/call/AgoraTokenModels.kt`):
```kotlin
data class AgoraTokenRequest(
    val channelName: String = "",
    val uid: Int = 0
)
```

- `channelName`: deterministic per pair of users, built client-side as
  `listOf(userId, contactId).sorted().joinToString("_")` (see `CallChannel.kt`) — e.g. two
  users with ids 3 and 7 always get channel `"3_7"` regardless of who calls whom.
- `uid`: the Agora numeric UID the caller wants to join as. The frontend always sends the
  **authenticated user's own id** (`AppState.user.id`) as `uid`.

### Response body

Wrapped in the project's standard envelope:
```kotlin
data class BaseResponse<T>(
    val data: T,
    val message: String = "",
    val statusCode: Int = 0,
    val timestamp: String = ""
)

data class AgoraTokenResponse(
    val token: String = "",
    val appId: String = ""
)
```

Example:
```json
{
  "data": {
    "token": "007eJxTYFj...",
    "appId": "a1b2c3d4e5f6..."
  },
  "message": "OK",
  "statusCode": 200,
  "timestamp": "2026-07-03T12:00:00Z"
}
```

### Server-side logic

1. Resolve the authenticated user from the JWT (same middleware as every other endpoint).
2. **Validate `uid` belongs to the caller.** Reject (403) if `request.uid != authenticatedUserId`.
   Without this check, any authenticated user could mint a token impersonating another
   user's Agora UID.
3. *(Recommended, optional)* Validate the authenticated user is actually one of the two ids
   encoded in `channelName` (split on `_`, parse both as ints, confirm membership) so users
   can't join arbitrary/other people's channels. Not currently enforced by the frontend, but
   cheap and meaningfully closes an authorization gap.
4. Generate an Agora RTC token using Agora's server SDK (`RtcTokenBuilder2` /
   `AccessToken2`, available for Java/Kotlin, Node, Go, Python, PHP...) with:
   - App ID + App Certificate from server-side config/env (`AGORA_APP_ID`,
     `AGORA_APP_CERTIFICATE`) — **never expose the certificate to the client**.
   - `channelName` = request value.
   - `uid` = request value.
   - Role = `PUBLISHER` (`RtcRole.PUBLISHER`) — both call participants publish audio/video.
   - Expiration: short-lived, e.g. `privilegeExpiredTs = now + 3600` seconds (1 hour is
     plenty for a call; can be tuned).
5. Return `AgoraTokenResponse(token = generatedToken, appId = AGORA_APP_ID)`.

### Config / secrets needed
- `AGORA_APP_ID` — public, safe to return to clients.
- `AGORA_APP_CERTIFICATE` — secret, server-only, used only to sign tokens. Store in env/secret
  manager, never log it, never send it in any response.

### Java/Kotlin token generation reference (Agora `agora-token` package)
```kotlin
import io.agora.media.RtcTokenBuilder2
import io.agora.media.RtcTokenBuilder2.Role

val builder = RtcTokenBuilder2()
val token = builder.buildTokenWithUid(
    appId,
    appCertificate,
    channelName,
    uid,
    Role.ROLE_PUBLISHER,
    tokenExpireSeconds,   // e.g. 3600
    tokenExpireSeconds    // privilege expiry, can match token expiry
)
```
(Exact API depends on the chosen Agora token SDK version — check whichever `agora-token`
library the backend adds to its dependencies; the shape above is representative of the
current v2 builder.)

---

## 3. WebSocket signaling

### Reuse the existing endpoint

The frontend does **not** open a new socket for calls — it reuses the exact same endpoint
already used for chat:
```
ws://<host>/web-socket/{userId}?token={jwt}
```

Important existing detail: the frontend currently opens **two separate WebSocket
connections** to this same URL per logged-in user — one owned by `ChatWebSocketService`
(chat) and one owned by `CallSignalingService` (calls). **The backend must support multiple
concurrent WS connections for the same `userId`** and deliver relayed events to all of that
user's open sockets (or at minimum to whichever socket(s) are alive) — do not assume a
single-socket-per-user model.

### Message envelope

All frames are JSON text frames. Every message — inbound or outbound — is shaped as:
```json
{
  "event": "CALL_INVITE",
  "data": { ... }
}
```
Matching:
```kotlin
data class SocketResponse(val event: String, val data: JsonElement)
```

`event` is one of (`domain/model/chat/SocketEvent.kt`):
```kotlin
enum class SocketEvent { MESSAGE, PRESENCE, CALL_INVITE, CALL_ACCEPT, CALL_REJECT, CALL_END }
```
This spec only covers the four `CALL_*` events (`MESSAGE`/`PRESENCE` are pre-existing chat
events, already implemented).

### `data` payload for all four call events — `CallSignal`

```kotlin
data class CallSignal(
    val channelName: String = "",     // e.g. "3_7"
    val callType: String = "VIDEO",   // "VIDEO" or "AUDIO"
    val fromUserId: Int = 0,
    val toUserId: Int = 0,
    val fromUserName: String = "",
    val fromUserImage: String? = null
)
```

Client → server, the frame is:
```json
{
  "event": "CALL_INVITE",
  "data": {
    "channelName": "3_7",
    "callType": "VIDEO",
    "fromUserId": 3,
    "toUserId": 7,
    "fromUserName": "Amina",
    "fromUserImage": "amina.jpg"
  }
}
```

### Required backend relay behavior

For each of the 4 events, on receiving it from user A's socket, the backend must:

1. **Trust `fromUserId` from the authenticated connection, not the payload** — i.e. overwrite/
   validate `data.fromUserId` against the `userId` the socket authenticated as (from the JWT
   used to open `/web-socket/{userId}?token=`), so a client cannot spoof another user's
   identity in signaling. (Today's frontend already sends its own real id, but the backend
   should not trust client input for identity.)
2. **Forward the exact same frame** (`{event, data}`) to every open socket belonging to
   `data.toUserId`. If the target user has no open socket (offline), the invite is simply
   dropped — no push notification / offline queueing is required for this feature (that could
   be a future enhancement, not required now).
3. No transformation of `channelName`/`callType`/names/image needed — pass through as-is.
4. No persistence needed for MVP (see §5 for optional history).

### Event-by-event semantics (for backend context — logic is pure relay, but useful to know)

| Event | Sent by | Meaning | Backend action |
|---|---|---|---|
| `CALL_INVITE` | caller | "I'm calling you on channel X" | Relay to `toUserId`'s socket(s) |
| `CALL_ACCEPT` | callee | "I accepted, joining channel X" | Relay to `toUserId` (= original caller) |
| `CALL_REJECT` | callee (or caller cancelling) | "Declined / cancelled" | Relay to `toUserId` |
| `CALL_END` | either party | "Hanging up" | Relay to `toUserId` |

Notes on frontend behavior that constrain correctness (don't need backend changes, just
context so relay timing makes sense):
- On `CALL_ACCEPT`, the **caller's** client calls `POST /agora/token` and joins Agora. The
  callee already joined right after sending `CALL_ACCEPT` (see `CallViewModel.joinAndPublish`
  triggered immediately for the non-caller). So both `/agora/token` calls happen close
  together right after `CALL_ACCEPT` — expect near-simultaneous token requests for the same
  `channelName` with two different `uid`s. This is expected and fine (each uid gets its own
  token for the same channel).
- On `CALL_REJECT`/`CALL_END`, the receiving client matches purely on `channelName` — the
  backend doesn't need to track call state to make this work, just relay honestly.
- There is no `CALL_BUSY`/timeout event today — if the callee doesn't answer, the caller UI
  presumably stays in `CALLING` state indefinitely (frontend concern, not backend).

### Suggested Ktor-side implementation sketch

```kotlin
// Pseudocode — adapt to existing WS route already handling MESSAGE/PRESENCE
webSocket("/web-socket/{userId}") {
    val userId = call.parameters["userId"]!!.toInt()
    val token = call.request.queryParameters["token"]
    val authedUserId = verifyJwtAndGetUserId(token) // must equal userId in path, else close

    val connectionId = registerConnection(authedUserId, this) // supports N sockets/user
    try {
        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            val msg = Json.decodeFromString<SocketResponseEnvelope>(frame.readText())
            when (msg.event) {
                "CALL_INVITE", "CALL_ACCEPT", "CALL_REJECT", "CALL_END" -> {
                    val signal = msg.data.jsonObject.toCallSignal()
                        .copy(fromUserId = authedUserId) // never trust client-provided sender id
                    sendToUserSockets(signal.toUserId, event = msg.event, data = signal)
                }
                "MESSAGE" -> { /* existing chat handling */ }
                else -> {}
            }
        }
    } finally {
        unregisterConnection(authedUserId, connectionId)
    }
}
```

`registerConnection`/`sendToUserSockets`/`unregisterConnection` should back onto whatever
connection registry already exists for chat (likely a `Map<Int, MutableSet<WebSocketSession>>`
or similar) — reuse it rather than building a parallel one, since the same endpoint already
must do this for multi-device chat delivery.

---

## 4. Security checklist

- [ ] `/agora/token` requires a valid JWT (same auth middleware as other REST routes).
- [ ] `/agora/token` rejects requests where `uid != authenticated user id`.
- [ ] `/agora/token` (recommended) validates the authenticated user is a member of
      `channelName` (parse `"a_b"`, check membership).
- [ ] Agora App Certificate lives only in server config/secrets — never logged, never
      returned in any response.
- [ ] Tokens are short-lived (≤ 1 hour recommended).
- [ ] WebSocket relay overwrites `fromUserId` with the authenticated socket's user id rather
      than trusting the payload, preventing signaling spoofing.
- [ ] WS auth (`?token=`) is validated the same way as the existing chat connection — no new
      auth mechanism needed, calls ride on the same authenticated socket.

---

## 5. Optional / future (not required for MVP, do not build unless asked)

- Persisting call history (caller, callee, channel, start/end time, duration, missed/rejected
  status) in a `calls` table — useful for a "recent calls" UI later.
- Push notifications for `CALL_INVITE` when the callee has no open socket (currently silently
  dropped).
- A `CALL_BUSY` event if the callee is already in another call.
- Rate limiting `/agora/token` per user to prevent abuse.

None of the above is expected by the current frontend — implementing only §2–4 is sufficient
to make calling work end-to-end.
