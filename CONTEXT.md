# Context

Domain language and protocol contract for the Chatwoot SDK. Terms here are canonical — use
them in code, docs, and discussion.

## Glossary

- **Website inbox** — the Chatwoot inbox type this SDK talks to. Created in the Chatwoot
  dashboard; identified by its **website token**.
- **Website token** — public token identifying a website inbox (`website_token` query param
  on every API call). Not a secret.
- **Conversation token** (`cw_conversation`) — a JWT identifying one contact session
  (claims: `source_id`, `inbox_id`). Returned by the widget bootstrap as `window.authToken`,
  sent as the `X-Auth-Token` header on REST calls, and passed back as `?cw_conversation=` on
  re-bootstrap to resume the same contact + conversation. Persisted by `TokenStore`
  (SharedPreferences / NSUserDefaults), keyed per website token.
- **Pubsub token** — the contact's ActionCable subscription credential
  (`window.chatwootPubsubToken`). A *contact* pubsub token only receives events for that
  contact's own session; *user* (agent) tokens see account-wide events — the SDK only ever
  holds contact tokens.
- **RoomChannel** — the single ActionCable channel Chatwoot broadcasts on. Contact
  subscriptions identify as `{"channel":"RoomChannel","pubsub_token":"…"}` (no
  account_id/user_id — those are for agent connections).
- **ChatPage** — the SDK's entire public UI surface:
  `ChatPage(show, onFinish, styleConfig = DefaultStyle)`.
- **StyleConfig / DefaultStyle** — the one theming object; all visual customisation flows
  through it. No other styling knobs exist.
- **Contact** — the end user chatting. Chatwoot auto-creates an anonymous contact (e.g.
  "weathered-shape-813") on first bootstrap.
- **Attachment** — a file carried by a Message. Each has a `file_type` (`image`, `audio`,
  `video`, `file`, plus rarer kinds — anything other than the first three renders as a generic
  file), a `data_url` (original) and optional `thumb_url` (preview). Sent one-per-message and
  caption-less (the upload carries no `content`). _Avoid_: media, upload.

## Protocol contract (verified against app.chatwoot.com, June 2026)

> The upstream wiki (`Steps-to-build-the-integration`) is stale: `GET /widgets.json` no
> longer exists, and message POSTs authenticate with `X-Auth-Token`, not bare query params.

### Bootstrap

`GET <base>/widget?website_token=T[&cw_conversation=JWT]` returns **HTML**; the session
tokens are embedded as script globals and parsed by `WidgetPageParser`:

```
window.authToken = '<conversation JWT>'
window.chatwootPubsubToken = '<pubsub token>'
```

Omitting `cw_conversation` creates a fresh contact; passing it resumes the session.

### REST (all under `/api/v1/widget`, all with `?website_token=T` + `X-Auth-Token` header)

| Call | Notes |
|---|---|
| `GET /messages` | `{"payload":[Message…]}`. `message_type`: 0 contact, 1 agent, 2 activity, 3 template. `created_at` is unix seconds. `private: true` messages are agent notes — never render. |
| `POST /messages` | Body `{"message":{"content","timestamp","referer_url"}}` — only for an **existing** conversation. Returns the created Message. |
| `POST /messages` (multipart) | **Attachment** upload (`multipart/form-data`): `message[attachments][]` (the file, with filename) + `message[referer_url]` + `message[timestamp]`. No `content`, no `echo_id` — one file, caption-less. Returns the created Message (parse for the real `id` + attachment URLs). |
| `POST /conversations` | First message of a session: `{"message":{…}}` (optional `contact:{name,email,phone_number}`). Creates conversation + message. Caveat: in *this* response `message_type` is a string (`"template"`); the SDK refetches `GET /messages` instead of parsing it. |
| `GET /conversations` | `{}` when no conversation exists yet. |
| `GET /inbox_members` | `{"payload":[{id,name,avatar_url,availability_status}]}` — no auth header needed. |

A Message carries an `attachments` array; each entry: `{id, file_type, data_url, thumb_url,
file_size, width, height, extension}`. Received attachments need no special handling — they
arrive on `GET /messages` and on the `message.created`/`message.updated` websocket events like
any other field.

> **Unverified edge:** sending the session's *first* message as an attachment uses multipart
> `POST /conversations` (same `message[attachments][]` fields). The JSON create path is verified;
> the multipart create is not yet probed against app.chatwoot.com — verify during e2e. The
> create response's `message_type` is a string (as with the text path), so the SDK refetches
> `GET /messages` instead of parsing it.

### Realtime (`wss://<base>/cable`, ActionCable wire protocol)

1. On connect the server sends `{"type":"welcome"}`, then `{"type":"ping"}` every ~3s.
2. Subscribe: `{"command":"subscribe","identifier":"{\"channel\":\"RoomChannel\",\"pubsub_token\":\"…\"}"}`
   — note the identifier is a JSON-*string*, not an object. Server confirms with
   `{"type":"confirm_subscription"}`.
3. Keepalive: every 30s send
   `{"command":"message","identifier":<same>,"data":"{\"action\":\"update_presence\"}"}`.
4. Broadcasts arrive as `{"identifier":…,"message":{"event":"…","data":{…}}}`. Events a
   contact receives: `message.created`, `message.updated`, `conversation.typing_on/off`
   (`data.user` may be the contact itself — filter `type=="contact"`; honour `is_private`),
   `conversation.status_changed`, `presence.update`.
5. On drop: reconnect with exponential backoff and refetch `GET /messages` to catch up
   (`CableClient` + `ChatRepository.onCableEvent`).
