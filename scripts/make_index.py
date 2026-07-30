import json
from pathlib import Path

entry = [{
    "name": "Tachiyomi: Tailspace",
    "pkg": "eu.kanade.tachiyomi.extension.en.tailspace",
    "apk": "tachiyomi-en.tailspace-v1.4.3.apk",
    "lang": "en",
    "code": 3,
    "version": "1.4.3",
    "nsfw": 1,
    "sources": [{
        "name": "Tailspace",
        "lang": "en",
        "id": "4704682754071289646",
        "baseUrl": "https://tailspace.com",
        "versionId": 1
    }]
}]
repo = Path("repo")
repo.mkdir(exist_ok=True)
(repo / "index.min.json").write_text(json.dumps(entry, separators=(",", ":")), encoding="utf-8")
(repo / "index.json").write_text(json.dumps(entry, indent=2), encoding="utf-8")
