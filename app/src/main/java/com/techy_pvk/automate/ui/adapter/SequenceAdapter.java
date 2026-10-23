package com.techy_pvk.automate.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.techy_pvk.automate.R;
import com.techy_pvk.automate.model.AutomationSequence;

import java.util.ArrayList;
import java.util.List;

/** Pure rendering: shows whatever list it's given and reports taps. It holds no Context and does
 *  no I/O - deciding what a tap MEANS belongs to MainViewModel. */
public class SequenceAdapter extends RecyclerView.Adapter<SequenceAdapter.ViewHolder> {

    public interface Listener {
        void onRun(AutomationSequence sequence);
        void onEdit(AutomationSequence sequence);
        void onShare(AutomationSequence sequence);
        void onDelete(AutomationSequence sequence);
    }

    private final List<AutomationSequence> items = new ArrayList<>();
    private final Listener listener;

    public SequenceAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<AutomationSequence> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sequence, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AutomationSequence sequence = items.get(position);
        holder.name.setText(sequence.name);

        String stepsWord = sequence.steps.size() == 1 ? "step" : "steps";
        String repeatText = sequence.repeatCount == 0
                ? "loops until stopped"
                : "runs " + sequence.repeatCount + "x";
        holder.subtitle.setText(sequence.steps.size() + " " + stepsWord + " \u00b7 " + repeatText);

        holder.runButton.setOnClickListener(v -> listener.onRun(sequence));
        holder.editButton.setOnClickListener(v -> listener.onEdit(sequence));
        holder.shareButton.setOnClickListener(v -> listener.onShare(sequence));
        holder.deleteButton.setOnClickListener(v -> listener.onDelete(sequence));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, subtitle;
        ImageButton runButton, editButton, shareButton, deleteButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_sequence_name);
            subtitle = itemView.findViewById(R.id.text_sequence_subtitle);
            runButton = itemView.findViewById(R.id.button_run_sequence);
            editButton = itemView.findViewById(R.id.button_edit_sequence);
            shareButton = itemView.findViewById(R.id.button_share_sequence);
            deleteButton = itemView.findViewById(R.id.button_delete_sequence);
        }
    }
}