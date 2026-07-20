package com.georoute.dto;

import lombok.Data;

/** Request body for POST /api/snap. */
@Data
public class SnapRequest {
    private double lat;
    private double lng;
}
