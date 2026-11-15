# CUI版の検証記録

実測日：2026-11-15。旧UI版の [verification.md](verification.md) とは別の記録です。

## Swagger UI追加（2026-11-15）

Entity AにSwagger UI 5.34.1とOpenAPI 3.0.3の教材用操作APIを追加。Aだけをlocalhost:18766で公開し、B/C/Anchorは内部HTTPSのままです。以下の既存CUI検証結果は追加前の記録で、当時はホスト公開ポートなしでした。

- 暗号検証のJUnit 7件：成功。既存CUIのsmoke：成功。
- `run/smoke-api.sh`：Bの取得・未検証表示・200/TRUSTED、Cの取得・欠損表示・422/NOT_TRUSTEDを確認。
- APIが返すclaimsのiss/sub、Cのnot_found、取得・表示・検証でfetched_atが変わらないことをassert。
- 不正な対象は404、GETによるfetchは405を確認。
- ブラウザーのSwagger UIで4操作の表示、Try it out、Bのfetchの200/UNVERIFIED、Cのverifyの422/MISSING_SUBORDINATEを実際に確認。
- Swagger資材は公式npm配布5.34.1をSHA-512検証してビルドへ同梱。ブラウザー側で外部CDNを使わない構成。
- Swagger追加版の新規clone再現：実施後に追記。

## 実行環境

- WindowsホストのWSL、Ubuntu 24.04.3 LTS、ARM64。
- Docker Engine 28.4.0 / Docker Compose v2.39.2。
- Maven 3.9.9、Java 21、Nimbus OAuth 2.0 / OIDC SDK 11.38.2、JUnit Jupiter 5.11.4。
- ベースイメージdigestは `cui/Dockerfile` に固定。amd64での実行は未確認。

## 実測結果

`cui/` で `docker compose up --build -d` と `sh run/smoke-test.sh` を実行。

| 確認 | 結果 |
| --- | --- |
| コンテナ分離 | A・B・C・Anchorが別コンテナ。initは終了コード0。ホスト公開ポートなし |
| Bの取得 | B EC、Anchor EC、Anchor発行B Statementの3回がHTTPS 200 |
| Bの構造表示 | leafはiss=sub=B、subordinateはiss=Anchor/sub=B、anchorはiss=sub=Anchor |
| Bの検証 | `TRUSTED: https://entity-b:8443 -> https://trust-anchor:8443`、終了コード0 |
| Cの取得 | C ECとAnchor ECは200、AnchorへのC Statement要求は404/not_found |
| Cの検証 | 自己署名PASSの後に `NOT_TRUSTED: MISSING_SUBORDINATE`、終了コード2 |
| サーバーログ | B/CにECのGET、AnchorにECとfetchのGETが到達していることを確認 |
| デコード詳細 | `chain show B --json` でheader、claims、compact JWTを表示 |
| 取得と検証の分離 | B/C/Anchorを停止したまま `chain verify B` が成功。再取得せず保存済み文書を検証 |
| 停止からの復旧 | B/C/Anchorを再起動。initが既存の鍵を保持して終了 |

ビルド中のJUnit：**7 tests / 0 failures / 0 errors / 0 skipped**。

- Bの正常チェーン
- Cの正しい自己署名と欠けたSubordinate Statement
- 署名後のメタデータ改ざん
- 信頼起点の公開鍵不一致
- 有効期限切れ
- 自己署名は正しいがAnchorが裏付けていない鍵
- 未対応Metadata Policyを黙って無視せず拒否

## 観測出力（抜粋）

```text
GET https://entity-b:8443/.well-known/openid-federation -> HTTP 200
GET https://trust-anchor:8443/.well-known/openid-federation -> HTTP 200
GET https://trust-anchor:8443/fetch?sub=https%3A%2F%2Fentity-b%3A8443 -> HTTP 200
COLLECTED: 3文書を保存しました。信頼の検証はまだ行っていません。
TRUSTED: https://entity-b:8443 -> https://trust-anchor:8443

GET https://entity-c:8443/.well-known/openid-federation -> HTTP 200
GET https://trust-anchor:8443/.well-known/openid-federation -> HTTP 200
GET https://trust-anchor:8443/fetch?sub=https%3A%2F%2Fentity-c%3A8443 -> HTTP 404
PASS: Entity Configurationの自己署名・iss/sub・有効期限（所属の証明ではない）
PASS: AnchorのEntity ConfigurationをAに事前設定した公開鍵で検証
NOT_TRUSTED: MISSING_SUBORDINATE: Anchorが対象について発行したStatementがない
```

## 新規cloneからの再現

GitHubの [4f9b58ac0207198142cd5bd7cf2a02cfcbcbf468](https://github.com/takuma-8trees/openid-federation-handson/tree/4f9b58ac0207198142cd5bd7cf2a02cfcbcbf468/cui) を別ディレクトリへcloneし、READMEのビルド・起動・B/Cの操作を `run/smoke-test.sh` で再現。**成功**。

既存実行環境と分離するため、`COMPOSE_PROJECT_NAME=oidf-cui-replay`、`CUI_SUBNET=10.203.189.0/24` を設定した以外は追加入力なし。専用の新規ボリュームで鍵が生成され、作業用環境とはAnchor thumbprintが異なることも確認。再現用コンテナとネットワークは `docker compose down` で停止・削除済み。元のCUI環境と旧UI環境は保持。

## 残る確認

- 読者としての操作感：ユーザーの受け入れ確認はこれから。
- 仕様全体への適合性、任意の外部Federationとの相互接続、OIDCフローは検証対象外。
