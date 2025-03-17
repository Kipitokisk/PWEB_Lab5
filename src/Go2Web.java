import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class Go2Web {
    private static final Map<String, String> cache = new HashMap<>();

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equals("-h")) {
            printHelp();
        }

        if (args[0].equals("-u") && args.length > 1) {

        } else if (args[0].equals("-s") && args.length > 1) {

        } else {
            System.out.println("Invalid arguments! Use -h for help.");
        }
    }

    private static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  go2web -u <URL>         # Fetch a webpage");
        System.out.println("  go2web -s <search-term> # Search using DuckDuckGo");
        System.out.println("  go2web -h               # Show this help");
    }

    private static void fetchURL(String url, boolean isRedirect) {
        try {
            URL parsedURL = new URL(url);
            String host = parsedURL.getHost();
            String path = parsedURL.getPath().isEmpty() ? "/" : parsedURL.getPath();

            if (cache.containsKey(url)) {
                System.out.println("[Cache Hit] " + url);
                System.out.println(cache.get(url));
                return;
            }

            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n"
                    + "Connection: close\r\n"
                    + "Accept: text/html\r\n\r\n";

            String response = sendHttpRequest(host, 80, request);

            cache.put(url, response);

            System.out.println(cleanHTML(response));

        } catch (Exception e) {
            System.out.println("Invalid URL: " + e.getMessage());
        }
    }

    private static String sendHttpRequest(String host, int port, String request) {
        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.print(request);
            out.flush();

            StringBuilder response = new StringBuilder();
            String line;
            boolean headersEnded = false;

            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) {
                    headersEnded = true;
                    continue;
                }
                if (headersEnded) response.append(line).append("\n");
            }

            return response.toString();

        } catch (IOException e) {
            return "Error connecting to server: " + e.getMessage();
        }
    }

    private static String cleanHTML(String html) {
        return html.replaceAll("<[^>]*>", "").replaceAll("&\\w+;", " ");
    }
}
