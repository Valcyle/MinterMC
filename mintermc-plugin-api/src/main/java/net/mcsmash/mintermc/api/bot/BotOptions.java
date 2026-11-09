package net.mcsmash.mintermc.api.bot;

/**
 * Options applied when a bot connects to a server.
 *
 * @param viewDistance       the requested view distance, or {@code null} for the server default
 * @param simulationDistance the requested simulation distance, or {@code null} for the server default
 * @param version            the Minecraft client version
 * @param transport          the Bedrock transport ({@code raknet} or {@code nethernet}); ignored by Java Edition
 * @param authMode           how the bot proves its identity
 * @param account            the local account name used for the Microsoft token cache
 */
public record BotOptions(
        Integer viewDistance,
        Integer simulationDistance,
        String version,
        String transport,
        AuthMode authMode,
        String account) {

    public static class Builder {
        private Integer viewDistance;
        private Integer simulationDistance;
        private String version;
        private String transport;
        private AuthMode authMode = AuthMode.OFFLINE;
        private String account = "default";

        public Builder viewDistance(Integer viewDistance) {
            this.viewDistance = viewDistance;
            return this;
        }

        public Builder simulationDistance(Integer simulationDistance) {
            this.simulationDistance = simulationDistance;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * Selects the Bedrock transport.
         *
         * @param transport {@code raknet} or {@code nethernet}
         * @return this builder
         */
        public Builder transport(String transport) {
            this.transport = transport;
            return this;
        }

        /**
         * Selects how the bot proves its identity.
         *
         * @param authMode {@link AuthMode#OFFLINE} or {@link AuthMode#MICROSOFT}
         * @return this builder
         */
        public Builder authMode(AuthMode authMode) {
            this.authMode = authMode != null ? authMode : AuthMode.OFFLINE;
            return this;
        }

        /**
         * Sets the local account name used for the Microsoft token cache.
         * <br>
         * The same name restores the saved refresh token on later logins.
         *
         * @param account a stable local name such as {@code main}
         * @return this builder
         */
        public Builder account(String account) {
            this.account = account != null && !account.isBlank() ? account : "default";
            return this;
        }

        public BotOptions build() {
            return new BotOptions(viewDistance, simulationDistance, version, transport,
                    authMode, account);
        }
    }
}
