import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Go2Web {
    private static final String CACHE_FILE = "go2web_cache.txt";
    private static final Map<String, String> cache = new HashMap<>();

    public static void main(String[] args) {
        loadCache();
        if (args.length == 0 || args[0].equals("-h")) {
            printHelp();
        }

        if (args[0].equals("-u") && args.length > 1) {
            fetchURL(args[1], false);
        } else if (args[0].equals("-s") && args.length > 1) {
            searchWeb(args);
        } else {
            System.out.println("Invalid arguments! Use -h for help.");
        }

        saveCache();
    }

    private static void loadCache() {
        try (BufferedReader reader = new BufferedReader(new FileReader(CACHE_FILE))) {
            String line;
            String currentUrl = null;
            StringBuilder contentBuilder = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("URL::")) {
                    if (currentUrl != null) {
                        cache.put(currentUrl, contentBuilder.toString());
                        contentBuilder = new StringBuilder();
                    }
                    currentUrl = line.substring(5);
                } else if (currentUrl != null) {
                    contentBuilder.append(line).append("\n");
                }
            }

            if (currentUrl != null) {
                cache.put(currentUrl, contentBuilder.toString());
            }

            System.out.println("[Cache] Loaded " + cache.size() + " entries from file");
        } catch (IOException e) {
            System.out.println("[Cache] No existing cache file found. Creating new cache.");
        }
    }

    private static void saveCache() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CACHE_FILE))) {
            for (Map.Entry<String, String> entry : cache.entrySet()) {
                writer.println("URL::" + entry.getKey());
                if (!entry.getValue().isEmpty()) {
                    writer.println(entry.getValue());
                }
            }
            System.out.println("[Cache] Saved " + cache.size() + " entries to file");
        } catch (IOException e) {
            System.out.println("[Cache] Error saving cache: " + e.getMessage());
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
            if (parsedURL.getQuery() != null) {
                path += "?" + parsedURL.getQuery();
            }

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

            String[] parts = response.split("\r\n\r\n", 2);
            String headers = parts[0];
            String body = parts.length > 1 ? parts[1] : "";

            if (!isRedirect && (headers.contains("HTTP/1.1 301") || headers.contains("HTTP/1.1 302"))) {
                String newLocation = extractRedirectLocation(headers);
                if (newLocation != null) {
                    System.out.println("[Redirect] Following to: " + newLocation);
                    fetchURL(newLocation, true);
                    return;
                }
            }

            String cleanedBody = cleanHTML(body);
            cache.put(url, cleanedBody);
            System.out.println(cleanedBody);

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
            while ((line = in.readLine()) != null) {
                response.append(line).append("\r\n");
            }

            return response.toString();

        } catch (IOException e) {
            return "Error connecting to server: " + e.getMessage();
        }
    }


    private static void searchWeb(String[] args) {
        try {
            String query = String.join("+", Arrays.copyOfRange(args, 1, args.length));
            String searchURL = "http://www.bing.com/search?q=" + query;

            System.out.println("[Search] " + searchURL);
            fetchURL(searchURL, false);
        } catch (Exception e) {
            System.out.println("Error performing search: " + e.getMessage());
        }
    }

    private static String cleanHTML(String html) {
        StringBuilder output = new StringBuilder();

        // For Bing search results
        Pattern pattern = Pattern.compile("<h2><a href=\"(.*?)\".*?>(.*?)</a></h2>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);

        int count = 0;
        while (matcher.find() && count < 10) { // Limit to top 10 results
            String link = matcher.group(1).replace("&amp;", "&");
            String title = matcher.group(2).replaceAll("<.*?>", "");

            output.append(count + 1).append(". ").append(title).append("\n   Link: ").append(link).append("\n\n");
            count++;
        }

        if (count == 0) {
            // Simple HTML tag stripping for regular web pages
            String plainText = html.replaceAll("<script.*?</script>", "")
                    .replaceAll("<style.*?</style>", "")
                    .replaceAll("<.*?>", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            output.append(plainText);
        }

        return output.toString();
    }



    private static String extractRedirectLocation(String headers) {
        Pattern pattern = Pattern.compile("Location: (.*?)\\r\\n", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(headers);
        return matcher.find() ? matcher.group(1) : null;
    }
}
