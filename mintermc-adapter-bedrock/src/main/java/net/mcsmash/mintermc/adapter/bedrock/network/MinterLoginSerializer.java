package net.mcsmash.mintermc.adapter.bedrock.network;

import org.cloudburstmc.protocol.bedrock.codec.v818.serializer.LoginSerializer_v818;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthPayload;
import org.cloudburstmc.protocol.bedrock.data.auth.TokenPayload;
import org.jose4j.json.JsonUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Login serializer that keeps both login identity fields populated.
 * <br>
 * Cloudburst's token path writes an empty {@code Certificate} string, but the
 * Bedrock server parses that field as a JSON object. It expects the shape used
 * by the retail client and by bedrock-protocol for offline OIDC logins:
 * {@code {"chain":[""]}} together with a {@code Token}.
 */
public class MinterLoginSerializer extends LoginSerializer_v818 {

    @Override
    protected String writeAuthJwt(AuthPayload payload) {
        if (payload instanceof TokenPayload tokenPayload) {
            Map<String, Object> certificate = new LinkedHashMap<>();
            certificate.put("chain", List.of(""));

            Map<String, Object> auth = new LinkedHashMap<>();
            auth.put("Certificate", JsonUtil.toJson(certificate));
            auth.put("AuthenticationType", payload.getAuthType().ordinal() - 1);
            auth.put("Token", tokenPayload.getToken());
            return JsonUtil.toJson(auth);
        }
        return super.writeAuthJwt(payload);
    }
}
