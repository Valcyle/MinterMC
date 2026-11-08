# Connection Testing Guide

This guide explains how to connect MinterMC to a real server. It also records the results verified on 2026-11-07.

---

## 1. Start the MinterMC server

```powershell
cd C:\path\to\MinterMC
.\gradlew :mintermc-server:run
```

The server started successfully when you see `MinterMC gRPC Server started, listening on 50051`.

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
$env:MINTER_TARGET_PORT = "19228"
$env:MINTER_BOT_NAME    = "MinterProbe"
.\.venv\Scripts\python.exe examples\python\join_be.py
```

### Java Edition

```powershell
$env:MINTER_TARGET_HOST = "127.0.0.1"
$env:MINTER_TARGET_PORT = "25565"
$env:MINTER_BOT_NAME    = "MinterProbe"
.\.venv\Scripts\python.exe examples\python\join_je.py
```

If the Python dependencies are not installed yet:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install grpcio grpcio-tools
```

The gRPC stubs are already generated in `examples/python/`. To regenerate them:

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
Bedrock RakNet connection established to 127.0.0.1:19228, waiting for network settings
Sent Bedrock offline login packet for MinterProbe
Bedrock login accepted for MinterProbe
Bedrock bot MinterProbe joined the world at ...
Bedrock bot MinterProbe spawned
```

For Java, the client prints `Login SUCCESS!` and the current position.

### Python client

If `position` stays at `(0.00, 0.00, 0.00)`, the bot has not joined yet. World coordinates such as `(0.50, 65.00, 0.50)` mean the bot has joined successfully.

---

## 4. Verified results by server (2026-11-07)

| Server | Transport | Result |
|---|---|---|
| Nukkit-MOT 1.26.60 (RakNet, `127.0.0.1:19228`) | `raknet` | **Joined and spawned** |
| Official BDS 1.26.61.1 (NetherNet, `127.0.0.1:19132`) | `nethernet` | **Joined and spawned** |
| Official BDS 1.26.61.1 (RakNet compatibility) | `raknet` | Disconnected with `PacketViolationWarningPacket`; use NetherNet |
| Java Edition server | - | Not verified yet; use `join_je.py` to test |

### Testing with Nukkit-MOT

The Nukkit servers under `mcsmash-dev` listen on RakNet.

```powershell
$env:MINTER_BEDROCK_TRANSPORT = "raknet"
.\gradlew :mintermc-server:run
```

```powershell
$env:MINTER_TARGET_HOST = "127.0.0.1"
$env:MINTER_TARGET_PORT = "19228"   # skyblock
$env:MINTER_BOT_NAME    = "MinterProbe"
.\.venv\Scripts\python.exe examples\python\join_be.py
```

The survival `nukkit` server uses `19226`; `skyblock` uses `19228`. Use a server with `xbox-auth=off`.

### Testing with the official BDS

The official BDS requires `online-mode=false` and `transport=nethernet`. If you use a test BDS, restore its configuration after testing.

```properties
online-mode=false
allow-list=false
server-port=19132
transport=nethernet
```

After the server starts, MinterMC connects with the default `nethernet` transport.

```powershell
$env:MINTER_TARGET_HOST = "127.0.0.1"
$env:MINTER_TARGET_PORT = "19132"
$env:MINTER_BOT_NAME    = "MinterProbe"
.\.venv\Scripts\python.exe examples\python\join_be.py
```

On success, the MinterMC log shows:

```
Bedrock login accepted for MinterProbe
Bedrock bot MinterProbe joined the world at ...
Bedrock bot MinterProbe spawned
```

> [!NOTE]
> BDS rejects `DeviceOS=7` (Windows) with `readNoHeader`. MinterMC sends `DeviceOS=12` (Switch/NX), matching retail clients. The NetherNet identity and the login `Token` share the same multiplayer token.

---

## 5. Non-fatal decode errors

When connecting to Nukkit, Cloudburst may log exceptions while decoding `CraftingDataPacket`, `ItemComponentPacket`, or `CreativeContentPacket`. These occur because the item definition registry is not populated yet, but **the connection and spawn continue**. If the log contains `joined the world` and `spawned`, the test succeeded.

---

## 6. Cleanup

- Stop `gradlew :mintermc-server:run` with `Ctrl+C`.
- If you started a test BDS, stop the process and restore `server.properties`.
- Do not touch the user's lobby BDS or Waterdog instance.
