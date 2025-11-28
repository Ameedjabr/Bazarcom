package bazar.frontend;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class FrontEndService {

    public static void main(String[] args) throws Exception {

        HttpServer server = HttpServer.create(new InetSocketAddress(9000), 0);

        server.createContext("/search", FrontEndService::handleSearch);
        server.createContext("/info", FrontEndService::handleInfo);
        server.createContext("/purchase", FrontEndService::handlePurchase);

        server.setExecutor(null);
        System.out.println("FrontEndService running at http://localhost:9000");
        server.start();
    }

    private static void handleSearch(HttpExchange ex) throws IOException {

        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/", 3);

        if (parts.length < 3) {
            sendText(ex, 400, "Missing topic: /search/{topic}");
            return;
        }

        String topic = parts[2];
        String url = "http://localhost:5000/search/" + topic;

        String response = httpGET(url);

        sendJSON(ex, 200, response);
    }

    private static void handleInfo(HttpExchange ex) throws IOException {

        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/", 3);

        if (parts.length < 3) {
            sendText(ex, 400, "Missing item ID: /info/{id}");
            return;
        }

        String id = parts[2];
        String url = "http://localhost:5000/info/" + id;

        String response = httpGET(url);

        sendJSON(ex, 200, response);
    }

    private static void handlePurchase(HttpExchange ex) throws IOException {

        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/", 3);

        if (parts.length < 3) {
            sendText(ex, 400, "Missing purchase ID: /purchase/{id}");
            return;
        }

        String id = parts[2];
        String url = "http://localhost:7000/order?id=" + id;

        String response = httpGET(url);

        sendJSON(ex, 200, response);
    }

    private static String httpGET(String urlString) throws IOException {

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder result = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            result.append(line);
        }

        br.close();
        return result.toString();
    }

    private static void sendText(HttpExchange ex, int code, String msg) throws IOException {
        byte[] bytes = msg.getBytes();
        ex.sendResponseHeaders(code, bytes.length);
        OutputStream os = ex.getResponseBody();
        os.write(bytes);
        os.close();
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
