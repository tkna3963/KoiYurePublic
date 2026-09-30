# WebView側でできること

## 1. 文書の目的

この文書は、KoiYure地震情報（公開版）におけるWebView側の担当範囲、
JavaScriptで実行できる処理、Androidネイティブ層との接続方法、制約および
デバッグ方法をまとめたものである。

WebView側はアプリ全体の通信中枢ではない。地震情報の受信、通知、TTS、
Foreground Serviceの維持はJava側が担当し、WebView側は主に画面表示、
ユーザー操作、表示用データの管理を担当する。

```text
Java Foreground Service
    ├─ P2PQuakeから受信
    ├─ 通知・TTSを実行
    └─ LocalWebSocketServer
          ↓
WebView / JavaScript
    ├─ 状態を表示
    ├─ 地震情報を画面・履歴・地図へ反映
    ├─ ユーザー操作を受け付ける
    └─ AndroidBridge経由で設定・Service操作を依頼
```

## 2. WebViewでできること

### 2.1 地震情報の表示

Local WebSocketから受信した`earthquakeData`イベントを画面に反映できる。

- 地震情報、津波情報、緊急地震速報などの表示
- 最新情報の自動表示
- 過去に受信した情報の一覧表示
- 前後の履歴への移動
- 最新情報への復帰
- 受信件数の表示
- コード別の表示ラベル
- 震源地、マグニチュード、最大震度などの概要表示
- キャラクターの吹き出し・反応の更新

現在の履歴はJavaScript配列`AllWebsocketData`に保持される。アプリを
終了すると原則として消える一時的な画面状態であり、現状は無制限に増加する
ため、長時間稼働時には上限管理が必要である。

### 2.2 接続状態の可視化

次の状態を画面に表示できる。

- P2PQuake外部WebSocketの接続済み
- P2PQuake外部WebSocketの未接続
- 再接続予定
- Foreground Serviceの稼働中・停止
- TTSの有効・無効
- 通知の有効・無効
- Local WebSocketの接続中・切断

接続状態は、`connectionStateChanged`および`serviceStateChanged`イベントを
受信して更新する。Local WebSocket接続直後には、現在の状態スナップショットが
Java側から再送されるため、WebViewの読み込みがService起動より遅くても初期状態を
取得できる。

### 2.3 地図表示

Leafletを利用して、WebView内に地図を表示できる。

- 震源位置のマーカー表示
- 感知地域の表示
- 都道府県・細分区域などのGeoJSON表示
- 津波関連区域の表示
- 現在地マーカーの表示
- 地震情報に応じた地図中心・表示内容の更新
- 地図表示の切り替え

地図タイル、Leaflet、住所変換サービス、GeoJSONの読み込みに失敗すると、
地図が表示できない場合がある。地図表示は防災情報の受信処理そのものとは分離
されているため、地図が失敗しても通知・TTS・履歴処理はJava側および他の画面
処理で継続する。

### 2.4 ユーザー操作

HTML/CSS/JavaScriptで、次のようなUI操作を実装できる。

- メニューの開閉
- 情報一覧の選択
- 履歴の前後移動
- 最新情報への移動
- 地図の表示切り替え
- Serviceの開始・停止
- TTSと通知の全体ON/OFF
- TTS速度・音程の変更
- コード別TTS・通知設定
- 現在地表示の要求
- 設定ページへの移動
- デバッグ情報の表示

### 2.5 設定画面

`settings.html`では、表示上の設定をLocalStorageへ保存し、実処理に影響する
設定をAndroidBridge経由でJava側へ送信する。

#### 全体設定

- TTSの有効・無効
- 通知の有効・無効
- TTS読み上げ速度
- TTS音程
- 地図自動更新
- WebView側のログレベル表示値

#### コード別設定

次のコードについて、TTSと通知を個別にON/OFFできる。

```text
551, 552, 554, 555, 556, 561, 9611, 1112
```

Java側の`SharedPreferences`が通知・TTS処理の正本である。LocalStorageは
設定画面の表示復元用であり、LocalStorageだけを変更してもバックグラウンドの
通知や読み上げ判定は変更されない。

設定をリセットすると、WebViewのLocalStorageとJava側のコード別設定が
リセットされる。

## 3. Local WebSocket連携

### 3.1 接続先

通常のアプリ内接続先は次の通り。

```text
ws://localhost:9001
```

これはAndroid端末内で動作する`LocalWebSocketServer`への接続である。
PC上のSandbox ServerやP2PQuake APIとは別の接続である。

### 3.2 JavaからWebViewへ届くイベント

| type | 主な内容 | WebView側の処理 |
|---|---|---|
| `serviceStateChanged` | Service稼働状態 | Service表示と操作ボタンを更新 |
| `connectionStateChanged` | P2PQuake接続状態 | 接続表示、再接続表示を更新 |
| `earthquakeData` | 地震情報本体 | 履歴、本文、地図、吹き出しを更新 |
| `ttsStatus` | TTS設定状態 | TTSトグルを更新 |
| `notifStatus` | 通知設定状態 | 通知トグルを更新 |

地震データは次の形式で受信する。

```json
{
  "type": "earthquakeData",
  "data": {
    "code": 551
  }
}
```

WebView側は受信JSONを`JSON.parse()`し、`handleLocalWsMessage()`から
イベント種別ごとの処理へ振り分ける。

### 3.3 再接続

Local WebSocketが切断された場合、JavaScript側は指数バックオフで再接続する。
待ち時間は概ね1秒、2秒、4秒、8秒、最大10秒で、現在の実装では再接続試行を
最大5回まで行う。

再接続失敗時は、以下をLogcatへ出力する。

```text
[LocalWS] エラー
[LocalWS] 切断 code=... reason=...
```

## 4. AndroidBridgeで呼び出せる機能

WebViewからJavaへ公開されているインターフェース名は`AndroidBridge`である。
JavaScript側は、AndroidBridgeが存在しないブラウザ環境でも動作できるよう、
利用前に存在確認を行う。

| JavaScript API | 用途 |
|---|---|
| `AndroidBridge.startBackground()` | Foreground Serviceを起動 |
| `AndroidBridge.stopBackground()` | Foreground Serviceを停止 |
| `AndroidBridge.isServiceRunning()` | Service稼働状態を取得 |
| `AndroidBridge.setTtsEnabled(boolean)` | TTS全体設定を変更 |
| `AndroidBridge.isTtsEnabled()` | TTS全体設定を取得 |
| `AndroidBridge.setTtsSpeechRate(float)` | TTS速度を変更 |
| `AndroidBridge.setTtsPitch(float)` | TTS音程を変更 |
| `AndroidBridge.setNotificationEnabled(boolean)` | 通知全体設定を変更 |
| `AndroidBridge.isNotificationEnabled()` | 通知全体設定を取得 |
| `AndroidBridge.setTtsCodeEnabled(int, boolean)` | コード別TTS設定を変更 |
| `AndroidBridge.setNotificationCodeEnabled(int, boolean)` | コード別通知設定を変更 |
| `AndroidBridge.resetCodeSettings()` | コード別設定をリセット |
| `AndroidBridge.log(String)` | Android Logcatへログを出力 |

Bridge呼び出しはJava側のMain Handlerへ渡される。JavaScriptから呼び出した
直後にService状態が変わるとは限らないため、結果はLocal WebSocketの状態イベント
または画面状態の更新で確認する。

## 5. JavaScriptから使えるWeb API

WebView内のJavaScriptでは、標準的なWeb APIも利用できる。ただし、Android
WebViewのバージョン、権限、端末設定、ネットワーク状態の影響を受ける。

- `WebSocket`
- `localStorage`
- DOM API
- タイマー（`setTimeout`、`setInterval`）
- `navigator.geolocation`
- `fetch`
- `JSON`
- Leafletの地図API
- ページ遷移・履歴API

### 5.1 位置情報

位置情報は、端末の実行時権限と位置情報設定が必要である。権限がない場合や
取得に失敗した場合は、現在地マーカーや住所表示を利用できない。

### 5.2 ネットワーク

外部CDN、地図タイル、Nominatimなどへのアクセスは、インターネット接続、
Androidの通信設定、サービス側の応答に依存する。WebViewのLocal WebSocketは
通常の外部ネットワークとは異なり、端末内のJavaサーバーとの通信である。

## 6. WebView側でできないこと・Java側に任せること

WebView側だけでは、次の処理を信頼性高く担当できない。

- アプリが閉じた後の地震情報の常時受信
- Foreground Serviceの維持
- Android通知の発行
- Android TextToSpeechの確実な実行
- WakeLockやWatchdogの管理
- 端末再起動後の自動復旧
- Androidの通知権限・バッテリー最適化の管理
- 通知・TTSのコード別判定の正本管理

WebViewは画面が破棄・再生成される可能性があるため、防災情報の受信状態を
WebViewのJavaScript変数だけで管理してはいけない。受信・通知・TTS・設定の
重要状態はJava側で保持し、WebViewはLocal WebSocketで現在状態を再同期する。

## 7. JavaScriptから直接実装できる改善

### 7.1 表示性能

- 履歴配列とDOMの最大件数を設定する
- 古い情報を画面から削除する
- 大きなJSONを画面ログへ出さない
- 地図更新を必要な場合だけ実行する
- 大量イベント時に描画をまとめる

### 7.2 表示の信頼性

- イベントIDや発表時刻で重複表示を防ぐ
- 不正なJSONや未知の`type`を明示的に表示する
- 接続中、再接続中、未接続を別表示にする
- 最終受信時刻を表示する
- 地図読み込み失敗を画面へ通知する

### 7.3 アクセシビリティ

- 接続状態に`role="status"`と`aria-live`を付ける
- 色だけでなく文字でも状態を示す
- ボタン名とフォーカス状態を明確にする
- 読み上げ対象の本文を簡潔にする

### 7.4 デバッグ機能

- Local WebSocketのURL、readyState、切断コードを表示する
- 受信件数、最後の受信時刻、最後のイベント種別を表示する
- JavaScript例外を`AndroidBridge.log()`へ送る
- 本文全体ではなくコード、長さ、識別子を記録する
- デバッグ表示を本番向けに無効化できるようにする

## 8. デバッグ方法

### 8.1 Android Studio Logcat

アプリのログだけを表示する。

```text
package:com.example.koiyurepublic
```

WebView側のログだけを確認する。

```text
package:com.example.koiyurepublic tag:WebView/JS
```

Local WebSocketのJava側ログを確認する。

```text
package:com.example.koiyurepublic tag:LocalWebSocketServer
```

Serviceと接続処理を確認する。

```text
package:com.example.koiyurepublic (tag:SpinalCord | tag:P2PQuakeWebSocketClient)
```

Android Studioの検索構文対応が異なる場合は、まずパッケージだけで絞り、
Logcat上部のタグ・レベルフィルターを併用する。

### 8.2 正常な起動時の確認ポイント

```text
SpinalCord: onCreate 完了
LocalWebSocketServer: サーバー開始
WebView/JS: [LocalWS] 接続完了
LocalWebSocketServer: クライアント接続
```

`Address already in use`が出た場合は、Local WebSocketのポート競合であり、
PC上のSandbox Serverの8080番ポートとは別問題である。

## 9. 現在の制約

- Local WebSocketの接続先は現在`localhost:9001`に固定されている
- WebViewの履歴配列と履歴DOMに上限がない
- WebViewからLocal WebSocketへ送信したコマンド処理は未実装
- LocalStorageとSharedPreferencesに設定が分かれている
- Leaflet、地図タイル、Nominatimなど外部サービスに依存している
- WebViewが破棄されている間は画面表示を更新できない
- WebViewで表示できても、通知やTTSが端末設定で拒否される場合がある

## 10. 設計上の原則

1. WebViewは表示と操作を担当し、常時受信の責任を持たせない。
2. 地震データの通常配信経路はLocal WebSocketに統一する。
3. JavaScriptの表示状態は、再接続時にJava側のスナップショットから復元する。
4. Bridgeの公開メソッドは必要最小限にする。
5. 受信JSON全体を無制限にLogcatへ出力しない。
6. 通知・TTS・設定の実処理はJava側の状態を正本とする。
7. 表示できない場合でも、通知・TTS・Serviceの動作を妨げない。
