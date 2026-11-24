# OpenID Federation: Trust Chain 最小デモ

このリポジトリは、OpenID Federation の **Trust Chain（Trustee → Trust Anchor）** を観察・検証するための最小デモです。

- 立てる環境（サーバ）: **Trustee / Trust Anchor**
- 体験者（Trustor）: **取得して検証する側**（サーバは立てない）

## 起動

```sh
docker compose up --build --wait -d
```

- Trustee: http://localhost:18767
- Trust Anchor: http://localhost:18769

停止:

```sh
docker compose down
```

初期化し直す（鍵・登録を作り直す）:

```sh
docker compose down --volumes
```

## 社内プロキシ（SSL inspection）対策: 任意のCA証明書を追加

社内プロキシ等で Maven の依存取得が `SSLHandshakeException` になる場合、
プロキシのルートCA証明書（`.crt`/`.pem`）を [certs/](/home/yagitka/openid-federation-handson/certs) に置いてください。

- [certs/](/home/yagitka/openid-federation-handson/certs) 配下の証明書は `.gitignore` 対象です（コミットしません）
- 追加/削除したら `docker compose build --no-cache` で反映してください

## Trustor（体験者）: 取得 → 検証

手順の詳細は [run/](/home/yagitka/openid-federation-handson/run) と [inbox/](/home/yagitka/openid-federation-handson/inbox) を使う前提で README を整備していきます。
現状はまず最小コマンドとして、以下が使えます。

取得（JWTを保存）:

```sh
curl -fsS http://localhost:18767/.well-known/openid-federation -o inbox/trustee.jwt
curl -fsS http://localhost:18769/.well-known/openid-federation -o inbox/anchor.jwt
curl -fsS --get --data-urlencode sub=https://trustee:8443 http://localhost:18769/fetch -o inbox/trustee-subordinate.jwt
```

検証（オフライン）:

```sh
docker compose run --rm -T trustor chain verify-files trustee /inbox/trustee.jwt /inbox/trustee-subordinate.jwt /inbox/anchor.jwt
```
