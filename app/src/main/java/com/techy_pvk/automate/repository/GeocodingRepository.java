package com.techy_pvk.automate.repository;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import androidx.annotation.NonNull;

import com.techy_pvk.automate.util.AppExecutors;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Address -> coordinates, for the Location trigger dialog's "Look up" button. Runs on the
 *  background executor (Geocoder does network I/O) and uses the application context, so a slow
 *  lookup can't keep a destroyed Activity alive the way the old inline `new Thread` did. */
public class GeocodingRepository {

    private static volatile GeocodingRepository instance;

    private final Context appContext;
    private final AppExecutors executors;

    public static GeocodingRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (GeocodingRepository.class) {
                if (instance == null) {
                    instance = new GeocodingRepository(context.getApplicationContext(), AppExecutors.getInstance());
                }
            }
        }
        return instance;
    }

    GeocodingRepository(@NonNull Context appContext, @NonNull AppExecutors executors) {
        this.appContext = appContext;
        this.executors = executors;
    }

    public void lookup(@NonNull String address, @NonNull Consumer<GeocodeResult> callback) {
        executors.diskIO().execute(() -> {
            GeocodeResult result = lookupBlocking(address);
            executors.mainThread().execute(() -> callback.accept(result));
        });
    }

    private GeocodeResult lookupBlocking(String address) {
        if (!Geocoder.isPresent()) {
            return GeocodeResult.failure("Address lookup isn't available on this device - enter lat/lng manually");
        }
        try {
            List<Address> results = new Geocoder(appContext, Locale.getDefault()).getFromLocationName(address, 1);
            if (results == null || results.isEmpty()) {
                return GeocodeResult.failure("Couldn't find that address");
            }
            Address found = results.get(0);
            return GeocodeResult.success(found.getLatitude(), found.getLongitude());
        } catch (Exception e) {
            // Network / geocoder-service hiccup.
            return GeocodeResult.failure("Couldn't find that address");
        }
    }
}
