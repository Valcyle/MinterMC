# Plugin Development

MinterMC plugins are JAR files that extend the kernel in-process. A plugin can
register chat commands, subscribe to bot events, and schedule work on a bot's
event loop.

## When to write a plugin

The kernel owns everything that reproduces game state or the protocol:

- connections, authentication, reconnects, keepalive
- packet encoding and decoding
- world state, block updates, inventory state
- final movement (physics that the server validates)

Plugins own decisions:

- pathfinding and navigation
- combat, aiming, and targeting
- building, mining, farming, and other goals
- chat commands and server-specific behavior

## Plugin structure

```java
package example.ping;

import net.mcsmash.mintermc.api.MinterPlugin;
import net.mcsmash.mintermc.api.PluginContext;

public final class PingPlugin implements MinterPlugin {

    @Override
    public void onEnable(PluginContext context) {
        context.registerCommand("ping", (bot, command, args) ->
                bot.chat("pong from " + bot.getUsername()));
    }

    @Override
    public void onDisable() {
    }
}
```

The kernel loads one class implementing `MinterPlugin` per JAR and calls
`onEnable` once at startup.

## PluginContext

| Method | Purpose |
|---|---|
| `getLogger()` | Logger tagged with the plugin name |
| `getDataFolder()` | Directory for the plugin's own files |
| `getBots()` | Bots that are currently registered |
| `getBot(UUID)` | Look up a bot by its MinterMC id |
| `registerCommand(name, handler)` | Register a `.name` chat command |
| `subscribe(listener)` | Receive events from every current and future bot |
| `unsubscribe(listener)` | Remove an event listener |

`onBotJoin(Bot)` and `onBotLeave(Bot)` are also available as convenience hooks
on `MinterPlugin`.

## Events

`BotListener` has empty defaults, so a plugin only overrides what it needs.

| Event | When |
|---|---|
| `ChatMessageEvent` | A chat or system message arrives |
| `BotSpawnEvent` | The bot entered the world |
| `BotDisconnectEvent` | The session ended |
| `HealthChangeEvent` | The reported health changed |

Events run on the bot's event loop. Do not block in a callback; offload heavy
work to another thread and use the scheduler to return to the loop.

## Commands

Commands use a dot prefix. `.goto 10 64 10` invokes the handler registered as
`goto` with the arguments `[10, 64, 10]`. Messages that the bot itself sent are
ignored, so a plugin cannot trigger itself through the server's chat echo.

## Scheduling

```java
ScheduledTask task = bot.every(20, () -> {
    // runs once per second on the bot's event loop
});
task.cancel();

bot.after(40, () -> {
    // runs once, two seconds later
});
```

One tick is 50 ms. Scheduled tasks stop automatically when the session ends.

## Threading rules

- Callbacks run on the bot's virtual thread, one at a time.
- Do not block that thread: no sleeps, no long I/O, no waiting on network calls.
- Bot state such as `getLocation()` and `getHealth()` is safe to read.
- `getBlockAt` may wait for the server to send a section, so avoid calling it
  in tight loops.
- `getLoadedBlockAt` never waits and returns `null` when the section is not
  cached yet. Prefer it for pathfinding and per-tick checks.
- `getLocation().y` is the position Bedrock reports, which is at eye level:
  subtract `1.62` to get the block the player stands on.

## Build and install

```bash
./gradlew :mintermc-plugin-api:jar
javac -cp "mintermc-plugin-api/build/libs/*" -d build/ping examples/plugins/ping/PingPlugin.java
jar --create --file plugins/ping.jar -C build/ping .
```

The kernel scans the `plugins` directory on startup. Per-plugin data is stored
under `plugin-data/<PluginClassName>/`.

## Examples

| Example | Command | Notes |
|---|---|---|
| [`examples/plugins/ping`](../examples/plugins/ping) | `.ping` | Replies with `pong from <bot>` |
| [`examples/plugins/nav`](../examples/plugins/nav) | `.goto <x> <z>` | A* over cached blocks; same level or drops, no jump |
