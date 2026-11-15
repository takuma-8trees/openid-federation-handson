# 標準Endpointを直接叩く教材の検証

2026-11-15。従来の `/api/chains` で取得を代行するSwaggerは置き換え、削除した。

環境：WSL Ubuntu 24.04.3 LTS ARM64、Docker 28.4.0、Compose 2.39.2、Java21、Nimbus11.38.2、Swagger UI5.34.1。DockerベースイメージdigestとSwagger SHA-512はDockerfileに固定。

## 通信と検証

- AはSwagger/OpenAPI/JWTデコード補助の静的配信のみ。`/api/chains/B/fetch` は404。
- OpenAPIのpathsは `/.well-known/openid-federation` と `/fetch` の2種類。
- HTTPのB/C/Anchor入口へ直接リクエストし、ECは200、`application/entity-statement+jwt` の生JWTを確認。
- Bのauthority_hints、Anchorのfederation_fetch_endpoint、Subordinate Statementのiss=Anchor/sub=Bを確認。
- Anchorへのsub=C要求は404/not_found。subなしは400。
- Swagger originへのCORSヘッダーを確認。
- そのHTTP応答をファイルに保存し、Aの `chain verify-files` に渡した。Bは0/TRUSTED、Cは自己署名PASS後2/MISSING_SUBORDINATE。
- 回帰スクリプト：`cui/run/smoke-endpoints.sh`。読者はSwaggerで一回ずつ操作する。

- ブラウザーでBの標準EC Endpointを直接実行し、Request URLが18767、200、生JWT、Content-Typeを確認。JWTデコード補助にその値を貼り付け、authority_hintsを確認。
- ブラウザーでAnchorのfetchにsub=Cを入力し、Request URLが18769、404/not_foundのJSONを確認。
- 正式HTTPS curl手順：AのcurlでB EC・Anchor EC・BのSubordinate Statementを個別に取得。`--cacert`でホスト名・証明書を検証し、保存ファイルを `verify-files` で検証成功。
- JUnit：7件、failure/error/skippedはすべて0。
- 起動直後の接続失敗を避けるため各サービスにhealthcheckを追加し、READMEでは `up --build --wait -d` を使用。
- 新規clone：[7ba510657cd1318bc0bddbc0a79dec00bbf1052d](https://github.com/takuma-8trees/openid-federation-handson/tree/7ba510657cd1318bc0bddbc0a79dec00bbf1052d/cui) をGitHubからcloneして成功。新規プロジェクト `oidf-standard-replay`、新規ボリューム、subnet 10.203.189.0/24・10.203.191.0/24、ports18866〜18869に分離し、`up --build --wait -d` → `smoke-endpoints.sh` → Aから証明書検証付きHTTPS GETが成功。再現用コンテナとネットワークはdown済み。

## 境界

ブラウザー入口のHTTPはローカル教材用の追加入口であり、OIDFのHTTPS要件を満たすものとは扱わない。内部の正式なHTTPS Endpointと同じPublisherのハンドラーで応答する。JWTに書かれたHTTPSのEntity IDとブラウザーのHTTP接続先の対応をREADMEとSwaggerに明記した。完全な仕様適合性やOIDCフローは検証対象外。ユーザーの読者視点での受け入れ確認は未完了。
