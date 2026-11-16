package za.co.neroland.neromaps.text;

import java.util.Locale;

/**
 * The one name filter for everything a player can label on the map: waypoint names now, signpost labels in a later
 * stage. It strips control characters, formatting codes ({@code \u00A7}), line breaks and bidirectional overrides,
 * collapses runs of whitespace, trims, clamps to {@link #MAX_NAME} code points, and rejects a name that is empty
 * afterwards or contains a word from the server's deny list. Pure; no Minecraft types.
 */
public final class NameFilter {

    /** The length limit shared by waypoints and signposts, in code points. */
    public static final int MAX_NAME = 32;

    /** The formatting-code marker, U+00A7. */
    private static final int SECTION = 0xA7;

    private NameFilter() {
    }

    /** {@code raw} cleaned and clamped to {@link #MAX_NAME} code points (possibly empty). */
    public static String clean(String raw) {
        return clean(raw, MAX_NAME);
    }

    /** {@code raw} cleaned and clamped to {@code max} code points (possibly empty). */
    public static String clean(String raw, int max) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(raw.length(), max * 2));
        boolean space = false;
        int count = 0;
        for (int i = 0; i < raw.length() && count < max; ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == SECTION) {
                if (i < raw.length()) {
                    i += Character.charCount(raw.codePointAt(i)); // the formatting code letter goes with it
                }
                continue;
            }
            if (isBidi(cp) || Character.getType(cp) == Character.FORMAT
                    || Character.getType(cp) == Character.PRIVATE_USE || Character.getType(cp) == Character.SURROGATE
                    || Character.getType(cp) == Character.UNASSIGNED) {
                continue;
            }
            if (Character.isISOControl(cp) && !Character.isWhitespace(cp)) {
                continue;
            }
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                space = out.length() > 0;
                continue;
            }
            if (space) {
                if (count + 1 >= max) {
                    break;
                }
                out.append(' ');
                count++;
                space = false;
            }
            out.appendCodePoint(cp);
            count++;
        }
        return out.toString();
    }

    private static boolean isBidi(int cp) {
        return (cp >= 0x202A && cp <= 0x202E) || (cp >= 0x2066 && cp <= 0x2069) || cp == 0x200E || cp == 0x200F
                || cp == 0x061C;
    }

    /** Why a name was refused, or {@link Verdict#OK}. */
    public enum Verdict { OK, EMPTY, DENIED }

    /** Checks an already-cleaned name against the deny list (comma-separated words, case-insensitive). */
    public static Verdict check(String cleaned, String denyList) {
        if (cleaned == null || cleaned.isEmpty()) {
            return Verdict.EMPTY;
        }
        if (denyList == null || denyList.isBlank()) {
            return Verdict.OK;
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        for (String word : denyList.split(",")) {
            String w = word.trim().toLowerCase(Locale.ROOT);
            if (!w.isEmpty() && lower.contains(w)) {
                return Verdict.DENIED;
            }
        }
        return Verdict.OK;
    }
}
