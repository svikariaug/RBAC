import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ValidationUtilsTest {

    @Test
    void validUsername() {
        assertTrue(ValidationUtils.isValidUsername("abc"));
        assertTrue(ValidationUtils.isValidUsername("user_123"));
        assertFalse(ValidationUtils.isValidUsername("ab"));
        assertFalse(ValidationUtils.isValidUsername("user name"));
    }

    @Test
    void validEmail() {
        assertTrue(ValidationUtils.isValidEmail("a@b.com"));
        assertTrue(ValidationUtils.isValidEmail("john.doe@company.org"));
        assertFalse(ValidationUtils.isValidEmail("no-at.example.com"));
        assertFalse(ValidationUtils.isValidEmail("user@.ru"));
    }

    @Test
    void validDateAcceptsDateAndDateTime() {
        assertTrue(ValidationUtils.isValidDate("2026-05-09"));
        assertTrue(ValidationUtils.isValidDate("2026-05-09 23:59"));
        assertTrue(ValidationUtils.isValidDate("2026-05-09 23:59:59"));
        assertFalse(ValidationUtils.isValidDate("09-05-2026"));
    }

    @Test
    void requireNonEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> ValidationUtils.requireNonEmpty("", "field"));
        assertDoesNotThrow(() -> ValidationUtils.requireNonEmpty("x", "field"));
    }
}

