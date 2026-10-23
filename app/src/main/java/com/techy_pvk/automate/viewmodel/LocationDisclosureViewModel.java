package com.techy_pvk.automate.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.techy_pvk.automate.repository.PermissionRepository;
import com.techy_pvk.automate.util.Event;
import com.techy_pvk.automate.util.UiMessage;

/**
 * The state machine behind the Location disclosure screen. The screen only shows the disclosure
 * and two buttons; once the user taps Allow, THIS decides what happens next:
 *
 *   Allow -> (foreground not granted)               REQUEST_FOREGROUND
 *         -> (foreground ok, background wanted,     EXPLAIN_BACKGROUND  (a short extra dialog, then
 *             not granted)                          the system routes the user to "Allow all the time")
 *         -> otherwise                              done
 *
 * Android forbids asking for foreground and background location in one go on Android 11+, so
 * they are always two separate steps - and each one is only reached after the user has seen the
 * disclosure and chosen Allow.
 */
public class LocationDisclosureViewModel extends AndroidViewModel {

    public static final String KEY_INCLUDE_BACKGROUND = "include_background";

    public enum Step { REQUEST_FOREGROUND, EXPLAIN_BACKGROUND }

    private final PermissionRepository permissionRepository;
    private final boolean includeBackground;

    private final MutableLiveData<Event<Step>> step = new MutableLiveData<>();
    private final MutableLiveData<Event<UiMessage>> message = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> finishScreen = new MutableLiveData<>();

    public LocationDisclosureViewModel(@NonNull Application application, @NonNull SavedStateHandle savedState) {
        this(application, savedState, new PermissionRepository(application));
    }

    LocationDisclosureViewModel(@NonNull Application application, @NonNull SavedStateHandle savedState,
                                @NonNull PermissionRepository permissionRepository) {
        super(application);
        this.permissionRepository = permissionRepository;
        Boolean extra = savedState.get(KEY_INCLUDE_BACKGROUND);
        this.includeBackground = Boolean.TRUE.equals(extra);
    }

    /** Whether the disclosure must also explain background ("Allow all the time") access. */
    public boolean includesBackground() { return includeBackground; }

    public LiveData<Event<Step>> getStep() { return step; }
    public LiveData<Event<UiMessage>> getMessage() { return message; }
    public LiveData<Event<Boolean>> getFinishScreen() { return finishScreen; }

    /** The user read the disclosure and tapped Allow. */
    public void onAllowClicked() {
        advance();
    }

    /** The user tapped Don't Allow (or Back). Nothing is requested. */
    public void onDenyClicked() {
        finishScreen.setValue(new Event<>(Boolean.TRUE));
    }

    /** Result of the system's foreground location dialog (precise + approximate requested together). */
    public void onForegroundResult(boolean preciseGranted, boolean approximateGranted) {
        if (preciseGranted) {
            advance();
        } else if (approximateGranted) {
            // Android 12+ lets the user downgrade to "Approximate", which is too coarse for a
            // geofence of a few hundred metres.
            tell(UiMessage.longMessage("Please choose \"Precise\" location - approximate location is too "
                    + "coarse to tell when you arrive at or leave a place."));
            finishScreen.setValue(new Event<>(Boolean.TRUE));
        } else {
            tell(UiMessage.longMessage("Location wasn't allowed, so location triggers and Wi-Fi network "
                    + "names won't work. You can allow it any time from Settings."));
            finishScreen.setValue(new Event<>(Boolean.TRUE));
        }
    }

    /** The user chose "Not now" on the background dialog. */
    public void onBackgroundSkipped() {
        tell(UiMessage.longMessage("Location triggers will only work while AutoMate is open until you "
                + "allow background location."));
        finishScreen.setValue(new Event<>(Boolean.TRUE));
    }

    /** Result of the system's background location request. */
    public void onBackgroundResult(boolean granted) {
        if (!granted) {
            tell(UiMessage.longMessage("If you weren't asked, choose \"Allow all the time\" under Settings "
                    + "\u2192 Apps \u2192 AutoMate \u2192 Permissions \u2192 Location."));
        }
        finishScreen.setValue(new Event<>(Boolean.TRUE));
    }

    private void advance() {
        if (!permissionRepository.hasForegroundLocation()) {
            step.setValue(new Event<>(Step.REQUEST_FOREGROUND));
        } else if (includeBackground && !permissionRepository.hasBackgroundLocation()) {
            step.setValue(new Event<>(Step.EXPLAIN_BACKGROUND));
        } else {
            finishScreen.setValue(new Event<>(Boolean.TRUE));
        }
    }

    private void tell(UiMessage text) {
        message.setValue(new Event<>(text));
    }
}
