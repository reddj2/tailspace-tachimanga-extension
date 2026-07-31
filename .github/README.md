# Tailspace extension for Tachimanga

Version **1.6.6** adds Tailspace tag filtering to the existing working source.

## What changed

- Adds a searchable/scrollable **Tags** filter group in compatible clients.
- Tap a tag once to include it; tap twice to exclude it.
- Sends repeated `tag=<ID>` and `excludeTag=<ID>` parameters exactly as the
  Tailspace website does.
- Search text and tag filters work together.
- The GitHub Action refreshes the name-to-ID tag list from Tailspace's public
  browse data before compiling.
- A fallback tag list is included so a temporary Tailspace outage does not stop
  the extension from building.

The repository uses the official Keiyoushi build project during GitHub Actions,
then publishes the APK and Tachimanga index to the `repo` branch.
