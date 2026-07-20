package com.georoute.dto;

import lombok.Data;

/** Request body for POST /api/route. */
@Data
public class RouteRequest {
    private double sourceLat;
    private double sourceLng;
    private double destLat;
    private double destLng;
    /** Number of routes to return (>= 1). The first is the shortest path. */
    private int numAlternatePaths = 1;
    /** "astar" (default) or "dijkstra". */
    private String algorithm = "astar";
}
