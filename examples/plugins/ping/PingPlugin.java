package example.ping;

import net.mcsmash.mintermc.api.MinterPlugin;
import net.mcsmash.mintermc.api.PluginContext;
import net.mcsmash.mintermc.api.event.ChatMessageEvent;
import net.mcsmash.mintermc.api.event.BotListener;

/**
 * Example MinterMC plugin.
 * <br>
 * Registers a {@code .ping} chat command and logs every message the bot
 * receives. Build it against {@code mintermc-plugin-api} and drop the JAR into
 * the {@code plugins} directory of the kernel.
 */
public final class PingPlugin implements MinterPlugin {

    private PluginContext context;

    @Override
    public void onEnable(PluginContext context) {
        this.context = context;

        context.registerCommand("ping", (bot, command, args) -> {
            String suffix = args.isEmpty() ? "" : " " + String.join(" ", args);
            bot.chat("pong from " + bot.getUsername() + suffix);
        });

        context.subscribe(new BotListener() {
            @Override
            public void onChat(ChatMessageEvent event) {
                context.getLogger().info("{}: {}",
                        event.sender().isEmpty() ? "server" : event.sender(),
                        event.content());
            }
        });

        context.getLogger().info("Ping plugin enabled");
    }

    @Override
    public void onDisable() {
        if (context != null) {
            context.getLogger().info("Ping plugin disabled");
        }
    }
}
