# GeoRoute — Offline portable run

This folder is a **prebuilt** GeoRoute package. You do **not** need Node.js, npm, Maven, or Angular CLI on this machine.

## What you still need installed

| Software | Why |
|----------|-----|
| **JDK 17** | Runs the JAR |
| **PostgreSQL 15+ with PostGIS** | Road graph storage |

No internet is required at runtime.

## One-time database setup

```bat
createdb georoute
psql -d georoute -f db\init.sql
```

Edit `application.properties`:

- set `spring.datasource.password=...` to your Postgres password
- confirm `geotiff.base.path` and `shapefile.base.path` point at files under `data\`

Put your GeoTIFF + road Shapefile into `data\` if they were not shipped in this zip (see `data\README.md`).

## Start the app

```bat
run-offline.bat
```

Open **http://localhost:8080** — UI + API run on the same port.

First start may take a while while roads are ingested into PostGIS. Later starts skip ingest unless you set `shapefile.force-reload=true`.

## Notes

- Delete `tile-cache\` after changing the GeoTIFF so tiles rebuild.
- Dev workflow (`npm start` on :4200) is separate — this bundle is for offline / demos / air-gapped PCs.
