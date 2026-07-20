package com.georoute.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of snapping a clicked point to the nearest road. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SnapResult {
    private double snappedLat;
    private double snappedLng;
    private long nodeId;
    private String nearestRoadName;
    private double distanceToRoadM;
}
