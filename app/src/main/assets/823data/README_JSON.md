# JSON データ概要

このフォルダーには、2026-08-23 の地震関連 WebSocket/API 通知を保存した JSON
ファイルが入っています。調査時点では **363 ファイル**あり、`code` の内訳は次の
とおりです。

| `code` | 件数 | 概要 |
|---:|---:|---|
| 551 | 8 | 地震の震度情報・観測点情報 |
| 554 | 1 | 地震情報の配信完了通知 |
| 555 | 196 | 地域別の接続先・購読者数集計 |
| 556 | 1 | 緊急地震速報の推定震度・到達情報 |
| 9611 | 157 | 地域別集計の信頼度情報 |

すべての JSON は JSON オブジェクトで、配列をルートには持ちません。

## ファイル名

ファイル名は次の形式です。

```text
{code}_{_id}_{time}.json
```

例:

```text
551_6a89d673e88ee598246bf24a_2026-08-23 02-03-47-089.json
```

- `code`: JSON 内の `code` と同じ通知種別
- `_id`: JSON 内の通知 ID
- `time`: JSON 内の `time` をファイル名用に変換した値
  - `/`、`:`、`.` は `-` に置換

## 共通フィールド

| フィールド | 型 | 内容 |
|---|---|---|
| `_id` | string | 通知を識別する ID |
| `code` | number | 通知種別 |
| `time` | string | 通知の発生・登録時刻。`YYYY/MM/DD HH:mm:ss[.SSS]` 形式 |
| `ver` | string | データ形式または生成システムのバージョン |

`551`、`554`、`556` には `user_agent`、`9611` には `user-agent` があり、
ハイフンとアンダースコアが統一されていません。

時刻は JSON 内では `/` 区切り、ファイル名では `-` 区切りです。タイムゾーンは
フィールド内に明記されていません。

## code 551: 地震の震度情報

### トップレベル

```json
{
  "_id": "string",
  "code": 551,
  "comments": {},
  "earthquake": {},
  "issue": {},
  "points": [],
  "time": "string",
  "timestamp": {},
  "user_agent": "string",
  "ver": "20231023"
}
```

### `earthquake`

| フィールド | 型 | 内容 |
|---|---|---|
| `domesticTsunami` | string | 国内津波情報。例: `None`, `Checking` |
| `foreignTsunami` | string | 海外津波情報。例: `Unknown` |
| `maxScale` | number | 最大震度。未確定時は `-1` |
| `time` | string | 地震発生時刻 |
| `hypocenter.depth` | number | 深さ。未確定時は `-1` |
| `hypocenter.latitude` | number | 緯度。未確定時は `-200` |
| `hypocenter.longitude` | number | 経度。未確定時は `-200` |
| `hypocenter.magnitude` | number | マグニチュード。未確定時は `-1` |
| `hypocenter.name` | string | 震央地名 |

### `issue`

| フィールド | 型 | 内容 |
|---|---|---|
| `correct` | string | 訂正情報。調査データでは `None` |
| `source` | string | 情報源。調査データでは `気象庁` |
| `time` | string | 情報の発表時刻 |
| `type` | string | 通知種別。`Destination`、`DetailScale`、`ScalePrompt` |

### `points`

震度観測点または地域ごとの配列です。要素は次のフィールドを持ちます。

| フィールド | 型 | 内容 |
|---|---|---|
| `addr` | string | 観測地点または地域名 |
| `isArea` | boolean | 地域単位かどうか |
| `pref` | string | 都道府県名 |
| `scale` | number | 震度。例: `10`、`30`、`40`、`45` |

`issue.type` が `Destination` のデータでは `points` が空配列になる場合があります。

### `timestamp`

| フィールド | 型 | 内容 |
|---|---|---|
| `convert` | string | 変換処理時刻 |
| `register` | string | 登録時刻 |

## code 554: 配信完了通知

地震情報の一連の配信が完了したことを示す簡易通知です。

```json
{
  "_id": "string",
  "code": 554,
  "time": "2026/08/23 02:00:53.886",
  "type": "Full",
  "user_agent": "jmaxml-seis-parser-go, relay, register-api",
  "ver": "20231023"
}
```

- `type`: 調査データでは `Full`
- `earthquake` や `points` などの詳細情報は含みません

## code 555: 地域別の接続先・購読者数

`areas` に地域ごとの集計を持ちます。

```json
{
  "_id": "string",
  "areas": [
    {
      "id": 250,
      "peer": 235
    }
  ],
  "code": 555,
  "created_at": "2026/08/23 09:51:29.663",
  "expire": "2026/08/23 09:54:30",
  "hop": 10,
  "time": "2026/08/23 09:51:29.652",
  "uid": "2026/08/23 09:54:30",
  "ver": "20150406"
}
```

| フィールド | 型 | 内容 |
|---|---|---|
| `areas` | object[] | 地域別集計の配列 |
| `areas[].id` | number | 地域 ID |
| `areas[].peer` | number | その地域の接続先・購読者数とみられる値 |
| `created_at` | string | 集計作成時刻 |
| `expire` | string | 有効期限 |
| `hop` | number | 中継段数または伝播情報 |
| `uid` | string | 調査データでは時刻文字列が入る |

調査データでは `areas` の要素は常に `id` と `peer` の組で、配列長は
おおむね 107〜129 です。

## code 556: 緊急地震速報の推定情報

地震発生直後の推定震度と各地域への到達時刻を持つデータです。

```json
{
  "_id": "string",
  "areas": [
    {
      "arrivalTime": "string",
      "kindCode": "10",
      "name": "茨城県南部",
      "pref": "茨城",
      "scaleFrom": 50,
      "scaleTo": 50
    }
  ],
  "cancelled": false,
  "code": 556,
  "earthquake": {},
  "issue": {},
  "time": "string",
  "timestamp": {},
  "user_agent": "string",
  "ver": "20231023"
}
```

### `areas[]`

| フィールド | 型 | 内容 |
|---|---|---|
| `arrivalTime` | string | 推定到達時刻 |
| `kindCode` | string | 地域・情報種別コード |
| `name` | string | 地域名 |
| `pref` | string | 都道府県名 |
| `scaleFrom` | number | 推定震度の下限 |
| `scaleTo` | number | 推定震度の上限 |

### `earthquake`

| フィールド | 型 | 内容 |
|---|---|---|
| `arrivalTime` | string | 緊急地震速報の到達時刻 |
| `originTime` | string | 地震発生時刻 |
| `condition` | string | 付加状態 |
| `hypocenter.depth` | number | 深さ |
| `hypocenter.latitude` | number | 緯度 |
| `hypocenter.longitude` | number | 経度 |
| `hypocenter.magnitude` | number | マグニチュード |
| `hypocenter.name` | string | 震央地名 |
| `hypocenter.reduceName` | string | 短縮された震央地名 |

### `issue`

| フィールド | 型 | 内容 |
|---|---|---|
| `eventId` | string | 地震イベント ID |
| `serial` | string | 情報番号 |
| `time` | string | 発表時刻 |

## code 9611: 地域別集計の信頼度

ユーザー地震情報集計システムが、地域別集計の信頼度を記録したデータです。

```json
{
  "_id": "string",
  "area_confidences": {},
  "code": 9611,
  "confidence": 0.98052,
  "count": 196,
  "started_at": "2026/08/23 02:00:53.308",
  "time": "2026/08/23 02:00:53.442",
  "updated_at": "2026/08/23 02:00:53.308",
  "user-agent": "userquake-aggregator 20260612",
  "ver": "20260612"
}
```

| フィールド | 型 | 内容 |
|---|---|---|
| `area_confidences` | object | 地域 ID をキーにした地域ごとの信頼度。空オブジェクトの場合もある |
| `confidence` | number | 全体の信頼度。調査データでは `0`、`0.96774`、`0.98052` |
| `count` | number | 集計対象件数 |
| `started_at` | string | 集計開始時刻 |
| `updated_at` | string | 集計更新時刻 |
| `user-agent` | string | 生成システム。`userquake-aggregator 20260612` |

`confidence` は 0〜1 の小数として記録される形式ですが、初期状態では `0` の
場合があります。`area_confidences` が空でない場合は、地域 ID をキーとして
次のオブジェクトを持ちます。

```json
{
  "205": {
    "confidence": 0.0035128949038814223,
    "count": 1,
    "display": "E"
  }
}
```

| `area_confidences.{地域ID}` のフィールド | 型 | 内容 |
|---|---|---|
| `confidence` | number | その地域の信頼度 |
| `count` | number | その地域の集計対象件数 |
| `display` | string | 表示用の信頼度ランク。例: `E` |

## 値の扱いに関する注意

- 未確定値に `-1`、緯度・経度の未確定値に `-200` が使われます。通常の数値と
  同じ意味で扱わず、欠損値として判定してください。
- 同じ地震イベントについて、`551` は `ScalePrompt`、`DetailScale`、
  `Destination` のように段階的に複数届くことがあります。
- `551` の `maxScale` と `points[].scale`、`556` の `scaleFrom`/`scaleTo` は
  このデータセット固有の震度数値表現です。表示時に一般的な震度文字列へ変換
  する場合は、別途マッピングを定義してください。
- `551`、`556` の時刻は気象庁由来の情報時刻と、登録処理時刻が混在します。
  `time`、`earthquake.time`、`timestamp.register` を同一の意味として扱わない
  でください。

## 補助スクリプト

同じフォルダーの `temp1.py` は、JSON の `_id`、`code`、`time` を読み取り、
現在のファイル名形式へリネームするスクリプトです。JSON の内容は変更せず、
必須フィールドがないファイル、または同名ファイルが存在する場合はスキップ
します。
