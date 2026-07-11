package com.mercury.chinesepinyinime;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ComputerDictionaryService extends Service {
    public static final String ACTION_STATE_CHANGED =
            "com.mercury.chinesepinyinime.COMPUTER_MANAGER_STATE_CHANGED";
    public static final String ACTION_START = "computer_manager.start";
    public static final String ACTION_STOP = "computer_manager.stop";

    private static final String ACTION_APPROVE_CONNECT = "computer_manager.approve_connect";
    private static final String ACTION_REJECT_CONNECT = "computer_manager.reject_connect";
    private static final String ACTION_CONFIRM_CLEAR = "computer_manager.confirm_clear";
    private static final String ACTION_REJECT_CLEAR = "computer_manager.reject_clear";
    private static final String EXTRA_REQUEST_ID = "request_id";
    private static final String EXTRA_CLEAR_KIND = "clear_kind";
    private static final String CLEAR_MANUAL = "manual";
    private static final String CLEAR_LEARNED = "learned";
    private static final String CHANNEL_ID = "computer_dictionary_manager";
    private static final int FOREGROUND_NOTIFICATION_ID = 2100;
    private static final int REQUEST_NOTIFICATION_ID = 2101;
    private static final int TCP_PORT = 37621;
    private static final int UDP_PORT = 37622;
    private static final int MAX_BODY_BYTES = 10 * 1024 * 1024;
    private static final String DISCOVERY_REQUEST = "CIME_DISCOVER_V1";

    private static volatile boolean running;
    private static volatile boolean connected;

    private final ExecutorService clients = Executors.newCachedThreadPool();
    private final ComputerManagerSession session = new ComputerManagerSession();
    private final Object requestLock = new Object();
    private final Map<String, RequestState> requests = new HashMap<>();
    private volatile boolean stopping;
    private ServerSocket serverSocket;
    private DatagramSocket discoverySocket;
    private Thread tcpThread;
    private Thread discoveryThread;
    private String connectedComputer = "";

    public static boolean isRunning() {
        return running;
    }

    public static boolean isConnected() {
        return connected;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelfSafely();
            return START_NOT_STICKY;
        }
        if (ACTION_APPROVE_CONNECT.equals(action)) {
            approveConnection(intent.getStringExtra(EXTRA_REQUEST_ID));
            return START_NOT_STICKY;
        }
        if (ACTION_REJECT_CONNECT.equals(action)) {
            finishRequest(intent.getStringExtra(EXTRA_REQUEST_ID), "rejected", null);
            getSystemService(NotificationManager.class).cancel(REQUEST_NOTIFICATION_ID);
            return START_NOT_STICKY;
        }
        if (ACTION_CONFIRM_CLEAR.equals(action)) {
            confirmClear(
                    intent.getStringExtra(EXTRA_REQUEST_ID),
                    intent.getStringExtra(EXTRA_CLEAR_KIND));
            return START_NOT_STICKY;
        }
        if (ACTION_REJECT_CLEAR.equals(action)) {
            finishRequest(intent.getStringExtra(EXTRA_REQUEST_ID), "rejected", null);
            getSystemService(NotificationManager.class).cancel(REQUEST_NOTIFICATION_ID);
            return START_NOT_STICKY;
        }
        if (!running) {
            startForeground(FOREGROUND_NOTIFICATION_ID, buildServiceNotification());
            startServers();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        shutdownServers();
        clients.shutdownNow();
        super.onDestroy();
    }

    private void startServers() {
        stopping = false;
        running = true;
        connected = false;
        broadcastState();
        tcpThread = new Thread(this::runTcpServer, "dictionary-manager-tcp");
        discoveryThread = new Thread(this::runDiscoveryServer, "dictionary-manager-discovery");
        tcpThread.start();
        discoveryThread.start();
    }

    private void runTcpServer() {
        try (ServerSocket socket = new ServerSocket(TCP_PORT)) {
            serverSocket = socket;
            while (!stopping) {
                Socket client = socket.accept();
                client.setSoTimeout(15_000);
                clients.execute(() -> handleClient(client));
            }
        } catch (IOException ignored) {
            if (!stopping) {
                stopSelfSafely();
            }
        }
    }

    private void runDiscoveryServer() {
        byte[] buffer = new byte[512];
        try (DatagramSocket socket = new DatagramSocket(UDP_PORT)) {
            discoverySocket = socket;
            while (!stopping) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(
                        packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                if (!DISCOVERY_REQUEST.equals(message.trim())) {
                    continue;
                }
                byte[] reply = discoveryPayload().getBytes(StandardCharsets.UTF_8);
                socket.send(new DatagramPacket(
                        reply, reply.length, packet.getAddress(), packet.getPort()));
            }
        } catch (IOException ignored) {
            // Closing the socket is the normal service-stop path.
        }
    }

    private String discoveryPayload() {
        return "CIME_DEVICE_V1\t" + deviceId() + "\t" + safeField(deviceName())
                + "\t" + safeField(Build.MANUFACTURER + " " + Build.MODEL)
                + "\t" + TCP_PORT + "\tv0.02.0002";
    }

    private void handleClient(Socket socket) {
        try (Socket ignored = socket;
             BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
             BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream())) {
            HttpRequest request = HttpRequest.read(input);
            if (request == null) {
                return;
            }
            HttpResponse response = route(request);
            response.write(output);
        } catch (IOException | RuntimeException ignored) {
            // A disconnected browser/helper must not stop the foreground service.
        }
    }

    private HttpResponse route(HttpRequest request) {
        if ("GET".equals(request.method) && "/v1/info".equals(request.path)) {
            return HttpResponse.json(200, "{\"deviceId\":\"" + json(deviceId())
                    + "\",\"name\":\"" + json(deviceName()) + "\",\"model\":\""
                    + json(Build.MANUFACTURER + " " + Build.MODEL)
                    + "\",\"version\":\"v0.02.0002\"}");
        }
        if ("POST".equals(request.method) && "/v1/connect".equals(request.path)) {
            String computer = new String(request.body, StandardCharsets.UTF_8).trim();
            if (computer.isEmpty() || computer.length() > 100) {
                return HttpResponse.error(400, "invalid_computer_name");
            }
            String id = createRequest("connect", computer);
            showConnectionRequest(id, computer);
            return HttpResponse.json(202, "{\"requestId\":\"" + id + "\"}");
        }
        if ("GET".equals(request.method) && "/v1/request".equals(request.path)) {
            RequestState state = getRequest(request.query.get("id"));
            return state == null
                    ? HttpResponse.error(404, "request_not_found")
                    : HttpResponse.json(200, state.toJson());
        }
        if (!authorize(request)) {
            return HttpResponse.error(401, "invalid_session_or_sequence");
        }
        if ("GET".equals(request.method) && "/v1/status".equals(request.path)) {
            return statusResponse();
        }
        if ("GET".equals(request.method) && "/v1/export".equals(request.path)) {
            return exportDictionary("manual".equals(request.query.get("scope")));
        }
        if ("POST".equals(request.method) && "/v1/import".equals(request.path)) {
            return importDictionary(request.body);
        }
        if ("POST".equals(request.method) && "/v1/clear".equals(request.path)) {
            String kind = request.query.get("kind");
            if (!CLEAR_MANUAL.equals(kind) && !CLEAR_LEARNED.equals(kind)) {
                return HttpResponse.error(400, "invalid_clear_kind");
            }
            String id = createRequest("clear", kind);
            showClearRequest(id, kind);
            return HttpResponse.json(202, "{\"requestId\":\"" + id + "\"}");
        }
        if ("POST".equals(request.method) && "/v1/disconnect".equals(request.path)) {
            closeSession();
            return HttpResponse.json(200, "{\"ok\":true}");
        }
        return HttpResponse.error(404, "not_found");
    }

    private boolean authorize(HttpRequest request) {
        String sequenceText = request.headers.get("x-cime-sequence");
        try {
            return session.authorize(
                    request.headers.get("x-cime-token"), Long.parseLong(sequenceText));
        } catch (NumberFormatException | NullPointerException ignored) {
            return false;
        }
    }

    private HttpResponse statusResponse() {
        int manual = ManualDictionaryStore.getInstance().getEntryCount(this);
        int learned = UserDictionaryStore.getInstance().getLearnedPhraseCount(this);
        int selections = UserFrequencyStore.getInstance().getTotalSelectionCount(this);
        return HttpResponse.json(200, "{\"manualEntries\":" + manual
                + ",\"learnedEntries\":" + learned
                + ",\"selectionCount\":" + selections + "}");
    }

    private HttpResponse importDictionary(byte[] body) {
        if (body.length == 0 || body.length > MAX_BODY_BYTES) {
            return HttpResponse.error(400, "invalid_file_size");
        }
        try {
            DictionaryTsvCodec.ParseResult result = DictionaryTsvCodec.parse(
                    new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8));
            if (result.getValidEntries() == 0) {
                return HttpResponse.error(400, "no_valid_entries");
            }
            if (!ManualDictionaryStore.getInstance().replaceAll(this, result.getEntries())) {
                return HttpResponse.error(500, "replace_failed");
            }
            if (!reloadDictionary()) {
                return HttpResponse.error(500, "reload_timeout");
            }
            return HttpResponse.json(200, "{\"valid\":" + result.getValidEntries()
                    + ",\"duplicates\":" + result.getDuplicateRows()
                    + ",\"rejected\":" + result.getRejectedRows() + "}");
        } catch (IOException | RuntimeException ignored) {
            return HttpResponse.error(400, "invalid_dictionary");
        }
    }

    private HttpResponse exportDictionary(boolean manualOnly) {
        ManualDictionaryStore manualStore = ManualDictionaryStore.getInstance();
        manualStore.load(this);
        UserDictionaryStore learnedStore = UserDictionaryStore.getInstance();
        learnedStore.load(this);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(bytes, StandardCharsets.UTF_8));
            DictionaryTsvCodec.writeCombined(
                    writer,
                    manualStore.snapshotWeightedEntries(),
                    manualOnly ? Collections.emptyMap() : learnedStore.snapshotWeightedEntries());
            return HttpResponse.tsv(200, bytes.toByteArray());
        } catch (IOException ignored) {
            return HttpResponse.error(500, "export_failed");
        }
    }

    private boolean reloadDictionary() {
        CountDownLatch latch = new CountDownLatch(1);
        PinyinDictionary.getInstance().reloadUserDictionariesAsync(this, latch::countDown);
        try {
            return latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String createRequest(String type, String value) {
        String id = UUID.randomUUID().toString();
        synchronized (requestLock) {
            requests.put(id, new RequestState(type, value));
        }
        return id;
    }

    private RequestState getRequest(String id) {
        synchronized (requestLock) {
            return requests.get(id);
        }
    }

    private void finishRequest(String id, String status, String token) {
        synchronized (requestLock) {
            RequestState state = requests.get(id);
            if (state != null) {
                state.status = status;
                state.token = token;
            }
        }
    }

    private void approveConnection(String id) {
        RequestState request = getRequest(id);
        if (request == null || !"connect".equals(request.type) || !"pending".equals(request.status)) {
            return;
        }
        closeSession();
        connectedComputer = request.value;
        String token = session.open();
        connected = true;
        finishRequest(id, "approved", token);
        getSystemService(NotificationManager.class).cancel(REQUEST_NOTIFICATION_ID);
        refreshServiceNotification();
        broadcastState();
    }

    private void confirmClear(String id, String kind) {
        RequestState request = getRequest(id);
        if (request == null || !"clear".equals(request.type)
                || !kind.equals(request.value) || !"pending".equals(request.status)) {
            return;
        }
        clients.execute(() -> {
            boolean success;
            if (CLEAR_MANUAL.equals(kind)) {
                success = ManualDictionaryStore.getInstance().clear(this);
            } else {
                UserFrequencyStore.getInstance().clear(this);
                UserDictionaryStore.getInstance().clear(this);
                success = true;
            }
            if (success) {
                success = reloadDictionary();
            }
            finishRequest(id, success ? "completed" : "failed", null);
            getSystemService(NotificationManager.class).cancel(REQUEST_NOTIFICATION_ID);
            broadcastState();
        });
    }

    private void showConnectionRequest(String id, String computer) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.computer_manager_connection_request, computer))
                .setContentText(getString(R.string.computer_manager_hint))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(false)
                .addAction(0, getString(R.string.computer_manager_approve),
                        serviceAction(ACTION_APPROVE_CONNECT, id, null, 10))
                .addAction(0, getString(R.string.computer_manager_reject),
                        serviceAction(ACTION_REJECT_CONNECT, id, null, 11))
                .build();
        getSystemService(NotificationManager.class).notify(REQUEST_NOTIFICATION_ID, notification);
    }

    private void showClearRequest(String id, String kind) {
        int title = CLEAR_MANUAL.equals(kind)
                ? R.string.computer_manager_clear_manual_request
                : R.string.computer_manager_clear_learned_request;
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(title))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(false)
                .addAction(0, getString(R.string.computer_manager_confirm),
                        serviceAction(ACTION_CONFIRM_CLEAR, id, kind, 20))
                .addAction(0, getString(R.string.computer_manager_reject),
                        serviceAction(ACTION_REJECT_CLEAR, id, kind, 21))
                .build();
        getSystemService(NotificationManager.class).notify(REQUEST_NOTIFICATION_ID, notification);
    }

    private PendingIntent serviceAction(
            String action, String requestId, String clearKind, int requestCode) {
        Intent intent = new Intent(this, ComputerDictionaryService.class).setAction(action);
        if (requestId != null) {
            intent.putExtra(EXTRA_REQUEST_ID, requestId);
        }
        if (clearKind != null) {
            intent.putExtra(EXTRA_CLEAR_KIND, clearKind);
        }
        return PendingIntent.getService(
                this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildServiceNotification() {
        String text = connected
                ? getString(R.string.computer_manager_notification_connected, connectedComputer)
                : getString(R.string.computer_manager_notification_idle);
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 1, openApp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.settings_section_computer_manager))
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .addAction(0, getString(R.string.computer_manager_stop),
                        serviceAction(ACTION_STOP, null, null, 2))
                .build();
    }

    private void refreshServiceNotification() {
        getSystemService(NotificationManager.class)
                .notify(FOREGROUND_NOTIFICATION_ID, buildServiceNotification());
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.computer_manager_notification_channel),
                NotificationManager.IMPORTANCE_HIGH);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void closeSession() {
        session.close();
        connected = false;
        connectedComputer = "";
        if (running) {
            refreshServiceNotification();
            broadcastState();
        }
    }

    private void stopSelfSafely() {
        shutdownServers();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void shutdownServers() {
        stopping = true;
        running = false;
        closeSession();
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        if (discoverySocket != null) {
            discoverySocket.close();
        }
        broadcastState();
    }

    private void broadcastState() {
        sendBroadcast(new Intent(ACTION_STATE_CHANGED).setPackage(getPackageName()));
    }

    private String deviceId() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    private String deviceName() {
        String name = Settings.Global.getString(getContentResolver(), Settings.Global.DEVICE_NAME);
        return name == null || name.trim().isEmpty() ? Build.MODEL : name.trim();
    }

    private static String safeField(String value) {
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static final class RequestState {
        private final String type;
        private final String value;
        private volatile String status = "pending";
        private volatile String token;

        private RequestState(String type, String value) {
            this.type = type;
            this.value = value;
        }

        private String toJson() {
            String result = "{\"status\":\"" + json(status) + "\"";
            if (token != null) {
                result += ",\"token\":\"" + json(token) + "\"";
            }
            return result + "}";
        }
    }

    private static final class HttpRequest {
        private final String method;
        private final String path;
        private final Map<String, String> query;
        private final Map<String, String> headers;
        private final byte[] body;

        private HttpRequest(
                String method,
                String path,
                Map<String, String> query,
                Map<String, String> headers,
                byte[] body) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.headers = headers;
            this.body = body;
        }

        private static HttpRequest read(BufferedInputStream input) throws IOException {
            String requestLine = readLine(input);
            if (requestLine == null || requestLine.isEmpty()) {
                return null;
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                throw new IOException("Invalid request line");
            }
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(input)) != null && !line.isEmpty()) {
                int separator = line.indexOf(':');
                if (separator > 0) {
                    headers.put(
                            line.substring(0, separator).trim().toLowerCase(),
                            line.substring(separator + 1).trim());
                }
            }
            int length = parseContentLength(headers.get("content-length"));
            byte[] body = new byte[length];
            int offset = 0;
            while (offset < length) {
                int read = input.read(body, offset, length - offset);
                if (read < 0) {
                    throw new IOException("Incomplete body");
                }
                offset += read;
            }
            String target = parts[1];
            int queryStart = target.indexOf('?');
            String path = queryStart < 0 ? target : target.substring(0, queryStart);
            Map<String, String> query = queryStart < 0
                    ? Collections.emptyMap()
                    : parseQuery(target.substring(queryStart + 1));
            return new HttpRequest(parts[0], path, query, headers, body);
        }

        private static int parseContentLength(String value) throws IOException {
            if (value == null) {
                return 0;
            }
            try {
                int length = Integer.parseInt(value);
                if (length < 0 || length > MAX_BODY_BYTES) {
                    throw new IOException("Body too large");
                }
                return length;
            } catch (NumberFormatException ignored) {
                throw new IOException("Invalid content length");
            }
        }

        private static Map<String, String> parseQuery(String raw) {
            Map<String, String> result = new HashMap<>();
            for (String pair : raw.split("&")) {
                int separator = pair.indexOf('=');
                String key = separator < 0 ? pair : pair.substring(0, separator);
                String value = separator < 0 ? "" : pair.substring(separator + 1);
                result.put(
                        URLDecoder.decode(key, StandardCharsets.UTF_8),
                        URLDecoder.decode(value, StandardCharsets.UTF_8));
            }
            return result;
        }

        private static String readLine(BufferedInputStream input) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int current;
            while ((current = input.read()) != -1) {
                if (current == '\n') {
                    break;
                }
                if (current != '\r') {
                    bytes.write(current);
                }
                if (bytes.size() > 8192) {
                    throw new IOException("Header line too long");
                }
            }
            if (current == -1 && bytes.size() == 0) {
                return null;
            }
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static final class HttpResponse {
        private final int status;
        private final String contentType;
        private final byte[] body;

        private HttpResponse(int status, String contentType, byte[] body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }

        private static HttpResponse json(int status, String body) {
            return new HttpResponse(
                    status, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
        }

        private static HttpResponse tsv(int status, byte[] body) {
            return new HttpResponse(status, "text/tab-separated-values; charset=utf-8", body);
        }

        private static HttpResponse error(int status, String code) {
            return json(status, "{\"error\":\"" + ComputerDictionaryService.json(code) + "\"}");
        }

        private void write(BufferedOutputStream output) throws IOException {
            String reason = status == 200 ? "OK" : status == 202 ? "Accepted"
                    : status == 400 ? "Bad Request" : status == 401 ? "Unauthorized"
                    : status == 404 ? "Not Found" : "Internal Server Error";
            String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                    + "Content-Type: " + contentType + "\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            output.write(headers.getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            output.flush();
        }
    }
}
