# Tailspace extension for Tachimanga

This repository stores only the Tailspace module and its publishing workflow.

The GitHub Action checks out the official `keiyoushi/extensions-source` project,
copies the Tailspace module into it, builds the APK, generates the repository
index, and publishes the result to the `repo` branch.
