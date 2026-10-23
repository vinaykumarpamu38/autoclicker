package com.techy_pvk.automate.repository;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import com.techy_pvk.automate.model.AutomationSequence;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads and parses a shared ".automateseq" file. The file comes from outside the app (WhatsApp,
 * Drive, a file manager, anyone) so it is treated as hostile input: it is size-capped before it
 * is read into memory, and anything that parses is then run through SequenceSanitizer.
 */
public final class SequenceFileReader {

    /** A real sequence is a few KB of JSON; 1 MB is generous and stops a huge file from
     *  exhausting memory. */
    public static final int MAX_BYTES = 1024 * 1024;

    private SequenceFileReader() {}

    /** Thrown when the file exceeds MAX_BYTES. */
    public static final class TooLargeException extends IOException {
        public TooLargeException() {
            super("File is larger than " + MAX_BYTES + " bytes");
        }
    }

    public static String readUtf8(@NonNull Context context, @NonNull Uri uri) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            return readUtf8(in, MAX_BYTES);
        }
    }

    /** Reads at most maxBytes; throws TooLargeException rather than silently truncating. */
    static String readUtf8(InputStream in, int maxBytes) throws IOException {
        if (in == null) throw new IOException("Empty stream");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) throw new TooLargeException();
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * @return a cleaned sequence, or null if the JSON parsed but holds nothing usable (no valid steps)
     * @throws JsonParseException if the text isn't a sequence at all
     */
    public static AutomationSequence parse(String json) throws JsonParseException {
        AutomationSequence raw = new Gson().fromJson(json, AutomationSequence.class);
        return SequenceSanitizer.sanitize(raw);
    }
}
