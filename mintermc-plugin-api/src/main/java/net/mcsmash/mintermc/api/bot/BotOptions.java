package net.mcsmash.mintermc.api.bot;

public record BotOptions(
        Integer viewDistance,
        Integer simulationDistance,
        String version) {

    public static class Builder {
        private Integer viewDistance;
        private Integer simulationDistance;
        private String version;

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

        public BotOptions build() {
            return new BotOptions(viewDistance, simulationDistance, version);
        }
    }
}
