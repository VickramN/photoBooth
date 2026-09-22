package com.example.photoBooth.service.upload;

import com.example.photoBooth.config.ClamAvProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.net.SocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClamAvClientTest {

    private ServerSocket serverSocket;

    @AfterEach
    void tearDown() throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
    }

    @Test
    void shouldReturnFalseWhenClamdReportsClean() throws IOException {
        serverSocket = new ServerSocket(0);
        respondWith("stream: OK");

        ClamAvClient client = new ClamAvClient(properties(serverSocket.getLocalPort()), SocketFactory.getDefault());

        assertFalse(client.isInfected("harmless bytes".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldReturnTrueWhenClamdReportsVirusFound() throws IOException {
        serverSocket = new ServerSocket(0);
        respondWith("stream: Eicar-Test-Signature FOUND");

        ClamAvClient client = new ClamAvClient(properties(serverSocket.getLocalPort()), SocketFactory.getDefault());

        assertTrue(client.isInfected("fake eicar bytes".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldThrowWhenClamdUnreachable() throws IOException {
        ServerSocket temp = new ServerSocket(0);
        int unusedPort = temp.getLocalPort();
        temp.close();

        ClamAvClient client = new ClamAvClient(properties(unusedPort), SocketFactory.getDefault());

        assertThrows(ClamAvUnavailableException.class,
                () -> client.isInfected("bytes".getBytes(StandardCharsets.UTF_8)));
    }


    @Test
    void scratchVerifyAgainstRealClamAv() {
        ClamAvProperties realProperties = new ClamAvProperties();
        realProperties.setHost("localhost");
        realProperties.setPort(3310);

        ClamAvClient client = new ClamAvClient(realProperties, SocketFactory.getDefault());

        String eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
        boolean infected = client.isInfected(eicar.getBytes(StandardCharsets.US_ASCII));

        System.out.println("EICAR detected as infected: " + infected);
    }
    private void respondWith(String response) {
        Thread serverThread = new Thread(() -> {
            try (Socket socket = serverSocket.accept()) {
                drainInstream(socket.getInputStream());
                socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
            } catch (IOException ignored) {
                // test server socket closed during teardown
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void drainInstream(InputStream in) throws IOException {
        byte[] header = in.readNBytes(10); // "zINSTREAM\0"
        assertEquals(10, header.length);

        while (true) {
            byte[] lengthBytes = in.readNBytes(4);
            if (lengthBytes.length < 4) {
                break;
            }
            int length = ((lengthBytes[0] & 0xFF) << 24) | ((lengthBytes[1] & 0xFF) << 16)
                    | ((lengthBytes[2] & 0xFF) << 8) | (lengthBytes[3] & 0xFF);
            if (length == 0) {
                break;
            }
            in.readNBytes(length);
        }
    }

    private ClamAvProperties properties(int port) {
        ClamAvProperties properties = new ClamAvProperties();
        properties.setHost("localhost");
        properties.setPort(port);
        return properties;
    }
}