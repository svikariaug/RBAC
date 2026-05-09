import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReportGeneratorTest {

    @Test
    void generatesReports() {
        RBACSystem system = new RBACSystem();
        system.initialize();

        ReportGenerator generator = new ReportGenerator();
        String users = generator.generateUserReport(system.getUserManager(), system.getAssignmentManager());
        String roles = generator.generateRoleReport(system.getRoleManager(), system.getAssignmentManager());
        String matrix = generator.generatePermissionMatrix(system.getUserManager(), system.getAssignmentManager());

        assertTrue(users.contains("Отчёт по пользователям"));
        assertTrue(roles.contains("Отчёт по ролям"));
        assertTrue(matrix.contains("Матрица прав"));
    }
}

