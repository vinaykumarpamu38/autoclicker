package com.techy_pvk.automate.ui;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * EdgeToEdge.enable() (called in every activity's onCreate, before setContentView) makes the
 * window draw behind the status bar / nav bar / display cutout - by itself that just means
 * content can end up hidden underneath them. This applies the system bars' insets as padding
 * so content stays clear of them instead.
 */
public final class EdgeToEdgeUtils {

    private EdgeToEdgeUtils() {}

    /** Pads the given root view by the system bars' insets on all four sides. Right for a
     *  screen that's just one scrollable/simple container - see applyMainScreenInsets for
     *  MainActivity's Toolbar+FAB layout, which needs different treatment per view. */
    public static void applyRootInsets(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    /** For a Toolbar sitting at the very top of the window: adds the top inset as extra top
     *  padding (on top of its existing height) so its title/icons clear the status bar,
     *  without shrinking the toolbar's own content area. */
    public static void applyToolbarTopInset(View toolbar) {
        int basePaddingLeft = toolbar.getPaddingLeft();
        int basePaddingRight = toolbar.getPaddingRight();
        int basePaddingBottom = toolbar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(basePaddingLeft + bars.left, bars.top, basePaddingRight + bars.right, basePaddingBottom);
            return insets;
        });
    }

    /** For a view anchored to the bottom (e.g. a FloatingActionButton): adds the bottom inset
     *  on top of its existing margin, so it clears a gesture nav bar instead of sitting under it. */
    public static void applyBottomInsetAsMargin(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            android.view.ViewGroup.MarginLayoutParams params =
                    (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.bottomMargin = bars.bottom + v.getResources().getDimensionPixelSize(
                    com.techy_pvk.automate.R.dimen.fab_base_margin);
            v.setLayoutParams(params);
            return insets;
        });
    }
}
