import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Go2Web {
    private static final Map<String, String> cache = new HashMap<>();

    public static void main(String[] args) {
        loadCache();
        if (args.length == 0 || args[0].equals("-h")) {
            printHelp();
        }

        if (args[0].equals("-u") && args.length > 1) {
            fetchURL(args[1], 0, false);
        } else if (args[0].equals("-s") && args.length > 1) {
            searchWeb(args);
        } else {
            System.out.println("Invalid arguments! Use -h for help.");
        }
        saveCache();
    }

    private static void loadCache() {
        try (BufferedReader reader = new BufferedReader(new FileReader("go2web_cache.txt"))) {
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
        try (PrintWriter writer = new PrintWriter(new FileWriter("go2web_cache.txt"))) {
            for (Map.Entry<String, String> entry : cache.entrySet()) {
                writer.println("URL::" + entry.getKey());
                if (!entry.getValue().isEmpty()) {
                    writer.println(entry.getValue());
                }
            }
            System.out.println("[Cache]" + cache.size() + " entries in cache file");
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

    private static void fetchURL(String url, int redirectCount, boolean isSearch) {
        try {
            if (redirectCount > 5) {
                System.out.println("[Error] Too many redirects (maximum " + 5 + ")");
                return;
            }

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }

            URL parsedURL = new URL(url);
            String protocol = parsedURL.getProtocol();
            String host = parsedURL.getHost();
            String path = parsedURL.getPath().isEmpty() ? "/" : parsedURL.getPath();
            if (parsedURL.getQuery() != null) {
                path += "?" + parsedURL.getQuery();
            }

            int port = parsedURL.getPort();
            if (port == -1) {
                port = protocol.equals("https") ? 443 : 80;
            }

            if (cache.containsKey(url)) {
                System.out.println("[Cache Hit] " + url);
                System.out.println(cache.get(url));
                return;
            }

            System.out.println("[Fetching] " + url);

            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n"
                    + "Connection: close\r\n"
                    + "Accept: text/html\r\n"
                    + "User-Agent: Go2Web/1.0\r\n\r\n";

            String response;
            if (protocol.equals("https")) {
                response = sendHttpsRequest(host, port, request);
            } else {
                response = sendHttpRequest(host, port, request);
            }

            String[] parts = response.split("\r\n\r\n", 2);
            String headers = parts[0];
            String body = parts.length > 1 ? parts[1] : "";

            if (headers.contains("HTTP/1.1 301") || headers.contains("HTTP/1.1 302") ||
                    headers.contains("HTTP/1.0 301") || headers.contains("HTTP/1.0 302") ||
                    headers.contains("HTTP/1.1 307") || headers.contains("HTTP/1.1 308")) {

                String newLocation = host + extractRedirectLocation(headers);
                if (newLocation != null) {
                    fetchURL(newLocation, redirectCount + 1, isSearch);
                    return;
                }
            }

            if (!isSearch) {
                cache.put(url, cleanHTML(body));
                System.out.println(cleanHTML(body));
            } else {
                System.out.println(search(body));
            }

        } catch (Exception e) {
            System.out.println("[Error] Problem with URL: " + e.getMessage());
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

    private static String sendHttpsRequest(String host, int port, String request) {
        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket socket = (SSLSocket) factory.createSocket(host, port);

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.print(request);
            out.flush();

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line).append("\r\n");
            }

            socket.close();
            return response.toString();

        } catch (IOException e) {
            return "Error connecting to HTTPS server: " + e.getMessage();
        }
    }


    private static void searchWeb(String[] args) {
        try {
            String query = String.join("+", Arrays.copyOfRange(args, 1, args.length));
            String searchURL = "http://www.bing.com/search?q=" + query;

            System.out.println("[Search] " + searchURL);
            fetchURL(searchURL, 0, true);
        } catch (Exception e) {
            System.out.println("Error performing search: " + e.getMessage());
        }
    }

    private static String cleanHTML(String html) {
        try {
            Document doc = Jsoup.parse(html);
            StringBuilder output = new StringBuilder();

            String title = doc.title();
            if (!title.isEmpty()) {
                output.append("Title: ").append(title).append("\n\n");
            }

            Elements headings = doc.select("h1, h2, h3");
            for (Element heading : headings) {
                output.append(heading.tagName().toUpperCase()).append(": ").append(heading.text()).append("\n");
            }

            output.append("\n");

            Elements paragraphs = doc.select("p");
            for (Element paragraph : paragraphs) {
                output.append(paragraph.text()).append("\n\n");
            }

            Elements links = doc.select("a[href]");
            output.append("Links:\n");
            for (Element link : links) {
                output.append("- ").append(link.text()).append(": ").append(link.absUrl("href")).append("\n");
            }

            return output.toString().trim();

        } catch (Exception e) {
            return "Error parsing HTML: " + e.getMessage();
        }
    }

    private static String extractRedirectLocation(String headers) {
        Pattern pattern = Pattern.compile("Location: (.*?)\\r\\n", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(headers);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String search(String html) {
        StringBuilder output = new StringBuilder();
        Pattern pattern = Pattern.compile("<h2><a href=\"(.*?)\".*?>(.*?)</a></h2>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);

        int count = 0;
        while (matcher.find() && count < 10) {
            String link = matcher.group(1).replace("&amp;", "&");
            String title = matcher.group(2).replaceAll("<.*?>", "");

            output.append(count + 1).append(". ").append(title).append("\n   Link: ").append(link).append("\n\n");
            count++;
        }
        return output.toString();
    }
}