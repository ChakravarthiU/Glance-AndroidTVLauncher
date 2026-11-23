package com.chakriau.tvhome.v2.home;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chakriau.tvhome.v2.R;
import com.chakriau.tvhome.v2.apps.AppArt;
import com.chakriau.tvhome.v2.ui.Fonts;
import com.chakriau.tvhome.v2.watching.Artwork;
import com.chakriau.tvhome.v2.watching.ContinueWatching;
import com.chakriau.tvhome.v2.watching.WatchingRow;

import java.util.Locale;
import java.util.TimeZone;

/**
 * Theme 1's Continue watching row (Requirements.md 5.7; mockup {@code design/theme1-home-scrolled.png}): a header,
 * one row of 378 × 212 cards that slide off the screen edges, and a caption ("3 of 10 · latest item from each app,
 * up to 10"). Becomes "Recently opened" when Watch Next can't be read; hidden while there is nothing to show.
 */
public final class WatchingRowView {

    private static final int CARD_W = 378, CARD_H = 212, CARD_GAP = 26;
    private static final int CARD_ROOM = 22;       // room above and below the cards for the focused one to grow
    /** Tag key for what a card shows (the plain tag stays empty, so a card is never mistaken for an app tile). */
    private static final int TAG_CARD = R.id.tag_card;

    private final HomeKit kit;
    private final AppArt art;
    private final Artwork artwork;
    private final int side;
    private final LinearLayout section;
    private final TextView header, caption;
    private final CardScroller scroll;
    private final LinearLayout row;
    private WatchingRow.Row shown;

    /** {@code sideDesignPx}: the page's side margin, which the cards scroll past to the screen edge. */
    public WatchingRowView(HomeKit kit, AppArt art, int sideDesignPx) {
        this.kit = kit;
        this.art = art;
        this.artwork = new Artwork(kit.activity);
        this.side = sideDesignPx;

        section = new LinearLayout(kit.activity);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setClipChildren(false);
        section.setClipToPadding(false);
        section.setVisibility(View.GONE);   // until there is something to show

        header = kit.text("Continue watching", 36, HomeKit.TEXT, Fonts.Weight.BOLD);
        section.addView(header);

        row = new LinearLayout(kit.activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        // Inset by the page margin inside a full-width scroller, so cards slide off the screen edge as in the mockup.
        row.setPadding(kit.px(side), kit.px(CARD_ROOM), kit.px(side), kit.px(CARD_ROOM));
        scroll = new CardScroller(kit.activity);
        scroll.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = -kit.px(side);
        slp.rightMargin = -kit.px(side);
        slp.topMargin = kit.px(26 - CARD_ROOM);
        section.addView(scroll, slp);

        caption = kit.text("", 26, HomeKit.TEXT_2, Fonts.Weight.REGULAR);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = kit.px(40 - CARD_ROOM);
        section.addView(caption, clp);
    }

    /** The whole section (header, cards, caption), to add to the page. */
    public View view() {
        return section;
    }

    public boolean contains(View v) {
        return HomeKit.isIn(v, section);
    }

    /** Back to the first card (Home pressed again). */
    public void scrollToStart() {
        scroll.scrollTo(0, 0);
    }

    public void onTrimMemory() {
        artwork.clear();
    }

    public void onDestroy() {
        artwork.shutdown();
    }

    /**
     * Shows a new row, keeping focus on the same card (or the same place) if it was in the row. {@code focusRoot} is
     * the page; {@code focusStart} puts focus where Home starts, if the row emptied under it.
     */
    public void show(WatchingRow.Row r, View focusRoot, Runnable focusStart) {
        if (shown != null && shown.key().equals(r.key())) return;   // unchanged: keep cards and focus
        shown = r;

        View focused = focusRoot.findFocus();
        boolean hadFocus = focused != null && HomeKit.isIn(focused, row);
        int focusedIndex = hadFocus ? row.indexOfChild(cardOf(focused)) : -1;
        String focusedCard = focusedIndex >= 0 ? (String) row.getChildAt(focusedIndex).getTag(TAG_CARD) : null;

        row.removeAllViews();
        boolean recent = r.kind == WatchingRow.Kind.RECENTLY_OPENED;
        header.setText(recent ? "Recently opened" : "Continue watching");
        int n = r.cards.size();
        for (int i = 0; i < n; i++) {
            View card = card(r.cards.get(i), i, n);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(kit.px(CARD_W), kit.px(CARD_H));
            if (i > 0) lp.leftMargin = kit.px(CARD_GAP);
            row.addView(card, lp);
        }
        section.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
        updateCaption(0);

        if (!hadFocus) return;
        View again = null;
        for (int i = 0; i < row.getChildCount() && focusedCard != null; i++) {
            if (focusedCard.equals(row.getChildAt(i).getTag(TAG_CARD))) again = row.getChildAt(i);
        }
        if (again == null && n > 0) again = row.getChildAt(Math.min(focusedIndex, n - 1));
        View target = again;
        focusRoot.post(() -> {
            if (target != null) target.requestFocus();
            else focusStart.run();
        });
    }

    private View cardOf(View v) {
        View cur = v;
        while (cur != null && cur.getParent() != row) {
            cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
        }
        return cur;
    }

    private void updateCaption(int index) {
        if (shown == null) return;
        int n = shown.cards.size();
        String what = shown.kind == WatchingRow.Kind.RECENTLY_OPENED
                ? "apps opened from Home, newest first"
                : (shown.perApp == 1 ? "latest item from each app" : "up to " + shown.perApp
                + " from each app") + ", up to " + shown.total;
        caption.setText((Math.min(index, n - 1) + 1) + " of " + n + " · " + what);
    }

    /**
     * One card: the programme's picture (or the app's banner until it loads, or when it has none), the app's name,
     * title, episode and time left, and a progress bar. Focused, it grows 8%, gets a white border and a Resume label.
     * OK resumes in the app; hold OK shows App info, as on any app (Requirements.md 5.6).
     */
    private View card(WatchingRow.Card c, int index, int count) {
        int w = kit.px(CARD_W), h = kit.px(CARD_H);
        FrameLayout t = new FrameLayout(kit.activity);
        t.setFocusable(true);
        t.setTag(TAG_CARD, c.item == null ? c.app.packageName : c.app.packageName + "#" + c.item.id);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, cardColors(c.app.packageName));
        bg.setCornerRadius(kit.px(22));
        t.setBackground(bg);
        t.setClipToOutline(true);
        t.setElevation(kit.px(6));

        // The app's banner first; the programme's own picture covers it once loaded.
        ImageView appImage = new ImageView(kit.activity);
        t.addView(appImage, HomeKit.match());
        art.load(c.app, appImage, a -> {
            appImage.setImageDrawable(a.drawable);
            if (a.isBanner) {
                appImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                appImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                appImage.setPadding(w / 3, h / 6, w / 3, h / 2);
            }
        });
        ImageView picture = new ImageView(kit.activity);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        t.addView(picture, HomeKit.match());
        if (c.item != null && c.item.artUri != null) {
            artwork.load(c.item.artUri, w, h, picture, b -> {
                if (b != null) picture.setImageBitmap(b);
            });
        }

        // A dark fade under the text, so it reads over any picture.
        View scrim = new View(kit.activity);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{Color.argb(215, 6, 8, 16), Color.argb(120, 6, 8, 16), Color.TRANSPARENT}));
        t.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(h * 0.72f),
                Gravity.BOTTOM));

        if (c.item != null) {
            TextView chip = kit.text(c.app.label.toUpperCase(Locale.getDefault()), 20, HomeKit.TEXT,
                    Fonts.Weight.BOLD);
            chip.setShadowLayer(0, 0, 0, 0);
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setMaxWidth(Math.round(w * 0.55f));
            chip.setLetterSpacing(0.04f);
            chip.setPadding(kit.px(12), kit.px(7), kit.px(12), kit.px(7));
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setColor(Color.argb(170, 10, 12, 22));
            chipBg.setCornerRadius(kit.px(9));
            chip.setBackground(chipBg);
            FrameLayout.LayoutParams chlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
            chlp.setMargins(kit.px(16), kit.px(16), 0, 0);
            t.addView(chip, chlp);
        }

        // Resume (or Play, or Open) at the top right, shown only on the focused card.
        LinearLayout action = new LinearLayout(kit.activity);
        action.setOrientation(LinearLayout.HORIZONTAL);
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setPadding(kit.px(12), kit.px(6), kit.px(14), kit.px(6));
        GradientDrawable actionBg = new GradientDrawable();
        actionBg.setColor(Color.WHITE);
        actionBg.setCornerRadius(kit.px(9));
        action.setBackground(actionBg);
        ImageView play = new ImageView(kit.activity);
        play.setImageDrawable(new Icons.Play(kit.px(18), HomeKit.TEXT_ON_WHITE));
        action.addView(play, new LinearLayout.LayoutParams(kit.px(18), kit.px(18)));
        TextView actionText = kit.text(c.item == null ? "Open" : ContinueWatching.action(c.item), 21,
                HomeKit.TEXT_ON_WHITE, Fonts.Weight.BOLD);
        actionText.setShadowLayer(0, 0, 0, 0);
        actionText.setPadding(kit.px(8), 0, 0, 0);
        action.addView(actionText);
        action.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        alp.setMargins(0, kit.px(16), kit.px(16), 0);
        t.addView(action, alp);

        // Title and the line under it.
        LinearLayout texts = new LinearLayout(kit.activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(kit.px(20), 0, kit.px(20), kit.px(26));
        TextView title = kit.text(c.item == null ? c.app.label : c.item.title, 29, HomeKit.TEXT, Fonts.Weight.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        String sub = c.item == null
                ? ContinueWatching.opened(c.openedAt, System.currentTimeMillis(), TimeZone.getDefault())
                : ContinueWatching.subtitle(c.item);
        if (!sub.isEmpty()) {
            TextView subtitle = kit.text(sub, 21, Color.argb(225, 235, 238, 246), Fonts.Weight.REGULAR);
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setPadding(0, kit.px(8), 0, 0);
            texts.addView(subtitle);
        }
        t.addView(texts, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        // How much is watched, along the bottom edge.
        float progress = c.item == null ? 0f : ContinueWatching.progress(c.item);
        if (progress > 0f) {
            View track = new View(kit.activity);
            track.setBackgroundColor(Color.argb(70, 255, 255, 255));
            t.addView(track, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.px(6),
                    Gravity.BOTTOM));
            View fill = new View(kit.activity);
            fill.setBackgroundColor(Color.WHITE);
            t.addView(fill, new FrameLayout.LayoutParams(Math.max(kit.px(6), Math.round(w * progress)), kit.px(6),
                    Gravity.BOTTOM | Gravity.START));
        }

        View border = new View(kit.activity);
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(kit.px(22));
        ring.setStroke(kit.px(5), Color.WHITE);
        border.setBackground(ring);
        border.setVisibility(View.INVISIBLE);
        t.addView(border, HomeKit.match());

        t.setOnFocusChangeListener((v, has) -> {
            border.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
            action.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                    .translationZ(has ? kit.px(16) : 0).setDuration(140).start();
            if (has) updateCaption(index);
        });
        // Left on the first card and Right on the last stay put, instead of jumping to the grid below.
        t.setOnKeyListener((v, code, e) -> e.getAction() == KeyEvent.ACTION_DOWN
                && ((code == KeyEvent.KEYCODE_DPAD_LEFT && index == 0)
                || (code == KeyEvent.KEYCODE_DPAD_RIGHT && index == count - 1)));
        t.setOnClickListener(v -> kit.actions.resume(c));
        t.setOnLongClickListener(v -> {
            kit.actions.showAppInfo(c.app);
            return true;
        });
        String desc = c.item == null ? c.app.label : c.app.label + ", " + c.item.title;
        t.setContentDescription(sub.isEmpty() ? desc : desc + ", " + sub);
        return t;
    }

    /** A colour pair per app, for cards before (or without) a picture, like the mockup's coloured cards. */
    private static int[] cardColors(String pkg) {
        float hue = (pkg.hashCode() & 0x7fffffff) % 360;
        int a = Color.HSVToColor(new float[]{hue, 0.62f, 0.52f});
        int b = Color.HSVToColor(new float[]{(hue + 28) % 360, 0.7f, 0.72f});
        return new int[]{a, b};
    }

    /**
     * A horizontal row that keeps the focused card a page margin away from the screen edges (a plain
     * HorizontalScrollView would push it flush against the edge).
     */
    private final class CardScroller extends HorizontalScrollView {
        CardScroller(Context c) {
            super(c);
            setHorizontalScrollBarEnabled(false);
            setHorizontalFadingEdgeEnabled(false);
            setOverScrollMode(View.OVER_SCROLL_NEVER);
            setClipChildren(false);
            setFocusable(false);
        }

        @Override
        protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
            if (getChildCount() == 0) return 0;
            int width = getWidth();
            int current = getScrollX();
            int margin = kit.px(side);
            int want = current;
            if (r.right + margin > current + width) want = r.right + margin - width;
            if (r.left - margin < want) want = r.left - margin;
            int max = Math.max(0, getChildAt(0).getWidth() - width);
            want = Math.max(0, Math.min(want, max));
            return want - current;
        }
    }
}
