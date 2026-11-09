package net.mcsmash.mintermc.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.bedrock.model.MinecraftCertificateChain;
import net.raphimc.minecraftauth.bedrock.model.MinecraftMultiplayerToken;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.java.model.MinecraftProfile;
import net.raphimc.minecraftauth.java.model.MinecraftToken;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Signs bots in with a Microsoft account and caches the refresh tokens.
 * <br>
 * The first sign-in uses the Microsoft device code flow: the URL and code are
 * printed to the kernel log and the caller blocks until the user completes the
 * sign-in in a browser. Later logins restore the cached session and refresh it
 * silently.
 * <br>
 * Cache files contain refresh tokens. They are written under the configured
 * account directory (not the repository) with owner-only permissions where the
 * file system supports them, and their contents are never logged.
 */
public final class MicrosoftAuthService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(MicrosoftAuthService.class);
    private static final String USER_AGENT = "MinterMC/1.0";

    private final Path cacheDirectory;
    private final HttpClient httpClient;
    /** Serializes sign-ins per account so two bots cannot race the same token cache. */
    private final Map<String, Object> accountLocks = new ConcurrentHashMap<>();

    /**
     * @param cacheDirectory directory for the per-account token caches
     */
    public MicrosoftAuthService(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
        this.httpClient = MinecraftAuth.createHttpClient(USER_AGENT);
        try {
            Files.createDirectories(cacheDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the account cache directory", e);
        }
    }

    /**
     * Returns the default cache directory.
     * <br>
     * {@code MINTER_AUTH_DIR} overrides the location. The default is
     * {@code ~/.mintermc/accounts}.
     *
     * @return the account cache directory
     */
    public static Path defaultCacheDirectory() {
        String configured = System.getenv("MINTER_AUTH_DIR");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return Path.of(System.getProperty("user.home"), ".mintermc", "accounts");
    }

    /**
     * Signs in to a Java Edition account.
     *
     * @param account local account name used for the token cache
     * @return the Java profile and access token
     * @throws IOException          if the sign-in or token refresh fails
     * @throws InterruptedException if the device code wait is interrupted
     * @throws TimeoutException     if the device code expires
     */
    public JavaAuthSession loginJava(String account)
            throws IOException, InterruptedException, TimeoutException {
        String key = accountKey(account);
        synchronized (accountLocks.computeIfAbsent(key + ".java", ignored -> new Object())) {
            Path file = cacheDirectory.resolve(key + ".java.json");
            JavaAuthManager manager = loadJava(file);
            if (manager == null) {
                java.util.function.Consumer<MsaDeviceCode> callback =
                        code -> announceDeviceCode(account, code);
                manager = JavaAuthManager.create(httpClient).login(
                        DeviceCodeMsaAuthService::new, callback);
                registerJavaSave(file, manager);
            }

            MinecraftProfile profile = manager.getMinecraftProfile().getUpToDate();
            MinecraftToken token = manager.getMinecraftToken().getUpToDate();
            return new JavaAuthSession(profile.getId(), profile.getName(), token.getToken());
        }
    }

    /**
     * Signs in to a Bedrock Edition account.
     *
     * @param account     local account name used for the token cache
     * @param gameVersion the Bedrock client version, for example {@code 1.26.60}
     * @return the Bedrock identity, login chain and NetherNet token
     * @throws IOException          if the sign-in or token refresh fails
     * @throws InterruptedException if the device code wait is interrupted
     * @throws TimeoutException     if the device code expires
     */
    public BedrockAuthSession loginBedrock(String account, String gameVersion)
            throws IOException, InterruptedException, TimeoutException {
        String key = accountKey(account);
        synchronized (accountLocks.computeIfAbsent(key + ".bedrock", ignored -> new Object())) {
            Path file = cacheDirectory.resolve(key + ".bedrock.json");
            BedrockAuthManager manager = loadBedrock(file, gameVersion);
            if (manager == null) {
                java.util.function.Consumer<MsaDeviceCode> callback =
                        code -> announceDeviceCode(account, code);
                manager = BedrockAuthManager.create(httpClient, gameVersion).login(
                        DeviceCodeMsaAuthService::new, callback);
                registerBedrockSave(file, manager);
            }

            MinecraftCertificateChain chain =
                    manager.getMinecraftCertificateChain().getUpToDate();
            MinecraftMultiplayerToken multiplayer =
                    manager.getMinecraftMultiplayerToken().getUpToDate();
            String issuer = multiplayer.getParsedToken().getPayload().reqString("iss");
            String playFabId = multiplayer.getParsedToken().getPayload().reqString("mid");

            return new BedrockAuthSession(
                    chain.getIdentityDisplayName(),
                    chain.getIdentityXuid(),
                    chain.getIdentityUuid(),
                    manager.getSessionKeyPair(),
                    chain.getMojangJwt(),
                    chain.getIdentityJwt(),
                    multiplayer.getToken(),
                    issuer,
                    playFabId);
        }
    }

    @Override
    public void close() {
        if (httpClient instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                logger.debug("Could not close the authentication HTTP client", e);
            }
        }
    }

    private JavaAuthManager loadJava(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JavaAuthManager manager = JavaAuthManager.fromJson(httpClient, json);
            manager.getMinecraftToken().refreshIfExpired();
            return manager;
        } catch (Exception e) {
            logger.warn("Could not restore the Java account cache {}; signing in again ({})",
                    file.getFileName(), e.getMessage());
            return null;
        }
    }

    private BedrockAuthManager loadBedrock(Path file, String gameVersion) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            BedrockAuthManager manager = BedrockAuthManager.fromJson(httpClient, gameVersion, json);
            manager.getMinecraftMultiplayerToken().refreshIfExpired();
            return manager;
        } catch (Exception e) {
            logger.warn("Could not restore the Bedrock account cache {}; signing in again ({})",
                    file.getFileName(), e.getMessage());
            return null;
        }
    }

    private void registerJavaSave(Path file, JavaAuthManager manager) {
        save(file, JavaAuthManager.toJson(manager));
        manager.getChangeListeners().add(() -> save(file, JavaAuthManager.toJson(manager)));
    }

    private void registerBedrockSave(Path file, BedrockAuthManager manager) {
        save(file, BedrockAuthManager.toJson(manager));
        manager.getChangeListeners().add(() -> save(file, BedrockAuthManager.toJson(manager)));
    }

    private void save(Path file, JsonObject json) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, json.toString(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            restrictPermissions(file);
        } catch (IOException e) {
            logger.warn("Could not save the account cache {}", file.getFileName(), e);
        }
    }

    private static void restrictPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and some file systems do not expose POSIX permissions.
        }
    }

    private static void announceDeviceCode(String account, MsaDeviceCode code) {
        logger.info("Microsoft sign-in required for account '{}'", account);
        logger.info("Open {} and enter the code {}",
                code.getVerificationUri(), code.getUserCode());
        logger.info("Or open {}", code.getDirectVerificationUri());
    }

    private static String accountKey(String account) {
        String normalized = account == null ? "default" : account.trim();
        if (normalized.isEmpty()) {
            normalized = "default";
        }
        return normalized.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
