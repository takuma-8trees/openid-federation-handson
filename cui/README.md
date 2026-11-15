# 標準Endpointを叩きながらTrust Chainを組み立てる

読者は **Entity Aの立場** で、Bの文書を取得し、上位Entityを見つけ、上位がBについて発行した文書を取得します。まず3文書の関係を確認し、その後に署名を検証します。CはAnchor未登録の負例です。

Swaggerが叩くのは各サービスの **`/.well-known/openid-federation` と `federation_fetch_endpoint`** です。Aに一括取得させるAPIはありません。デコード補助はブラウザー内だけで動きます。署名検証は読者が保存したJWTをAに渡す独立した手順です。

## 概念とサービス

```text
読者（Entity Aの立場）
  1. BのEntity ConfigurationをGET
  2. authority_hintsからAnchorを見つけ、AnchorのEntity ConfigurationをGET
  3. federation_fetch_endpointにsub=Bを指定してGET
  4. 取得した3文書を並べてから、Aの事前設定したAnchor公開鍵で検証

Entity B ── Anchor発行のSubordinate Statementがある ── Trust Anchor
Entity C ── 自己署名は正しいが、AnchorのStatementがない
```

| サービス | 役割 | ブラウザー入口 | Entity ID（内部HTTPS） |
| --- | --- | --- | --- |
| Entity A | 静的Swagger、デコード補助、ファイル署名検証コマンド | http://localhost:18766 | 検証者のみ。自身のECは公開しない |
| Entity B | B自身のEntity Configuration | http://localhost:18767 | `https://entity-b:8443` |
| Entity C | C自身のEntity Configuration | http://localhost:18768 | `https://entity-c:8443` |
| Trust Anchor | AnchorのECとSubordinate Statement | http://localhost:18769 | `https://trust-anchor:8443` |

4つは別コンテナです。SwaggerからのリクエストはブラウザーからB/C/Anchorへ直接届き、Aは通信を代行しません。各サービスは自分の秘密鍵だけを持ち、AnchorはBの公開鍵だけを登録しています。初回だけ動く `init` が教材用の鍵、TLS証明書、Bの登録、AへのAnchor公開鍵の配布を行います。

**HTTPとHTTPSの区別：** OIDFのEntity IDとEC取得先は仕様上HTTPSです。本教材のブラウザー入口は、証明書のOS登録なしでリクエストと生JWTを観察するためのローカルHTTP入口です。同じサービスの同じハンドラーで処理しますが、**このHTTP入口自体は仕様のHTTPS要件を満たしません**。JWT内のID・authority_hints・fetch endpointは内部HTTPSのままです。Swaggerでは表の対応先を選び、`sub` にはHTTPSのEntity IDを入力してください。正式なHTTPSの取得例も後述します。

## 起動

Docker Engine、Compose v2、Gitが必要です。

```sh
git clone https://github.com/takuma-8trees/openid-federation-handson.git
cd openid-federation-handson/cui
docker compose up --build --wait -d
```

[Swagger UI](http://localhost:18766/) を開きます。旧UI試作の18765とは別です。以前のSwaggerを開いていた場合は再読み込みしてください。表示される操作は **GETの2種類だけ** です。

ポート・サブネットが重なる場合だけ `.env.example` を `.env` にコピーして変更します。公開先はすべて127.0.0.1です。

## 1. BのEntity Configurationを取得する

Swaggerの **GET `/.well-known/openid-federation`** を開き、**Try it out** を押します。その後に表示される操作内の **ServersでEntity B** を選び、**Execute**。

- Request URL：`http://localhost:18767/.well-known/openid-federation`
- HTTP 200、Content-Type：`application/entity-statement+jwt`
- Response body：`eyJ...` のような、3部分をピリオドでつないだ署名付きJWT

Swaggerの `Unrecognized response type; displaying content as text.` はJWTを文字列表示している案内です。HTTP 200なら取得失敗ではありません。

Response bodyを [JWTを読む補助](http://localhost:18766/jwt.html) に貼り付け、デコードします。この補助は送信・取得・署名検証をしません。

```text
iss = https://entity-b:8443
sub = https://entity-b:8443
authority_hints = [https://trust-anchor:8443]
jwks = Bの公開鍵
```

ここで初めて、次に問い合わせる候補がAnchorだと分かります。ヒントはBの自己申告であり、Anchorによる承認ではありません。

SwaggerのResponse bodyの **Download** で保存し、ファイル名を `b.jwt` にして、このリポジトリの **`cui/inbox/`** に置きます。保存時はJWT本文だけにし、JSONや引用符で包みません。次の取得後も同じ3文書を使うためです。

## 2. AnchorのEntity Configurationを取得する

同じ `/.well-known/openid-federation` 操作の **ServersをTrust Anchorへ変更** してExecuteします。JWTを補助で読みます。

```text
iss = sub = https://trust-anchor:8443
metadata.federation_entity.federation_fetch_endpoint
  = https://trust-anchor:8443/fetch
```

これで次に使うfetch endpointが分かりました。`/fetch` という名前そのものが仕様で固定されているわけではありません。**このメタデータに記載されたURLを使う**のが仕様です。

同じ方法でJWTを **`cui/inbox/anchor.jwt`** に保存します。

## 3. AnchorがBについて発行した文書を取得する

Swaggerの **GET `/fetch`** を開きます。送信先はAnchorです。Try it outを押し、`sub` にBのECで確認した値を貼り付けます。

```text
https://entity-b:8443
```

Executeすると `http://localhost:18769/fetch?sub=https%3A%2F%2Fentity-b%3A8443` にGETします。HTTP 200のJWTをデコードし、**`cui/inbox/b-subordinate.jwt`** に保存します。

```text
iss = https://trust-anchor:8443
sub = https://entity-b:8443
jwks = AnchorがBのものとして示した公開鍵
```

ここまでの3文書を比べます。**まだ署名は検証していません。**

| 文書 | iss（誰が発行） | sub（誰について） |
| --- | --- | --- |
| BのEC | B | B |
| BのSubordinate Statement | Anchor | B |
| AnchorのEC | Anchor | Anchor |

Bの自己申告に加え、AnchorがBについて署名する文書が1つ間に入りました。

## 4. 保存した3文書をAで検証する

`cui/` で実行します。最初にAが事前設定した信頼起点を見ます。

```sh
docker compose exec -T entity-a entity-a trust show
docker compose exec -T entity-a entity-a chain verify-files B /inbox/b.jwt /inbox/b-subordinate.jwt /inbox/anchor.jwt
```

このコマンドは **指定したファイルを読み、通信せずに検証** します。自動取得した別文書への置き換えはありません。各署名・iss/sub・有効期限のPASSに続き、最後に次が出ます。

```text
TRUSTED: https://entity-b:8443 -> https://trust-anchor:8443
```

`inbox` はAに読み取り専用でマウントされます。ファイル名が違う場合はコマンドのパスも合わせてください。期限は発行から1時間です。期限切れなら標準Endpointから再度取得します。

## 5. Cでも同じEndpointを叩く

1. EC操作のServersをEntity CにしてExecute。自己署名JWTをデコードし、`authority_hints` が同じAnchorを指すことを確認。`cui/inbox/c.jwt` に保存。
2. AnchorのECを取得してfetch endpointを確認。
3. `/fetch` のsubを **`https://entity-c:8443`** に変更しExecute。

Anchorから **HTTP 404 / `not_found`** が返ります。Cの文書が読めても、AnchorはCのSubordinate Statementを発行しません。JSONエラーをJWTとして保存しないでください。

```sh
docker compose exec -T entity-a entity-a chain verify-files C /inbox/c.jwt - /inbox/anchor.jwt
```

`-` はSubordinate Statementがないことを明示します。Cの自己署名はPASSしますが、最後は次です（終了コード2、期待する負例）。

```text
NOT_TRUSTED: MISSING_SUBORDINATE: Anchorが対象について発行したStatementがない
```

## 正式なHTTPSのEndpointをAから1つずつ叩く場合

Swagger用HTTP入口との違いを確認するための補足です。Aコンテナにはcurlとローカル証明書があります。`-k` は使いません。リダイレクトはホストのファイルへ保存するため、以下は **Bash / WSL** で実行してください。

```sh
docker compose exec -T entity-a curl --cacert /data/entity-b.pem https://entity-b:8443/.well-known/openid-federation > inbox/b.jwt
docker compose exec -T entity-a entity-a document decode /inbox/b.jwt
# 上のauthority_hintsを読んで次のURLを決める
docker compose exec -T entity-a curl --cacert /data/trust-anchor.pem https://trust-anchor:8443/.well-known/openid-federation > inbox/anchor.jwt
docker compose exec -T entity-a entity-a document decode /inbox/anchor.jwt
# 上のfederation_fetch_endpointを読み、Bのsubを指定する
docker compose exec -T entity-a curl --cacert /data/trust-anchor.pem --get --data-urlencode sub=https://entity-b:8443 https://trust-anchor:8443/fetch > inbox/b-subordinate.jwt
```

その後は同じ `verify-files` コマンドで検証できます。正式なHTTPSのURL、標準パラメーター、生JWTを1回ずつ確認でき、自動探索APIは使いません。

## ログ・停止・検証範囲

```sh
docker compose logs entity-b entity-c trust-anchor
docker compose down
```

各サービスのログで実際のGETとHTTPコードを確認できます。通常のdownでは鍵・登録・inboxのファイルは残ります。初期化し直す場合に限り `cui/` で `docker compose down --volumes` を実行すると鍵が消えます。次回起動で新しい鍵になり、以前のJWTは使えません。inboxのファイルはホスト上なのでこの操作では消えません。

Java 21、Nimbus SDK 11.38.2、Swagger UI 5.34.1。Nimbusが署名とTrust Chainを検証し、サービス・教材用初期化・CUIは自作です。提供された参考実装を設計の参考にしています。Swagger配布資材はビルド時に固定SHA-512で検証し、閲覧時のCDNは不要です。

対象はRS256、B/Cから単一Anchorまでの直接チェーン。Metadata Policy、Trust Marks、Constraints、中間Entity、鍵ローテーション、OIDCログイン・クライアント登録は対象外。未対応claimは検証で拒否します。所属の審査基準をOIDFが自動で決めるわけではなく、この教材では初期設定者がBだけを登録しています。

開発者用：`sh run/smoke-endpoints.sh`（Python 3標準ライブラリ使用）で標準Endpoint・生JWT・ファイル検証を確認します。JUnit7件はDockerビルドで実行。既存の `chain fetch/show/verify` は内部回帰チェック用に残していますが、読者向け手順では使いません。旧 `/api/trust`・`/api/chains` は削除しました。

規範：[OpenID Federation 1.0 Final](https://openid.net/specs/openid-federation-1_0-final.html) Sections 3・4・8.1・9。[実測記録](../evidence/standard-endpoints.md)。仕様全体への適合認証を主張する教材ではありません。
