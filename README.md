# MinterMC
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
[<img src="https://img.youtube.com/vi/8_sQTFVja0g/0.jpg" alt="tutorial 1" width="200">](https://www.youtube.com/watch?v=8_sQTFVja0g)

Currently joining java edition servers is supported.
Python, C++, JavaScript and Java examples are available.
Check out [`examples`](examples/) folder for more examples.

# Features

- **Protocol Abstraction** — Control Java Edition and Bedrock Edition bots through a single unified API
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
    public void onEnable() { /* setup */ }

    @Override
    public void onDisable() { /* cleanup */ }
}
```

# Note

This project is currently in early development. The gRPC server and plugin system are work in progress, but the Minecraft protocol adapters (Java Edition / Bedrock Edition) are not yet implemented.

> [!NOTE] 
> MinterMC uses [MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib) for Java Edition adapter implementation.
> MinterMC uses [Protocol](https://github.com/CloudburstMC/Protocol) for Bedrock Edition adapter implementation.

# Author

* [Valcyle](https://discord.gg/ckQGXN46pu)

# License

TBD

