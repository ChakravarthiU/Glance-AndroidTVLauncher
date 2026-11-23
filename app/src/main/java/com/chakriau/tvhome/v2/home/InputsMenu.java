package com.chakriau.tvhome.v2.home;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.chakriau.tvhome.v2.system.Inputs;
import com.chakriau.tvhome.v2.ui.Fonts;

import java.util.List;

/**
 * The Inputs list (Requirements.md 5.9): a glass panel under the Inputs button, HDMI ports first, then AV and
 * others, then Live TV, with a line between kinds. OK switches straight to the source.
 */
final class InputsMenu {

    private static final int CONNECTED = Color.rgb(120, 220, 150);

    private final HomeKit kit;
    private PopupWindow popup;

    InputsMenu(HomeKit kit) {
        this.kit = kit;
    }

    void show(View anchor) {
        List<Inputs.Source> sources = Inputs.list(kit.activity);
        if (sources.isEmpty()) {
            Toast.makeText(kit.activity, "No inputs to switch to on this TV", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout panel = new LinearLayout(kit.activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(kit.px(14), kit.px(20), kit.px(14), kit.px(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(244, 22, 27, 44));
        bg.setStroke(kit.line(2), Color.argb(60, 255, 255, 255));
        bg.setCornerRadius(kit.px(26));
        panel.setBackground(bg);
        TextView head = kit.text("INPUTS", 21, HomeKit.TEXT_2, Fonts.Weight.MEDIUM);
        head.setLetterSpacing(0.14f);
        head.setPadding(kit.px(18), 0, kit.px(18), kit.px(12));
        panel.addView(head);

        View first = null;
        Inputs.Kind previous = null;
        for (Inputs.Source src : sources) {
            if (previous != null && previous != src.kind) {
                View line = new View(kit.activity);
                line.setBackgroundColor(Color.argb(50, 255, 255, 255));
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        kit.line(2));
                llp.setMargins(kit.px(14), kit.px(8), kit.px(14), kit.px(8));
                panel.addView(line, llp);
            }
            previous = src.kind;
            View row = row(src);
            panel.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            if (first == null) first = row;
        }

        popup = new PopupWindow(panel, kit.px(600), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // lets Back close it
        popup.setOutsideTouchable(true);
        popup.setElevation(kit.px(24));
        popup.setOnDismissListener(() -> popup = null);
        popup.showAsDropDown(anchor, 0, kit.px(14));
        View f = first;
        panel.post(f::requestFocus);
    }

    void dismiss() {
        if (popup != null) popup.dismiss();
    }

    private View row(Inputs.Source src) {
        LinearLayout row = new LinearLayout(kit.activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setMinimumHeight(kit.px(72));
        row.setPadding(kit.px(18), kit.px(10), kit.px(20), kit.px(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(kit.px(14));
        bg.setColor(Color.TRANSPARENT);
        row.setBackground(bg);
        Icons.Monitor glyph = new Icons.Monitor(kit.px(32), HomeKit.TEXT, src.kind == Inputs.Kind.LIVE_TV);
        ImageView icon = new ImageView(kit.activity);
        icon.setImageDrawable(glyph);
        row.addView(icon, new LinearLayout.LayoutParams(kit.px(32), kit.px(32)));
        TextView label = kit.text(src.label, 29, HomeKit.TEXT, Fonts.Weight.REGULAR);
        label.setShadowLayer(0, 0, 0, 0);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(kit.px(18), 0, kit.px(12), 0);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView state = null;
        if (src.connected != null) {
            state = kit.text(src.connected ? "● Connected" : "Not connected", 21,
                    src.connected ? CONNECTED : HomeKit.TEXT_2, Fonts.Weight.REGULAR);
            state.setShadowLayer(0, 0, 0, 0);
            row.addView(state);
        }
        final TextView st = state;
        row.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Color.WHITE : Color.TRANSPARENT);
            int fg = has ? HomeKit.TEXT_ON_WHITE : HomeKit.TEXT;
            glyph.setColor(fg);
            label.setTextColor(fg);
            label.setTypeface(Fonts.get(kit.activity, has ? Fonts.Weight.MEDIUM : Fonts.Weight.REGULAR));
            if (st != null) st.setTextColor(has ? HomeKit.TEXT_ON_WHITE
                    : Boolean.TRUE.equals(src.connected) ? CONNECTED : HomeKit.TEXT_2);
        });
        row.setOnClickListener(v -> {
            dismiss();
            kit.actions.openInput(src);
        });
        row.setContentDescription(src.connected == null ? src.label
                : src.label + (src.connected ? ", connected" : ", not connected"));
        return row;
    }
}
