# GIS Data Directory

This folder must contain the local GIS data files the backend reads at runtime.
They are **not** committed to git (too large, license-bound). Download them once
during setup, then the app runs 100% offline.

## Required files

```
data/
├── india_natural_color.tif     # GeoTIFF basemap (EPSG:4326)
├── india_roads.shp             # Road network geometry
├── india_roads.dbf             # Road attributes (fclass, name, oneway, maxspeed)
├── india_roads.shx             # Shape index
└── india_roads.prj             # Projection definition
```

## Where to get them

### Road network Shapefile (recommended: Geofabrik OSM extract)
- https://download.geofabrik.de/asia/india.html
- Download `india-latest-free.shp.zip`, extract `gis_osm_roads_free_1.shp` (and its
  `.dbf/.shx/.prj`) and rename to `india_roads.*`.
- For development you can use a smaller city extract from https://extract.bbbike.org/.

### GeoTIFF basemap
- NASA Worldview snapshot (quick prototyping, 250m): https://worldview.earthdata.nasa.gov/
- ISRO Bhuvan (best India coverage): https://bhuvan.nrsc.gov.in/
- USGS Landsat / Copernicus Sentinel-2 (higher resolution).

## GeoTIFF prep with GDAL (offline tool, install once)

```bash
# Reproject to EPSG:4326 if needed
gdalwarp -t_srs EPSG:4326 input.tif india_natural_color.tif

# Build overview pyramids (speeds up low-zoom tile rendering)
gdaladdo -r average india_natural_color.tif 2 4 8 16 32 64

# Internal tiling (speeds up random tile-crop access)
gdal_translate -co TILED=YES -co COMPRESS=LZW india_natural_color.tif india_tiled.tif
```

> The road Shapefile is expected to expose an `fclass` attribute (OSM road class).
> The data loader maps `fclass` to a default speed. If your dataset uses a different
> attribute name, adjust `DataLoaderService` accordingly.
