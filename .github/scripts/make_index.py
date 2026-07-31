import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INPUT_APK = Path(sys.argv[1]).resolve()
OUTPUT = ROOT / "repo"
APK_NAME = "tachiyomi-en.tailspace-v1.6.6.apk"
APK_DEST = OUTPUT / "apk" / APK_NAME

if not INPUT_APK.is_file():
    raise FileNotFoundError(f"APK not found: {INPUT_APK}")

APK_DEST.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(INPUT_APK, APK_DEST)

index = [
    {
        "name": "Tachiyomi: Tailspace",
        "pkg": "eu.kanade.tachiyomi.extension.en.tailspace",
        "apk": f"apk/{APK_NAME}",
        "lang": "en",
        "code": 6,
        "version": "1.6.6",
        "nsfw": 1,
        "hasReadme": 0,
        "hasChangelog": 0,
        "sources": [
            {
                "name": "Tailspace",
                "lang": "en",
                "id": "4704682754071289646",
                "baseUrl": "https://tailspace.com",
                "versionId": 1,
            }
        ],
    }
]

OUTPUT.mkdir(parents=True, exist_ok=True)
(OUTPUT / "index.json").write_text(
    json.dumps(index, indent=2, ensure_ascii=False) + "\n",
    encoding="utf-8",
)
(OUTPUT / "index.min.json").write_text(
    json.dumps(index, separators=(",", ":"), ensure_ascii=False),
    encoding="utf-8",
)
(OUTPUT / ".nojekyll").touch()

print(f"Published APK: {APK_DEST}")
print(f"Published index: {OUTPUT / 'index.min.json'}")
