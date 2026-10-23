package com.techy_pvk.automate.ui.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.techy_pvk.automate.R;
import com.techy_pvk.automate.model.AppInfo;

import java.util.List;

/** The "Inspect Screen" picker: which app to look at, and whether to read the whole screen or
 *  point at one element. Pure view code - the choice goes back through {@link OnChosen}. */
public final class InspectDialog {

    public interface OnChosen {
        void onChosen(AppInfo app, boolean pointMode);
    }

    private InspectDialog() {}

    @NonNull
    public static AlertDialog show(@NonNull Context context, @NonNull List<AppInfo> apps,
                                   @NonNull OnChosen onChosen) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_inspect_target, null);
        Spinner appSpinner = view.findViewById(R.id.spinner_inspect_app);
        RadioGroup modeGroup = view.findViewById(R.id.radio_group_inspect_mode);
        appSpinner.setAdapter(StepDialogs.appAdapter(context, apps));

        return new AlertDialog.Builder(context)
                .setTitle("Inspect Screen")
                .setView(view)
                .setPositiveButton("Continue", (dialog, which) -> {
                    AppInfo selectedApp = (AppInfo) appSpinner.getSelectedItem();
                    boolean pointMode = modeGroup.getCheckedRadioButtonId() == R.id.radio_inspect_point;
                    onChosen.onChosen(selectedApp, pointMode);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
