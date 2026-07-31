#!/usr/bin/env python3
"""Refresh Tailspace's name-to-ID tag list from its public browse data.

The Tailspace website returns React Router's compact devalue-style payload from
/browse.data. Every comic object includes its tag objects, so crawling the
browse pages is enough to recover the site's current tag map without guessing
IDs or issuing one request per possible ID.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Iterable

BASE_URL = "https://tailspace.com"
DEFAULT_OUTPUT = Path(
    "src/en/tailspace/src/eu/kanade/tachiyomi/extension/en/tailspace/"
    "TailspaceTags.kt"
)
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150 Safari/537.36"
)


def decode_devalue(text: str) -> Any:
    values = json.loads(text)
    if not isinstance(values, list):
        raise ValueError("Tailspace data was not a devalue array")

    memo: dict[int, Any] = {}
    negative_specials = {
        -1: None,
        -2: float("nan"),
        -3: float("inf"),
        -4: float("-inf"),
        -5: None,
        -6: None,
    }

    def decode_reference(reference: Any) -> Any:
        if isinstance(reference, bool) or reference is None:
            return reference
        if isinstance(reference, (str, float)):
            return reference
        if isinstance(reference, int):
            if reference < 0:
                return negative_specials.get(reference)
            if reference >= len(values):
                # Tagged values such as ["D", 123456789] contain raw numbers.
                return reference
            return decode_index(reference)
        return reference

    def decode_index(index: int) -> Any:
        if index in memo:
            return memo[index]

        value = values[index]
        if value is None or isinstance(value, (str, bool, int, float)):
            memo[index] = value
            return value

        if isinstance(value, list):
            # Tailspace uses small tagged arrays for dates, e.g. ["D", millis].
            if value and isinstance(value[0], str) and len(value) <= 4:
                tagged = tuple(value)
                memo[index] = tagged
                return tagged

            decoded_list: list[Any] = []
            memo[index] = decoded_list
            decoded_list.extend(decode_reference(item) for item in value)
            return decoded_list

        if isinstance(value, dict):
            decoded_dict: dict[str, Any] = {}
            memo[index] = decoded_dict
            for encoded_key, encoded_value in value.items():
                if encoded_key.startswith("_") and encoded_key[1:].isdigit():
                    key = str(decode_index(int(encoded_key[1:])))
                else:
                    key = encoded_key
                decoded_dict[key] = decode_reference(encoded_value)
            return decoded_dict

        raise TypeError(f"Unsupported Tailspace value: {type(value)!r}")

    return decode_index(0)


def find_browse_page(value: Any) -> dict[str, Any] | None:
    if isinstance(value, dict):
        if "comicsAndAds" in value and "numberOfPages" in value:
            return value
        for nested in value.values():
            found = find_browse_page(nested)
            if found is not None:
                return found
    elif isinstance(value, list):
        for nested in value:
            found = find_browse_page(nested)
            if found is not None:
                return found
    return None


def collect_tags(browse_page: dict[str, Any], tags: dict[str, int]) -> None:
    comics = browse_page.get("comicsAndAds", [])
    if not isinstance(comics, list):
        return

    for item in comics:
        if not isinstance(item, dict):
            continue
        comic_tags = item.get("tags", [])
        if not isinstance(comic_tags, list):
            continue
        for tag in comic_tags:
            if not isinstance(tag, dict):
                continue
            name = tag.get("name")
            tag_id = tag.get("id")
            if not isinstance(name, str) or not isinstance(tag_id, int):
                continue
            name = name.strip()
            if not name:
                continue
            previous = tags.get(name)
            if previous is not None and previous != tag_id:
                raise ValueError(
                    f"Conflicting IDs for tag {name!r}: {previous} and {tag_id}"
                )
            tags[name] = tag_id


def fetch_text(url: str, attempts: int = 4) -> str:
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "*/*",
            "Accept-Language": "en-US,en;q=0.9",
            "Referer": f"{BASE_URL}/browse",
            "User-Agent": USER_AGENT,
        },
    )

    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                return response.read().decode("utf-8")
        except (urllib.error.URLError, TimeoutError) as error:
            last_error = error
            if attempt < attempts:
                time.sleep(attempt * 2)

    raise RuntimeError(f"Could not fetch {url}: {last_error}")


def fetch_browse_page(page: int) -> dict[str, Any]:
    query = urllib.parse.urlencode({"page": page})
    payload = decode_devalue(fetch_text(f"{BASE_URL}/browse.data?{query}"))
    browse_page = find_browse_page(payload)
    if browse_page is None:
        raise ValueError(f"Browse data was not found on page {page}")
    return browse_page


def collect_live_tags(delay: float) -> dict[str, int]:
    first_page = fetch_browse_page(1)
    total_pages = int(first_page.get("numberOfPages") or 1)
    if total_pages < 1 or total_pages > 500:
        raise ValueError(f"Unreasonable page count returned by Tailspace: {total_pages}")

    tags: dict[str, int] = {}
    collect_tags(first_page, tags)
    print(f"Page 1/{total_pages}: {len(tags)} unique tags")

    for page in range(2, total_pages + 1):
        if delay > 0:
            time.sleep(delay)
        collect_tags(fetch_browse_page(page), tags)
        print(f"Page {page}/{total_pages}: {len(tags)} unique tags")

    return tags


def collect_har_tags(paths: Iterable[Path]) -> dict[str, int]:
    tags: dict[str, int] = {}
    for path in paths:
        har = json.loads(path.read_text(encoding="utf-8"))
        for entry in har.get("log", {}).get("entries", []):
            url = entry.get("request", {}).get("url", "")
            if "/browse.data" not in url:
                continue
            content = entry.get("response", {}).get("content", {})
            text = content.get("text")
            if not isinstance(text, str) or not text:
                continue
            browse_page = find_browse_page(decode_devalue(text))
            if browse_page is not None:
                collect_tags(browse_page, tags)
    return tags


def kotlin_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def write_kotlin(tags: dict[str, int], output: Path) -> None:
    ordered = sorted(tags.items(), key=lambda item: (item[0].casefold(), item[0]))
    lines = [
        "package eu.kanade.tachiyomi.extension.en.tailspace",
        "",
        "// Generated by scripts/update_tags.py. Do not edit by hand.",
        "internal data class TailspaceTag(val name: String, val id: Int)",
        "",
        "internal val TAILSPACE_TAGS = listOf(",
    ]
    lines.extend(
        f"    TailspaceTag({kotlin_string(name)}, {tag_id}),"
        for name, tag_id in ordered
    )
    lines.append(")")
    lines.append("")

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {len(ordered)} tags to {output}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--har", type=Path, action="append", default=[])
    parser.add_argument("--delay", type=float, default=0.15)
    parser.add_argument("--min-tags", type=int, default=100)
    args = parser.parse_args()

    tags = collect_har_tags(args.har) if args.har else collect_live_tags(args.delay)
    if len(tags) < args.min_tags:
        raise RuntimeError(
            f"Only {len(tags)} tags were found; refusing to replace the existing list"
        )

    write_kotlin(tags, args.output)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"Tag refresh failed: {error}", file=sys.stderr)
        raise
