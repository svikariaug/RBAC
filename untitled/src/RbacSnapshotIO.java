import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Простейший текстовый снимок данных RBAC для фонового сохранения (формат v1, табуляция между полями).
 */
public final class RbacSnapshotIO {

    private static final String HEADER = "RBAC-SNAPSHOT-v1";

    private RbacSnapshotIO() {}

    public static void exportToFile(RBACSystem system, String filename) throws IOException {
        ValidationUtils.requireNonEmpty(filename, "filename");
        Path path = Path.of(filename);

        List<User> users = system.getUserManager().findAll().stream()
                .sorted(Comparator.comparing(User::username))
                .collect(Collectors.toList());
        List<Role> roles = system.getRoleManager().findAll().stream()
                .sorted(Comparator.comparing(Role::getName))
                .collect(Collectors.toList());
        List<RoleAssignment> assignments = system.getAssignmentManager().findAll();

        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write(HEADER);
            w.write('\n');

            for (User u : users) {
                w.write(row("USER", esc(u.username()), esc(u.fullName()), esc(u.email())));
                w.write('\n');
            }
            for (Role r : roles) {
                String perms = r.getPermissions().stream()
                        .sorted(Comparator.comparing(Permission::name).thenComparing(Permission::resource))
                        .map(p -> esc(p.name()) + "§" + esc(p.resource()) + "§" + esc(p.description()))
                        .collect(Collectors.joining("¤"));
                w.write(row("ROLE", esc(r.getId()), esc(r.getName()), esc(r.getDescription()), perms));
                w.write('\n');
            }
            for (RoleAssignment ra : assignments) {
                AssignmentMetadata md = ra.metadata();
                User u = ra.user();
                Role role = ra.role();

                String revoked = "";
                String expiresCol = "";
                String autorenewCol = "";
                if (ra instanceof PermanentAssignment pa) {
                    revoked = pa.isRevoked() ? "1" : "0";
                } else if (ra instanceof TemporaryAssignment ta) {
                    expiresCol = esc(ta.getExpiresAt());
                    autorenewCol = ta.isAutoRenew() ? "1" : "0";
                }

                w.write(row("ASSIGN",
                        esc(ra.assignmentId()),
                        esc(u.username()),
                        esc(role.getId()),
                        esc(ra.assignmentType()),
                        esc(md.assignedBy()),
                        esc(md.assignedAt()),
                        esc(md.reason() != null ? md.reason() : ""),
                        revoked,
                        expiresCol,
                        autorenewCol));
                w.write('\n');
            }
        }
    }

    private static String row(String kind, String... fields) {
        StringBuilder sb = new StringBuilder(kind);
        for (String f : fields) {
            sb.append('\t').append(f);
        }
        return sb.toString();
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
