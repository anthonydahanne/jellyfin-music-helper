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
import java.util.*;

import static net.dahanne.jmh.Utils.textOrNull;

@Component
public class FeaturingArtistsFinder {

    private static final Comparator<String> IGNORE_CASE_COMPARATOR =
            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final JellyfinRequestFactory requestFactory;
    private final JellyfinArtistService artistService;
    private final List<String> featuringMarkers;

    public FeaturingArtistsFinder(ObjectMapper objectMapper,
                                  JellyfinArtistService artistService,
                                  HttpClient jellyfinHttpClient,
                                  JellyfinRequestFactory requestFactory,
                                  JellyfinProperties properties) {
        this.objectMapper = objectMapper;
        this.artistService = artistService;
        this.httpClient = jellyfinHttpClient;
        Objects.requireNonNull(properties.getBaseUrl(),
                "Property jellyfin.base-url must be configured.");
        this.requestFactory = requestFactory;
        List<String> source = properties.getFeaturingArtists().getMarkers();
        this.featuringMarkers = source.stream()
                .map(marker -> marker == null ? "" : marker.toLowerCase(Locale.ROOT))
                .filter(marker -> !marker.isBlank())
                .toList();
    }

    public void findFeaturingArtists() {
        List<JellyfinArtistService.Artist> artists = artistService.fetchArtists();
        artists.stream()
                .filter(artist -> containsFeaturingMarker(artist.name()))
                .sorted(Comparator.comparing(JellyfinArtistService.Artist::name, IGNORE_CASE_COMPARATOR))
                .forEach(artist -> {
                    List<String> albums = fetchAlbumsForArtist(artist.id());
                    String albumList = albums.isEmpty()
                            ? "<no albums>"
                            : String.join(", ", albums);
                    System.out.println(artist.name() + " -> " + albumList);
                });
    }

    private boolean containsFeaturingMarker(String name) {
        if (name == null) {
            return false;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        for (String marker : featuringMarkers) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private List<String> fetchAlbumsForArtist(String artistId) {
        String path = "/Items?IncludeItemTypes=MusicAlbum&Recursive=true&Limit=2000&ArtistIds=" + artistId;
        HttpRequest.Builder builder = requestFactory.create(path).GET();

        Set<String> albums = new LinkedHashSet<>();
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.println("Album lookup failed for artist " + artistId + " (HTTP " + response.statusCode() + ")");
                return List.of();
            }
            JsonNode items = objectMapper.readTree(response.body()).path("Items");
            if (!items.isArray()) {
                return List.of();
            }
            items.forEach(item -> {
                String album = textOrNull(item.path("Name"));
                if (album != null && !album.isBlank()) {
                    albums.add(album);
                }
            });
        } catch (IOException | InterruptedException e) {
            System.err.println("Album lookup error for artist " + artistId + ": " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        List<String> sorted = new ArrayList<>(albums);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

}
