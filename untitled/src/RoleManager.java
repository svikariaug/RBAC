
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RoleManager implements Repository<Role> {
    private final Map<String, Role> rolesById = new ConcurrentHashMap<>();
    private final Map<String, Role> rolesByName = new ConcurrentHashMap<>();
    private AssignmentManager assignmentManager;

    public void setAssignmentManager(AssignmentManager assignmentManager) {
        this.assignmentManager = assignmentManager;
    }

    @Override
    public synchronized void add(Role role) {
        String key = role.getName().toUpperCase();
        if (rolesByName.containsKey(key)) {
            throw new IllegalArgumentException("Role with name " + role.getName() + " already exists");
        }
        rolesById.put(role.getId(), role);
        rolesByName.put(key, role);
    }

    @Override
    public synchronized boolean remove(Role role) {
        if (assignmentManager != null && !assignmentManager.findByRole(role).isEmpty()) {
            throw new IllegalStateException("Cannot remove role " + role.getName() + " as it is assigned to users");
        }
        rolesById.remove(role.getId());
        rolesByName.remove(role.getName().toUpperCase());
        return true;
    }

    @Override
    public Optional<Role> findById(String id) {
        return Optional.ofNullable(rolesById.get(id));
    }

    @Override
    public List<Role> findAll() {
        return new ArrayList<>(rolesById.values());
    }

    @Override
    public int count() {
        return rolesById.size();
    }

    @Override
    public synchronized void clear() {
        rolesById.clear();
        rolesByName.clear();
    }

    public Optional<Role> findByName(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(rolesByName.get(name.trim().toUpperCase()));
    }

    public List<Role> findByFilter(RoleFilter filter) {
        return rolesById.values().stream()
                .filter(filter::test)
                .collect(Collectors.toList());
    }

    public List<Role> findByFilterParallel(RoleFilter filter) {
        return rolesById.values().parallelStream()
                .filter(filter::test)
                .collect(Collectors.toList());
    }

    public List<Role> findAll(RoleFilter filter, Comparator<Role> sorter) {
        return rolesById.values().stream()
                .filter(filter::test)
                .sorted(sorter)
                .collect(Collectors.toList());
    }

    public boolean exists(String name) {
        if (name == null) return false;
        return rolesByName.containsKey(name.trim().toUpperCase());
    }

    public synchronized void addPermissionToRole(String roleName, Permission permission) {
        Role role = findByName(roleName).orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
        role.addPermission(permission);
    }

    public synchronized void removePermissionFromRole(String roleName, Permission permission) {
        Role role = findByName(roleName).orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
        role.removePermission(permission);
    }

    public List<Role> findRolesWithPermission(String permissionName, String resource) {
        return rolesById.values().stream()
                .filter(role -> role.hasPermission(permissionName, resource))
                .collect(Collectors.toList());
    }
}