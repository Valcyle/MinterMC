package net.mcsmash.mintermc.server;

import net.mcsmash.mintermc.adapter.bedrock.BedrockEditionBotSession;
import net.mcsmash.mintermc.core.BotManager;
import net.mcsmash.mintermc.core.CommandRegistry;
import net.mcsmash.mintermc.core.MinterBotServiceImpl;
import net.mcsmash.mintermc.core.PluginManager;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.mcsmash.mintermc.core.BotSessionFactory;
import net.mcsmash.mintermc.adapter.java.JavaEditionBotSession;
import net.mcsmash.mintermc.api.bot.AuthMode;
import net.mcsmash.mintermc.api.bot.BotOptions;
import net.mcsmash.mintermc.auth.MicrosoftAuthService;
import net.mcsmash.mintermc.protocol.EditionType;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * The main entry point for the MinterMC bot kernel.
 * <br>
 * Initializes the bot manager, loads plugins from the environment,
 * and starts the gRPC server to accept connections from SDKs.
 */
public class MinterServer {

    private static final Logger logger = LoggerFactory.getLogger(MinterServer.class);

    /** Overridable so a local server can be tested without rebuilding. */
    private static final String JAVA_VERSION =
            envOrDefault("MINTER_JAVA_VERSION", "1.21.4");
    private static final String BEDROCK_VERSION =
            envOrDefault("MINTER_BEDROCK_VERSION", "1.26.60");
    private static final String BEDROCK_TRANSPORT =
            envOrDefault("MINTER_BEDROCK_TRANSPORT", "nethernet");

    private final int port;
    private final String bindHost;
    private final boolean tokenAuthEnabled;
    private final Server server;
    private final BotManager botManager;
    private final PluginManager pluginManager;
    private final CommandRegistry commandRegistry;
    private final MicrosoftAuthService authService;

    public MinterServer(int port) {
        this.port = port;
        this.bindHost = envOrDefault("MINTER_GRPC_BIND", "127.0.0.1");
        String token = System.getenv("MINTER_GRPC_TOKEN");
        this.tokenAuthEnabled = token != null && !token.isBlank();

        boolean loopback = isLoopback(bindHost);
        boolean allowInsecure = Boolean.parseBoolean(
                envOrDefault("MINTER_GRPC_ALLOW_INSECURE", "false"));
        if (!loopback && !tokenAuthEnabled && !allowInsecure) {
            throw new IllegalStateException("Refusing to bind the gRPC server to "
                    + bindHost + " without MINTER_GRPC_TOKEN. Set a token, bind to "
                    + "127.0.0.1, or set MINTER_GRPC_ALLOW_INSECURE=true.");
        }
        if (!loopback && !tokenAuthEnabled) {
            logger.warn("MINTER_GRPC_ALLOW_INSECURE is set; the gRPC API is exposed "
                    + "without authentication");
        }

        this.botManager = new BotManager();
        this.commandRegistry = new CommandRegistry();
        this.authService = new MicrosoftAuthService(MicrosoftAuthService.defaultCacheDirectory());
        this.pluginManager = new PluginManager(
                new java.io.File(envOrDefault("MINTER_PLUGIN_DIR", "plugins")),
                new java.io.File(envOrDefault("MINTER_PLUGIN_DATA", "plugin-data")),
                botManager,
                commandRegistry);

        // Load plugins before starting the server
        this.pluginManager.loadPlugins();
        this.pluginManager.enablePlugins();

        // Factory that knows how to create specific adapter sessions
        BotSessionFactory sessionFactory = (sessionToken, request) -> {
            AuthMode authMode = request.getAuthMode()
                    == net.mcsmash.mintermc.protocol.AuthMode.AUTH_MICROSOFT
                            ? AuthMode.MICROSOFT
                            : AuthMode.OFFLINE;
            String account = request.getAccount().isBlank() ? "default" : request.getAccount();

            if (request.getTargetEdition() == EditionType.JAVA_EDITION) {
                JavaEditionBotSession session = new JavaEditionBotSession(sessionToken);

                BotOptions options = new BotOptions.Builder()
                        .version(JAVA_VERSION)
                        .authMode(authMode)
                        .account(account)
                        .build();

                session.connect(
                        request.getHost(),
                        request.getPort(),
                        request.getBotName(),
                        authMode == AuthMode.MICROSOFT,
                        options,
                        authService);

                return session;
            } else if (request.getTargetEdition() == EditionType.BEDROCK_EDITION) {
                BedrockEditionBotSession session = new BedrockEditionBotSession(sessionToken);

                BotOptions options = new BotOptions.Builder()
                        .version(BEDROCK_VERSION) // Protocol 2225 (Bedrock_v2225)
                        .transport(BEDROCK_TRANSPORT)
                        .authMode(authMode)
                        .account(account)
                        .build();

                session.connect(
                        request.getHost(),
                        request.getPort(),
                        request.getBotName(),
                        authMode == AuthMode.MICROSOFT,
                        options,
                        authService);

                return session;
            } else {
                throw new UnsupportedOperationException("Requested edition is not supported");
            }
        };

        // MinterBotServiceImpl handles the gRPC endpoints
        NettyServerBuilder builder = NettyServerBuilder.forAddress(
                new InetSocketAddress(bindHost, port));
        if (tokenAuthEnabled) {
            builder.intercept(new GrpcTokenInterceptor(token));
        }
        this.server = builder
                .addService(new MinterBotServiceImpl(
                        botManager, sessionFactory, pluginManager, commandRegistry))
                .build();
    }

    /**
     * Start the gRPC server and block until shutdown.
     */
    public void start() throws IOException, InterruptedException {
        server.start();
        logger.info("MinterMC gRPC Server started on {}:{} (token auth {})",
                bindHost, port, tokenAuthEnabled ? "enabled" : "disabled");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down gRPC server since JVM is shutting down");
            try {
                MinterServer.this.stop();
            } catch (InterruptedException e) {
                logger.error("Error during server shutdown", e);
            }
            logger.info("Server shut down");
        }));

        server.awaitTermination();
    }

    /**
     * Stop the gRPC server.
     */
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
        }
        authService.close();
    }

    private static boolean isLoopback(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid MINTER_GRPC_BIND address: " + host, e);
        }
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 50051; // Default gRPC port
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        MinterServer minterServer = new MinterServer(port);
        minterServer.start();
    }
}

