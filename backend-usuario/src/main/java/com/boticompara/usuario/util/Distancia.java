package com.boticompara.usuario.util;

/** H27: distancia en línea recta entre dos puntos de la Tierra, con la fórmula de Haversine. */
public final class Distancia {

    private static final double RADIO_TIERRA_KM = 6371.0;

    private Distancia() {
    }

    /** Distancia en kilómetros entre (lat1, lng1) y (lat2, lng2), en grados decimales. */
    public static double km(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * RADIO_TIERRA_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}