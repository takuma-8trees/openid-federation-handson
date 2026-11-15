# OpenID Federation：Entity AからTrust Chainを確認する

**今回のハンズオンは [cui/README.md](cui/README.md) です。Swagger UIから操作できます。**

Entity Aの立場でEntity Bの文書をHTTPSで取得し、Trust Chainの構成を見てから署名を検証します。同じ操作を、Trust Anchorに登録されていないEntity Cにも行います。

```sh
git clone https://github.com/takuma-8trees/openid-federation-handson.git
cd openid-federation-handson/cui
docker compose up --build -d
```

[Swagger UI（localhost:18766）](http://localhost:18766/) を開き、1〜4を順に実行します。CUIコマンドも引き続き利用できます。

| 資材 | 用途 |
| --- | --- |
| `cui/` | 今回の4サービス版。AのSwagger UI/APIをlocalhost:18766で公開。CUIも利用可能 |
| ルートの `src/`・`compose.yaml` | 以前のUI試作。単一プロセスの別シナリオ。今回の手順では使わない |
| [docs/ui-prototype.md](docs/ui-prototype.md) | 以前のUI試作の手順 |
| [evidence/cui-verification.md](evidence/cui-verification.md) | CUI版の実測・検証記録 |

旧UI試作（18765）と今回のSwagger版（18766）はComposeプロジェクト・コード・鍵・ボリューム・ネットワークを共有しません。**今回の操作は必ず `cui/` に移動してから実行してください。**

提供された参考実装のNimbusによる署名・チェーン検証を参考に、教材として独立実装しています。規範は [OpenID Federation 1.0 Final](https://openid.net/specs/openid-federation-1_0-final.html)。仕様全体への適合認証を意味しません。依存ライブラリは [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) を参照してください。
