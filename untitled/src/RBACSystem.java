import java.util.*;

public class RBACSystem {
    private final UserManager userManager;
    private final RoleManager roleManager;
    private final AssignmentManager assignmentManager;
    private String currentUser;

    public RBACSystem() {
        this.userManager = new UserManager();
        this.roleManager = new RoleManager();
        this.assignmentManager = new AssignmentManager(userManager, roleManager);
        this.roleManager.setAssignmentManager(this.assignmentManager);
        this.currentUser = "system";
    }

    public RBACSystem(String currentUser) {
        this();
        setCurrentUser(currentUser);
    }

    public UserManager getUserManager() { return userManager; }
    public RoleManager getRoleManager() { return roleManager; }
    public AssignmentManager getAssignmentManager() { return assignmentManager; }

    public void setCurrentUser(String username) {
        if (username == null || username.equals("system")) {
            this.currentUser = "system";
        } else if (userManager.exists(username)) {
            this.currentUser = username;
        } else {
            throw new IllegalArgumentException("Пользователь " + username + " не существует");
        }
    }

    public String getCurrentUser() { return currentUser; }

    public void initialize() {
        Permission p1 = new Permission("READ", "users", "Просмотр пользователей");
        Permission p2 = new Permission("WRITE", "users", "Редактирование пользователей");
        Permission p3 = new Permission("DELETE", "users", "Удаление пользователей");
        Permission p4 = new Permission("READ", "roles", "Просмотр ролей");
        Permission p5 = new Permission("ALL", "system", "Полный доступ к системе");

        Role adminRole = new Role("ADMIN", "Администратор с полным доступом");
        adminRole.addPermission(p1);
        adminRole.addPermission(p2);
        adminRole.addPermission(p3);
        adminRole.addPermission(p4);
        adminRole.addPermission(p5);

        Role managerRole = new Role("MANAGER", "Менеджер для управления пользователями");
        managerRole.addPermission(p1);
        managerRole.addPermission(p2);
        managerRole.addPermission(p4);

        Role viewerRole = new Role("VIEWER", "Наблюдатель только для чтения");
        viewerRole.addPermission(p1);
        viewerRole.addPermission(p4);

        roleManager.add(adminRole);
        roleManager.add(managerRole);
        roleManager.add(viewerRole);

        User admin = User.create("admin", "System Administrator", "admin@company.com");
        userManager.add(admin);

        AssignmentMetadata metadata = AssignmentMetadata.now(currentUser, "Initial system setup");
        RoleAssignment adminAssign = new PermanentAssignment(admin, adminRole, metadata);
        assignmentManager.add(adminAssign);

        try {
            User user1 = User.create("john_doe", "John Doe", "john@example.com");
            userManager.add(user1);

            User user2 = User.create("jane_smith", "Jane Smith", "jane@example.com");
            userManager.add(user2);

            // Назначаем роли
            AssignmentMetadata meta1 = AssignmentMetadata.now(currentUser, "Viewer role");
            assignmentManager.add(new PermanentAssignment(user1, viewerRole, meta1));

            AssignmentMetadata meta2 = AssignmentMetadata.now(currentUser, "Manager role");
            assignmentManager.add(new PermanentAssignment(user2, managerRole, meta2));

        } catch (IllegalArgumentException e) {
            System.err.println("Ошибка при создании тестовых пользователей: " + e.getMessage());
        }
    }

    public String generateStatistics() {
        int users = userManager.count();
        int roles = roleManager.count();
        int totalAssign = assignmentManager.count();
        int active = assignmentManager.getActiveAssignments().size();
        int expired = assignmentManager.getExpiredAssignments().size();
        double avg = users > 0 ? (double) totalAssign / users : 0;

        return String.format("""
                === Статистика RBAC ===
                Текущий пользователь: %s
                Пользователей: %d
                Ролей: %d
                Назначений: %d (активных: %d, истекших: %d)
                Среднее ролей на пользователя: %.1f
                """, currentUser, users, roles, totalAssign, active, expired, avg);
    }
}