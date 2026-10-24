package com.github.viktor235.schedalertbot.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and writes one file of a GitHub repository through the
 * <a href="https://docs.github.com/en/rest/repos/contents">Contents API</a>
 */
public class GitHubContentsClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final String owner;
    private final String repo;
    private final String branch;
    private final String[] pathSegments;

    GitHubContentsClient(RestClient restClient, IcsPublishProperties props) {
        this.restClient = restClient;
        String[] repository = props.repository().split("/", 2);
        this.owner = repository[0];
        this.repo = repository[1];
        this.branch = props.branch();
        this.pathSegments = props.path().split("/");
    }

    public static GitHubContentsClient create(IcsPublishProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return new GitHubContentsClient(configure(RestClient.builder(), props).requestFactory(requestFactory).build(), props);
    }

    static RestClient.Builder configure(RestClient.Builder builder, IcsPublishProperties props) {
        return builder
                .baseUrl(props.apiUrl())
                .defaultHeader("Authorization", "Bearer " + props.token())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");
    }

    /**
     * @return blob SHA of the file on the branch, or empty if there is no such file
     */
    public Optional<String> getSha() {
        try {
            JsonNode body = restClient.get()
                    .uri(b -> contentsUri(b).queryParam("ref", branch).build())
                    .retrieve()
                    .body(JsonNode.class);
            return Optional.of(body.path("sha").asText());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    /**
     * Creates or replaces the file in one commit
     *
     * @param sha blob SHA of the file being replaced, {@code null} to create it
     * @return blob SHA of the written file
     * @throws GitHubConflictException if {@code sha} is not the current one
     */
    public String put(byte[] content, String sha, String message) {
        Map<String, String> request = new LinkedHashMap<>();
        request.put("message", message);
        request.put("content", Base64.getEncoder().encodeToString(content));
        request.put("branch", branch);
        if (sha != null) {
            request.put("sha", sha);
        }
        try {
            JsonNode body = restClient.put()
                    .uri(b -> contentsUri(b).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            return body.path("content").path("sha").asText();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)
                    || e.getStatusCode().isSameCodeAs(HttpStatus.UNPROCESSABLE_ENTITY)) {
                throw new GitHubConflictException("Outdated file version: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
            }
            throw e;
        }
    }

    private UriBuilder contentsUri(UriBuilder builder) {
        return builder.pathSegment("repos", owner, repo, "contents").pathSegment(pathSegments);
    }

    @Override
    public String toString() {
        return owner + "/" + repo + "@" + branch + ":" + String.join("/", pathSegments);
    }
}
