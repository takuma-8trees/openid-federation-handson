package local.oidf;

import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.*;
import com.nimbusds.openid.connect.sdk.federation.entities.EntityStatement;
import com.nimbusds.openid.connect.sdk.federation.trust.TrustChain;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** Deliberately restricted direct-chain teaching profile, not a general resolver. */
final class Verification {
    static void verify(String target, SignedJWT leaf, SignedJWT subordinate, SignedJWT root,
                       JWKSet pin, Instant now, Consumer<String> report) throws Exception {
        check(leaf, target, target, now, true);
        require(List.of(Statements.ANCHOR).equals(leaf.getJWTClaimsSet().getStringListClaim("authority_hints")), "Unexpected authority_hints");
        var leafKeys = JWKSet.parse(leaf.getJWTClaimsSet().getJSONObjectClaim("jwks"));
        EntityStatement.parse(leaf).verifySignature(leafKeys);
        report.accept("PASS: Entity Configurationの自己署名・iss/sub・有効期限（所属の証明ではない）");
        check(root, Statements.ANCHOR, Statements.ANCHOR, now, true);
        require(root.getJWTClaimsSet().getClaim("authority_hints") == null, "Anchor must terminate this direct chain");
        EntityStatement.parse(root).verifySignature(pin);
        report.accept("PASS: AnchorのEntity ConfigurationをAに事前設定した公開鍵で検証");
        require(subordinate != null, "MISSING_SUBORDINATE: Anchorが対象について発行したStatementがない");
        check(subordinate, Statements.ANCHOR, target, now, false);
        var chain = new TrustChain(EntityStatement.parse(leaf), List.of(EntityStatement.parse(subordinate)), EntityStatement.parse(root));
        chain.verifySignatures(pin);
        report.accept("PASS: Anchor署名のSubordinate Statementと、そのjwksによる対象の署名検証");
        report.accept("TRUSTED: " + target + " -> " + Statements.ANCHOR);
    }
    private static void check(SignedJWT jwt, String issuer, String subject, Instant now, boolean configuration) throws Exception {
        require(JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm()), "This profile requires RS256");
        require(new JOSEObjectType("entity-statement+jwt").equals(jwt.getHeader().getType()), "Invalid typ");
        require(jwt.getHeader().getCriticalParams() == null || jwt.getHeader().getCriticalParams().isEmpty(), "Unsupported critical headers");
        var c = jwt.getJWTClaimsSet();
        require(issuer.equals(c.getIssuer()) && subject.equals(c.getSubject()), "iss/sub mismatch");
        require(c.getIssueTime() != null && c.getExpirationTime() != null, "iat/exp required");
        require(!c.getIssueTime().toInstant().isAfter(now) && c.getExpirationTime().toInstant().isAfter(now)
            && c.getExpirationTime().after(c.getIssueTime()), "Expired or invalid iat/exp");
        var allowed = configuration ? Set.of("iss", "sub", "iat", "exp", "jwks", "metadata", "authority_hints")
                                    : Set.of("iss", "sub", "iat", "exp", "jwks");
        require(allowed.containsAll(c.getClaims().keySet()), "Unsupported claims in this teaching profile");
        var keys = JWKSet.parse(c.getJSONObjectClaim("jwks"));
        require(!keys.getKeys().isEmpty() && keys.getKeys().stream().noneMatch(JWK::isPrivate), "Public jwks required");
        if (configuration) {
            var metadata = c.getJSONObjectClaim("metadata");
            require(metadata != null && metadata.keySet().equals(Set.of("federation_entity")), "Only federation_entity metadata is supported");
        }
    }
    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
}
