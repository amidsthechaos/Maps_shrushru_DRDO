package com.georoute.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.processing.Operations;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.media.jai.Interpolation;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Renders 256x256 XYZ PNG tiles from a local GeoTIFF (EPSG:4326). No WMS, no network.
 *
 * <p>Standard slippy-map (Web Mercator) tile indices are converted to a WGS84 lon/lat
 * bounding box, the coverage is resampled to that box at 256x256, and the result is PNG
 * encoded and cached to disk. Tiles outside the GeoTIFF footprint (or when no GeoTIFF is
 * present) render transparent so the map still loads.
 */
@Slf4j
@Service
public class GeoTiffTileService {

    private static final int TILE = 256;

    @Value("${geotiff.base.path}")
    private String geotiffPath;

    @Value("${tiles.cache.dir:./tile-cache}")
    private String cacheDir;

    @Value("${tiles.cache.enabled:true}")
    private boolean cacheEnabled;

    private GridCoverage2D coverage;
    private ReferencedEnvelope coverageEnvelope;
    private boolean available;
    private int bandCount;

    @PostConstruct
    void init() {
        File file = new File(geotiffPath);
        if (!file.exists()) {
            log.warn("GeoTIFF not found at '{}'. Tiles will render transparent until it is provided.",
                    file.getAbsolutePath());
            return;
        }
        try {
            GeoTiffReader reader = new GeoTiffReader(file);
            this.coverage = reader.read(null);
            org.opengis.geometry.Envelope env = coverage.getEnvelope();
            this.coverageEnvelope = new ReferencedEnvelope(
                    env.getMinimum(0), env.getMaximum(0),
                    env.getMinimum(1), env.getMaximum(1),
                    DefaultGeographicCRS.WGS84);
            this.bandCount = coverage.getNumSampleDimensions();
            this.available = true;
            log.info("GeoTIFF basemap loaded: {} bands={} (envelope {})",
                    file.getName(), bandCount, coverageEnvelope);
        } catch (Exception e) {
            log.error("Failed to read GeoTIFF '{}': {}", file.getAbsolutePath(), e.getMessage(), e);
        }
    }

    /** Return PNG bytes for the given tile. */
    public byte[] getTile(int z, int x, int y) throws Exception {
        Path cached = Paths.get(cacheDir, String.valueOf(z), String.valueOf(x), y + ".png");
        if (cacheEnabled && Files.exists(cached)) {
            return Files.readAllBytes(cached);
        }

        BufferedImage image = renderTile(z, x, y);
        byte[] png = toPng(image);

        if (cacheEnabled) {
            Files.createDirectories(cached.getParent());
            Files.write(cached, png);
        }
        return png;
    }

    private BufferedImage renderTile(int z, int x, int y) {
        double lonW = tile2lon(x, z);
        double lonE = tile2lon(x + 1, z);
        double latN = tile2lat(y, z);
        double latS = tile2lat(y + 1, z);

        BufferedImage out = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);

        if (!available) {
            return out;   // fully transparent
        }

        ReferencedEnvelope tileEnv =
                new ReferencedEnvelope(lonW, lonE, latS, latN, DefaultGeographicCRS.WGS84);
        if (!coverageEnvelope.intersects((org.locationtech.jts.geom.Envelope) tileEnv)) {
            return out;   // outside basemap footprint
        }

        try {
            GridGeometry2D target = new GridGeometry2D(
                    new GridEnvelope2D(new Rectangle(0, 0, TILE, TILE)), tileEnv);

            GridCoverage2D resampled;
            synchronized (this) {
                resampled = (GridCoverage2D) Operations.DEFAULT.resample(
                        coverage,
                        DefaultGeographicCRS.WGS84,
                        target,
                        Interpolation.getInstance(Interpolation.INTERP_BILINEAR));
            }

            paintCoverage(resampled.getRenderedImage(), out);
        } catch (Exception e) {
            log.debug("Tile {}/{}/{} render fell back to transparent: {}", z, x, y, e.getMessage());
        }
        return out;
    }

    /**
     * Paint resampled coverage into ARGB. Multi-band RGB is drawn directly; single-band
     * is mapped to grayscale (0 → transparent) so constant-fill / float rasters do not
     * become solid yellow via GeoTools' default ColorModel.
     */
    private void paintCoverage(RenderedImage ri, BufferedImage out) {
        int bands = ri.getSampleModel().getNumBands();
        if (bands >= 3 && ri.getColorModel() != null
                && ri.getColorModel().getColorSpace().getNumComponents() >= 3) {
            Graphics2D g = out.createGraphics();
            try {
                g.drawRenderedImage(ri, new AffineTransform());
            } finally {
                g.dispose();
            }
            return;
        }

        Raster raster = ri.getData();
        int w = Math.min(TILE, raster.getWidth());
        int h = Math.min(TILE, raster.getHeight());
        boolean floating = raster.getDataBuffer().getDataType() == DataBuffer.TYPE_FLOAT
                || raster.getDataBuffer().getDataType() == DataBuffer.TYPE_DOUBLE;

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        if (floating || bands == 1) {
            for (int y = 0; y < h; y += 8) {
                for (int x = 0; x < w; x += 8) {
                    double v = raster.getSampleDouble(x, y, 0);
                    if (Double.isNaN(v)) continue;
                    if (v < min) min = v;
                    if (v > max) max = v;
                }
            }
        }
        if (!(max > min)) {
            min = 0;
            max = 255;
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (bands >= 3) {
                    int rr = clampByte(raster.getSampleDouble(x, y, 0));
                    int gg = clampByte(raster.getSampleDouble(x, y, 1));
                    int bb = clampByte(raster.getSampleDouble(x, y, 2));
                    out.setRGB(x, y, (255 << 24) | (rr << 16) | (gg << 8) | bb);
                } else {
                    double v = raster.getSampleDouble(x, y, 0);
                    if (Double.isNaN(v) || v == 0.0) {
                        continue;
                    }
                    int gray = (int) Math.round(255.0 * (v - min) / (max - min));
                    gray = Math.max(0, Math.min(255, gray));
                    int alpha = (max - min) < 1e-6 ? 40 : 255;
                    out.setRGB(x, y, (alpha << 24) | (gray << 16) | (gray << 8) | gray);
                }
            }
        }
    }

    private static int clampByte(double v) {
        if (Double.isNaN(v)) return 0;
        if (v < 0) return 0;
        if (v > 255) return 255;
        return (int) Math.round(v);
    }

    private byte[] toPng(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        }
    }

    private double tile2lon(int x, int z) {
        return x / Math.pow(2.0, z) * 360.0 - 180.0;
    }

    private double tile2lat(int y, int z) {
        double n = Math.PI - 2.0 * Math.PI * y / Math.pow(2.0, z);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }
}
