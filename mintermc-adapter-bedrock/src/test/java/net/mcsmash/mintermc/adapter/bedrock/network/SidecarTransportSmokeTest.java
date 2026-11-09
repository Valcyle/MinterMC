package net.mcsmash.mintermc.adapter.bedrock.network;

import org.junit.jupiter.api.Test;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.common.util.VarInts;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Smoke test for the Node NetherNet sidecar transport.
 * <br>
 * Starts the sidecar with a captured identity, waits for the server's
 * NetworkSettings batch, sends the captured login batch compressed, and checks
 * that the server sends anything back. Skipped unless the sidecar script and
 * the captured JS login/raw batch paths are provided.
 */
class SidecarTransportSmokeTest {

    @Test
    void sidecarCarriesCapturedLogin() throws Exception {
        String script = System.getenv("MINTER_SIDECAR_SCRIPT");
        String loginFile = System.getenv("MINTER_REPLAY_LOGIN");
        String rawFile = System.getenv("MINTER_REPLAY_RAW");
        assumeTrue(script != null && loginFile != null && rawFile != null,
                "sidecar smoke test environment is not set");

        String loginJson = Files.readString(Path.of(loginFile));
        // Keep the JSON parsing dependency-free: the captured file is small and
        // only three string fields are needed.
        String privateKey = extractJsonString(loginJson, "privateKeyDer");
        String token = extractJsonString(loginJson, "identityToken");
        String host = extractJsonString(loginJson, "host");

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("host", host);
        config.put("privateKeyDerBase64", privateKey);
        config.put("token", token);
        config.put("domain", "MinterMC");
        config.put("listenPort", 0);

        Path configFile = Files.createTempFile("mintermc-sidecar", ".json");
        Files.writeString(configFile, toJson(config), StandardCharsets.UTF_8);

        Process process = new ProcessBuilder("node", script, configFile.toString())
                .redirectErrorStream(true)
                .start();

        try {
            int port = readListeningPort(process);
            System.out.println("Sidecar listening on " + port);

            try (Socket socket = new Socket("127.0.0.1", port)) {
                socket.setSoTimeout(25_000);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                // The Bedrock client starts the exchange; the server answers with
                // NetworkSettings only after this request.
                writeFrame(out, requestNetworkSettingsBatch());
                System.out.println("Sidecar sent RequestNetworkSettings");

                byte[] networkSettings = readFrame(in);
                System.out.println("Sidecar first inbound: " + networkSettings.length + " bytes");

                byte[] rawLogin = Files.readAllBytes(Path.of(rawFile));
                byte[] compressed = rawLogin;
                writeFrame(out, compressed);
                System.out.println("Sidecar sent login: " + compressed.length + " bytes uncompressed");

                byte[] response = readFrame(in);
                System.out.println("Sidecar response: " + response.length + " bytes");
                assertTrue(response.length > 0, "server answered after login");
            }
        } finally {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            Files.deleteIfExists(configFile);
        }
    }

    private static int readListeningPort(Process process) throws IOException {
        var reader = process.inputReader(StandardCharsets.UTF_8);
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("[sidecar] " + line);
            if (line.startsWith("LISTENING ")) {
                Thread logReader = new Thread(() -> {
                    try {
                        String next;
                        while ((next = reader.readLine()) != null) {
                            System.out.println("[sidecar] " + next);
                        }
                    } catch (IOException ignored) {
                        // process ended
                    }
                }, "sidecar-log");
                logReader.setDaemon(true);
                logReader.start();
                return Integer.parseInt(line.substring("LISTENING ".length()).trim());
            }
        }
        throw new IOException("sidecar exited before listening");
    }

    private static byte[] readFrame(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0) {
            throw new IOException("invalid frame length " + length);
        }
        return in.readNBytes(length);
    }

    private static void writeFrame(DataOutputStream out, byte[] payload) throws IOException {
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }

    private static byte[] requestNetworkSettingsBatch() {
        RequestNetworkSettingsPacket packet = new RequestNetworkSettingsPacket();
        packet.setProtocolVersion(Bedrock_v2225.CODEC.getProtocolVersion());
        ByteBuf body = Unpooled.buffer();
        try {
            Bedrock_v2225.CODEC.tryEncode(Bedrock_v2225.CODEC.createHelper(), body, packet);
            ByteBuf batch = Unpooled.buffer();
            VarInts.writeUnsignedInt(batch, body.readableBytes());
            batch.writeBytes(body);
            byte[] bytes = new byte[batch.readableBytes()];
            batch.readBytes(bytes);
            batch.release();
            return bytes;
        } finally {
            body.release();
        }
    }

    private static byte[] deflateRaw(byte[] data) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    private static String extractJsonString(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new IllegalArgumentException("missing " + key);
        }
        start += marker.length();
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '\\') {
                end += 2;
                continue;
            }
            if (c == '"') {
                break;
            }
            end++;
        }
        return json.substring(start, end);
    }

    private static String toJson(Map<String, Object> values) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append('"').append(entry.getKey()).append("\":");
            if (entry.getValue() instanceof Number number) {
                out.append(number);
            } else {
                out.append('"').append(escape(String.valueOf(entry.getValue()))).append('"');
            }
        }
        return out.append('}').toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
