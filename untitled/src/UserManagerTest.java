import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserManagerTest {

    private UserManager manager;

    @BeforeEach
    void setUp() {
        manager = new UserManager();
    }

    @Test
    void add_shouldAddUserAndIncreaseCount() {
        User user = User.create("alice", "Alice Smith", "alice@example.com");
        manager.add(user);

        assertEquals(1, manager.count());
        assertTrue(manager.exists("alice"));
    }

    @Test
    void add_duplicateUsername_shouldThrow() {
        User u1 = User.create("bob", "Bob", "bob@ex.com");
        User u2 = User.create("bob", "Bob2", "bob2@ex.com");

        manager.add(u1);
        assertThrows(IllegalArgumentException.class, () -> manager.add(u2));
    }

    @Test
    void update_shouldChangeNameAndEmail() {
        User original = User.create("test", "Old Name", "old@email.com");
        manager.add(original);

        manager.update("test", "New Name", "new@email.com");

        Optional<User> updated = manager.findByUsername("test");
        assertTrue(updated.isPresent());
        assertEquals("New Name", updated.get().fullName());
        assertEquals("new@email.com", updated.get().email());
    }

    @Test
    void update_nonExisting_shouldThrow() {
        assertThrows(NoSuchElementException.class,
                () -> manager.update("ghost", "Name", "email@ex.com"));
    }

    @Test
    void findByFilter_shouldReturnMatchingUsers() {
        manager.add(User.create("anna", "Anna", "anna@comp.ru"));
        manager.add(User.create("boris", "Boris", "boris@other.ru"));

        UserFilter filter = UserFilters.byEmailDomain("@comp.ru");

        List<User> result = manager.findByFilter(filter);
        assertEquals(1, result.size());
        assertEquals("anna", result.get(0).username());
    }
}