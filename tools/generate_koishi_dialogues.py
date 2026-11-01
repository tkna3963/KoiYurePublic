import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "koishi_dialogues.json"

DAILY_OPENERS = [
    "今日は何をしようかな？",
    "見つけた？ 私はまだ見つかってないよ。",
    "静かな時間って、ちょっとわくわくするね。",
    "おはよう。今日も気配を消していこう。",
    "こんにちは。ここにいるけど、気にしなくていいよ。",
    "こんばんは。夜は音が少なくて好きだな。",
    "おなかすいた？ 私も……たぶん。",
    "窓の外、さっきより明るくなった気がする。",
    "今日はゆっくり話してもいい日だよ。",
    "ねえねえ、今の音聞こえた？",
    "何もないところに、何かあるかもしれないよ。",
    "少し休憩しようよ。急がなくても大丈夫。",
    "あれ、ボタンが呼んでる気がする。",
    "気づいたらここにいたよ。ふしぎだね。",
    "今日はどんな知らせが来るのかな？",
    "見えないものも、たまには気にしてみよう。",
    "ひと息ついたら、また画面を見てみようか。",
    "私はここで見張り……じゃなくて、見守ってるね。",
    "何かあったら、すぐ知らせるよ。",
    "ゆっくりでいいよ。大事なことは逃げないから。",
]

DAILY_ENDINGS = [
    "気楽にいこうね。",
    "見つからなくても、それはそれで楽しいよ。",
    "画面は明るく、心はのんびり。",
    "今日もよろしくね。",
    "気づいた人から、そっと確認しよう。",
    "あわてないのがいちばんだよ。",
    "たまには空を見上げるのもいいね。",
    "私はちゃんとここにいるよ。",
    "小さな変化も教えてね。",
    "またあとで会おうね。",
]

QUAKE_OPENERS = [
    "揺れを感じたら、まず自分の安全を確かめよう。",
    "地震のお知らせだよ。落ち着いて画面を見てね。",
    "今の情報を受け取ったよ。身を守る準備をしよう。",
    "大事なお知らせ。あわてず、頭を守ってね。",
    "揺れが続くかもしれないよ。無理に動かないでね。",
    "地震情報が届いたよ。周りの人にも知らせよう。",
    "安全確認の時間だよ。机の下や丈夫な場所を探してね。",
    "速報を受信したよ。数字だけで判断せず、周囲も確認しよう。",
    "いまは遊びより安全を優先しようね。",
    "津波の情報があるときは、海から離れて高いところへ。",
    "地震のあとには落下物にも注意してね。",
    "火を使っていたら、無理のない範囲で確認しよう。",
    "揺れが収まっても、続報を確認してね。",
    "近くの人と声をかけ合おう。ひとりで抱えなくていいよ。",
    "警報が出たら、画面より避難を優先してね。",
    "この表示は防災の目安。現地の公式情報も確認しよう。",
    "怖いときは深呼吸。次にすることを一つずつ考えよう。",
    "落ち着いて、出口や避難経路を確認しよう。",
    "情報が変わることもあるから、更新を見守ろう。",
    "揺れの情報を受け取ったよ。無理せず安全な場所へ。",
]

QUAKE_ENDINGS = [
    "こいしちゃんとの約束だよ。",
    "まずは頭と体を守ろうね。",
    "近くの大人や係の人にも伝えてね。",
    "落ち着いて行動できれば大丈夫。",
    "困ったら周りの人に助けを求めてね。",
    "最新の防災情報を確認してね。",
    "安全が確認できるまで、無理に戻らないでね。",
    "この画面は判断の手助けとして使ってね。",
    "みんなで声をかけ合おう。",
    "深呼吸して、一つずつだよ。",
]

FACES = [
    "koishi_smile.png",
    "koishi_laugh.png",
    "koishi_confident.png",
    "koishi_confident_shadow.png",
    "koishi_surprised.png",
    "koishi_worried.png",
    "koishi_cry.png",
    "koishi_angry.png",
    "koishi_displeased.png",
]

QUAKE_CODES = [551, 552, 554, 556, 561, 9611]


def build():
    sets = []
    for index in range(200):
        daily = f"{DAILY_OPENERS[index % len(DAILY_OPENERS)]} {DAILY_ENDINGS[(index * 7) % len(DAILY_ENDINGS)]}"
        quake = f"{QUAKE_OPENERS[(index * 3) % len(QUAKE_OPENERS)]} {QUAKE_ENDINGS[(index * 5) % len(QUAKE_ENDINGS)]}"
        sets.append(
            {
                "id": index + 1,
                "daily": {
                    "text": daily,
                    "face": FACES[index % len(FACES)],
                },
                "earthquake": {
                    "code": QUAKE_CODES[index % len(QUAKE_CODES)],
                    "text": quake,
                    "face": FACES[(index * 2 + 4) % len(FACES)],
                },
            }
        )

    document = {
        "version": 1,
        "notice": "公式台詞の再現ではなく、展示用のオリジナル会話データです。",
        "imageDirectory": "Img/koishi_faces/",
        "sets": sets,
    }
    OUTPUT.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Generated {len(sets)} dialogue sets: {OUTPUT}")


if __name__ == "__main__":
    build()
