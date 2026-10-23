package com.techy_pvk.automate.repository;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Reads the name (SSID) of the Wi-Fi network the phone is on right now. Android only reveals
 *  it to apps that hold location permission - which is why the Wi-Fi trigger's disclosure
 *  mentions location. */
public class WifiRepository {

    private final Context appContext;

    public WifiRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    /** The connected network's name without quotes, or null if not on Wi-Fi / not permitted. */
    @Nullable
    @SuppressWarnings("deprecation") // getConnectionInfo() is deprecated from API 31 but still works for this foreground read
    public String currentSsid() {
        WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) return null;
        WifiInfo info = wifiManager.getConnectionInfo();
        if (info == null) return null;
        String ssid = info.getSSID();
        if (ssid == null || ssid.contains("<unknown ssid>")) return null;
        if (ssid.length() >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            return ssid.substring(1, ssid.length() - 1);
        }
        return ssid;
    }
}
