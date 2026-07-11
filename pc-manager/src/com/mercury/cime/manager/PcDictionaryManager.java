package com.mercury.cime.manager;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class PcDictionaryManager {
    private static final int LOCAL_PORT = 37620;
    private static final int DISCOVERY_PORT = 37622;
    private static final byte[] DISCOVERY = "CIME_DISCOVER_V1".getBytes(StandardCharsets.UTF_8);
    private static final int MAX_IMPORT = 10 * 1024 * 1024;

    private final AtomicLong sequence = new AtomicLong();
    private volatile String phoneBase;
    private volatile String token;

    public static void main(String[] args) throws Exception {
        boolean openBrowser = args.length == 0 || !"--no-browser".equals(args[0]);
        new PcDictionaryManager().start(openBrowser);
    }

    private void start(boolean openBrowser) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", LOCAL_PORT), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        URI page = URI.create("http://127.0.0.1:" + LOCAL_PORT + "/");
        System.out.println("ChinesePinyinIME 电脑词库管理已启动：" + page);
        if (openBrowser && Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(page);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/api/") && !isTrustedLocalRequest(exchange)) {
                sendError(exchange, 403, "untrusted_browser_origin");
                return;
            }
            if ("/".equals(path)) {
                send(exchange, 200, "text/html; charset=utf-8",
                        resource("/web/index.html").getBytes(StandardCharsets.UTF_8));
                return;
            }
            if ("/api/discover".equals(path)) {
                sendJson(exchange, 200, discover());
                return;
            }
            if ("/api/select".equals(path)) {
                Map<String, String> query = query(exchange);
                String host = query.get("host");
                String port = query.get("port");
                if (!isLanAddress(host) || port == null || !port.matches("\\d{1,5}")) {
                    sendError(exchange, 400, "invalid_device");
                    return;
                }
                phoneBase = "http://" + host + ":" + port;
                token = null;
                sequence.set(0);
                sendJson(exchange, 200, proxy("GET", "/v1/info", null, false).body);
                return;
            }
            if ("/api/connect".equals(path)) {
                requirePhone();
                String computerName = InetAddress.getLocalHost().getHostName();
                PhoneResponse response = proxy(
                        "POST", "/v1/connect", computerName.getBytes(StandardCharsets.UTF_8), false);
                sendJson(exchange, response.status, response.body);
                return;
            }
            if ("/api/request".equals(path)) {
                requirePhone();
                PhoneResponse response = proxy(
                        "GET", "/v1/request?id=" + encode(query(exchange).get("id")), null, false);
                String body = response.body;
                String approvedToken = jsonField(body, "token");
                if (approvedToken != null) {
                    token = approvedToken;
                    sequence.set(0);
                    body = body.replace(",\"token\":\"" + approvedToken + "\"", "");
                }
                sendJson(exchange, response.status, body);
                return;
            }
            if ("/api/status".equals(path)) {
                forwardJson(exchange, "GET", "/v1/status", null);
                return;
            }
            if ("/api/import/preview".equals(path)) {
                byte[] body = readBody(exchange, MAX_IMPORT);
                sendJson(exchange, 200, preview(body));
                return;
            }
            if ("/api/import".equals(path)) {
                byte[] body = readBody(exchange, MAX_IMPORT);
                forwardJson(exchange, "POST", "/v1/import", body);
                return;
            }
            if ("/api/export".equals(path)) {
                String scope = "manual".equals(query(exchange).get("scope")) ? "manual" : "combined";
                PhoneResponse response = authorizedProxy("GET", "/v1/export?scope=" + scope, null);
                Headers headers = exchange.getResponseHeaders();
                headers.set("Content-Disposition", "attachment; filename=ChinesePinyinIME_" + scope + ".tsv");
                send(exchange, response.status, "text/tab-separated-values; charset=utf-8",
                        response.bytes);
                return;
            }
            if ("/api/clear".equals(path)) {
                String kind = query(exchange).get("kind");
                if (!"manual".equals(kind) && !"learned".equals(kind)) {
                    sendError(exchange, 400, "invalid_clear_kind");
                    return;
                }
                forwardJson(exchange, "POST", "/v1/clear?kind=" + kind, new byte[0]);
                return;
            }
            sendError(exchange, 404, "not_found");
        } catch (IllegalStateException exception) {
            sendError(exchange, 409, exception.getMessage());
        } catch (Exception exception) {
            sendError(exchange, 502, "phone_unreachable");
        } finally {
            exchange.close();
        }
    }

    private String discover() throws IOException {
        Map<String, Device> devices = new LinkedHashMap<>();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(450);
            for (InetAddress address : broadcastAddresses()) {
                socket.send(new DatagramPacket(DISCOVERY, DISCOVERY.length, address, DISCOVERY_PORT));
            }
            long deadline = System.currentTimeMillis() + 1300;
            byte[] buffer = new byte[1024];
            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String message = new String(
                            packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                    String[] fields = message.split("\\t", -1);
                    if (fields.length == 6 && "CIME_DEVICE_V1".equals(fields[0])) {
                        devices.put(fields[1], new Device(
                                fields[1], fields[2], fields[3], packet.getAddress().getHostAddress(),
                                fields[4], fields[5]));
                    }
                } catch (java.net.SocketTimeoutException ignored) {
                    // Keep receiving until the complete discovery window ends.
                }
            }
        }
        StringBuilder json = new StringBuilder("{\"devices\":[");
        boolean first = true;
        for (Device device : devices.values()) {
            if (!first) json.append(',');
            first = false;
            json.append(device.toJson());
        }
        return json.append("]}").toString();
    }

    private List<InetAddress> broadcastAddresses() throws IOException {
        List<InetAddress> addresses = new ArrayList<>();
        addresses.add(InetAddress.getByName("255.255.255.255"));
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface network = interfaces.nextElement();
            if (!network.isUp() || network.isLoopback()) continue;
            network.getInterfaceAddresses().forEach(address -> {
                if (address.getBroadcast() != null) addresses.add(address.getBroadcast());
            });
        }
        return addresses;
    }

    private String preview(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        int valid = 0;
        int rejected = 0;
        int duplicate = 0;
        Map<String, Boolean> seen = new LinkedHashMap<>();
        StringBuilder rows = new StringBuilder("[");
        int shown = 0;
        for (String line : text.split("\\R", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            String[] fields = line.split("\\t", -1);
            boolean rowValid = fields.length == 3
                    && fields[0].trim().matches("[A-Za-z]{1,64}")
                    && !fields[1].trim().isEmpty()
                    && validWeight(fields[2]);
            if (rowValid) {
                String key = fields[0].trim().toLowerCase() + "\u0000" + fields[1].trim();
                if (seen.put(key, true) != null) duplicate++;
                else valid++;
            } else rejected++;
            if (shown++ < 20) {
                if (rows.length() > 1) rows.append(',');
                rows.append("{\"line\":\"").append(json(line)).append("\",\"valid\":")
                        .append(rowValid).append('}');
            }
        }
        return "{\"valid\":" + valid + ",\"duplicates\":" + duplicate
                + ",\"rejected\":" + rejected + ",\"rows\":" + rows + "]}";
    }

    private boolean validWeight(String raw) {
        try {
            int weight = Integer.parseInt(raw.trim());
            return weight > 0 && weight <= 1_000_000;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void forwardJson(HttpExchange exchange, String method, String path, byte[] body)
            throws IOException {
        PhoneResponse response = authorizedProxy(method, path, body);
        sendJson(exchange, response.status, response.body);
    }

    private PhoneResponse authorizedProxy(String method, String path, byte[] body) throws IOException {
        requirePhone();
        if (token == null) throw new IllegalStateException("not_connected");
        return proxy(method, path, body, true);
    }

    private PhoneResponse proxy(String method, String path, byte[] body, boolean authorize)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(phoneBase + path).openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(65_000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Connection", "close");
        if (authorize) {
            connection.setRequestProperty("X-CIME-Token", token);
            connection.setRequestProperty("X-CIME-Sequence", Long.toString(sequence.incrementAndGet()));
        }
        if (body != null) {
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(body.length);
            connection.getOutputStream().write(body);
        }
        int status = connection.getResponseCode();
        InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        byte[] bytes = input == null ? new byte[0] : input.readAllBytes();
        return new PhoneResponse(status, bytes);
    }

    private void requirePhone() {
        if (phoneBase == null) throw new IllegalStateException("no_device_selected");
    }

    private static byte[] readBody(HttpExchange exchange, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = exchange.getRequestBody().read(buffer)) >= 0) {
            total += read;
            if (total > limit) throw new IOException("Import too large");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> result = new LinkedHashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) return result;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(decode(parts[0]), parts.length == 2 ? decode(parts[1]) : "");
        }
        return result;
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = PcDictionaryManager.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing resource " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendError(HttpExchange exchange, int status, String code) throws IOException {
        sendJson(exchange, status, "{\"error\":\"" + json(code) + "\"}");
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static boolean isLanAddress(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isSiteLocalAddress() || address.isLinkLocalAddress();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isTrustedLocalRequest(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (!("127.0.0.1:" + LOCAL_PORT).equals(host)
                && !("localhost:" + LOCAL_PORT).equalsIgnoreCase(host)) {
            return false;
        }
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        return origin == null
                || ("http://127.0.0.1:" + LOCAL_PORT).equals(origin)
                || ("http://localhost:" + LOCAL_PORT).equalsIgnoreCase(origin);
    }

    private static String jsonField(String body, String field) {
        String marker = "\"" + field + "\":\"";
        int start = body.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = body.indexOf('"', start);
        return end < 0 ? null : body.substring(start, end);
    }

    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static final class PhoneResponse {
        private final int status;
        private final byte[] bytes;
        private final String body;

        private PhoneResponse(int status, byte[] bytes) {
            this.status = status;
            this.bytes = bytes;
            this.body = new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static final class Device {
        private final String id, name, model, host, port, version;

        private Device(String id, String name, String model, String host, String port, String version) {
            this.id = id; this.name = name; this.model = model; this.host = host;
            this.port = port; this.version = version;
        }

        private String toJson() {
            return "{\"id\":\"" + json(id) + "\",\"name\":\"" + json(name)
                    + "\",\"model\":\"" + json(model) + "\",\"host\":\"" + json(host)
                    + "\",\"port\":\"" + json(port) + "\",\"version\":\"" + json(version) + "\"}";
        }
    }
}
