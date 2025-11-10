package net.dahanne.jmh;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.dahanne.jmh.config.JellyfinRequestFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static net.dahanne.jmh.Utils.textOrNull;

@Component
public class SpotifyHistoryMatcher {

    private static final TypeReference<List<StreamingHistoryEntry>> HISTORY_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final JellyfinRequestFactory requestFactory;
    private final Path historyPath;
    private final Map<String, LookupResult> jellyfinCache = new ConcurrentHashMap<>();

    public SpotifyHistoryMatcher(
            ObjectMapper objectMapper,
            HttpClient jellyfinHttpClient,
            JellyfinRequestFactory requestFactory,
            @Value("${streaming.history.file}") String historyFile) {
        this.objectMapper = objectMapper;
        this.httpClient = jellyfinHttpClient;
        this.requestFactory = requestFactory;
        this.historyPath = Path.of(historyFile);
    }

    public void compareWithSpotifyList() {
        List<StreamingHistoryEntry> entries = readStreamingHistory();
        if (entries.isEmpty()) {
            return;
        }

        Map<String, TrackAggregate> aggregates = aggregate(entries);
        for (TrackAggregate aggregate : aggregates.values()) {
            TrackMetadata track = aggregate.metadata();
            LookupResult result;
            if (!track.hasMetadata()) {
                result = LookupResult.notFound("missing metadata");
            } else {
                result = jellyfinCache.computeIfAbsent(track.cacheKey(),
                        _ -> queryJellyfinForTrack(track.artist(), track.track()));
            }
            printLookupLine(track.displayArtist(), track.displayTrack(), aggregate.count(), result);
        }
    }

    private List<StreamingHistoryEntry> readStreamingHistory() {
        try {
            if (!Files.exists(historyPath)) {
                System.err.println("Failed to read file: " + historyPath + " does not exist");
                return List.of();
            }
            return objectMapper.readValue(historyPath.toFile(), HISTORY_TYPE);
        } catch (JacksonException e) {
            System.err.println("Failed to parse JSON: " + e.getMessage());
            return List.of();
        }
    }

    private Map<String, TrackAggregate> aggregate(List<StreamingHistoryEntry> entries) {
        Map<String, TrackAggregate> aggregates = new TreeMap<>();
        for (StreamingHistoryEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            TrackMetadata metadata = TrackMetadata.from(entry);
            TrackAggregate aggregate = aggregates.computeIfAbsent(sortKey(metadata),
                    _ -> new TrackAggregate(metadata));
            aggregate.increment();
        }
        return aggregates;
    }

    private void printLookupLine(String artist, String track, int count, LookupResult result) {
        StringBuilder line = new StringBuilder()
                .append(artist)
                .append(" - ")
                .append(track)
                .append(' ')
                .append(toEmojiNumber(count))
                .append(' ')
                .append(result.found() ? "✅" : "❌");

        if (result.found() && result.hasAlbum()) {
            line.append(' ').append(result.albumName());
        } else if (!result.found() && result.hasMessage()) {
            line.append(" (").append(result.message()).append(')');
        }

        System.out.println(line);
    }

    private LookupResult queryJellyfinForTrack(String artist, String track) {
        try {
            String searchTerm = URLEncoder.encode(track, StandardCharsets.UTF_8);
            String path = "/Items?IncludeItemTypes=Audio&Recursive=true&Fields=AlbumArtists,Artists&Limit=50&searchTerm="
                    + searchTerm;

            HttpRequest.Builder requestBuilder = requestFactory.create(path).GET();

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return LookupResult.notFound("lookup failed (HTTP " + response.statusCode() + ")");
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.path("Items");
            if (!items.isArray()) {
                return LookupResult.notFound(null);
            }

            for (JsonNode item : items) {
                List<String> candidateArtists = extractStringList(item.path("Artists"), item.path("AlbumArtists"));
                String candidateName = textOrNull(item.path("Name"));
                if (candidateArtists.stream().anyMatch(a -> equalsIgnoreCase(a, artist))
                        && equalsIgnoreCase(candidateName, track)) {
                    String album = extractAlbumName(item);
                    return LookupResult.found(album);
                }
            }
            return LookupResult.notFound(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return LookupResult.notFound("lookup interrupted");
        } catch (IOException e) {
            return LookupResult.notFound("lookup error: " + e.getClass().getSimpleName());
        }
    }

    private String extractAlbumName(JsonNode item) {
        String album = textOrNull(item.path("Album"));
        if (album == null) {
            album = textOrNull(item.path("AlbumName"));
        }
        if (album == null) {
            album = textOrNull(item.path("AlbumTitle"));
        }
        return album;
    }

    private List<String> extractStringList(JsonNode... sources) {
        List<String> result = new ArrayList<>();
        for (JsonNode source : sources) {
            if (source == null || source.isMissingNode() || source.isNull()) {
                continue;
            }
            if (source.isArray()) {
                source.forEach(node -> {
                    String value = textOrNull(node);
                    if (value != null) {
                        result.add(value);
                    }
                });
            } else {
                String value = textOrNull(source);
                if (value != null) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return Objects.equals(left == null ? null : left.toLowerCase(Locale.ROOT),
                right == null ? null : right.toLowerCase(Locale.ROOT));
    }

    private String sortKey(TrackMetadata track) {
        return (track.displayArtist() + "||" + track.displayTrack()).toLowerCase(Locale.ROOT);
    }

    private String toEmojiNumber(int value) {
        String digits = Integer.toString(Math.max(1, value));
        StringBuilder result = new StringBuilder();
        for (char digit : digits.toCharArray()) {
            result.append(EmojiDigits.forDigit(digit));
        }
        return result.toString();
    }

    private record TrackMetadata(String artist, String track) {
        static TrackMetadata from(StreamingHistoryEntry entry) {
            return new TrackMetadata(entry.artistName(), entry.trackName());
        }

        boolean hasMetadata() {
            return artist != null && track != null;
        }

        String displayArtist() {
            return artist != null ? artist : "<missing artist>";
        }

        String displayTrack() {
            return track != null ? track : "<missing track>";
        }

        String cacheKey() {
            String safeArtist = artist == null ? "" : artist;
            String safeTrack = track == null ? "" : track;
            return (safeArtist + "||" + safeTrack).toLowerCase(Locale.ROOT);
        }
    }

    private record LookupResult(boolean found, String albumName, String message) {
        static LookupResult found(String albumName) {
            return new LookupResult(true, albumName, null);
        }

        static LookupResult notFound(String message) {
            return new LookupResult(false, null, message);
        }

        boolean hasAlbum() {
            return albumName != null && !albumName.isBlank();
        }

        boolean hasMessage() {
            return message != null && !message.isBlank();
        }
    }

    private static final class TrackAggregate {
        private final TrackMetadata metadata;
        private int count;

        private TrackAggregate(TrackMetadata metadata) {
            this.metadata = metadata;
        }

        private void increment() {
            count++;
        }

        private int count() {
            return count;
        }

        private TrackMetadata metadata() {
            return metadata;
        }
    }

    private enum EmojiDigits {
        ZERO('0', "0️⃣"),
        ONE('1', "1️⃣"),
        TWO('2', "2️⃣"),
        THREE('3', "3️⃣"),
        FOUR('4', "4️⃣"),
        FIVE('5', "5️⃣"),
        SIX('6', "6️⃣"),
        SEVEN('7', "7️⃣"),
        EIGHT('8', "8️⃣"),
        NINE('9', "9️⃣");

        private final char digit;
        private final String emoji;

        EmojiDigits(char digit, String emoji) {
            this.digit = digit;
            this.emoji = emoji;
        }

        static String forDigit(char digit) {
            for (EmojiDigits value : values()) {
                if (value.digit == digit) {
                    return value.emoji;
                }
            }
            return "";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StreamingHistoryEntry(
            @JsonProperty("artistName") String artistName,
            @JsonProperty("trackName") String trackName) {
    }
}
