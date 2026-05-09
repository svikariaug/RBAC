import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ReportGenerator {

    public String generateUserReport(UserManager userManager, AssignmentManager assignmentManager) {
        List<User> users = userManager.findAll();
        users.sort(Comparator.comparing(User::username));

        StringBuilder sb = new StringBuilder();
        sb.append(FormatUtils.formatHeader("Отчёт по пользователям"));
        if (users.isEmpty()) {
            sb.append("Нет пользователей.\n");
            return sb.toString();
        }

        List<String[]> rows = new ArrayList<>();
        for (User u : users) {
            List<String> roles = assignmentManager.findByUser(u).stream()
                    .filter(RoleAssignment::isActive)
                    .map(ra -> ra.role().getName())
                    .distinct()
                    .sorted()
                    .toList();
            rows.add(new String[]{
                    u.username(),
                    FormatUtils.truncate(u.fullName(), 30),
                    FormatUtils.truncate(u.email(), 30),
                    String.join(", ", roles)
            });
        }

        sb.append(FormatUtils.formatTable(
                new String[]{"Username", "Full Name", "Email", "Active roles"},
                rows
        ));
        return sb.toString();
    }

    public String generateRoleReport(RoleManager roleManager, AssignmentManager assignmentManager) {
        List<Role> roles = roleManager.findAll();
        roles.sort(Comparator.comparing(Role::getName));

        StringBuilder sb = new StringBuilder();
        sb.append(FormatUtils.formatHeader("Отчёт по ролям"));
        if (roles.isEmpty()) {
            sb.append("Нет ролей.\n");
            return sb.toString();
        }

        Map<String, Set<String>> roleToUsers = new HashMap<>();
        for (RoleAssignment ra : assignmentManager.findAll()) {
            if (!ra.isActive()) continue;
            roleToUsers.computeIfAbsent(ra.role().getName(), k -> new HashSet<>())
                    .add(ra.user().username());
        }

        List<String[]> rows = new ArrayList<>();
        for (Role r : roles) {
            int userCount = roleToUsers.getOrDefault(r.getName(), Set.of()).size();
            rows.add(new String[]{
                    r.getName(),
                    String.valueOf(r.getPermissions().size()),
                    String.valueOf(userCount),
                    FormatUtils.truncate(r.getId(), 18)
            });
        }

        sb.append(FormatUtils.formatTable(
                new String[]{"Role", "Perms", "Users", "ID"},
                rows
        ));
        return sb.toString();
    }

    public String generatePermissionMatrix(UserManager userManager, AssignmentManager assignmentManager) {
        List<User> users = userManager.findAll();
        users.sort(Comparator.comparing(User::username));

        Set<String> resources = assignmentManager.findAll().stream()
                .filter(RoleAssignment::isActive)
                .flatMap(ra -> ra.role().getPermissions().stream())
                .map(Permission::resource)
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(TreeSet::new));

        StringBuilder sb = new StringBuilder();
        sb.append(FormatUtils.formatHeader("Матрица прав (пользователи × ресурсы)"));

        if (users.isEmpty()) {
            sb.append("Нет пользователей.\n");
            return sb.toString();
        }
        if (resources.isEmpty()) {
            sb.append("Нет ресурсов/прав для построения матрицы.\n");
            return sb.toString();
        }

        List<String> resList = new ArrayList<>(resources);
        String[] headers = new String[1 + resList.size()];
        headers[0] = "Username";
        for (int i = 0; i < resList.size(); i++) headers[i + 1] = resList.get(i);

        List<String[]> rows = new ArrayList<>();
        for (User u : users) {
            Set<String> userResources = assignmentManager.getUserPermissions(u).stream()
                    .map(Permission::resource)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            String[] row = new String[headers.length];
            row[0] = u.username();
            for (int i = 0; i < resList.size(); i++) {
                row[i + 1] = userResources.contains(resList.get(i)) ? "✓" : "";
            }
            rows.add(row);
        }

        sb.append(FormatUtils.formatTable(headers, rows));
        return sb.toString();
    }

    public void exportToFile(String report, String filename) {
        ValidationUtils.requireNonEmpty(filename, "filename");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write(report == null ? "" : report);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить отчёт: " + e.getMessage(), e);
        }
    }
}

