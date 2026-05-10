import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DateUtilsTest {

    @Test
    void compareDatesByString() {
        assertTrue(DateUtils.isBefore("2026-01-01", "2026-01-02"));
        assertTrue(DateUtils.isAfter("2026-12-31", "2026-01-01"));
        assertFalse(DateUtils.isAfter("2026-01-01", "2026-01-01"));
    }

    @Test
    void addDaysWorks() {
        assertEquals("2026-02-02", DateUtils.addDays("2026-02-01", 1));
        assertEquals("2026-02-02 10:00", DateUtils.addDays("2026-02-01 10:00", 1));
    }

    @Test
    void formatRelativeTimeReturnsNonEmpty() {
        String r = DateUtils.formatRelativeTime(DateUtils.getCurrentDate());
        assertNotNull(r);
        assertFalse(r.isBlank());
    }
}

