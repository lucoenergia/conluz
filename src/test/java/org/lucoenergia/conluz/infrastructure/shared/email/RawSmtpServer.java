package org.lucoenergia.conluz.infrastructure.shared.email;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A misbehaving SMTP server on a free local port: it either refuses every connection with a 554 greeting, or
 * accepts it and says nothing until {@link #release()} is called. It counts the connections it receives.
 */
class RawSmtpServer implements AutoCloseable {

    private enum Behaviour { REJECT, HOLD }

    private final Behaviour behaviour;
    private final ServerSocket serverSocket;
    private final AtomicInteger connections = new AtomicInteger();
    private final CountDownLatch connected = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);
    private final List<Socket> sockets = new CopyOnWriteArrayList<>();

    private RawSmtpServer(Behaviour behaviour) throws IOException {
        this.behaviour = behaviour;
        this.serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(this::acceptLoop, "raw-smtp-server");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    static RawSmtpServer rejecting() throws IOException {
        return new RawSmtpServer(Behaviour.REJECT);
    }

    static RawSmtpServer holding() throws IOException {
        return new RawSmtpServer(Behaviour.HOLD);
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    int connections() {
        return connections.get();
    }

    boolean awaitConnection(long seconds) throws InterruptedException {
        return connected.await(seconds, TimeUnit.SECONDS);
    }

    /**
     * Closes the connections held so far, so that the client waiting on them fails at once.
     */
    void release() {
        released.countDown();
        sockets.forEach(RawSmtpServer::closeQuietly);
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                connections.incrementAndGet();
                connected.countDown();
                if (behaviour == Behaviour.REJECT) {
                    OutputStream out = socket.getOutputStream();
                    out.write("554 5.3.2 Service not available\r\n".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    closeQuietly(socket);
                } else if (released.getCount() == 0) {
                    closeQuietly(socket);
                } else {
                    sockets.add(socket);
                }
            } catch (IOException e) {
                // The server socket was closed
            }
        }
    }

    @Override
    public void close() {
        release();
        closeQuietly(serverSocket);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            // Nothing to do
        }
    }
}
