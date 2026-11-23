package com.chakriau.tvhome.v2.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;

/**
 * The bundled Inter fonts (OFL, {@code assets/fonts/}), loaded once per process. Light, Medium and Bold are the Latin
 * subset; text in other scripts falls back to the TV's own fonts.
 */
public final class Fonts {

    public enum Weight {
        LIGHT("Inter-Light.ttf"), REGULAR("Inter-Regular.ttf"), MEDIUM("Inter-Medium.ttf"), BOLD("Inter-Bold.ttf");

        final String file;

        Weight(String file) {
            this.file = file;
        }
    }

    private static final Typeface[] cache = new Typeface[Weight.values().length];

    private Fonts() {
    }

    public static synchronized Typeface get(Context context, Weight weight) {
        Typeface t = cache[weight.ordinal()];
        if (t == null) {
            try {
                t = Typeface.createFromAsset(context.getApplicationContext().getAssets(), "fonts/" + weight.file);
            } catch (RuntimeException e) {
                Log.w("TvHome", "font " + weight.file + " not loaded: " + e);
                t = weight == Weight.BOLD ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
            }
            cache[weight.ordinal()] = t;
        }
        return t;
    }
}
