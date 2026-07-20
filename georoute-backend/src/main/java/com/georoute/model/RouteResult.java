package com.georoute.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** A single computed route. Coordinates are {@code [lng, lat]} pairs (GeoJSON order). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteResult {
    private int routeIndex;
    private String label;
    private double totalDistanceMeters;
    private double estimatedTimeMinutes;
    private List<double[]> coordinates;
}
