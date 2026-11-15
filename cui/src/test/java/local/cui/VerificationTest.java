package local.cui;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VerificationTest {
    final RSAKey anchor = new RSAKeyGenerator(2048).keyID("anchor").generate();
    final RSAKey b = new RSAKeyGenerator(2048).keyID("b").generate();
    final RSAKey c = new RSAKeyGenerator(2048).keyID("c").generate();
    final Instant now = Instant.now();
    final SignedJWT leaf = Statements.configuration("entity-b", b);
    final SignedJWT root = Statements.configuration("trust-anchor", anchor);
    final SignedJWT sub = Statements.issue(Statements.ANCHOR, Statements.id("entity-b"), Statements.publicKeys(b), anchor, Map.of(), now);
    VerificationTest() throws Exception {}
    void verify(SignedJWT l, SignedJWT s, JWKSet pin, Instant time) throws Exception {
        Verification.verify(Statements.id("entity-b"), l, s, root, pin, time, line -> {});
    }
    @Test void registeredB() throws Exception { verify(leaf, sub, new JWKSet(anchor.toPublicJWK()), now); }
    @Test void unregisteredCStillHasValidSelfSignature() throws Exception {
        var self = Statements.configuration("entity-c", c);
        var messages = new ArrayList<String>();
        var error = assertThrows(Exception.class, () -> Verification.verify(Statements.id("entity-c"), self, null, root, new JWKSet(anchor.toPublicJWK()), now, messages::add));
        assertTrue(error.getMessage().contains("MISSING_SUBORDINATE")); assertEquals(2, messages.size());
    }
    @Test void tamperedPayload() throws Exception {
        var claims = new LinkedHashMap<>(leaf.getJWTClaimsSet().toJSONObject());
        claims.put("metadata", Map.of("federation_entity", Map.of("organization_name", "Changed")));
        var changed = SignedJWT.parse(leaf.getHeader().toBase64URL() + "." + Base64URL.encode(JWTClaimsSet.parse(claims).toString()) + "." + leaf.getSignature());
        assertThrows(Exception.class, () -> verify(changed, sub, new JWKSet(anchor.toPublicJWK()), now));
    }
    @Test void wrongPin() { assertThrows(Exception.class, () -> verify(leaf, sub, new JWKSet(c.toPublicJWK()), now)); }
    @Test void expired() { assertThrows(Exception.class, () -> verify(leaf, sub, new JWKSet(anchor.toPublicJWK()), now.plusSeconds(3700))); }
    @Test void validSelfSignatureWithUnendorsedKey() throws Exception {
        var replacement = Statements.configuration("entity-b", c);
        assertThrows(Exception.class, () -> verify(replacement, sub, new JWKSet(anchor.toPublicJWK()), now));
    }
    @Test void unsupportedPolicyMustNotBeSilentlyIgnored() throws Exception {
        var policy = Statements.issue(Statements.ANCHOR, Statements.id("entity-b"), Statements.publicKeys(b), anchor, Map.of("metadata_policy", Map.of()), now);
        assertThrows(Exception.class, () -> verify(leaf, policy, new JWKSet(anchor.toPublicJWK()), now));
    }
}
