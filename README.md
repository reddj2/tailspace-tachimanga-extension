# Tailspace extension for Tachimanga

This repository builds a Tailspace Tachiyomi-compatible extension and publishes a Tachimanga repository on the `repo` branch.

## Upload to GitHub

1. Create a new **public** GitHub repository.
2. Upload every file and folder in this ZIP to the repository's `main` branch.
3. Open **Actions** and run **Build Tailspace extension**. GitHub may ask you to enable Actions first.
4. After the build succeeds, a branch named `repo` will appear.
5. In Tachimanga on iPhone, add this repository URL:

   `https://raw.githubusercontent.com/YOUR-GITHUB-NAME/YOUR-REPOSITORY-NAME/repo/index.min.json`

6. Refresh Extensions, find **Tailspace**, and install it.

## Important

This is a best-effort first build based on Tailspace's public HTML as of July 29, 2026. The GitHub Action compiles the APK, but the extension still needs real-device testing. Tailspace can alter its HTML or query parameters without notice. Search is implemented with `?search=`; browsing and reading are designed to work even if Tailspace ignores the requested sort parameter.

The reader requests each numbered gallery page and collects the comic images, because Tailspace only exposes a few image elements at a time.
