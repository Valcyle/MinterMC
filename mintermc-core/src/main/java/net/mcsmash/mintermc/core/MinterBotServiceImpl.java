package net.mcsmash.mintermc.core;

import net.mcsmash.mintermc.api.math.Location;
import net.mcsmash.mintermc.api.math.Vector3;
import net.mcsmash.mintermc.api.block.Block;
import net.mcsmash.mintermc.protocol.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Implementation of the gRPC BotService.
 * <br>
 * This class receives calls from the SDK over the network and routes them
 * to the appropriate BotSession (Virtual Thread) managed by the BotManager.
 */
public class MinterBotServiceImpl extends MinterBotServiceGrpc.MinterBotServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(MinterBotServiceImpl.class);
    private final BotManager botManager;
    private final BotSessionFactory sessionFactory;

    public MinterBotServiceImpl(BotManager botManager, BotSessionFactory sessionFactory) {
        this.botManager = botManager;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public void login(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
        logger.info("Received login request for bot: {} to {}:{}",
                request.getBotName(), request.getHost(), request.getPort());

        String sessionToken = UUID.randomUUID().toString();

        try {
            // Create the specific bot session via the factory
            BotSession session = sessionFactory.create(sessionToken, request);

            // Register and start the virtual thread loop
            botManager.registerSession(session);
            session.startEventLoop();

            LoginResponse response = LoginResponse.newBuilder()
                    .setSuccess(true)
                    .setSessionId(sessionToken)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to initialize bot session", e);
            responseObserver.onNext(LoginResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("Initialization failed: " + e.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getStatus(StatusRequest request, StreamObserver<StatusResponse> responseObserver) {
        BotSession session = botManager.getSessionByToken(request.getSessionId());

        if (session == null) {
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription("Invalid or expired session token")
                    .asRuntimeException());
            return;
        }

        Location loc = session.getLocation();
        Vector3 pos = loc.getPosition();

        StatusResponse response = StatusResponse.newBuilder()
                .setX(pos.getX())
                .setY(pos.getY())
                .setZ(pos.getZ())
                .setHealth(session.getHealth())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void moveTo(MoveRequest request, StreamObserver<MoveResponse> responseObserver) {
        BotSession session = botManager.getSessionByToken(request.getSessionId());

        if (session == null) {
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription("Invalid or expired session token")
                    .asRuntimeException());
            return;
        }

        // Delegate the high-level movement to the Virtual Thread event loop.
        // We use the CompletableFuture to detect completion.
        session.moveTo(new Vector3(request.getTargetX(), request.getTargetY(), request.getTargetZ()))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.error("Movement failed for session {}", request.getSessionId(), ex);
                        responseObserver.onNext(MoveResponse.newBuilder().setReached(false).build());
                    } else {
                        responseObserver.onNext(MoveResponse.newBuilder().setReached(true).build());
                    }
                    responseObserver.onCompleted();
                });
    }

    @Override
    public void getBlock(BlockRequest request, StreamObserver<BlockResponse> responseObserver) {
        BotSession session = botManager.getSessionByToken(request.getSessionId());

        if (session == null) {
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription("Invalid or expired session token")
                    .asRuntimeException());
            return;
        }

        Block block = session.getBlockAt(
                new Vector3(request.getX(), request.getY(), request.getZ()));
        BlockResponse response = block == null
                ? BlockResponse.newBuilder().setFound(false).build()
                : BlockResponse.newBuilder()
                        .setFound(true)
                        .setMaterial(block.getMaterialName())
                        .setPassable(block.isPassable())
                        .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<ClientCommand> eventStream(StreamObserver<ServerEvent> responseObserver) {
        // Here we handle the bi-directional stream for commands and events.
        return new StreamObserver<ClientCommand>() {
            private final Object sendLock = new Object();
            private BotSession session;
            private boolean subscribed;

            private final BotSessionListener listener = (sender, content) -> {
                ServerEvent event = ServerEvent.newBuilder()
                        .setChatReceived(ChatMessage.newBuilder()
                                .setSender(sender)
                                .setContent(content)
                                .setTimestamp(System.currentTimeMillis())
                                .build())
                        .build();
                try {
                    synchronized (sendLock) {
                        responseObserver.onNext(event);
                    }
                } catch (Exception e) {
                    logger.debug("Could not deliver a chat event to the SDK", e);
                }
            };

            private void detach() {
                if (subscribed && session != null) {
                    session.removeListener(listener);
                    subscribed = false;
                    logger.debug("Detached the SDK listener from session {}",
                            session.getSessionToken());
                }
            }

            @Override
            public void onNext(ClientCommand command) {
                if (session == null) {
                    session = botManager.getSessionByToken(command.getSessionId());
                    if (session == null) {
                        responseObserver.onError(io.grpc.Status.NOT_FOUND
                                .withDescription("Invalid or expired session token")
                                .asRuntimeException());
                        return;
                    }
                    session.addListener(listener);
                    subscribed = true;
                    logger.debug("Attached an SDK listener to session {}", command.getSessionId());
                }

                if (command.hasChatMessage()) {
                    // Send to the virtual thread to execute
                    session.execute(() -> session.chat(command.getChatMessage()));
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.warn("Event stream error", t);
                detach();
            }

            @Override
            public void onCompleted() {
                logger.info("Event stream completed by client");
                detach();
                responseObserver.onCompleted();
            }
        };
    }
}
