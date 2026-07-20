package com.georoute.controller;

import com.georoute.service.GeoTiffTileService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tiles")
public class TileController {

    private final GeoTiffTileService tileService;

    public TileController(GeoTiffTileService tileService) {
        this.tileService = tileService;
    }

    @GetMapping(value = "/{z}/{x}/{y}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> tile(@PathVariable int z,
                                       @PathVariable int x,
                                       @PathVariable int y) {
        try {
            byte[] png = tileService.getTile(z, x, y);
            // Basemap swaps (or regenerations) must not stick in browsers for a week —
            // yellow tiles from the old rasterized.tif were stuck behind this header.
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .cacheControl(CacheControl.noStore().mustRevalidate())
                    .header("Pragma", "no-cache")
                    .body(png);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
