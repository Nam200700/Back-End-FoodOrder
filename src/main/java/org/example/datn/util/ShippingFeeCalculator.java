package org.example.datn.util;

import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;

/**
 * Shipping fee formula kept in sync with the FE (utils/shippingFee.js):
 * 15.000đ for the first 2km + 5.000đ per extra km, rounded up to 1.000đ.
 */
public final class ShippingFeeCalculator {

    private static final long BASE_FEE = 15_000L;
    private static final double BASE_KM = 2.0;
    private static final long PER_KM_FEE = 5_000L;
    private static final double MAX_DISTANCE = 10.0;

    private ShippingFeeCalculator() {
    }

    public static long calculate(double distanceKm) {
        if (distanceKm <= 0) {
            return 0L;
        }
        if (distanceKm > MAX_DISTANCE) {
            throw new AppException(ErrorCode.DISTANCE_TOO_FAR,
                    "Khoảng cách vượt quá " + MAX_DISTANCE + "km");
        }
        if (distanceKm <= BASE_KM) {
            return BASE_FEE;
        }
        long extra = (long) Math.ceil(distanceKm - BASE_KM) * PER_KM_FEE;
        long raw = BASE_FEE + extra;
        return (long) (Math.ceil(raw / 1000.0) * 1000);
    }

    public static double getMaxDistanceKm() {
        return MAX_DISTANCE;
    }
}
