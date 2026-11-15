package local.cui;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.SignedJWT;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

final class Reader {
    static final Path DATA = Path.of("/data");
    private final java.io.PrintStream out;
    Reader(java.io.PrintStream out) { this.out = out; }
    static int run(String[] args) throws Exception { return new Reader(System.out).execute(args); }
    int execute(String[] args) throws Exception {
        if (Arrays.equals(args, new String[]{"trust", "show"})) {
            out.println("Entity Aの事前設定（ネットワークから自動採用しない）");
            out.println("Trust Anchor: " + Statements.ANCHOR);
            for (var key : pin().getKeys()) out.println("Pinned JWK SHA-256: " + key.computeThumbprint());
            out.println("Anchor公開鍵: /data/anchor-jwks.json\nHTTPS用の証明書ストア: /data/tls-trust.p12（Federationの信頼設定とは別）");
            return 0;
        }
        if (args.length < 3 || args.length > 4 || !args[0].equals("chain") || !Set.of("B", "C").contains(args[2])
            || (args.length == 4 && !(args[1].equals("show") && args[3].equals("--json"))))
            throw new IllegalArgumentException("entity-a trust show | chain fetch/show/verify B/C [--json for show]");
        String target = Statements.id("entity-" + args[2].toLowerCase(Locale.ROOT));
        Path dir = DATA.resolve(args[2]);
        switch (args[1]) {
            case "fetch" -> fetch(target, dir);
            case "show" -> show(dir, args.length == 4);
            case "verify" -> {
                var docs = load(dir);
                try {
                    Verification.verify(target, parse(docs, "leaf"), parse(docs, "subordinate"), parse(docs, "anchor"), pin(), Instant.now(), out::println);
                } catch (Exception e) { out.println("NOT_TRUSTED: " + e.getMessage()); return 2; }
            }
            default -> throw new IllegalArgumentException("Unknown chain command");
        }
        return 0;
    }
    private static JWKSet pin() throws Exception { return JWKSet.parse(Files.readString(DATA.resolve("anchor-jwks.json"))); }
    private static SignedJWT parse(Map<String,Object> docs, String name) throws Exception {
        return docs.get(name) == null ? null : SignedJWT.parse((String) docs.get(name));
    }
    static Map<String,Object> load(Path dir) throws Exception {
        if (!Files.exists(dir.resolve("snapshot.json"))) throw new IllegalStateException("先に chain fetch " + dir.getFileName() + " を実行してください");
        return JSONObjectUtils.parse(Files.readString(dir.resolve("snapshot.json")));
    }
    private HttpResponse<String> get(String uri) throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        var response = client.send(HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
        out.println("GET " + uri + " -> HTTP " + response.statusCode());
        return response;
    }
    private static String statement(HttpResponse<String> response) {
        if (response.statusCode() != 200 || !response.headers().firstValue("content-type").orElse("").split(";")[0].equals("application/entity-statement+jwt"))
            throw new IllegalStateException("Expected HTTP 200 application/entity-statement+jwt");
        return response.body();
    }
    private void fetch(String target, Path dir) throws Exception {
        Files.createDirectories(dir);
        // Invalidate the old snapshot first, so a failed refresh cannot validate stale evidence.
        Files.deleteIfExists(dir.resolve("snapshot.json"));
        String leaf = statement(get(target + "/.well-known/openid-federation"));
        var hints = SignedJWT.parse(leaf).getJWTClaimsSet().getStringListClaim("authority_hints");
        if (!List.of(Statements.ANCHOR).equals(hints)) throw new IllegalStateException("This fixture only follows the configured Anchor hint");
        out.println("authority_hints -> " + hints + "（まだ未検証）");
        String anchor = statement(get(Statements.ANCHOR + "/.well-known/openid-federation"));
        var metadata = SignedJWT.parse(anchor).getJWTClaimsSet().getJSONObjectClaim("metadata");
        Object endpoint = ((Map<?,?>) metadata.get("federation_entity")).get("federation_fetch_endpoint");
        if (!(Statements.ANCHOR + "/fetch").equals(endpoint)) throw new IllegalStateException("Unexpected fetch endpoint");
        var response = get(endpoint + "?sub=" + URLEncoder.encode(target, StandardCharsets.UTF_8));
        var docs = new LinkedHashMap<String,Object>();
        docs.put("fetched_at", Instant.now().toString()); docs.put("leaf", leaf); docs.put("anchor", anchor);
        if (response.statusCode() == 404 && response.headers().firstValue("content-type").orElse("").equals("application/json")
            && "not_found".equals(JSONObjectUtils.parse(response.body()).get("error"))) {
            docs.put("subordinate_error", JSONObjectUtils.parse(response.body()));
            out.println("INCOMPLETE: AnchorのSubordinate Statementなし。取得できた文書を保存します。");
        } else { docs.put("subordinate", statement(response)); out.println("COLLECTED: 3文書を保存しました。信頼の検証はまだ行っていません。"); }
        Files.writeString(dir.resolve("snapshot.json"), JSONObjectUtils.toJSONString(docs));
        out.println("保存先: " + dir.resolve("snapshot.json") + "\n次: chain show " + dir.getFileName());
    }
    private void show(Path dir, boolean json) throws Exception {
        var docs = load(dir);
        out.println("Entity Aで取得した候補チェーン（デコード表示・このコマンドは検証しません）");
        out.println("取得時刻: " + docs.get("fetched_at"));
        for (String name : List.of("leaf", "subordinate", "anchor")) {
            var jwt = parse(docs, name);
            if (jwt == null) { out.println("[subordinate] MISSING: " + docs.get("subordinate_error")); continue; }
            var claims = jwt.getJWTClaimsSet();
            out.println("[" + name + "] iss=" + claims.getIssuer() + "\n  sub=" + claims.getSubject());
            if (json) {
                out.println("  header=" + jwt.getHeader().toString());
                out.println("  claims=" + JSONObjectUtils.toJSONString(claims.toJSONObject()));
                out.println("  compact_jwt=" + jwt.serialize());
            }
        }
        out.println("次: chain verify " + dir.getFileName() + "（保存した同じ文書を検証）");
    }
}
