package local.cui;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Static documentation only: never fetches, resolves, or verifies a chain. */
final class Docs {
    static void start() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String resource = switch (path) {
                case "/" -> "/swagger-index.html";
                case "/jwt.html", "/openapi.json", "/swagger/swagger-ui.css", "/swagger/swagger-ui-bundle.js" -> path;
                default -> null;
            };
            int status = 200; byte[] body;
            String type = path.endsWith(".json") ? "application/json" : path.endsWith(".css") ? "text/css" : path.endsWith(".js") ? "text/javascript" : "text/html";
            if (!exchange.getRequestMethod().equals("GET") || resource == null) {
                status = 404; body = "Not found. This server only serves documentation.".getBytes(StandardCharsets.UTF_8);
            } else try (var in = Docs.class.getResourceAsStream(resource)) {
                if (in == null) throw new IllegalStateException("Missing resource " + resource);
                body = in.readAllBytes();
                if (path.equals("/openapi.json")) {
                    String spec = new String(body, StandardCharsets.UTF_8);
                    for (String actor : new String[]{"B", "C", "TA"})
                        spec = spec.replace("__" + actor + "_PORT__", System.getenv().getOrDefault(actor + "_PORT", actor.equals("B") ? "18767" : actor.equals("C") ? "18768" : "18769"));
                    body = spec.getBytes(StandardCharsets.UTF_8);
                }
            }
            exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(status, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
    }
}
