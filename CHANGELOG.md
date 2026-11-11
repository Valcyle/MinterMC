# Changelog

All notable changes to MinterMC are documented in this file.

## [0.1.0] - 2026-11-11

Initial release.

### Added

- Bedrock Edition adapter with NetherNet and RakNet transports.
- Offline identities and Microsoft account sign-in (device code, token cache).
- Bedrock login, spawn sequence, movement corrections, chat, block queries and
  sub-chunk caching.
- Java Edition adapter for offline server joining and status.
- gRPC API for login, status, movement, block queries and chat events.
- Plugin runtime with commands, events and scheduling.
- A* navigation plugin example.
- Loopback-only gRPC binding by default and optional token authentication.
- Documentation, examples, third-party notices and the Minecraft usage
  disclaimer.

### Known limitations

- Java Edition movement, block access and chat are not implemented.
- Java Edition Microsoft sign-in is implemented but not verified end to end.
- No automatic reconnect; block update events and inventory are not implemented.
- BDS 1.26.x requires a UDP port range for multiple NetherNet bots.
