# OpenID Federation：その署名鍵を、なぜ信頼できる？

ブラウザーで条件を変えながら、**信頼する起点からAの公開鍵までを検証し、AのJWTを受け入れる根拠を確かめる**ハンズオンです。
画面は教材用に作成したものです。結果は固定のアニメーションではなく、Nimbus SDKによる実際のRSA署名検証から返します。

## 起動

Docker EngineとDocker Compose v2、Gitが必要です。

```sh
git clone https://github.com/takuma-8trees/openid-federation-handson.git
cd openid-federation-handson
docker compose up --build -d
```

ブラウザーで **http://localhost:18765/** を開きます。初回はJava依存ライブラリの取得・テスト・ビルドを待ちます。ログは `docker compose logs lab`。
今回の実測・未確認事項は [evidence/verification.md](evidence/verification.md) を確認してください。
専用Dockerネットワークは既定で `10.203.187.0/24` です。既存ネットワークやVPNと重複する場合は、未使用の範囲を `LAB_SUBNET` 環境変数で指定してください。既存ネットワークの削除は不要です。

## 体験すること

### 1. あなたはBの担当者

Aのトークンを受け入れるために、まずAの署名鍵を信用してよいか確かめます。
画面上のTrust Anchorは「このFederation公開鍵はAのもの」と署名する役です。BはそのAnchorの公開鍵を別の経路で入手し、あらかじめ信頼しています。
この教材では参加審査と鍵の受け渡しは準備済みです。自己署名しただけで参加できるという意味ではありません。

### 2. 正常な情報を検証

初期値のまま **Bとして検証する** を押してください。

- 結果が「受け入れ」となり、4つの検証段階が成功します。
- Bが固定したAnchor鍵と、文書を発行したAnchor鍵のThumbprintが一致します。
- 「根拠となった文書」を開き、Aの文書の `iss` と `sub`、AnchorからAへの文書の `iss` と `sub` を見比べます。
- Aの文書の `jwks` はFederation用の鍵、`metadata.oauth_authorization_server.jwks` はトークン用の鍵です。2つの役割を区別してください。

**確認：Aが自分で鍵を公開したことに加え、どの文書がその鍵を裏付けていますか？**

### 3. Bの信頼する起点だけを変える

「Bが事前に信頼したAnchor公開鍵」を **別のAnchor鍵** にして検証します。
正常な文書を用意していても「拒否」となり、Anchor署名の検証で止まります。最初の2つのThumbprintも不一致になります。

**確認：届いた文書にAnchorの公開鍵が含まれているのに、なぜそれで受け入れないのでしょうか？**

チェーンに書かれた鍵をそのまま信頼してしまうと、第三者も自分でAnchorを名乗れるためです。「正常な条件に戻す」で復旧を確認します。

### 4. Aから届く情報を変える

Bの起点を正常に戻したうえで、各ケースを試します。

| 届く情報 | 期待する結果 | 学ぶこと |
|---|---|---|
| メタデータを署名後に改ざん | チェーン検証で拒否 | 読めるJSONでも、内容と署名が一致しない |
| Aの文書が期限切れ | 有効期間確認で拒否 | 過去に署名された情報を無期限に信用しない |
| AがAnchor未登録の鍵で署名 | チェーン検証で拒否 | Aの自己署名だけでは上位の承認にならない |
| 提示JWTだけを別の鍵で署名 | 最後のJWT検証で拒否 | 正常なチェーンがあっても、実際のJWTまで鍵が結び付く必要がある |

最後のケースではチェーン検証の段階までは成功します。「結果を比較する」と各文書を見て、どこまで確認でき、どこで止まったかを確かめてください。毎回その場で文書を生成し、結果の許可・拒否は暗号検証で決まります。

### 5. 正常に戻して終了

**正常な条件に戻す** を押し、再び受け入れになることを確認します。

```sh
docker compose down
```

鍵はサービスのメモリーだけに保持します。コンテナの再起動で鍵は再生成されます。履歴はブラウザー内だけに保持し、ページ再読み込みで消えます。永続volumeや既存IdP設定は使用しません。

## 構成と範囲

```text
ブラウザー → 教材用Javaサービス（localhost:18765）
               ├─ Anchor：Aについて署名する
               ├─ A：自身の情報とサンプルJWTを署名する
               └─ B：別途固定したAnchor鍵からNimbusで検証する
```

3つの**論理的な役割**を1プロセスで再現しています。実際に3組織へ分散したネットワーク構成ではありません。Entity IDはHTTPSの `.invalid` ドメインで、ネットワーク接続には使いません。画面へのlocalhost HTTP接続とEntity IDは別です。

OpenID Federation 1.0 Finalの直接Trust Chainに絞った、オフラインの教材プロファイルです。チェーンはAの自己署名文書、AnchorがAについて発行する文書、Anchorの自己署名文書の3つです。BはAnchorの公開鍵を別途固定します。
オンライン探索、メタデータポリシーの汎用評価、Trust Mark、自動クライアント登録、OIDCログイン、トークンの実発行サービス、即時失効、仕様全体への適合性検証は含みません。提示JWTはID Tokenではありません。
信頼チェーンが通ることは、業務上の参加資格やトークン中の主張の真実性を自動で保証しません。

公開APIは教材内で生成した固定形式のケースだけを受け付けます。任意のJWTを受け付ける汎用検証APIではありません。本番の認証・認可に使わないでください。

## 実装と検証

- Java 21 / Nimbus OAuth 2.0 / OIDC SDK **11.38.2**。
- `Lab.java`：実験用文書の生成と、検証段階の記録。`TrustChain.verifySignatures` を使用。
- `Main.java`：HTTP APIと画面配信。
- `index.html`：ブラウザーの操作・公開文書・結果比較。
- `LabTest.java`：正常、異なる起点、改ざん、期限切れ、未登録鍵、JWT鍵不一致、復旧。

Java 21とMaven 3.9.9で、Dockerを使わず確認することもできます。

```sh
mvn -B package
java -jar target/federation-lab-0.1.0.jar
# http://localhost:8080/
```

実行中のサーバーに対する確認：`sh run/smoke-test.sh http://localhost:18765`（curlが必要）。ブラウザーによる上の手順も併せて実施してください。

## 参考

- [OpenID Federation 1.0 Final / Trust Chain](https://openid.net/specs/openid-federation-1_0-final.html#section-4)
- [Nimbus公式のFederation機能](https://connect2id.com/products/nimbus-oauth-openid-connect-sdk/examples/openid-connect/federation)
- ユーザー提供のKeycloak/NimbusオフラインPoCを設計の参考にしました。元案件のMCP・CRUD・Keycloak拡張は同梱していません。[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
