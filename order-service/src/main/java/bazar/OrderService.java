package bazar.order;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class OrderService {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(7000), 0);

        server.createContext("/order", (HttpExchange exchange) -> {
            try {
                if ("GET".equals(exchange.getRequestMethod())) {
                    String query = exchange.getRequestURI().getQuery();

                    if (query == null || !query.contains("id=")) {
                        sendResponse(exchange, "Missing product ID: use /order?id=1001", 400);
                        return;
                    }

                    String productId = query.split("=")[1];

                    String productDetails = fetchProductDetails(productId);

                    if (productDetails == null) {
                        sendResponse(exchange, "Product not found!", 404);
                        return;
                    }

                    sendResponse(exchange, "Order confirmed: " + productDetails, 200);
                }
            } catch (Exception ex) {
                sendResponse(exchange, "Error processing request.", 500);
            }
        });

        server.setExecutor(null);
        System.out.println("OrderService running at http://localhost:7000/order?id=1001");
        server.start();
    }

    private static String fetchProductDetails(String id) throws IOException {
    URL url = new URL("http://localhost:5000/products?id=" + id);

    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");

    BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
    String line;
    while ((line = br.readLine()) != null) {
        if (line.startsWith(id + ",")) {
            br.close();
            return line;
        }
    }
    br.close();
    return null;
}


    private static void sendResponse(HttpExchange exchange, String response, int statusCode) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}

