import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static int PORT = 8080;
    private static HttpServer server;
    private static final Map<Integer, FoodItem> menu = new ConcurrentHashMap<>();
    private static final Map<Integer, Order> orders = new ConcurrentHashMap<>();
    private static final AtomicInteger foodIdCounter = new AtomicInteger(1);
    private static final AtomicInteger orderIdCounter = new AtomicInteger(1);

    public static void main(String[] args) throws IOException {
        // Check if a port was provided as command line argument
        if (args.length > 0) {
            try {
                PORT = Integer.parseInt(args[0]);
                System.out.println("Using specified port: " + PORT);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number. Finding available port...");
                PORT = findAvailablePort();
            }
        } else {
            PORT = findAvailablePort();
        }

        initializeSampleData();
        startServer();
        System.out.println("✅ Server started successfully on port " + PORT);
        System.out.println("🌐 Visit: http://localhost:" + PORT);
        System.out.println(" Menu: http://localhost:" + PORT + "/menu");
        System.out.println(" Orders: http://localhost:" + PORT + "/orders");
    }

    private static int findAvailablePort() {
        // Try ports from 8080 to 8090
        for (int port = 8080; port <= 8090; port++) {
            try (ServerSocket socket = new ServerSocket(port)) {
                socket.close();
                System.out.println("Found available port: " + port);
                return port;
            } catch (IOException e) {
                System.out.println("Port " + port + " is busy, trying next...");
            }
        }

        // If all ports are busy, use a random port
        try (ServerSocket socket = new ServerSocket(0)) {
            int randomPort = socket.getLocalPort();
            System.out.println("All common ports busy, using random port: " + randomPort);
            return randomPort;
        } catch (IOException e) {
            System.out.println("Failed to find any available port, using default 8080");
            return 8080;
        }
    }

    private static void initializeSampleData() {
        // Add some sample food items
        FoodItem pizza = new FoodItem("Pizza Margherita", "Classic cheese pizza with tomato sauce and mozzarella", 12.99, "Italian");
        pizza.id = foodIdCounter.getAndIncrement();
        menu.put(pizza.id, pizza);

        FoodItem burger = new FoodItem("Cheeseburger", "Beef patty with cheese, lettuce, tomato, and special sauce", 9.99, "American");
        burger.id = foodIdCounter.getAndIncrement();
        menu.put(burger.id, burger);

        FoodItem sushi = new FoodItem("Sushi Platter", "Assorted sushi pieces with salmon, tuna, and California rolls", 18.99, "Japanese");
        sushi.id = foodIdCounter.getAndIncrement();
        menu.put(sushi.id, sushi);

        FoodItem salad = new FoodItem("Caesar Salad", "Fresh romaine lettuce with grilled chicken, croutons, and parmesan", 8.99, "Salads");
        salad.id = foodIdCounter.getAndIncrement();
        menu.put(salad.id, salad);

        FoodItem pasta = new FoodItem("Spaghetti Carbonara", "Classic pasta with bacon, eggs, and parmesan cheese", 14.99, "Italian");
        pasta.id = foodIdCounter.getAndIncrement();
        menu.put(pasta.id, pasta);

        FoodItem iceCream = new FoodItem("Vanilla Ice Cream", "Homemade vanilla ice cream with chocolate sauce", 5.99, "Desserts");
        iceCream.id = foodIdCounter.getAndIncrement();
        menu.put(iceCream.id, iceCream);
    }

    private static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Menu endpoints
        server.createContext("/menu", new MenuHandler());
        server.createContext("/menu/add", new AddFoodHandler());
        server.createContext("/menu/update", new UpdateFoodHandler());
        server.createContext("/menu/delete", new DeleteFoodHandler());

        // Order endpoints
        server.createContext("/orders", new OrdersHandler());
        server.createContext("/orders/place", new PlaceOrderHandler());
        server.createContext("/orders/update", new UpdateOrderHandler());
        server.createContext("/orders/delete", new DeleteOrderHandler());

        // Root endpoint
        server.createContext("/", new HomeHandler());

        server.setExecutor(null);
        server.start();
    }

    // Data classes
    static class FoodItem {
        int id;
        String name;
        String description;
        double price;
        String category;
        boolean available;

        FoodItem(String name, String description, double price, String category) {
            this.name = name;
            this.description = description;
            this.price = price;
            this.category = category;
            this.available = true;
        }

        String toJson() {
            return "{" +
                    "\"id\":" + id + "," +
                    "\"name\":\"" + escapeJson(name) + "\"," +
                    "\"description\":\"" + escapeJson(description) + "\"," +
                    "\"price\":" + price + "," +
                    "\"category\":\"" + escapeJson(category) + "\"," +
                    "\"available\":" + available +
                    "}";
        }

        private String escapeJson(String str) {
            return str.replace("\"", "\\\"").replace("\\", "\\\\");
        }
    }

    static class OrderItem {
        int foodId;
        int quantity;
        String foodName;
        double price;

        OrderItem(int foodId, int quantity, String foodName, double price) {
            this.foodId = foodId;
            this.quantity = quantity;
            this.foodName = foodName;
            this.price = price;
        }

        String toJson() {
            return "{" +
                    "\"foodId\":" + foodId + "," +
                    "\"quantity\":" + quantity + "," +
                    "\"foodName\":\"" + escapeJson(foodName) + "\"," +
                    "\"price\":" + price + "," +
                    "\"total\":" + (price * quantity) +
                    "}";
        }

        private String escapeJson(String str) {
            return str.replace("\"", "\\\"").replace("\\", "\\\\");
        }
    }

    static class Order {
        int id;
        String customerName;
        String customerAddress;
        String customerPhone;
        List<OrderItem> items;
        String status;
        Date orderDate;
        double total;

        Order() {
            this.items = new ArrayList<>();
            this.status = "Pending";
            this.orderDate = new Date();
        }

        void calculateTotal() {
            total = 0;
            for (OrderItem item : items) {
                total += item.price * item.quantity;
            }
        }

        String toJson() {
            StringBuilder itemsArray = new StringBuilder("[");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) itemsArray.append(",");
                itemsArray.append(items.get(i).toJson());
            }
            itemsArray.append("]");

            return "{" +
                    "\"id\":" + id + "," +
                    "\"customerName\":\"" + escapeJson(customerName) + "\"," +
                    "\"customerAddress\":\"" + escapeJson(customerAddress) + "\"," +
                    "\"customerPhone\":\"" + escapeJson(customerPhone) + "\"," +
                    "\"status\":\"" + escapeJson(status) + "\"," +
                    "\"orderDate\":\"" + orderDate.toString() + "\"," +
                    "\"total\":" + total + "," +
                    "\"items\":" + itemsArray.toString() +
                    "}";
        }

        private String escapeJson(String str) {
            return str.replace("\"", "\\\"").replace("\\", "\\\\");
        }
    }

    // Handlers
    static class HomeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "<html><head><title>Online Food Ordering</title>" +
                    "<style>" +
                    "body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; }" +
                    ".container { max-width: 800px; margin: 0 auto; text-align: center; }" +
                    ".header { margin-bottom: 40px; }" +
                    ".buttons { display: flex; justify-content: center; gap: 20px; margin: 30px 0; }" +
                    ".btn { padding: 20px 40px; font-size: 18px; text-decoration: none; border-radius: 10px; transition: transform 0.2s; }" +
                    ".btn:hover { transform: translateY(-2px); }" +
                    ".btn-menu { background: #4CAF50; color: white; }" +
                    ".btn-orders { background: #2196F3; color: white; }" +
                    ".info { margin-top: 40px; padding: 20px; background: rgba(255,255,255,0.1); border-radius: 10px; }" +
                    "</style></head><body>" +
                    "<div class='container'>" +
                    "<div class='header'>" +
                    "<h1> Online Food Ordering System</h1>" +
                    "<p>Order delicious food from the comfort of your home!</p>" +
                    "</div>" +
                    "<div class='buttons'>" +
                    "<a href='/menu' class='btn btn-menu'> View Menu</a>" +
                    "<a href='/orders' class='btn btn-orders'> View Orders</a>" +
                    "</div>" +
                    "<div class='info'>" +
                    "<p>Server running on port: <strong>" + PORT + "</strong></p>" +
                    "<p>Total menu items: <strong>" + menu.size() + "</strong></p>" +
                    "<p>Total orders: <strong>" + orders.size() + "</strong></p>" +
                    "</div>" +
                    "</div></body></html>";

            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    static class MenuHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String response = "<html><head><title>Menu</title><style>" +
                        "body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }" +
                        ".container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }" +
                        "table { width: 100%; border-collapse: collapse; margin: 20px 0; }" +
                        "th, td { border: 1px solid #ddd; padding: 15px; text-align: left; }" +
                        "th { background-color: #4CAF50; color: white; }" +
                        "tr:nth-child(even) { background-color: #f9f9f9; }" +
                        "tr:hover { background-color: #f0f0f0; }" +
                        ".form-container { background-color: #f8f9fa; padding: 20px; border-radius: 8px; margin: 20px 0; }" +
                        "input[type='text'], input[type='number'] { width: 100%; padding: 10px; margin: 8px 0; border: 1px solid #ddd; border-radius: 4px; }" +
                        "input[type='submit'] { background-color: #4CAF50; color: white; padding: 12px 24px; border: none; border-radius: 4px; cursor: pointer; }" +
                        ".available-yes { color: green; font-weight: bold; }" +
                        ".available-no { color: red; font-weight: bold; }" +
                        ".back-link { display: inline-block; margin-bottom: 20px; padding: 10px 20px; background: #6c757d; color: white; text-decoration: none; border-radius: 4px; }" +
                        "</style></head><body>" +
                        "<div class='container'>" +
                        "<a href='/' class='back-link'> Back to Home</a>" +
                        "<h1> Menu</h1>" +
                        "<table>" +
                        "<tr><th>ID</th><th>Name</th><th>Description</th><th>Price</th><th>Category</th><th>Available</th><th>Actions</th></tr>";

                for (FoodItem item : menu.values()) {
                    String availableClass = item.available ? "available-yes" : "available-no";
                    String availableText = item.available ? "Yes" : "No";
                    String toggleText = item.available ? "Disable" : "Enable";

                    response += String.format("<tr><td>%d</td><td><strong>%s</strong></td><td>%s</td><td>$%.2f</td><td>%s</td>" +
                                    "<td class='%s'>%s</td>" +
                                    "<td><a href='/menu/delete?id=%d' style='color: red; text-decoration: none; padding: 5px 10px; background: #ffebee; border-radius: 3px;'> Delete</a> " +
                                    "<a href='/menu/update?id=%d&available=%b' style='color: #1976d2; text-decoration: none; padding: 5px 10px; background: #e3f2fd; border-radius: 3px; margin-left: 5px;'>%s</a></td></tr>",
                            item.id, item.name, item.description, item.price, item.category,
                            availableClass, availableText, item.id, item.id, !item.available, toggleText);
                }

                response += "</table>" +
                        "<div class='form-container'>" +
                        "<h2> Add New Food Item</h2>" +
                        "<form action='/menu/add' method='POST'>" +
                        "<div style='display: grid; grid-template-columns: 1fr 1fr; gap: 20px;'>" +
                        "<div><label>Name: </label><input type='text' name='name' required></div>" +
                        "<div><label>Price: $</label><input type='number' step='0.01' name='price' required></div>" +
                        "<div><label>Description: </label><input type='text' name='description' required></div>" +
                        "<div><label>Category: </label><input type='text' name='category' required></div>" +
                        "</div>" +
                        "<input type='submit' value='Add Item' style='margin-top: 20px;'>" +
                        "</form>" +
                        "</div>" +
                        "</div></body></html>";

                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }

    static class AddFoodHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(requestBody);

                String name = params.get("name");
                String description = params.get("description");
                double price = Double.parseDouble(params.get("price"));
                String category = params.get("category");

                int id = foodIdCounter.getAndIncrement();
                FoodItem newItem = new FoodItem(name, description, price, category);
                newItem.id = id;
                menu.put(id, newItem);

                // Redirect back to menu
                exchange.getResponseHeaders().set("Location", "/menu");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            }
        }
    }

    static class UpdateFoodHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = parseQuery(query);
                int id = Integer.parseInt(params.get("id"));
                boolean available = Boolean.parseBoolean(params.get("available"));

                FoodItem item = menu.get(id);
                if (item != null) {
                    item.available = available;
                }

                // Redirect back to menu
                exchange.getResponseHeaders().set("Location", "/menu");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            }
        }
    }

    static class DeleteFoodHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = parseQuery(query);
                int id = Integer.parseInt(params.get("id"));

                menu.remove(id);

                // Redirect back to menu
                exchange.getResponseHeaders().set("Location", "/menu");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            }
        }
    }

    static class OrdersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String response = "<html><head><title>Orders</title><style>" +
                        "body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }" +
                        ".container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }" +
                        "table { width: 100%; border-collapse: collapse; margin: 20px 0; }" +
                        "th, td { border: 1px solid #ddd; padding: 15px; text-align: left; }" +
                        "th { background-color: #2196F3; color: white; }" +
                        ".status-pending { color: #ff9800; font-weight: bold; }" +
                        ".status-completed { color: #4CAF50; font-weight: bold; }" +
                        ".status-cancelled { color: #f44336; font-weight: bold; }" +
                        ".form-container { background-color: #f8f9fa; padding: 20px; border-radius: 8px; margin: 20px 0; }" +
                        "input[type='text'], input[type='number'] { padding: 10px; margin: 8px 0; border: 1px solid #ddd; border-radius: 4px; }" +
                        "input[type='submit'] { background-color: #4CAF50; color: white; padding: 12px 24px; border: none; border-radius: 4px; cursor: pointer; }" +
                        ".food-item { border: 1px solid #ddd; padding: 15px; margin: 10px 0; border-radius: 8px; background: #f9f9f9; }" +
                        ".back-link { display: inline-block; margin-bottom: 20px; padding: 10px 20px; background: #6c757d; color: white; text-decoration: none; border-radius: 4px; }" +
                        "</style></head><body>" +
                        "<div class='container'>" +
                        "<a href='/' class='back-link'> Back to Home</a>" +
                        "<h1> Orders</h1>";

                if (orders.isEmpty()) {
                    response += "<p style='text-align: center; color: #666; font-size: 18px;'>No orders yet. Be the first to order!</p>";
                } else {
                    response += "<table>" +
                            "<tr><th>ID</th><th>Customer</th><th>Total</th><th>Status</th><th>Date</th><th>Actions</th></tr>";

                    for (Order order : orders.values()) {
                        String statusClass = "status-" + order.status.toLowerCase();
                        response += String.format("<tr><td>%d</td><td>%s</td><td>$%.2f</td>" +
                                        "<td class='%s'>%s</td><td>%s</td>" +
                                        "<td><a href='/orders/delete?id=%d' style='color: red; text-decoration: none; padding: 5px 10px; background: #ffebee; border-radius: 3px;'> Delete</a> " +
                                        "<a href='/orders/update?id=%d&status=Completed' style='color: green; text-decoration: none; padding: 5px 10px; background: #e8f5e8; border-radius: 3px; margin-left: 5px;'> Complete</a> " +
                                        "<a href='/orders/update?id=%d&status=Cancelled' style='color: red; text-decoration: none; padding: 5px 10px; background: #ffebee; border-radius: 3px; margin-left: 5px;'> Cancel</a></td></tr>",
                                order.id, order.customerName, order.total, statusClass, order.status,
                                order.orderDate.toString().substring(0, 16), order.id, order.id, order.id);
                    }
                    response += "</table>";
                }

                response += "<div class='form-container'>" +
                        "<h2> Place New Order</h2>" +
                        "<form action='/orders/place' method='POST'>" +
                        "<div style='display: grid; grid-template-columns: 1fr 1fr; gap: 20px;'>" +
                        "<div><label>Name: </label><input type='text' name='customerName' required style='width: 100%;'></div>" +
                        "<div><label>Phone: </label><input type='text' name='customerPhone' required style='width: 100%;'></div>" +
                        "<div><label>Address: </label><input type='text' name='customerAddress' required style='width: 100%;'></div>" +
                        "</div>" +
                        "<h3> Menu Items:</h3>";

                for (FoodItem item : menu.values()) {
                    if (item.available) {
                        response += String.format("<div class='food-item'>" +
                                        "<input type='checkbox' name='foodId' value='%d'> <strong>%s</strong> - $%.2f<br>" +
                                        "<label>Quantity: </label><input type='number' name='quantity_%d' value='1' min='1' style='width: 80px;'>" +
                                        "</div>",
                                item.id, item.name, item.price, item.id);
                    }
                }

                response += "<input type='submit' value='Place Order' style='margin-top: 20px;'>" +
                        "</form>" +
                        "</div>" +
                        "</div></body></html>";

                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }

    static class PlaceOrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(requestBody);

                Order newOrder = new Order();
                newOrder.id = orderIdCounter.getAndIncrement();
                newOrder.customerName = params.get("customerName");
                newOrder.customerAddress = params.get("customerAddress");
                newOrder.customerPhone = params.get("customerPhone");

                // Process food items
                String foodIdsStr = params.get("foodId");
                if (foodIdsStr != null && !foodIdsStr.isEmpty()) {
                    String[] foodIds = foodIdsStr.split(",");
                    for (String foodIdStr : foodIds) {
                        int foodId = Integer.parseInt(foodIdStr.trim());
                        FoodItem foodItem = menu.get(foodId);
                        if (foodItem != null && foodItem.available) {
                            String quantityKey = "quantity_" + foodId;
                            int quantity = Integer.parseInt(params.get(quantityKey));
                            newOrder.items.add(new OrderItem(foodId, quantity, foodItem.name, foodItem.price));
                        }
                    }
                }

                if (newOrder.items.isEmpty()) {
                    // No items selected, redirect back
                    exchange.getResponseHeaders().set("Location", "/orders");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                    return;
                }

                newOrder.calculateTotal();
                orders.put(newOrder.id, newOrder);

                // Redirect back to orders
                exchange.getResponseHeaders().set("Location", "/orders");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            }
        }
    }

    static class UpdateOrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = parseQuery(query);
                int id = Integer.parseInt(params.get("id"));
                String newStatus = params.get("status");

                Order order = orders.get(id);
                if (order != null) {
                    order.status = newStatus;
                }

                // Redirect back to orders
                exchange.getResponseHeaders().set("Location", "/orders");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            }
        }
    }

    static class DeleteOrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = parseQuery(query);
                int id = Integer.parseInt(params.get("id"));

                orders.remove(id);

                // Redirect back to orders
                exchange.getResponseHeaders().set("Location", "/orders");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            }
        }
    }

    // Utility methods
    private static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    try {
                        result.put(keyValue[0], URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name()));
                    } catch (UnsupportedEncodingException e) {
                        result.put(keyValue[0], keyValue[1]);
                    }
                }
            }
        }
        return result;
    }

    private static Map<String, String> parseFormData(String formData) throws UnsupportedEncodingException {
        Map<String, String> map = new HashMap<>();
        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
            String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name()) : "";

            // Handle multiple values for the same key (like checkboxes)
            if (map.containsKey(key)) {
                String existingValue = map.get(key);
                map.put(key, existingValue + "," + value);
            } else {
                map.put(key, value);
            }
        }
        return map;
    }
}