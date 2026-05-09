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
            List<String[]> rows = new ArrayList<>();
            users.sort(Comparator.comparing(User::username));
            for (User user : users) {
                rows.add(new String[]{
                        user.username(),
                        FormatUtils.truncate(user.fullName(), 28),
                        FormatUtils.truncate(user.email(), 28)
                });
            }
            System.out.print(FormatUtils.formatHeader("Список пользователей"));
            System.out.println(FormatUtils.formatTable(new String[]{"Username", "Full Name", "Email"}, rows));
        });

        parser.registerCommand("user-create", "создать нового пользователя", (scanner, system) -> {
            String username = ConsoleUtils.promptString(scanner,
                    "Введите username (латиница, цифры, _, 3-20 символов): ", true);
            String fullName = ConsoleUtils.promptString(scanner, "Введите полное имя: ", true);
            String email = ConsoleUtils.promptString(scanner, "Введите email: ", true);

            try {
                User user = User.create(username, fullName, email);
                system.getUserManager().add(user);
                System.out.println("Пользователь создан: " + user.format());
                system.getAuditLog().log("USER_CREATE", system.getCurrentUser(), user.username(), user.format());
            } catch (IllegalArgumentException e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        });

        parser.registerCommand("user-view", "просмотр информации о пользователе", (scanner, system) -> {
            String username = ConsoleUtils.promptString(scanner, "Введите username: ", true);

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
            String username = ConsoleUtils.promptString(scanner, "Введите username: ", true);

            Optional<User> userOpt = system.getUserManager().findByUsername(username);
            if (userOpt.isEmpty()) {
                System.out.println("Пользователь не найден.");
                return;
            }

            String newFullName = ConsoleUtils.promptString(scanner, "Введите новое полное имя: ", true);
            String newEmail = ConsoleUtils.promptString(scanner, "Введите новый email: ", true);

            try {
                system.getUserManager().update(username, newFullName, newEmail);
                System.out.println("Данные обновлены.");
                system.getAuditLog().log("USER_UPDATE", system.getCurrentUser(), username,
                        "Updated profile fields");
            } catch (IllegalArgumentException e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        });

        parser.registerCommand("user-delete", "удалить пользователя", (scanner, system) -> {
            String username = ConsoleUtils.promptString(scanner, "Введите username: ", true);

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
                if (!ConsoleUtils.promptYesNo(scanner, "Удалить пользователя и все назначения? (да/нет): ")) {
                    System.out.println("Удаление отменено.");
                    return;
                }
                for (RoleAssignment ra : assignments) {
                    system.getAssignmentManager().remove(ra);
                }
            }

            system.getUserManager().remove(user);
            System.out.println("Пользователь удален.");
            system.getAuditLog().log("USER_DELETE", system.getCurrentUser(), username, user.format());
        });

        parser.registerCommand("user-search", "поиск пользователей по фильтрам", (scanner, system) -> {
            System.out.println("\nВыберите фильтр:");
            System.out.println("1. По username (содержит)");
            System.out.println("2. По email (содержит)");
            System.out.println("3. По домену email");
            System.out.println("4. По полному имени (содержит)");
            int choice = ConsoleUtils.promptInt(scanner, "Ваш выбор: ", 1, 4);
            UserFilter filter = null;

            switch (choice) {
                case 1:
                    filter = UserFilters.byUsernameContains(
                            ConsoleUtils.promptString(scanner, "Введите часть username: ", true));
                    break;
                case 2:
                    filter = UserFilters.byEmailContains(
                            ConsoleUtils.promptString(scanner, "Введите часть email: ", true));
                    break;
                case 3:
                    filter = UserFilters.byEmailDomain(
                            ConsoleUtils.promptString(scanner, "Введите домен (например @company.com): ", true));
                    break;
                case 4:
                    filter = UserFilters.byFullNameContains(
                            ConsoleUtils.promptString(scanner, "Введите часть полного имени: ", true));
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
            roles.sort(Comparator.comparing(Role::getName));
            List<String[]> rows = new ArrayList<>();
            for (Role role : roles) {
                rows.add(new String[]{
                        role.getName(),
                        String.valueOf(role.getPermissions().size()),
                        FormatUtils.truncate(role.getId(), 18)
                });
            }
            System.out.print(FormatUtils.formatHeader("Список ролей"));
            System.out.println(FormatUtils.formatTable(new String[]{"Role", "Perms", "ID"}, rows));
        });

        parser.registerCommand("role-create", "создать новую роль", (scanner, system) -> {
            String name = ConsoleUtils.promptString(scanner, "Введите название роли: ", true);
            String description = ConsoleUtils.promptString(scanner, "Введите описание роли: ", true);

            try {
                Role role = new Role(name, description);
                system.getRoleManager().add(role);
                System.out.println("Роль создана. ID: " + role.getId());
                system.getAuditLog().log("ROLE_CREATE", system.getCurrentUser(), role.getName(), role.getDescription());

                if (ConsoleUtils.promptYesNo(scanner, "Добавить права сейчас? (да/нет): ")) {
                    addPermissionsToRole(scanner, system, role);
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        });

        parser.registerCommand("role-view", "просмотр информации о роли", (scanner, system) -> {
            String name = ConsoleUtils.promptString(scanner, "Введите имя роли: ", true);

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
            String name = ConsoleUtils.promptString(scanner, "Введите имя роли: ", true);

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
                if (!ConsoleUtils.promptYesNo(scanner, "Все назначения будут удалены. Продолжить? (да/нет): ")) {
                    System.out.println("Удаление отменено.");
                    return;
                }
                for (RoleAssignment ra : assignments) {
                    system.getAssignmentManager().remove(ra);
                }
            }

            system.getRoleManager().remove(role);
            System.out.println("Роль удалена.");
            system.getAuditLog().log("ROLE_DELETE", system.getCurrentUser(), role.getName(), role.getId());
        });

        parser.registerCommand("role-add-permission", "добавить право к роли", (scanner, system) -> {
            String name = ConsoleUtils.promptString(scanner, "Введите имя роли: ", true);

            Optional<Role> roleOpt = system.getRoleManager().findByName(name);
            if (roleOpt.isEmpty()) {
                System.out.println("Роль не найдена.");
                return;
            }

            addPermissionsToRole(scanner, system, roleOpt.get());
        });

        parser.registerCommand("role-remove-permission", "удалить право из роли", (scanner, system) -> {
            String name = ConsoleUtils.promptString(scanner, "Введите имя роли: ", true);

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

            int choice = ConsoleUtils.promptInt(scanner, "Введите номер права для удаления (0 - отмена): ", 0, permList.size());
            if (choice == 0) return;
            system.getRoleManager().removePermissionFromRole(name, permList.get(choice - 1));
            System.out.println("Право удалено.");
        });

        parser.registerCommand("role-search", "поиск ролей", (scanner, system) -> {
            System.out.println("\nВыберите фильтр:");
            System.out.println("1. По имени (содержит)");
            System.out.println("2. По наличию права");
            System.out.println("3. По минимальному количеству прав");
            int choice = ConsoleUtils.promptInt(scanner, "Ваш выбор: ", 1, 3);
            RoleFilter filter = null;

            switch (choice) {
                case 1:
                    filter = RoleFilters.byNameContains(ConsoleUtils.promptString(scanner, "Введите часть имени: ", true));
                    break;
                case 2:
                    String permName = ConsoleUtils.promptString(scanner, "Введите название права: ", true).toUpperCase();
                    String resource = ConsoleUtils.promptString(scanner, "Введите ресурс: ", true).toLowerCase();
                    filter = RoleFilters.hasPermission(permName, resource);
                    break;
                case 3:
                    int min = ConsoleUtils.promptInt(scanner, "Введите минимальное количество прав: ", 0, 10_000);
                    filter = RoleFilters.hasAtLeastNPermissions(min);
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
            String username = ConsoleUtils.promptString(scanner, "Введите username: ", true);

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

            try {
                availableRoles.sort(Comparator.comparing(Role::getName));
                Role role = ConsoleUtils.promptChoice(scanner, "\nДоступные роли:", availableRoles);
                if (system.getAssignmentManager().userHasRole(user, role)) {
                    System.out.println("Роль уже назначена.");
                    return;
                }

                int typeChoice = ConsoleUtils.promptInt(scanner, "Тип (1 - постоянное, 2 - временное): ", 1, 2);
                String reason = ConsoleUtils.promptString(scanner, "Причина: ", true);

                AssignmentMetadata metadata = AssignmentMetadata.now(system.getCurrentUser(), reason);
                RoleAssignment assignment;

                if (typeChoice == 2) {
                    String expiresAt;
                    while (true) {
                        expiresAt = ConsoleUtils.promptString(scanner, "Дата истечения (ГГГГ-ММ-ДД ЧЧ:ММ): ", true);
                        if (expiresAt.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$")) break;
                        System.out.println("Неверный формат. Пример: 2026-05-09 23:59");
                    }
                    assignment = new TemporaryAssignment(user, role, metadata, expiresAt, false);
                } else {
                    assignment = new PermanentAssignment(user, role, metadata);
                }

                system.getAssignmentManager().add(assignment);
                System.out.println("Роль назначена. ID: " + assignment.assignmentId());
                system.getAuditLog().log("ASSIGN_ROLE", system.getCurrentUser(),
                        user.username(), role.getName() + " (" + assignment.assignmentType() + ")");
            } catch (Exception e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        });

        parser.registerCommand("revoke-role", "отозвать роль у пользователя", (scanner, system) -> {
            String username = ConsoleUtils.promptString(scanner, "Введите username: ", true);

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

            try {
                int choice = ConsoleUtils.promptInt(scanner, "Выберите номер (0 - отмена): ", 0, active.size());
                if (choice == 0) return;
                RoleAssignment toRevoke = active.get(choice - 1);
                if (toRevoke instanceof PermanentAssignment) {
                    ((PermanentAssignment) toRevoke).revoke();
                } else {
                    system.getAssignmentManager().revokeAssignment(toRevoke.assignmentId());
                }
                System.out.println("Роль отозвана.");
                system.getAuditLog().log("REVOKE_ROLE", system.getCurrentUser(),
                        username, toRevoke.role().getName() + " (" + toRevoke.assignmentType() + ")");
            } catch (Exception e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        });

        parser.registerCommand("assignment-list", "список всех назначений", (scanner, system) -> {
            List<RoleAssignment> assignments = system.getAssignmentManager().findAll();
            if (assignments.isEmpty()) {
                System.out.println("Назначения не найдены.");
                return;
            }
            List<String[]> rows = new ArrayList<>();
            assignments.sort(Comparator.comparing(a -> a.user().username()));
            for (RoleAssignment ra : assignments) {
                rows.add(new String[]{
                        ra.user().username(),
                        ra.role().getName(),
                        ra.assignmentType(),
                        ra.isActive() ? "ACTIVE" : "INACTIVE",
                        FormatUtils.truncate(ra.metadata().assignedAt(), 19)
                });
            }
            System.out.print(FormatUtils.formatHeader("Все назначения"));
            System.out.println(FormatUtils.formatTable(
                    new String[]{"Username", "Role", "Type", "Status", "Assigned At"},
                    rows
            ));
        });

        parser.registerCommand("assignment-list-user", "назначения пользователя", (scanner, system) -> {
            String username = ConsoleUtils.promptString(scanner, "Введите username: ", true);

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
            String roleName = ConsoleUtils.promptString(scanner, "Введите имя роли: ", true);

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
            String id = ConsoleUtils.promptString(scanner, "Введите ID назначения: ", true);

            Optional<RoleAssignment> opt = system.getAssignmentManager().findById(id);
            if (opt.isEmpty()) {
                System.out.println("Назначение не найдено.");
                return;
            }

            if (!(opt.get() instanceof TemporaryAssignment)) {
                System.out.println("Это не временное назначение.");
                return;
            }

            String newDate;
            while (true) {
                newDate = ConsoleUtils.promptString(scanner, "Новая дата истечения (ГГГГ-ММ-ДД ЧЧ:ММ): ", true);
                if (newDate.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$")) break;
                System.out.println("Неверный формат. Пример: 2026-05-09 23:59");
            }

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
            int choice = ConsoleUtils.promptInt(scanner, "Ваш выбор: ", 1, 6);
            AssignmentFilter filter = null;

            switch (choice) {
                case 1:
                    filter = AssignmentFilters.byUsername(
                            ConsoleUtils.promptString(scanner, "Введите username: ", true));
                    break;
                case 2:
                    filter = AssignmentFilters.byRoleName(
                            ConsoleUtils.promptString(scanner, "Введите имя роли: ", true));
                    break;
                case 3:
                    filter = AssignmentFilters.byType(
                            ConsoleUtils.promptString(scanner, "Тип (PERMANENT/TEMPORARY): ", true).toUpperCase());
                    break;
                case 4:
                    int status = ConsoleUtils.promptInt(scanner, "Статус (1 - активные, 2 - неактивные): ", 1, 2);
                    filter = status == 1 ? AssignmentFilters.activeOnly() : AssignmentFilters.inactiveOnly();
                    break;
                case 5: {
                    String afterDate;
                    while (true) {
                        afterDate = ConsoleUtils.promptString(scanner, "Дата (ГГГГ-ММ-ДД): ", true);
                        if (afterDate.matches("^\\d{4}-\\d{2}-\\d{2}$")) break;
                        System.out.println("Неверный формат. Пример: 2026-05-09");
                    }
                    filter = AssignmentFilters.assignedAfter(afterDate);
                    break;
                }
                case 6: {
                    String beforeDate;
                    while (true) {
                        beforeDate = ConsoleUtils.promptString(scanner, "Дата (ГГГГ-ММ-ДД): ", true);
                        if (beforeDate.matches("^\\d{4}-\\d{2}-\\d{2}$")) break;
                        System.out.println("Неверный формат. Пример: 2026-05-09");
                    }
                    filter = AssignmentFilters.expiringBefore(beforeDate);
                    break;
                }
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
            String username = ConsoleUtils.promptString(scanner, "Введите username: ", true);

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
            String username = ConsoleUtils.promptString(scanner, "Введите username: ", true);

            Optional<User> userOpt = system.getUserManager().findByUsername(username);
            if (userOpt.isEmpty()) {
                System.out.println("Пользователь не найден.");
                return;
            }

            String permName = ConsoleUtils.promptString(scanner, "Введите название права: ", true).toUpperCase();
            String resource = ConsoleUtils.promptString(scanner, "Введите ресурс: ", true).toLowerCase();

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

        parser.registerCommand("scheduler-tick", "запустить один цикл планировщика (истечение временных ролей + лог)", (scanner, system) -> {
            ScheduledMaintenanceTask.runMaintenanceTick(system);
            System.out.println("Цикл планировщика выполнен; запись SCHEDULER_TICK добавлена в audit log.");
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

        parser.registerCommand("audit-log", "просмотр audit log", (scanner, system) -> {
            system.getAuditLog().printLog();
            if (!system.getAuditLog().getAll().isEmpty()
                    && ConsoleUtils.promptYesNo(scanner, "Сохранить audit log в файл? (да/нет): ")) {
                String filename = ConsoleUtils.promptString(scanner, "Имя файла: ", true);
                system.getAuditLog().saveToFile(filename);
                System.out.println("Audit log сохранён в " + filename);
            }
        });

        parser.registerCommand("report-users", "отчёт по пользователям", (scanner, system) -> {
            ReportGenerator generator = new ReportGenerator();
            String report = generator.generateUserReport(system.getUserManager(), system.getAssignmentManager());
            System.out.println(report);
            if (ConsoleUtils.promptYesNo(scanner, "Сохранить отчёт в файл? (да/нет): ")) {
                String filename = ConsoleUtils.promptString(scanner, "Имя файла: ", true);
                generator.exportToFile(report, filename);
                System.out.println("Отчёт сохранён в " + filename);
            }
        });

        parser.registerCommand("report-users-async", "отчёт по пользователям (фоновая генерация)", (scanner, system) -> {
            boolean save = ConsoleUtils.promptYesNo(scanner, "Сохранить результат в файл по готовности? (да/нет): ");
            final String filename = save ? ConsoleUtils.promptString(scanner, "Имя файла: ", true) : null;
            System.out.println("Запущена фоновая генерация отчёта по пользователям…");
            system.getAuditLog().log("REPORT_USERS_ASYNC", system.getCurrentUser(), "",
                    filename != null && !filename.isBlank() ? "save:" + filename : "print");

            final ReportGenerator generator = new ReportGenerator();
            system.getBackgroundExecutor().execute(() -> {
                try {
                    String report = generator.generateUserReport(system.getUserManager(), system.getAssignmentManager());
                    if (filename != null && !filename.isBlank()) {
                        generator.exportToFile(report, filename);
                        System.out.println("\n[фон] Отчёт готов, файл: " + filename);
                    } else {
                        System.out.println("\n[фон] Отчёт по пользователям:\n" + report);
                    }
                } catch (Throwable t) {
                    System.err.println("[фон] Ошибка отчёта: " + t.getMessage());
                }
            });
        });

        parser.registerCommand("save-async", "сохранение снимка данных в файл (фон)", (scanner, system) -> {
            String filename = ConsoleUtils.promptString(scanner, "Имя файла: ", true);
            System.out.println("Фоновое сохранение запущено: " + filename);
            system.getAuditLog().log("SAVE_ASYNC", system.getCurrentUser(), filename, "scheduled");
            system.getBackgroundExecutor().execute(() -> {
                try {
                    RbacSnapshotIO.exportToFile(system, filename);
                    System.out.println("\n[фон] Снимок сохранён: " + filename);
                    system.getAuditLog().log("SAVE_ASYNC", system.getCurrentUser(), filename, "done");
                } catch (Exception e) {
                    System.err.println("[фон] Ошибка сохранения: " + e.getMessage());
                    system.getAuditLog().log("SAVE_ASYNC", system.getCurrentUser(), filename,
                            "fail: " + (e.getMessage() != null ? e.getMessage() : "error"));
                }
            });
        });

        parser.registerCommand("report-roles", "отчёт по ролям", (scanner, system) -> {
            ReportGenerator generator = new ReportGenerator();
            String report = generator.generateRoleReport(system.getRoleManager(), system.getAssignmentManager());
            System.out.println(report);
            if (ConsoleUtils.promptYesNo(scanner, "Сохранить отчёт в файл? (да/нет): ")) {
                String filename = ConsoleUtils.promptString(scanner, "Имя файла: ", true);
                generator.exportToFile(report, filename);
                System.out.println("Отчёт сохранён в " + filename);
            }
        });

        parser.registerCommand("report-matrix", "матрица прав", (scanner, system) -> {
            ReportGenerator generator = new ReportGenerator();
            String report = generator.generatePermissionMatrix(system.getUserManager(), system.getAssignmentManager());
            System.out.println(report);
            if (ConsoleUtils.promptYesNo(scanner, "Сохранить отчёт в файл? (да/нет): ")) {
                String filename = ConsoleUtils.promptString(scanner, "Имя файла: ", true);
                generator.exportToFile(report, filename);
                System.out.println("Отчёт сохранён в " + filename);
            }
        });

        parser.registerCommand("clear", "очистить экран", (scanner, system) -> {
            System.out.print("\033[H\033[2J");
            System.out.flush();
        });

        parser.registerCommand("exit", "выход из программы", (scanner, system) -> {
            if (ConsoleUtils.promptYesNo(scanner, "Выйти? (да/нет): ")) {
                system.shutdownAsyncServices();
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
            String name = ConsoleUtils.promptString(scanner, "Название права (READ/WRITE/DELETE): ", true).toUpperCase();
            String resource = ConsoleUtils.promptString(scanner, "Ресурс (users/roles/reports): ", true).toLowerCase();
            String description = ConsoleUtils.promptString(scanner, "Описание: ", true);

            try {
                Permission permission = new Permission(name, resource, description);
                system.getRoleManager().addPermissionToRole(role.getName(), permission);
                System.out.println("Право добавлено.");
            } catch (Exception e) {
                System.out.println("Ошибка: " + e.getMessage());
            }

            if (!ConsoleUtils.promptYesNo(scanner, "Добавить еще? (да/нет): ")) {
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

}