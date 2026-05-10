
public class Main {

    public static void main(String[] args) {
        if (args.length >= 1 && "--user-validation".equals(args[0])) {
            UserValidationSmoke.main(new String[0]);
            return;
        }
        RbacConsoleApp.main(args);
    }
}
