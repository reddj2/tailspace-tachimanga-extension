import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT / "repo"
APK_SOURCE = ROOT / "extension" / "build" / "outputs" / "apk" / "debug" / "extension-debug.apk"
APK_NAME = "tachiyomi-en.tailspace-v1.6.3.apk"
APK_DESTINATION = REPO / "apk" / APK_NAME

REPO.mkdir(parents=True, exist_ok=True)
APK_DESTINATION.parent.mkdir(parents=True, exist_ok=True)

if not APK_SOURCE.exists():
    raise FileNotFoundError(f"Built APK was not found: {APK_SOURCE}")

shutil.copy2(APK_SOURCE, APK_DESTINATION)

index = [
    {
        "name": "Tachiyomi: Tailspace",
        "pkg": "eu.kanade.tachiyomi.extension.en.tailspace",
        "apk": f"apk/{APK_NAME}",
        "lang": "en",
        "code": 3,
        "version": "1.6.3",
        "nsfw": 1,
        "hasReadme": 0,
        "hasChangelog": 0,
        "sources": [
            {
                "name": "Tailspace",
                "lang": "en",
                "id": "4704682754071289646",
                "baseUrl": "https://tailspace.com",
                "versionId": 1
            }
        ]
    }
]

(REPO / "index.json").write_text(
    json.dumps(index, indent=2, ensure_ascii=False) + "\n",
    encoding="utf-8",
)
(REPO / "index.min.json").write_text(
    json.dumps(index, separators=(",", ":"), ensure_ascii=False),
    encoding="utf-8",
)
(REPO / ".nojekyll").touch()

print(f"Created {REPO / 'index.json'}")
print(f"Created {REPO / 'index.min.json'}")
print(f"Copied APK to {APK_DESTINATION}")
