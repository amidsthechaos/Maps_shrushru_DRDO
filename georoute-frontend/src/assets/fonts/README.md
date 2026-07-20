# Self-hosted fonts

These `.woff2` files are downloaded by `setup.sh` / `setup.bat` (they are gitignored).

Expected files:

- `JetBrainsMono-Regular.woff2`
- `JetBrainsMono-Medium.woff2`
- `Inter-Regular.woff2`

If a font is missing, the app still works — it falls back to the system font stack
defined in `src/styles.scss` (`--font-body`, `--font-mono`). No external font CDN is
ever used.
