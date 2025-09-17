package com.example.zeromq.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lightweight in-process ZAP authenticator for handling CURVE authentication requests.
 *
 * <p>This authenticator implements a minimal subset of the ZAP protocol sufficient
 * to validate CURVE client public keys against a configured allow-list. It binds to
 * the standard inproc ZAP endpoint: inproc://zeromq.zap.01
 *
 * <p>Designed to be started from auto-configuration when CURVE is enabled.
 */
public class ZmqZapAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(ZmqZapAuthenticator.class);

    private final ZmqContextHolder contextHolder;
    private final Set<String> allowedClientKeys; // Z85 encoded public keys

    private volatile boolean running = false;
    private Thread worker;
    private ZMQ.Socket socket;

    public ZmqZapAuthenticator(ZmqContextHolder contextHolder, List<String> allowedClientKeys) {
        this.contextHolder = contextHolder;
        if (allowedClientKeys == null) {
            this.allowedClientKeys = Collections.emptySet();
        } else {
            this.allowedClientKeys = new HashSet<>(allowedClientKeys);
        }
    }

    public void start() {
        if (running) return;
        if (!contextHolder.isAvailable()) {
            log.warn("ZMQ context not available - ZAP authenticator will not start");
            return;
        }

        running = true;
        worker = new Thread(this::runLoop, "zmq-zap-authenticator");
        worker.setDaemon(true);
        worker.start();
        log.info("ZAP authenticator started (inproc://zeromq.zap.01) allowedKeysCount={}", allowedClientKeys.size());
    }

    public void stop() {
        running = false;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception e) {
            log.debug("Error closing ZAP socket: {}", e.getMessage());
        }
        if (worker != null) {
            worker.interrupt();
        }
        log.info("ZAP authenticator stopped");
    }

    private void runLoop() {
        ZContext ctx = contextHolder.getContext();
        socket = ctx.createSocket(ZMQ.REP);
        socket.bind("inproc://zeromq.zap.01");

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                byte[] first = socket.recv(0);
                if (first == null) {
                    continue; // interrupted or closed
                }

                java.util.List<byte[]> frames = new java.util.ArrayList<>();
                frames.add(first);
                while (socket.hasReceiveMore()) {
                    frames.add(socket.recv());
                }

                // Minimal parsing according to ZAP: version, requestId, domain, address, identity, mechanism, credentials...
                int idx = 0;
                if (frames.size() < 6) {
                    sendErrorReply(new byte[0], "400", "invalid-zap-request");
                    continue;
                }

                String version = new String(frames.get(idx++), StandardCharsets.US_ASCII);
                byte[] requestId = frames.get(idx++);
                String domain = new String(frames.get(idx++), StandardCharsets.US_ASCII);
                String address = new String(frames.get(idx++), StandardCharsets.US_ASCII);
                byte[] identity = frames.get(idx++);
                String mechanism = new String(frames.get(idx++), StandardCharsets.US_ASCII);

                boolean allowed = false;
                String userId = "";

                if ("CURVE".equalsIgnoreCase(mechanism)) {
                    if (idx < frames.size()) {
                        byte[] cred = frames.get(idx);
                        // credentials frame should contain the Z85 encoded 32-byte public key
                        String clientPub = new String(cred, StandardCharsets.US_ASCII);
                        if (allowedClientKeys.contains(clientPub)) {
                            allowed = true;
                            userId = clientPub; // use public key as user id (public info)
                        }
                    }
                }

                if (allowed) {
                    sendOkReply(requestId, userId);
                } else {
                    sendFailureReply(requestId, "400", "unauthorized");
                }

            } catch (Exception e) {
                log.error("ZAP authenticator error: {}", e.getMessage());
            }
        }

        try { socket.close(); } catch (Exception ignore) {}
    }

    private void sendOkReply(byte[] requestId, String userId) {
        // version, requestId, statusCode, statusText, userId, metadata(empty)
        socket.send("1.0".getBytes(StandardCharsets.US_ASCII), ZMQ.SNDMORE);
        socket.send(requestId, ZMQ.SNDMORE);
        socket.send("200".getBytes(StandardCharsets.US_ASCII), ZMQ.SNDMORE);
        socket.send("OK".getBytes(StandardCharsets.US_ASCII), ZMQ.SNDMORE);
        socket.send(userId.getBytes(StandardCharsets.US_ASCII), ZMQ.SNDMORE);
        socket.send("".getBytes(StandardCharsets.US_ASCII), 0);
    }

    private void sendFailureReply(byte[] requestId, String code, String reason) {
        socket.send("1.0".getBytes(StandardCharsets.US_ASCII), ZMQ.SNDMORE);
        socket.send(requestId, ZMQ.SNDMORE);
        socket.send(code.getBytes(StandardCharsets.US_ASCII), ZMQ.SNDMORE);
        socket.send(reason.getBytes(StandardCharsets.US_ASCII), ZMQ.SNDMORE);
        socket.send("".getBytes(StandardCharsets.US_ASCII), ZMQ.SNDMORE);
        socket.send("".getBytes(StandardCharsets.US_ASCII), 0);
    }

    private void sendErrorReply(byte[] requestId, String code, String reason) {
        try {
            sendFailureReply(requestId, code, reason);
        } catch (Exception e) {
            log.debug("Failed to send ZAP error reply: {}", e.getMessage());
        }
    }
} 