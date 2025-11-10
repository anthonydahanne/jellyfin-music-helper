package net.dahanne.jmh.config;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Objects;

@Component
public class JellyfinRequestFactory {

    private final JellyfinProperties properties;

    public JellyfinRequestFactory(JellyfinProperties properties) {
        this.properties = properties;
    }

    public HttpRequest.Builder create(String pathOrAbsoluteUrl) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(buildUri(pathOrAbsoluteUrl))
                .timeout(Duration.ofSeconds(10));
        String token = properties.getApiToken();
        if (token != null && !token.isBlank()) {
            builder.header("X-Emby-Token", token);
        }
        return builder;
    }

    private URI buildUri(String pathOrAbsoluteUrl) {
        if (pathOrAbsoluteUrl.startsWith("http://") || pathOrAbsoluteUrl.startsWith("https://")) {
            return URI.create(pathOrAbsoluteUrl);
        }
        String baseUrl = Objects.requireNonNull(properties.getBaseUrl(),
                "Property jellyfin.base-url must be configured.");
        String normalized = pathOrAbsoluteUrl.startsWith("/") ? pathOrAbsoluteUrl : "/" + pathOrAbsoluteUrl;
        return URI.create(baseUrl + normalized);
    }
}
