package local.oidf;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.*;
import java.time.Instant;
import java.util.*;

final class Statements {
    static final String ANCHOR = "https://trust-anchor:8443";
    static String id(String host) { return "https://" + host + ":8443"; }
    static Map<String,Object> publicKeys(RSAKey key) { return new JWKSet(key.toPublicJWK()).toJSONObject(); }
    static SignedJWT issue(String issuer, String subject, Map<String,Object> keys,
                           RSAKey signer, Map<String,Object> extra, Instant now) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject(subject)
            .issueTime(Date.from(now.minusSeconds(5))).expirationTime(Date.from(now.plusSeconds(3600)))
            .claim("jwks", keys);
        extra.forEach(claims::claim);
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
            .type(new JOSEObjectType("entity-statement+jwt")).keyID(signer.getKeyID()).build(), claims.build());
        jwt.sign(new RSASSASigner(signer));
        return jwt;
    }
    static SignedJWT configuration(String host, RSAKey key) throws Exception {
        String entity = id(host);
        var metadata = new LinkedHashMap<String,Object>();
        metadata.put(
            "organization_name",
            switch (host) {
                case "trust-anchor" -> "Trust Anchor";
                case "trustee" -> "Trustee";
                default -> "Entity";
            }
        );
        if (entity.equals(ANCHOR)) metadata.put("federation_fetch_endpoint", ANCHOR + "/fetch");
        var extra = new LinkedHashMap<String,Object>();
        extra.put("metadata", Map.of("federation_entity", metadata));
        if (!entity.equals(ANCHOR)) extra.put("authority_hints", List.of(ANCHOR));
        return issue(entity, entity, publicKeys(key), key, extra, Instant.now());
    }
}
