# Stage 0 + ARM64 host POC: OpenCode V2.2.5

Status: host POC passed. Stage 0 push is blocked on a PAT with `workflow` scope.
No Android device is attached, so no Android runtime or UI claim is made here.

- Date: 2026-10-31
- Branch: `opencode-v2` at `ba3e030`
- V1 rollback tag: `v0.43.0-v1-final` (annotated, local only)
- Remote: `https://github.com/Rapas11/Closecode.git`
- Base build: 43, versionCode 43, version 0.43.0
- APK: `closecode-43-debug.apk`, 40,061,987 bytes,
  SHA-256 `5a15ba9bec1527a19113a71ddc25a9cc1c2e4c0e88c2fd143aa10b94c2c5fc25`
- Tests for build 43: 1412 run, 0 failures, 8 skipped
- Spec: `spacui.md` in the working tree (modified, not committed)

## Stage 0 state

Local work is complete:

- Created `opencode-v2` at `ba3e030`.
- Created annotated tag `v0.43.0-v1-final` with message
  `Last V1 build (43) before OpenCode V2 migration`.
- Updated `origin` to the canonical repository URL.
- `main` is unchanged locally (`ba3e030`); `origin/main` is still `bbdff7e`.

Push is blocked. The user-supplied PAT lacks the `workflow` scope required because
`ba3e030` adds `.github/workflows/build.yml`. Exact error:

```
refusing to allow a Personal Access Token to create or update workflow
'.github/workflows/build.yml' without 'workflow' scope
```

The tag was not pushed. `.github/workflows/build.yml` triggers on `v*` and runs a
release upload job, so a remote tag would start CI. Keep the tag local until the
push and CI behavior are explicitly confirmed.

Untracked files left untouched: `.opencode/`, `closecode-43-debug.apk`.
`spacui.md` is modified in the working tree and was not committed.

## Host environment

- aarch64, Ubuntu with glibc 2.27
- JDK 17 at `/opt/java/jdk-17.0.20.1+1`
- SDK 35 at `/opt/android_sdk`
- 375 GB free, about 3.2 GB RAM available
- No Node, CMake, NDK, `curl`, `readelf`, or `objdump`
- `LD_LIBRARY_PATH` and `LD_PRELOAD` must be unset for host tools and the POC;
  the app's support library path breaks host TLS.

## V2 artifacts verified

V2 is npm-distributed. GitHub "latest" is still V1 (`v1.19.5`); do not use it for V2.
Install channels: `https://opencode.ai/v2/install` and
`https://opencode.ai/update/api/latest/cli/npm`.

- Package: `@opencode/cli` 2.2.5, MIT
- Platform package: `@opencode/cli-linux-arm64-musl`
- Tarball: 115,471,239 bytes,
  SHA-256 `ef26ea9a81b8adf4185a47b7d3aa5bb3a2a69bc4297cbdaaff9641881339b594`
- Binary: `extract/package/bin/opencode`,
  SHA-256 `2d719ef7b593a3e836dab611bc28da0e9ee71958d5e72ddb41e0f6abe2924e14`
- Unpacked: 245,570,908 bytes
- ELF: AArch64 PIE, interpreter `/lib/ld-musl-aarch64.so.1`,
  NEEDED `libstdc++.so.6` and `libc.musl-aarch64.so.1`
- x64-musl comparison: 251,573,912 bytes unpacked

Alpine runtime pins downloaded and SHA-256 verified; these match
`OpenCodeRuntime.kt`:

- `musl-1.2.6-r3.apk`
- `libgcc-15.2.0-r5.apk`
- `libstdc++-15.2.0-r5.apk`
- `zlib-1.3.2-r1.apk`

Loader invocation used for the POC:

```sh
LD_LIBRARY_PATH=musl/lib:musl/usr/lib \
  musl/lib/ld-musl-aarch64.so.1 extract/package/bin/opencode \
  --print-logs serve --hostname 127.0.0.1 --port <port>
```

## Host POC results

POC root: `/data/user/0/com.m4coding.ide/cache/opencode/v2-poc/`
Artifacts: `doc.json` (OpenAPI), `server*.log`, `index.html`, UI JS assets,
`host_poc*.py`, `poc_results.json`. These are outside the repository and are not
committed.

### CLI and server

- `opencode v2.2.5 --version` and `--help` pass through the musl loader.
- `serve` starts the V2 API and web server.
- Server ready in 1.0-2.1 s; database bootstrap ran 59 migrations.
- Listens on `http://127.0.0.1:<port>` and reports that URL from `/api/info`.
- `ServerInfo` returns version, pid, hostname, urls, paths.tmp, and
  `capabilities.persistentPty=true`.

### Auth

- `/api/info` returns 401 without credentials:
  `{"_tag":"UnauthorizedError","message":"Authentication required"}`.
- Both `OPENCODE_PASSWORD` and `OPENCODE_SERVER_PASSWORD` work individually.
- With neither set, the server generates a random password and logs
  `server password <value>`. The app should set its own random password.
- API Basic auth: username `opencode`, password = configured secret.
- The shipped Web UI uses `Authorization: Basic btoa("opencode:"+password)`.
- The shipped Web UI bootstrap supports
  `/?auth_token=<base64url("opencode:"+password)>`; the token is URL-safe base64
  without padding. This is the clean WebView bootstrap path.
- No secret from the POC is recorded in this report.

### API contract

Base is `/api/*`. V1 paths are not valid. `/global/health`, `/doc`, `/config`,
`/session`, `/event` are SPA catch-all responses, not API routes.

Verified endpoints:

| Endpoint | Method | Result |
|---|---|---|
| `/openapi.json` | GET | OpenAPI 3.1.0 "opencode HttpApi", 138 paths, 350,880 bytes |
| `/api/info` | GET | 200, server info |
| `/api/location` | GET | 200; query `location[directory]=<canonical>` |
| `/api/config` | GET | 200, `Config.Entry[]` (document + directory) |
| `/api/project` | GET | 200, project list |
| `/api/agent` | GET | 200, 7 agents |
| `/api/model` | GET | 200, 8 models |
| `/api/provider` | GET | 200, 1 provider (`opencode`) |
| `/api/mcp` | GET | 200, 0 servers |
| `/api/command` | GET | 200, 3 commands |
| `/api/skill` | GET | 200, 3 skills |
| `/api/permission/request` | GET | 200, empty list |
| `/api/session` | POST | 200; body `{title, location:{directory}}` |
| `/api/session` | GET | 200; query `directory`, `limit`, ... |
| `/api/session/{id}/prompt` | POST | 200; async user inbox item |
| `/api/session/{id}/message` | GET | 200; user and assistant messages |
| `/api/session/{id}/inbox` | GET | 200 |
| `/api/session/{id}/context` | GET | 200 |
| `/api/session/{id}/diff` | GET | 200 |
| `/api/event` | GET | 200 `text/event-stream`; SSE events |
| `/api/pair` | POST | 200, `{code, expires_in}` (300 s) |

`location` query parameters use OpenAPI `deepObject`/`explode`, e.g.
`?location[directory]=/canonical/path`. Sending a JSON string returns
`Expected object | undefined at ["location"]`.

Permission reply contract:

- `POST /api/session/{sessionID}/permission/{requestID}/reply`
- Body `{decision, message?}`
- `decision` is `once`, `always`, or `reject`

SSE event framing: `data: {json}\n\n`, plus `: heartbeat` comments.
Observed event types include `server.connected`, `session.created`,
`session.inbox.enqueued`, `session.execution.started`, `session.step.started`,
`session.step.streamed`, `session.step.ended`, `session.text.delta`,
`session.text.ended`, `session.usage.updated`, `session.execution.succeeded`,
`session.inbox.delivered`, and `provider/model/agent/command/skill/plugin/reference`
update events. Permission events are in the event schema but were not triggered
in this POC.

### Model inference and snapshots

The built-in `opencode` provider exposes a public model:

- Provider: `opencode`, `apiKey: "public"`,
  `baseURL: "https://opencode.ai/inference/openai/v1"`
- Model: `big-pickle`
- Prompt "Reply with exactly: POC OK" completed asynchronously.
- Assistant message content: `POC OK`
- `finish: "stop"`, `cost: 0`, tokens input 18 / output 4, cache read 5860
- Snapshot start/end succeeded, no snapshot error.

This proves V2 model inference and session snapshots work on the ARM64 host.
It does not prove Android network reachability; verify on device.

### Canonical path requirement

V2 does not canonicalize `location.directory`. Passing
`/data/user/0/...` while the project canonical path was `/data/data/...` produced:

- `subpath: "../../../../../../user/0/..."`
- `Snapshot.Error: Location is outside the project`

Using the canonical path removed `subpath` and fixed snapshots. On Android:

- Resolve the workspace with `File.getCanonicalPath()`.
- Start the server with that canonical directory as cwd.
- Use the same canonical directory in every `location.directory` and
  `location[directory]` value.
- Do not mix `/data/user/0/<pkg>` and `/data/data/<pkg>` forms.

### Web UI

- `/` returns the official V2 SPA: 9571-byte HTML, title `OpenCode`,
  `/_assets/index-BDq5uAY5.js` plus modulepreload assets.
- The HTML contains no inline API or password; the bundle owns the API client.
- The bundle builds Basic auth as `btoa("opencode:"+password)`.
- The bundle decodes `auth_token` with URL-safe base64 and uses it to seed the
  local server connection.
- The UI is the official interface; do not replace it with a custom chat UI.
- Host POC fetched and inspected the HTML/JS but did not render it in a browser.
  WebView rendering and touch behavior remain a device-gate item.

### Config shape

`/api/config?location[directory]=<canonical>` returned `Config.Entry[]`:

```json
[
  {"type":"document","path":".../opencode.json","info":{...}},
  {"type":"directory","path":".../opencode"}
]
```

The POC config used:

```json
{
  "$schema": "https://opencode.ai/config.json",
  "update": "disable",
  "permissions": [
    {"action": "*", "resource": "*", "effect": "allow"},
    {"action": "external_directory", "resource": "*", "effect": "ask"},
    {"action": "read", "resource": "*.env", "effect": "ask"}
  ]
}
```

The server read and returned `permissions`, `external_directory`, and
`update: disable`. The final permission policy must follow `spacui.md`; the POC
rules are shape evidence, not the final policy. V2 config schema also exposes
`watcher.ignore` and `snapshots`.

### Watcher behavior and risk

The server subscribes to project ancestors and config roots. With the canonical
project under `/data/data/...`, logs show:

- `watcher subscribe` for `/data`, `/data/data`, `/data/user/0`, `/etc`, `/`
- `failed to watch directory` EACCES for `/data`, `/data/data`, `/data/user/0`
- Server continues; these errors are non-fatal

On Android, `/data` and `/data/data` are unreadable and `/` is large. The EACCES
errors are expected, but watching `/etc` and `/` may be a battery/performance
risk. `watcher.ignore` exists in the config schema; test it on device. This is
not yet mitigated.

## Decisions carried into implementation

- Use OpenCode 2.2.5 pinned; manual, tested updates only.
- ARM64 musl runtime, downloaded at first run, never bundled in the APK.
- Official V2 Web UI only; no custom chat, model, or permission UI.
- Use `/api/*`; remove V1 route assumptions.
- Set a random `OPENCODE_PASSWORD` (and optionally
  `OPENCODE_SERVER_PASSWORD`); bind loopback only.
- Bootstrap the WebView with `?auth_token=<base64url("opencode:"+password)>`.
- Use canonical paths everywhere.
- Write V2 `permissions` (not V1 `permission`); keep `update: disable`.
- Treat prompt as async inbox + SSE; handle `once`/`always`/`reject` replies.
- Do not claim Android compatibility until the device POC passes.

## Blockers

1. Push blocked: need a PAT with `workflow` scope (or an equivalent push path).
2. No Android device: Phase B device POC and all Android UI/runtime claims are
   blocked.
3. Watcher ancestor subscriptions: potential Android performance risk; needs
   device measurement and possibly `watcher.ignore`.
4. Official Web UI not yet rendered in a WebView; auth/bootstrap must be
   verified on device.

## Next actions

1. Obtain a new PAT with repository contents and workflow write access, then:
   ```sh
   git push -u origin main
   git push -u origin opencode-v2
   ```
   Decide separately whether to push `v0.43.0-v1-final` (it triggers CI).
2. Device POC (Phase B): install the arm64 runtime, start V2, render the official
   Web UI, verify auth token bootstrap, create a session, send a prompt, observe
   SSE, handle one permission request, and stop/restart cleanly.
3. Then proceed to implementation phases per `spacui.md`.
