package net.mcsmash.mintermc.testclient;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.mcsmash.mintermc.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * gRPC test client for MinterMC.
 * <br>
 * Connects to the MinterMC gRPC server and sequentially tests the Login,
 * GetStatus, and MoveTo RPCs to verify the server is responding correctly.
 */
public class TestClient {

    private static final Logger logger = LoggerFactory.getLogger(TestClient.class);

    /** Target server host */
    private static final String HOST = "localhost";

    /** Target server port */
    private static final int PORT = 50051;

    /**
     * Entry point for the test client.
     *
     * @param args command-line arguments (not used)
     * @throws InterruptedException if the channel shutdown is interrupted
     */
    public static void main(String[] args) throws InterruptedException {
        logger.info("=== MinterMC gRPC Test Client ===");
        logger.info("Connecting to {}:{}", HOST, PORT);

        // Create a plaintext (no-TLS) gRPC channel for local testing
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(HOST, PORT)
                .usePlaintext()
                .build();

        MinterBotServiceGrpc.MinterBotServiceBlockingStub stub =
                MinterBotServiceGrpc.newBlockingStub(channel);

        try {
            testLogin(stub);
        } finally {
            // Always shut down the channel cleanly
            logger.info("Shutting down channel...");
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            logger.info("=== Test Complete ===");
        }
    }

    /**
     * Tests the Login RPC and, on success, chains GetStatus and MoveTo tests.
     *
     * @param stub the blocking gRPC stub to use for calls
     */
    private static void testLogin(MinterBotServiceGrpc.MinterBotServiceBlockingStub stub) {
        logger.info("--- [Test 1] Login ---");

        LoginRequest loginRequest = LoginRequest.newBuilder()
                .setBotName("TestBot")
                .setTargetEdition(EditionType.JAVA_EDITION)
                .setHost("mc.example.com")
                .setPort(25565)
                .build();

        LoginResponse loginResponse = stub.login(loginRequest);

        if (!loginResponse.getSuccess()) {
            logger.error("Login FAILED: {}", loginResponse.getErrorMessage());
            return;
        }

        String sessionId = loginResponse.getSessionId();
        logger.info("Login SUCCESS! Session ID: {}", sessionId);

        testGetStatus(stub, sessionId);
        testMoveTo(stub, sessionId);
    }

    /**
     * Tests the GetStatus RPC using the given session ID.
     *
     * @param stub      the blocking gRPC stub to use for calls
     * @param sessionId the session ID obtained from a successful login
     */
    private static void testGetStatus(MinterBotServiceGrpc.MinterBotServiceBlockingStub stub,
                                      String sessionId) {
        logger.info("--- [Test 2] GetStatus ---");

        StatusRequest statusRequest = StatusRequest.newBuilder()
                .setSessionId(sessionId)
                .build();

        StatusResponse statusResponse = stub.getStatus(statusRequest);

        logger.info("GetStatus SUCCESS!");
        logger.info("  Position: ({}, {}, {})", statusResponse.getX(), statusResponse.getY(), statusResponse.getZ());
        logger.info("  Health:   {}", statusResponse.getHealth());
    }

    /**
     * Tests the MoveTo RPC using the given session ID.
     *
     * @param stub      the blocking gRPC stub to use for calls
     * @param sessionId the session ID obtained from a successful login
     */
    private static void testMoveTo(MinterBotServiceGrpc.MinterBotServiceBlockingStub stub,
                                   String sessionId) {
        logger.info("--- [Test 3] MoveTo ---");

        MoveRequest moveRequest = MoveRequest.newBuilder()
                .setSessionId(sessionId)
                .setTargetX(100.0)
                .setTargetY(64.0)
                .setTargetZ(200.0)
                .build();

        MoveResponse moveResponse = stub.moveTo(moveRequest);

        if (moveResponse.getReached()) {
            logger.info("MoveTo SUCCESS! Bot reached the destination.");
        } else {
            logger.warn("MoveTo result: Bot did NOT reach the destination.");
        }
    }
}
