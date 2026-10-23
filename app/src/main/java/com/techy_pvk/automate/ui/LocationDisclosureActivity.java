package com.techy_pvk.automate.ui;

import android.Manifest;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.techy_pvk.automate.R;
import com.techy_pvk.automate.util.EventObserver;
import com.techy_pvk.automate.util.ToastUtil;
import com.techy_pvk.automate.viewmodel.LocationDisclosureViewModel;

import java.util.Map;

/**
 * Prominent disclosure shown BEFORE any location permission is requested (Google Play's User Data
 * policy). It has two clearly separate buttons, nothing auto-dismisses, and Back counts as
 * "Don't Allow". Nothing is requested until the user taps Allow.
 *
 * Pass {@link LocationDisclosureViewModel#KEY_INCLUDE_BACKGROUND} = true when the user's
 * sequences need location while AutoMate is closed; the disclosure then also explains that, and
 * the "Allow all the time" step follows the normal location dialog.
 *
 * What happens after Allow is decided by {@link LocationDisclosureViewModel}; this class only
 * shows the disclosure and runs the system permission requests it asks for.
 */
public class LocationDisclosureActivity extends AppCompatActivity {

    private LocationDisclosureViewModel viewModel;
    private final DialogHolder dialogs = new DialogHolder();

    private final ActivityResultLauncher<String[]> foregroundLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onForegroundResult);

    private final ActivityResultLauncher<String> backgroundLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> viewModel.onBackgroundResult(granted));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_location_disclosure);
        EdgeToEdgeUtils.applyRootInsets(findViewById(android.R.id.content));

        viewModel = new ViewModelProvider(this).get(LocationDisclosureViewModel.class);

        findViewById(R.id.text_location_background).setVisibility(
                viewModel.includesBackground() ? View.VISIBLE : View.GONE);

        findViewById(R.id.btn_location_allow).setOnClickListener(v -> viewModel.onAllowClicked());
        // Explicit decline - requests nothing. (Back behaves the same: the screen just closes.)
        findViewById(R.id.btn_location_deny).setOnClickListener(v -> viewModel.onDenyClicked());

        viewModel.getStep().observe(this, new EventObserver<>(this::onStep));
        viewModel.getMessage().observe(this, new EventObserver<>(message -> ToastUtil.show(this, message)));
        viewModel.getFinishScreen().observe(this, new EventObserver<>(ignored -> finish()));
    }

    @Override
    protected void onDestroy() {
        dialogs.dismiss();
        super.onDestroy();
    }

    private void onStep(LocationDisclosureViewModel.Step step) {
        switch (step) {
            case REQUEST_FOREGROUND:
                // Precise and approximate are requested together: on Android 12+ the user can
                // downgrade to approximate, which the ViewModel then explains is too coarse.
                foregroundLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION});
                break;
            case EXPLAIN_BACKGROUND:
                showBackgroundExplanation();
                break;
        }
    }

    private void onForegroundResult(Map<String, Boolean> result) {
        boolean precise = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
        boolean approximate = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
        viewModel.onForegroundResult(precise, approximate);
    }

    /** Android 11+ won't show a normal dialog for background location - it sends the user to a
     *  Settings page. Say so first, so that page isn't a surprise. */
    private void showBackgroundExplanation() {
        dialogs.track(new AlertDialog.Builder(this)
                .setTitle(R.string.location_background_explain_title)
                .setMessage(R.string.location_background_explain_message)
                .setPositiveButton(R.string.location_background_explain_continue, (dialog, which) ->
                        backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                .setNegativeButton(R.string.location_background_explain_skip, (dialog, which) ->
                        viewModel.onBackgroundSkipped())
                .setOnCancelListener(dialog -> viewModel.onBackgroundSkipped())
                .show());
    }
}
