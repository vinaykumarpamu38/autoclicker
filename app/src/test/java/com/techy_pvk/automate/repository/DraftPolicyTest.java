package com.techy_pvk.automate.repository;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DraftPolicyTest {

    private static final long NOW = 1_000_000_000L;

    @Test
    public void noDraftNeverMatches() {
        assertFalse(DraftPolicy.belongsToRequest(null, NOW, null, NOW));
        assertFalse(DraftPolicy.belongsToRequest(null, NOW, "abc", NOW));
    }

    @Test
    public void editingOnlyResumesThatSequencesOwnDraft() {
        assertTrue(DraftPolicy.belongsToRequest("abc", NOW - 99_999_999L, "abc", NOW)); // age is irrelevant when editing
        assertFalse(DraftPolicy.belongsToRequest("other", NOW, "abc", NOW));            // the "blank edit page" bug
    }

    @Test
    public void newSequenceResumesOnlyAFreshDraft() {
        assertTrue(DraftPolicy.belongsToRequest("abc", NOW - 1_000, null, NOW));
        assertFalse(DraftPolicy.belongsToRequest("abc",
                NOW - DraftPolicy.NEW_SEQUENCE_RESUME_WINDOW_MS - 1, null, NOW));
    }

    @Test
    public void aDraftFromTheFutureIsNotTrusted() {
        assertFalse(DraftPolicy.belongsToRequest("abc", NOW + 5_000, null, NOW));
    }
}
