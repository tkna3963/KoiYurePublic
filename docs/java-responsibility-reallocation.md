# Java責務再分配メモ

## 1. 目的

現在のアプリは動作しているが、`SpinalCord`、`MainActivity`、
`P2PConverts`に複数の責務が集まっている。今後の機能追加とテストを
容易にするため、通信、判定、配信、表示制御、設定を分離する。

この文書は、設計と実装の対応を記録する。移行後も、既存のイベント形式と
動作互換性を保った範囲で、実績を追記する。

## 2. 現状の責務

| 現在のファイル | 現在集まっている主な責務 | 問題 |
|---|---|---|
| `SpinalCord.java` | Foreground Service、P2P受信、JSON判定、通知、TTS、設定、LocalWS、WakeLock、Watchdog | 最大の責務集中。単体テストしにくい |
| `MainActivity.java` | WebView初期化、Service起動・停止・bind、Bridge、権限・電池最適化 | UI制御とService制御が混在 |
| `P2PConverts.java` | JSON解析、コード判定、短文・詳細文の生成 | 変換・判定・文章生成が一つに集中 |
| `P2PQuakeWebSocketClient.java` | 外部WebSocket接続、再接続、Listener通知 | 通信自体は分離済み。受信状態の管理を追加しやすい |
| `LocalWebSocketServer.java` | WebSocketサーバー、クライアント管理、状態スナップショット、イベントJSON生成 | 状態モデルと送信処理を分ける余地がある |
| `NotifiConnection.java` | 通知チャンネル、通知生成、通知取消 | ほぼ独立しており移動優先度は低い |
| `TTSConnection.java` | TTS初期化、キュー、割り込み、速度・音程 | ほぼ独立しており移動優先度は低い |
| `EpspArea.java` | CSV読み込み、地域検索 | データアクセスと地域判定を分けられる |
| `BootReceiver.java` | 起動完了時のService起動 | 薄いまま維持する |
| `WatchdogReceiver.java` | Watchdog発火、Service再起動 | 薄いまま維持する |

## 3. 移行後の構成案

```text
MainActivity
 ├─ WebViewController
 └─ MainActivityBridge

SpinalCord（Serviceのライフサイクルだけ）
 ├─ EarthquakeMessageCoordinator
 │   ├─ EarthquakeParser
 │   ├─ EarthquakePolicy
 │   └─ EarthquakeDeduplicator
 ├─ NotificationDispatcher
 ├─ TtsDispatcher
 ├─ LocalEventPublisher
 ├─ SettingsRepository
 └─ ServiceRuntimeController

P2PQuakeWebSocketClient ── P2PQuakeConnection
EpspArea ──────────────── AreaRepository / AreaMatcher
```

新しいクラスは、まず同じパッケージ
`com.example.koiyurepublic`に置く。機能が安定してから、
`data`、`domain`、`service`、`web`などのサブパッケージへ整理する。
先にパッケージを分けるとimport変更が増えるため、初回移行では行わない。

## 4. どこからどこへ移すか

### 4.1 `SpinalCord.java`

#### `EarthquakeMessageCoordinator.java`へ移す

- P2P受信JSONを受け取った後の処理順
- JSONからコードを取り出す処理
- 通知対象かどうかの判定呼び出し
- TTS対象かどうかの判定呼び出し
- 通知・TTS・LocalWSへ同じイベントを配る処理
- 津波解除・EEW取消などのイベント種別ごとの分岐

移行後の`SpinalCord`は、ServiceのListenerとして
`coordinator.handle(json)`を呼ぶだけにする。

#### `SettingsRepository.java`へ移す

- `SharedPreferences("koiyure_settings")`の取得
- 全体TTS設定
- 全体通知設定
- コード別TTS設定
- コード別通知設定
- TTS速度・音程設定
- 未保存キーを既定値`true`として扱う処理
- コード別設定のリセット

`SpinalCord`に設定値を直接読ませず、Repository経由で取得する。

#### `ServiceRuntimeController.java`へ移す

- WakeLockの取得・解放
- Watchdogの登録・解除
- Service状態の保存・通知
- 外部P2P接続の開始・停止
- Local WebSocketサーバーの開始・停止

ただしAndroidの`Service`ライフサイクルである`onCreate`、
`onStartCommand`、`onDestroy`自体は`SpinalCord`に残す。

#### `NotificationDispatcher.java`へ移す

- 通知の全体設定・コード別設定を確認した後の発行
- `NotifiConnection.notify()`の呼び出し
- EEW、津波、通常情報の通知種別の振り分け
- 津波解除・EEW取消のキャンセル呼び出し

`NotifiConnection`はAndroid Notification APIの低レベル実装として残し、
「通知するか」「何を通知するか」はDispatcherへ移す。

#### `TtsDispatcher.java`へ移す

- TTSの全体設定・コード別設定を確認した後の発話判断
- 通常読み上げと緊急割り込みの選択
- コード555、9611などの読み上げ除外条件
- 詳細文の読み上げ依頼

`TTSConnection`はTextToSpeechのキューや音声エンジン管理に集中させる。

#### `LocalEventPublisher.java`へ移す

- `LocalWebSocketServer.broadcast...()`の呼び出し
- Service状態、P2P接続状態、TTS状態、通知状態の配信
- 地震イベントのWebView配信

JSON文字列の形式は、最終的には`LocalEvent`などのイベントモデルで
管理する。最初の移行では既存の`LocalWebSocketServer` APIを呼ぶだけでもよい。

### 4.2 `P2PConverts.java`

#### `EarthquakeParser.java`へ移す

- JSON文字列の構文解析
- `code`、地震、津波、震度、震源などの値の抽出
- 欠損値・型違いの扱い

#### `EarthquakeMessageFormatter.java`へ移す

- `toBriefMessage`
- `toFullMessage`
- 通知用文章
- TTS用文章
- 表示用文章

#### `EarthquakePolicy.java`へ移す

- コードごとの通知対象判定
- コードごとのTTS対象判定
- EEW判定
- 津波解除・取消判定
- 低信頼度情報を除外する判定

最初から3クラスへ分割するのが大きすぎる場合は、まず
`P2PConverts`から`EarthquakePolicy`だけを分離する。

### 4.3 `MainActivity.java`

#### `WebViewController.java`へ移す

- WebView設定
- JavaScript有効化
- assetページの読み込み
- WebViewClient/WebChromeClient設定
- ページ再読み込み
- WebView破棄処理

#### `MainActivityBridge.java`へ移す

- `startBackground`
- `stopBackground`
- `isServiceRunning`
- TTS設定API
- 通知設定API
- コード別設定API
- Logcat出力API

BridgeはActivityへの参照を必要とするため、最初は
`MainActivity`の内部クラスとして切り出し、動作確認後に独立クラスへ移す。

`MainActivity`には、画面ライフサイクル、Service bind/unbind、
権限要求、BridgeとControllerの組み立てだけを残す。

### 4.4 `LocalWebSocketServer.java`

#### `LocalWebSocketState.java`へ移す

- `serviceRunning`
- `p2pConnected`
- `p2pWillReconnect`
- `ttsEnabled`
- `notificationEnabled`

#### `LocalEventSerializer.java`へ移す

- `serviceStateChanged`
- `connectionStateChanged`
- `earthquakeData`
- `ttsStatus`
- `notifStatus`のJSON生成

`LocalWebSocketServer`は、接続クライアント管理、onOpen/onClose、
送信、起動停止だけを担当する。

なお、現在の`Address already in use`調査に必要な起動状態ログは、
再分配後もServer側に残す。

### 4.5 `EpspArea.java`

#### `AreaRepository.java`へ移す

- `MYepsp-area.csv`のasset読み込み
- CSV行の解析
- 読み込みエラーの集計
- 地域データの保持

#### `AreaMatcher.java`へ移す

- 受信情報と地域データの照合
- 対象地域の抽出

CSVが小さく、当面は変更頻度も低いため、これは後半の移行でよい。

## 5. 移行後も既存ファイルに残す処理

### `SpinalCord.java`に残すもの

- `Service`のライフサイクル
- `onBind`
- Foreground通知の開始
- 依存クラスの生成と破棄
- Listener登録・解除
- Coordinatorへの受信委譲
- `onDestroy`の最終停止処理

### `MainActivity.java`に残すもの

- Activityライフサイクル
- WebViewとServiceの接続
- runtime permission
- Battery Optimization画面への遷移
- UIスレッドへの処理移送

### `LocalWebSocketServer.java`に残すもの

- WebSocketの`onOpen`、`onClose`、`onError`
- 接続クライアント集合
- サーバーの起動・停止
- 接続直後の状態送信

### `NotifiConnection.java`に残すもの

- NotificationChannel作成
- NotificationCompat.Builder
- 通知ID、色、優先度の低レベル設定
- cancel処理

### `TTSConnection.java`に残すもの

- TextToSpeech初期化
- Locale設定
- speak/stop/queue
- 速度・音程の適用
- shutdown

## 6. 推奨する移行順序

1. `SettingsRepository`を追加し、設定読み書きだけを移す。
2. `EarthquakePolicy`を追加し、通知/TTS判定を移す。
3. `EarthquakeMessageCoordinator`を追加し、`SpinalCord.onMessage`を薄くする。
4. `NotificationDispatcher`と`TtsDispatcher`を追加する。
5. `LocalEventPublisher`を追加する。
6. `MainActivityBridge`と`WebViewController`を分離する。
7. `P2PConverts`をParser/Formatterへ分割する。
8. Local WebSocketのState/Serializerを分離する。
9. `EpspArea`をRepository/Matcherへ分割する。
10. 安定後にサブパッケージを整理する。

各段階で、1回の変更ごとにDebug APKをビルドし、既存の動作を確認する。

## 7. 移行時の禁止事項

- ServiceとWebViewの通信経路を再び二重化しない
- `SpinalCord`から直接ActivityやWebViewを参照しない
- 通知・TTS判定をJavaScript側だけに移さない
- RepositoryとLocalStorageで設定の正本を二重にしない
- 移行と同時にイベントJSON形式を変更しない
- 大量のJSON全文をログに出さない
- `Address already in use`を握りつぶして起動成功扱いにしない

## 8. 移籍実績メモ

| 日付 | 移籍元 | 移籍先 | 移した処理 | 状態 |
|---|---|---|---|---|
| 2026-10-01 | `SpinalCord.java` | `SettingsRepository.java` | `koiyure_settings` の取得、全体TTS/通知、TTS速度・音程、コード別TTS/通知の読み書き、コード別設定リセット | 完了 |
| 2026-10-01 | `SpinalCord.java` / `P2PConverts.java` | `EarthquakePolicy.java` | コード抽出、通知/TTS全体・コード別可否、555/9611のTTS除外、554/556の割り込み、津波解除・EEW取消条件、通知タイトル | 完了 |
| 2026-10-01 | `SpinalCord.java` | `NotificationDispatcher.java` | 通知可否判定、短文生成、通知発行、津波/EEW通知キャンセルのオーケストレーション | 完了 |
| 2026-10-01 | `SpinalCord.java` | `TtsDispatcher.java` | TTS可否判定、詳細文生成、特殊除外、通常読み上げ/割り込みの選択 | 完了 |
| 2026-10-01 | `SpinalCord.java` | `EarthquakeMessageCoordinator.java` | 受信イベントのコード解析、通知/TTS/LocalWebSocketへの単一配信順序 | 完了 |
| 2026-10-01 | `SpinalCord.java` | `EarthquakeMessageCoordinator.java` | `onMessage` の詳細処理を委譲し、ServiceにはListenerとライフサイクル配線を残した | 完了 |

実際に移籍した場合は、次の形式で追記する。

```text
2026-10-01
移籍元: SpinalCord.java
移籍先: SettingsRepository.java
内容: SharedPreferencesの取得、全体設定とコード別設定の読み書き
確認: :app:assembleDebug 成功 / git diff --check 成功
```

## 9. 完了条件

- `SpinalCord`が受信処理の詳細を持たず、Coordinatorへ委譲している
- 通知判定、TTS判定、コード別設定判定が単体テスト可能である
- WebView再生成後にLocal WebSocketから状態を復元できる
- 通知、TTS、履歴、地図が同じイベントを二重処理しない
- Android Service停止時にすべての接続・WakeLock・TTSが解放される
- `:app:assembleDebug`が成功する
- 既存のLocal WebSocketイベント形式が維持される
