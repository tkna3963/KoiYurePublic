
import json
from pathlib import Path

folder = Path(__file__).parent

count = 0

for file in folder.glob("*.json"):
    try:
        with file.open("r", encoding="utf-8") as f:
            data = json.load(f)

        if "_id" not in data or "code" not in data or "time" not in data:
            print(f"スキップ: {file.name}")
            continue

        code = data["code"]
        _id = data["_id"]
        time = data["time"]

        # Windowsで使えない文字を変更
        safe_time = (
            time.replace("/", "-")
                .replace(":", "-")
                .replace(".", "-")
        )

        new_name = f"{code}_{_id}_{safe_time}.json"
        new_file = folder / new_name

        if new_file.exists():
            print(f"同名ファイルがあるためスキップ: {new_name}")
            continue

        file.rename(new_file)

        count += 1
        print(f"{file.name} -> {new_name}")

    except json.JSONDecodeError:
        print(f"JSONエラー: {file.name}")

    except Exception as e:
        print(f"エラー: {file.name} / {e}")

print()
print(f"完了！ {count}個のファイルをリネームしました。")
