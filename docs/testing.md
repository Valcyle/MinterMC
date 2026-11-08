# Connection Testing Guide

This guide describes how to connect MinterMC to a Minecraft server.

---

## 1. Start the MinterMC server

```powershell
cd C:\path\to\MinterMC
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

The official BDS requires `online-mode=false` and `transport=nethernet` for offline bots. Use a dedicated test server and restore its configuration after testing.

```properties
online-mode=false
allow-list=false
server-port=19132
transport=nethernet
```

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

## 5. Non-fatal decode errors

Cloudburst may log exceptions while decoding `CraftingDataPacket`, `ItemComponentPacket`, or `CreativeContentPacket`. These occur because the item definition registry is not populated yet, but the connection and spawn continue. If the log contains `joined the world` and `spawned`, the test succeeded.

---

## 6. Cleanup

- Stop `gradlew :mintermc-server:run` with `Ctrl+C`.
- If you started a test server, stop the process and restore its configuration.
- Use a dedicated test server rather than a production instance.
