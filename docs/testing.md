# Connection Testing Guide

This guide describes how to connect MinterMC to a Minecraft server.

---

## 1. Start the MinterMC server

```powershell
cd path\to\MinterMC
.\gradlew :mintermc-server:run
```

The server is ready when the log contains `MinterMC gRPC Server started, listening on 50051`.

### Overriding the version and transport

The adapter can be switched with environment variables; no rebuild is required.

| Variable | Default | Purpose |
|---|---|---|
| `MINTER_JAVA_VERSION` | `1.21.4` | Java Edition protocol version |
| `MINTER_BEDROCK_VERSION` | `1.26.60` | Bedrock protocol version (2225) |
| `MINTER_BEDROCK_TRANSPORT` | `nethernet` | `nethernet` / `nethernet-lan` / `raknet` |

Example:

```powershell
$env:MINTER_BEDROCK_TRANSPORT = "raknet"
.\gradlew :mintermc-server:run
```

---

## 2. Run the sample client

Use a second terminal.

### Bedrock Edition

```powershell
$env:MINTER_TARGET_HOST = "127.0.0.1"
$env:MINTER_TARGET_PORT = "19132"
$env:MINTER_BOT_NAME    = "TestBot"
.\.venv\Scripts\python.exe examples\python\join_be.py
```

### Java Edition

```powershell
$env:MINTER_TARGET_HOST = "127.0.0.1"
$env:MINTER_TARGET_PORT = "25565"
$env:MINTER_BOT_NAME    = "TestBot"
.\.venv\Scripts\python.exe examples\python\join_je.py
```

If the Python dependencies are not installed yet:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install grpcio grpcio-tools
```

The gRPC stubs are generated in `examples/python/`. To regenerate them:

```powershell
cd examples\python
..\..\.venv\Scripts\python.exe -m grpc_tools.protoc `
  -I..\..\mintermc-protocol\src\main\proto `
  --python_out=. --grpc_python_out=. `
  ..\..\mintermc-protocol\src\main\proto\bot_service.proto
```

---

## 3. What success looks like

### MinterMC server log

For Bedrock, the log normally shows the following sequence:

```
Bedrock NetherNet connection established to 127.0.0.1:19132, waiting for network settings
Sent Bedrock offline login packet for TestBot
Bedrock login accepted for TestBot
Bedrock bot TestBot joined the world at ...
Bedrock bot TestBot spawned
```

For Java, the client prints `Login SUCCESS!` and the current position.

### Python client

If `position` stays at `(0.00, 0.00, 0.00)`, the bot has not joined yet. World coordinates such as `(0.50, 65.00, 0.50)` mean the bot has joined successfully.

---

## 4. Choosing a transport

| Server type | Transport |
|---|---|
| Official Bedrock Dedicated Server 1.26+ | `nethernet` |
| RakNet servers such as Nukkit or PocketMine | `raknet` |
| NetherNet server on the local network | `nethernet-lan` |
| Java Edition server | Java adapter (no transport option) |

### Official Bedrock Dedicated Server

The official BDS requires `online-mode=false` and `transport=nethernet` for offline bots. Download the server from Mojang and follow its license; do not redistribute the server binary or its world data. Use a dedicated test server and restore its configuration after testing.

Run these tests only on servers you own or are authorized to use. The Minecraft
EULA permits independent tools, but individual server rules may prohibit bots
and automation.

```properties
online-mode=false
allow-list=false
server-port=19132
transport=nethernet
# BDS 1.26.x: a single shared UDP port drops one of several simultaneous
# NetherNet connections. Use a range large enough for the number of bots.
server-udp-ports=19132-19232
```

The port range must cover the maximum number of bots that connect at the same
time, including bots that are still negotiating. With the default single port
(`server-udp-ports=19132`), the first bot spawns but one connection loses ICE
connectivity as soon as another bot spawns.

Start MinterMC with the default `nethernet` transport:

```powershell
$env:MINTER_TARGET_HOST = "127.0.0.1"
$env:MINTER_TARGET_PORT = "19132"
$env:MINTER_BOT_NAME    = "TestBot"
.\.venv\Scripts\python.exe examples\python\join_be.py
```

### RakNet servers

Use `raknet` for servers that expose a RakNet UDP port.

```powershell
$env:MINTER_BEDROCK_TRANSPORT = "raknet"
.\gradlew :mintermc-server:run
```

```powershell
$env:MINTER_TARGET_HOST = "127.0.0.1"
$env:MINTER_TARGET_PORT = "19132"
$env:MINTER_BOT_NAME    = "TestBot"
.\.venv\Scripts\python.exe examples\python\join_be.py
```

For Nukkit, use a server with `xbox-auth=off`.

> [!NOTE]
> The official BDS rejects the RakNet compatibility path with `PacketViolationWarningPacket`. Use `nethernet` for BDS.

---

## 5. Position and spawn values

`StartGame` carries the level spawn point, and a level whose spawn was never
finalised can store a placeholder Y value. The bot reports the value it
received and keeps sending `PlayerAuthInput`; the server sends a `MovePlayer`
correction once it has an authoritative position. A Y value outside the world
height is therefore a level data issue, not a decode failure.

---

## 6. Chat

Chat can be sent and received over the `EventStream` RPC.

```powershell
$env:MINTER_CHAT = "hello from mintermc"
.\.venv\Scripts\python.exe examples\python\chat_be.py
```

The server log contains a `[Bedrock chat]` line for every message the bot
receives, and the sample prints each `chat_received` event. A chat message the
bot sends is echoed back by the server, which confirms that it was accepted.

Each bot advertises its own XUID through its self-signed login token, and chat
packets carry the same value. On BDS 1.26.x this makes the server relay chat
between bots. A message with an empty XUID is accepted by the server but is not
forwarded to other players.

---

## 7. Authentication

Bedrock and Java bots support two authentication modes.

| Mode | `auth_mode` | Server requirement |
|---|---|---|
| Offline identity | `AUTH_OFFLINE` (default) | `online-mode=false` |
| Microsoft account | `AUTH_MICROSOFT` | Online mode, with your own account |

For Microsoft mode, set `auth_mode` to `AUTH_MICROSOFT` and pass a stable
`account` name in `LoginRequest`. The first sign-in prints a device-code URL and
code in the kernel log. Open the URL in a browser, enter the code, and wait for
the login call to finish. Later logins reuse the cached refresh token.

The cache is written to `MINTER_AUTH_DIR` (default `~/.mintermc/accounts`). It
contains refresh tokens: do not commit it, log it, or share it. The Microsoft
mode signs in with the account owner's own credentials through the official
device code flow and does not bypass authentication. Use it only with accounts
you own and on servers where automation is permitted.

Use a distinct account for every bot that connects at the same time. Servers
reject or replace a second session that uses the same account.

```powershell
# The cache directory can be moved outside the home directory.
$env:MINTER_AUTH_DIR = "D:\mintermc-accounts"
```

Example with the Python client:

```python
response = stub.Login(bot_service_pb2.LoginRequest(
    bot_name="TestBot",
    target_edition=1,
    host="127.0.0.1",
    port=19132,
    auth_mode=bot_service_pb2.AUTH_MICROSOFT,
    account="main",
))
```

The Python stubs in `examples/python` may need regeneration to expose the new
fields; see section 2. An executable example is
[`examples/python/join_be_auth.py`](../examples/python/join_be_auth.py).

---

## 8. Blocks

`GetBlock` returns the block at the given coordinates.

```powershell
$env:MINTER_BLOCK_X  = "7"
$env:MINTER_BLOCK_Z  = "23"
$env:MINTER_BLOCK_YS = "-64,0,32,64,70,100"
.\.venv\Scripts\python.exe examples\python\blocks_be.py
```

Bedrock sends block data per 16x16x16 section on request, so the first query
of a section costs one round trip to the server. A section that the server does
not have (outside the loaded area) returns `found=false`. Fully empty sections
are reported as `minecraft:air`.

---

## 9. Movement

`MoveTo` walks the bot to a position. Bedrock uses server-authoritative
movement: the bot reports local input and its predicted position every tick,
BDS simulates the movement and sends `CorrectPlayerMovePrediction` when the
prediction drifts. Without the same spawn sequence the retail client uses
(`InteractUpdate` and `EndLoadingScreen` before `SetLocalPlayerAsInitialized`),
BDS keeps the player in the loading state and ignores movement.

```powershell
$env:MINTER_MOVE_X = "11"
$env:MINTER_MOVE_Z = "23"
.\.venv\Scripts\python.exe examples\python\move_be.py
```

Without `MINTER_MOVE_X`, `MINTER_MOVE_Y` and `MINTER_MOVE_Z`, the sample moves
four blocks along X from the current position. `MoveTo` returns `reached=false`
when the request times out or a new request replaces it.

The current implementation moves in a straight line and follows the ground
height reported by the server. It does not avoid blocks or jump yet; combine it
with block queries in a plugin for navigation.

---

## 10. Cleanup

- Stop `gradlew :mintermc-server:run` with `Ctrl+C`.
- If you started a test server, stop the process and restore its configuration.
- Use a dedicated test server rather than a production instance.
