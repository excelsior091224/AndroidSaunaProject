# ビルド・開発環境

## 前提バージョン

| 項目                   | バージョン                            |
| ---------------------- | ------------------------------------- |
| AGP                    | 8.6.0                                 |
| Kotlin                 | 2.0.0                                 |
| KSP                    | 2.0.0-1.0.24                          |
| Room                   | 2.6.1                                 |
| Compose BOM            | 2024.09.00                            |
| Wear Compose           | 1.4.0                                 |
| Health Services Client | 1.1.0-alpha03 (安定版が無くalpha固定) |
| Play Services Wearable | 18.2.0                                |
| compileSdk / targetSdk | 35                                    |
| minSdk                 | wear=30、shared/mobile=26             |

## ビルド前の注意(重要)

システムのデフォルトJDKが25の環境では、Gradle 8.10.2 / AGPと非互換で
`BUILD FAILED: 25.0.3` という分かりにくいエラーになる。**必ずJDK17を明示すること。**

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew \
  :shared:assembleDebug :wear:assembleDebug :mobile:assembleDebug
```

## Android SDKの設定

- `local.properties` に `sdk.dir=<Android SDKのパス>` が必要。
- `compileSdk = 35` を使うため、`platforms;android-35` と `build-tools;35.0.0` を
  `sdkmanager` で追加インストール済み(このリポジトリの開発機では対応済み。新しい環境では
  `sdkmanager "platforms;android-35" "build-tools;35.0.0"` の実行とライセンス同意が必要)。

## 動作確認済み事項

- 2026-08-25時点で `shared` / `wear` / `mobile` の3モジュールとも `assembleDebug` が
  BUILD SUCCESSFULであることを確認済み。
- 実機・エミュレータでの起動確認、Health Servicesからの実心拍取得、Wearable Data Layerによる
  実際の同期動作は**未検証**(このドキュメント作成環境ではWear OS実機/エミュレータを操作できないため)。

## 既知のビルド上の注意点

- Compose Material3の`TopAppBar`はExperimental APIのため、使用箇所に
  `@OptIn(ExperimentalMaterial3Api::class)` が必要([HistoryScreen.kt](../mobile/src/main/kotlin/com/totonoi/sauna/mobile/ui/HistoryScreen.kt)で対応済み)。
