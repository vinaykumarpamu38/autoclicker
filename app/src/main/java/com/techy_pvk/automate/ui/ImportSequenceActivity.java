package com.techy_pvk.automate.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.techy_pvk.automate.util.EventObserver;
import com.techy_pvk.automate.util.UiMessage;
import com.techy_pvk.automate.util.ToastUtil;
import com.techy_pvk.automate.viewmodel.ImportSequenceViewModel;

/**
 * Launched when the user opens a shared ".automateseq" file (WhatsApp, Drive, a file manager...).
 * Shows no screen of its own: it reads the file, asks "Import this?", and finishes.
 *
 * The file is untrusted input - apps like WhatsApp hand over any shared file as a generic
 * "application/octet-stream", so this can be reached with content that isn't a sequence at all.
 * Reading, size-limiting, parsing and sanitizing all happen in {@link ImportSequenceViewModel}
 * (off the main thread); nothing here can throw on bad content.
 */
public class ImportSequenceActivity extends AppCompatActivity {

    private final DialogHolder dialogs = new DialogHolder();
    private ImportSequenceViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ImportSequenceViewModel.class);

        viewModel.getState().observe(this, this::render);
        viewModel.getImported().observe(this, new EventObserver<>(name -> {
            ToastUtil.show(this, UiMessage.shortMessage("Imported \"" + name + "\""));
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }));

        // Idempotent: a rotation does not re-read the file.
        viewModel.load(getIntent() != null ? getIntent().getData() : null);
    }

    private void render(ImportSequenceViewModel.UiState state) {
        switch (state.status) {
            case LOADING:
                dialogs.dismiss();
                break;
            case ERROR:
                dialogs.dismiss();
                ToastUtil.show(this, UiMessage.longMessage(state.errorMessage));
                finish();
                break;
            case READY:
                showConfirmation(state);
                break;
        }
    }

    private void showConfirmation(ImportSequenceViewModel.UiState state) {
        String stepsWord = state.stepCount == 1 ? "step" : "steps";
        StringBuilder message = new StringBuilder()
                .append('"').append(state.sequenceName).append("\" (")
                .append(state.stepCount).append(' ').append(stepsWord)
                .append(") will be added to your saved sequences.");
        if (state.automaticTriggerSummary != null) {
            // A shared file can make a sequence start by itself, so say so before saving it.
            message.append("\n\nThis sequence can start by itself:\n")
                    .append(state.automaticTriggerSummary)
                    .append("\n\nOnly import it if you trust whoever sent it.");
        }

        dialogs.track(new AlertDialog.Builder(this)
                .setTitle("Import sequence?")
                .setMessage(message.toString())
                .setCancelable(false)
                .setPositiveButton("Import", (dialog, which) -> viewModel.confirmImport())
                .setNegativeButton("Cancel", (dialog, which) -> finish())
                .show());
    }

    @Override
    protected void onDestroy() {
        dialogs.dismiss();
        super.onDestroy();
    }
}
