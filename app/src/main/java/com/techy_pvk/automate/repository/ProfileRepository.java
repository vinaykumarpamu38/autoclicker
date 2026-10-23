package com.techy_pvk.automate.repository;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.techy_pvk.automate.data.UserProfileStore;
import com.techy_pvk.automate.util.AppExecutors;
import com.techy_pvk.automate.util.PrefsLiveData;

/**
 * The on-device profile. Exposed as LiveData so every screen showing it (Profile, the drawer
 * greeting) updates the moment the name is edited anywhere. UserProfileStore is the only thing
 * that touches the preferences file - when Firebase Auth arrives, this class is the one place
 * that changes: the ViewModels above it keep working unmodified.
 */
public class ProfileRepository {

    /** Immutable view of the profile. */
    public static final class Profile {
        public final String displayName;
        public final long createdAtMillis;

        public Profile(String displayName, long createdAtMillis) {
            this.displayName = displayName == null ? "" : displayName;
            this.createdAtMillis = createdAtMillis;
        }

        public boolean hasName() {
            return !displayName.trim().isEmpty();
        }

        /** First letter, upper-cased, for the avatar circle - "?" until a name is set. */
        public String initial() {
            return hasName() ? displayName.trim().substring(0, 1).toUpperCase() : "?";
        }
    }

    private static volatile ProfileRepository instance;

    private final Context appContext;
    private final AppExecutors executors;
    private LiveData<Profile> profile; // created lazily, on the main thread

    public static ProfileRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (ProfileRepository.class) {
                if (instance == null) {
                    instance = new ProfileRepository(context.getApplicationContext(), AppExecutors.getInstance());
                }
            }
        }
        return instance;
    }

    ProfileRepository(@NonNull Context appContext, @NonNull AppExecutors executors) {
        this.appContext = appContext;
        this.executors = executors;
    }

    @MainThread
    @NonNull
    public LiveData<Profile> observe() {
        if (profile == null) {
            profile = new PrefsLiveData<Profile>(
                    appContext.getSharedPreferences(UserProfileStore.PREFS, Context.MODE_PRIVATE),
                    executors.diskIO()) {
                @Override
                protected Profile load() {
                    return new Profile(
                            UserProfileStore.getDisplayName(appContext),
                            UserProfileStore.getOrInitCreatedAt(appContext));
                }
            };
        }
        return profile;
    }

    public void setDisplayName(@NonNull String name) {
        executors.diskIO().execute(() -> UserProfileStore.setDisplayName(appContext, name));
    }
}
