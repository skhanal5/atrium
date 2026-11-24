package dev.skhanal.atrium.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TmdbHttpTest {

    private ServerSocket server;
    private String base;
    private final AtomicReference<String> seenAuth = new AtomicReference<>();
    private final AtomicReference<String> seenPath = new AtomicReference<>();

    /**
     * A one-request-at-a-time HTTP server on a free local port. The JDK's own
     * server is not on the Android test classpath.
     */
    @Before
    public void startServer() throws IOException {
        server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        base = "http://127.0.0.1:" + server.getLocalPort() + "/3";
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket s = server.accept()) {
                    answer(s);
                } catch (IOException e) {
                    return;
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void answer(Socket s) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        String path = in.readLine().split(" ")[1];
        seenPath.set(path);
        for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
            if (line.toLowerCase().startsWith("authorization:")) {
                seenAuth.set(line.substring("authorization:".length()).trim());
            }
        }
        String bare = path.split("\\?")[0];
        int code = bare.endsWith("/denied") ? 401 : bare.endsWith("/broken") ? 503 : 200;
        byte[] body = (code == 200 ? "{\"name\": \"Café\"}" : "{\"status_message\": \"no\"}")
                .getBytes(StandardCharsets.UTF_8);
        OutputStream out = s.getOutputStream();
        out.write(("HTTP/1.1 " + code + " X\r\nContent-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    @After
    public void stopServer() throws IOException {
        server.close();
    }

    @Test
    public void sendsTheTokenAndReturnsTheBody() throws Exception {
        String body = new TmdbHttp(base, "secret-token").get("/tv/popular?language=en-US");

        assertEquals("{\"name\": \"Café\"}", body);
        assertEquals("Bearer secret-token", seenAuth.get());
        assertEquals("/3/tv/popular?language=en-US", seenPath.get());
    }

    @Test
    public void failsWhenTmdbRefusesTheToken() {
        assertFails(new TmdbHttp(base, "bad"), "/denied", "401");
    }

    @Test
    public void failsWhenTmdbIsDown() {
        assertFails(new TmdbHttp(base, "secret-token"), "/broken", "503");
    }

    @Test
    public void failsWhenNothingAnswers() throws IOException {
        server.close();
        assertFails(new TmdbHttp(base, "secret-token"), "/tv/popular", null);
    }

    @Test
    public void failsAtOnceWithNoToken() {
        // A build made without TMDB_READ_TOKEN must not send requests that can only fail.
        assertFails(new TmdbHttp(base, ""), "/tv/popular", "no TMDB token");
        assertNull(seenPath.get());
    }

    private static void assertFails(TmdbHttp http, String path, String messagePart) {
        try {
            http.get(path);
            fail("expected IOException");
        } catch (IOException e) {
            if (messagePart != null) {
                assertTrue(e.getMessage(), e.getMessage().contains(messagePart));
            }
        }
    }
}
