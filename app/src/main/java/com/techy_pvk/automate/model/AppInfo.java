package com.techy_pvk.automate.model;

/** Minimal wrapper for an installed, launchable app - used to populate the app-picker Spinner. */
public class AppInfo {
    public String label;
    public String packageName;

    public AppInfo(String label, String packageName) {
        this.label = label;
        this.packageName = packageName;
    }

    @Override
    public String toString() {
        // ArrayAdapter calls toString() to render each Spinner row.
        return label;
    }
}
