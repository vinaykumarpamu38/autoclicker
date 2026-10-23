package com.techy_pvk.automate.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.techy_pvk.automate.R;

/**
 * Shown before every path that leads to Android's Accessibility Settings screen - required by
 * Google Play's Accessibility API policy (AutoMate isn't eligible for isAccessibilityTool, since
 * it's an automation tool, not a disability-accessibility tool, so this prominent disclosure +
 * explicit consent is mandatory, not optional).
 *
 * Built to satisfy the two reasons Play Console cited for an earlier rejection:
 *   - "Non compliant design": two clearly separate buttons (Allow / Don't Allow), neither
 *     auto-dismisses, and Back just closes the screen with nothing requested - it is never
 *     treated as consent, exactly like tapping Don't Allow.
 *   - "Insufficient prominent disclosure": the text names the actual features instead of a
 *     generic one-line justification.
 *
 * There is deliberately no ViewModel: the screen has no state and makes no decisions. (Whether
 * the service is currently enabled is PermissionRepository.isAccessibilityServiceEnabled().)
 */
public class AccessibilityDisclosureActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_accessibility_disclosure);
        EdgeToEdgeUtils.applyRootInsets(findViewById(android.R.id.content));

        findViewById(R.id.btn_disclosure_allow).setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            finish();
        });

        // Explicit decline - does not navigate anywhere, does not request anything.
        findViewById(R.id.btn_disclosure_deny).setOnClickListener(v -> finish());
    }
}
