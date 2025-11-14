package bazar.catalog;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class CatalogService {

    public static void main(String[] args) throws Exception {
        // Start server on port 5000
        HttpServer server = HttpServer.create(new InetSocketAddress(5000), 0);

        // Handle GET /products
        server.createContext("/products", (HttpExchange exchange) -> {
            try {
                String csvData = readCSV("src/main/resources/catalog.csv");

                exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
                exchange.sendResponseHeaders(200, csvData.getBytes().length);
                exchange.getResponseBody().write(csvData.getBytes());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                exchange.close();
            }
        });

        server.setExecutor(null); 
        System.out.println("CatalogService running on http://localhost:5000/products");
        server.start();
    }

    // Reads CSV file and returns its content
    private static String readCSV(String path) throws IOException {
        StringBuilder content = new StringBuilder();
        BufferedReader br = new BufferedReader(new FileReader(path));

        String line;
        while ((line = br.readLine()) != null) {
            content.append(line).append("\n");
        }
        br.close();
        return content.toString();
    }
}
