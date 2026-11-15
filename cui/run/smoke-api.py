"""Developer check; the reader's hands-on uses Swagger UI, not this script."""
import json
import sys
import time
import urllib.request
import urllib.error

base = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:18766"

def request(path, method="GET", expected=200):
    req = urllib.request.Request(base + path, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = response.read().decode()
        assert response.status == expected, (path, response.status, body)
        return json.loads(body)

for attempt in range(20):
    try:
        assert request("/api/trust")["status"] == "CONFIGURED"
        break
    except (OSError, AssertionError):
        if attempt == 19:
            raise
        time.sleep(1)

spec = request("/openapi.json")
assert len(spec["paths"]) == 4
if "--fresh" in sys.argv:
    for target in ("B", "C"):
        assert request(f"/api/chains/{target}", expected=409)["error"] == "FETCH_REQUIRED"
        request(f"/api/chains/{target}/verify", "POST", 409)

for target, complete, status in (("B", True, "TRUSTED"), ("C", False, "NOT_TRUSTED")):
    fetched = request(f"/api/chains/{target}/fetch", "POST")
    assert fetched["status"] == "UNVERIFIED" and fetched["complete"] is complete
    shown = request(f"/api/chains/{target}")
    assert shown["status"] == "UNVERIFIED"
    assert shown["fetched_at"] == fetched["fetched_at"]
    documents = shown["documents"]
    assert [doc["role"] for doc in documents] == ["leaf", "subordinate", "anchor"]
    assert documents[0]["claims"]["iss"] == f"https://entity-{target.lower()}:8443"
    if complete:
        assert documents[1]["claims"]["iss"] == "https://trust-anchor:8443"
        assert documents[1]["claims"]["sub"] == "https://entity-b:8443"
    else:
        assert documents[1]["missing"]["error"] == "not_found"
    verified = request(f"/api/chains/{target}/verify", "POST", 200 if complete else 422)
    assert verified["status"] == status and verified["fetched_at"] == fetched["fetched_at"]
    assert verified["steps"][0].startswith("PASS: Entity Configuration")
    if not complete:
        assert "MISSING_SUBORDINATE" in verified["steps"][-1]
    print(f"PASS: {target}: fetch -> show UNVERIFIED -> verify {status}")
request("/api/chains/D/fetch", "POST", 404)
request("/api/chains/B/fetch", "GET", 405)
print("PASS: OpenAPI, response documents, status codes and stored-snapshot continuity")
