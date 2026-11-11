package net.mcsmash.mintermc.server;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Requires a shared token in the gRPC {@code authorization} metadata.
 * <br>
 * The token is compared in constant time. Both {@code Bearer <token>} and the
 * raw token are accepted.
 */
final class GrpcTokenInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final byte[] expected;

    GrpcTokenInterceptor(String token) {
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
            Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        if (!matches(expected, headers.get(AUTHORIZATION))) {
            call.close(Status.UNAUTHENTICATED
                    .withDescription("Missing or invalid MinterMC token"), new Metadata());
            return new ServerCall.Listener<ReqT>() {
            };
        }
        return next.startCall(call, headers);
    }

    static boolean matches(byte[] expected, String headerValue) {
        if (headerValue == null) {
            return false;
        }
        String value = headerValue.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            value = value.substring(7).trim();
        }
        if (value.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(expected, value.getBytes(StandardCharsets.UTF_8));
    }
}
