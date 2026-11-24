package local.oidf;

import com.sun.net.httpserver.*;
import com.nimbusds.jose.jwk.JWKSet;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.Instant;
import java.util.*;
import javax.net.ssl.*;

final class Publisher {
    static void start(String host) throws Exception {
        if (!Set.of("trustee", "trust-anchor").contains(host)) throw new IllegalArgumentException("Unknown publisher");
        var key = Material.key(Path.of("/data/signing.jwk"));
        var store = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(Path.of("/data/tls.p12"))) { store.load(in, Material.PASSWORD.toCharArray()); }
        var km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); km.init(store, Material.PASSWORD.toCharArray());
        var ssl = SSLContext.getInstance("TLS"); ssl.init(km.getKeyManagers(), null, null);
        var server = HttpsServer.create(new InetSocketAddress(8443), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(ssl));
        HttpHandler handler = exchange -> {
            int status = 500; String body; String type = "application/json";
            try {
                String path = exchange.getRequestURI().getPath();
                if (!exchange.getRequestMethod().equals("GET")) { status = 405; body = error("invalid_request", "GET required"); }
                else if (path.equals("/.well-known/openid-federation")) {
                    status = 200; type = "application/entity-statement+jwt"; body = Statements.configuration(host, key).serialize();
                } else if (host.equals("trust-anchor") && path.equals("/fetch")) {
                    String query = exchange.getRequestURI().getRawQuery();
                    String sub = query != null && query.startsWith("sub=") && !query.contains("&")
                        ? URLDecoder.decode(query.substring(4), StandardCharsets.UTF_8) : "";
                    if (sub.isEmpty() || sub.equals(Statements.ANCHOR)) { status = 400; body = error("invalid_request", "A subordinate sub is required"); }
                    else if (sub.equals(Statements.id("trustee"))) {
                        var keys = JWKSet.parse(Files.readString(Path.of("/data/registered-trustee.json")));
                        body = Statements.issue(Statements.ANCHOR, sub, keys.toJSONObject(), key, Map.of(), Instant.now()).serialize();
                        status = 200; type = "application/entity-statement+jwt";
                    } else { status = 404; body = error("not_found", "No subordinate statement for " + sub); }
                } else { status = 404; body = error("not_found", "Unknown endpoint"); }
            } catch (Exception e) { body = error("server_error", "Unable to issue statement"); }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
            System.out.println(host + " " + exchange.getRequestMethod() + " " + exchange.getRequestURI() + " -> " + status);
        };
        server.createContext("/", handler);
        server.start(); System.out.println(host + " listening on HTTPS :8443");
        // Loopback-published teaching transport; the exact same endpoint handler, no proxy.
        var browser = HttpServer.create(new InetSocketAddress(8080), 0);
        browser.createContext("/", handler);
        browser.start(); System.out.println(host + " browser teaching transport on HTTP :8080");
    }
    private static String error(String code, String description) {
        return com.nimbusds.jose.util.JSONObjectUtils.toJSONString(Map.of("error", code, "error_description", description));
    }
}
