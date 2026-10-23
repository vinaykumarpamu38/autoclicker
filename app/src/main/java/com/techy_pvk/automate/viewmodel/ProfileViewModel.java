package com.techy_pvk.automate.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.techy_pvk.automate.repository.ProfileRepository;
import com.techy_pvk.automate.util.Event;
import com.techy_pvk.automate.util.UiMessage;

/** Profile screen: the saved profile, and a simple view/edit toggle for the display name. */
public class ProfileViewModel extends AndroidViewModel {

    private final ProfileRepository profileRepository;

    private final LiveData<ProfileRepository.Profile> profile;
    private final MutableLiveData<Boolean> editing = new MutableLiveData<>(false);
    private final MutableLiveData<Event<UiMessage>> message = new MutableLiveData<>();

    /** Used by the default ViewModelProvider factory. */
    public ProfileViewModel(@NonNull Application application) {
        this(application, ProfileRepository.getInstance(application));
    }

    /** Dependency-injecting constructor, for tests. */
    ProfileViewModel(@NonNull Application application, @NonNull ProfileRepository profileRepository) {
        super(application);
        this.profileRepository = profileRepository;
        this.profile = profileRepository.observe();
    }

    public LiveData<ProfileRepository.Profile> getProfile() { return profile; }
    public LiveData<Boolean> getEditing() { return editing; }
    public LiveData<Event<UiMessage>> getMessage() { return message; }

    /** The name to pre-fill the edit box with. */
    @NonNull
    public String getCurrentName() {
        ProfileRepository.Profile current = profile.getValue();
        return current != null ? current.displayName : "";
    }

    /** The single Edit / Save button. While viewing it starts editing; while editing it saves
     *  whatever is in the box. */
    public void onEditButtonClicked(String typedName) {
        if (!Boolean.TRUE.equals(editing.getValue())) {
            editing.setValue(true);
            return;
        }

        String name = typedName == null ? "" : typedName.trim();
        if (name.isEmpty()) {
            message.setValue(new Event<>(UiMessage.shortMessage("Name can't be empty")));
            return;
        }
        profileRepository.setDisplayName(name);
        editing.setValue(false);
        message.setValue(new Event<>(UiMessage.shortMessage("Profile updated")));
    }
}
