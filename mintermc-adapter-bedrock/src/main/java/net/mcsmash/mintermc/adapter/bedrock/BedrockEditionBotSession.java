package net.mcsmash.mintermc.adapter.bedrock;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.concurrent.ScheduledFuture;
import net.mcsmash.mintermc.adapter.bedrock.network.BedrockBotPacketHandler;
import net.mcsmash.mintermc.adapter.bedrock.network.BedrockLoginHelper;
import net.mcsmash.mintermc.adapter.bedrock.network.BedrockNetherNetConnector;
import net.mcsmash.mintermc.adapter.bedrock.network.BedrockStaticDefinitions;
import net.mcsmash.mintermc.adapter.bedrock.network.MinterLoginSerializer;
import net.mcsmash.mintermc.adapter.bedrock.world.BedrockBlock;
import net.mcsmash.mintermc.adapter.bedrock.world.SectionKey;
import net.mcsmash.mintermc.api.block.Block;
import net.mcsmash.mintermc.api.bot.BotOptions;
import net.mcsmash.mintermc.api.math.Location;
import net.mcsmash.mintermc.api.math.Vector3;
import net.mcsmash.mintermc.auth.BedrockAuthSession;
import net.mcsmash.mintermc.auth.MicrosoftAuthService;
import net.mcsmash.mintermc.core.BotSession;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.netty.util.nethernet.OperatorIdentity;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import org.cloudburstmc.protocol.bedrock.data.ClientPlayMode;
import org.cloudburstmc.protocol.bedrock.data.InputInteractionModel;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Bedrock Edition implementation of a bot session.
 * <br>
 * Supports both transports used by Bedrock servers: RakNet (older and
 * third-party servers) and NetherNet (Minecraft 1.26 and later). The login
 * handshake and packet handling are shared; only the Netty transport and its
 * framing differ.
 */
public class BedrockEditionBotSession extends BotSession {
    private static final Logger logger = LoggerFactory.getLogger(BedrockEditionBotSession.class);

    /** Protocol 2225 (1.26.60) with the login serializer BDS expects. */
    private static final BedrockCodec CODEC = Bedrock_v2225.CODEC.toBuilder()
            .updateSerializer(LoginPacket.class, new MinterLoginSerializer())
            .build();

    /** One movement report per server tick. */
    private static final long AUTH_INPUT_INTERVAL_MS = 50;

    /** Horizontal walking speed per tick. A vanilla player walks at 0.21585. */
    private static final double WALK_SPEED_PER_TICK = 0.2;

    /** Distance at which a movement request counts as reached. */
    private static final double ARRIVE_DISTANCE = 0.5;

    private volatile BedrockClientSession session;
    private volatile NioEventLoopGroup eventLoopGroup;
    private volatile boolean loggedIn;
    private volatile boolean spawned;
    private volatile long runtimeEntityId;
    private volatile ScheduledFuture<?> authInputTask;
    private long authInputTick;
    private float lastX;
    private float lastY;
    private float lastZ;
    private volatile int dimension;
    private volatile MovementTarget movementTarget;
    private volatile boolean onGround;
    private volatile String xuid = "0";

    /** Upper bound for cached sub-chunks; each entry holds 4096 block ids. */
    private static final int MAX_CACHED_SECTIONS = 2048;
    /** Decoded sub-chunks, keyed by section. */
    private final Map<SectionKey, int[]> sections = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<SectionKey, int[]> eldest) {
                    return size() > MAX_CACHED_SECTIONS;
                }
            });
    /** Sub-chunk requests that are waiting for a server response. */
    private final Map<SectionKey, CompletableFuture<int[]>> pendingSections = new ConcurrentHashMap<>();
    /** Sections the server could not provide, and the time they may be retried. */
    private final Map<SectionKey, Long> sectionRetryAt = new ConcurrentHashMap<>();

    public BedrockEditionBotSession(String sessionToken) {
        super(sessionToken);
    }

    @Override
    protected void cleanup() {
        closeConnection();
    }

    @Override
    protected void handleNetworkDisconnect() {
        closeConnection();
    }

    @Override
    public CompletableFuture<Void> moveTo(Vector3 position) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (position == null || session == null || !isConnected()) {
            future.completeExceptionally(new IllegalStateException("The bot is not connected"));
            return future;
        }
        MovementTarget previous = movementTarget;
        if (previous != null) {
            previous.future().completeExceptionally(
                    new java.util.concurrent.CancellationException("superseded by a new target"));
        }
        Location current = getLocation().getPosition() == null
                ? new Location(new Vector3(0, 0, 0), 0, 0)
                : getLocation();
        double distance = Math.hypot(
                position.getX() - current.getPosition().getX(),
                position.getZ() - current.getPosition().getZ());
        long timeoutNanos = (long) ((distance / WALK_SPEED_PER_TICK) * AUTH_INPUT_INTERVAL_MS
                + 5_000) * 1_000_000L;
        movementTarget = new MovementTarget(position, future,
                System.nanoTime() + timeoutNanos);
        logger.debug("Bedrock bot {} moving to {}", getUsername(), position);
        return future;
    }

    @Override
    public Block getBlockAt(Vector3 position) {
        int blockX = (int) Math.floor(position.getX());
        int blockY = (int) Math.floor(position.getY());
        int blockZ = (int) Math.floor(position.getZ());
        if (blockY < -64 || blockY > 319) {
            // Outside the vanilla overworld height limits.
            return null;
        }
        Block loaded = getLoadedBlockAt(position);
        if (loaded != null) {
            return loaded;
        }
        SectionKey key = new SectionKey(blockX >> 4, blockY >> 4, blockZ >> 4);
        if (loadSection(key) == null) {
            return null;
        }
        return getLoadedBlockAt(position);
    }

    /**
     * {@inheritDoc}
     * <br>
     * Returns {@code null} for sections that have not been received yet.
     */
    @Override
    public Block getLoadedBlockAt(Vector3 position) {
        int blockX = (int) Math.floor(position.getX());
        int blockY = (int) Math.floor(position.getY());
        int blockZ = (int) Math.floor(position.getZ());
        if (blockY < -64 || blockY > 319) {
            return null;
        }
        SectionKey key = new SectionKey(blockX >> 4, blockY >> 4, blockZ >> 4);
        int[] section = sections.get(key);
        if (section == null) {
            return null;
        }
        // Blocks are stored in XZY order.
        int index = ((blockX & 15) << 8) | ((blockZ & 15) << 4) | (blockY & 15);
        String material = BedrockStaticDefinitions.blockName(section[index]);
        return new BedrockBlock(blockX, blockY, blockZ, material);
    }

    /**
     * Requests a sub-chunk and waits for the server response.
     *
     * @param key the section to load
     * @return the decoded block hashes, or {@code null} on timeout
     */
    private int[] loadSection(SectionKey key) {
        CompletableFuture<int[]> pending = new CompletableFuture<>();
        CompletableFuture<int[]> existing = pendingSections.putIfAbsent(key, pending);
        if (existing == null) {
            requestSubChunk(key.x(), key.y(), key.z());
        } else {
            pending = existing;
        }
        try {
            return pending.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pendingSections.remove(key, pending);
            return null;
        } catch (Exception e) {
            pendingSections.remove(key, pending);
            return null;
        }
    }

    /**
     * Stores a sub-chunk received from the server.
     *
     * @param chunkX   the chunk X coordinate
     * @param sectionY the sub-chunk index
     * @param chunkZ   the chunk Z coordinate
     * @param blocks   4096 block state hashes in XZY order
     */
    public void acceptSection(int chunkX, int sectionY, int chunkZ, int[] blocks) {
        SectionKey key = new SectionKey(chunkX, sectionY, chunkZ);
        sections.put(key, blocks);
        CompletableFuture<int[]> pending = pendingSections.remove(key);
        if (pending != null) {
            pending.complete(blocks);
        }
    }

    /**
     * Releases a sub-chunk request that the server could not answer.
     *
     * @param chunkX   the chunk X coordinate
     * @param sectionY the sub-chunk index
     * @param chunkZ   the chunk Z coordinate
     */
    public void rejectSection(int chunkX, int sectionY, int chunkZ) {
        SectionKey key = new SectionKey(chunkX, sectionY, chunkZ);
        sectionRetryAt.put(key, System.nanoTime() + TimeUnit.SECONDS.toNanos(5));
        CompletableFuture<int[]> pending = pendingSections.remove(key);
        if (pending != null) {
            pending.complete(null);
        }
    }

    /**
     * Requests a section in the background when it is not cached yet.
     * <br>
     * Used to keep the ground below the bot available without blocking the
     * network thread.
     *
     * @param key the section to load
     */
    public void prefetchSection(SectionKey key) {
        if (sections.containsKey(key)) {
            return;
        }
        Long retryAt = sectionRetryAt.get(key);
        if (retryAt != null && retryAt > System.nanoTime()) {
            return;
        }
        CompletableFuture<int[]> pending = new CompletableFuture<>();
        if (pendingSections.putIfAbsent(key, pending) == null) {
            requestSubChunk(key.x(), key.y(), key.z());
        }
    }

    /**
     * Prefetches the sections around the bot.
     * <br>
     * A 3x3 chunk area and four sections of height are kept available so that
     * plugins can query nearby blocks without waiting for the server.
     *
     * @param x the bot X coordinate
     * @param y the bot Y coordinate
     * @param z the bot Z coordinate
     */
    private void prefetchAround(float x, float y, float z) {
        int chunkX = (int) Math.floor(x) >> 4;
        int chunkZ = (int) Math.floor(z) >> 4;
        int sectionY = (int) Math.floor(y) >> 4;
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                for (int offsetY = -2; offsetY <= 1; offsetY++) {
                    int target = sectionY + offsetY;
                    if (target < -4 || target > 19) {
                        continue;
                    }
                    prefetchSection(new SectionKey(
                            chunkX + offsetX, target, chunkZ + offsetZ));
                }
            }
        }
    }

    /**
     * Sets the dimension reported by {@code StartGame}.
     *
     * @param dimension the Bedrock dimension id
     */
    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    /**
     * Records whether the server reports the bot as standing on a block.
     *
     * @param onGround the ground state from a movement correction
     */
    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }

    @Override
    public void chat(String message) {
        BedrockClientSession current = this.session;
        if (current == null || !current.isConnected()) {
            logger.warn("Cannot send chat from {}: the session is not connected", getUsername());
            return;
        }
        TextPacket packet = new TextPacket();
        packet.setType(TextPacket.Type.CHAT);
        packet.setSourceName(getUsername());
        packet.setMessage(message);
        packet.setNeedsTranslation(false);
        // BDS drops client chat that carries no XUID, so send the same value the
        // self-signed login token advertises for this bot.
        packet.setXuid(xuid);
        // Packet writes must happen on the channel's event loop.
        current.getPeer().getChannel().eventLoop().execute(
                () -> current.sendPacketImmediately(packet));
        logger.debug("Sent Bedrock chat as {}: {}", getUsername(), message);
    }

    @Override
    public boolean isConnected() {
        BedrockClientSession current = this.session;
        return current != null && current.isConnected() && this.loggedIn;
    }

    /**
     * Connects the bot to a Bedrock Edition server.
     * <br>
     * The method blocks until the selected transport is established. The
     * Bedrock login itself continues asynchronously on the Netty event loop.
     *
     * @param host        the target server host
     * @param port        the target server port
     * @param username    the player name to join with
     * @param onlineMode  whether to use Microsoft account authentication
     * @param options     the bot options such as the client version and transport
     * @param authService the Microsoft authentication service, or {@code null}
     */
    public void connect(String host, int port, String username, boolean onlineMode,
            BotOptions options, MicrosoftAuthService authService) {
        BedrockAuthSession auth = null;
        if (onlineMode) {
            if (authService == null) {
                throw new IllegalStateException("Microsoft authentication is not configured");
            }
            try {
                auth = authService.loginBedrock(options.account(), options.version());
                username = auth.displayName();
                logger.info("Authenticated as {} (xuid {})", auth.displayName(), auth.xuid());
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Microsoft sign-in failed: " + e.getMessage(), e);
            }
        }

        this.setUsername(username);
        this.setVersion(options.version());
        this.setEdition("Bedrock");

        String transport = options.transport() != null ? options.transport() : "raknet";
        if (transport.toLowerCase().startsWith("nethernet")) {
            connectNetherNet(host, port, username, options,
                    "nethernet-lan".equalsIgnoreCase(transport), auth);
        } else {
            connectRakNet(host, port, username, options, auth);
        }
    }

    private void connectRakNet(String host, int port, String username, BotOptions options,
            BedrockAuthSession auth) {
        NioEventLoopGroup group = new NioEventLoopGroup();
        this.eventLoopGroup = group;

        BedrockLoginHelper loginHelper = auth != null
                ? BedrockLoginHelper.forMicrosoft(null, auth, options.version(), host + ":" + port)
                : null;

        ChannelFuture future = new Bootstrap()
                .channelFactory(RakChannelFactory.client(NioDatagramChannel.class))
                .group(group)
                .handler(new BedrockClientInitializer() {
                    @Override
                    protected void initSession(BedrockClientSession bedrockSession) {
                        initializeSession(bedrockSession, username, options, host, port, false, null, loginHelper);
                    }
                })
                .option(RakChannelOption.RAK_PROTOCOL_VERSION, 11)
                .connect(new InetSocketAddress(host, port))
                .syncUninterruptibly();

        if (!future.isSuccess()) {
            Throwable cause = future.cause();
            logger.error("Failed to open RakNet connection to {}:{}", host, port, cause);
            group.shutdownGracefully();
            throw new IllegalStateException("Failed to connect to " + host + ":" + port, cause);
        }

        BedrockPeer peer = future.channel().pipeline().get(BedrockPeer.class);
        if (peer == null) {
            group.shutdownGracefully();
            throw new IllegalStateException("Bedrock peer was not initialized");
        }

        peer.setCodec(CODEC);
        sendNetworkSettingsRequest(peer);
        logger.info("Bedrock RakNet connection established to {}:{}, waiting for network settings", host, port);
    }

    private void connectNetherNet(String host, int port, String username, BotOptions options,
            boolean lanDiscovery, BedrockAuthSession auth) {
        NioEventLoopGroup group = new NioEventLoopGroup();
        this.eventLoopGroup = group;

        KeyPair keyPair;
        BedrockLoginHelper loginHelper;
        OperatorIdentity identity;
        if (auth != null) {
            keyPair = auth.keyPair();
            loginHelper = BedrockLoginHelper.forMicrosoft(
                    null, auth, options.version(), host + ":" + port);
            identity = OperatorIdentity.fromToken(
                    keyPair, auth.multiplayerToken(), auth.issuer());
        } else {
            keyPair = EncryptionUtils.createKeyPair();
            loginHelper = new BedrockLoginHelper(
                    null, username, options.version(), host + ":" + port,
                    resolveOfflineIdentity(username), keyPair);
            identity = OperatorIdentity.fromToken(
                    keyPair, loginHelper.identityToken(), "MinterMC");
        }

        BedrockLoginHelper finalLoginHelper = loginHelper;
        ChannelFuture future = BedrockNetherNetConnector.connect(group, host, port, identity, lanDiscovery,
                bedrockSession -> {
                    finalLoginHelper.setSession(bedrockSession);
                    initializeSession(bedrockSession, username, options, host, port, true, keyPair, finalLoginHelper);
                });

        if (!future.isSuccess()) {
            Throwable cause = future.cause();
            logger.error("Failed to open NetherNet connection to {}:{}", host, port, cause);
            group.shutdownGracefully();
            throw new IllegalStateException("Failed to connect to " + host + ":" + port, cause);
        }

        BedrockPeer peer = future.channel().pipeline().get(BedrockPeer.class);
        if (peer == null) {
            group.shutdownGracefully();
            throw new IllegalStateException("Bedrock peer was not initialized");
        }

        peer.setCodec(CODEC);
        sendNetworkSettingsRequest(peer);
        logger.info("Bedrock NetherNet connection established to {}:{}, waiting for network settings", host, port);
    }

    private void initializeSession(BedrockClientSession bedrockSession, String username, BotOptions options,
            String host, int port, boolean netherNet, KeyPair keyPair, BedrockLoginHelper loginHelper) {
        this.session = bedrockSession;
        bedrockSession.setCodec(CODEC);
        BedrockStaticDefinitions.install(bedrockSession.getPeer().getCodecHelper());
        if (loginHelper == null) {
            loginHelper = new BedrockLoginHelper(
                    bedrockSession,
                    username,
                    options.version(),
                    host + ":" + port,
                    resolveOfflineIdentity(username),
                    keyPair);
        } else {
            loginHelper.setSession(bedrockSession);
        }
        this.xuid = loginHelper.xuid();
        bedrockSession.setPacketHandler(new BedrockBotPacketHandler(
                this, bedrockSession, loginHelper, netherNet));
    }

    private static void sendNetworkSettingsRequest(BedrockPeer peer) {
        RequestNetworkSettingsPacket request = new RequestNetworkSettingsPacket();
        request.setProtocolVersion(CODEC.getProtocolVersion());
        peer.sendPacketImmediately(0, 0, request);
    }

    private void closeConnection() {
        MovementTarget target = this.movementTarget;
        this.movementTarget = null;
        if (target != null) {
            target.future().completeExceptionally(
                    new IllegalStateException("The session was closed"));
        }
        ScheduledFuture<?> task = this.authInputTask;
        if (task != null) {
            task.cancel(false);
            this.authInputTask = null;
        }
        BedrockClientSession current = this.session;
        if (current != null && current.isConnected()) {
            current.disconnect("MinterMC session closed");
        }
        NioEventLoopGroup group = this.eventLoopGroup;
        if (group != null) {
            group.shutdownGracefully();
            this.eventLoopGroup = null;
        }
    }

    /**
     * Starts the server-authoritative movement loop.
     * <br>
     * BDS uses server authoritative movement, so the client must report its
     * position once per tick with {@code PlayerAuthInput}. Servers also use these
     * reports to confirm that a player has actually entered the world, which
     * gates chat and player-list visibility.
     */
    public void startAuthInputLoop() {
        if (authInputTask != null) {
            return;
        }
        BedrockClientSession current = this.session;
        if (current == null) {
            return;
        }
        authInputTask = current.getPeer().getChannel().eventLoop().scheduleAtFixedRate(
                this::sendAuthInput, 0, AUTH_INPUT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        logger.debug("Started Bedrock movement reports for {}", getUsername());
    }

    private void sendAuthInput() {
        BedrockClientSession current = this.session;
        if (current == null || !current.isConnected() || !loggedIn) {
            return;
        }
        Location location = getLocation();
        Vector3 position = location.getPosition();
        float x = (float) position.getX();
        float y = (float) position.getY();
        float z = (float) position.getZ();
        float pitch = (float) location.getPitch();
        float yaw = (float) location.getYaw();
        Vector2f motion = Vector2f.ZERO;

        MovementTarget target = movementTarget;
        if (target != null) {
            double dx = target.position().getX() - x;
            double dz = target.position().getZ() - z;
            double distance = Math.hypot(dx, dz);
            if (distance <= ARRIVE_DISTANCE) {
                movementTarget = null;
                target.future().complete(null);
                logger.debug("Bedrock bot {} reached {}", getUsername(), target.position());
            } else if (System.nanoTime() >= target.deadlineNanos()) {
                movementTarget = null;
                target.future().completeExceptionally(
                        new java.util.concurrent.TimeoutException("movement timed out"));
                logger.debug("Bedrock bot {} timed out moving to {}",
                        getUsername(), target.position());
            } else {
                double step = Math.min(WALK_SPEED_PER_TICK, distance);
                double dirX = dx / distance;
                double dirZ = dz / distance;
                x += (float) (dirX * step);
                z += (float) (dirZ * step);
                // Bedrock yaw 0 faces +Z; the camera forward vector is
                // (-sin(yaw), *, cos(yaw)).
                yaw = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
                // The move vector is a local input: X is strafe, Y is forward.
                motion = Vector2f.from(0f, 1f);
            }
        }

        prefetchAround(x, y, z);
        setLocation(new Location(new Vector3(x, y, z), yaw, pitch));

        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);

        PlayerAuthInputPacket packet = new PlayerAuthInputPacket();
        packet.setRotation(Vector3f.from(pitch, yaw, yaw));
        packet.setPosition(Vector3f.from(x, y, z));
        packet.setMotion(motion);
        packet.getInputData().add(PlayerAuthInputData.BLOCK_BREAKING_DELAY_ENABLED);
        if (onGround) {
            packet.getInputData().add(PlayerAuthInputData.VERTICAL_COLLISION);
        }
        if (target != null && movementTarget != null) {
            packet.getInputData().add(PlayerAuthInputData.UP);
        }
        packet.setInputMode(InputMode.MOUSE);
        packet.setPlayMode(ClientPlayMode.NORMAL);
        packet.setInputInteractionModel(InputInteractionModel.CROSSHAIR);
        packet.setInteractRotation(Vector2f.from(pitch, yaw));
        packet.setTick(authInputTick++);
        packet.setDelta(Vector3f.from(x - lastX, y - lastY, z - lastZ));
        packet.setAnalogMoveVector(Vector2f.ZERO);
        packet.setCameraOrientation(Vector3f.from(
                (float) (-Math.sin(yawRadians) * Math.cos(pitchRadians)),
                (float) (-Math.sin(pitchRadians)),
                (float) (Math.cos(yawRadians) * Math.cos(pitchRadians))));
        packet.setRawMoveVector(Vector2f.ZERO);
        current.sendPacketImmediately(packet);

        lastX = x;
        lastY = y;
        lastZ = z;
    }

    /**
     * Requests one sub-chunk from the server.
     * <br>
     * Since 1.18.30 the level chunk packet only carries biome and height data;
     * block storage is fetched on demand with {@code SubChunkRequest}.
     *
     * @param chunkX   the chunk X coordinate
     * @param sectionY the sub-chunk index, where 0 covers Y 0-15
     * @param chunkZ   the chunk Z coordinate
     */
    public void requestSubChunk(int chunkX, int sectionY, int chunkZ) {
        BedrockClientSession current = this.session;
        if (current == null || !current.isConnected()) {
            return;
        }
        org.cloudburstmc.protocol.bedrock.packet.SubChunkRequestPacket request =
                new org.cloudburstmc.protocol.bedrock.packet.SubChunkRequestPacket();
        request.setDimension(dimension);
        request.setSubChunkPosition(Vector3i.from(chunkX, sectionY, chunkZ));
        request.setPositionOffsets(List.of(Vector3i.ZERO));
        current.sendPacketImmediately(request);
    }

    private static UUID resolveOfflineIdentity(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A pending straight-line movement request.
     *
     * @param position      the destination
     * @param future        completed when the bot arrives or the request fails
     * @param deadlineNanos the {@link System#nanoTime()} deadline
     */
    private record MovementTarget(Vector3 position, CompletableFuture<Void> future,
            long deadlineNanos) {
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public boolean isSpawned() {
        return spawned;
    }

    public long getRuntimeEntityId() {
        return runtimeEntityId;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    public void setSpawned(boolean spawned) {
        this.spawned = spawned;
    }

    public void setRuntimeEntityId(long runtimeEntityId) {
        this.runtimeEntityId = runtimeEntityId;
    }
}
