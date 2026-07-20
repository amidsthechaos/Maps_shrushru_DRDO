#!/usr/bin/env bash
# GeoRoute one-time ONLINE setup (Linux / macOS).
# After this completes, the app builds and runs fully offline.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND="$ROOT/georoute-frontend"
BACKEND="$ROOT/georoute-backend"
FONTS="$FRONTEND/src/assets/fonts"

echo "==> GeoRoute setup starting"

# ── 1. Angular CLI (global, one time) ───────────────────────────────────────
if ! command -v ng >/dev/null 2>&1; then
  echo "==> Installing Angular CLI 17 globally"
  npm install -g @angular/cli@17
else
  echo "==> Angular CLI already installed: $(ng version | head -n 1 || true)"
fi

# ── 2. Frontend npm packages (includes OpenLayers) ──────────────────────────
echo "==> Installing frontend npm packages"
cd "$FRONTEND"
npm install

# ── 3. Self-hosted fonts (downloaded once, then served locally) ─────────────
echo "==> Downloading self-hosted fonts"
mkdir -p "$FONTS"
TMP="$(mktemp -d)"

# JetBrains Mono (OFL-1.1)
curl -L https://github.com/JetBrains/JetBrainsMono/releases/download/v2.304/JetBrainsMono-2.304.zip \
     -o "$TMP/jbmono.zip"
unzip -o "$TMP/jbmono.zip" "fonts/webfonts/JetBrainsMono-Regular.woff2" \
                           "fonts/webfonts/JetBrainsMono-Medium.woff2" \
     -d "$TMP/jbmono"
cp "$TMP/jbmono/fonts/webfonts/"*.woff2 "$FONTS/"

# Inter (OFL-1.1)
curl -L https://github.com/rsms/inter/releases/download/v4.0/Inter-4.0.zip \
     -o "$TMP/inter.zip"
unzip -o "$TMP/inter.zip" "Inter Desktop/Inter-Regular.woff2" -d "$TMP/inter"
cp "$TMP/inter/Inter Desktop/Inter-Regular.woff2" "$FONTS/"

rm -rf "$TMP"
echo "==> Fonts installed in $FONTS"

# ── 4. Backend Maven dependencies (cache locally) ───────────────────────────
echo "==> Caching backend Maven dependencies (full online install — required for offline builds)"
cd "$BACKEND"
mvn install

echo ""
echo "==> Setup complete."
echo "    Next:"
echo "      1. Put GIS data into ./data (see data/README.md)"
echo "      2. createdb georoute && psql -d georoute -f db/init.sql"
echo "      3. Set DB password in georoute-backend/src/main/resources/application.properties"
echo "      4. cd georoute-backend && mvn spring-boot:run"
echo "      5. cd georoute-frontend && npm start"
