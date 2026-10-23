package com.techy_pvk.automate.ui;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.techy_pvk.automate.R;
import com.techy_pvk.automate.ui.adapter.NotificationLogAdapter;
import com.techy_pvk.automate.util.EventObserver;
import com.techy_pvk.automate.util.ToastUtil;
import com.techy_pvk.automate.viewmodel.NotificationLogViewModel;

/** Read-only view of notifications captured by any sequence's "Notification received" trigger
 *  that has "store notification data" turned on - newest first, with a Clear option. The list is
 *  live: it updates while the screen is open when a new notification is captured. */
public class NotificationLogActivity extends AppCompatActivity {

    private final DialogHolder dialogs = new DialogHolder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_notification_log);
        EdgeToEdgeUtils.applyRootInsets(findViewById(android.R.id.content));

        NotificationLogViewModel viewModel = new ViewModelProvider(this).get(NotificationLogViewModel.class);

        View emptyState = findViewById(R.id.text_log_empty_state);
        RecyclerView recyclerView = findViewById(R.id.recycler_notification_log);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        NotificationLogAdapter adapter = new NotificationLogAdapter();
        recyclerView.setAdapter(adapter);

        findViewById(R.id.button_clear_log).setOnClickListener(v -> viewModel.onClearClicked());

        viewModel.getEntries().observe(this, adapter::submitList);
        viewModel.getShowEmptyState().observe(this, show ->
                emptyState.setVisibility(Boolean.TRUE.equals(show) ? View.VISIBLE : View.GONE));
        viewModel.getMessage().observe(this, new EventObserver<>(message -> ToastUtil.show(this, message)));
        viewModel.getConfirmClear().observe(this, new EventObserver<>(ignored ->
                dialogs.track(new AlertDialog.Builder(this)
                        .setTitle("Clear the notification log?")
                        .setMessage("This can't be undone.")
                        .setPositiveButton("Clear", (dialog, which) -> viewModel.onClearConfirmed())
                        .setNegativeButton("Cancel", null)
                        .show())));
    }

    @Override
    protected void onDestroy() {
        dialogs.dismiss();
        super.onDestroy();
    }
}
