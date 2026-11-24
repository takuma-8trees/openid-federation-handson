"""Developer regression: standard endpoint flow (Trustee -> Anchor)."""
import base64
import json
import os
from pathlib import Path
import subprocess
import urllib.request
import urllib.error

ports = {
    "TRUSTEE": os.environ.get("B_PORT", "18767"),
    "TA": os.environ.get("TA_PORT", "18769"),
}

def get(actor, path, expected=200):
    url = "http://localhost:" + ports[actor] + path
    req = urllib.request.Request(url)
    try:
        response = urllib.request.urlopen(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = response.read().decode()
        assert response.status == expected, (url, response.status, body)
        if expected == 200:
            assert response.headers["Content-Type"] == "application/entity-statement+jwt"
        print(f"GET {url} -> {response.status}")
        return body

def claims(jwt):
    return json.loads(base64.urlsafe_b64decode(jwt.split('.')[1] + '==='))

def verify(target, leaf, sub, anchor, expected):
    result = subprocess.run(
        [
            "docker",
            "compose",
            "run",
            "--rm",
            "-T",
            "trustor",
            "chain",
            "verify-files",
            target,
            leaf,
            sub,
            anchor,
        ],
        capture_output=True,
        text=True,
    )
    assert result.returncode == expected, result.stdout + result.stderr
    assert "PASS: Entity Configuration" in result.stdout
    print(result.stdout)
    return result.stdout

b = get("TRUSTEE", "/.well-known/openid-federation")
assert claims(b)["authority_hints"] == ["https://trust-anchor:8443"]
anchor = get("TA", "/.well-known/openid-federation")
assert claims(anchor)["metadata"]["federation_entity"]["federation_fetch_endpoint"] == "https://trust-anchor:8443/fetch"
sub = get("TA", "/fetch?sub=https%3A%2F%2Ftrustee%3A8443")
assert claims(sub)["iss"] == "https://trust-anchor:8443" and claims(sub)["sub"] == claims(b)["sub"]
get("TA", "/fetch", 400)
# Own temporary filenames; never overwrite the reader's saved JWTs.
files = []
try:
    for name, value in (("smoke-trustee", b), ("smoke-anchor", anchor), ("smoke-subordinate", sub)):
        file = Path("inbox") / (name + ".jwt")
        if file.exists(): raise RuntimeError(f"Existing test file: {file}")
        file.write_text(value, encoding="utf-8"); files.append(file)
    verify("trustee", "/inbox/smoke-trustee.jwt", "/inbox/smoke-subordinate.jwt", "/inbox/smoke-anchor.jwt", 0)
finally:
    for file in files: file.unlink()
print("PASS: direct standard endpoints, raw JWTs, and saved-file verification")
