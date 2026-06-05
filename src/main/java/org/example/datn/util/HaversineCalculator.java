package org.example.datn.util;

/**
 * Great-circle distance between two coordinates. Free, runs on the JVM, and
 * accurate enough (&lt; 0.5% error) for short urban distances.
 */
public final class HaversineCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private HaversineCalculator() {
    }

    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double raw = EARTH_RADIUS_KM * c;

        // Round to 1 decimal place: 2.37 -> 2.4
        return Math.round(raw * 10.0) / 10.0;
    }
}
