## KoiYurePublic

KoiYurePublic は、P2PQuake の地震情報を受信し、通知・音声読み上げ・WebView 表示を行う Android アプリです。

## 開発環境

- JDK 17
- Android SDK（`compileSdk 36` / `minSdk 29`）
- Gradle Wrapper

## ビルド

```bash
bash ./gradlew build --no-daemon
```

## 主要ディレクトリ

- `app/src/main/java/com/example/koiyurepublic`: Android (Java) 実装
- `app/src/main/assets`: WebView 側アセット
- `docs/java-side-spec.md`: Java 側の仕様メモ
