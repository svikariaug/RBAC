import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AuditLog {
    private final List<AuditEntry> entries = new ArrayList<>();

    public record AuditEntry(
            String timestamp,
            String action,
            String performer,
            String target,
            String details
    ) {}

    public void log(String action, String performer, String target, String details) {
        entries.add(new AuditEntry(
                DateUtils.getCurrentDateTime(),
                ValidationUtils.normalizeString(action),
                ValidationUtils.normalizeString(performer),
                ValidationUtils.normalizeString(target),
                ValidationUtils.normalizeString(details)
        ));
    }

    public List<AuditEntry> getAll() {
        return List.copyOf(entries);
    }

    public List<AuditEntry> getByPerformer(String performer) {
        String p = ValidationUtils.normalizeString(performer);
        return entries.stream()
                .filter(e -> e.performer() != null && e.performer().equalsIgnoreCase(p))
                .collect(Collectors.toList());
    }

    public List<AuditEntry> getByAction(String action) {
        String a = ValidationUtils.normalizeString(action);
        return entries.stream()
                .filter(e -> e.action() != null && e.action().equalsIgnoreCase(a))
                .collect(Collectors.toList());
    }

    public void printLog() {
        if (entries.isEmpty()) {
            System.out.println("Audit log пуст.");
            return;
        }

        List<String[]> rows = new ArrayList<>();
        for (AuditEntry e : entries) {
            rows.add(new String[]{
                    FormatUtils.truncate(e.timestamp(), 19),
                    FormatUtils.truncate(e.action(), 16),
                    FormatUtils.truncate(e.performer(), 16),
                    FormatUtils.truncate(e.target(), 18),
                    FormatUtils.truncate(e.details(), 40)
            });
        }
        String table = FormatUtils.formatTable(
                new String[]{"Timestamp", "Action", "Performer", "Target", "Details"},
                rows
        );
        System.out.println(table);
    }

    public void saveToFile(String filename) {
        ValidationUtils.requireNonEmpty(filename, "filename");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write("timestamp\taction\tperformer\ttarget\tdetails\n");
            for (AuditEntry e : entries) {
                writer.write(safeTsv(e.timestamp())); writer.write("\t");
                writer.write(safeTsv(e.action())); writer.write("\t");
                writer.write(safeTsv(e.performer())); writer.write("\t");
                writer.write(safeTsv(e.target())); writer.write("\t");
                writer.write(safeTsv(e.details()));
                writer.write("\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить audit log: " + e.getMessage(), e);
        }
    }

    private static String safeTsv(String s) {
        if (s == null) return "";
        return s.replace("\t", " ").replace("\n", "\\n").replace("\r", "\\r");
    }
}

