# Entity AからTrust Chainを確認する（Swagger UI / CUI）

読者は一貫して **Entity Aの操作者** です。まず文書を集めてチェーンの構造を見て、次に同じ文書の署名を検証します。Entity BはAnchorの登録対象、Entity Cは未登録です。

## 1. 概念構成

```text
Entity A（読者）
  ├─ 事前設定：信頼するTrust AnchorのEntity IDと公開鍵
  ├─ Bを調べる → Bの自己署名文書 → AnchorのBについての署名文書 → Anchor
  └─ Cを調べる → Cの自己署名文書 → AnchorのCについての署名文書がない
```

`authority_hints` は取得先を探すヒントです。そこにAnchorのIDを書くだけでは所属の証拠になりません。BについてはAnchorが署名した **Subordinate Statement** があり、その中の公開鍵を使ってBの文書を検証できます。Cも正しい自己署名文書と同じヒントを公開しますが、AnchorはCのStatementを発行しません。

A自身の登録はこの片方向の検証に必要ありません。この教材のAは検証者として動作し、自身のEntity Configurationは公開しません。「Bが信頼できる」は、ここではAの指定したAnchorまで署名による裏付けがつながるという限定した意味です。Bのあらゆる行動やサービス品質を保証する意味ではありません。

## 2. マイクロサービス構成

| コンテナ | 役割 | 持つもの |
| --- | --- | --- |
| `entity-a` | 読者のSwagger UI/API・CUI・取得・検証 | Anchorの公開鍵、TLS用公開証明書、取得した文書 |
| `entity-b` | BのEntity Configurationを公開 | Bだけの署名秘密鍵・TLS秘密鍵 |
| `entity-c` | CのEntity Configurationを公開 | Cだけの署名秘密鍵・TLS秘密鍵 |
| `trust-anchor` | AnchorのEntity Configuration、BのSubordinate Statementを公開 | Anchorだけの秘密鍵、Bの登録公開鍵 |

4サービスは同じイメージから別プロセスとして起動します。AがB/CとAnchorへ直接、内部ネットワークのHTTPS :8443で通信します。各コンテナは他者の秘密鍵をマウントしません。Aだけがブラウザー用ネットワークにも参加し、`127.0.0.1:18766` でSwagger UIと教材用APIを公開します。B/C/Anchorのホスト公開ポートはありません。

別途 `init` が初回に終了するセットアップ処理として、教材用の鍵・証明書・Bの登録を用意します。これは通信を仲介するサービスではありません。初期設定者がAへAnchorの公開鍵を渡す操作を、この処理で再現しています。Aが相手から取得した公開鍵を無条件に信頼設定へ追加することはありません。

TLS証明書はローカル教材用です。Aは事前に渡された証明書とホスト名を検証し、TLS検証を無効化しません。TLSの通信先認証と、FederationのAnchor公開鍵による信頼の検証は別の処理です。

## 3. 起動

前提：Docker EngineとDocker Compose v2。以下の通常操作はBashとPowerShellで共通です。WindowsではDockerが利用できるWSLのBashでも実行できます。Java/Mavenをホストへ入れる必要はありません。

```sh
git clone https://github.com/takuma-8trees/openid-federation-handson.git
cd openid-federation-handson/cui
docker compose up --build -d
docker compose ps -a
```

`init` は `Exited (0)`、他の4サービスは起動した状態になります。初回ビルドにはネットワーク接続が必要です。サブネットやポートが既存環境と重複する場合だけ `.env.example` を `.env` にコピーし、`CUI_SUBNET`・`A_SUBNET`・`A_PORT` を変更してください。旧UI版の起動ディレクトリとは異なります。

## Swagger UIで試す

[http://localhost:18766/](http://localhost:18766/) を開きます。旧UI試作の18765ではありません。

操作する行を開き、**Try it out → Execute** を押します。レスポンスの `steps` を上から読みます。最初は `target=B` で次の順に進めてください。

| 順番 | 操作 | 確認すること |
| --- | --- | --- |
| 1 | `GET /api/trust` | Aが事前に信頼するAnchorのIDと公開鍵 |
| 2 | `POST /api/chains/{target}/fetch` | 3文書を取得。`status=UNVERIFIED`、`complete=true` |
| 3 | `GET /api/chains/{target}` | `steps` のiss/subを比較。`documents` に署名付き文書とデコード結果 |
| 4 | `POST /api/chains/{target}/verify` | 保存した同じ文書を検証。HTTP 200、`status=TRUSTED` |

**3でチェーンの構造を見てから、4の検証へ進んでください。** `documents` はleaf（対象の自己署名）、subordinate（Anchorが対象について署名）、anchor（Anchorの自己署名）の順です。デコード表示では署名検証を行いません。

次に各操作の `target` を **C** に変えて、2〜4を繰り返します。2はHTTP 200でも `complete=false`、3ではsubordinateが `missing`。4は **HTTP 422 / NOT_TRUSTED** になり、自己署名のPASSに続いて `MISSING_SUBORDINATE` と表示されます。これが期待する負例です。取得前の3/4はHTTP 409で取得を促します。

Swagger UI/APIとCUIは同じAの取得・検証処理と保存データを使います。下記 `/api` は教材用の操作APIであり、OpenID Federationの標準endpointではありません。OpenAPI定義は [openapi.json](src/main/resources/openapi.json)。Swagger UI **5.34.1** の配布資材を固定ハッシュでビルド時に取得し、Aから配信します。閲覧時のCDN通信は不要です。

以下は同じ流れをCUIで確認する手順です。

## 4. まずBのChainを確認する

### Aが誰を信頼しているか見る

```sh
docker compose exec -T entity-a entity-a trust show
```

AnchorのIDと、Aに事前設定された公開鍵のSHA-256 thumbprintが出ます。これはネットワークから取得したBの自己申告とは独立した出発点です。

### 文書を取得する（まだ検証しない）

```sh
docker compose exec -T entity-a entity-a chain fetch B
```

3回のHTTP 200を確認します。

1. Bの `/.well-known/openid-federation` からBのEntity Configurationを取得。
2. `authority_hints` をたどり、AnchorのEntity Configurationを取得。
3. Anchorの `federation_fetch_endpoint` に `sub=https://entity-b:8443` を指定し、BのSubordinate Statementを取得。

最後に `COLLECTED` と出ます。これは文書がそろったという意味で、信頼できるという判定ではありません。

### 誰が誰について述べているか読む

```sh
docker compose exec -T entity-a entity-a chain show B
```

```text
[leaf] iss=https://entity-b:8443
  sub=https://entity-b:8443
[subordinate] iss=https://trust-anchor:8443
  sub=https://entity-b:8443
[anchor] iss=https://trust-anchor:8443
  sub=https://trust-anchor:8443
```

`iss` は発行者、`sub` は対象です。leafとanchorは自分についての文書です。間の文書だけが **AnchorからBへの裏付け** になっています。

```sh
docker compose exec -T entity-a entity-a chain show B --json
```

詳しく見る場合はJWTヘッダー・claims・署名付きJWT全体を表示します。Bの `authority_hints`、Anchorのfetch endpoint、Subordinate Statementの `jwks`（Bの公開鍵）を探してください。デコードして読めることと、署名が正しいことは別です。

## 5. 同じChainをAで検証する

```sh
docker compose exec -T entity-a entity-a chain verify B
```

検証は保存済みの文書を使い、再取得しません。自己署名、iss/sub、有効期限、事前設定したAnchor公開鍵、Anchorの署名、Anchorが示したBの鍵によるBの署名を確認します。最後が次のようになれば成功です。

```text
TRUSTED: https://entity-b:8443 -> https://trust-anchor:8443
```

## 6. Cではどこで途切れるか確認する

```sh
docker compose exec -T entity-a entity-a chain fetch C
docker compose exec -T entity-a entity-a chain show C
docker compose exec -T entity-a entity-a chain verify C
```

取得時はCの文書・Anchorの文書がHTTP 200、CのSubordinate Statementが **HTTP 404 / not_found** になります。`show` では `[subordinate] MISSING` と表示されます。

`verify` ではCの自己署名とAnchorの署名はPASSしますが、次で止まります。

```text
NOT_TRUSTED: MISSING_SUBORDINATE: Anchorが対象について発行したStatementがない
```

これは期待する負例です。終了コードは **2**（検証拒否）。Bの成功は0、通信エラー・操作エラーは1です。B/Cを固定値で成功・失敗に分岐させず、取得できた署名文書で判断します。

## 7. 通信と保存内容を確かめる

```sh
docker compose logs entity-b entity-c trust-anchor
docker compose exec -T entity-a cat /data/B/snapshot.json
docker compose exec -T entity-a cat /data/C/snapshot.json
```

ログには別サービスへ届いたGETとHTTPステータスが残ります。snapshotには取得時刻と署名付きJWT、CについてはAnchorのエラー応答が残ります。`show` は常にデコード表示として扱い、過去の検証成功を保存・表示しません。1時間を過ぎて文書が失効したら `fetch` からやり直してください。

## 8. 停止・再実行

```sh
docker compose down
docker compose up -d
```

通常の停止では専用ボリューム内の鍵・登録・snapshotが残ります。TLS証明書の有効期間は生成から365日です。

初期状態からやり直す場合だけ、**`cui/` にいることを確認して**次を実行します。CUI専用ボリュームの鍵と取得済みデータが消え、次の起動で新しい鍵になります。

```sh
docker compose down --volumes
docker compose up -d
```

## 検証スクリプト・実装範囲

開発側の再現チェックは、起動後に `cui/` で `sh run/smoke-test.sh`（Bash/WSL）を実行します。読者は先に上記コマンドを1つずつ試してください。Dockerビルド時には暗号検証のJUnitテストも走ります。

APIの再現チェックは `sh run/smoke-api.sh`（開発者用、Python 3標準ライブラリを使用）。新規ボリュームで取得前の409も確認する場合は `sh run/smoke-api.sh http://localhost:18766 --fresh`。通常のハンズオンではこのスクリプトの実行は不要です。

- Java 21 / Nimbus OAuth 2.0 & OIDC SDK **11.38.2**。提供された参考実装と同じライブラリ系列を使い、EntityStatementとTrustChainの署名検証を利用しています。提供されたアプリ自体を起動する構成ではありません。
- 自作：HTTPS公開サービス、初期教材データ、取得・保存・CUI、直接チェーン用の検証条件。
- 対象は **BまたはC → 単一Anchor**、RS256、`federation_entity` メタデータの教材用部分集合。任意URLをたどる汎用Resolverではありません。取得先は固定した教材ホストに限定します。
- Metadata Policy、Constraints、Trust Marks、中間Entity、鍵ローテーション、OIDCログイン、クライアント登録は扱いません。未対応のStatement claimが含まれた場合は検証を拒否します。
- Anchorが誰を登録すべきかという組織上の審査は、このプロトコルが自動で決めるものではありません。教材では初期設定者がBだけを登録しています。

実装は `src/main/java/local/cui/` の `Main`（起動）、`Material`（初期設定）、`Publisher`（HTTPS配信）、`Statements`（署名発行）、`Reader`（CUI・取得・保存）、`Verification`（検証）に分かれています。

`ReaderApi` がSwagger配信とAの操作APIを担当し、`Reader` と `Verification` を共用します。ブラウザー操作もAの立場からのみ行います。

規範・参照：[OpenID Federation 1.0 Final](https://openid.net/specs/openid-federation-1_0-final.html) のSections 3・4・8.1・9、[Nimbus Federation examples](https://connect2id.com/products/nimbus-oauth-openid-connect-sdk/examples/openid-connect/federation)。完全な仕様実装・適合認証を主張するものではありません。実測は [検証記録](../evidence/cui-verification.md) を参照してください。
