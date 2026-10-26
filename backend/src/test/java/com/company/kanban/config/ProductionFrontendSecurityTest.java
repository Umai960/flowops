package com.company.kanban.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "prod"})
class ProductionFrontendSecurityTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Test
    void publicFrontendAndSpaRoutesServeIndex() throws Exception {
        assertIndex("/");
        assertIndex("/login");
        assertIndex("/dashboard");
        assertIndex("/projects");
        assertIndex("/reviews?taskId=1");
        assertIndex("/reports");
        assertIndex("/reports/monthly");
        assertIndex("/ppc/raw-material-arrivals?arrivalId=1");
        assertIndex("/settings/desktop-notifications");
        assertIndex("/activate?token=test");
        assertIndex("/admin/users");
        assertIndex("/admin/settings/data-management");
        assertIndex("/chat?type=task&id=1");
    }

    @Test
    void generatedFrontendAssetsArePublic() throws Exception {
        HttpResponse<String> response = get("/assets/test-app.js");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("production frontend test asset");
    }

    @Test
    void healthEndpointIsPublicAndExposesOnlyReadiness() throws Exception {
        HttpResponse<String> response = get("/api/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void protectedApisReturn401AndAreNeverForwardedToTheSpa() throws Exception {
        for (String path : new String[]{
                "/api/auth/me",
                "/api/tasks/my",
                "/api/boards",
                "/api/users",
                "/api/admin/users",
                "/api/admin/data-management/backups",
                "/api/notifications",
                "/api/agent/notifications",
                "/api/reviews",
                "/api/daily-reports/today",
                "/api/not-a-real-endpoint"
        }) {
            HttpResponse<String> response = get(path);

            assertThat(response.statusCode()).as(path).isEqualTo(401);
            assertThat(response.body()).as(path).doesNotContain("frontend-test-root");
        }
    }

    private void assertIndex(String path) throws Exception {
        HttpResponse<String> response = get(path);

        assertThat(response.statusCode()).as(path).isEqualTo(200);
        assertThat(response.body()).as(path).contains("frontend-test-root");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
