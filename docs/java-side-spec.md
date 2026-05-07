# KoiYurePublic Java側 仕様書（現行実装ベース）

## 1. 目的

このドキュメントは、`app/src/main/java/com/example/koiyurepublic` 配下の Java 実装の責務・入出力・連携仕様を明確化し、WebView 側再構築時の参照基準にすることを目的とする。

---

## 2. 構成要素

- `SpinalCord`  
  フォアグラウンド Service。本アプリの中枢。受信・通知・TTS・UI連携・自己復旧を統括。

- `P2PQuakeWebSocketClient`  
  `wss://api.p2pquake.net/v2/ws` への接続管理（再接続付き）。

- `P2PConverts`  
  受信 JSON を表示/通知/TTS 用文面へ変換。

- `NotifiConnection`  
  通知チャンネル作成・通知発行・通知キャンセル。

- `TTSConnection`  
  Android TTS の初期化・読み上げ・緊急割り込み読み上げ。

- `LocalWebSocketServer`  
  `ws://127.0.0.1:9001` のローカルWSサーバー。Java→WebView高速配信。

- `MainActivity`  
  WebViewホスト。`AndroidBridge`（`@JavascriptInterface`）を公開。

- `EpspArea`  
  `assets/accompanying/MYepsp-area.csv` から地域コード辞書を構築。

- `BootReceiver` / `WatchdogReceiver`  
  端末再起動復帰・定期生存監視（Alarm連鎖）によるサービス復旧。

---

## 3. ランタイムフロー

1. `MainActivity` 起動時、`SpinalCord` が未起動なら起動。  
2. `SpinalCord.onCreate()` で以下を実行。  
   - Foreground通知開始  
   - WakeLock取得  
   - Watchdog設定  
   - `EpspArea` 初期化  
   - `TTSConnection` / `NotifiConnection` 初期化  
   - `P2PQuakeWebSocketClient` 接続開始  
   - `LocalWebSocketServer` 起動
3. 受信メッセージ到着時（`onMessage`）  
   - `code` 抽出  
   - `P2PConverts` で文面生成  
   - 通知発行（必要に応じて取消通知処理）  
   - TTS読み上げ（EEW系は割り込み）  
   - UIコールバック送信  
   - Local WS で配信
4. 切断時はWSクライアントが指数バックオフ再接続。  
5. Service異常停止時は Watchdog / 自己再起動 / BootReceiver で復旧。

---

## 4. WebView 連携仕様

## 4.1 JavaScriptInterface 名称

- インターフェース名: `AndroidBridge`
- 提供元: `MainActivity` 内 `JsBridge`

## 4.2 JavaScript → Java（呼び出し可能API）

- `notifyReady()`
- `startBackground()`
- `stopBackground()`
- `isServiceRunning(): boolean`
- `setTtsEnabled(enabled: boolean)`
- `isTtsEnabled(): boolean`
- `setTtsSpeechRate(rate: float)`
- `setTtsPitch(pitch: float)`
- `setNotificationEnabled(enabled: boolean)`
- `isNotificationEnabled(): boolean`
- `log(message: string)`

## 4.3 Java → JavaScript（`evaluateJavascript`）

- `window.onEarthquakeData(jsonString)`
- `window.onConnectionStateChanged(connected, willReconnect)`
- `window.onServiceStateChanged(running)`

## 4.4 Local WebSocket（`ws://127.0.0.1:9001`）メッセージ

- `{"type":"serviceStateChanged","running":boolean}`
- `{"type":"connectionStateChanged","connected":boolean,"willReconnect":boolean}`
- `{"type":"earthquakeData","data":<P2P JSON>}`
- `{"type":"ttsStatus","enabled":boolean}`
- `{"type":"notifStatus","enabled":boolean}`

---

## 5. P2P受信コード仕様（主要）

- `551`: 地震情報
- `552`: 津波予報
- `554`: EEW検出
- `555`: ピア情報
- `556`: 緊急地震速報（警報）
- `561`: 地震感知情報
- `9611`: 地震感知解析

文面生成は `P2PConverts.toFullMessage()` / `toBriefMessage()` が担当。

---

## 6. 通知仕様（`NotifiConnection`）

- チャンネル
  - `koiyure_eew`（高重要度）
  - `koiyure_quake`（高重要度）
  - `koiyure_info`（低重要度）
- 通知IDをコード別固定で上書き運用。
- 解除系
  - 津波解除時: `cancelTsunami()`
  - EEW取消時: `cancelEEW()`

---

## 7. TTS仕様（`TTSConnection`）

- 初期化完了前の `speak()` は内部キューへ積む。
- `speakNow()` は既存読み上げ中断＋優先再生。
- `setEnabled(false)` で停止・以降スキップ。
- `SpinalCord.onDestroy()` で `shutdown()` 必須。

---

## 8. 起動維持・復旧仕様

- `START_STICKY` 運用。
- `WakeLock` でCPUスリープ抑制。
- `WatchdogReceiver` が定期監視し停止時に再起動。
- `onTaskRemoved` / `onDestroy` で自己再起動スケジュール（意図停止時除外）。
- `BootReceiver` が再起動後に Service 自動起動。

---

## 9. 必要パーミッション（Manifest）

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_DATA_SYNC`
- `POST_NOTIFICATIONS`
- `RECEIVE_BOOT_COMPLETED`
- `WAKE_LOCK`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `SCHEDULE_EXACT_ALARM`
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` / `ACCESS_BACKGROUND_LOCATION`

---

## 10. 非機能・運用前提

- 最低SDK: 29
- 対象SDK: 36
- Java: 11
- WebSocketライブラリ: `org.java-websocket:1.5.6`
- 端末依存挙動（省電力制御）に備え、複数経路の復旧ロジックを採用。

---

## 11. 再構築時の互換必須ポイント

WebView 側を作り直す場合、以下の互換は最低限維持すること。

1. `AndroidBridge` のメソッド名・引数
2. Local WS の URL とメッセージ `type`
3. `onEarthquakeData / onConnectionStateChanged / onServiceStateChanged` コールバック名
4. P2P受信コード（551, 552, 554, 555, 556, 561, 9611）の意味

---

## 12. 既知課題（現行）

再構築または改修時に優先的に扱うこと。

1. JS側 `confidenceLabel` の判定順が不正（レベル1到達性の問題）
2. JavaとLocal WS経由の二重反映で重複表示が起こり得る
3. 地図用アセットの参照パス整合（`Item/` 参照）
4. JS内 `innerHTML` によるデータ挿入の安全性

