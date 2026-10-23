package com.techy_pvk.automate.repository;

/** Outcome of turning a typed address into coordinates. */
public final class GeocodeResult {

    public final boolean found;
    public final double latitude;
    public final double longitude;
    /** User-facing explanation when found == false. */
    public final String message;

    private GeocodeResult(boolean found, double latitude, double longitude, String message) {
        this.found = found;
        this.latitude = latitude;
        this.longitude = longitude;
        this.message = message;
    }

    public static GeocodeResult success(double latitude, double longitude) {
        return new GeocodeResult(true, latitude, longitude, null);
    }

    public static GeocodeResult failure(String message) {
        return new GeocodeResult(false, 0, 0, message);
    }
}
