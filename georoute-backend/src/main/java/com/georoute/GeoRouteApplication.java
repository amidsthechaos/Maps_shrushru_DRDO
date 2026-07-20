package com.georoute;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GeoRouteApplication {

    public static void main(String[] args) {
        // Force longitude-first axis order globally for GeoTools (WGS84 lon/lat).
        System.setProperty("org.geotools.referencing.forceXY", "true");
        SpringApplication.run(GeoRouteApplication.class, args);
    }
}
