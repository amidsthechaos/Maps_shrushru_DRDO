package com.georoute.util;

/** Pure-Java geodesic helpers. No external libraries. */
public final class GeoUtils {

    /** Mean Earth radius in metres. */
    public static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoUtils() {
    }

    /**
     * Great-circle distance between two WGS84 points, in metres.
     *
     * <pre>
     *   a = sin^2(dLat/2) + cos(lat1) * cos(lat2) * sin^2(dLon/2)
     *   d = R * 2 * atan2(sqrt(a), sqrt(1 - a))
     * </pre>
     */
    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double sinLat = Math.sin(dLat / 2);
        double sinLon = Math.sin(dLon / 2);
        double a = sinLat * sinLat
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinLon * sinLon;
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
