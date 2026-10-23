package com.techy_pvk.automate.ui;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.techy_pvk.automate.repository.SequenceRepository;
import com.techy_pvk.automate.service.SequenceRunner;

/**
 * No visible UI (see its manifest theme) - this exists purely so an app shortcut (or, on
 * Samsung devices, potentially a Modes and Routines "Other apps" action - see AppShortcuts'
 * class doc) can trigger one specific saved sequence directly, the same way tapping Run on the
 * main screen does, without the main screen ever becoming visible.
 *
 * A trampoline with no state and no screen, so it has no ViewModel: it asks the repository for
 * the sequence, fires the run, and finishes.
 */
public class RunSequenceShortcutActivity extends AppCompatActivity {

    public static final String EXTRA_SEQUENCE_ID = "sequence_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Application context: the lookup finishes after this Activity may already be gone.
        Context appContext = getApplicationContext();
        String sequenceId = getIntent().getStringExtra(EXTRA_SEQUENCE_ID);
        if (sequenceId == null) {
            tellAndFinish(appContext, "That sequence no longer exists");
            return;
        }

        SequenceRepository.getInstance(appContext).getById(sequenceId, sequence -> {
            if (sequence == null || sequence.steps == null || sequence.steps.isEmpty()) {
                tellAndFinish(appContext, "That sequence no longer exists");
                return;
            }
            SequenceRunner.run(appContext, sequence.id);
            tellAndFinish(appContext, "Running \"" + sequence.name + "\"\u2026");
        });
    }

    private void tellAndFinish(Context appContext, String text) {
        Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show();
        finish();
    }
}
