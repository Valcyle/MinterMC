# MinterMC

> [!CAUTION]
> This project is currently in early development. This project is not yet ready for production use.
> This project is not affiliated with Mojang Studios or Microsoft.

A universal Minecraft bot kernel that abstracts the differences between **Java Edition** and **Bedrock Edition**.  
Exposes a single **gRPC API** so bots can be controlled from any programming language via an SDK.

# DEMO

> Coming soon

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

