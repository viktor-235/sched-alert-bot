package com.github.viktor235.schedalertbot.calendar;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Per-site settings for publishing the calendar file to a GitHub repository, e.g. {@code site.stopgame.ics.publish.*}
 *
 * @param enabled       whether the site's calendar file is published
 * @param repository    target repository, {@code owner/name}
 * @param branch        target branch
 * @param path          file path in the repository
 * @param minInterval   minimum time between publish attempts
 * @param token         GitHub token with write access to the repository contents
 * @param apiUrl        GitHub REST API base URL
 * @param commitMessage message of the bot's commits
 */
public record IcsPublishProperties(@DefaultValue("false") boolean enabled,
                                   String repository,
                                   @DefaultValue("main") String branch,
                                   String path,
                                   @DefaultValue("10m") Duration minInterval,
                                   String token,
                                   @DefaultValue("https://api.github.com") String apiUrl,
                                   @DefaultValue("Update calendar") String commitMessage) {

    private static final Pattern REPOSITORY = Pattern.compile("[\\w.-]+/[\\w.-]+");

    public IcsPublishProperties {
        if (enabled) {
            List<String> missing = new ArrayList<>();
            if (isBlank(repository)) missing.add("repository");
            if (isBlank(path)) missing.add("path");
            if (isBlank(token)) missing.add("token");
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("Calendar publishing is enabled, but required settings are missing: " + missing);
            }
            if (!REPOSITORY.matcher(repository).matches()) {
                throw new IllegalArgumentException("Calendar publishing repository must be 'owner/name', got: " + repository);
            }
            if (path.startsWith("/")) {
                throw new IllegalArgumentException("Calendar publishing path must be relative to the repository root, got: " + path);
            }
        }
    }

    /**
     * Binds the settings under {@code prefix}; a missing section gives the defaults (publishing disabled)
     */
    public static IcsPublishProperties bind(Binder binder, String prefix) {
        return binder.bindOrCreate(prefix, IcsPublishProperties.class);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public String toString() {
        return "IcsPublishProperties[enabled=" + enabled +
                ", repository=" + repository +
                ", branch=" + branch +
                ", path=" + path +
                ", minInterval=" + minInterval +
                ", token=" + (isBlank(token) ? "<none>" : "***") +
                ", apiUrl=" + apiUrl +
                ", commitMessage=" + commitMessage + "]";
    }
}
