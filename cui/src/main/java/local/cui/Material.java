package local.cui;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.nio.file.*;
import java.util.*;

/** Fixture provisioning only. No actor can read another actor's private key. */
final class Material {
    static final String PASSWORD = "local-demo";
    static RSAKey key(Path file) throws Exception { return RSAKey.parse(Files.readString(file)); }
    static void initialize() throws Exception {
        Path base = Path.of("/material"), a = base.resolve("a");
        if (Files.exists(a.resolve("ready"))) {
            for (String host : List.of("entity-b", "entity-c", "trust-anchor")) {
                if (!Files.exists(base.resolve(host).resolve("signing.jwk")) || !Files.exists(base.resolve(host).resolve("tls.p12")))
                    throw new IllegalStateException("Incomplete fixture; restore all CUI volumes together");
            }
            publicPem(a);
            System.out.println("Existing CUI fixture retained"); return;
        }
        Files.createDirectories(a);
        for (String host : List.of("entity-b", "entity-c", "trust-anchor")) {
            Path dir = base.resolve(host); Files.createDirectories(dir);
            if (Files.exists(dir.resolve("signing.jwk"))) throw new IllegalStateException("Partial initialization; reset only CUI volumes using README");
            RSAKey key = new RSAKeyGenerator(2048).keyID(host + "-federation").generate();
            Files.writeString(dir.resolve("signing.jwk"), key.toJSONString());
            keytool("-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=" + host,
                "-ext", "SAN=dns:" + host, "-validity", "365", "-storetype", "PKCS12", "-keystore", dir.resolve("tls.p12").toString());
            Path cert = a.resolve(host + ".cer");
            keytool("-exportcert", "-alias", "server", "-keystore", dir.resolve("tls.p12").toString(), "-file", cert.toString());
            keytool("-importcert", "-noprompt", "-alias", host, "-keystore", a.resolve("tls-trust.p12").toString(), "-file", cert.toString());
        }
        RSAKey anchor = key(base.resolve("trust-anchor/signing.jwk"));
        Files.writeString(a.resolve("anchor-jwks.json"), new JWKSet(anchor.toPublicJWK()).toString());
        Files.writeString(base.resolve("trust-anchor/registered-b.json"), new JWKSet(key(base.resolve("entity-b/signing.jwk")).toPublicJWK()).toString());
        Files.writeString(a.resolve("ready"), "CUI fixture v1\n");
        publicPem(a);
        System.out.println("Provisioned: A trusts Anchor; Anchor registers B only. C is unregistered.");
    }
    private static void publicPem(Path a) throws Exception {
        for (String host : List.of("entity-b", "entity-c", "trust-anchor")) {
            String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(Files.readAllBytes(a.resolve(host + ".cer")));
            Files.writeString(a.resolve(host + ".pem"), "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----\n");
        }
    }
    private static void keytool(String... args) throws Exception {
        var command = new ArrayList<>(List.of("keytool")); command.addAll(List.of(args));
        command.addAll(List.of("-storepass", PASSWORD));
        if (new ProcessBuilder(command).inheritIO().start().waitFor() != 0) throw new IllegalStateException("TLS fixture setup failed");
    }
}
