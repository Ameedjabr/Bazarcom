package bazar.order;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class OrderService {

    private static final Gson gson = new Gson();

    public static void main(String[] args) throws Exception {

        HttpServer server = HttpServer.create(new InetSocketAddress(7000), 0);

        server.createContext("/order", OrderService::handleOrder);

        server.setExecutor(null);
        System.out.println("OrderService running at http://localhost:7000/order?id=1");

        server.start();
    }

    /* -------------------- Handler -------------------- */

    private static void handleOrder(HttpExchange exchange) throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendResponse(exchange, 405, "Only GET allowed");
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        if (query == null || !query.contains("id=")) {
            sendResponse(exchange, 400, "Missing product ID: /order?id=1");
            return;
        }

        String id = query.split("=")[1];
        System.out.println("Received order request for ID=" + id);

        // 1) Fetch product info from CatalogService
        JsonObject item = fetchItemInfo(id);

        if (item == null) {
            sendResponse(exchange, 404, "Item not found in catalog!");
            return;
        }

        int qty = item.get("quantity").getAsInt();
        String title = item.get("title").getAsString();
        double price = item.get("price").getAsDouble();

        // 2) Check stock
        if (qty <= 0) {
            sendResponse(exchange, 400, "Item is OUT OF STOCK");
            return;
        }

        // 3) Decrement stock → call Catalog /update/{id}
        boolean updated = updateStock(id, qty - 1);

        if (!updated) {
            sendResponse(exchange, 500, "Failed to update stock!");
            return;
        }

        // 4) Return success JSON
        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("message", "Book purchased successfully");
        response.addProperty("title", title);
        response.addProperty("price", price);

        sendJSON(exchange, 200, response.toString());
    }

    /* -------------------- Helper: GET info/{id} -------------------- */

    private static JsonObject fetchItemInfo(String id) throws IOException {
        URL url = new URL("http://localhost:5000/info/" + id);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        if (conn.getResponseCode() != 200) {
            return null;
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder json = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            json.append(line);
        }

        br.close();
        return gson.fromJson(json.toString(), JsonObject.class);
    }

    /* -------------------- Helper: POST update/{id} -------------------- */

    private static boolean updateStock(String id, int newQty) throws IOException {
        URL url = new URL("http://localhost:5000/update/" + id + "?quantity=" + newQty);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");

        return conn.getResponseCode() == 200;
    }

    /* -------------------- Response helpers -------------------- */

    private static void sendResponse(HttpExchange ex, int code, String msg) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("error", msg);
        sendJSON(ex, code, json.toString());
    }

    private static void sendJSON(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes();

        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);

        OutputStream os = ex.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
