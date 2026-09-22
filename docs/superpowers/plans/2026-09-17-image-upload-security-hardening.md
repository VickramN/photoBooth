# Image Upload Security Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the `POST /albums/{albumId}/images` upload path so uploaded files are verified, re-encoded, malware-scanned, rate-limited, and stored privately — with images only ever readable via short-lived presigned URLs issued to their owner.

**Architecture:** A validation pipeline (rate limit → size → content-sniff → re-encode → malware scan) sits in front of the existing R2 upload call inside `ImageService.create()`. R2 storage moves from a public bucket + stored URL to a private bucket + stored object key, with `S3Presigner` generating owner-scoped, expiring URLs on read. Redis backs the rate limiter; ClamAV backs the malware scan — both run as new `docker-compose` services.

**Tech Stack:** Spring Boot 4.0.6 / Java 17, Apache Tika (content sniffing), Thumbnailator (re-encode), Bucket4j + Lettuce/Redis (rate limiting), a hand-rolled clamd `INSTREAM` TCP client, AWS SDK `S3Presigner`.

**Spec:** `docs/superpowers/specs/2026-09-17-backend-security-hardening-design.md`

## Global Constraints

- Allowed image types: `image/jpeg`, `image/png`, `image/webp` (detected from bytes via Tika, not trusted from the client).
- Max upload size: 10MB (`upload.max-file-size-bytes=10485760`).
- Max image dimension after re-encode: 4096px on the longest side (`upload.max-dimension-px=4096`).
- Rate limit: 20 uploads/user/hour (`upload.rate-limit.max-per-hour=20`), Redis-backed via Bucket4j so it survives app restarts.
- Presigned URL expiry: 15 minutes (`upload.presigned-url.expiry-minutes=15`).
- All re-encoded images are normalized to JPEG output — this strips EXIF/metadata and collapses most polyglot payloads by construction (only pixel data survives decode→re-encode).
- No content-moderation API, no admin image access, no async/status-tracking machinery — explicitly out of scope per the spec.
- Follow existing code style: no dedicated `@ControllerAdvice`/global exception handler exists in this codebase (see `AdminController`'s `DeleteResult` enum pattern) — new failure modes are modeled the same way, as a sealed result type the controller switches on, not thrown exceptions mapped by a handler.

---

### Task 1: Infrastructure — dependencies, Docker services, config

**Files:**

- Modify: `pom.xml`
- Modify: `docker-compose.yml`
- Modify: `src/main/resources/application.properties`

**Interfaces:**

- Produces: `tika-core`, `thumbnailator`, `bucket4j-redis`, and a managed `lettuce-core` version (via `spring-boot-starter-data-redis`) on the classpath for later tasks. New Redis and ClamAV services reachable at `localhost:6379` / `localhost:3310` for local dev and tests.

- [ ] **Step 1: Add new dependencies to `pom.xml`**

Add inside `<dependencies>`, after the existing `jjwt-jackson` entry:

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.2</version>
</dependency>
<dependency>
    <groupId>net.coobird</groupId>
    <artifactId>thumbnailator</artifactId>
    <version>0.4.20</version>
</dependency>
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-redis</artifactId>
    <version>8.10.1</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

`spring-boot-starter-data-redis` is added only to get a version of `lettuce-core` that Spring Boot has already verified compatible with this Boot version — Task 5 uses Lettuce's client directly, not Spring Data Redis's abstractions.

- [ ] **Step 2: Verify the dependencies resolve**

Run: `./mvnw -q dependency:resolve`
Expected: exits 0, no "could not resolve dependency" errors. If `bucket4j-redis:8.10.1` fails to resolve, check https://mvnrepository.com/artifact/com.bucket4j/bucket4j-redis for the latest available `8.x` version and use that instead — record whatever version you actually used, since later tasks assume the `io.github.bucket4j.*` package names from the 8.x line.

- [ ] **Step 3: Add Redis and ClamAV services to `docker-compose.yml`**

Add alongside the existing `postgres` service (before the `volumes:` block):

```yaml
redis:
  image: redis:7
  ports:
    - "6379:6379"
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
    interval: 5s
    timeout: 5s
    retries: 5

clamav:
  image: clamav/clamav:1.3
  ports:
    - "3310:3310"
  healthcheck:
    test: ["CMD-SHELL", "echo PING | nc -w 3 localhost 3310 | grep -q PONG"]
    interval: 30s
    timeout: 10s
    retries: 10
    start_period: 120s
```

ClamAV downloads virus definitions on first boot, which can take 1-2 minutes — the long `start_period` avoids false-unhealthy status during that window.

- [ ] **Step 4: Start the new services and confirm they're healthy**

Run: `docker compose up -d redis clamav`
Then: `docker compose ps`
Expected: both `redis` and `clamav` show `healthy` (ClamAV may take up to 2 minutes on first run — re-check with `docker compose ps` if it still shows `starting`).

- [ ] **Step 5: Add new configuration properties to `application.properties`**

Add at the end of the file:

```properties
upload.max-file-size-bytes=10485760
upload.max-dimension-px=4096
upload.rate-limit.max-per-hour=20
upload.presigned-url.expiry-minutes=15

clamav.host=localhost
clamav.port=3310

spring.data.redis.host=localhost
spring.data.redis.port=6379

spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

- [ ] **Step 6: Manual step — make the R2 bucket private**

In the Cloudflare dashboard, open the R2 bucket referenced by `r2.bucket-name` and disable public access / remove the custom public domain that `r2.public-url` currently points to. This can't be scripted from this repo; do it once, by hand, before Task 7 ships (once `ImageStorageService` stops relying on `r2.public-url`, that property becomes dead and can be removed from `application.properties` — do that cleanup at the end of Task 7).

- [ ] **Step 7: Commit**

```bash
git add pom.xml docker-compose.yml src/main/resources/application.properties
git commit -m "Add upload security infra: Tika, Thumbnailator, Bucket4j-Redis, ClamAV/Redis services"
```

---

### Task 2: Content-type validation (Apache Tika)

**Files:**

- Create: `src/main/java/com/example/photoBooth/service/upload/ContentTypeValidator.java`
- Test: `src/test/java/com/example/photoBooth/service/upload/ContentTypeValidatorTest.java`

**Interfaces:**

- Produces: `ContentTypeValidator.isAllowedImage(byte[] bytes): boolean` — used by `ImageService` in Task 8.

- [ ] **Step 1: Write the failing test**

```java
package com.example.photoBooth.service.upload;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentTypeValidatorTest {

    private final ContentTypeValidator validator = new ContentTypeValidator();

    @Test
    void shouldAcceptGenuineJpeg() throws IOException {
        assertTrue(validator.isAllowedImage(genuineJpegBytes()));
    }

    @Test
    void shouldRejectTextFileDisguisedAsImage() {
        byte[] bytes = "this is not an image".getBytes();
        assertFalse(validator.isAllowedImage(bytes));
    }

    @Test
    void shouldRejectExecutableDisguisedAsImage() {
        byte[] windowsExeHeader = new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00};
        assertFalse(validator.isAllowedImage(windowsExeHeader));
    }

    private byte[] genuineJpegBytes() throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=ContentTypeValidatorTest`
Expected: FAIL — `ContentTypeValidator` doesn't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

```java
package com.example.photoBooth.service.upload;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ContentTypeValidator {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final Tika tika = new Tika();

    public boolean isAllowedImage(byte[] bytes) {
        String detected = tika.detect(bytes);
        return ALLOWED_TYPES.contains(detected);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=ContentTypeValidatorTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/photoBooth/service/upload/ContentTypeValidator.java src/test/java/com/example/photoBooth/service/upload/ContentTypeValidatorTest.java
git commit -m "Add Tika-based content-type validation for image uploads"
```

---

### Task 3: Image re-encoding (Thumbnailator)

**Files:**

- Create: `src/main/java/com/example/photoBooth/service/upload/ImageReencoder.java`
- Test: `src/test/java/com/example/photoBooth/service/upload/ImageReencoderTest.java`

**Interfaces:**

- Consumes: nothing from earlier tasks.
- Produces: `ImageReencoder.reencode(byte[] originalBytes, int maxDimensionPx): byte[] throws IOException` — used by `ImageService` in Task 8. Output is always JPEG.

- [ ] **Step 1: Write the failing test**

```java
package com.example.photoBooth.service.upload;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageReencoderTest {

    private final ImageReencoder reencoder = new ImageReencoder();

    @Test
    void shouldCapDimensionsToMax() throws IOException {
        byte[] original = buildJpeg(500, 300);

        byte[] result = reencoder.reencode(original, 100);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(100, Math.max(decoded.getWidth(), decoded.getHeight()));
    }

    @Test
    void shouldNotUpscaleImagesSmallerThanMax() throws IOException {
        byte[] original = buildJpeg(50, 30);

        byte[] result = reencoder.reencode(original, 4096);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(50, decoded.getWidth());
        assertEquals(30, decoded.getHeight());
    }

    @Test
    void shouldAlwaysOutputJpegRegardlessOfInputFormat() throws IOException {
        byte[] originalPng = buildPng(40, 40);

        byte[] result = reencoder.reencode(originalPng, 4096);

        assertEquals("JPEG", detectFormat(result));
    }

    private byte[] buildJpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private byte[] buildPng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private String detectFormat(byte[] bytes) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(iis);
            assertTrue(readers.hasNext());
            return readers.next().getFormatName().toUpperCase();
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=ImageReencoderTest`
Expected: FAIL — `ImageReencoder` doesn't exist yet.

- [ ] **Step 3: Write the implementation**

```java
package com.example.photoBooth.service.upload;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class ImageReencoder {

    public byte[] reencode(byte[] originalBytes, int maxDimensionPx) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(originalBytes));
        if (original == null) {
            throw new IOException("Unable to decode image bytes");
        }

        int longestSide = Math.max(original.getWidth(), original.getHeight());
        double scale = longestSide > maxDimensionPx ? (double) maxDimensionPx / longestSide : 1.0;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(original)
                .scale(scale)
                .outputFormat("jpg")
                .outputQuality(0.9)
                .toOutputStream(out);
        return out.toByteArray();
    }
}
```

Decoding into a `BufferedImage` and re-encoding from there (rather than streaming the original bytes straight into Thumbnailator) is what strips EXIF/metadata and collapses polyglot payloads: only the decoded pixel grid survives, nothing else from the original file makes it into the output.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=ImageReencoderTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/photoBooth/service/upload/ImageReencoder.java src/test/java/com/example/photoBooth/service/upload/ImageReencoderTest.java
git commit -m "Add Thumbnailator-based image re-encoding to strip metadata and cap dimensions"
```

---

### Task 4: ClamAV malware scanning client

**Files:**

- Create: `src/main/java/com/example/photoBooth/config/ClamAvProperties.java`
- Create: `src/main/java/com/example/photoBooth/config/UploadConfig.java`
- Create: `src/main/java/com/example/photoBooth/service/upload/ClamAvUnavailableException.java`
- Create: `src/main/java/com/example/photoBooth/service/upload/ClamAvClient.java`
- Modify: `src/main/java/com/example/photoBooth/PhotoBoothApplication.java`
- Test: `src/test/java/com/example/photoBooth/service/upload/ClamAvClientTest.java`

**Interfaces:**

- Consumes: nothing from earlier tasks.
- Produces: `ClamAvClient.isInfected(byte[] fileBytes): boolean`, throwing `ClamAvUnavailableException` (a `RuntimeException`) if clamd can't be reached — used by `ImageService` in Task 8.

- [ ] **Step 1: Write `ClamAvProperties`**

```java
package com.example.photoBooth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clamav")
public class ClamAvProperties {

    private String host;
    private int port;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
```

- [ ] **Step 2: Register `ClamAvProperties` in `PhotoBoothApplication`**

```java
@SpringBootApplication
@EnableConfigurationProperties({ NominatimProperties.class, R2Properties.class, JwtProperties.class,
        ClamAvProperties.class })
public class PhotoBoothApplication {
```

(`UploadProperties` will be added to this same list in Task 6.)

- [ ] **Step 3: Add a `SocketFactory` bean**

```java
package com.example.photoBooth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.SocketFactory;

@Configuration
public class UploadConfig {

    @Bean
    public SocketFactory socketFactory() {
        return SocketFactory.getDefault();
    }
}
```

Injecting `SocketFactory` instead of calling `new Socket(...)` directly is what makes `ClamAvClient` testable against a fake local server in Step 6, without needing real ClamAV running for the test suite.

- [ ] **Step 4: Write `ClamAvUnavailableException`**

```java
package com.example.photoBooth.service.upload;

public class ClamAvUnavailableException extends RuntimeException {

    public ClamAvUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 5: Write the failing test**

```java
package com.example.photoBooth.service.upload;

import com.example.photoBooth.config.ClamAvProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.net.SocketFactory;
import java.io.ByteArrayOutputStream;
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
```

- [ ] **Step 6: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=ClamAvClientTest`
Expected: FAIL — `ClamAvClient` doesn't exist yet.

- [ ] **Step 7: Write the implementation**

```java
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
```

- [ ] **Step 8: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=ClamAvClientTest`
Expected: PASS (3 tests).

- [ ] **Step 9: Verify against real ClamAV**

With the `clamav` container from Task 1 running and healthy, run a quick manual check:

```bash
echo -n 'X5O!P%@AP[4\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*' > /tmp/eicar.txt
```

Then write a short throwaway `main` or use the test above against `localhost:3310` (change `clamav.port`/host in a scratch test) to confirm a real EICAR test string is flagged `FOUND` — this is the standard, harmless industry test string for verifying AV integration. Discard the scratch code after confirming; it's not part of the plan's deliverables.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/example/photoBooth/config/ClamAvProperties.java src/main/java/com/example/photoBooth/config/UploadConfig.java src/main/java/com/example/photoBooth/service/upload/ClamAvUnavailableException.java src/main/java/com/example/photoBooth/service/upload/ClamAvClient.java src/main/java/com/example/photoBooth/PhotoBoothApplication.java src/test/java/com/example/photoBooth/service/upload/ClamAvClientTest.java
git commit -m "Add ClamAV INSTREAM client for malware scanning uploads"
```

---

### Task 5: Redis-backed rate limiting (Bucket4j)

**Files:**

- Create: `src/main/java/com/example/photoBooth/config/UploadProperties.java`
- Create: `src/main/java/com/example/photoBooth/config/RedisRateLimitConfig.java`
- Create: `src/main/java/com/example/photoBooth/service/upload/RateLimiterService.java`
- Modify: `src/main/java/com/example/photoBooth/PhotoBoothApplication.java`
- Test: `src/test/java/com/example/photoBooth/service/upload/RateLimiterServiceTest.java`

**Interfaces:**

- Consumes: `UploadProperties.getRateLimit().getMaxPerHour()`.
- Produces: `RateLimiterService.tryConsumeUploadToken(UUID userId): boolean` — used by `ImageService` in Task 8.

**Note on third-party API risk:** Bucket4j's Redis/Lettuce integration class names below match the `com.bucket4j:bucket4j-redis:8.x` line as of this plan's writing. If the version that actually resolved in Task 1 has different package/class names, check that version's docs/source (`~/.m2/repository/com/bucket4j/bucket4j-redis/<version>/`) and adjust imports accordingly — the logic and test structure stay the same either way.

- [ ] **Step 1: Write `UploadProperties`**

```java
package com.example.photoBooth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "upload")
public class UploadProperties {

    private long maxFileSizeBytes = 10_485_760L;
    private int maxDimensionPx = 4096;
    private final RateLimit rateLimit = new RateLimit();
    private final PresignedUrl presignedUrl = new PresignedUrl();

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public int getMaxDimensionPx() {
        return maxDimensionPx;
    }

    public void setMaxDimensionPx(int maxDimensionPx) {
        this.maxDimensionPx = maxDimensionPx;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public PresignedUrl getPresignedUrl() {
        return presignedUrl;
    }

    public static class RateLimit {
        private int maxPerHour = 20;

        public int getMaxPerHour() {
            return maxPerHour;
        }

        public void setMaxPerHour(int maxPerHour) {
            this.maxPerHour = maxPerHour;
        }
    }

    public static class PresignedUrl {
        private int expiryMinutes = 15;

        public int getExpiryMinutes() {
            return expiryMinutes;
        }

        public void setExpiryMinutes(int expiryMinutes) {
            this.expiryMinutes = expiryMinutes;
        }
    }
}
```

- [ ] **Step 2: Register `UploadProperties` in `PhotoBoothApplication`**

```java
@EnableConfigurationProperties({ NominatimProperties.class, R2Properties.class, JwtProperties.class,
        ClamAvProperties.class, UploadProperties.class })
```

- [ ] **Step 3: Write the failing test**

```java
package com.example.photoBooth.service.upload;

import com.example.photoBooth.config.UploadProperties;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private ProxyManager<byte[]> proxyManager;

    @Mock
    private RemoteBucketBuilder<byte[]> bucketBuilder;

    @Mock
    private Bucket bucket;

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        UploadProperties properties = new UploadProperties();
        properties.getRateLimit().setMaxPerHour(20);
        rateLimiterService = new RateLimiterService(proxyManager, properties);
    }

    @Test
    void shouldAllowWhenBucketHasTokens() {
        when(proxyManager.builder()).thenReturn(bucketBuilder);
        when(bucketBuilder.build(any(byte[].class), any(Supplier.class))).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);

        assertTrue(rateLimiterService.tryConsumeUploadToken(UUID.randomUUID()));
    }

    @Test
    void shouldDenyWhenBucketExhausted() {
        when(proxyManager.builder()).thenReturn(bucketBuilder);
        when(bucketBuilder.build(any(byte[].class), any(Supplier.class))).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(false);

        assertFalse(rateLimiterService.tryConsumeUploadToken(UUID.randomUUID()));
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=RateLimiterServiceTest`
Expected: FAIL — `RateLimiterService` doesn't exist yet.

- [ ] **Step 5: Write `RateLimiterService`**

```java
package com.example.photoBooth.service.upload;

import com.example.photoBooth.config.UploadProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class RateLimiterService {

    private final ProxyManager<byte[]> proxyManager;
    private final UploadProperties uploadProperties;

    public RateLimiterService(ProxyManager<byte[]> proxyManager, UploadProperties uploadProperties) {
        this.proxyManager = proxyManager;
        this.uploadProperties = uploadProperties;
    }

    public boolean tryConsumeUploadToken(UUID userId) {
        byte[] key = ("upload-rate-limit:" + userId).getBytes(StandardCharsets.UTF_8);
        Bucket bucket = proxyManager.builder().build(key, configSupplier());
        return bucket.tryConsume(1);
    }

    private Supplier<BucketConfiguration> configSupplier() {
        int maxPerHour = uploadProperties.getRateLimit().getMaxPerHour();
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(maxPerHour, Refill.intervally(maxPerHour, Duration.ofHours(1))))
                .build();
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=RateLimiterServiceTest`
Expected: PASS (2 tests). If it fails to compile because `ProxyManager`, `RemoteBucketBuilder`, `Bandwidth`, `Refill`, or `BucketConfiguration` live in different packages in the resolved version, fix the imports per the note at the top of this task.

- [ ] **Step 7: Wire the real Redis-backed `ProxyManager` bean**

```java
package com.example.photoBooth.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisRateLimitConfig {

    @Bean
    public RedisClient redisClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port) {
        return RedisClient.create(RedisURI.builder().withHost(host).withPort(port).build());
    }

    @Bean
    public StatefulRedisConnection<byte[], byte[]> redisConnection(RedisClient redisClient) {
        return redisClient.connect(ByteArrayCodec.INSTANCE);
    }

    @Bean
    public ProxyManager<byte[]> bucketProxyManager(StatefulRedisConnection<byte[], byte[]> connection) {
        return LettuceBasedProxyManager.builderFor(connection).build();
    }
}
```

- [ ] **Step 8: Verify the app context loads with Redis running**

With the `redis` container from Task 1 up, run: `./mvnw -q test -Dtest=PhotoBoothApplicationTests`
Expected: PASS — confirms the new beans wire up without errors. (This test uses H2/mocked config per the existing setup; if it doesn't touch Redis at context-load time, that's fine — the important signal is no bean-wiring failure.)

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/example/photoBooth/config/UploadProperties.java src/main/java/com/example/photoBooth/config/RedisRateLimitConfig.java src/main/java/com/example/photoBooth/service/upload/RateLimiterService.java src/main/java/com/example/photoBooth/PhotoBoothApplication.java src/test/java/com/example/photoBooth/service/upload/RateLimiterServiceTest.java
git commit -m "Add Redis-backed upload rate limiting via Bucket4j"
```

---

### Task 6: Presigned URL generation

**Files:**

- Modify: `src/main/java/com/example/photoBooth/config/R2ClientConfig.java`
- Create: `src/main/java/com/example/photoBooth/service/upload/PresignedUrlService.java`
- Test: `src/test/java/com/example/photoBooth/service/upload/PresignedUrlServiceTest.java`

**Interfaces:**

- Consumes: `R2Properties.getBucketName()`, `UploadProperties.getPresignedUrl().getExpiryMinutes()`.
- Produces: `PresignedUrlService.generateGetUrl(String objectKey): String` — used by `ImageService` in Task 8.

- [ ] **Step 1: Add an `S3Presigner` bean to `R2ClientConfig`**

```java
package com.example.photoBooth.config;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class R2ClientConfig {

    @Bean
    public S3Client r2Client(R2Properties properties) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKeyId(),
                properties.getSecretAccessKey());

        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint(properties)))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of("auto"))
                .serviceConfiguration(serviceConfiguration)
                .build();
    }

    @Bean
    public S3Presigner r2Presigner(R2Properties properties) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKeyId(),
                properties.getSecretAccessKey());

        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint(properties)))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of("auto"))
                .serviceConfiguration(serviceConfiguration)
                .build();
    }

    private String endpoint(R2Properties properties) {
        return String.format("https://%s.r2.cloudflarestorage.com", properties.getAccountId());
    }
}
```

- [ ] **Step 2: Write the failing test**

```java
package com.example.photoBooth.service.upload;

import com.example.photoBooth.config.R2Properties;
import com.example.photoBooth.config.UploadProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresignedUrlServiceTest {

    @Mock
    private S3Presigner presigner;

    @Mock
    private PresignedGetObjectRequest presignedRequest;

    private PresignedUrlService service;

    @BeforeEach
    void setUp() {
        R2Properties r2Properties = new R2Properties();
        r2Properties.setBucketName("test-bucket");

        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.getPresignedUrl().setExpiryMinutes(15);

        service = new PresignedUrlService(presigner, r2Properties, uploadProperties);
    }

    @Test
    void shouldGeneratePresignedUrlForObjectKey() throws Exception {
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);
        when(presignedRequest.url())
                .thenReturn(new URL("https://example.r2.cloudflarestorage.com/test-bucket/key123?sig=abc"));

        String url = service.generateGetUrl("key123");

        assertEquals("https://example.r2.cloudflarestorage.com/test-bucket/key123?sig=abc", url);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=PresignedUrlServiceTest`
Expected: FAIL — `PresignedUrlService` doesn't exist yet.

- [ ] **Step 4: Write `PresignedUrlService`**

```java
package com.example.photoBooth.service.upload;

import com.example.photoBooth.config.R2Properties;
import com.example.photoBooth.config.UploadProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Service
public class PresignedUrlService {

    private final S3Presigner presigner;
    private final R2Properties r2Properties;
    private final UploadProperties uploadProperties;

    public PresignedUrlService(S3Presigner presigner, R2Properties r2Properties, UploadProperties uploadProperties) {
        this.presigner = presigner;
        this.r2Properties = r2Properties;
        this.uploadProperties = uploadProperties;
    }

    public String generateGetUrl(String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(r2Properties.getBucketName())
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(uploadProperties.getPresignedUrl().getExpiryMinutes()))
                .getObjectRequest(getObjectRequest)
                .build();

        return presigner.presignGetObject(presignRequest).url().toString();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=PresignedUrlServiceTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/photoBooth/config/R2ClientConfig.java src/main/java/com/example/photoBooth/service/upload/PresignedUrlService.java src/test/java/com/example/photoBooth/service/upload/PresignedUrlServiceTest.java
git commit -m "Add presigned GET URL generation for private R2 objects"
```

---

### Task 7: Private object storage — migration, entity, `ImageStorageService`

**Files:**

- Create: `src/main/resources/db/migration/V6__image_object_key.sql`
- Modify: `src/main/java/com/example/photoBooth/entity/Image.java`
- Modify: `src/main/java/com/example/photoBooth/service/ImageStorageService.java`
- Test: `src/test/java/com/example/photoBooth/service/ImageStorageServiceTest.java` (new)
- Modify: `src/main/resources/application.properties` (remove now-unused `r2.public-url`)

**Interfaces:**

- Produces: `Image.getObjectKey()/setObjectKey(String)`; `ImageStorageService.upload(UUID ownerId, UUID albumId, UUID storageId, byte[] bytes): String` (returns the object key); `ImageStorageService.delete(String objectKey): void`. Used by `ImageService` in Task 8.

- [ ] **Step 1: Write the migration**

```sql
ALTER TABLE image RENAME COLUMN img TO object_key;
```

- [ ] **Step 2: Update the `Image` entity**

```java
package com.example.photoBooth.entity;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

@Entity
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String objectKey;

    @JsonBackReference
    @ManyToOne
    @JoinColumn(name = "album_id")
    private Album album;

    public Image() {
    }

    public Image(UUID id, String objectKey, Album album) {
        this.id = id;
        this.objectKey = objectKey;
        this.album = album;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public Album getAlbum() {
        return album;
    }

    public void setAlbum(Album album) {
        this.album = album;
    }

    public UUID getAlbumId() {
        return (album != null) ? album.getId() : null;
    }
}
```

- [ ] **Step 3: Write the failing test for `ImageStorageService`**

```java
package com.example.photoBooth.service;

import com.example.photoBooth.config.R2Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ImageStorageServiceTest {

    @Mock
    private S3Client s3Client;

    private ImageStorageService imageStorageService;

    @Test
    void uploadShouldStoreUnderOwnerPrefixedKeyAndReturnIt() {
        R2Properties properties = new R2Properties();
        properties.setBucketName("test-bucket");
        imageStorageService = new ImageStorageService(s3Client, properties);

        UUID ownerId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        UUID storageId = UUID.randomUUID();
        byte[] bytes = "jpeg-bytes".getBytes();

        String key = imageStorageService.upload(ownerId, albumId, storageId, bytes);

        assertEquals("users/" + ownerId + "/albums/" + albumId + "/" + storageId + ".jpg", key);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertEquals("test-bucket", requestCaptor.getValue().bucket());
        assertEquals(key, requestCaptor.getValue().key());
        assertTrue(requestCaptor.getValue().contentType().equals("image/jpeg"));
    }

    @Test
    void deleteShouldRemoveObjectByKey() {
        R2Properties properties = new R2Properties();
        properties.setBucketName("test-bucket");
        imageStorageService = new ImageStorageService(s3Client, properties);

        imageStorageService.delete("users/owner/albums/album/storage.jpg");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertEquals("test-bucket", requestCaptor.getValue().bucket());
        assertEquals("users/owner/albums/album/storage.jpg", requestCaptor.getValue().key());
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=ImageStorageServiceTest`
Expected: FAIL — `upload`/constructor signatures don't match yet.

- [ ] **Step 5: Rewrite `ImageStorageService`**

```java
package com.example.photoBooth.service;

import com.example.photoBooth.config.R2Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    private final S3Client s3Client;
    private final R2Properties r2Properties;

    public ImageStorageService(S3Client s3Client, R2Properties r2Properties) {
        this.s3Client = s3Client;
        this.r2Properties = r2Properties;
    }

    public String upload(UUID ownerId, UUID albumId, UUID storageId, byte[] bytes) {
        String key = buildKey(ownerId, albumId, storageId);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(r2Properties.getBucketName())
                .key(key)
                .contentType("image/jpeg")
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(bytes));

        log.info("Uploaded image to R2 with key {}", key);
        return key;
    }

    public void delete(String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(r2Properties.getBucketName())
                .key(objectKey)
                .build();

        s3Client.deleteObject(request);
        log.info("Deleted image from R2 with key {}", objectKey);
    }

    private String buildKey(UUID ownerId, UUID albumId, UUID storageId) {
        return "users/" + ownerId + "/albums/" + albumId + "/" + storageId + ".jpg";
    }
}
```

The upload path always writes `.jpg`/`image/jpeg` because `ImageReencoder` (Task 3) normalizes every accepted upload to JPEG before it ever reaches this service.

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=ImageStorageServiceTest`
Expected: PASS (2 tests).

- [ ] **Step 7: Remove the now-unused `r2.public-url` property**

Delete the `r2.public-url=...` line from `src/main/resources/application.properties`. Nothing reads `R2Properties.getPublicUrl()` after this task — leave the getter/setter on `R2Properties` itself alone (harmless dead code on a config POJO is fine; deleting the property source is what matters for the "bucket is now private" story).

Note: `ImageServiceTest` and `ImageControllerTest` still reference the old `img`/`getImg`/`setImg` API at this point in the plan and **will not compile** after this task. That's expected — Task 8 rewrites `ImageService` (and its test) to use the new `ImageStorageService` signature, and Task 9 rewrites `ImageController`/its test. Do not try to patch those tests here; leave them broken and move directly to Task 8.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V6__image_object_key.sql src/main/java/com/example/photoBooth/entity/Image.java src/main/java/com/example/photoBooth/service/ImageStorageService.java src/test/java/com/example/photoBooth/service/ImageStorageServiceTest.java src/main/resources/application.properties
git commit -m "Store images by private object key instead of public URL"
```

---

### Task 8: Wire the validation pipeline into `ImageService`

**Files:**

- Create: `src/main/java/com/example/photoBooth/service/UploadError.java`
- Create: `src/main/java/com/example/photoBooth/service/ImageUploadResult.java`
- Create: `src/main/java/com/example/photoBooth/api/ImageResponse.java`
- Modify: `src/main/java/com/example/photoBooth/service/ImageService.java`
- Modify: `src/test/java/com/example/photoBooth/service/ImageServiceTest.java`

**Interfaces:**

- Consumes: `RateLimiterService.tryConsumeUploadToken`, `ContentTypeValidator.isAllowedImage`, `ImageReencoder.reencode`, `ClamAvClient.isInfected`, `ImageStorageService.upload`, `PresignedUrlService.generateGetUrl`.
- Produces: `ImageService.create(...): ImageUploadResult`; `ImageService.toResponse(Image): ImageResponse` — used by `ImageController` in Task 9.

- [ ] **Step 1: Write `UploadError` and `ImageUploadResult`**

```java
package com.example.photoBooth.service;

public enum UploadError {
    ALBUM_NOT_FOUND,
    RATE_LIMITED,
    FILE_TOO_LARGE,
    INVALID_IMAGE_TYPE,
    INFECTED_FILE
}
```

```java
package com.example.photoBooth.service;

import com.example.photoBooth.entity.Image;

public sealed interface ImageUploadResult {

    record Success(Image image) implements ImageUploadResult {
    }

    record Failure(UploadError error) implements ImageUploadResult {
    }
}
```

- [ ] **Step 2: Write `ImageResponse`**

```java
package com.example.photoBooth.api;

import java.util.UUID;

public record ImageResponse(UUID id, UUID albumId, String url) {
}
```

- [ ] **Step 3: Rewrite `ImageServiceTest` for the new pipeline (failing first)**

```java
package com.example.photoBooth.service;

import com.example.photoBooth.api.ImageResponse;
import com.example.photoBooth.entity.Album;
import com.example.photoBooth.entity.Image;
import com.example.photoBooth.entity.User;
import com.example.photoBooth.config.UploadProperties;
import com.example.photoBooth.repository.AlbumRepository;
import com.example.photoBooth.repository.ImageRepository;
import com.example.photoBooth.service.upload.ClamAvClient;
import com.example.photoBooth.service.upload.ContentTypeValidator;
import com.example.photoBooth.service.upload.ImageReencoder;
import com.example.photoBooth.service.upload.PresignedUrlService;
import com.example.photoBooth.service.upload.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID OTHER_OWNER_ID = UUID.randomUUID();
    private static final UUID ALBUM_ID = UUID.randomUUID();
    private static final UUID MISSING_ALBUM_ID = UUID.randomUUID();
    private static final UUID IMAGE_ID = UUID.randomUUID();
    private static final UUID MISSING_IMAGE_ID = UUID.randomUUID();

    @Mock
    private ImageRepository imageRepository;
    @Mock
    private AlbumRepository albumRepository;
    @Mock
    private ImageStorageService imageStorageService;
    @Mock
    private RateLimiterService rateLimiterService;
    @Mock
    private ContentTypeValidator contentTypeValidator;
    @Mock
    private ImageReencoder imageReencoder;
    @Mock
    private ClamAvClient clamAvClient;
    @Mock
    private PresignedUrlService presignedUrlService;

    private ImageService imageService;

    @BeforeEach
    void setUp() {
        UploadProperties uploadProperties = new UploadProperties();
        imageService = new ImageService(imageRepository, albumRepository, imageStorageService,
                rateLimiterService, contentTypeValidator, imageReencoder, clamAvClient,
                presignedUrlService, uploadProperties);
    }

    private User owner(UUID ownerId) {
        User owner = new User();
        owner.setId(ownerId);
        return owner;
    }

    private Album ownedAlbum(UUID albumId, UUID ownerId) {
        Album album = new Album();
        album.setId(albumId);
        album.setOwner(owner(ownerId));
        return album;
    }

    private void stubHappyPathUpToClamAv(byte[] originalBytes, byte[] reencodedBytes) throws Exception {
        when(rateLimiterService.tryConsumeUploadToken(OWNER_ID)).thenReturn(true);
        when(contentTypeValidator.isAllowedImage(originalBytes)).thenReturn(true);
        when(imageReencoder.reencode(eq(originalBytes), anyInt())).thenReturn(reencodedBytes);
    }

    @Test
    void createShouldSaveImageWhenAllChecksPass() throws Exception {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        byte[] originalBytes = "original-bytes".getBytes();
        byte[] reencodedBytes = "reencoded-bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", originalBytes);

        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));
        stubHappyPathUpToClamAv(originalBytes, reencodedBytes);
        when(clamAvClient.isInfected(reencodedBytes)).thenReturn(false);
        when(imageStorageService.upload(eq(OWNER_ID), eq(ALBUM_ID), any(UUID.class), eq(reencodedBytes)))
                .thenReturn("users/" + OWNER_ID + "/albums/" + ALBUM_ID + "/storage.jpg");
        Image savedImage = new Image();
        savedImage.setId(IMAGE_ID);
        savedImage.setObjectKey("users/" + OWNER_ID + "/albums/" + ALBUM_ID + "/storage.jpg");
        savedImage.setAlbum(album);
        when(imageRepository.save(any(Image.class))).thenReturn(savedImage);

        ImageUploadResult result = imageService.create(ALBUM_ID, file, OWNER_ID);

        assertInstanceOf(ImageUploadResult.Success.class, result);
        assertEquals(IMAGE_ID, ((ImageUploadResult.Success) result).image().getId());
    }

    @Test
    void createShouldFailWithAlbumNotFoundWhenAlbumMissing() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "bytes".getBytes());
        when(albumRepository.findById(MISSING_ALBUM_ID)).thenReturn(Optional.empty());

        ImageUploadResult result = imageService.create(MISSING_ALBUM_ID, file, OWNER_ID);

        assertEquals(new ImageUploadResult.Failure(UploadError.ALBUM_NOT_FOUND), result);
        verifyNoInteractions(rateLimiterService, contentTypeValidator, imageReencoder, clamAvClient, imageStorageService);
    }

    @Test
    void createShouldFailWithAlbumNotFoundWhenNotOwned() throws Exception {
        Album album = ownedAlbum(ALBUM_ID, OTHER_OWNER_ID);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "bytes".getBytes());
        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));

        ImageUploadResult result = imageService.create(ALBUM_ID, file, OWNER_ID);

        assertEquals(new ImageUploadResult.Failure(UploadError.ALBUM_NOT_FOUND), result);
    }

    @Test
    void createShouldFailWithRateLimitedWhenLimiterDenies() throws Exception {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "bytes".getBytes());
        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));
        when(rateLimiterService.tryConsumeUploadToken(OWNER_ID)).thenReturn(false);

        ImageUploadResult result = imageService.create(ALBUM_ID, file, OWNER_ID);

        assertEquals(new ImageUploadResult.Failure(UploadError.RATE_LIMITED), result);
        verifyNoInteractions(contentTypeValidator, imageReencoder, clamAvClient, imageStorageService);
    }

    @Test
    void createShouldFailWithFileTooLargeWhenOverLimit() throws Exception {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        byte[] oversized = new byte[11];
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", oversized);
        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));
        when(rateLimiterService.tryConsumeUploadToken(OWNER_ID)).thenReturn(true);

        UploadProperties tinyLimitProperties = new UploadProperties();
        tinyLimitProperties.setMaxFileSizeBytes(10L);
        imageService = new ImageService(imageRepository, albumRepository, imageStorageService,
                rateLimiterService, contentTypeValidator, imageReencoder, clamAvClient,
                presignedUrlService, tinyLimitProperties);

        ImageUploadResult result = imageService.create(ALBUM_ID, file, OWNER_ID);

        assertEquals(new ImageUploadResult.Failure(UploadError.FILE_TOO_LARGE), result);
        verifyNoInteractions(contentTypeValidator, imageReencoder, clamAvClient, imageStorageService);
    }

    @Test
    void createShouldFailWithInvalidImageTypeWhenNotAGenuineImage() throws Exception {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        byte[] originalBytes = "not-an-image".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", originalBytes);
        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));
        when(rateLimiterService.tryConsumeUploadToken(OWNER_ID)).thenReturn(true);
        when(contentTypeValidator.isAllowedImage(originalBytes)).thenReturn(false);

        ImageUploadResult result = imageService.create(ALBUM_ID, file, OWNER_ID);

        assertEquals(new ImageUploadResult.Failure(UploadError.INVALID_IMAGE_TYPE), result);
        verifyNoInteractions(imageReencoder, clamAvClient, imageStorageService);
    }

    @Test
    void createShouldFailWithInfectedFileWhenClamAvFlagsIt() throws Exception {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        byte[] originalBytes = "original-bytes".getBytes();
        byte[] reencodedBytes = "reencoded-bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", originalBytes);
        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));
        stubHappyPathUpToClamAv(originalBytes, reencodedBytes);
        when(clamAvClient.isInfected(reencodedBytes)).thenReturn(true);

        ImageUploadResult result = imageService.create(ALBUM_ID, file, OWNER_ID);

        assertEquals(new ImageUploadResult.Failure(UploadError.INFECTED_FILE), result);
        verifyNoInteractions(imageStorageService);
    }

    @Test
    void findByAlbumIdShouldReturnImagesWhenOwned() {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        Image image = new Image();
        image.setId(IMAGE_ID);
        image.setObjectKey("key");
        image.setAlbum(album);

        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));
        when(imageRepository.findByAlbum_Id(ALBUM_ID)).thenReturn(List.of(image));

        Optional<List<Image>> result = imageService.findByAlbumId(ALBUM_ID, OWNER_ID);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().size());
    }

    @Test
    void findByAlbumIdShouldReturnEmptyOptionalWhenAlbumNotFound() {
        when(albumRepository.findById(MISSING_ALBUM_ID)).thenReturn(Optional.empty());

        Optional<List<Image>> result = imageService.findByAlbumId(MISSING_ALBUM_ID, OWNER_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void toResponseShouldIncludePresignedUrl() {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        Image image = new Image();
        image.setId(IMAGE_ID);
        image.setObjectKey("users/x/albums/y/z.jpg");
        image.setAlbum(album);
        when(presignedUrlService.generateGetUrl("users/x/albums/y/z.jpg"))
                .thenReturn("https://presigned.example/z.jpg");

        ImageResponse response = imageService.toResponse(image);

        assertEquals(IMAGE_ID, response.id());
        assertEquals(ALBUM_ID, response.albumId());
        assertEquals("https://presigned.example/z.jpg", response.url());
    }

    @Test
    void deleteByAlbumIdAndImageIdShouldReturnTrueWhenOwned() {
        Album album = ownedAlbum(ALBUM_ID, OWNER_ID);
        Image image = new Image();
        image.setId(IMAGE_ID);
        image.setObjectKey("users/x/albums/y/z.jpg");
        image.setAlbum(album);

        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));
        when(imageRepository.findById(IMAGE_ID)).thenReturn(Optional.of(image));

        boolean result = imageService.deleteByAlbumIdAndImageId(ALBUM_ID, IMAGE_ID, OWNER_ID);

        assertTrue(result);
        verify(imageStorageService).delete("users/x/albums/y/z.jpg");
        verify(imageRepository).deleteByAlbum_IdAndId(ALBUM_ID, IMAGE_ID);
    }

    @Test
    void deleteByAlbumIdAndImageIdShouldReturnFalseWhenAlbumNotOwned() {
        Album album = ownedAlbum(ALBUM_ID, OTHER_OWNER_ID);
        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));

        boolean result = imageService.deleteByAlbumIdAndImageId(ALBUM_ID, IMAGE_ID, OWNER_ID);

        assertFalse(result);
        verifyNoInteractions(imageStorageService);
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=ImageServiceTest`
Expected: FAIL — `ImageService`'s constructor and `create`/`toResponse` don't match yet.

- [ ] **Step 5: Rewrite `ImageService`**

```java
package com.example.photoBooth.service;

import com.example.photoBooth.api.ImageResponse;
import com.example.photoBooth.config.UploadProperties;
import com.example.photoBooth.entity.Album;
import com.example.photoBooth.entity.Image;
import com.example.photoBooth.repository.AlbumRepository;
import com.example.photoBooth.repository.ImageRepository;
import com.example.photoBooth.service.upload.ClamAvClient;
import com.example.photoBooth.service.upload.ContentTypeValidator;
import com.example.photoBooth.service.upload.ImageReencoder;
import com.example.photoBooth.service.upload.PresignedUrlService;
import com.example.photoBooth.service.upload.RateLimiterService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);

    private final ImageRepository imageRepository;
    private final AlbumRepository albumRepository;
    private final ImageStorageService imageStorageService;
    private final RateLimiterService rateLimiterService;
    private final ContentTypeValidator contentTypeValidator;
    private final ImageReencoder imageReencoder;
    private final ClamAvClient clamAvClient;
    private final PresignedUrlService presignedUrlService;
    private final UploadProperties uploadProperties;

    public ImageService(ImageRepository imageRepository, AlbumRepository albumRepository,
            ImageStorageService imageStorageService, RateLimiterService rateLimiterService,
            ContentTypeValidator contentTypeValidator, ImageReencoder imageReencoder,
            ClamAvClient clamAvClient, PresignedUrlService presignedUrlService,
            UploadProperties uploadProperties) {
        this.imageRepository = imageRepository;
        this.albumRepository = albumRepository;
        this.imageStorageService = imageStorageService;
        this.rateLimiterService = rateLimiterService;
        this.contentTypeValidator = contentTypeValidator;
        this.imageReencoder = imageReencoder;
        this.clamAvClient = clamAvClient;
        this.presignedUrlService = presignedUrlService;
        this.uploadProperties = uploadProperties;
    }

    public Optional<List<Image>> findByAlbumId(UUID albumId, UUID ownerId) {
        if (!isAlbumOwnedBy(albumId, ownerId)) {
            return Optional.empty();
        }
        return Optional.of(imageRepository.findByAlbum_Id(albumId));
    }

    public Optional<Image> findById(UUID id, UUID ownerId) {
        return imageRepository.findById(id)
                .filter(img -> img.getAlbum() != null && ownerId.equals(img.getAlbum().getOwnerId()));
    }

    public ImageResponse toResponse(Image image) {
        String url = presignedUrlService.generateGetUrl(image.getObjectKey());
        return new ImageResponse(image.getId(), image.getAlbumId(), url);
    }

    public ImageUploadResult create(UUID albumId, MultipartFile file, UUID ownerId) {
        Optional<Album> optionalAlbum = albumRepository.findById(albumId)
                .filter(album -> ownerId.equals(album.getOwnerId()));
        if (optionalAlbum.isEmpty()) {
            logger.warn("Cannot create image. Album {} not found or not owned by {}", albumId, ownerId);
            return new ImageUploadResult.Failure(UploadError.ALBUM_NOT_FOUND);
        }

        if (!rateLimiterService.tryConsumeUploadToken(ownerId)) {
            logger.warn("Upload rate limit exceeded for owner {}", ownerId);
            return new ImageUploadResult.Failure(UploadError.RATE_LIMITED);
        }

        byte[] originalBytes;
        try {
            originalBytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }

        if (originalBytes.length > uploadProperties.getMaxFileSizeBytes()) {
            logger.warn("Upload rejected, file too large ({} bytes) for owner {}", originalBytes.length, ownerId);
            return new ImageUploadResult.Failure(UploadError.FILE_TOO_LARGE);
        }

        if (!contentTypeValidator.isAllowedImage(originalBytes)) {
            logger.warn("Upload rejected, not a genuine allowed image type for owner {}", ownerId);
            return new ImageUploadResult.Failure(UploadError.INVALID_IMAGE_TYPE);
        }

        byte[] reencodedBytes;
        try {
            reencodedBytes = imageReencoder.reencode(originalBytes, uploadProperties.getMaxDimensionPx());
        } catch (IOException e) {
            logger.warn("Upload rejected, unable to re-encode image for owner {}", ownerId);
            return new ImageUploadResult.Failure(UploadError.INVALID_IMAGE_TYPE);
        }

        if (clamAvClient.isInfected(reencodedBytes)) {
            logger.warn("Upload rejected, malware detected for owner {}", ownerId);
            return new ImageUploadResult.Failure(UploadError.INFECTED_FILE);
        }

        Album album = optionalAlbum.get();
        UUID storageId = UUID.randomUUID();
        String objectKey = imageStorageService.upload(ownerId, albumId, storageId, reencodedBytes);

        Image image = new Image();
        image.setObjectKey(objectKey);
        image.setAlbum(album);

        Image savedImage = imageRepository.save(image);
        logger.info("Image created successfully with id {}", savedImage.getId());

        return new ImageUploadResult.Success(savedImage);
    }

    @Transactional
    public boolean deleteByAlbumIdAndImageId(UUID albumId, UUID imageId, UUID ownerId) {
        if (!isAlbumOwnedBy(albumId, ownerId)) {
            return false;
        }

        Optional<Image> optionalImage = imageRepository.findById(imageId);
        if (optionalImage.isEmpty() || !albumId.equals(optionalImage.get().getAlbumId())) {
            return false;
        }

        Image image = optionalImage.get();
        imageStorageService.delete(image.getObjectKey());
        imageRepository.deleteByAlbum_IdAndId(albumId, imageId);

        return true;
    }

    private boolean isAlbumOwnedBy(UUID albumId, UUID ownerId) {
        return albumRepository.findById(albumId)
                .map(album -> ownerId.equals(album.getOwnerId()))
                .orElse(false);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=ImageServiceTest`
Expected: PASS (all tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/photoBooth/service/UploadError.java src/main/java/com/example/photoBooth/service/ImageUploadResult.java src/main/java/com/example/photoBooth/api/ImageResponse.java src/main/java/com/example/photoBooth/service/ImageService.java src/test/java/com/example/photoBooth/service/ImageServiceTest.java
git commit -m "Wire upload validation pipeline into ImageService"
```

---

### Task 9: Update `ImageController` for the new result type and response DTO

**Files:**

- Create: `src/main/java/com/example/photoBooth/api/ErrorResponse.java`
- Modify: `src/main/java/com/example/photoBooth/controller/ImageController.java`
- Modify: `src/test/java/com/example/photoBooth/controller/ImageControllerTest.java`

**Interfaces:**

- Consumes: `ImageService.create(...): ImageUploadResult`, `ImageService.toResponse(Image): ImageResponse`, `ImageService.findByAlbumId(...): Optional<List<Image>>`, `ImageService.deleteByAlbumIdAndImageId(...): boolean` (all from Task 8).

- [ ] **Step 1: Write `ErrorResponse`**

```java
package com.example.photoBooth.api;

public record ErrorResponse(String error) {
}
```

- [ ] **Step 2: Rewrite `ImageControllerTest` for the new response shape (failing first)**

```java
package com.example.photoBooth.controller;

import com.example.photoBooth.api.ImageResponse;
import com.example.photoBooth.entity.Image;
import com.example.photoBooth.entity.User;
import com.example.photoBooth.security.UserPrincipal;
import com.example.photoBooth.service.ImageService;
import com.example.photoBooth.service.ImageUploadResult;
import com.example.photoBooth.service.UploadError;
import com.example.photoBooth.security.JwtService;
import com.example.photoBooth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ImageController.class)
class ImageControllerTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID ALBUM_ID = UUID.randomUUID();
    private static final UUID IMAGE_ID = UUID.randomUUID();
    private static final UUID MISSING_ALBUM_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageService imageService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserRepository userRepository;

    private UserPrincipal principal() {
        User owner = new User();
        owner.setId(OWNER_ID);
        owner.setUsername("owner");
        owner.setPassword("hashed");
        return new UserPrincipal(owner);
    }

    private Image imageWithKey(String key) {
        Image image = new Image();
        image.setId(IMAGE_ID);
        image.setObjectKey(key);
        return image;
    }

    @Test
    void getImagesByAlbumIdShouldReturnImages() throws Exception {
        Image image = imageWithKey("users/x/albums/y/z.jpg");
        when(imageService.findByAlbumId(ALBUM_ID, OWNER_ID)).thenReturn(Optional.of(List.of(image)));
        when(imageService.toResponse(image)).thenReturn(new ImageResponse(IMAGE_ID, ALBUM_ID, "https://presigned.example/z.jpg"));

        mockMvc.perform(get("/albums/" + ALBUM_ID + "/images")
                .with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].url").value("https://presigned.example/z.jpg"));
    }

    @Test
    void getImagesByAlbumIdShouldReturnNotFoundWhenMissingOrNotOwned() throws Exception {
        when(imageService.findByAlbumId(MISSING_ALBUM_ID, OWNER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/albums/" + MISSING_ALBUM_ID + "/images")
                .with(user(principal())))
                .andExpect(status().isNotFound());
    }

    @Test
    void createImageShouldReturnCreatedImageWhenAllChecksPass() throws Exception {
        Image image = imageWithKey("users/x/albums/y/z.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        when(imageService.create(eq(ALBUM_ID), any(), eq(OWNER_ID)))
                .thenReturn(new ImageUploadResult.Success(image));
        when(imageService.toResponse(image))
                .thenReturn(new ImageResponse(IMAGE_ID, ALBUM_ID, "https://presigned.example/z.jpg"));

        mockMvc.perform(multipart("/albums/" + ALBUM_ID + "/images")
                .file(file)
                .with(user(principal()))
                .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(IMAGE_ID.toString()))
                .andExpect(jsonPath("$.url").value("https://presigned.example/z.jpg"));
    }

    @Test
    void createImageShouldReturnNotFoundWhenAlbumMissingOrNotOwned() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());
        when(imageService.create(eq(MISSING_ALBUM_ID), any(), eq(OWNER_ID)))
                .thenReturn(new ImageUploadResult.Failure(UploadError.ALBUM_NOT_FOUND));

        mockMvc.perform(multipart("/albums/" + MISSING_ALBUM_ID + "/images")
                .file(file)
                .with(user(principal()))
                .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createImageShouldReturnTooManyRequestsWhenRateLimited() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());
        when(imageService.create(eq(ALBUM_ID), any(), eq(OWNER_ID)))
                .thenReturn(new ImageUploadResult.Failure(UploadError.RATE_LIMITED));

        mockMvc.perform(multipart("/albums/" + ALBUM_ID + "/images")
                .file(file)
                .with(user(principal()))
                .with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"));
    }

    @Test
    void createImageShouldReturnBadRequestWhenFileTooLarge() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());
        when(imageService.create(eq(ALBUM_ID), any(), eq(OWNER_ID)))
                .thenReturn(new ImageUploadResult.Failure(UploadError.FILE_TOO_LARGE));

        mockMvc.perform(multipart("/albums/" + ALBUM_ID + "/images")
                .file(file)
                .with(user(principal()))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FILE_TOO_LARGE"));
    }

    @Test
    void createImageShouldReturnBadRequestWhenInvalidImageType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());
        when(imageService.create(eq(ALBUM_ID), any(), eq(OWNER_ID)))
                .thenReturn(new ImageUploadResult.Failure(UploadError.INVALID_IMAGE_TYPE));

        mockMvc.perform(multipart("/albums/" + ALBUM_ID + "/images")
                .file(file)
                .with(user(principal()))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_IMAGE_TYPE"));
    }

    @Test
    void createImageShouldReturnUnprocessableEntityWhenInfected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());
        when(imageService.create(eq(ALBUM_ID), any(), eq(OWNER_ID)))
                .thenReturn(new ImageUploadResult.Failure(UploadError.INFECTED_FILE));

        mockMvc.perform(multipart("/albums/" + ALBUM_ID + "/images")
                .file(file)
                .with(user(principal()))
                .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INFECTED_FILE"));
    }

    @Test
    void deleteImageShouldReturnNoContentWhenDeleted() throws Exception {
        when(imageService.deleteByAlbumIdAndImageId(ALBUM_ID, IMAGE_ID, OWNER_ID)).thenReturn(true);

        mockMvc.perform(delete("/albums/" + ALBUM_ID + "/images/" + IMAGE_ID)
                .with(user(principal()))
                .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteImageShouldReturnNotFoundWhenNotDeleted() throws Exception {
        when(imageService.deleteByAlbumIdAndImageId(ALBUM_ID, IMAGE_ID, OWNER_ID)).thenReturn(false);

        mockMvc.perform(delete("/albums/" + ALBUM_ID + "/images/" + IMAGE_ID)
                .with(user(principal()))
                .with(csrf()))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=ImageControllerTest`
Expected: FAIL — controller still returns raw `Image`/`Optional`-based responses.

- [ ] **Step 4: Rewrite `ImageController`**

```java
package com.example.photoBooth.controller;

import com.example.photoBooth.api.ErrorResponse;
import com.example.photoBooth.api.ImageResponse;
import com.example.photoBooth.security.UserPrincipal;
import com.example.photoBooth.service.ImageService;
import com.example.photoBooth.service.ImageUploadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/albums/{albumId}/images")
public class ImageController {

    private static final Logger logger = LoggerFactory.getLogger(ImageController.class);

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping
    public ResponseEntity<List<ImageResponse>> getImagesByAlbumId(@PathVariable UUID albumId,
            @AuthenticationPrincipal UserPrincipal principal) {
        logger.info("GET /albums/{}/images - Fetching images for album", albumId);

        return imageService.findByAlbumId(albumId, principal.getId())
                .map(images -> images.stream().map(imageService::toResponse).toList())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> createImage(
            @PathVariable UUID albumId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {

        logger.info("POST /albums/{}/images - Creating image for album", albumId);

        ImageUploadResult result = imageService.create(albumId, file, principal.getId());

        if (result instanceof ImageUploadResult.Success success) {
            return ResponseEntity.status(HttpStatus.CREATED).body(imageService.toResponse(success.image()));
        }

        ImageUploadResult.Failure failure = (ImageUploadResult.Failure) result;
        return switch (failure.error()) {
            case ALBUM_NOT_FOUND -> ResponseEntity.notFound().build();
            case RATE_LIMITED -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErrorResponse("RATE_LIMITED"));
            case FILE_TOO_LARGE -> ResponseEntity.badRequest().body(new ErrorResponse("FILE_TOO_LARGE"));
            case INVALID_IMAGE_TYPE -> ResponseEntity.badRequest().body(new ErrorResponse("INVALID_IMAGE_TYPE"));
            case INFECTED_FILE -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(new ErrorResponse("INFECTED_FILE"));
        };
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @PathVariable UUID albumId,
            @PathVariable UUID imageId,
            @AuthenticationPrincipal UserPrincipal principal) {

        logger.info("DELETE /albums/{}/images/{} - Deleting image", albumId, imageId);

        boolean deleted = imageService.deleteByAlbumIdAndImageId(albumId, imageId, principal.getId());

        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=ImageControllerTest`
Expected: PASS (all tests).

- [ ] **Step 6: Run the full test suite**

Run: `./mvnw -q test`
Expected: PASS (all modules, including `AlbumServiceTest`/`AlbumControllerTest`/`GeocodingServiceTest`, which are untouched by this plan).

- [ ] **Step 7: Manual end-to-end smoke test**

With `docker compose up -d` (all four services healthy) and the app running (`./mvnw spring-boot:run`):

1. Register + log in a user via `/auth/register` and `/auth/login` to get a JWT.
2. Create an album via `POST /albums`.
3. Upload a real small JPEG via `POST /albums/{albumId}/images` with the JWT — confirm `201` with a `url` field that's a working presigned R2 link (opens in a browser, resolves the image).
4. Wait past the presigned URL's 15-minute expiry (or temporarily shrink `upload.presigned-url.expiry-minutes` to 1 for the test) and confirm the old URL 403s from R2, while `GET /albums/{albumId}/images` still returns a fresh, working URL.
5. Upload a non-image file (e.g. a `.txt` renamed to `.jpg`) — confirm `400 INVALID_IMAGE_TYPE`.
6. Upload 21 images within an hour as the same user — confirm the 21st returns `429 RATE_LIMITED`.
7. (Optional, requires the EICAR test string from Task 4 Step 9) Upload the EICAR file — confirm `422 INFECTED_FILE`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/example/photoBooth/api/ErrorResponse.java src/main/java/com/example/photoBooth/controller/ImageController.java src/test/java/com/example/photoBooth/controller/ImageControllerTest.java
git commit -m "Return owner-scoped presigned URLs and typed upload errors from ImageController"
```
