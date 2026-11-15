package local.cui;

import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** A's teaching API. These /api paths are not OpenID Federation endpoints. */
final class ReaderApi {
    static void start() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", ReaderApi::handle);
        server.start();
        System.out.println("Entity A Swagger UI: http://localhost:18766/");
    }
    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        try {
            if (!path.startsWith("/api/")) {
                if (!exchange.getRequestMethod().equals("GET")) { json(exchange, 405, Map.of("error", "GET required")); return; }
                String resource = switch (path) {
                    case "/" -> "/swagger-index.html";
                    case "/openapi.json" -> "/openapi.json";
                    case "/swagger/swagger-ui.css", "/swagger/swagger-ui-bundle.js" -> path;
                    default -> null;
                };
                if (resource == null) { json(exchange, 404, Map.of("error", "not_found")); return; }
                try (var stream = ReaderApi.class.getResourceAsStream(resource)) {
                    if (stream == null) throw new IllegalStateException("Static resource missing; build with Docker");
                    String type = path.endsWith(".json") ? "application/json" : path.endsWith(".css") ? "text/css" : path.endsWith(".js") ? "text/javascript" : "text/html";
                    send(exchange, 200, type + "; charset=utf-8", stream.readAllBytes()); return;
                }
            }
            String[] command; String target = null; String operation;
            if (path.equals("/api/trust")) { command = new String[]{"trust", "show"}; operation = "trust"; }
            else {
                var match = java.util.regex.Pattern.compile("/api/chains/(B|C)(?:/(fetch|verify))?").matcher(path);
                if (!match.matches()) { json(exchange, 404, Map.of("error", "Use target B or C and a documented endpoint")); return; }
                target = match.group(1); operation = match.group(2) == null ? "show" : match.group(2);
                command = new String[]{"chain", operation, target};
            }
            String method = Set.of("fetch", "verify").contains(operation) ? "POST" : "GET";
            if (!exchange.getRequestMethod().equals(method)) {
                exchange.getResponseHeaders().set("Allow", method);
                json(exchange, 405, Map.of("error", method + " required")); return;
            }
            // A local teaching tool: no cross-origin mutation or CORS allowance.
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            String host = exchange.getRequestHeaders().getFirst("Host");
            if (origin != null && !origin.equals("http://" + host)) { json(exchange, 403, Map.of("error", "Same-origin requests only")); return; }
            if (target != null && !operation.equals("fetch") && !Files.exists(Reader.DATA.resolve(target).resolve("snapshot.json"))) {
                json(exchange, 409, Map.of("error", "FETCH_REQUIRED", "message", "先に POST /api/chains/" + target + "/fetch を実行してください")); return;
            }
            var buffer = new ByteArrayOutputStream();
            int code;
            try (var output = new PrintStream(buffer, true, StandardCharsets.UTF_8)) { code = new Reader(output).execute(command); }
            var result = new LinkedHashMap<String,Object>();
            result.put("actor", "Entity A"); result.put("operation", operation);
            result.put("status", operation.equals("verify") ? (code == 0 ? "TRUSTED" : "NOT_TRUSTED") : "UNVERIFIED");
            result.put("steps", buffer.toString(StandardCharsets.UTF_8).lines()
                .filter(line -> !line.startsWith("次:") && !line.startsWith("保存先:")).toList());
            if (target != null) {
                result.put("target", target);
                var snapshot = Reader.load(Reader.DATA.resolve(target));
                result.put("fetched_at", snapshot.get("fetched_at"));
                result.put("complete", snapshot.containsKey("subordinate"));
                if (operation.equals("show")) {
                    var documents = new ArrayList<Map<String,Object>>();
                    for (String role : List.of("leaf", "subordinate", "anchor")) {
                        var document = new LinkedHashMap<String,Object>(); document.put("role", role);
                        if (snapshot.containsKey(role)) {
                            var jwt = SignedJWT.parse((String) snapshot.get(role));
                            document.put("header", jwt.getHeader().toJSONObject());
                            document.put("claims", jwt.getJWTClaimsSet().toJSONObject());
                            document.put("compact_jwt", jwt.serialize());
                        } else document.put("missing", snapshot.get("subordinate_error"));
                        documents.add(document);
                    }
                    result.put("documents", documents);
                }
            } else result.put("status", "CONFIGURED");
            json(exchange, code == 2 ? 422 : 200, result);
        } catch (Exception e) { json(exchange, 502, Map.of("error", "OPERATION_FAILED", "message", Objects.toString(e.getMessage(), e.getClass().getSimpleName()))); }
    }
    private static void json(HttpExchange exchange, int status, Map<String,Object> value) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", JSONObjectUtils.toJSONString(value).getBytes(StandardCharsets.UTF_8));
    }
    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        try (var out = exchange.getResponseBody()) { out.write(body); }
    }
}
