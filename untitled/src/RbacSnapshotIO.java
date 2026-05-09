import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Текстовый снимок RBAC v1: экспорт/импорт (save/load).
 */
public final class RbacSnapshotIO {

    static final String HEADER = "RBAC-SNAPSHOT-v1";

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

    /** Загружает снимок: очищает систему и восстанавливает данные в порядке users → roles → assignments. */
    public static void importFromFile(RBACSystem system, String filename) throws IOException {
        ValidationUtils.requireNonEmpty(filename, "filename");
        Path path = Path.of(filename);
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Файл пуст");
        }
        if (!HEADER.equals(lines.get(0).trim())) {
            throw new IllegalArgumentException("Неизвестный формат снимка: ожидалась строка " + HEADER);
        }

        List<String[]> userRows = new ArrayList<>();
        List<String[]> roleRows = new ArrayList<>();
        List<String[]> assignRows = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] p = line.split("\t", -1);
            if (p.length < 1 || p[0].isBlank()) {
                continue;
            }
            switch (p[0]) {
                case "USER" -> {
                    if (p.length >= 4) {
                        userRows.add(p);
                    }
                }
                case "ROLE" -> {
                    if (p.length >= 5) {
                        roleRows.add(p);
                    }
                }
                case "ASSIGN" -> {
                    if (p.length >= 11) {
                        assignRows.add(p);
                    }
                }
                default -> {
                    // игнор неизвестных записей
                }
            }
        }

        system.clearAllData();

        for (String[] p : userRows) {
            User u = User.create(unesc(p[1]), unesc(p[2]), unesc(p[3]));
            system.getUserManager().add(u);
        }
        for (String[] p : roleRows) {
            Role r = Role.restoreFromSnapshot(unesc(p[1]), unesc(p[2]), unesc(p[3]));
            String permBlock = p[4];
            for (String chunk : permBlock.split("¤", -1)) {
                if (chunk.isEmpty()) {
                    continue;
                }
                String[] q = chunk.split("§", -1);
                if (q.length >= 3) {
                    r.addPermission(new Permission(unesc(q[0]), unesc(q[1]), unesc(q[2])));
                }
            }
            system.getRoleManager().add(r);
        }

        for (String[] p : assignRows) {
            String assignId = unesc(p[1]);
            String username = unesc(p[2]);
            String roleId = unesc(p[3]);
            String type = unesc(p[4]);
            AssignmentMetadata md = new AssignmentMetadata(unesc(p[5]), unesc(p[6]), unesc(p[7]));

            User user = system.getUserManager().findByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("USER не найден: " + username));
            Role role = system.getRoleManager().findById(roleId)
                    .orElseThrow(() -> new IllegalStateException("ROLE не найдена: " + roleId));

            RoleAssignment assignment;
            if ("PERMANENT".equalsIgnoreCase(type)) {
                boolean revoked = "1".equals(p[8]);
                assignment = PermanentAssignment.restoreFromSnapshot(assignId, user, role, md, revoked);
            } else if ("TEMPORARY".equalsIgnoreCase(type)) {
                String exp = unesc(p[9]);
                boolean ar = "1".equals(p[10]);
                assignment = TemporaryAssignment.restoreFromSnapshot(assignId, user, role, md, exp, ar);
            } else {
                throw new IllegalArgumentException("Неизвестный тип назначения: " + type);
            }
            system.getAssignmentManager().add(assignment);
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

    static String unesc(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 't' -> sb.append('\t');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case '\\' -> sb.append('\\');
                    default -> {
                        sb.append('\\');
                        sb.append(n);
                    }
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
