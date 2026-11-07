package net.mcsmash.mintermc.adapter.bedrock.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HexFormat;
import java.util.List;

/**
 * Adapts whole NetherNet messages to Bedrock batch wrappers.
 * <br>
 * NetherNet already delivers one complete Bedrock batch per message, so unlike
 * RakNet there is no frame ID byte to read or write. The handler only has to
 * move the message payload in and out of {@link BedrockBatchWrapper}.
 */
public class NetherNetBatchCodec extends MessageToMessageCodec<ByteBuf, BedrockBatchWrapper> {

    public static final String NAME = "nethernet-batch-codec";

    private static final Logger logger = LoggerFactory.getLogger(NetherNetBatchCodec.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, BedrockBatchWrapper msg, List<Object> out) {
        if (msg.getCompressed() == null) {
            throw new IllegalStateException("Bedrock batch was not compressed");
        }
        ByteBuf compressed = msg.getCompressed();
        byte[] head = new byte[Math.min(20, compressed.readableBytes())];
        compressed.getBytes(compressed.readerIndex(), head);
        logger.debug("NetherNet outbound batch {} bytes head {}",
                compressed.readableBytes(), HexFormat.of().formatHex(head));
        out.add(compressed.retainedSlice());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
        if (!msg.isReadable()) {
            return;
        }
        out.add(BedrockBatchWrapper.newInstance(msg.readRetainedSlice(msg.readableBytes()), null));
    }
}
