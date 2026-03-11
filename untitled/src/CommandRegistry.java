import java.util.*;
import java.util.stream.Collectors;

public class CommandRegistry {

    public static void registerAllCommands(CommandParser parser) {
        registerUserCommands(parser);
        registerRoleCommands(parser);
        registerAssignmentCommands(parser);
        registerPermissionCommands(parser);
        registerUtilityCommands(parser);
    }

    private static void registerUserCommands(CommandParser parser) {
        parser.registerCommand("user-list", "вывести список всех пользователей", (scanner, system) -> {
            List<User> users = system.getUserManager().findAll();
            if (users.isEmpty()) {
                System.out.println("Пользователи не найдены.");
                return;
            }
            System.out.println("\n=== Список пользователей ===");
            System.out.printf("%-20s %-30s %-30s\n", "Username", "Full Name", "Email");
            System.out.println("-".repeat(80));
            for (User user : users) {
                System.out.printf("%-20s %-30s %-30s\n",
                        user.username(),
                        truncate(user.fullName(), 28),
                        truncate(user.email(), 28));
            }
        });

        parser.registerCommand("user-create", "создать нового пользователя", (scanner, system) -> {
            System.out.print("Введите username (латиница, цифры, _, 3-20 символов): ");
            String username = scanner.nextLine().trim();
            System.out.print("Введите полное имя: ");
            String fullName = scanner.nextLine().trim();
            System.out.print("Введите email: ");
            String email = scanner.nextLine().trim();

            try {
                User user = User.create(username, fullName, email);
                system.getUserManager().add(user);
                System.out.println("Пользователь создан: " + user.format());
            } catch (IllegalArgumentException e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        });

        parser.registerCommand("user-view", "просмотр информации о пользователе", (scanner, system) -> {
            System.out.print("Введите username: ");
            String username = scanner.nextLine().trim();

            Optional<User> userOpt = system.getUserManager().findByUsername(username);
            if (userOpt.isEmpty()) {
                System.out.println("Пользователь не найден.");
                return;
            }

            User user = userOpt.get();
            System.out.println("\n=== Информация о пользователе ===");
            System.out.println(user.format());

            List<RoleAssignment> assignments = system.getAssignmentManager().findByUser(user);
            System.out.println("\nНазначенные роли (" + assignments.size() + "):");
            for (RoleAssignment ra : assignments) {
                String status = ra.isActive() ? "АКТИВНА" : "НЕАКТИВНА";
                System.out.printf("  - %s [%s] (%s) - %s\n",
                        ra.role().getName(), ra.assignmentType(), status, ra.metadata().assignedAt());
            }

            Set<Permission> permissions = system.getAssignmentManager().getUserPermissions(user);
            System.out.println("\nВсе права (" + permissions.size() + "):");
            Map<String, List<Permission>> byResource = new HashMap<>();
            for (Permission p : permissions) {
                byResource.computeIfAbsent(p.resource(), k -> new ArrayList<>()).add(p);
            }
            for (Map.Entry<String, List<Permission>> entry : byResource.entrySet()) {
                System.out.println("  " + entry.getKey() + ":");
                for (Permission p : entry.getValue()) {
                    System.out.println("    - " + p.name() + ": " + p.description());
                }
            }
        });

        parser.registerCommand("user-update", "обновить данные пользователя", (scanner, system) -> {
            System.out.print("Введите username: ");
            String username = scanner.nextLine().trim();

            Optional<User> userOpt = system.getUserManager().findByUsername(username);
            if (userOpt.isEmpty()) {
                System.out.println("Пользователь не найден.");
                return;
            }

            System.out.print("Введите новое полное имя: ");
            String newFullName = scanner.nextLine().trim();
            System.out.print("Введите новый email: ");
            String newEmail = scanner.nextLine().trim();

            try {
                system.getUserManager().update(username, newFullName, newEmail);
                System.out.println("Данные обновлены.");
            } catch (IllegalArgumentException e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        });

        parser.registerCommand("user-delete", "удалить пользователя", (scanner, system) -> {
            System.out.print("Введите username: ");
            String username = scanner.nextLine().trim();

            Optional<User> userOpt = system.getUserManager().findByUsername(username);
            if (userOpt.isEmpty()) {
                System.out.println("Пользователь не найден.");
                return;
            }

            User user = userOpt.get();
            List<RoleAssignment> assignments = system.getAssignmentManager().findByUser(user);
            if (!assignments.isEmpty()) {
                System.out.println("У пользователя есть назначения:");
                for (RoleAssignment ra : assignments) {
                    System.out.println("  - " + ra.role().getName());
                }
                System.out.print("Удалить пользователя и все назначения? (да/нет): ");
                if (!scanner.nextLine().trim().equalsIgnoreCase("да")) {
                    System.out.println("Удаление отменено.");
                    return;
                }
                for (RoleAssignment ra : assignments) {
                    system.getAssignmentManager().remove(ra);
                }
            }

            system.getUserManager().remove(user);
            System.out.println("Пользователь удален.");
        });

        parser.registerCommand("user-search", "поиск пользователей по фильтрам", (scanner, system) -> {
            System.out.println("\nВыберите фильтр:");
            System.out.println("1. По username (содержит)");
            System.out.println("2. По email (содержит)");
            System.out.println("3. По домену email");
            System.out.println("4. По полному имени (содержит)");
            System.out.print("Ваш выбор: ");

            String choice = scanner.nextLine().trim();
            UserFilter filter = null;

            switch (choice) {
                case "1":
                    System.out.print("Введите часть username: ");
                    filter = UserFilters.byUsernameContains(scanner.nextLine().trim());
                    break;
                case "2":
                    System.out.print("Введите часть email: ");
                    filter = UserFilters.byEmailDomain("@" + scanner.nextLine().trim()); // временное решение
                    break;
                case "3":
                    System.out.print("Введите домен (например @company.com): ");
                    filter = UserFilters.byEmailDomain(scanner.nextLine().trim());
                    break;
                case "4":
                    System.out.print("Введите часть полного имени: ");
                    filter = UserFilters.byFullNameContains(scanner.nextLine().trim());
                    break;
                default:
                    System.out.println("Неверный выбор.");
                    return;
            }

            List<User> results = system.getUserManager().findByFilter(filter);
            if (results.isEmpty()) {
                System.out.println("Пользователи не найдены.");
            } else {
                System.out.println("\nНайдено пользователей: " + results.size());
                for (User user : results) {
                    System.out.println("  " + user.format());
                }
            }
        });
    }

    private static void registerRoleCommands(CommandParser parser) {
        parser.registerCommand("role-list", "вывести список всех ролей", (scanner, system) -> {
            List<Role> roles = system.getRoleManager().findAll();
            if (roles.isEmpty()) {
                System.out.println("Роли не найдены.");
                return;
            }
            System.out.println("\n=== Список ролей ===");
            System.out.printf("%-20s %-10s %s\n", "Название", "Прав", "ID");
            System.out.println("-".repeat(50));
            for (Role role : roles) {
                System.out.printf("%-20s %-10d %s\n",
                        role.getName(), role.getPermissions().size(), role.getId());
            }
        });

        parser.registerCommand("role-create", "создать новую роль", (scanner, system) -> {
            System.out.print("Введите название роли: ");
            String name = scanner.nextLine().trim();
            System.out.print("Введите описание роли: ");
            String description = scanner.nextLine().trim();

            try {
                Role role = new Role(name, description);
                system.getRoleManager().add(role);
                System.out.println("Роль создана. ID: " + role.getId());

                System.out.print("Добавить права сейчас? (да/нет): ");
                if (scanner.nextLine().trim().equalsIgnoreCase("да")) {
                    addPermissionsToRole(scanner, system, role);
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        });

        parser.registerCommand("role-view", "просмотр информации о роли", (scanner, system) -> {
            System.out.print("Введите имя роли: ");
            String name = scanner.nextLine().trim();

            Optional<Role> roleOpt = system.getRoleManager().findByName(name);
            if (roleOpt.isEmpty()) {
                System.out.println("Роль не найдена.");
                return;
            }

            System.out.println("\n" + roleOpt.get().format());
        });

        parser.registerCommand("role-update", "обновить роль", (scanner, system) -> {
            System.out.println("Функция обновления роли временно недоступна.");
        });

        parser.registerCommand("role-delete", "удалить роль", (scanner, system) -> {
            System.out.print("Введите имя роли: ");
            String name = scanner.nextLine().trim();

            Optional<Role> roleOpt = system.getRoleManager().findByName(name);
            if (roleOpt.isEmpty()) {
                System.out.println("Роль не найдена.");
                return;
            }

            Role role = roleOpt.get();
            List<RoleAssignment> assignments = system.getAssignmentManager().findByRole(role);
            if (!assignments.isEmpty()) {
                System.out.println("Роль назначена пользователям:");
                for (RoleAssignment ra : assignments) {
                    System.out.println("  - " + ra.user().username());
                }
                System.out.print("Все назначения будут удалены. Продолжить? (да/нет): ");
                if (!scanner.nextLine().trim().equalsIgnoreCase("да")) {
                    System.out.println("Удаление отменено.");
                    return;
                }
                for (RoleAssignment ra : assignments) {
                    system.getAssignmentManager().remove(ra);
                }
            }

            system.getRoleManager().remove(role);
            System.out.println("Роль удалена.");
        });

        parser.registerCommand("role-add-permission", "добавить право к роли", (scanner, system) -> {
            System.out.print("Введите имя роли: ");
            String name = scanner.nextLine().trim();

            Optional<Role> roleOpt = system.getRoleManager().findByName(name);
            if (roleOpt.isEmpty()) {
                System.out.println("Роль не найдена.");
                return;
            }

            addPermissionsToRole(scanner, system, roleOpt.get());
        });

        parser.registerCommand("role-remove-permission", "удалить право из роли", (scanner, system) -> {
            System.out.print("Введите имя роли: ");
            String name = scanner.nextLine().trim();

            Optional<Role> roleOpt = system.getRoleManager().findByName(name);
            if (roleOpt.isEmpty()) {
                System.out.println("Роль не найдена.");
                return;
            }

            Role role = roleOpt.get();
            Set<Permission> permissions = role.getPermissions();
            if (permissions.isEmpty()) {
                System.out.println("У роли нет прав.");
                return;
            }

            System.out.println("\nПрава роли:");
            List<Permission> permList = new ArrayList<>(permissions);
            for (int i = 0; i < permList.size(); i++) {
                Permission p = permList.get(i);
                System.out.printf("%d. %s on %s: %s\n", i + 1, p.name(), p.resource(), p.description());
            }

            System.out.print("Введите номер права для удаления (0 - отмена): ");
            try {
                int choice = Integer.parseInt(scanner.nextLine().trim());
                if (choice == 0) return;
                if (choice < 1 || choice > permList.size()) {
                    System.out.println("Неверный номер.");
                    return;
                }
                system.getRoleManager().removePermissionFromRole(name, permList.get(choice - 1));
                System.out.println("Право удалено.");
            } catch (NumberFormatException e) {
                System.out.println("Неверный формат.");
            }
        });

        parser.registerCommand("role-search", "поиск ролей", (scanner, system) -> {
            System.out.println("\nВыберите фильтр:");
            System.out.println("1. По имени (содержит)");
            System.out.println("2. По наличию права");
            System.out.println("3. По минимальному количеству прав");
            System.out.print("Ваш выбор: ");

            String choice = scanner.nextLine().trim();
            RoleFilter filter = null;

            switch (choice) {
                case "1":
                    System.out.print("Введите часть имени: ");
                    filter = RoleFilters.byNameContains(scanner.nextLine().trim());
                    break;
                case "2":
                    System.out.print("Введите название права: ");
                    String permName = scanner.nextLine().trim().toUpperCase();
                    System.out.print("Введите ресурс: ");
                    String resource = scanner.nextLine().trim().toLowerCase();
                    filter = RoleFilters.hasPermission(permName, resource);
                    break;
                case "3":
                    System.out.print("Введите минимальное количество прав: ");
                    try {
                        int min = Integer.parseInt(scanner.nextLine().trim());
                        filter = RoleFilters.hasAtLeastNPermissions(min);
                    } catch (NumberFormatException e) {
                        System.out.println("Неверный формат.");
                        return;
                    }
                    break;
                default:
                    System.out.println("Неверный выбор.");
                    return;
            }

            List<Role> results = system.getRoleManager().findByFilter(filter);
            if (results.isEmpty()) {
                System.out.println("Роли не найдены.");
            } else {
                System.out.println("\nНайдено ролей: " + results.size());
                for (Role role : results) {
                    System.out.printf("  - %s (%d прав)\n", role.getName(), role.getPermissions().size());
                }
            }
        });
    }

    private static void registerAssignmentCommands(CommandParser parser) {
        parser.registerCommand("assign-role", "назначить роль пользователю", (scanner, system) -> {
            System.out.print("Введите username: ");
            String username = scanner.nextLine().trim();

            Optional<User> userOpt = system.getUserManager().findByUsername(username);
            if (userOpt.isEmpty()) {
                System.out.println("Пользователь не найден.");
                return;
            }

            User user = userOpt.get();
            List<Role> availableRoles = system.getRoleManager().findAll();
            if (availableRoles.isEmpty()) {
                System.out.println("Нет доступных ролей.");
                return;
            }

            System.out.println("\nДоступные роли:");
            for (int i = 0; i < availableRoles.size(); i++) {
                Role role = availableRoles.get(i);
                System.out.printf("%d. %s - %s\n", i + 1, role.getName(), role.getDescription());
            }

            System.out.print("Выберите номер роли: ");
            try {
                int choice = Integer.parseInt(scanner.nextLine().trim());
                if (choice < 1 || choice > availableRoles.size()) {
                    System.out.println("Неверный номер.");
                    return;
                }

                Role role = availableRoles.get(choice - 1);
                if (system.getAssignmentManager().userHasRole(user, role)) {
                    System.out.println("Роль уже назначена.");
                    return;
                }

                System.out.print("Тип (1 - постоянное, 2 - временное): ");
                String typeChoice = scanner.nextLine().trim();
                System.out.print("Причина: ");
                String reason = scanner.nextLine().trim();

                AssignmentMetadata metadata = AssignmentMetadata.now(system.getCurrentUser(), reason);
                RoleAssignment assignment;

                if (typeChoice.equals("2")) {
                    System.out.print("Дата истечения (ГГГГ-ММ-ДД ЧЧ:ММ): ");
                    String expiresAt = scanner.nextLine().trim();
                    assignment = new TemporaryAssignment(user, role, metadata, expiresAt, false);
                } else {
                    assignment = new PermanentAssignment(user, role, metadata);
                }

                system.getAssignmentManager().add(assignment);
                System.out.println("Роль назначена. ID: " + assignment.assignmentId());
            } catch (Exception e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        });

        parser.registerCommand("revoke-role", "отозвать роль у пользователя", (scanner, system) -> {
            System.out.print("Введите username: ");
            String username = scanner.nextLine().trim();

            Optional<User> userOpt = system.getUserManager().findByUsername(username);
            if (userOpt.isEmpty()) {
                System.out.println("Пользователь не найден.");
                return;
            }

            User user = userOpt.get();
            List<RoleAssignment> active = system.getAssignmentManager().findByUser(user).stream()
                    .filter(RoleAssignment::isActive).collect(Collectors.toList());

            if (active.isEmpty()) {
                System.out.println("Нет активных назначений.");
                return;
            }

            System.out.println("\nАктивные назначения:");
            for (int i = 0; i < active.size(); i++) {
                RoleAssignment ra = active.get(i);
                System.out.printf("%d. %s [%s]\n", i + 1, ra.role().getName(), ra.assignmentType());
            }

            System.out.print("Выберите номер (0 - отмена): ");
            try {
                int choice = Integer.parseInt(scanner.nextLine().trim());
                if (choice == 0) return;
                if (choice < 1 || choice > active.size()) {
                    System.out.println("Неверный номер.");
                    return;
                }

                RoleAssignment toRevoke = active.get(choice - 1);
                if (toRevoke instanceof PermanentAssignment) {
                    ((PermanentAssignment) toRevoke).revoke();
                } else {
                    system.getAssignmentManager().revokeAssignment(toRevoke.assignmentId());
                }
                System.out.println("Роль отозвана.");
            } catch (NumberFormatException e) {
                System.out.println("Неверный формат.");
            }
        });

        parser.registerCommand("assignment-list", "список всех назначений", (scanner, system) -> {
            List<RoleAssignment> assignments = system.getAssignmentManager().findAll();
            if (assignments.isEmpty()) {
                System.out.println("Назначения не найдены.");
                return;
            }
            System.out.println("\n=== Все назначения ===");
            System.out.printf("%-20s %-20s %-12s %-10s %s\n", "Username", "Role", "Type", "Status", "Assigned At");
            System.out.println("-".repeat(80));
            for (RoleAssignment ra : assignments) {
                String status = ra.isActive() ? "АКТИВНА" : "НЕАКТИВНА";
                System.out.printf("%-20s %-20s %-12s %-10s %s\n",
                        ra.user().username(), ra.role().getName(),
                        ra.assignmentType(), status, ra.metadata().assignedAt());
            }
        });

        parser.registerCommand("assignment-list-user", "назначения пользователя", (scanner, system) -> {
            System.out.print("Введите username: ");
            String username = scanner.nextLine().trim();

            Optional<User> userOpt = system.getUserManager().findByUsername(username);
            if (userOpt.isEmpty()) {
                System.out.println("Пользователь не найден.");
                return;
            }

            List<RoleAssignment> assignments = system.getAssignmentManager().findByUser(userOpt.get());
            if (assignments.isEmpty()) {
                System.out.println("Нет назначений.");
                return;
            }
            System.out.println("\nНазначения пользователя " + username + ":");
            for (RoleAssignment ra : assignments) {
                System.out.println("  " + formatAssignmentSummary(ra));
            }
        });

        parser.registerCommand("assignment-list-role", "пользователи с ролью", (scanner, system) -> {
            System.out.print("Введите имя роли: ");
            String roleName = scanner.nextLine().trim();

            Optional<Role> roleOpt = system.getRoleManager().findByName(roleName);
            if (roleOpt.isEmpty()) {
                System.out.println("Роль не найдена.");
                return;
            }

            List<RoleAssignment> assignments = system.getAssignmentManager().findByRole(roleOpt.get());
            if (assignments.isEmpty()) {
                System.out.println("Нет пользователей с этой ролью.");
                return;
            }
            System.out.println("\nПользователи с ролью " + roleName + ":");
            for (RoleAssignment ra : assignments) {
                System.out.println("  - " + ra.user().username() + " (" + ra.user().fullName() + ")");
            }
        });

        parser.registerCommand("assignment-active", "только активные назначения", (scanner, system) -> {
            List<RoleAssignment> active = system.getAssignmentManager().getActiveAssignments();
            if (active.isEmpty()) {
                System.out.println("Нет активных назначений.");
                return;
            }
            System.out.println("\nАктивные назначения (" + active.size() + "):");
            for (RoleAssignment ra : active) {
                System.out.println("  " + formatAssignmentSummary(ra));
            }
        });

        parser.registerCommand("assignment-expired", "истекшие назначения", (scanner, system) -> {
            List<RoleAssignment> expired = system.getAssignmentManager().getExpiredAssignments();
            if (expired.isEmpty()) {
                System.out.println("Нет истекших назначений.");
                return;
            }
            System.out.println("\nИстекшие назначения (" + expired.size() + "):");
            for (RoleAssignment ra : expired) {
                System.out.println("  " + formatAssignmentSummary(ra));
            }
        });

        parser.registerCommand("assignment-extend", "продлить временное назначение", (scanner, system) -> {
            System.out.print("Введите ID назначения: ");
            String id = scanner.nextLine().trim();

            Optional<RoleAssignment> opt = system.getAssignmentManager().findById(id);
            if (opt.isEmpty()) {
                System.out.println("Назначение не найдено.");
                return;
            }

            if (!(opt.get() instanceof TemporaryAssignment)) {
                System.out.println("Это не временное назначение.");
                return;
            }

            System.out.print("Новая дата истечения (ГГГГ-ММ-ДД ЧЧ:ММ): ");
            String newDate = scanner.nextLine().trim();

            try {
                system.getAssignmentManager().extendTemporaryAssignment(id, newDate);
                System.out.println("Назначение продлено.");
            } catch (Exception e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        });

        parser.registerCommand("assignment-search", "поиск назначений", (scanner, system) -> {
            System.out.println("\nВыберите фильтр:");
            System.out.println("1. По пользователю");
            System.out.println("2. По роли");
            System.out.println("3. По типу");
            System.out.println("4. По статусу");
            System.out.println("5. Назначенные после даты");
            System.out.println("6. Истекающие до даты");
            System.out.print("Ваш выбор: ");

            String choice = scanner.nextLine().trim();
            AssignmentFilter filter = null;

            switch (choice) {
                case "1":
                    System.out.print("Введите username: ");
                    String username = scanner.nextLine().trim();
                    filter = AssignmentFilters.byUsername(username);
                    break;
                case "2":
                    System.out.print("Введите имя роли: ");
                    String roleName = scanner.nextLine().trim();
                    filter = AssignmentFilters.byRoleName(roleName);
                    break;
                case "3":
                    System.out.print("Тип (PERMANENT/TEMPORARY): ");
                    String type = scanner.nextLine().trim().toUpperCase();
                    filter = AssignmentFilters.byType(type);
                    break;
                case "4":
                    System.out.print("Статус (1 - активные, 2 - неактивные): ");
                    String status = scanner.nextLine().trim();
                    filter = status.equals("1") ? AssignmentFilters.activeOnly() : AssignmentFilters.inactiveOnly();
                    break;
                case "5":
                    System.out.print("Дата (ГГГГ-ММ-ДД): ");
                    String afterDate = scanner.nextLine().trim();
                    filter = AssignmentFilters.assignedAfter(afterDate);
                    break;
                case "6":
                    System.out.print("Дата (ГГГГ-ММ-ДД): ");
                    String beforeDate = scanner.nextLine().trim();
                    filter = AssignmentFilters.expiringBefore(beforeDate);
                    break;
                default:
                    System.out.println("Неверный выбор.");
                    return;
            }

            List<RoleAssignment> results = system.getAssignmentManager().findByFilter(filter);
            if (results.isEmpty()) {
                System.out.println("Назначения не найдены.");
            } else {
                System.out.println("\nНайдено назначений: " + results.size());
                for (RoleAssignment ra : results) {
                    System.out.println("  " + formatAssignmentSummary(ra));
                }
            }
        });
    }

    private static void registerPermissionCommands(CommandParser parser) {
        parser.registerCommand("permissions-user", "права пользователя", (scanner, system) -> {
            System.out.print("Введите username: ");
            String username = scanner.nextLine().trim();

            Optional<User> userOpt = system.getUserManager().findByUsername(username);
            if (userOpt.isEmpty()) {
                System.out.println("Пользователь не найден.");
                return;
            }

            User user = userOpt.get();
            Set<Permission> permissions = system.getAssignmentManager().getUserPermissions(user);

            System.out.println("\nПрава пользователя " + username + ":");
            if (permissions.isEmpty()) {
                System.out.println("  Нет прав");
                return;
            }

            Map<String, List<Permission>> byResource = new HashMap<>();
            for (Permission p : permissions) {
                byResource.computeIfAbsent(p.resource(), k -> new ArrayList<>()).add(p);
            }

            for (Map.Entry<String, List<Permission>> entry : byResource.entrySet()) {
                System.out.println("  " + entry.getKey() + ":");
                for (Permission p : entry.getValue()) {
                    System.out.println("    - " + p.name());
                }
            }
        });

        parser.registerCommand("permissions-check", "проверить право у пользователя", (scanner, system) -> {
            System.out.print("Введите username: ");
            String username = scanner.nextLine().trim();

            Optional<User> userOpt = system.getUserManager().findByUsername(username);
            if (userOpt.isEmpty()) {
                System.out.println("Пользователь не найден.");
                return;
            }

            System.out.print("Введите название права: ");
            String permName = scanner.nextLine().trim().toUpperCase();
            System.out.print("Введите ресурс: ");
            String resource = scanner.nextLine().trim().toLowerCase();

            User user = userOpt.get();
            boolean hasPermission = system.getAssignmentManager().userHasPermission(user, permName, resource);

            if (hasPermission) {
                System.out.println("✓ Пользователь имеет право " + permName + " на " + resource);
                for (RoleAssignment ra : system.getAssignmentManager().findByUser(user)) {
                    if (ra.isActive() && ra.role().hasPermission(permName, resource)) {
                        System.out.println("  (из роли: " + ra.role().getName() + ")");
                    }
                }
            } else {
                System.out.println("✗ Пользователь НЕ имеет право " + permName + " на " + resource);
            }
        });
    }

    private static void registerUtilityCommands(CommandParser parser) {
        parser.registerCommand("help", "справка по командам", (scanner, system) -> {
            parser.printHelp();
        });

        parser.registerCommand("stats", "статистика системы", (scanner, system) -> {
            System.out.print(system.generateStatistics());

            Map<String, Integer> roleCount = new HashMap<>();
            for (RoleAssignment ra : system.getAssignmentManager().findAll()) {
                String roleName = ra.role().getName();
                roleCount.put(roleName, roleCount.getOrDefault(roleName, 0) + 1);
            }

            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(roleCount.entrySet());
            sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

            System.out.println("\nТоп-3 самых популярных ролей:");
            if (sorted.isEmpty()) {
                System.out.println("  Нет данных");
            } else {
                for (int i = 0; i < Math.min(3, sorted.size()); i++) {
                    Map.Entry<String, Integer> entry = sorted.get(i);
                    System.out.printf("  %d. %s: %d назначений\n", i + 1, entry.getKey(), entry.getValue());
                }
            }
        });

        parser.registerCommand("clear", "очистить экран", (scanner, system) -> {
            System.out.print("\033[H\033[2J");
            System.out.flush();
        });

        parser.registerCommand("exit", "выход из программы", (scanner, system) -> {
            System.out.print("Выйти? (да/нет): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("да")) {
                System.out.println("До свидания!");
                System.exit(0);
            }
        });

        parser.registerCommand("save", "сохранить данные в файл", (scanner, system) -> {
            System.out.println("Сохранение данных... (функция в разработке)");
        });

        parser.registerCommand("load", "загрузить данные из файла", (scanner, system) -> {
            System.out.println("Загрузка данных... (функция в разработке)");
        });
    }

    private static void addPermissionsToRole(Scanner scanner, RBACSystem system, Role role) {
        while (true) {
            System.out.print("Название права (READ/WRITE/DELETE): ");
            String name = scanner.nextLine().trim().toUpperCase();
            System.out.print("Ресурс (users/roles/reports): ");
            String resource = scanner.nextLine().trim().toLowerCase();
            System.out.print("Описание: ");
            String description = scanner.nextLine().trim();

            try {
                Permission permission = new Permission(name, resource, description);
                system.getRoleManager().addPermissionToRole(role.getName(), permission);
                System.out.println("Право добавлено.");
            } catch (Exception e) {
                System.out.println("Ошибка: " + e.getMessage());
            }

            System.out.print("Добавить еще? (да/нет): ");
            if (!scanner.nextLine().trim().equalsIgnoreCase("да")) {
                break;
            }
        }
    }

    private static String formatAssignmentSummary(RoleAssignment ra) {
        String status = ra.isActive() ? "АКТИВНА" : "НЕАКТИВНА";
        return "[" + ra.assignmentType() + "] " + ra.role().getName() +
                " назначена " + ra.user().username() +
                " пользователем " + ra.metadata().assignedBy() +
                " в " + ra.metadata().assignedAt() +
                " Причина: " + ra.metadata().reason() +
                " Статус: " + status;
    }

    private static String truncate(String str, int length) {
        if (str == null || str.length() <= length) return str;
        return str.substring(0, length - 3) + "...";
    }
}