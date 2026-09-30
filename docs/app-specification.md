# KoiYure地震情報（公開版）アプリケーション仕様書

## 文書管理

| 項目 | 内容 |
|---|---|
| 文書名 | KoiYure地震情報（公開版）アプリケーション仕様書 |
| 対象 | `KoiYurePublic` Androidアプリ |
| 対象実装 | `app/src/main/java/com/example/koiyurepublic` |
| 作成基準 | 現行ソースコード、AndroidManifest、Gradle設定、WebView資産 |
| 文書の性質 | 実装仕様書兼システム仕様書 |
| 注意 | 「実装済み」と「今後の改善候補」を分けて記載する |

---

## 1. 目的と概要

### 1.1 アプリの目的

本アプリは、P2PQuake APIから地震関連情報をリアルタイムに受信し、Android端末上で
以下の方法により利用者へ伝達する防災情報アプリである。

- アプリ画面への詳細表示
- Androidプッシュ通知
- Android TextToSpeechによる音声読み上げ
- 震源・地域情報の地図表示
- 受信履歴の保存と閲覧
- バックグラウンドでの継続受信

画面には古明地こいしをモチーフとしたキャラクターUIを使用する。

### 1.2 アプリの特徴

本アプリは、画面を表示する`Activity`と、地震情報を常時受信する
`Foreground Service`を分離したハイブリッドアプリである。

```text
Android OS
├─ MainActivity
│  └─ WebView
│     ├─ Maindex.html
│     ├─ MainScript.js
│     ├─ LeafletConnection.js
│     ├─ Styles.css
│     └─ settings.html
│
└─ SpinalCord（Foreground Service）
   ├─ P2PQuakeWebSocketClient
   ├─ P2PConverts
   ├─ NotifiConnection
   ├─ TTSConnection
   ├─ LocalWebSocketServer
   ├─ EpspArea
   └─ Watchdog / BootReceiver
```

### 1.3 非対象

本仕様書では、P2PQuake API自体のサーバー実装、気象庁の発表仕様、
OpenStreetMapおよびNominatimのサービス内部仕様は規定しない。

---

## 2. 動作環境・ビルド仕様

### 2.1 Androidアプリ設定

| 項目 | 値 |
|---|---|
| Application ID | `com.example.koiyurepublic` |
| Namespace | `com.example.koiyurepublic` |
| minSdk | 29 |
| targetSdk | 36 |
| compileSdk | 36 |
| Java | 11 |
| versionCode | 1 |
| versionName | 1.0 |
| UI基盤 | AndroidX AppCompat / Material / ConstraintLayout |
| WebSocketライブラリ | Java-WebSocket 1.5.6 |
| Analytics | Firebase Analytics |

### 2.2 ビルド成果物

Debugビルドでは次のAPKが生成される。

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 2.3 アプリケーションラベル

Androidアプリ名は次のとおり。

```text
KoiYure地震情報(公開版)
```

---

## 3. 用語定義

| 用語 | 定義 |
|---|---|
| P2PQuake | 地震関連情報を配信する外部サービス/API |
| SpinalCord | 本アプリの常駐バックグラウンド処理を担うService |
| EEW | 緊急地震速報 |
| TTS | TextToSpeech。端末による音声読み上げ |
| WebView | HTML/CSS/JavaScriptを表示するAndroid UIコンポーネント |
| AndroidBridge | JavaScriptからAndroid Javaメソッドを呼び出すインターフェース |
| LocalWS | 端末内`localhost:9001`で動作するWebSocket |
| コード | P2PQuake JSONの`code`フィールド |
| 全体設定 | TTSまたは通知機能全体のON/OFF |
| コード別設定 | 情報コード単位のTTSまたは通知のON/OFF |

---

## 4. システム構成

### 4.1 コンポーネント一覧

| コンポーネント | 実装クラス/ファイル | 主責務 |
|---|---|---|
| メインActivity | `MainActivity.java` | WebViewホスト、Bridge、Service接続 |
| 常駐Service | `SpinalCord.java` | 受信・通知・読み上げ・配信の統括 |
| 外部WSクライアント | `P2PQuakeWebSocketClient.java` | P2PQuake接続・再接続 |
| メッセージ変換 | `P2PConverts.java` | JSONの短文/詳細文変換 |
| Android通知 | `NotifiConnection.java` | 通知チャンネル・通知発行 |
| TTS | `TTSConnection.java` | 日本語読み上げ |
| アプリ内WSサーバー | `LocalWebSocketServer.java` | Service→WebViewイベント配信 |
| 地域データ | `EpspArea.java` | 地域コード、地域名、座標の変換 |
| 起動Receiver | `BootReceiver.java` | 端末起動後のService開始 |
| 監視Receiver | `WatchdogReceiver.java` | Service生存確認、再起動 |
| WebViewメイン画面 | `Maindex.html` | 画面構造 |
| WebView処理 | `MainScript.js` | 受信・履歴・設定・状態表示 |
| WebView地図 | `LeafletConnection.js` | Leaflet地図・マーカー |
| WebView設定 | `settings.html` | 設定編集・LocalStorage保存 |

### 4.2 データ経路

```text
外部P2PQuake WebSocket
        │
        ▼
P2PQuakeWebSocketClient
        │ Listener
        ▼
SpinalCord.onMessage(json)
        ├─ code抽出
        ├─ コード別設定判定
        ├─ P2PConverts.toBriefMessage()
        │    └─ NotifiConnection
        ├─ P2PConverts.toFullMessage()
        │    └─ TTSConnection
        └─ LocalWebSocketServer
             └─ WebView JavaScript
```

地震情報のWebView配信はLocalWSに一本化する。`evaluateJavascript()`による
地震データの直接配信は行わず、履歴や地図が同じ受信データを二重処理しないことを
保証する。

---

## 5. AndroidManifest仕様

### 5.1 権限

| 権限 | 用途 |
|---|---|
| `INTERNET` | 外部P2PQuake、地図、住所変換通信 |
| `ACCESS_NETWORK_STATE` | ネットワーク状態参照 |
| `FOREGROUND_SERVICE` | 常駐Service |
| `FOREGROUND_SERVICE_DATA_SYNC` | dataSync型Foreground Service |
| `POST_NOTIFICATIONS` | Android通知 |
| `RECEIVE_BOOT_COMPLETED` | 端末起動後のService起動 |
| `WAKE_LOCK` | CPUスリープ抑制 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | バッテリー最適化除外誘導 |
| `SCHEDULE_EXACT_ALARM` | Watchdog・自己再起動Alarm |
| `ACCESS_FINE_LOCATION` | 高精度現在地 |
| `ACCESS_COARSE_LOCATION` | おおよその現在地 |
| `ACCESS_BACKGROUND_LOCATION` | バックグラウンド位置情報用途 |

### 5.2 Component

| Component | exported | 仕様 |
|---|---:|---|
| `MainActivity` | true | Launcher Activity |
| `SpinalCord` | false | `foregroundServiceType="dataSync"` |
| `WatchdogReceiver` | false | 内部Alarm専用 |
| `BootReceiver` | true | Boot/QuickBootを受信 |

`SpinalCord`は`stopWithTask="false"`であり、タスク一覧からActivityを消しても
Serviceが停止しない構成である。

### 5.3 ネットワークセキュリティ

`network_security_config.xml`により、端末内WebSocket接続に使用する`localhost`の
クリアテキスト通信を許可する。外部P2PQuake接続はTLS付き`wss`を使用する。

---

## 6. MainActivity仕様

### 6.1 役割

`MainActivity`は画面表示とユーザー操作の入口である。地震情報の常時受信、
通知発行、音声読み上げは`SpinalCord`が担当する。

### 6.2 `onCreate()`処理

1. Edge-to-edge表示を有効化する。
2. `activity_main.xml`を設定する。
3.システムバー分のInsetsをWebView親Viewへ適用する。
4. WebViewを取得する。
5. JavaScriptを有効化する。
6. ファイルアクセスを有効化する。
7. DOM Storageを有効化する。
8. `AndroidBridge`を登録する。
9. `file:///android_asset/Maindex.html`を読み込む。
10. Android 13以降で通知権限を要求する。
11. 初回のみバッテリー最適化除外を要求する。
12. Serviceが停止していれば起動する。
13. 戻るボタンをWebView履歴に対応させる。

### 6.3 `onStart()`処理

1. Serviceが動作していなければForeground Serviceとして起動する。
2. `bindService()`でServiceに接続する。
3. 接続完了後、`syncWebViewState()`を呼ぶ。

なお、接続直後の状態取りこぼしを防ぐため、実際の初期状態送信は
`LocalWebSocketServer.onOpen()`でも実行する。

### 6.4 `onStop()`処理

ActivityとServiceのBinder接続を解除する。Serviceは継続する。
これにより画面非表示中も通知・TTS・外部WebSocket受信を維持する。

### 6.5 AndroidBridge API

| API | 引数 | 戻り値 | 動作 |
|---|---|---|---|
| `notifyReady()` | なし | なし | ページ準備通知。現状は予約 |
| `startBackground()` | なし | なし | Service開始 |
| `stopBackground()` | なし | なし | 意図的停止、Watchdogキャンセル、Service停止 |
| `isServiceRunning()` | なし | boolean | Service実行状態 |
| `setTtsEnabled()` | boolean | なし | TTS全体設定 |
| `isTtsEnabled()` | なし | boolean | TTS全体設定取得 |
| `setNotificationEnabled()` | boolean | なし | 通知全体設定 |
| `isNotificationEnabled()` | なし | boolean | 通知全体設定取得 |
| `setTtsCodeEnabled()` | int, boolean | なし | コード別TTS設定 |
| `setNotificationCodeEnabled()` | int, boolean | なし | コード別通知設定 |
| `setTtsSpeechRate()` | float | なし | TTS速度 |
| `setTtsPitch()` | float | なし | TTSピッチ |
| `resetCodeSettings()` | なし | なし | コード別設定削除 |
| `log()` | String | なし | Logcat出力 |

---

## 7. SpinalCord仕様

### 7.1 役割

`SpinalCord`は本アプリの実行中枢であり、次の責務を持つ。

- P2PQuake WebSocketの開始と停止
- 受信JSONのコード判定
- 通知の発行
- TTSの実行
- WebViewへのイベント配信
- Foreground通知の更新
- WakeLock管理
- Watchdog管理
- Service停止後の自己再起動

### 7.2 Service状態

`isRunning`をvolatileな静的状態として保持する。

| 状態 | 意味 |
|---|---|
| true | Serviceの`onCreate()`後で、破棄前 |
| false | Service未起動または`onDestroy()`後 |

### 7.3 `onCreate()`初期化順序

初期化順序は機能依存を考慮して次のとおり。

1. Service状態をtrueに設定する。
2. LocalWSへService起動状態を通知する。
3. Foreground通知チャンネルを作成する。
4. Foreground通知を表示する。
5. Partial WakeLockを取得する。
6. Watchdogを設定する。
7. 地域CSVを読み込む。
8. TTSを生成する。
9. 通知管理を生成する。
10. TTS速度・ピッチを設定する。
11. P2PQuake Listenerに登録する。
12. 外部WebSocketへ接続する。
13. LocalWSサーバーを起動する。

### 7.4 `onMessage()`詳細仕様

#### 入力

P2PQuake WebSocketが受信したJSON文字列。

#### 処理

1. 受信ログを出力する。
2. JSONから`code`を抽出する。
3. `P2PConverts.toBriefMessage()`で短文を生成する。
4. 通知全体設定とコード別通知設定を判定する。
5. 通知対象なら`NotifiConnection.notify()`を呼び出す。
6. TTS全体設定とコード別TTS設定を判定する。
7. TTS対象なら詳細文を生成する。
8. EEW系コードなら` speakNow()`を呼び出す。
9. それ以外なら` speak()`を呼び出す。
10. 元JSONをLocalWSへ配信する。

#### 取消処理

- コード552かつ短文に「解除」が含まれる場合、津波通知をキャンセルする。
- コード556かつ短文に「取消」が含まれる場合、EEW通知をキャンセルする。

### 7.5 接続状態イベント

`onConnected()`では次を行う。

- 内部接続状態をconnected=trueへ更新
- Foreground通知を「接続済み」に更新
- LocalWSへ接続済みイベントを配信

`onDisconnected(willReconnect)`では次を行う。

- 内部接続状態をconnected=falseへ更新
- 再接続予定に応じてForeground通知を更新
- LocalWSへ切断/再接続イベントを配信

---

## 8. P2PQuake WebSocketクライアント仕様

### 8.1 接続先

```text
wss://api.p2pquake.net/v2/ws
```

### 8.2 Listenerイベント

| イベント | 発生条件 |
|---|---|
| `onMessage(json)` | メッセージ受信 |
| `onConnected()` | WebSocket onOpen |
| `onDisconnected(willReconnect)` | 切断または再接続判断 |

Listenerは`CopyOnWriteArrayList`で管理する。

### 8.3 再接続方式

| 項目 | 値 |
|---|---:|
| 初回再接続待ち | 3秒 |
| 最大再接続待ち | 60秒 |
| 再接続回数上限 | 無制限 |
| 待ち時間 | `3秒 × 2^回数`を60秒で上限 |
| 手動切断 | 再接続しない |

再接続には単一スレッドのScheduledExecutorServiceを使用する。

---

## 9. P2PConverts仕様

### 9.1 目的

P2PQuake JSONを、ユーザー向け日本語テキストへ変換する。

### 9.2 変換API

| API | 用途 |
|---|---|
| `toBriefMessage(json)` | 通知、短いステータス表示 |
| `toFullMessage(json)` | TTS、詳細情報 |

### 9.3 対応コード

| コード | 識別名 | 内容 |
|---:|---|---|
| 551 | JMAQuake | 気象庁地震情報 |
| 552 | JMATsunami | 津波予報 |
| 554 | EEWDetection | 緊急地震速報検出 |
| 555 | Areapeers | 地域別ピア情報 |
| 556 | EEW | 緊急地震速報 |
| 561 | Userquake | ユーザー地震感知 |
| 9611 | UserquakeEvaluation | 感知情報解析結果 |

JSON解析失敗時は、短文/詳細文それぞれの規定エラーメッセージを返す。

---

## 10. 通知仕様

### 10.1 通知チャンネル

| ID | 名称 | 対象 | 重要度 |
|---|---|---|---|
| `koiyure_eew` | 緊急地震速報・大津波警報 | 556、554、大津波 | HIGH/MAX |
| `koiyure_quake` | 地震情報・津波予報 | 551、552 | HIGH |
| `koiyure_info` | 地震感知・接続情報 | 555、561、9611 | LOW〜DEFAULT |

### 10.2 通知ID

| ID | 対象 |
|---:|---|
| 100 | EEW |
| 101 | EEW検出 |
| 200 | 地震情報 |
| 201 | 津波予報 |
| 300 | 地震感知解析 |
| 400 | 一般情報 |

### 10.3 通知判定順

```text
全体通知設定がOFF
    └─ 発行しない
全体通知設定がON
    └─ コード別通知設定がOFF
        └─ 発行しない
    └─ コード別通知設定がON
        └─ コードに応じたチャンネル・優先度で発行
```

コード別設定が未保存の場合はONとして扱う。

### 10.4 特殊通知

- EEWは赤色・高優先度・警報カテゴリ。
- 大津波警報はEEWチャンネルを使用する。
- 津波解除時は既存の津波通知をキャンセルする。
- 信頼度が低い地震感知解析は通知しない。

---

## 11. TTS仕様

### 11.1 初期化

`TextToSpeech`を生成し、成功時に日本語ロケールを設定する。
日本語データが不足している場合は警告ログを出す。

### 11.2 読み上げ方式

| 情報 | メソッド | 動作 |
|---|---|---|
| 通常情報 | `speak()` | キューへ追加 |
| EEW | `speakNow()` | 現在の読み上げを停止して即時再生 |
| EEW検出 | `speakNow()` | 現在の読み上げを停止して即時再生 |
| コード555 | なし | 読み上げしない |
| 信頼度低9611 | なし | 読み上げしない |

TTS初期化完了前の要求は内部キューへ保存し、初期化後に処理する。

### 11.3 TTS設定

| 設定 | 初期値 | 推奨範囲 |
|---|---:|---:|
| 音声速度 | 1.0 | 0.5〜2.0 |
| ピッチ | 1.3 | 0.5〜2.0 |

---

## 12. WebView仕様

### 12.1 メイン画面

`Maindex.html`は次のUIを持つ。

- 現在時刻
- 最新地震情報本文
- 過去データ用ページバー
- 前へ/次へ/最新へボタン
- 受信件数
- 古明地こいしの吹き出し
- バックグラウンドService状態
- TTS設定
- 通知設定
- P2P接続状態
- 現在地
- 受信履歴
- 設定ページへのリンク
- 通常背景/地図切替ボタン

### 12.2 接続状態表示

| 内部状態 | メイン表示 | 詳細表示 |
|---|---|---|
| 接続済み | 接続済み | P2Pサーバーから受信中 |
| 再接続中 | 再接続中 | 接続が切れました。再試行中です |
| 未接続 | 未接続 | P2Pサーバーから受信できていません |
| 初期状態 | 接続状態を確認中 | P2Pサーバーから応答を待っています |

状態変更時には時刻を併記する。表示領域には`role="status"`と`aria-live`
を指定し、スクリーンリーダーにも状態変化を伝える。

### 12.3 LocalWSイベント

#### Service状態

```json
{
  "type": "serviceStateChanged",
  "running": true
}
```

#### P2P接続状態

```json
{
  "type": "connectionStateChanged",
  "connected": true,
  "willReconnect": false
}
```

#### 地震データ

```json
{
  "type": "earthquakeData",
  "data": {
    "code": 551
  }
}
```

#### TTS状態

```json
{
  "type": "ttsStatus",
  "enabled": true
}
```

#### 通知状態

```json
{
  "type": "notifStatus",
  "enabled": true
}
```

### 12.4 初期状態同期

LocalWSクライアントが接続すると、サーバーは次の状態をそのクライアントへ個別送信する。

1. Service実行状態
2. P2P接続状態
3. TTS全体設定
4. 通知全体設定

ActivityのBinder接続時にも状態同期を要求するが、接続タイミング競合を避けるため、
`LocalWebSocketServer.onOpen()`での個別再送を正とする。

---

## 13. LocalWebSocketServer仕様

### 13.1 サーバー

| 項目 | 値 |
|---|---|
| Bindアドレス | `127.0.0.1` |
| JavaScript接続URL | `ws://localhost:9001` |
| ポート | 9001 |
| クライアント管理 | `CopyOnWriteArraySet` |

### 13.2 状態スナップショット

サーバーは次の最新値をメモリ上に保持する。

- `serviceRunning`
- `p2pConnected`
- `p2pWillReconnect`
- `ttsEnabled`
- `notificationEnabled`

新規クライアント接続時に、このスナップショットを利用して初期状態を送信する。

### 13.3 クライアントからの受信

現在、JavaScriptからサーバーへの受信メッセージはログ出力のみであり、
コマンド処理は実装していない。Android操作はAndroidBridgeを使用する。

---

## 14. 設定仕様

### 14.1 設定保存先

画面設定はWebViewのLocalStorageへ保存し、Android側で実処理に必要な設定は
`SharedPreferences`へ保存する。

```text
SharedPreferences名: koiyure_settings
```

### 14.2 全体設定キー

| キー | 内容 |
|---|---|
| `ttsEnabled` | TTS全体ON/OFF |
| `notifEnabled` | 通知全体ON/OFF |

### 14.3 コード別設定キー

```text
code_tts_551
code_tts_552
code_tts_554
code_tts_555
code_tts_556
code_tts_561
code_tts_9611
code_tts_1112

code_notif_551
code_notif_552
code_notif_554
code_notif_555
code_notif_556
code_notif_561
code_notif_9611
code_notif_1112
```

未保存キーはtrueとして扱い、既存ユーザーの従来動作を維持する。

### 14.4 設定更新

設定ページで「保存」を押すと、次の処理を行う。

1. 全設定をLocalStorageへ保存する。
2. 全体TTS/通知設定をAndroidBridgeへ送る。
3. 8種類のコード別TTS設定をAndroidBridgeへ送る。
4. 8種類のコード別通知設定をAndroidBridgeへ送る。
5. TTS速度・ピッチをAndroidBridgeへ送る。

「リセット」ではLocalStorageを削除し、Android側のコード別キーも削除する。

---

## 15. EpspArea仕様

### 15.1 入力ファイル

```text
assets/accompanying/MYepsp-area.csv
```

CSV列は次の構成を想定する。

```text
文字列型コード, 数値型コード, 地方, 都道府県, 地域,
緯度, 経度, mysub_code, mysub_name
```

### 15.2 Entry

| フィールド | 内容 |
|---|---|
| `code` | 地域コード |
| `region` | 地方 |
| `pref` | 都道府県 |
| `area` | 地域 |
| `latitude` | 緯度 |
| `longitude` | 経度 |
| `subCode` | 細分コード |
| `subName` | 細分名 |

### 15.3 公開操作

- `init(context)`：CSVを一度だけ読み込む
- `get(code)`：Entry取得
- `nameOf(code)`：表示名取得
- `latLonOf(code)`：座標取得
- `all()`：全データ取得

---

## 16. 位置情報・地図仕様

### 16.1 現在地

JavaScriptのGeolocation APIで緯度・経度を取得し、Nominatimの逆ジオコーディングで
都市・町・村名を取得する。取得結果は画面に表示し、地図上へ現在地マーカーを置く。

### 16.2 地図

Leafletを利用し、OpenStreetMapタイルを表示する。

表示対象は次のとおり。

- 現在地
- コード551の震源
- コード9611の地域別感知情報
- 都道府県・細分・津波地域のGeoJSON境界

### 16.3 地図モード切替

通常モードではキャラクター背景を表示する。
地図モードでは背景を非表示にし、Leaflet地図を表示する。

地図は初回切替時に初期化し、その後は既存地図を再利用する。

---

## 17. Receiver・復旧仕様

### 17.1 BootReceiver

以下のIntentを受信した場合、`SpinalCord`を起動する。

- `BOOT_COMPLETED`
- `QUICKBOOT_POWERON`
- `com.htc.intent.action.QUICKBOOT_POWERON`

Android 8.0以降では`startForegroundService()`を使用する。

### 17.2 WatchdogReceiver

`SpinalCord.WATCHDOG_INTERVAL_MS`（60,000ms）ごとに起動する。

1. Serviceの生存を確認する。
2. 停止していればServiceを起動する。
3. 次のWatchdog Alarmを再登録する。

`setExactAndAllowWhileIdle()`による連鎖Alarm方式を採用する。

### 17.3 自己再起動

`SpinalCord.onTaskRemoved()`または`onDestroy()`から約1.5秒後の再起動を登録する。
`stopIntentionally()`が呼ばれた場合は自己再起動しない。

---

## 18. 状態遷移

### 18.1 Service状態

```text
未起動
  │ MainActivity / BootReceiver / WatchdogReceiver
  ▼
起動要求
  ▼
onCreate
  ▼
稼働中
  ├─ onTaskRemoved → 再起動Alarm
  ├─ onDestroy → 再起動Alarm
  └─ stopIntentionally → 意図的停止
```

### 18.2 外部P2P接続状態

```text
未接続
  ├─ connect成功 → 接続済み
  └─ 接続失敗 → 再接続待ち
                    └─ 接続済み

接続済み
  └─ 切断 → 再接続中
              └─ 接続済み
```

### 18.3 WebView LocalWS状態

```text
未接続
  └─ WebSocket onOpen
       ├─ 現在状態を4種類受信
       └─ イベント配信を受信
```

---

## 19. エラー処理

| 事象 | 現行動作 |
|---|---|
| 外部WS接続失敗 | ログ出力、再接続 |
| 外部WS切断 | 接続状態を通知、再接続 |
| JSON解析失敗 | 変換クラスのエラー文を利用 |
| TTS初期化失敗 | エラーログ |
| 日本語TTS不足 | 警告ログ |
| 通知Manager取得失敗 | 警告ログ |
| AlarmManager取得失敗 | 警告ログ |
| LocalWS送信失敗 | 送信失敗ログ |
| 地域CSV読込失敗 | エラーログ、空テーブル |
| 位置情報拒否 | WebViewに権限要求/エラー表示 |
| 地図初期化失敗 | JavaScriptコンソールへ出力 |

---

## 20. ログ仕様

主要なLogcatタグは次のとおり。

| TAG | 対象 |
|---|---|
| `MainActivity` | Activity/権限/起動 |
| `SpinalCord` | Service/受信/状態 |
| `P2PQuakeWS` | 外部WebSocket |
| `LocalWebSocketServer` | LocalWS |
| `NotifiConnection` | 通知 |
| `TTSConnection` | 音声 |
| `EpspArea` | 地域CSV |
| `BootReceiver` | 起動Receiver |
| `WatchdogReceiver` | Watchdog |
| `WebView/JS` | JavaScriptログ |

受信JSONは先頭部分をデバッグログへ出力する。公開版では個人情報や過剰なデータを
ログへ残さない運用を推奨する。

---

## 21. セキュリティ・プライバシー

### 21.1 通信

- 外部P2PQuakeは`wss`を使用する。
- LocalWSはループバックアドレスに限定する。
- `localhost`のクリアテキスト許可は端末内通信のために使用する。

### 21.2 WebView

JavaScriptを有効化し、AndroidBridgeを公開するため、WebViewへ読み込む資産は
信頼できるものに限定する。

Leaflet CDN、Nominatim、OpenStreetMap等の外部リソースを利用するため、
外部サービス障害時にもアプリ本体の通知機能は動作できる設計を維持する。

### 21.3 位置情報

現在地取得には位置情報が必要である。位置情報は地図・住所表示用途に使用する。
バックグラウンド位置情報が不要な場合は、権限を削減する余地がある。

---

## 22. 実装済み要件一覧

- [x] P2PQuake WebSocketへの接続
- [x] 無制限再接続
- [x] Foreground Service
- [x] Android通知
- [x] TTS読み上げ
- [x] EEW割り込み読み上げ
- [x] 端末再起動後のService起動
- [x] Watchdog Alarm
- [x] Service自己再起動
- [x] WebViewメイン画面
- [x] AndroidBridge
- [x] LocalWSによるイベント配信
- [x] LocalWS接続時の状態再送
- [x] コード別TTS設定
- [x] コード別通知設定
- [x] 受信履歴
- [x] 地図表示
- [x] 現在地表示

---

## 23. 既知の制約・改善候補

以下は現行実装の仕様上の制約であり、未実装の改善候補である。

1. WebView受信履歴に件数上限がなく、長時間運用でメモリ使用量が増加する。
2. LocalWSのクライアント→サーバーコマンド処理は未実装である。
3. 地図用GeoJSONファイルの配置状況をビルド前に確認する必要がある。
4. Leaflet CDNはバージョン固定またはassets同梱が望ましい。
5. Nominatimはレート制限・通信障害の影響を受ける。
6. Android実行時の位置情報権限要求を明示的に整備する余地がある。
7. TTS速度・ピッチを初期化完了後に確実に適用する余地がある。
8. LocalStorageとSharedPreferencesの設定管理を一本化する余地がある。
9. WebSocket再接続中のユーザー向け表示と最終受信時刻をさらに明確化できる。
10. 外部サービスから取得する内容をキャッシュする仕組みは未実装である。

---

## 24. テスト仕様

### 24.1 起動テスト

- 初回起動でWebViewが表示されること。
- ServiceがForegroundとして起動すること。
- 常駐通知が表示されること。
- LocalWSが9001番ポートで待機すること。
- P2P接続状態が画面に表示されること。

### 24.2 受信テスト

- コード551が通知、TTS、WebViewへ反映されること。
- コード552の津波解除で通知がキャンセルされること。
- コード556が通常読み上げを中断して読み上げられること。
- コード555がTTSされないこと。
- コード9611の信頼度低データが抑制されること。
- 同一データが履歴へ二重登録されないこと。

### 24.3 設定テスト

- TTS全体OFFで全コードの読み上げが止まること。
- 通知全体OFFで全コードの通知が止まること。
- 551のTTSだけOFFにして551のみ読み上げられないこと。
- 551の通知だけOFFにして551のみ通知されないこと。
- Serviceを再起動してもコード別設定が維持されること。
- リセットでコード別設定が既定値へ戻ること。

### 24.4 接続テスト

- 外部P2P接続失敗時に「未接続」または「再接続中」と表示されること。
- 外部WSが復旧したとき「接続済み」と表示されること。
- WebViewを再読み込みしてもLocalWS初期状態を受信できること。
- LocalWS接続が外部P2P接続状態と混同されないこと。

### 24.5 ライフサイクルテスト

- Activityを閉じても通知・TTSが継続すること。
- タスク一覧からActivityを消してもServiceが維持されること。
- 端末再起動後にServiceが起動すること。
- Watchdogで停止Serviceが再起動すること。
- ユーザーが停止操作した場合に自動再起動しないこと。

---

## 25. 代表的なシーケンス

### 25.1 通常の地震情報

```text
P2PQuake
  → P2PQuakeWebSocketClient.onMessage(json)
  → SpinalCord.onMessage(json)
  → code=551を抽出
  → コード別通知設定を確認
  → NotifiConnection.notify(551,...)
  → コード別TTS設定を確認
  → P2PConverts.toFullMessage(json)
  → TTSConnection.speak(text)
  → LocalWebSocketServer.broadcastEarthquakeData(json)
  → WebView履歴/本文/地図を更新
```

### 25.2 WebViewの再接続

```text
WebView JavaScript
  → ws://localhost:9001へ接続
  → LocalWebSocketServer.onOpen(conn)
  → Service状態を送信
  → P2P接続状態を送信
  → TTS状態を送信
  → 通知状態を送信
  → WebViewの各表示を初期化
```

---

## 26. 変更時の設計ルール

1. 地震データをWebViewへ追加配信する場合、LocalWS経路を使用し、
   `evaluateJavascript()`経路を復活させない。
2. 受信コードを追加する場合、`P2PConverts`、通知、TTS、WebView表示、
   設定画面の全てを同時に確認する。
3. 通知の緊急度を変更する場合、Notification Channelの重要度と振動、
   通知ID、通知キャンセル処理をまとめて確認する。
4. Serviceの停止処理を変更する場合、Watchdogと自己再起動の挙動を確認する。
5. AndroidBridgeへAPIを追加する場合、入力値の検証と公開範囲を検討する。
6. WebView資産を追加する場合、`file:///android_asset`からの相対パスを確認する。
7. LocalWSイベントを追加する場合、イベント名、JSON形、状態再送の扱いを仕様化する。

