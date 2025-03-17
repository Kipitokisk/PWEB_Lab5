import java.util.HashMap;
import java.util.Map;

public class Go2Web {
    private static final Map<String, String> cache = new HashMap<>();

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equals("-h")) {
        }

        if (args[0].equals("-u") && args.length > 1) {

        } else if (args[0].equals("-s") && args.length > 1) {

        } else {
            System.out.println("Invalid arguments! Use -h for help.");
        }
    }
}
