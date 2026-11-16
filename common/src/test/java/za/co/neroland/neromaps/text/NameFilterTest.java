package za.co.neroland.neromaps.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NameFilterTest {

    @Test
    void stripsFormattingControlsAndBidi() {
        assertEquals("Home base", NameFilter.clean("\u00A7cHome\n\t  base\u202E"));
        assertEquals("ab", NameFilter.clean("a\u0000\u0007b"));
        assertEquals("", NameFilter.clean("   \u200F "));
    }

    @Test
    void clampsToThirtyTwoCodePoints() {
        String longName = "x".repeat(100);
        assertEquals(NameFilter.MAX_NAME, NameFilter.clean(longName).codePointCount(0, NameFilter.clean(longName).length()));
        String emoji = "\uD83D\uDDFA".repeat(40); // world-map emoji, a surrogate pair
        String c = NameFilter.clean(emoji);
        assertTrue(c.codePointCount(0, c.length()) <= NameFilter.MAX_NAME);
    }

    @Test
    void denyListAndEmpty() {
        assertEquals(NameFilter.Verdict.EMPTY, NameFilter.check("", ""));
        assertEquals(NameFilter.Verdict.OK, NameFilter.check("Mine", ""));
        assertEquals(NameFilter.Verdict.DENIED, NameFilter.check("My BadWord spot", "foo, badword"));
        assertEquals(NameFilter.Verdict.OK, NameFilter.check("Fine", "foo, badword"));
    }
}
