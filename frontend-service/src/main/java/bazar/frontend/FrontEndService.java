package bazar.frontend;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class FrontEndService {
   // ===================== Replicas =====================
private static final String[] CATALOG_REPLICAS = {
        "http://localhost:5000",
        "http://localhost:5001"
};

private static final String[] ORDER_REPLICAS = {
        "http://localhost:7000",
        "http://localhost:7001"
};

private static int catalogRR = 0;
private static int orderRR = 0;

private static synchronized String nextCatalogReplica() {
    String base = CATALOG_REPLICAS[catalogRR];
    catalogRR = (catalogRR + 1) % CATALOG_REPLICAS.length;
    return base;
}

private static synchronized String nextOrderReplica() {
    String base = ORDER_REPLICAS[orderRR];
    orderRR = (orderRR + 1) % ORDER_REPLICAS.length;
    return base;
}

// ===================== Cache (LRU) =====================
private static final int CACHE_CAPACITY = 50;

// Cache key = book id, Cache value = JSON string returned by /info/{id}
private static final Map<String, String> infoCache =
    new LinkedHashMap<String, String>(CACHE_CAPACITY, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > CACHE_CAPACITY;
        }
    };



    public static void main(String[] args) throws Exception {

        HttpServer server = HttpServer.create(new InetSocketAddress(9000), 0);

        server.createContext("/search", FrontEndService::handleSearch);
        server.createContext("/info", FrontEndService::handleInfo);
        server.createContext("/purchase", FrontEndService::handlePurchase);
        server.createContext("/invalidate", FrontEndService::handleInvalidate);


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
        String url = nextCatalogReplica() + "/search/" + topic;


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
        // 1) Check cache first (read-only caching)
        String cached;
        synchronized (infoCache) {
           cached = infoCache.get(id);
        }

        if (cached != null) {
         // cache HIT
         sendJSON(ex, 200, cached);
         return;
        }

       // 2) Cache MISS => forward to a catalog replica (round-robin)
       String url = nextCatalogReplica() + "/info/" + id;
       String response = httpGET(url);

       // 3) Put in cache
       synchronized (infoCache) {
          infoCache.put(id, response);
        }

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
        String url = nextOrderReplica() + "/order?id=" + id;


        String response = httpGET(url);

        sendJSON(ex, 200, response);
    }

    
private static void handleInvalidate(HttpExchange ex) throws IOException {

    if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
        sendText(ex, 405, "Only POST allowed");
        return;
    }

    String path = ex.getRequestURI().getPath();
    String[] parts = path.split("/", 3); // /invalidate/{id}

    if (parts.length < 3) {
        sendText(ex, 400, "Missing id: /invalidate/{id}");
        return;
    }

    String id = parts[2];

    synchronized (infoCache) {
        infoCache.remove(id);
    }

    sendJSON(ex, 200, "{\"status\":\"invalidated\",\"id\":\"" + id + "\"}");
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
