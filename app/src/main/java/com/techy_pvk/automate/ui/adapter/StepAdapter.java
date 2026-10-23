package com.techy_pvk.automate.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.techy_pvk.automate.R;
import com.techy_pvk.automate.model.GestureStep;

import java.util.ArrayList;
import java.util.List;

public class StepAdapter extends RecyclerView.Adapter<StepAdapter.ViewHolder> {

    private static final float ENABLED_ALPHA = 1f;
    private static final float DISABLED_ALPHA = 0.3f;

    public interface Listener {
        void onRemove(int position);
        void onMove(int position, int direction);
    }

    private final List<GestureStep> steps = new ArrayList<>();
    private final Listener listener;

    public StepAdapter(Listener listener) {
        this.listener = listener;
    }

    /** Replaces the shown steps. The whole list is rebound (not diffed) on purpose: each row's
     *  number and its enabled up/down arrows depend on its position, which changes for every row
     *  after an insert, remove or reorder - and the list is only ever a handful of items. */
    public void submitList(List<GestureStep> newSteps) {
        steps.clear();
        if (newSteps != null) steps.addAll(newSteps);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_step, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GestureStep step = steps.get(position);
        holder.text.setText((position + 1) + ". " + step.summary());

        boolean isFirst = position == 0;
        boolean isLast = position == steps.size() - 1;
        holder.up.setEnabled(!isFirst);
        holder.up.setAlpha(isFirst ? DISABLED_ALPHA : ENABLED_ALPHA);
        holder.down.setEnabled(!isLast);
        holder.down.setAlpha(isLast ? DISABLED_ALPHA : ENABLED_ALPHA);

        holder.up.setOnClickListener(v -> listener.onMove(holder.getBindingAdapterPosition(), -1));
        holder.down.setOnClickListener(v -> listener.onMove(holder.getBindingAdapterPosition(), 1));
        holder.remove.setOnClickListener(v -> listener.onRemove(holder.getBindingAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return steps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text;
        ImageButton up, down, remove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.text_step_summary);
            up = itemView.findViewById(R.id.button_move_up);
            down = itemView.findViewById(R.id.button_move_down);
            remove = itemView.findViewById(R.id.button_remove_step);
        }
    }
}