package com.techy_pvk.automate.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.techy_pvk.automate.R;
import com.techy_pvk.automate.model.AutomationSequence;
import com.techy_pvk.automate.ui.adapter.SequenceAdapter;
import com.techy_pvk.automate.ui.dialog.InspectDialog;
import com.techy_pvk.automate.util.EventObserver;
import com.techy_pvk.automate.util.ToastUtil;
import com.techy_pvk.automate.viewmodel.LocationDisclosureViewModel;
import com.techy_pvk.automate.viewmodel.MainViewModel;

/**
 * Home screen ("Your daily tasks"). All it does is draw what {@link MainViewModel} exposes and
 * forward taps to it. There is no database, SharedPreferences or permission logic in here.
 */
public class MainActivity extends AppCompatActivity implements SequenceAdapter.Listener {

    private MainViewModel viewModel;
    private DrawerLayout drawerLayout;
    private final DialogHolder dialogs = new DialogHolder();

    private final ActivityResultLauncher<String[]> runtimePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> { /* nothing to do: each feature re-checks its own permission when it runs */ });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        EdgeToEdgeUtils.applyToolbarTopInset(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        setUpDrawer(toolbar);

        FloatingActionButton newSequenceButton = findViewById(R.id.btn_new_sequence);
        EdgeToEdgeUtils.applyBottomInsetAsMargin(newSequenceButton);
        newSequenceButton.setOnClickListener(v ->
                startActivity(new Intent(this, SequenceBuilderActivity.class)));

        RecyclerView recyclerView = findViewById(R.id.recycler_sequences);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        SequenceAdapter adapter = new SequenceAdapter(this);
        recyclerView.setAdapter(adapter);

        View emptyState = findViewById(R.id.text_empty_state);

        viewModel.getSequences().observe(this, adapter::submitList);
        viewModel.getShowEmptyState().observe(this, show ->
                emptyState.setVisibility(Boolean.TRUE.equals(show) ? View.VISIBLE : View.GONE));

        observeEvents();
    }

    private void setUpDrawer(Toolbar toolbar) {
        NavigationView navView = findViewById(R.id.nav_view);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // Back closes the drawer only while it is open; otherwise the callback is disabled and
        // the system handles Back normally (this is what makes predictive-back work on Android 14+,
        // where overriding onBackPressed() is silently ignored).
        OnBackPressedCallback closeDrawerOnBack = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                drawerLayout.closeDrawers();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, closeDrawerOnBack);
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override public void onDrawerOpened(View drawerView) { closeDrawerOnBack.setEnabled(true); }
            @Override public void onDrawerClosed(View drawerView) { closeDrawerOnBack.setEnabled(false); }
        });
        closeDrawerOnBack.setEnabled(drawerLayout.isDrawerOpen(GravityCompat.START));

        TextView greeting = navView.getHeaderView(0).findViewById(R.id.text_drawer_greeting);
        viewModel.getGreeting().observe(this, greeting::setText);

        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_inspect_screen) {
                viewModel.onInspectScreenRequested();
            } else if (id == R.id.nav_notification_log) {
                startActivity(new Intent(this, NotificationLogActivity.class));
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            }
            // nav_sequences: already home - just close the drawer.
            drawerLayout.closeDrawers();
            return true;
        });
    }

    private void observeEvents() {
        viewModel.getMessage().observe(this, new EventObserver<>(message -> ToastUtil.show(this, message)));
        viewModel.getMinimizeApp().observe(this, new EventObserver<>(ignored -> moveTaskToBack(true)));
        viewModel.getShareChooser().observe(this, new EventObserver<>(this::startActivity));
        viewModel.getRequestRuntimePermissions().observe(this,
                new EventObserver<>(runtimePermissionLauncher::launch));
        viewModel.getPromptAccessibility().observe(this,
                new EventObserver<>(ignored -> showAccessibilityPrompt()));
        viewModel.getPromptLocation().observe(this,
                new EventObserver<>(this::showLocationPrompt));
        viewModel.getInspectDialogApps().observe(this, new EventObserver<>(apps ->
                dialogs.track(InspectDialog.show(this, apps, viewModel::startInspection))));
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.onScreenResumed();
    }

    @Override
    protected void onDestroy() {
        dialogs.dismiss(); // a dialog that outlives its Activity leaks its window on rotation
        super.onDestroy();
    }

    // ---- Dialogs (plain rendering; the ViewModel decided that they should appear) ----

    /** Without the Accessibility service nothing runs at all. "Enable" only opens the disclosure
     *  screen - the real Allow / Don't Allow choice lives there, so dismissing this is never consent. */
    private void showAccessibilityPrompt() {
        dialogs.track(new AlertDialog.Builder(this)
                .setTitle("AutoMate isn't active yet")
                .setMessage("AutoMate needs Android's Accessibility Service turned on before any "
                        + "sequence can run. Until then, your saved sequences and triggers won't do anything.")
                .setPositiveButton("Enable", (dialog, which) ->
                        startActivity(new Intent(this, AccessibilityDisclosureActivity.class)))
                .setNegativeButton("Not Now", null)
                .show());
    }

    private void showLocationPrompt(boolean includeBackground) {
        dialogs.track(new AlertDialog.Builder(this)
                .setTitle("Location access needed")
                .setMessage(includeBackground
                        ? "One of your sequences runs when you arrive at or leave a place. That needs "
                        + "location access, including while AutoMate is closed. Until then it won't run."
                        : "One of your sequences runs when you join a specific Wi-Fi network. Android only "
                        + "reveals a network's name to apps that have location access. Until then it won't run.")
                .setPositiveButton("Continue", (dialog, which) -> {
                    Intent intent = new Intent(this, LocationDisclosureActivity.class);
                    intent.putExtra(LocationDisclosureViewModel.KEY_INCLUDE_BACKGROUND, includeBackground);
                    startActivity(intent);
                })
                .setNegativeButton("Not Now", null)
                .show());
    }

    // ---- SequenceAdapter.Listener ----

    @Override
    public void onRun(AutomationSequence sequence) {
        viewModel.onRunClicked(sequence);
    }

    @Override
    public void onEdit(AutomationSequence sequence) {
        Intent intent = new Intent(this, SequenceBuilderActivity.class);
        intent.putExtra(SequenceBuilderActivity.EXTRA_SEQUENCE_ID, sequence.id);
        startActivity(intent);
    }

    @Override
    public void onShare(AutomationSequence sequence) {
        viewModel.onShareClicked(sequence);
    }

    @Override
    public void onDelete(AutomationSequence sequence) {
        dialogs.track(new AlertDialog.Builder(this)
                .setTitle("Delete \"" + sequence.name + "\"?")
                .setMessage("This can't be undone.")
                .setPositiveButton("Delete", (dialog, which) -> viewModel.deleteSequence(sequence))
                .setNegativeButton("Cancel", null)
                .show());
    }
}
