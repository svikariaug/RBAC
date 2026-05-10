import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AssignmentManager implements Repository<RoleAssignment> {
    private final Map<String, RoleAssignment> assignmentsById = new ConcurrentHashMap<>();
    private final UserManager userManager;
    private final RoleManager roleManager;

    public AssignmentManager(UserManager userManager, RoleManager roleManager) {
        this.userManager = userManager;
        this.roleManager = roleManager;
    }

    @Override
    public synchronized void add(RoleAssignment assignment) {
        if (userManager.findByUsername(assignment.user().username()).isEmpty()) {
            throw new IllegalArgumentException("User not found: " + assignment.user().username());
        }
        if (roleManager.findById(assignment.role().getId()).isEmpty()) {
            throw new IllegalArgumentException("Role not found: " + assignment.role().getId());
        }
        if (userHasRole(assignment.user(), assignment.role())) {
            throw new IllegalArgumentException("User already has this role assigned");
        }
        if (assignmentsById.putIfAbsent(assignment.assignmentId(), assignment) != null) {
            throw new IllegalArgumentException("Assignment id уже существует");
        }
    }

    @Override
    public synchronized boolean remove(RoleAssignment assignment) {
        return assignmentsById.remove(assignment.assignmentId()) != null;
    }

    @Override
    public Optional<RoleAssignment> findById(String id) {
        return Optional.ofNullable(assignmentsById.get(id));
    }

    @Override
    public List<RoleAssignment> findAll() {
        return new ArrayList<>(assignmentsById.values());
    }

    @Override
    public int count() {
        return assignmentsById.size();
    }

    @Override
    public synchronized void clear() {
        assignmentsById.clear();
    }

    public List<RoleAssignment> findByFilterParallel(AssignmentFilter filter) {
        return assignmentsById.values().parallelStream()
                .filter(filter::test)
                .collect(Collectors.toList());
    }

    public List<RoleAssignment> findByUser(User user) {
        return assignmentsById.values().stream()
                .filter(assignment -> assignment.user().equals(user))
                .collect(Collectors.toList());
    }

    public List<RoleAssignment> findByRole(Role role) {
        return assignmentsById.values().stream()
                .filter(assignment -> assignment.role().equals(role))
                .collect(Collectors.toList());
    }

    public List<RoleAssignment> findByFilter(AssignmentFilter filter) {
        return assignmentsById.values().stream()
                .filter(filter::test)
                .collect(Collectors.toList());
    }

    public List<RoleAssignment> findAll(AssignmentFilter filter, Comparator<RoleAssignment> sorter) {
        return assignmentsById.values().stream()
                .filter(filter::test)
                .sorted(sorter)
                .collect(Collectors.toList());
    }

    public List<RoleAssignment> getActiveAssignments() {
        return findByFilter(AssignmentFilters.activeOnly());
    }

    public List<RoleAssignment> getExpiredAssignments() {
        return findByFilter(AssignmentFilters.inactiveOnly());
    }

    public boolean userHasRole(User user, Role role) {
        return findByUser(user).stream()
                .anyMatch(assignment -> assignment.role().equals(role) && assignment.isActive());
    }

    public boolean userHasPermission(User user, String permissionName, String resource) {
        return getUserPermissions(user).stream()
                .anyMatch(p -> p.name().equalsIgnoreCase(permissionName) && p.resource().equalsIgnoreCase(resource));
    }

    public Set<Permission> getUserPermissions(User user) {
        Set<Permission> permissions = new HashSet<>();
        findByUser(user).stream()
                .filter(RoleAssignment::isActive)
                .forEach(assignment -> permissions.addAll(assignment.role().getPermissions()));
        return permissions;
    }

    public void revokeAssignment(String assignmentId) {
        RoleAssignment assignment = findById(assignmentId).orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));
        if (assignment instanceof PermanentAssignment pa) {
            pa.revoke();
        } else if (assignment instanceof TemporaryAssignment ta) {
            
            ta.extend("2000-01-01 00:00");
        }
    }

    public void extendTemporaryAssignment(String assignmentId, String newExpirationDate) {
        RoleAssignment assignment = findById(assignmentId).orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));
        if (assignment instanceof TemporaryAssignment ta) {
            ta.extend(newExpirationDate);
        } else {
            throw new IllegalArgumentException("Assignment is not temporary: " + assignmentId);
        }
    }

    
    public int finalizeExpiredTemporaryAssignments() {
        int n = 0;
        for (RoleAssignment ra : findAll()) {
            if (ra instanceof TemporaryAssignment ta && ta.finalizeExpirationIfDue()) {
                n++;
            }
        }
        return n;
    }
}