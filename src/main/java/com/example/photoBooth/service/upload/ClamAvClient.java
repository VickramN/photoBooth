package com.example.photoBooth.service.upload;

import com.example.photoBooth.config.ClamAvProperties;
import org.springframework.stereotype.Component;

import javax.net.SocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@Component
public class ClamAvClient {

    private static final int CHUNK_SIZE = 2048;

    private final ClamAvProperties properties;
    private final SocketFactory socketFactory;

    public ClamAvClient(ClamAvProperties properties, SocketFactory socketFactory) {
        this.properties = properties;
        this.socketFactory = socketFactory;
    }

    public boolean isInfected(byte[] fileBytes) {
        try (Socket socket = socketFactory.createSocket(properties.getHost(), properties.getPort())) {
            return scan(socket, fileBytes);
        } catch (IOException e) {
            throw new ClamAvUnavailableException(
                    "Failed to reach ClamAV at " + properties.getHost() + ":" + properties.getPort(), e);
        }
    }

    private boolean scan(Socket socket, byte[] fileBytes) throws IOException {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));

        int offset = 0;
        while (offset < fileBytes.length) {
            int length = Math.min(CHUNK_SIZE, fileBytes.length - offset);
            out.write(intToBytes(length));
            out.write(fileBytes, offset, length);
            offset += length;
        }
        out.write(intToBytes(0));
        out.flush();

        String response = readResponse(in);
        return response.contains("FOUND");
    }

    private byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }

    private String readResponse(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != 0) {
            buffer.write(b);
        }
        return buffer.toString(StandardCharsets.US_ASCII);
    }
}