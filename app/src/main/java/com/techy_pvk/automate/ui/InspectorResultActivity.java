package com.techy_pvk.automate.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.techy_pvk.automate.R;
import com.techy_pvk.automate.ui.adapter.InspectedElementAdapter;
import com.techy_pvk.automate.viewmodel.InspectorViewModel;

/**
 * Shows the results of an Inspect Screen scan - either everything found on the whole screen, or
 * (when you pointed at something) just the element under your marker plus its ancestor chain,
 * since the element itself often has no id/text but a parent does. Tap a row to copy its most
 * useful value to the clipboard, ready to paste into a Click by Resource ID / Text / Content
 * Description / Type Text step.
 *
 * This Activity is singleTop: running a second scan while it is open arrives via onNewIntent(),
 * which reloads - previously the old results simply stayed on screen.
 */
public class InspectorResultActivity extends AppCompatActivity {

    private InspectorViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_inspector_result);
        EdgeToEdgeUtils.applyRootInsets(findViewById(android.R.id.content));

        viewModel = new ViewModelProvider(this).get(InspectorViewModel.class);

        TextView title = findViewById(R.id.text_inspector_title);
        TextView empty = findViewById(R.id.text_inspector_empty);
        RecyclerView recyclerView = findViewById(R.id.recycler_inspector_results);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        InspectedElementAdapter adapter = new InspectedElementAdapter(this::copyToClipboard);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.button_close_inspector).setOnClickListener(v -> finish());

        viewModel.getState().observe(this, state -> {
            if (state == null) return;
            title.setText(state.title);
            empty.setText(state.emptyMessage);
            empty.setVisibility(state.showEmptyMessage ? View.VISIBLE : View.GONE);
            adapter.submitList(state.elements);
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        viewModel.reload();
    }

    private void copyToClipboard(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(this, "Copied " + label + ": " + value, Toast.LENGTH_SHORT).show();
    }
}
