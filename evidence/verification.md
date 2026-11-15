# 検証記録

検証日：2026-11-15。初版プロトタイプ。ユーザーによる受け入れは未完了。

## Windowsネイティブ

- Windows ARM64、Microsoft OpenJDK 21.0.13、Maven 3.9.9。
- `mvn package`成功。JUnit 3テストメソッドで正常・異なるAnchor・改ざん・期限切れ・未登録鍵・JWT鍵不一致・正常への復旧を確認。
- ブラウザーで正常系、起点不一致、JWT鍵不一致、改ざん、未登録鍵と復旧の表示を確認。JSONと署名済みJWTを画面で展開できる構成。
- 素早い条件変更で履歴の名前が実験条件とずれる不具合を発見し、要求開始時の条件を保持するよう修正。

## WSL / Docker

- Ubuntu 24.04.3 LTS、aarch64、Docker 28.4.0、Compose v2.39.2。
- 初回のWSL起動はHCS_E_CONNECTION_TIMEOUT。再開後は起動でき、Dockerビルド検証を開始。
- ベースイメージはDockerfileでOCI index digestを固定。双方にlinux/amd64・linux/arm64のmanifestがあることを確認。amd64実行は未検証。
- Composeビルド・起動成功。コンテナ内でJUnit 3件成功。HTTPスモークテスト7ケース（正常、起点不一致、改ざん、期限切れ、未登録鍵、JWT鍵不一致、正常復旧）すべて成功。
- WindowsブラウザーからWSL上のDocker版へ接続し、正常な4段階の検証成功と署名文書の展開表示を確認。
- 既存Docker環境の自動サブネット割当が枯渇していたため、他のネットワークを削除せず専用サブネットを設定。LAB_SUBNETで変更可能。
- 新規clone検証は後述。

## 範囲

公開APIは教材内で生成する固定形式の文書だけを扱う。外部入力に対する汎用OIDF検証器の適合性テストではない。表示上の成功に加え、サーバー側のNimbus検証の結果を確認する。
