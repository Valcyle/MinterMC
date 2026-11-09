package net.mcsmash.mintermc.adapter.bedrock.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.BatchCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Compression codec that follows the vanilla client's tolerance rules.
 * <br>
 * Bedrock servers may send a batch uncompressed when it is below the
 * negotiated threshold, and some implementations add the algorithm header even
 * when the negotiated framing does not. Cloudburst's stock codec assumes both
 * are always present. This subclass tries the negotiated framing first, then a
 * prefixed frame, and finally treats the batch as uncompressed.
 */
public class TolerantCompressionCodec extends CompressionCodec {

    private static final Logger logger = LoggerFactory.getLogger(TolerantCompressionCodec.class);

    private final CompressionStrategy strategy;
    private final boolean prefixed;

    public TolerantCompressionCodec(CompressionStrategy strategy, boolean prefixed) {
        super(strategy, prefixed);
        this.strategy = strategy;
        this.prefixed = prefixed;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, BedrockBatchWrapper msg, List<Object> out) throws Exception {
        ByteBuf compressed = msg.getCompressed();
        if (compressed == null) {
            super.decode(ctx, msg, out);
            return;
        }

        try {
            super.decode(ctx, msg, out);
            return;
        } catch (Exception negotiatedFailure) {
            // Some servers use the other framing even for the same protocol
            // version. Try it before assuming the batch is uncompressed.
            try {
                ByteBuf slice = compressed.slice();
                BatchCompression compression;
                if (prefixed) {
                    compression = strategy.getDefaultCompression();
                } else {
                    if (!slice.isReadable()) {
                        throw negotiatedFailure;
                    }
                    compression = strategy.getCompression(getCompressionAlgorithm(slice.readByte()));
                }
                ByteBuf decoded = compression.decode(ctx, slice);
                msg.setAlgorithm(compression.getAlgorithm());
                msg.setUncompressed(decoded);
                out.add(msg.retain());
                return;
            } catch (Exception alternateFailure) {
                // Fall through to the uncompressed case.
            }

            // Servers skip compression for batches below the threshold.
            logger.debug("Treating Bedrock batch as uncompressed after {} bytes failed to decompress",
                    compressed.readableBytes());
            ByteBuf raw = compressed.retainedSlice();
            if (prefixed && raw.isReadable()) {
                raw.readByte();
            }
            msg.setUncompressed(raw);
            out.add(msg.retain());
        }
    }
}
