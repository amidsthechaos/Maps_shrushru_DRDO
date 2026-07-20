package com.georoute.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A graph vertex (a junction/endpoint in the road network). Coordinates are WGS84. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoadNode {
    private long id;
    private double lng;
    private double lat;
}
