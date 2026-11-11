package net.mcsmash.mintermc.server;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrpcTokenInterceptorTest {

    private static final byte[] TOKEN = "s3cret".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsRawAndBearerTokens() {
        assertTrue(GrpcTokenInterceptor.matches(TOKEN, "s3cret"));
        assertTrue(GrpcTokenInterceptor.matches(TOKEN, "Bearer s3cret"));
        assertTrue(GrpcTokenInterceptor.matches(TOKEN, "bearer s3cret"));
        assertTrue(GrpcTokenInterceptor.matches(TOKEN, "  Bearer   s3cret  "));
    }

    @Test
    void rejectsMissingOrWrongTokens() {
        assertFalse(GrpcTokenInterceptor.matches(TOKEN, null));
        assertFalse(GrpcTokenInterceptor.matches(TOKEN, ""));
        assertFalse(GrpcTokenInterceptor.matches(TOKEN, "Bearer"));
        assertFalse(GrpcTokenInterceptor.matches(TOKEN, "wrong"));
        assertFalse(GrpcTokenInterceptor.matches(TOKEN, "Bearer wrong"));
        assertFalse(GrpcTokenInterceptor.matches(TOKEN, "Basic s3cret"));
    }
}
