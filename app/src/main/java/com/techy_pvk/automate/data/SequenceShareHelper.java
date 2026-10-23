package com.techy_pvk.automate.data;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.gson.Gson;

import com.techy_pvk.automate.model.AutomationSequence;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public final class SequenceShareHelper {

    public static final String MIME_TYPE = "application/vnd.automate.sequence";
    private static final String FILE_EXTENSION = ".automateseq";

    private SequenceShareHelper() {}

    /**
     * Writes the sequence to a shareable file and returns the ready-to-launch chooser Intent.
     * Does file I/O, so call it from a background thread (SequenceRepository does).
     */
    public static Intent createShareIntent(Context context, AutomationSequence sequence) throws IOException {
        File dir = new File(context.getCacheDir(), "shared_sequences");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Couldn't create share directory");

        String safeName = sequence.name != null
                ? sequence.name.replaceAll("[^a-zA-Z0-9_-]", "_") : "";
        if (safeName.isEmpty()) safeName = "sequence";
        File file = new File(dir, safeName + FILE_EXTENSION);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(new Gson().toJson(sequence));
        }

        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(MIME_TYPE);
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, sequence.name);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(shareIntent, "Share \"" + sequence.name + "\" via");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return chooser;
    }

    /** Convenience wrapper that writes the file and launches the chooser on the calling thread. */
    public static void shareSequence(Context context, AutomationSequence sequence) {
        try {
            context.startActivity(createShareIntent(context, sequence));
        } catch (IOException e) {
            Toast.makeText(context, "Couldn't create the share file", Toast.LENGTH_SHORT).show();
        }
    }
}
