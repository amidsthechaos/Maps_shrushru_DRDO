package com.georoute.dto;

import com.georoute.model.RouteResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed GeoJSON FeatureCollection for the /api/route response. Jackson serializes the
 * public fields directly, producing standard GeoJSON consumable by OpenLayers.
 */
public class GeoJson {

    public final String type = "FeatureCollection";
    public List<Feature> features = new ArrayList<>();

    public static class Feature {
        public final String type = "Feature";
        public Properties properties;
        public Geometry geometry;
    }

    public static class Properties {
        public int routeIndex;
        public String label;
        public double totalDistanceMeters;
        public double estimatedTimeMinutes;
    }

    public static class Geometry {
        public final String type = "LineString";
        public List<double[]> coordinates;
    }

    /** Build a FeatureCollection from computed routes. */
    public static GeoJson fromRoutes(List<RouteResult> routes) {
        GeoJson fc = new GeoJson();
        for (RouteResult r : routes) {
            Feature f = new Feature();
            Properties p = new Properties();
            p.routeIndex = r.getRouteIndex();
            p.label = r.getLabel();
            p.totalDistanceMeters = r.getTotalDistanceMeters();
            p.estimatedTimeMinutes = r.getEstimatedTimeMinutes();
            f.properties = p;

            Geometry g = new Geometry();
            g.coordinates = r.getCoordinates();
            f.geometry = g;

            fc.features.add(f);
        }
        return fc;
    }
}
