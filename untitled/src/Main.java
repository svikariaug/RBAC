public class Main {

    public static void main(String[] args) {
        System.out.println("Проверка класса User\n");
        System.out.println("Текущая дата в задании: 18 февраля 2026\n");

        test("alice123",      "Алиса Смирнова",     "alice@company.ru");
        test("dev_007",       "Боб Иванов",         "bob.dev@team.org");
        test("user_19_chars", "Тест Тестович",      "test19@example.org");
        test("a1_b2_c3",      "Три символа мин",    "min3@ok.ru");

        System.out.println();

        testFail("ab",              "Короткий",          "short@ok.ru");
        testFail("user name",       "Пробел",            "space@ok.ru");
        testFail("user!",           "Запрещённый символ","bad!@mail.ru");
        testFail("user@name",       "Собака",            "at@mail.ru");
        testFail("veryveryveryveryveryverylo", "Длинный", "long@name.com");
        testFail("ok",              "Без собаки",        "no-at.example.com");
        testFail("ok",              "Нет точки после @", "no.dot@mailcom");
        testFail("ok",              "Только @.",         "user@.ru");
        testFail(null,              "Null username",     "null@null.ru");
        testFail("ok",              null,                "null@null.ru");
        testFail("ok",              "Имя",               null);
        testFail("ok",              "",                  "empty@ok.ru");
        testFail("ok",              "Имя",               "");

        System.out.println("\nПроверка завершена.");
    }

    private static void test(String username, String fullName, String email) {
        try {
            User u = User.create(username, fullName, email);
            System.out.printf("[ OK ] %-20s → %s %n",
                    username, u.format());
        } catch (IllegalArgumentException e) {
            System.out.printf("[FAIL — не должно было упасть] %-20s   %s %n",
                    username, e.getMessage());
        }
    }

    private static void testFail(String username, String fullName, String email) {
        try {
            User.create(username, fullName, email);
            System.out.printf("[ОШИБКА — должно было выбросить исключение] %-20s %n",
                    username);
        } catch (IllegalArgumentException e) {
            System.out.printf("[ожидаемо] %-20s → %s %n",
                    username, e.getMessage());
        }
    }
}