package com.techy_pvk.automate.repository;

/**
 * Decides whether a leftover builder draft belongs to the screen that is opening now. Pure
 * Java so the rule can be unit-tested - this is the logic that fixed "tapping the pencil on a
 * saved sequence opened a blank Add Sequence page" (a stale draft from a different sequence
 * was being resumed instead of the one that was asked for).
 */
public final class DraftPolicy {

    /** A leftover draft older than this is treated as abandoned rather than "the same session".
     *  Only matters for a brand-new sequence, which has no id to match against. */
    public static final long NEW_SEQUENCE_RESUME_WINDOW_MS = 5 * 60 * 1000;

    private DraftPolicy() {}

    /**
     * @param draftId     id stored in the draft, or null if there is no draft
     * @param draftSavedAt when the draft was written (epoch millis)
     * @param requestedId id of the sequence being edited, or null for "new sequence"
     * @param now         current time (epoch millis)
     */
    public static boolean belongsToRequest(String draftId, long draftSavedAt, String requestedId, long now) {
        if (draftId == null) return false;

        if (requestedId != null) {
            // Editing sequence X: only X's own draft counts - e.g. we were mid-edit and the
            // activity was reclaimed while backgrounded to pick a point.
            return requestedId.equals(draftId);
        }

        // Brand-new sequence: nothing to match an id against, so fall back to recency. A
        // few-seconds-old draft is the same in-progress session; an old one is an abandoned attempt.
        long age = now - draftSavedAt;
        return age >= 0 && age < NEW_SEQUENCE_RESUME_WINDOW_MS;
    }
}
