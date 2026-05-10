import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;

class FormatUtilsTest {

    @Test
    void tableHasBorders() {
        String t = FormatUtils.formatTable(
                new String[]{"A", "B"},
                List.of(
                        new String[]{"1", "2"},  
                        new String[]{"3", "4"}   
                )
        );
        assertTrue(t.contains("+"));
        assertTrue(t.contains("|"));
        assertTrue(t.contains("A"));
        assertTrue(t.contains("1"));
    }

    @Test
    void boxWrapsText() {
        String b = FormatUtils.formatBox("Hello");
        assertTrue(b.contains("Hello"));
        assertTrue(b.startsWith("+"));
    }
}

