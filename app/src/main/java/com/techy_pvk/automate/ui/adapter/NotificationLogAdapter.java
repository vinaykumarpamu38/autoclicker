package com.techy_pvk.automate.ui.adapter;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.techy_pvk.automate.R;
import com.techy_pvk.automate.model.NotificationLogEntry;

import java.util.ArrayList;
import java.util.List;

public class NotificationLogAdapter extends RecyclerView.Adapter<NotificationLogAdapter.ViewHolder> {

    private final List<NotificationLogEntry> entries = new ArrayList<>();

    public void submitList(List<NotificationLogEntry> newEntries) {
        entries.clear();
        if (newEntries != null) entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationLogEntry entry = entries.get(position);
        CharSequence time = DateFormat.format("MMM d, h:mm a", entry.timestampMillis);
        holder.header.setText((entry.appLabel != null ? entry.appLabel : entry.packageName) + " \u2022 " + time
                + "  \u2192 ran \"" + entry.sequenceName + "\"");
        holder.title.setText(entry.title == null || entry.title.isEmpty() ? "(no title)" : entry.title);
        holder.text.setText(entry.text == null ? "" : entry.text);
        holder.text.setVisibility(entry.text == null || entry.text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView header, title, text;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            header = itemView.findViewById(R.id.text_log_header);
            title = itemView.findViewById(R.id.text_log_title);
            text = itemView.findViewById(R.id.text_log_text);
        }
    }
}
