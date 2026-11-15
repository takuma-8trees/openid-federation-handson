# OpenID Federation：Entity AからTrust Chainを確認する

**今回のハンズオンは [cui/README.md](cui/README.md) です。Swagger UIから操作できます。**

Entity Aの立場でBの標準Endpointを直接叩き、JWTのauthority_hintsを読んでAnchorへ進み、Subordinate Statementを取得します。3文書を並べてから、保存したJWTの署名を検証します。Cでも同じ操作を行い、Anchorの404/not_foundを観察します。

```sh
git clone https://github.com/takuma-8trees/openid-federation-handson.git
cd openid-federation-handson/cui
docker compose up --build --wait -d
```

[Swagger UI（localhost:18766）](http://localhost:18766/) を開きます。表示は **GET `/.well-known/openid-federation` と GET `/fetch` の2種類だけ**。EC操作のServersでB → Anchorを選び、それぞれのJWTを読んでからfetchのsubにBのEntity IDを入力します。

ブラウザー用には各サービスのローカルHTTP入口（B:18767、C:18768、Anchor:18769）を使います。HTTP入口自体はOIDFのHTTPS要件を満たしません。正式なEntity IDは内部HTTPSで、Aからcurlで直接叩く手順もあります。[JWTデコード補助](http://localhost:18766/jwt.html)はブラウザー内で動きます。Aによる取得代行APIは削除しました。

| 資材 | 用途 |
| --- | --- |
| `cui/` | 今回の4サービス版。SwaggerからB/C/Anchorの標準Endpointへ直接GET。保存したJWTをAで検証 |
| ルートの `src/`・`compose.yaml` | 以前のUI試作。単一プロセスの別シナリオ。今回の手順では使わない |
| [docs/ui-prototype.md](docs/ui-prototype.md) | 以前のUI試作の手順 |
| [evidence/standard-endpoints.md](evidence/standard-endpoints.md) | 標準Endpoint版の実測・検証記録 |

旧UI試作（18765）と今回のSwagger版（18766）はComposeプロジェクト・コード・鍵・ボリューム・ネットワークを共有しません。**今回の操作は必ず `cui/` に移動してから実行してください。**

提供された参考実装のNimbusによる署名・チェーン検証を参考に、教材として独立実装しています。規範は [OpenID Federation 1.0 Final](https://openid.net/specs/openid-federation-1_0-final.html)。仕様全体への適合認証を意味しません。依存ライブラリは [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) を参照してください。
