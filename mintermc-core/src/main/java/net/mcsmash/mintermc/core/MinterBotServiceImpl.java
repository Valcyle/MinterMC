package net.mcsmash.mintermc.core;

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

        // We would fetch coordinates from the BotSession state here.
        StatusResponse response = StatusResponse.newBuilder()
                .setX(0.0)
                .setY(64.0)
                .setZ(0.0)
                .setHealth(20.0f)
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
        session.moveTo(request.getTargetX(), request.getTargetY(), request.getTargetZ())
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
    public StreamObserver<ClientCommand> eventStream(StreamObserver<ServerEvent> responseObserver) {
        // Here we handle the bi-directional stream for events like chat messages.
        return new StreamObserver<ClientCommand>() {
            private BotSession session;

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
                }

                if (command.hasChatMessage()) {
                    // Send to the virtual thread to execute
                    session.execute(() -> session.chat(command.getChatMessage()));
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.warn("Event stream error", t);
                if (session != null) {
                    session.disconnect(); // Terminate gracefully
                }
            }

            @Override
            public void onCompleted() {
                logger.info("Event stream completed by client");
                responseObserver.onCompleted();
            }
        };
    }
}
