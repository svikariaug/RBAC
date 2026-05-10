import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReportGeneratorTest {

    @Test
    void generatesReports() {
        RBACSystem system = new RBACSystem(0);
        system.initialize();

        ReportGenerator generator = new ReportGenerator();
        String users = generator.generateUserReport(system.getUserManager(), system.getAssignmentManager());
        String usersPar = generator.generateUserReportParallel(system.getUserManager(), system.getAssignmentManager());
        assertEquals(users, usersPar);

        String roles = generator.generateRoleReport(system.getRoleManager(), system.getAssignmentManager());

        String matrix = generator.generatePermissionMatrix(system.getUserManager(), system.getAssignmentManager());
        String matrixPar = generator.generatePermissionMatrixParallel(system.getUserManager(), system.getAssignmentManager());
        assertEquals(matrix, matrixPar);

        assertTrue(users.contains("Отчёт по пользователям"));
        assertTrue(roles.contains("Отчёт по ролям"));
        assertTrue(matrix.contains("Матрица прав"));

        try {
            system.shutdownAsyncServices();
        } catch (Exception ignored) {
        }
    }
}

