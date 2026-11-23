package com.chakriau.tvhome.v2.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * Home's one vertical page (Requirements.md section 4). Holds a single tall child and scrolls it smoothly to wherever
 * the {@link Policy} says the newly focused view should be seen. Unlike ScrollView it never jumps and never scrolls on
 * its own when a key has nowhere to go, so Home only moves when focus does.
 */
final class HomeScroller extends FrameLayout {

    /** Decides the scroll position for a focused view. */
    interface Policy {
        /**
         * @param rect the focused view's bounds in the content's coordinates
         * @return the wanted scroll position (clamped by the scroller)
         */
        int scrollFor(View focused, Rect rect, int viewportHeight);
    }

    /** Told on every scroll step, for the dim and the corner clock. */
    interface Listener {
        void onScrolled(int scrollY);
    }

    private final Rect rect = new Rect();
    private Policy policy;
    private Listener listener;
    private ValueAnimator animator;

    HomeScroller(Context c) {
        super(c);
        setClipChildren(false);
        setClipToPadding(false);
    }

    void setPolicy(Policy p) {
        policy = p;
    }

    void setListener(Listener l) {
        listener = l;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
        if (getChildCount() > 0) {
            getChildAt(0).measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
        }
        setScrollY(clamp(getScrollY()));
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (policy == null || focused == null || getHeight() == 0) return;
        focused.getDrawingRect(rect);
        offsetDescendantRectToMyCoords(focused, rect);
        animateTo(policy.scrollFor(focused, rect, getHeight()));
    }

    /** Scrolls smoothly to a position. */
    void animateTo(int y) {
        int target = clamp(y);
        if (animator != null) animator.cancel();
        if (target == getScrollY()) return;
        animator = ValueAnimator.ofInt(getScrollY(), target);
        animator.setDuration(300);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(a -> setScrollY((Integer) a.getAnimatedValue()));
        animator.start();
    }

    /** Jumps straight to a position (Home pressed again, or Home shown). */
    void jumpTo(int y) {
        if (animator != null) animator.cancel();
        setScrollY(clamp(y));
    }

    private int clamp(int y) {
        int max = getChildCount() == 0 ? 0 : Math.max(0, getChildAt(0).getMeasuredHeight() - getHeight());
        return Math.max(0, Math.min(y, max));
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (listener != null) listener.onScrolled(t);
    }
}
