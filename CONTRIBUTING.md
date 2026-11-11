# Contributing to MinterMC

Thank you for contributing to **MinterMC**.

The project targets **Java 21 (LTS)** at runtime and builds with **JDK 25** and
**gRPC**. Contributions should preserve performance, scalability, and code
clarity.

---

## Prerequisites

Before you start, ensure you have the following installed:

* **JDK 25:** Required to build. The produced classes target Java 21; the project uses virtual threads and modern language features.
* **Gradle:** The build uses the Kotlin DSL.
* **Protobuf Compiler (protoc):** Required if you are modifying gRPC definitions.
* **Git:** For version control.

---

## Development Setup

1. Fork and Clone:
```bash
git clone https://github.com/Valcyle/MinterMC.git
cd MinterMC
```

2. Build the Project:
Verify that the infrastructure is working on your machine:
```bash
./gradlew build
```

3. Project Structure:
* mintermc-core: Common logic and Universal Models.
* mintermc-protocol: gRPC .proto definitions.
* mintermc-adapter-java: JE protocol implementation (MCProtocolLib).
* mintermc-adapter-bedrock: BE protocol implementation (Cloudburst).
* mintermc-server: The core kernel and session management.

---

## Coding Standards

To keep the codebase consistent, follow these guidelines:

* **Java 21:** Use records, sealed classes, and switch pattern matching where appropriate.
* **Concurrency:** Use **virtual threads** (`Thread.ofVirtual()`) instead of traditional thread pools for bot sessions.
* **Naming:** Follow standard Java naming conventions. Use descriptive names for gRPC messages in `.proto` files.
* **Documentation:** All public APIs in `mintermc-core` and `mintermc-server` should have Javadoc.
* **Code Style:** Follow the existing code style and conventions.

---

## Protocol Changes (gRPC)

If you modify files in mintermc-protocol/src/main/proto:

1. Ensure the changes are **backward compatible**.
2. Run the following to update the Java stubs:
```bash
./gradlew generateProto
```
3. Protocol changes affect downstream SDKs (Python, JS, Rust). Document them in the PR.

---

## Testing

Stability is important for the project.

* If you can, write **JUnit 5** tests for new features.
* If you add a new packet handler, include a test case in the respective adapter module.
* Run all tests before submitting:
```bash
./gradlew test
```

---

## Repository hygiene

Read [AGENTS.md](AGENTS.md) before committing. Do not commit personal or environment-specific information such as absolute local paths, usernames, hostnames, LAN IPs, XUIDs, tokens, or captured login artifacts. Use placeholders in examples.

---

## Pull Request Process

1. Create a Branch: Use a descriptive name like feat/pathfinding-api or fix/je-login-packet.
2. Keep it Focused: One PR should solve one issue or implement one feature.
3. Describe Your Changes: Explain what you changed and why in the PR description.
4. Review: At least one maintainer must review and approve your code before it is merged.

---

## Communication

* Discussions: For general ideas and architecture talks.
* Issues: For bugs and specific feature tasks.

---

## Disclaimer

**Official Minecraft Disclaimer**

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

This project, **MinterMC**, is an independent third-party tool and is not affiliated with, endorsed by, or integrated with Mojang AB or Microsoft Corporation. Minecraft is a trademark of Mojang AB.

**EULA and server rules**

Users are responsible for complying with the [Minecraft End User License Agreement](https://www.minecraft.net/en-us/eula), the [Brand and Asset Guidelines](https://www.minecraft.net/en-us/usage-guidelines), and the terms of service of any server they connect to.

* The repository does not include official Minecraft binaries or assets.
* The project is intended for development, research, and testing.

**Limitation of Liability**

The developers of MinterMC are not responsible for any account bans, server blacklists, or other consequences resulting from the use of this software. By using this tool, you agree to comply with the terms of service of any Minecraft server you connect to.