"""Rebuild data/rasterized.tif as an RGB road basemap (dark bg + light roads).

The previous raster was a single-band float filled entirely with 255, which
GeoTools rendered as solid yellow tiles (~RGB 255,255,1).
"""
from __future__ import annotations

import json
import os
import re
import time
from pathlib import Path

# Avoid picking up PostgreSQL's older PROJ database via PROJ_LIB.
os.environ.pop("PROJ_LIB", None)
os.environ.pop("PROJ_DATA", None)

import numpy as np
import rasterio
from rasterio import features
from rasterio.transform import from_bounds
from shapely import wkb
from shapely.geometry import box, mapping

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data" / "roads_basemap.tif"
DEBUG_LOG = ROOT / "debug-2eac4d.log"
PROPS = ROOT / "georoute-backend" / "src" / "main" / "resources" / "application.properties"

# Fallback Delhi-ish extent (matches the broken prior GeoTIFF envelope).
FALLBACK_BOUNDS = (76.8388922, 28.4046677, 77.3475706, 28.8834997)
WIDTH = 4096
HEIGHT = 4096
BG = (14, 18, 26)
ROAD = (210, 216, 224)
MAJOR = (240, 244, 250)

MAJOR_TYPES = {
    "motorway", "trunk", "primary", "secondary", "tertiary",
    "motorway_link", "trunk_link", "primary_link", "secondary_link",
}


def debug(hypothesis_id: str, message: str, data: dict) -> None:
    payload = {
        "sessionId": "2eac4d",
        "hypothesisId": hypothesis_id,
        "location": "rebuild_road_basemap.py",
        "message": message,
        "data": data,
        "timestamp": int(time.time() * 1000),
        "runId": "basemap-rebuild",
    }
    with DEBUG_LOG.open("a", encoding="utf-8") as f:
        f.write(json.dumps(payload) + "\n")


def load_jdbc() -> dict:
    text = PROPS.read_text(encoding="utf-8")
    url = re.search(r"^spring\.datasource\.url=(.+)$", text, re.M).group(1).strip()
    user = re.search(r"^spring\.datasource\.username=(.+)$", text, re.M).group(1).strip()
    password = re.search(r"^spring\.datasource\.password=(.+)$", text, re.M).group(1).strip()
    # jdbc:postgresql://localhost:5432/georoute
    m = re.match(r"jdbc:postgresql://([^:/]+)(?::(\d+))?/(.+)", url)
    return {
        "host": m.group(1),
        "port": int(m.group(2) or 5432),
        "dbname": m.group(3),
        "user": user,
        "password": password,
    }


def main() -> None:
    try:
        import psycopg2
    except ImportError as e:
        raise SystemExit("psycopg2 required: pip install psycopg2-binary") from e

    # Prefer existing raster envelope (old yellow file or prior basemap).
    legacy = ROOT / "data" / "rasterized.tif"
    envelope_src = OUT if OUT.exists() else (legacy if legacy.exists() else None)
    if envelope_src is not None:
        with rasterio.open(envelope_src) as ds:
            b = ds.bounds
            bounds = (b.left, b.bottom, b.right, b.top)
    else:
        bounds = FALLBACK_BOUNDS

    minx, miny, maxx, maxy = bounds
    pad = 0.01
    bounds = (minx - pad, miny - pad, maxx + pad, maxy + pad)
    transform = from_bounds(*bounds, WIDTH, HEIGHT)

    t0 = time.time()
    cfg = load_jdbc()
    conn = psycopg2.connect(
        host=cfg["host"], port=cfg["port"], dbname=cfg["dbname"],
        user=cfg["user"], password=cfg["password"],
    )
    major_geoms = []
    other_geoms = []
    kept = 0
    try:
        with conn.cursor(name="roads_basemap") as cur:
            cur.itersize = 5000
            cur.execute(
                """
                SELECT e.road_type, ST_AsBinary(e.geom)
                FROM road_edges e
                WHERE e.geom && ST_MakeEnvelope(%s, %s, %s, %s, 4326)
                """,
                bounds,
            )
            for road_type, geom_wkb in cur:
                if not geom_wkb:
                    continue
                geom = wkb.loads(bytes(geom_wkb))
                if geom.is_empty:
                    continue
                kept += 1
                fclass = (road_type or "").lower()
                if fclass in MAJOR_TYPES:
                    major_geoms.append(geom)
                else:
                    other_geoms.append(geom)
    finally:
        conn.close()

    debug("A", "postgis scan complete", {
        "kept": kept,
        "major": len(major_geoms),
        "other": len(other_geoms),
        "bounds": list(bounds),
        "scanMs": int((time.time() - t0) * 1000),
    })

    # Dark background RGB
    rgb = np.zeros((3, HEIGHT, WIDTH), dtype=np.uint8)
    rgb[0] = BG[0]
    rgb[1] = BG[1]
    rgb[2] = BG[2]

    def burn(geoms, color) -> int:
        if not geoms:
            return 0
        shapes = ((mapping(g), 1) for g in geoms if not g.is_empty)
        mask = features.rasterize(
            shapes,
            out_shape=(HEIGHT, WIDTH),
            transform=transform,
            fill=0,
            all_touched=True,
            dtype=np.uint8,
        )
        hit = int((mask > 0).sum())
        m = mask > 0
        rgb[0][m] = color[0]
        rgb[1][m] = color[1]
        rgb[2][m] = color[2]
        return hit

    other_px = burn(other_geoms, ROAD)
    major_px = burn(major_geoms, MAJOR)

    profile = {
        "driver": "GTiff",
        "height": HEIGHT,
        "width": WIDTH,
        "count": 3,
        "dtype": "uint8",
        "crs": rasterio.crs.CRS.from_wkt(
            'GEOGCS["WGS 84",DATUM["WGS_1984",SPHEROID["WGS 84",6378137,298.257223563]],'
            'PRIMEM["Greenwich",0],UNIT["degree",0.0174532925199433]]'
        ),
        "transform": transform,
        "compress": "LZW",
        "tiled": True,
        "blockxsize": 256,
        "blockysize": 256,
    }
    with rasterio.open(OUT, "w", **profile) as dst:
        dst.write(rgb)

    # Verify not a solid fill.
    with rasterio.open(OUT) as ds:
        sample = ds.read()
        unique_r = len(np.unique(sample[0]))
        mean_r = float(sample[0].mean())

    debug("A", "basemap written", {
        "out": str(OUT),
        "bytes": OUT.stat().st_size,
        "otherPx": other_px,
        "majorPx": major_px,
        "uniqueR": unique_r,
        "meanR": mean_r,
        "bands": 3,
        "totalMs": int((time.time() - t0) * 1000),
    })
    print(f"Wrote {OUT} ({OUT.stat().st_size} bytes), roads kept={kept}, uniqueR={unique_r}")


if __name__ == "__main__":
    main()
