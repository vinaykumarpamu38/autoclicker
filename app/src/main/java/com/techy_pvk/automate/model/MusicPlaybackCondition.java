package com.techy_pvk.automate.model;

/**
 * Which playback condition a MUSIC_PLAYBACK_STATE trigger fires on.
 *
 * STOPPED_OR_PAUSED only fires after a short debounce window (see NotificationTriggerService)
 * confirms the pause is real - not a track-to-track transition, which briefly reports a
 * non-playing state too. This is specifically what Samsung's Modes and Routines "music
 * playing/paused" condition gets wrong: it appears to react to any momentary non-PLAYING
 * state (including the SKIPPING_TO_NEXT/BUFFERING states a track change reports) as if it
 * were a real pause, which is why switching tracks briefly flips your ringer mode there.
 */
public enum MusicPlaybackCondition {
    STARTED, STOPPED_OR_PAUSED
}
