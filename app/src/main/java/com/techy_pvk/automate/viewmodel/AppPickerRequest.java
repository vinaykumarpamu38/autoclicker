package com.techy_pvk.automate.viewmodel;

import com.techy_pvk.automate.model.AppInfo;

import java.util.List;

/** "Here are the installed apps you asked for - show the dialog that needs them." */
public final class AppPickerRequest {

    /** Which dialog asked, so the screen knows which one to open with the list. */
    public enum Purpose { OPEN_APP_STEP, NOTIFICATION_TRIGGER, MUSIC_TRIGGER }

    public final Purpose purpose;
    public final List<AppInfo> apps;

    public AppPickerRequest(Purpose purpose, List<AppInfo> apps) {
        this.purpose = purpose;
        this.apps = apps;
    }
}
