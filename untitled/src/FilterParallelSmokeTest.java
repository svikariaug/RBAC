import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

class FilterParallelSmokeTest {

    private RBACSystem system;
    private UserManager users;
    private RoleManager roles;
    private AssignmentManager assignments;

    @BeforeEach
    void setUp() {
        system = new RBACSystem(0);
        users = system.getUserManager();
        roles = system.getRoleManager();
        assignments = system.getAssignmentManager();
        users.add(User.create("a1", "A", "a1@ex.com"));
        users.add(User.create("b2", "B", "b2@ex.com"));
        Role r = new Role("R1", "R");
        roles.add(r);
    }

    @AfterEach
    void tearDown() {
        system.shutdownAsyncServices();
    }

    @Test
    void userFindByFilterParallel_matchesSequential() {
        UserFilter f = UserFilters.byEmailDomain("@ex.com");
        var seq = users.findByFilter(f);
        seq.sort(Comparator.comparing(User::username));
        var par = users.findByFilterParallel(f);
        par.sort(Comparator.comparing(User::username));
        assertEquals(seq, par);
    }

    @Test
    void roleFindByFilterParallel_matchesSequential() {
        RoleFilter f = RoleFilters.byNameContains("R");
        var seq = roles.findByFilter(f);
        seq.sort(Comparator.comparing(Role::getName));
        var par = roles.findByFilterParallel(f);
        par.sort(Comparator.comparing(Role::getName));
        assertEquals(seq, par);
    }

    @Test
    void assignmentFindByFilterParallel_matchesSequential() {
        AssignmentFilter f = AssignmentFilters.activeOnly();
        var seq = assignments.findByFilter(f);
        seq.sort(Comparator.comparing(RoleAssignment::assignmentId));
        var par = assignments.findByFilterParallel(f);
        par.sort(Comparator.comparing(RoleAssignment::assignmentId));
        assertEquals(seq, par);
    }
}
