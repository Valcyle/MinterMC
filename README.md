# MinterMC

English | [日本語](README.ja.md)

[![Discord](https://img.shields.io/discord/872775092218761226?logo=discord&color=7389D8)](https://discord.gg/ckQGXN46pu)
[![Created at](https://img.shields.io/github/created-at/Valcyle/MinterMC)](https://github.com/Valcyle/MinterMC)
[![Last commit](https://img.shields.io/github/last-commit/Valcyle/MinterMC
)](https://github.com/Valcyle/MinterMC/commits/main/)
===========
Use any programming language to create Minecraft bots.

> [!NOTE]
> This project is currently in early development. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before contributing.

A universal Minecraft bot kernel that abstracts the differences between **Java Edition** and **Bedrock Edition**.  
Exposes a single **gRPC API** so bots can be controlled from any programming language via an SDK.

> [!IMPORTANT]
> **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**  
> MinterMC is an independent project and contains no Minecraft game code or assets.

# DEMO
[<img src="https://img.youtube.com/vi/8_sQTFVja0g/0.jpg" alt="1500 bots joining using MinterMC" width="200">](https://www.youtube.com/watch?v=8_sQTFVja0g)

Joining Java Edition and Bedrock Edition servers is supported, with offline
identities or a Microsoft account. Bedrock support is under active development.
Python, C++, JavaScript and Java examples are available.
Check out [`examples`](examples/) folder for more examples.

# Features

- **Protocol Abstraction** — Control Java Edition and Bedrock Edition bots through a single unified API
- **Chat and Events** — Send chat and receive server events over the bidirectional `EventStream` RPC
- **Authentication Modes** — Offline identities or your own Microsoft account (device code)
- **Block Access** — Read block identifiers and passability from the loaded world
- **Movement** — Walk bots to coordinates with server-authoritative movement
- **Language Agnostic** — Any language with a gRPC client can control bots (Python, TypeScript, Go, etc.)
- **Agent-per-Bot Model** — Each bot runs on its own Java 25 Virtual Thread for full isolation
- **Plugin System** — Drop a JAR into `plugins/` to extend bot behavior (pathfinding, combat, etc.)
- **Scalable Design** — Designed to handle hundreds of simultaneous bots

# Requirement

- Java 21+ (runtime)
- JDK 25 to build from source (the produced classes target Java 21)

For multiple Bedrock bots, configure the server with a UDP port range such as
`server-udp-ports=19132-19232` instead of a single shared port. On BDS 1.26.x,
the single-port mode drops one of several simultaneous NetherNet connections.
See [docs/testing.md](docs/testing.md) for details.

# Compatibility

| Component | Supported version |
|---|---|
| Bedrock server | BDS 1.26.61, protocol 2225, NetherNet |
| Bedrock authentication | Offline identity or Microsoft account |
| Java server | 1.21.4, offline mode |
| Runtime | Java 21+ |
| Build | JDK 25 |

Bedrock supports joining, spawning, chat, block queries and movement. Java
Edition currently supports joining and status; movement, block access and chat
are follow-up work.

# Build from source

```bash
git clone https://github.com/Valcyle/MinterMC.git
cd MinterMC
./gradlew build
```

# Run a release

The release zip is universal: it contains the Linux/macOS and Windows start
scripts and the native libraries for Windows, macOS and Linux.

1. Install Java 21 or newer (`java -version`).
2. Download `mintermc-server-0.1.0.zip` from the Releases page and extract it.
3. Start the kernel from the extracted directory.

Windows:

```powershell
.\bin\mintermc-server.bat
```

Linux or macOS:

```bash
./bin/mintermc-server
```

4. The gRPC server listens on `127.0.0.1:50051` by default. Connect with any
   gRPC client using the service definition in
   `mintermc-protocol/src/main/proto/bot_service.proto`; the bundled
   [docs/testing.md](docs/testing.md) contains examples.
5. Create a `plugins/` directory in the extracted directory to load plugin JARs.

# Usage

The commands below assume a source checkout.

```bash
# Start the gRPC server (default port: 50051)
./gradlew :mintermc-server:run
```

The gRPC server binds to `127.0.0.1` by default. To expose it, set
`MINTER_GRPC_BIND` and `MINTER_GRPC_TOKEN`; every RPC must then send
`authorization: Bearer <token>`. See
[docs/testing.md](docs/testing.md#grpc-access) for details.

Connect to the server using any gRPC client with the service definition in:  
[`mintermc-protocol/src/main/proto/bot_service.proto`](mintermc-protocol/src/main/proto/bot_service.proto)

### Authentication

Offline identities are the default. To use a Microsoft account, set
`auth_mode` to `AUTH_MICROSOFT` and pass a stable `account` name in
`LoginRequest`. The first sign-in prints a device-code URL and code to the
kernel log; later sign-ins reuse the cached refresh token.

The token cache is written to `MINTER_AUTH_DIR` (default
`~/.mintermc/accounts`). It contains refresh tokens: keep it out of the
repository and do not log or share it. Microsoft mode has been verified against
BDS 1.26.61; test other server versions before relying on it. See
[`examples/python/join_be_auth.py`](examples/python/join_be_auth.py) for a
complete Bedrock example.

### Plugin Development

1. Add `mintermc-plugin-api` as a dependency in your project
2. Implement the `MinterPlugin` interface
3. Build a JAR and place it in the `plugins/` directory

```java
public class MyPlugin implements MinterPlugin {
    @Override
    public void onEnable(PluginContext context) {
        context.registerCommand("ping", (bot, command, args) ->
                bot.chat("pong from " + bot.getUsername()));
    }

    @Override
    public void onDisable() { /* cleanup */ }
}
```

Plugins receive events, commands and scheduling through `PluginContext` and
`Bot`. See [`docs/plugins.md`](docs/plugins.md) for the full API and
[`examples/plugins`](examples/plugins) for a buildable example.

# Note

This project is currently in early development. The gRPC server and plugin system are work in progress. The Java Edition and Bedrock Edition adapters are functional. Bedrock block and item registries are loaded and blocks can be queried; placing and breaking blocks is still being implemented.

> [!NOTE] 
> MinterMC uses [MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib) for Java Edition adapter implementation.
> MinterMC uses [Protocol](https://github.com/CloudburstMC/Protocol) for Bedrock Edition adapter implementation.

# Usage notice

MinterMC is an independent project. It contains no Minecraft game code or game
assets.

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG
OR MICROSOFT.**

Use MinterMC only on servers you own or where the operator permits automation.
It supports offline identities for offline-mode servers and Microsoft account
sign-in for online-mode servers. Online mode signs in with your own account
through the official Microsoft device code flow and does not bypass
authentication. Do not use it to impersonate players, share accounts, or access
servers without permission.

Bots may violate the rules of third-party servers. Download the Bedrock
Dedicated Server from Mojang; do not redistribute the server binary or its
world data.

# Author

* [Valcyle](https://discord.gg/ckQGXN46pu)

# License

MinterMC is licensed under the [MIT License](LICENSE).

# Third-party notices

MinterMC depends on and bundles third-party components. Their licenses and
attribution requirements are listed in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

