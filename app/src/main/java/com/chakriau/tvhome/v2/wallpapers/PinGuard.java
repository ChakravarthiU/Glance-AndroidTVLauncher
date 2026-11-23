package com.chakriau.tvhome.v2.wallpapers;

import java.security.SecureRandom;

/**
 * The upload page's PIN (Requirements.md 5.1): checked on every request, locked for a while after repeated wrong
 * attempts so it can't be guessed. Plain Java; the clock is passed in so the lockout is unit-tested.
 */
public final class PinGuard {

    public static final int MAX_WRONG = 5;
    public static final long LOCK_MS = 5 * 60_000L;
    public static final int LENGTH = 6;

    public enum Result { OK, WRONG, LOCKED }

    private String pin;
    private int wrong;
    private long lockedUntil;

    public PinGuard(String pin) {
        this.pin = pin;
    }

    /** A new random PIN of {@link #LENGTH} digits. */
    public static String newPin(SecureRandom random) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < LENGTH; i++) b.append((char) ('0' + random.nextInt(10)));
        return b.toString();
    }

    public synchronized String pin() {
        return pin;
    }

    /** Changes the PIN (Settings › Wallpapers › Change PIN) and clears any lock. */
    public synchronized void setPin(String newPin) {
        pin = newPin;
        wrong = 0;
        lockedUntil = 0;
    }

    public synchronized Result check(String supplied, long now) {
        if (now < lockedUntil) return Result.LOCKED;
        if (pin != null && constantTimeEquals(pin, supplied)) {
            wrong = 0;
            return Result.OK;
        }
        wrong++;
        if (wrong >= MAX_WRONG) {
            wrong = 0;
            lockedUntil = now + LOCK_MS;
            return Result.LOCKED;
        }
        return Result.WRONG;
    }

    /** Minutes left on a lock, rounded up (0 when not locked). */
    public synchronized int lockedMinutes(long now) {
        if (now >= lockedUntil) return 0;
        return (int) ((lockedUntil - now + 59_999) / 60_000);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}
