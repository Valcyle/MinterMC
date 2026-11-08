# Plugin examples

Each directory contains a single plugin source file. A plugin is a JAR that
contains one class implementing `net.mcsmash.mintermc.api.MinterPlugin`.

## Build the API first

```bash
./gradlew :mintermc-plugin-api:jar
```

The jar is written to `mintermc-plugin-api/build/libs/`.

## Build the ping example

Windows:

```powershell
javac -cp "mintermc-plugin-api/build/libs/*" -d build\ping examples\plugins\ping\PingPlugin.java
jar --create --file plugins\ping.jar -C build\ping .
```

Linux or macOS:

```bash
javac -cp "mintermc-plugin-api/build/libs/*" -d build/ping examples/plugins/ping/PingPlugin.java
jar --create --file plugins/ping.jar -C build/ping .
```

Start the kernel and connect a bot. A player can then type `.ping` in chat, and
the bot replies with `pong from <bot name>`.

See [`docs/plugins.md`](../../docs/plugins.md) for the full API.
