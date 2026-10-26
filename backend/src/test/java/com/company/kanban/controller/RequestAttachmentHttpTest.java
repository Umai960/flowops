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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Files attached to interdepartment requests: upload, download, who may see and remove them. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RequestAttachmentHttpTest {
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired InterdepartmentRequestRepository requests;
    @Autowired JwtService jwt;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void requesterAttachesFilesThatTheOtherDepartmentCanOpen() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Department rdd = departments.save(new Department("Files RDD " + suffix));
        Department ppc = departments.save(new Department("Files PPC " + suffix));
        Department qc = departments.save(new Department("Files QC " + suffix));
        User requester = users.save(new User("Requester", "requester." + suffix + "@files.test", "x", Role.STAFF, rdd));
        User handler = users.save(new User("Handler", "handler." + suffix + "@files.test", "x", Role.STAFF, ppc));
        User outsider = users.save(new User("Outsider", "outsider." + suffix + "@files.test", "x", Role.STAFF, qc));
        InterdepartmentRequest item = requests.save(new InterdepartmentRequest(rdd, ppc, requester, "Test lot", "details", Priority.MEDIUM, LocalDate.of(2026, 11, 2)));
        String base = "/api/interdepartment-requests/" + item.getId() + "/attachments";

        HttpResponse<String> upload = upload(base, requester, "lot plan.pdf", new byte[]{1, 2, 3});
        assertThat(upload.statusCode()).isEqualTo(200);
        assertThat(upload.body()).contains("\"fileName\":\"lot plan.pdf\"").contains("\"uploadedByName\":\"Requester\"").doesNotContain("\"data\"");
        Matcher idMatch = Pattern.compile("\"attachments\":\\[\\{\"id\":(\\d+)").matcher(upload.body());
        assertThat(idMatch.find()).isTrue();
        String fileUrl = base + "/" + idMatch.group(1);

        HttpResponse<byte[]> download = client.send(get(fileUrl, handler), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).containsExactly(1, 2, 3);
        assertThat(download.headers().firstValue("Content-Type")).hasValue("application/pdf");

        assertThat(client.send(get(fileUrl, outsider), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
        assertThat(upload(base, outsider, "x.pdf", new byte[]{1}).statusCode()).isEqualTo(403);
        assertThat(upload(base, requester, "script.exe", new byte[]{1}).statusCode()).isEqualTo(400);
        assertThat(upload(base, requester, "huge.pdf", new byte[10 * 1024 * 1024 + 10]).statusCode()).isEqualTo(413);

        // The receiving department can add its own files, but only the uploader can remove one.
        assertThat(upload(base, handler, "schedule.xlsx", new byte[]{4}).statusCode()).isEqualTo(200);
        assertThat(client.send(delete(fileUrl, handler), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
        HttpResponse<String> removed = client.send(delete(fileUrl, requester), HttpResponse.BodyHandlers.ofString());
        assertThat(removed.statusCode()).isEqualTo(200);
        assertThat(removed.body()).doesNotContain("lot plan.pdf").contains("schedule.xlsx");
        assertThat(client.send(get(fileUrl, requester), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> upload(String path, User user, String fileName, byte[] content) throws Exception {
        String boundary = "----flowops" + UUID.randomUUID();
        var body = new java.io.ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return client.send(HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + jwt.generateToken(user))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest get(String path, User user) {
        return HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + jwt.generateToken(user)).GET().build();
    }

    private HttpRequest delete(String path, User user) {
        return HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + jwt.generateToken(user)).DELETE().build();
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
}
