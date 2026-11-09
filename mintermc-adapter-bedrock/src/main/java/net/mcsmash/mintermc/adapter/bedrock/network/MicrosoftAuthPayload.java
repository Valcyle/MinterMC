package net.mcsmash.mintermc.adapter.bedrock.network;

import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;

import java.util.List;

/**
 * Full-auth payload that carries both the certificate chain and the OIDC
 * multiplayer token expected by current Bedrock servers.
 */
public final class MicrosoftAuthPayload extends CertificateChainPayload {

    private final String token;

    public MicrosoftAuthPayload(List<String> chain, String token) {
        super(chain, AuthType.FULL);
        this.token = token;
    }

    public String token() {
        return token;
    }
}
