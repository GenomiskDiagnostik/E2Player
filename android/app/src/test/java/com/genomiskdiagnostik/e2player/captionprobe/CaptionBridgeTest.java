package com.genomiskdiagnostik.e2player.captionprobe;

import org.junit.Test;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class CaptionBridgeTest {
    @Test
    public void pairsFirstAuthorizedOriginAndUsesOnlySupportedRoutes() throws Exception {
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
            String other = request(bridge.port(), "GET /v1/state", host, "https://other.example", bridge.code(), "");
            assertTrue(other.startsWith("HTTP/1.1 403"));
            assertFalse(other.contains("Test caption"));
            assertTrue(request(bridge.port(), "POST /v1/command/4/pause", host, origin, "wrong", "")
                    .startsWith("HTTP/1.1 401"));
            assertTrue(request(bridge.port(), "POST /v1/command/3/pause", host, origin, bridge.code(), "")
                    .startsWith("HTTP/1.1 409"));
            assertTrue(request(bridge.port(), "POST /v1/command/4/pause", host, origin, bridge.code(), "")
                    .startsWith("HTTP/1.1 204"));
            assertEquals(1, sent.get());
        }
    }

    @Test
    public void newCaptureRejectsOldCodeAndAcceptsOpaqueWebViewOrigin() throws Exception {
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
    public void captionOnlyLinkServesTextWithoutAnActionEndpoint() throws Exception {
        try (var bridge = new CaptionBridge(0, () -> "{\"caption\":\"Test caption\"}", null)) {
            String host = "127.0.0.1:" + bridge.port();
            assertTrue(request(bridge.port(), "GET /v1/state", host, "null", bridge.code(), "")
                    .contains("Test caption"));
            assertTrue(request(bridge.port(), "POST /v1/command/4/pause", host, "null", bridge.code(), "")
                    .startsWith("HTTP/1.1 404"));
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
