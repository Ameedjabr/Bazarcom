package bazar.catalog;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class CatalogService {

    private static final String CATALOG_PATH =
    "data/catalog.csv";


    private static final Gson gson = new Gson();

    private static final Map<String, Book> catalog = new HashMap<>();

    public static void main(String[] args) throws Exception {

    loadCatalog();

    // 👇 ADD THIS
    int port = (args.length > 0) ? Integer.parseInt(args[0]) : 5000;

    // 👇 REPLACE 5000 with port
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

    server.createContext("/search", CatalogService::handleSearch);
    server.createContext("/info", CatalogService::handleInfo);
    server.createContext("/update", CatalogService::handleUpdate);

    // aliases (if you use them)
    server.createContext("/query", CatalogService::handleInfo);
    server.createContext("/query/subject", CatalogService::handleSearch);

    server.setExecutor(null);

    // 👇 PRINT ACTUAL PORT
    System.out.println("CatalogService running on http://localhost:" + port);

    server.start();
}


    /* -------------------- Handlers -------------------- */

    // /search/{topic}
    private static void handleSearch(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendError(exchange, 405, "Only GET allowed");
            return;
        }

        String[] parts = exchange.getRequestURI().getPath().split("/", 3);
        if (parts.length < 3) {
            sendError(exchange, 400, "Topic missing: /search/{topic}");
            return;
        }

        String topic = URLDecoder.decode(parts[2], StandardCharsets.UTF_8);

        List<Map<String, Object>> results = new ArrayList<>();

        for (Book b : catalog.values()) {
            if (b.topic.equalsIgnoreCase(topic)) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("id", b.id);
                entry.put("title", b.title);
                results.add(entry);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("items", results);

        sendJSON(exchange, gson.toJson(response), 200);
    }

    // /info/{id}
    private static void handleInfo(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendError(exchange, 405, "Only GET allowed");
            return;
        }

        String[] parts = exchange.getRequestURI().getPath().split("/", 3);
        if (parts.length < 3) {
            sendError(exchange, 400, "Item id missing: /info/{id}");
            return;
        }

        String id = parts[2];
        Book b = catalog.get(id);

        if (b == null) {
            sendError(exchange, 404, "Item not found");
            return;
        }

        sendJSON(exchange, gson.toJson(b), 200);
    }

    // /update/{id}?price=50&quantity=10
    private static void handleUpdate(HttpExchange exchange) throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(exchange, 405, "Only POST allowed");
            return;
        }

        String[] parts = exchange.getRequestURI().getPath().split("/", 3);
        if (parts.length < 3) {
            sendError(exchange, 400, "Item id missing: /update/{id}");
            return;
        }

        String id = parts[2];
        Book b = catalog.get(id);

        if (b == null) {
            sendError(exchange, 404, "Item not found");
            return;
        }

        // Parse query params
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

        if (params.containsKey("price")) {
            b.price = Double.parseDouble(params.get("price"));
        }
        if (params.containsKey("quantity")) {
            b.quantity = Integer.parseInt(params.get("quantity"));
        }

        

        // Save updated catalog back to CSV
          saveCatalog();

        sendJSON(exchange, gson.toJson(Map.of("status", "updated")), 200);
    }

    /* -------------------- Helpers -------------------- */

    private static void replicateUpdateToOtherReplica(
        String id, double price, int quantity) {

    try {
        // If this replica is 5000, replicate to 5001
        URL url = new URL(
            "http://localhost:5001/update/" + id +
            "?price=" + price + "&quantity=" + quantity
        );

        java.net.HttpURLConnection conn =
                (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.getResponseCode();

    } catch (Exception e) {
        System.err.println("Replication failed for item " + id);
    }
}


    private static void loadCatalog() throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(CATALOG_PATH))) {
            String line;
            while ((line = br.readLine()) != null) {

                if (line.isBlank()) continue;

                String[] p = line.split(",");
                if (p.length != 5) continue;

                String id = p[0].trim();
                String title = p[1].trim();
                String topic = p[2].trim();
                int quantity = Integer.parseInt(p[3].trim());
                double price = Double.parseDouble(p[4].trim());

                catalog.put(id, new Book(id, title, topic, quantity, price));
            }
        }

        System.out.println("Loaded " + catalog.size() + " items from CSV");
    }

    private static void saveCatalog() throws IOException {
        try (FileWriter fw = new FileWriter(CATALOG_PATH)) {
            for (Book b : catalog.values()) {
                fw.write(b.id + "," + b.title + "," + b.topic + "," + b.quantity + "," + b.price + "\n");
            }
        }
        System.out.println("Catalog updated and saved.");
    }

   


    private static Map<String, String> parseQuery(String q) {
        Map<String, String> map = new HashMap<>();
        if (q == null) return map;

        for (String param : q.split("&")) {
            String[] parts = param.split("=");
            if (parts.length == 2)
                map.put(parts[0], parts[1]);
        }
        return map;
    }

    private static void sendJSON(HttpExchange exchange, String json, int status) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, int code, String msg) throws IOException {
        sendJSON(exchange, gson.toJson(Map.of("error", msg)), code);
    }

    /* -------------------- Model -------------------- */

    static class Book {
        String id, title, topic;
        int quantity;
        double price;

        public Book(String id, String title, String topic, int quantity, double price) {
            this.id = id;
            this.title = title;
            this.topic = topic;
            this.quantity = quantity;
            this.price = price;
        }
    }
}
