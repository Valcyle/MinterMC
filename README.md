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

# DEMO
[<img src="https://img.youtube.com/vi/8_sQTFVja0g/0.jpg" alt="1500 bots joining using MinterMC" width="200">](https://www.youtube.com/watch?v=8_sQTFVja0g)

Currently, joining Java Edition and Bedrock Edition offline servers is supported.
Bedrock support is under active development.
Python, C++, JavaScript and Java examples are available.
Check out [`examples`](examples/) folder for more examples.

# Features

- **Protocol Abstraction** — Control Java Edition and Bedrock Edition bots through a single unified API
- **Chat and Events** — Send chat and receive server events over the bidirectional `EventStream` RPC
- **Block Access** — Read block identifiers and passability from the loaded world
- **Movement** — Walk bots to coordinates with server-authoritative movement
- **Language Agnostic** — Any language with a gRPC client can control bots (Python, TypeScript, Go, etc.)
- **Agent-per-Bot Model** — Each bot runs on its own Java 25 Virtual Thread for full isolation
- **Plugin System** — Drop a JAR into `plugins/` to extend bot behavior (pathfinding, combat, etc.)
- **Scalable Design** — Designed to handle hundreds of simultaneous bots

# Requirement

- Java 25+

# Installation

```bash
git clone https://github.com/Valcyle/MinterMC.git
cd MinterMC
./gradlew build
```

# Usage

```bash
# Start the gRPC server (default port: 50051)
./gradlew :mintermc-server:run
```

Connect to the server using any gRPC client with the service definition in:  
[`mintermc-protocol/src/main/proto/bot_service.proto`](mintermc-protocol/src/main/proto/bot_service.proto)

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

# Author

* [Valcyle](https://discord.gg/ckQGXN46pu)

# License

TBD

# Third-party notices

MinterMC depends on and bundles third-party components. Their licenses and
attribution requirements are listed in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

