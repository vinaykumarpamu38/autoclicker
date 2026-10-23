package com.techy_pvk.automate.ui;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.techy_pvk.automate.R;
import com.techy_pvk.automate.repository.ProfileRepository;
import com.techy_pvk.automate.util.EventObserver;
import com.techy_pvk.automate.util.ToastUtil;
import com.techy_pvk.automate.viewmodel.ProfileViewModel;

/** Shows the on-device profile and lets the user edit their display name. */
public class ProfileActivity extends AppCompatActivity {

    private ProfileViewModel viewModel;

    private TextView avatarInitial;
    private TextView displayNameText;
    private EditText displayNameInput;
    private TextView memberSinceText;
    private Button editButton;

    /** After a rotation the EditText restores what the user had typed; don't overwrite it. */
    private boolean restoredFromState;
    private boolean wasEditing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);
        EdgeToEdgeUtils.applyRootInsets(findViewById(android.R.id.content));
        setTitle("Profile");
        restoredFromState = savedInstanceState != null;

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        avatarInitial = findViewById(R.id.text_avatar_initial);
        displayNameText = findViewById(R.id.text_display_name);
        displayNameInput = findViewById(R.id.input_display_name);
        memberSinceText = findViewById(R.id.text_member_since);
        editButton = findViewById(R.id.btn_edit_profile);

        editButton.setOnClickListener(v ->
                viewModel.onEditButtonClicked(displayNameInput.getText().toString()));

        viewModel.getProfile().observe(this, this::renderProfile);
        viewModel.getEditing().observe(this, this::renderEditing);
        viewModel.getMessage().observe(this, new EventObserver<>(message -> ToastUtil.show(this, message)));
    }

    private void renderProfile(ProfileRepository.Profile profile) {
        if (profile == null) return;
        displayNameText.setText(profile.hasName() ? profile.displayName : "Set your name");
        avatarInitial.setText(profile.initial());
        memberSinceText.setText("On this device since "
                + DateFormat.format("MMM d, yyyy", profile.createdAtMillis));
    }

    private void renderEditing(Boolean editingValue) {
        boolean editing = Boolean.TRUE.equals(editingValue);
        if (editing && !wasEditing && !restoredFromState) {
            displayNameInput.setText(viewModel.getCurrentName());
            displayNameInput.setSelection(displayNameInput.length());
        }
        wasEditing = editing;
        restoredFromState = false; // only the very first render is a restore

        displayNameInput.setVisibility(editing ? View.VISIBLE : View.GONE);
        displayNameText.setVisibility(editing ? View.GONE : View.VISIBLE);
        editButton.setText(editing ? "Save" : "Edit Profile");
    }
}
