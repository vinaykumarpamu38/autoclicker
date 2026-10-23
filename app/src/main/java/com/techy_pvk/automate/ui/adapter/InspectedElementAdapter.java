package com.techy_pvk.automate.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.techy_pvk.automate.R;
import com.techy_pvk.automate.model.InspectedElement;

import java.util.ArrayList;
import java.util.List;

public class InspectedElementAdapter extends RecyclerView.Adapter<InspectedElementAdapter.ViewHolder> {

    /** label = what kind of value this is ("Resource ID", "Text", "Content Description"). */
    public interface Listener {
        void onCopy(String label, String value);
    }

    private final List<InspectedElement> elements = new ArrayList<>();
    private final Listener listener;

    public InspectedElementAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<InspectedElement> newElements) {
        elements.clear();
        if (newElements != null) elements.addAll(newElements);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_inspected_element, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InspectedElement element = elements.get(position);

        if (element.depth == 0) {
            holder.depthLabel.setVisibility(View.VISIBLE);
            holder.depthLabel.setText("Tapped element");
        } else if (element.depth > 0) {
            holder.depthLabel.setVisibility(View.VISIBLE);
            holder.depthLabel.setText(element.depth + (element.depth == 1 ? " level up" : " levels up"));
        } else {
            holder.depthLabel.setVisibility(View.GONE);
        }

        holder.className.setText(element.className + (element.clickable ? "  \u2022 clickable" : ""));

        boolean hasId = !TextUtils.isEmpty(element.resourceId);
        holder.idText.setVisibility(hasId ? View.VISIBLE : View.GONE);
        if (hasId) holder.idText.setText("ID: " + element.resourceId);

        boolean hasText = !TextUtils.isEmpty(element.text);
        holder.textText.setVisibility(hasText ? View.VISIBLE : View.GONE);
        if (hasText) holder.textText.setText("Text: " + element.text);

        boolean hasDesc = !TextUtils.isEmpty(element.contentDescription);
        holder.descText.setVisibility(hasDesc ? View.VISIBLE : View.GONE);
        if (hasDesc) holder.descText.setText("Desc: " + element.contentDescription);

        // Tapping the row copies whichever value is most useful to paste into a step: prefer
        // the resource ID (most stable across app updates), then visible text, then description.
        holder.itemView.setOnClickListener(v -> {
            if (hasId) listener.onCopy("Resource ID", element.resourceId);
            else if (hasText) listener.onCopy("Text", element.text);
            else if (hasDesc) listener.onCopy("Content Description", element.contentDescription);
        });
    }

    @Override
    public int getItemCount() {
        return elements.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView depthLabel, className, idText, textText, descText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            depthLabel = itemView.findViewById(R.id.text_element_depth);
            className = itemView.findViewById(R.id.text_element_class);
            idText = itemView.findViewById(R.id.text_element_id);
            textText = itemView.findViewById(R.id.text_element_text);
            descText = itemView.findViewById(R.id.text_element_desc);
        }
    }
}
