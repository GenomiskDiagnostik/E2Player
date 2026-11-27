package com.genomiskdiagnostik.e2player.captionprobe;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.List;

/** One request per WebSocket, on the same phone and with the same code as HTTP. */
final class CaptionSocket extends WebSocketServer {
    static final int PORT = 47842;
    private final CaptionBridge bridge;

    CaptionSocket(int port, CaptionBridge bridge) throws UnknownHostException {
        super(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), port),
                1, List.of(new Draft_6455(List.of(), 4096)));
        this.bridge = bridge;
        setReuseAddr(true);
        setTcpNoDelay(true);
        setMaxPendingConnections(8);
        setConnectionLostTimeout(2);
    }

    @Override
    public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(
            WebSocket connection, Draft draft, ClientHandshake handshake) throws InvalidDataException {
        String host = handshake.getFieldValue("Host");
        if (!handshake.getResourceDescriptor().equals("/v1/bridge")
                || !(host.equals("localhost:" + getPort()) || host.equals("127.0.0.1:" + getPort()))
                || !CaptionBridge.validOrigin(handshake.getFieldValue("Origin"))
                || getConnections().size() >= 8) {
            throw new InvalidDataException(CloseFrame.POLICY_VALIDATION, "Invalid local handshake");
        }
        return super.onWebsocketHandshakeReceivedAsServer(connection, draft, handshake);
    }

    @Override
    public void onMessage(WebSocket connection, String message) {
        if (!connection.isOpen()) return;
        connection.send(answer(message));
        connection.close();
    }

    private String answer(String message) {
        if (message.length() > 96) return "400\n";
        String[] request = message.split("\n", -1);
        if (request.length != 3) return "400\n";
        if (!bridge.authorized("Bearer " + request[0])) return "401\n";
        if (request[1].equals("GET") && request[2].equals("/v1/state")) {
            return "200\n" + bridge.snapshot();
        }
        String[] path = request[2].split("/", -1);
        if (!bridge.supportsCommands() || !request[1].equals("POST") || !CaptionBridge.isCommandPath(path)) {
            return "404\n";
        }
        return bridge.sendCommand(path[3], path[4]) ? "204\n" : "409\n";
    }

    @Override
    public void onMessage(WebSocket connection, ByteBuffer message) {
        connection.close(CloseFrame.REFUSE, "Expected text");
    }

    @Override public void onOpen(WebSocket connection, ClientHandshake handshake) {}
    @Override public void onClose(WebSocket connection, int code, String reason, boolean remote) {}
    @Override public void onStart() {}

    @Override
    public void onError(WebSocket connection, Exception failure) {
        // A failed socket leaves the existing HTTP bridge available.
        if (connection != null) connection.close();
    }
}
