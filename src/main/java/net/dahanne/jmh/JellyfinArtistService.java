package net.dahanne.jmh;

import net.dahanne.jmh.config.JellyfinProperties;
import net.dahanne.jmh.config.JellyfinRequestFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static net.dahanne.jmh.Utils.textOrNull;

@Component
public class JellyfinArtistService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final JellyfinRequestFactory requestFactory;

    public JellyfinArtistService(ObjectMapper objectMapper,
                                 HttpClient jellyfinHttpClient,
                                 JellyfinRequestFactory requestFactory,
                                 JellyfinProperties properties) {
        this.objectMapper = objectMapper;
        this.httpClient = jellyfinHttpClient;
        java.util.Objects.requireNonNull(properties.getBaseUrl(),
                "Property jellyfin.base-url must be configured.");
        this.requestFactory = requestFactory;
    }

    public List<Artist> fetchArtists() {
        List<Artist> artists = new ArrayList<>();
        String path = "/Artists?SortBy=SortName&SortOrder=Ascending&Limit=10000&Recursive=true";

        HttpRequest.Builder builder = requestFactory.create(path).GET();

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.println("Artist lookup failed (HTTP " + response.statusCode() + ")");
                return artists;
            }

            JsonNode items = objectMapper.readTree(response.body()).path("Items");
            if (!items.isArray()) {
                return artists;
            }
            items.forEach(item -> {
                String name = textOrNull(item.path("Name"));
                String id = textOrNull(item.path("Id"));
                if (name != null && !name.isBlank() && id != null && !id.isBlank()) {
                    artists.add(new Artist(id, name));
                }
            });
        } catch (IOException | InterruptedException e) {
            System.err.println("Artist lookup error: " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return artists;
    }

    public record Artist(String id, String name) {
    }

}
