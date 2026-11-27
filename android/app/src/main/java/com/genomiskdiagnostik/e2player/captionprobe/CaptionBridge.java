package com.genomiskdiagnostik.e2player.captionprobe;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/** One authenticated session on the phone's IPv4 loopback interface. */
final class CaptionBridge implements AutoCloseable {
    static final int PORT = 47841;
    private static final int MAX_HEADER = 8192;
    private static final int READ_TIMEOUT_MS = 800;
    private final ServerSocket listener = new ServerSocket();
    private final String code;
    private final byte[] authorization;
    private final Supplier<String> state;
    private final BiPredicate<String, String> command;
    private String pairedOrigin;

    CaptionBridge(int port, Supplier<String> state, BiPredicate<String, String> command) throws IOException {
        this(port, newCode(), state, command);
    }

    CaptionBridge(int port, String code, Supplier<String> state, BiPredicate<String, String> command) throws IOException {
        this.state = state;
        this.command = command;
        this.code = code;
        authorization = ("Bearer " + code).getBytes(StandardCharsets.US_ASCII);
        try {
            listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), port), 8);
        } catch (IOException failure) {
            listener.close();
            throw failure;
        }
        var thread = new Thread(this::serve, "e2player-loopback");
        thread.setDaemon(true);
        thread.start();
    }

    static String newCode() {
        byte[] secret = new byte[16];
        new SecureRandom().nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    int port() {
        return listener.getLocalPort();
    }

    String code() {
        return code;
    }

    private void serve() {
        while (!listener.isClosed()) {
            try (var client = listener.accept()) {
                client.setSoTimeout(READ_TIMEOUT_MS);
                handle(client);
            } catch (IOException | RuntimeException ignored) {
                // Drop malformed, incomplete, or closed connections without logging request data.
            }
        }
    }

    private void handle(Socket client) throws IOException {
        String[] lines = readHeaders(client);
        if (lines == null) return;
        var request = lines[0].split(" ");
        if (request.length != 3 || !request[2].equals("HTTP/1.1")) return;
        var headers = new HashMap<String, String>();
        for (int index = 1; index < lines.length; index++) {
            var line = lines[index];
            int colon = line.indexOf(':');
            if (colon <= 0 || Character.isWhitespace(line.charAt(0))) return;
            var key = line.substring(0, colon).toLowerCase(Locale.ROOT);
            if (headers.putIfAbsent(key, line.substring(colon + 1).strip()) != null) return;
        }
        String origin = headers.get("origin");
        if (!("127.0.0.1:" + port()).equals(headers.get("host"))
                || !validOrigin(origin)
                || headers.containsKey("transfer-encoding")
                || !headers.getOrDefault("content-length", "0").equals("0")) {
            reply(client, "403 Forbidden", null, "");
            return;
        }
        String path = request[1];
        String method = request[0].equals("OPTIONS") ? headers.get("access-control-request-method") : request[0];
        boolean read = "GET".equals(method) && path.equals("/v1/state");
        String[] segments = path.split("/", -1);
        boolean control = supportsCommands() && "POST".equals(method) && isCommandPath(segments);
        if (!read && !control) {
            reply(client, "404 Not Found", origin, "");
            return;
        }
        if (control && !origin.equals(pairedOrigin)) {
            reply(client, "403 Forbidden", null, "");
            return;
        }
        if (request[0].equals("OPTIONS")) {
            reply(client, "204 No Content", origin, "");
            return;
        }
        if (!authorized(headers.getOrDefault("authorization", ""))) {
            reply(client, "401 Unauthorized", origin, "");
            return;
        }
        if (listener.isClosed()) return;
        if (read) {
            pairedOrigin = origin;
            reply(client, "200 OK", origin, snapshot());
        } else {
            boolean sent = sendCommand(segments[3], segments[4]);
            reply(client, sent ? "204 No Content" : "409 Conflict", origin, "");
        }
    }

    private static String[] readHeaders(Socket client) throws IOException {
        var incoming = new BufferedInputStream(client.getInputStream());
        var bytes = new ByteArrayOutputStream();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(READ_TIMEOUT_MS);
        int suffix = 0;
        while (bytes.size() < MAX_HEADER && System.nanoTime() < deadline) {
            int next = incoming.read();
            if (next < 0 || next >= 127 || (next < 32 && next != 9 && next != 10 && next != 13)) return null;
            bytes.write(next);
            suffix = suffix << 8 | next;
            if (suffix == 0x0d0a0d0a) {
                String text = bytes.toString(StandardCharsets.US_ASCII);
                return text.substring(0, text.length() - 4).split("\r\n", -1);
            }
        }
        return null;
    }

    boolean authorized(String supplied) {
        return !listener.isClosed()
                && MessageDigest.isEqual(authorization, supplied.getBytes(StandardCharsets.US_ASCII));
    }

    synchronized String snapshot() {
        return state.get();
    }

    boolean supportsCommands() {
        return command != null;
    }

    synchronized boolean sendCommand(String id, String action) {
        return command != null && command.test(id, action);
    }

    static boolean isCommandPath(String[] segments) {
        return segments.length == 5 && segments[0].isEmpty() && segments[1].equals("v1")
                && segments[2].equals("command") && segments[3].matches("[0-9]{1,12}")
                && segments[4].matches("play|pause|stop|previous|next");
    }

    static boolean validOrigin(String origin) {
        if (origin == null || origin.length() > 256) return false;
        if (origin.equals("null")) return true; // An Even Hub file-backed WebView may have an opaque origin.
        try {
            var uri = URI.create(origin);
            String host = uri.getHost();
            if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || !uri.getRawPath().isEmpty()) return false;
            return "https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
                    && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]")));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static void reply(Socket client, String status, String origin, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        var headers = new StringBuilder("HTTP/1.1 ").append(status).append("\r\n")
                .append("Connection: close\r\nCache-Control: no-store\r\nVary: Origin\r\n");
        if (!status.startsWith("204")) {
            headers.append("Content-Type: application/json; charset=utf-8\r\n")
                    .append("Content-Length: ").append(payload.length).append("\r\n");
        }
        if (origin != null) {
            headers.append("Access-Control-Allow-Origin: ").append(origin).append("\r\n")
                    .append("Access-Control-Allow-Methods: GET, POST\r\n")
                    .append("Access-Control-Allow-Headers: Authorization\r\n")
                    .append("Access-Control-Max-Age: 120\r\n");
        }
        var output = client.getOutputStream();
        output.write(headers.append("\r\n").toString().getBytes(StandardCharsets.US_ASCII));
        output.write(payload);
    }

    @Override
    public void close() throws IOException {
        listener.close();
    }
}
