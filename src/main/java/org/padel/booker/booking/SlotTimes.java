package org.padel.booker.booking;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Helpers for the upstream's decimal-hours x100 slot codes (700=07:00, 2050=20:30). A 30-minute
 * step is +50 in code units, a 60-minute step +100, etc.
 */
public final class SlotTimes {

    private SlotTimes() {}

    public static int codeToMinutes(int code) {
        int hours = code / 100;
        int minutes = Math.round((code % 100) * 0.6f);
        return hours * 60 + minutes;
    }

    public static String minutesToFmt(int totalMinutes) {
        int h = (totalMinutes / 60) % 24;
        int m = totalMinutes % 60;
        return String.format("%02d:%02d", h, m);
    }

    public static String fmt(int code) {
        return minutesToFmt(codeToMinutes(code));
    }

    public static String csv(List<Integer> codes) {
        return codes.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** End-of-booking "HH:mm" = start of the last slot + one slot's minutes. */
    public static String endFmt(List<Integer> codes, int slotMinutes) {
        if (codes.isEmpty()) return null;
        int lastStart = codeToMinutes(codes.get(codes.size() - 1));
        return minutesToFmt(lastStart + slotMinutes);
    }
}
