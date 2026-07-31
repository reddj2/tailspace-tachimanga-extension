# Upload and install guide

## 1. Back up the current repository

Open your GitHub repository and use **Code → Download ZIP**. Keep that copy
until the new action has built successfully.

## 2. Replace the `main` branch files

Open:

`https://github.com/reddj2/tailspace-tachimanga-extension`

On the **main** branch, remove the old root items, especially the old
`extension/` folder and old workflow. The clean main branch should use this
layout:

```text
.github/
scripts/
src/
README.md
START_HERE.txt
UPLOAD_GUIDE.md
```

Extract the ZIP on Windows, turn on **View → Show → Hidden items**, then drag
all extracted contents into the GitHub upload page. The hidden `.github` folder
must be included.

Commit message:

```text
Add Tailspace tag filters v1.6.6
```

## 3. Run the build

1. Open the repository's **Actions** tab.
2. Open **Build and publish Tailspace**.
3. Press **Run workflow** if it did not start automatically.
4. Wait for every step to turn green.

The `Refresh Tailspace tag list` step is allowed to show a warning if
Tailspace is temporarily unavailable. In that case, the included fallback list
is used. The build itself should still finish green.

## 4. Verify the generated `repo` branch

Switch the branch selector from `main` to `repo`. Confirm these files exist:

```text
index.json
index.min.json
.nojekyll
apk/tachiyomi-en.tailspace-v1.6.6.apk
```

## 5. GitHub Pages

In **Settings → Pages** choose:

- Source: **Deploy from a branch**
- Branch: **repo**
- Folder: **/ (root)**

## 6. Refresh it in Tachimanga

Your repository address remains:

```text
https://reddj2.github.io/tailspace-tachimanga-extension/index.min.json
```

In Tachimanga:

1. Refresh extension repositories.
2. Update or reinstall **Tailspace**.
3. Open Tailspace search.
4. Open **Filters**.
5. Open **Tags**.
6. Tap a tag once to include it or twice to exclude it.
7. Apply the filters.

A test equivalent to the HAR capture is:

- Include tag ID `1`
- Exclude tag ID `9`
- Include `wolf` (ID `297`)

The generated request should contain repeated parameters like:

```text
/browse?tag=1&excludeTag=9&tag=297
```

## Troubleshooting

### The action says fewer than 100 tags

The automatic refresh failed and the fallback file is missing or damaged.
Re-upload the entire ZIP, including `TailspaceTags.kt`.

### GitHub cannot see `.github`

Enable **Hidden items** in Windows File Explorer before dragging the files.

### The extension does not show as an update

Confirm the `repo` branch contains the `v1.6.6` APK and that `index.min.json`
shows version `1.6.6` with code `6`. Then refresh repositories in Tachimanga.

### A tag is missing

Run the GitHub Action again. Each build crawls Tailspace's public browse pages
and regenerates the current name-to-ID list before compiling.
