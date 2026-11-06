package net.mcsmash.mintermc.api.bot;

/**
 * Options applied when a bot connects to a server.
 *
 * @param viewDistance       the requested view distance, or {@code null} for the server default
 * @param simulationDistance the requested simulation distance, or {@code null} for the server default
 * @param version            the Minecraft client version
 * @param transport          the Bedrock transport ({@code raknet} or {@code nethernet}); ignored by Java Edition
 */
public record BotOptions(
        Integer viewDistance,
        Integer simulationDistance,
        String version,
        String transport) {

    public static class Builder {
        private Integer viewDistance;
        private Integer simulationDistance;
        private String version;
        private String transport;

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

        public BotOptions build() {
            return new BotOptions(viewDistance, simulationDistance, version, transport);
        }
    }
}
