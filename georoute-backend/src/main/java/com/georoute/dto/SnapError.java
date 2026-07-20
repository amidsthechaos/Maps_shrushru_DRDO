package com.georoute.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Returned (HTTP 404) when no road is found within the search radius. */
@Data
@AllArgsConstructor
public class SnapError {
    private String error;   // e.g. "NO_ROAD_NEARBY"
    private String message;
}
