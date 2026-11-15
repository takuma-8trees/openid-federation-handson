"""Developer regression: each request is an actual standard endpoint request."""
import base64
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.request
import urllib.error

ports = {k: os.environ.get(k + "_PORT", str(p)) for k, p in (("A",18766),("B",18767),("C",18768),("TA",18769))}
origin = "http://localhost:" + ports["A"]

def get(actor, path, expected=200):
    url = "http://localhost:" + ports[actor] + path
    req = urllib.request.Request(url, headers={"Origin": origin})
    try:
        response = urllib.request.urlopen(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = response.read().decode()
        assert response.status == expected, (url, response.status, body)
        if actor != "A":
            assert response.headers["Access-Control-Allow-Origin"] == origin
        if expected == 200 and actor != "A":
            assert response.headers["Content-Type"] == "application/entity-statement+jwt"
        print(f"GET {url} -> {response.status}")
        return body

def claims(jwt):
    return json.loads(base64.urlsafe_b64decode(jwt.split('.')[1] + '==='))

def verify(target, leaf, sub, anchor, expected):
    result = subprocess.run(["docker", "compose", "exec", "-T", "entity-a", "entity-a", "chain", "verify-files", target, leaf, sub, anchor], capture_output=True, text=True)
    assert result.returncode == expected, result.stdout + result.stderr
    assert "PASS: Entity Configuration" in result.stdout
    print(result.stdout)
    return result.stdout

for attempt in range(20):
    try:
        spec = json.loads(get("A", "/openapi.json"))
        break
    except OSError:
        if attempt == 19: raise
        time.sleep(1)
assert set(spec["paths"]) == {"/.well-known/openid-federation", "/fetch"}
get("A", "/api/chains/B/fetch", 404)
b = get("B", "/.well-known/openid-federation")
assert claims(b)["authority_hints"] == ["https://trust-anchor:8443"]
anchor = get("TA", "/.well-known/openid-federation")
assert claims(anchor)["metadata"]["federation_entity"]["federation_fetch_endpoint"] == "https://trust-anchor:8443/fetch"
sub = get("TA", "/fetch?sub=https%3A%2F%2Fentity-b%3A8443")
assert claims(sub)["iss"] == "https://trust-anchor:8443" and claims(sub)["sub"] == claims(b)["sub"]
c = get("C", "/.well-known/openid-federation")
assert json.loads(get("TA", "/fetch?sub=https%3A%2F%2Fentity-c%3A8443", 404))["error"] == "not_found"
get("TA", "/fetch", 400)
# Own temporary filenames; never overwrite the reader's saved JWTs.
files = []
try:
    for name, value in (("smoke-b", b), ("smoke-anchor", anchor), ("smoke-sub", sub), ("smoke-c", c)):
        file = Path("inbox") / (name + ".jwt")
        if file.exists(): raise RuntimeError(f"Existing test file: {file}")
        file.write_text(value, encoding="utf-8"); files.append(file)
    verify("B", "/inbox/smoke-b.jwt", "/inbox/smoke-sub.jwt", "/inbox/smoke-anchor.jwt", 0)
    assert "MISSING_SUBORDINATE" in verify("C", "/inbox/smoke-c.jwt", "-", "/inbox/smoke-anchor.jwt", 2)
finally:
    for file in files: file.unlink()
print("PASS: direct standard endpoints, raw JWTs, CORS, saved-file verification and C rejection")
