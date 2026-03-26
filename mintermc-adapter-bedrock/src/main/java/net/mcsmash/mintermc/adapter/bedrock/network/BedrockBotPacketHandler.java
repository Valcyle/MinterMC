package net.mcsmash.mintermc.adapter.bedrock.network;

import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BedrockBotPacketHandler implements BedrockPacketHandler {
    private Logger logger = LoggerFactory.getLogger(BedrockBotPacketHandler.class);
    private BedrockClientSession session;

    public BedrockBotPacketHandler(BedrockClientSession session) {
        this.session = session;
    }
}
