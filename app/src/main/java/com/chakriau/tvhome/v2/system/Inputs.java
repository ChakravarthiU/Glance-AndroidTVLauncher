package com.chakriau.tvhome.v2.system;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The TV's sources for the Inputs button (Requirements.md 5.9): HDMI ports (or the CEC device on them), then AV and
 * other passthrough inputs, then Live TV when the TV has a tuner. Choosing one switches straight to it through the
 * TV's own system TV app. Shared by every theme; a theme only draws the list.
 */
public final class Inputs {

    private static final String TAG = "TvHomeInputs";

    public enum Kind { HDMI, OTHER, LIVE_TV }

    /** One row of the list. */
    public static final class Source {
        public final String label;
        public final Kind kind;
        /** True or false for HDMI rows whose state the TV reports sensibly; null when unknown (AV, Live TV). */
        public final Boolean connected;
        final Intent intent;

        Source(String label, Kind kind, Boolean connected, Intent intent) {
            this.label = label;
            this.kind = kind;
            this.connected = connected;
            this.intent = intent;
        }
    }

    private Inputs() {
    }

    /** Whether the TV has any source to switch to (streaming boxes have none: the button is hidden then). */
    public static boolean any(Context c) {
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return false;
        try {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput() && !info.isHidden(c)) return true;
                if (info.getType() == TvInputInfo.TYPE_TUNER && isSystem(info)) return true;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "input list: " + e);
        }
        return false;
    }

    /** The rows, in order. Empty when the TV has no inputs (or no TV input service). */
    public static List<Source> list(Context c) {
        List<Source> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        List<TvInputInfo> inputs;
        try {
            inputs = new ArrayList<>(tim.getTvInputList());
        } catch (RuntimeException e) {
            Log.w(TAG, "input list: " + e);
            return out;
        }
        // HDMI first, then the rest; each group by label.
        inputs.sort((a, b) -> {
            boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
            if (ha != hb) return ha ? -1 : 1;
            return label(c, a).compareToIgnoreCase(label(c, b));
        });

        Set<String> ids = new HashSet<>();
        for (TvInputInfo i : inputs) ids.add(i.getId());
        // An HDMI-CEC device (an Apple TV, a soundbar) is an input of its own whose parent is its port: it takes
        // the port's row, and a bare port is listed only when nothing on it speaks CEC.
        Map<String, List<TvInputInfo>> cec = new HashMap<>();
        for (TvInputInfo i : inputs) {
            String parent = i.getParentId();
            if (parent == null || !ids.contains(parent) || !i.isPassthroughInput() || i.isHidden(c)) continue;
            List<TvInputInfo> onPort = cec.get(parent);
            if (onPort == null) cec.put(parent, onPort = new ArrayList<>());
            onPort.add(i);
        }
        int ports = 0, connected = 0;
        for (TvInputInfo i : inputs) {
            if (!i.isPassthroughInput() || i.getType() != TvInputInfo.TYPE_HDMI || child(i, ids)) continue;
            ports++;
            if (state(tim, i.getId()) == TvInputManager.INPUT_STATE_CONNECTED) connected++;
        }
        boolean bogus = InputLabels.hdmiStateBogus(ports, connected);

        List<String> tuners = new ArrayList<>();
        for (TvInputInfo i : inputs) {
            if (i.getType() == TvInputInfo.TYPE_TUNER && isSystem(i)) tuners.add(i.getId());
            if (!i.isPassthroughInput() || i.isHidden(c) || child(i, ids)) continue;
            boolean hdmi = i.getType() == TvInputInfo.TYPE_HDMI;
            String port = InputLabels.port(label(c, i), i.getId(), hdmi);
            String custom = InputLabels.tidy(customLabel(c, i));
            List<TvInputInfo> devices = cec.get(i.getId());
            if (devices != null) {
                for (TvInputInfo d : devices) {
                    String name = InputLabels.tidy(customLabel(c, d));
                    if (name == null) name = custom != null ? custom : label(c, d);
                    out.add(source(c, InputLabels.row(name, port), Kind.HDMI,
                            bogus ? null : state(tim, d.getId()) == TvInputManager.INPUT_STATE_CONNECTED,
                            TvContract.buildChannelUriForPassthroughInput(d.getId())));
                }
                continue;
            }
            // Analog AV jacks have no plug detection, and some TVs call them always connected: no state for them.
            Boolean on = !hdmi || bogus ? null : state(tim, i.getId()) == TvInputManager.INPUT_STATE_CONNECTED;
            out.add(source(c, InputLabels.row(custom, port), hdmi ? Kind.HDMI : Kind.OTHER, on,
                    TvContract.buildChannelUriForPassthroughInput(i.getId())));
        }
        out.removeIf(s -> s.intent == null);

        String tuner = InputLabels.pickTuner(tuners);
        if (tuner != null) {
            Source live = source(c, "Live TV", Kind.LIVE_TV, null, TvContract.buildChannelUriForPassthroughInput(tuner));
            if (live.intent == null) live = liveTvApp(c);
            if (live != null) out.add(live);
        }
        return out;
    }

    /** Switches to the source. */
    public static void open(Activity a, Source s) {
        try {
            a.startActivity(s.intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "could not open " + s.label + ": " + e);
            Toast.makeText(a, s.label + " could not be opened", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * A row whose intent views the input's channel URI in the TV's own system TV app. Apps installed later (a media
     * player) can claim the same URI, and two handlers would bring up a chooser instead of the input, so the intent is
     * aimed at the system app. No intent when nothing on the TV handles it.
     */
    private static Source source(Context c, String label, Kind kind, Boolean connected, Uri uri) {
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, TvContract.Channels.CONTENT_ITEM_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String pkg = systemHandler(c, view);
        return new Source(label, kind, connected, pkg == null ? null : view.setPackage(pkg));
    }

    private static String systemHandler(Context c, Intent view) {
        List<ResolveInfo> handlers;
        try {
            handlers = c.getPackageManager().queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY);
        } catch (RuntimeException e) {
            return null;
        }
        String any = null;
        for (ResolveInfo r : handlers) {
            if (r.activityInfo == null) continue;
            String pkg = r.activityInfo.packageName;
            if (pkg.equals("android") || pkg.equals(c.getPackageName())) continue;
            if ((r.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) return pkg;
            if (any == null) any = pkg;
        }
        return any;   // no system handler (unusual): the only other app that handles it
    }

    /** The TV app of a known maker, opened as an app, for a tuner its URI can't open. */
    private static Source liveTvApp(Context c) {
        String[] known = {"com.sony.dtv.tvx", "com.google.android.tv", "com.android.tv",
                "com.mediatek.wwtv.tvcenter", "com.droidlogic.tvsource", "com.tcl.tv", "com.mitv.tvhome.atv"};
        PackageManager pm = c.getPackageManager();
        for (String pkg : known) {
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) return new Source("Live TV", Kind.LIVE_TV, null, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        return null;
    }

    private static boolean child(TvInputInfo i, Set<String> ids) {
        return i.getParentId() != null && ids.contains(i.getParentId());
    }

    private static boolean isSystem(TvInputInfo i) {
        try {
            return (i.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static int state(TvInputManager tim, String id) {
        try {
            return tim.getInputState(id);
        } catch (RuntimeException e) {
            return TvInputManager.INPUT_STATE_DISCONNECTED;
        }
    }

    private static String label(Context c, TvInputInfo i) {
        try {
            CharSequence l = i.loadLabel(c);
            return l == null ? i.getId() : l.toString();
        } catch (RuntimeException e) {
            return i.getId();
        }
    }

    private static CharSequence customLabel(Context c, TvInputInfo i) {
        try {
            return i.loadCustomLabel(c);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
