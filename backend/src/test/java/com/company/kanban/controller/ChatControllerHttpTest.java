package com.company.kanban.controller;

import com.company.kanban.entity.*;
import com.company.kanban.repository.*;
import com.company.kanban.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the real HTTP stack: login token, file upload limits and the live message stream. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatControllerHttpTest {
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired PpcPlanningItemRepository planningItems;
    @Autowired JwtService jwt;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void messagesArriveLiveAndFilesCanBeUploaded() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Department department = departments.save(new Department("Chat HTTP " + suffix));
        User sender = users.save(new User("Sender", "sender." + suffix + "@chat.test", "x", Role.STAFF, department));
        User reader = users.save(new User("Reader", "reader." + suffix + "@chat.test", "x", Role.STAFF, department));
        PpcPlanningItem item = planningItems.save(new PpcPlanningItem("Live plan", null, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2), null, null, reader));

        HttpResponse<java.io.InputStream> stream = client.send(HttpRequest.newBuilder(uri("/api/chat/stream"))
                .header("Authorization", "Bearer " + jwt.generateToken(reader)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        assertThat(stream.statusCode()).isEqualTo(200);
        BufferedReader lines = new BufferedReader(new InputStreamReader(stream.body(), StandardCharsets.UTF_8));
        CompletableFuture<String> received = CompletableFuture.supplyAsync(() -> {
            try {
                String line;
                while ((line = lines.readLine()) != null) if (line.startsWith("data:") && line.contains("Live hello")) return line;
                return null;
            } catch (Exception e) { return null; }
        });

        HttpResponse<String> posted = postMultipart("/api/chat/planning/" + item.getId() + "/messages", sender,
                "body", null, "Live hello".getBytes(StandardCharsets.UTF_8));
        assertThat(posted.statusCode()).isEqualTo(201);
        assertThat(received.get(10, TimeUnit.SECONDS)).contains("\"senderName\":\"Sender\"");
        stream.body().close();

        HttpResponse<String> upload = postMultipart("/api/chat/planning/" + item.getId() + "/messages", sender,
                "file", "stock.xlsx", new byte[]{9, 8, 7});
        assertThat(upload.statusCode()).isEqualTo(201);
        assertThat(upload.body()).contains("\"name\":\"stock.xlsx\"");

        HttpResponse<String> tooLarge = postMultipart("/api/chat/planning/" + item.getId() + "/messages", sender,
                "file", "huge.pdf", new byte[10 * 1024 * 1024 + 10]);
        assertThat(tooLarge.statusCode()).isEqualTo(413);

        HttpResponse<String> anonymous = client.send(HttpRequest.newBuilder(uri("/api/chat/planning/" + item.getId())).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(anonymous.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> postMultipart(String path, User user, String field, String fileName, byte[] content) throws Exception {
        String boundary = "----flowops" + UUID.randomUUID();
        var body = new java.io.ByteArrayOutputStream();
        String header = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field + "\""
                + (fileName == null ? "" : "; filename=\"" + fileName + "\"\r\nContent-Type: application/octet-stream") + "\r\n\r\n";
        body.write(header.getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return client.send(HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + jwt.generateToken(user))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
}
