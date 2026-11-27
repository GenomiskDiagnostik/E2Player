package com.genomiskdiagnostik.e2player.captionprobe;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.Test;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class CaptionBridgeTest {
    @Test
    public void updatesThePairedOriginOnAuthorizedReadsAndUsesOnlySupportedRoutes() throws Exception {
        var sent = new AtomicInteger();
        try (var bridge = new CaptionBridge(0, () -> "{\"caption\":\"Test caption\"}", (id, action) ->
                id.equals("4") && action.equals("pause") && sent.incrementAndGet() > 0)) {
            String host = "127.0.0.1:" + bridge.port();
            String origin = "https://even.example";
            String preflight = request(bridge.port(), "OPTIONS /v1/state", host, origin, "",
                    "Access-Control-Request-Method: GET\r\nAccess-Control-Request-Headers: authorization\r\n");
            assertTrue(preflight.startsWith("HTTP/1.1 204"));
            assertTrue(preflight.contains("Access-Control-Allow-Origin: " + origin));
            assertFalse(preflight.contains("Test caption"));

            String wrong = request(bridge.port(), "GET /v1/state", host, origin, "wrong", "");
            assertTrue(wrong.startsWith("HTTP/1.1 401"));
            assertFalse(wrong.contains("Test caption"));
            assertTrue(request(bridge.port(), "GET /v1/state", "wrong.example", origin, bridge.code(), "")
                    .startsWith("HTTP/1.1 403"));

            String text = request(bridge.port(), "GET /v1/state", host, origin, bridge.code(), "");
            assertTrue(text.startsWith("HTTP/1.1 200"));
            assertTrue(text.contains("Test caption"));
            String otherOrigin = "https://other.example";
            assertTrue(request(bridge.port(), "GET /v1/state", host, otherOrigin, "wrong", "")
                    .startsWith("HTTP/1.1 401"));
            assertTrue(request(bridge.port(), "POST /v1/command/4/pause", host, otherOrigin, bridge.code(), "")
                    .startsWith("HTTP/1.1 403"));
            assertTrue(request(bridge.port(), "POST /v1/command/4/pause", host, origin, "wrong", "")
                    .startsWith("HTTP/1.1 401"));
            assertTrue(request(bridge.port(), "POST /v1/command/3/pause", host, origin, bridge.code(), "")
                    .startsWith("HTTP/1.1 409"));
            assertTrue(request(bridge.port(), "POST /v1/command/4/pause", host, origin, bridge.code(), "")
                    .startsWith("HTTP/1.1 204"));
            assertTrue(request(bridge.port(), "GET /v1/state", host, otherOrigin, bridge.code(), "")
                    .startsWith("HTTP/1.1 200"));
            assertTrue(request(bridge.port(), "POST /v1/command/4/pause", host, origin, bridge.code(), "")
                    .startsWith("HTTP/1.1 403"));
            assertEquals(1, sent.get());
        }
    }

    @Test
    public void aDifferentInstallationCodeIsRejectedEvenWithAnOpaqueOrigin() throws Exception {
        try (var first = new CaptionBridge(0, () -> "{}", (id, action) -> false);
             var second = new CaptionBridge(0, () -> "{}", (id, action) -> false)) {
            assertNotEquals(first.code(), second.code());
            String host = "127.0.0.1:" + second.port();
            assertTrue(request(second.port(), "GET /v1/state", host, "null", first.code(), "")
                    .startsWith("HTTP/1.1 401"));
            assertTrue(request(second.port(), "GET /v1/state", host, "null", second.code(), "")
                    .contains("Access-Control-Allow-Origin: null"));
        }
    }

    @Test
    public void restartedCaptureAcceptsTheSameStoredCode() throws Exception {
        String storedCode = CaptionBridge.newCode();
        for (int capture = 0; capture < 2; capture++) {
            try (var bridge = new CaptionBridge(0, storedCode, () -> "{}", null)) {
                String host = "127.0.0.1:" + bridge.port();
                assertTrue(request(bridge.port(), "GET /v1/state", host, "null", storedCode, "")
                        .startsWith("HTTP/1.1 200"));
            }
        }
    }

    @Test
    public void captionOnlyLinkServesTextWithoutAnActionEndpoint() throws Exception {
        try (var bridge = new CaptionBridge(0, () -> "{\"caption\":\"Test caption\"}", null)) {
            String host = "127.0.0.1:" + bridge.port();
            assertTrue(request(bridge.port(), "GET /v1/state", host, "null", bridge.code(), "")
                    .contains("Test caption"));
            assertTrue(request(bridge.port(), "POST /v1/command/4/pause", host, "null", bridge.code(), "")
                    .startsWith("HTTP/1.1 404"));
        }
    }

    @Test
    public void webSocketUsesTheSameSessionCodeAndRestrictsCommands() throws Exception {
        try (var bridge = new CaptionBridge(0, () -> "{\"caption\":\"Test caption\"}", null)) {
            var server = new CaptionSocket(0, bridge);
            server.start();
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (server.getPort() == 0 && System.nanoTime() < deadline) Thread.sleep(10);
                int port = server.getPort();
                assertTrue(port > 0);
                assertEquals("401\n", webSocketRequest(port, "wrong\nGET\n/v1/state"));
                assertEquals("200\n{\"caption\":\"Test caption\"}",
                        webSocketRequest(port, bridge.code() + "\nGET\n/v1/state"));
                assertEquals("404\n",
                        webSocketRequest(port, bridge.code() + "\nPOST\n/v1/command/4/pause"));
            } finally {
                server.stop(1000);
            }
        }
    }

    private static String webSocketRequest(int port, String request) throws Exception {
        var response = new CompletableFuture<String>();
        var client = new WebSocketClient(URI.create("ws://127.0.0.1:" + port + "/v1/bridge"),
                Map.of("Origin", "http://127.0.0.1:49152")) {
            @Override public void onOpen(ServerHandshake handshake) {}
            @Override public void onClose(int code, String reason, boolean remote) {}
            @Override public void onMessage(String text) { response.complete(text); }
            @Override public void onError(Exception failure) { response.completeExceptionally(failure); }
        };
        try {
            assertTrue(client.connectBlocking(3, TimeUnit.SECONDS));
            client.send(request);
            return response.get(3, TimeUnit.SECONDS);
        } finally {
            client.close();
        }
    }

    private static String request(int port, String start, String host, String origin, String code, String extra)
            throws IOException {
        try (var socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(2000);
            String request = start + " HTTP/1.1\r\nHost: " + host + "\r\nOrigin: " + origin
                    + "\r\nAuthorization: Bearer " + code + "\r\n" + extra + "\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
