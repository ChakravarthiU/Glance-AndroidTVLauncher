package com.chakriau.tvhome.v2.settings;

import android.view.View;

/** One page of the launcher's Settings, shown to the right of the menu. */
interface SettingsPage {

    /** The name in the left menu and the breadcrumb. */
    String title();

    /** Builds the page's view. Called once, the first time the page is shown. */
    View createView(Ui ui);

    /** Moves focus to the page's first control. False when the page has nothing to focus yet. */
    boolean focusFirst();

    /** Back while focus is in the page. True if the page used it (for example to close a menu). */
    boolean onBack();

    /** The page became the one on screen (also when Settings comes back to the front). */
    void onShown();

    /** Another page was chosen, or Settings went to the background. */
    default void onHidden() {
    }

    /** Settings is closing. */
    void onDestroy();
}
