package net.mcsmash.mintermc.server;

import net.mcsmash.mintermc.core.BotManager;
import net.mcsmash.mintermc.core.MinterBotServiceImpl;
import net.mcsmash.mintermc.core.PluginManager;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.mcsmash.mintermc.core.BotSessionFactory;
import net.mcsmash.mintermc.adapter.java.JavaEditionBotSession;
import net.mcsmash.mintermc.core.BotOptions;
import net.mcsmash.mintermc.protocol.EditionType;

import java.io.IOException;

/**
 * The main entry point for the MinterMC bot kernel.
 * <br>
 * Initializes the bot manager, loads plugins from the environment,
 * and starts the gRPC server to accept connections from SDKs.
 */
public class MinterServer {
    
    private static final Logger logger = LoggerFactory.getLogger(MinterServer.class);

    private final int port;
    private final Server server;
    private final BotManager botManager;
    private final PluginManager pluginManager;

    public MinterServer(int port) {
        this.port = port;
        this.botManager = new BotManager();
        this.pluginManager = new PluginManager(new java.io.File("plugins"));
        
        // Load plugins before starting the server
        this.pluginManager.loadPlugins();
        this.pluginManager.enablePlugins();
        
        // Factory that knows how to create specific adapter sessions
        BotSessionFactory sessionFactory = (sessionToken, request) -> {
            if (request.getTargetEdition() == EditionType.JAVA_EDITION) {
                JavaEditionBotSession session = new JavaEditionBotSession(sessionToken);
                
                // Trigger connection (this is blocking/network-heavy, 
                // but MinterBotServiceImpl calls this inside its own context)
                // For now we use default options or map from request if available
                BotOptions options = new BotOptions.Builder()
                        .version("1.21.4") // Default version
                        .build();
                
                session.connect(
                    request.getHost(), 
                    request.getPort(), 
                    request.getBotName(), 
                    false, // Offline mode for now to simplify testing
                    options
                );
                
                return session;
            } else {
                throw new UnsupportedOperationException("Bedrock Edition is not yet supported");
            }
        };

        // MinterBotServiceImpl handles the gRPC endpoints
        this.server = ServerBuilder.forPort(port)
                .addService(new MinterBotServiceImpl(botManager, sessionFactory))
                .build();
    }

    /**
     * Start the gRPC server and block until shutdown.
     */
    public void start() throws IOException, InterruptedException {
        server.start();
        logger.info("MinterMC gRPC Server started, listening on {}", port);
        
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
