package com.github.viktor235.schedalertbot.calendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GitHubContentsClientTest {

    private static final String CONTENTS_URL = "https://api.github.test/repos/owner/calendars/contents/calendars/my%20site.ics";
    private static final byte[] CONTENT = "BEGIN:VCALENDAR\r\n".getBytes(StandardCharsets.UTF_8);

    private MockRestServiceServer server;
    private GitHubContentsClient client;

    @BeforeEach
    void setup() {
        IcsPublishProperties props = new IcsPublishProperties(true, "owner/calendars", "pages", "calendars/my site.ics",
                Duration.ofMinutes(15), "secret-token", "https://api.github.test", "Update calendar");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GitHubContentsClient(GitHubContentsClient.configure(builder, props).build(), props);
    }

    @Test
    void getSha_whenFileExists_thenReturnsSha() {
        server.expect(requestTo(CONTENTS_URL + "?ref=pages"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer secret-token"))
                .andExpect(header("Accept", "application/vnd.github+json"))
                .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
                .andRespond(withSuccess("{\"type\":\"file\",\"sha\":\"abc123\"}", MediaType.APPLICATION_JSON));

        assertThat(client.getSha()).contains("abc123");
        server.verify();
    }

    @Test
    void getSha_whenNoFile_thenEmpty() {
        server.expect(requestTo(CONTENTS_URL + "?ref=pages"))
                .andRespond(withResourceNotFound());

        assertThat(client.getSha()).isEmpty();
    }

    @Test
    void put_whenFileExists_thenSendsShaAndReturnsNewSha() {
        server.expect(requestTo(CONTENTS_URL))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer secret-token"))
                .andExpect(content().json("""
                        {"message": "Update calendar", "content": "%s", "branch": "pages", "sha": "old"}
                        """.formatted(Base64.getEncoder().encodeToString(CONTENT)), JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"content\":{\"sha\":\"new\"},\"commit\":{\"sha\":\"c1\"}}", MediaType.APPLICATION_JSON));

        assertThat(client.put(CONTENT, "old", "Update calendar")).isEqualTo("new");
        server.verify();
    }

    @Test
    void put_whenNewFile_thenNoSha() {
        server.expect(requestTo(CONTENTS_URL))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.sha").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"content\":{\"sha\":\"new\"}}"));

        assertThat(client.put(CONTENT, null, "Update calendar")).isEqualTo("new");
        server.verify();
    }

    @Test
    void put_whenConflict_thenConflictException() {
        server.expect(requestTo(CONTENTS_URL)).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.put(CONTENT, "old", "Update calendar"))
                .isInstanceOf(GitHubConflictException.class);
    }

    @Test
    void put_whenShaMissingForExistingFile_thenConflictException() {
        server.expect(requestTo(CONTENTS_URL)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"Invalid request.\\n\\n\\\"sha\\\" wasn't supplied.\"}"));

        assertThatThrownBy(() -> client.put(CONTENT, null, "Update calendar"))
                .isInstanceOf(GitHubConflictException.class)
                .hasMessageContaining("wasn't supplied");
    }

    @Test
    void put_whenUnauthorized_thenNotConflict() {
        server.expect(requestTo(CONTENTS_URL)).andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.put(CONTENT, "old", "Update calendar"))
                .isNotInstanceOf(GitHubConflictException.class);
    }

    @Test
    void getSha_whenServerError_thenPropagates() {
        server.expect(requestTo(CONTENTS_URL + "?ref=pages")).andRespond(withServerError());

        assertThatThrownBy(() -> client.getSha())
                .isInstanceOf(HttpServerErrorException.class);
    }
}
