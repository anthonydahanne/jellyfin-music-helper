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
public class JellyfinGenreService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final JellyfinRequestFactory requestFactory;

    public JellyfinGenreService(ObjectMapper objectMapper,
                                HttpClient jellyfinHttpClient,
                                JellyfinRequestFactory requestFactory,
                                JellyfinProperties properties) {
        this.objectMapper = objectMapper;
        this.httpClient = jellyfinHttpClient;
        java.util.Objects.requireNonNull(properties.getBaseUrl(),
                "Property jellyfin.base-url must be configured.");
        this.requestFactory = requestFactory;
    }

    public List<GenreWithCount> fetchGenresWithAlbumCount() {
        List<GenreWithCount> genres = new ArrayList<>();
        String path = "/MusicGenres?SortBy=SortName&SortOrder=Ascending&Recursive=true";

        HttpRequest.Builder builder = requestFactory.create(path).GET();

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Genre lookup failed (HTTP " + response.statusCode() + ")");
            }

            JsonNode items = objectMapper.readTree(response.body()).path("Items");
            if (!items.isArray()) {
                return genres;
            }
            for (JsonNode item : items) {
                String name = textOrNull(item.path("Name"));
                String id = textOrNull(item.path("Id"));
                if (name != null && !name.isBlank() && id != null && !id.isBlank()) {
                    int albumCount = fetchAlbumCountForGenre(id);
                    genres.add(new GenreWithCount(id, name, albumCount));
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return genres;
    }

    private int fetchAlbumCountForGenre(String genreId) {
        String path = "/Items?IncludeItemTypes=MusicAlbum&GenreIds=" + genreId + "&Recursive=true&Limit=0";

        HttpRequest.Builder builder = requestFactory.create(path).GET();

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return 0;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode totalCount = root.path("TotalRecordCount");
            return totalCount.isInt() ? totalCount.asInt() : 0;
        } catch (IOException | InterruptedException e) {
            return 0;
        }
    }

    public List<GenreWithCount> deleteEmptyGenres() {
        List<GenreWithCount> deleted = new ArrayList<>();
        List<GenreWithCount> allGenres = fetchGenresWithAlbumCount();

        for (GenreWithCount genre : allGenres) {
            if (genre.albumCount() == 0) {
                if (deleteGenre(genre.id())) {
                    deleted.add(genre);
                }
            }
        }
        return deleted;
    }

    private boolean deleteGenre(String genreId) {
        String path = "/Items/" + genreId;

        HttpRequest.Builder builder = requestFactory.create(path).DELETE();

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 204 || response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    public MoveResult moveGenre(String originGenreId, String destinationGenreId) {
        var allGenres = fetchAllGenres();

        String originGenreName = allGenres.stream()
                .filter(g -> g.id().equals(originGenreId))
                .map(Genre::name)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Origin genre not found: " + originGenreId));

        String destinationGenreName = allGenres.stream()
                .filter(g -> g.id().equals(destinationGenreId))
                .map(Genre::name)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Destination genre not found: " + destinationGenreId));

        List<Album> albums = fetchAlbumsForGenre(originGenreId);
        int movedCount = 0;
        List<String> movedAlbums = new ArrayList<>();
        List<String> failedAlbums = new ArrayList<>();

        for (Album album : albums) {
            String error = updateAlbumGenre(album, originGenreName, destinationGenreName);
            if (error == null) {
                movedCount++;
                movedAlbums.add(album.name());
            } else {
                failedAlbums.add(album.name() + " (" + error + ")");
            }
        }
        return new MoveResult(albums.size(), movedCount, originGenreName, destinationGenreName, movedAlbums, failedAlbums);
    }

    public record MoveResult(int totalAlbums, int movedCount, String originGenreName,
                             String destinationGenreName, List<String> movedAlbums, List<String> failedAlbums) {
    }

    private List<Genre> fetchAllGenres() {
        List<Genre> genres = new ArrayList<>();
        String path = "/MusicGenres?SortBy=SortName&SortOrder=Ascending&Recursive=true";

        HttpRequest.Builder builder = requestFactory.create(path).GET();

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Genre lookup failed (HTTP " + response.statusCode() + ")");
            }

            JsonNode items = objectMapper.readTree(response.body()).path("Items");
            if (!items.isArray()) {
                return genres;
            }
            for (JsonNode item : items) {
                String name = textOrNull(item.path("Name"));
                String id = textOrNull(item.path("Id"));
                if (name != null && !name.isBlank() && id != null && !id.isBlank()) {
                    genres.add(new Genre(id, name));
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return genres;
    }

    private List<Album> fetchAlbumsForGenre(String genreId) {
        List<Album> albums = new ArrayList<>();
        String path = "/Items?IncludeItemTypes=MusicAlbum&GenreIds=" + genreId + "&Recursive=true&Fields=Genres";

        HttpRequest.Builder builder = requestFactory.create(path).GET();

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Album lookup failed (HTTP " + response.statusCode() + ")");
            }

            JsonNode items = objectMapper.readTree(response.body()).path("Items");
            if (!items.isArray()) {
                return albums;
            }
            for (JsonNode item : items) {
                String id = textOrNull(item.path("Id"));
                String name = textOrNull(item.path("Name"));
                List<String> genres = new ArrayList<>();
                JsonNode genreItems = item.path("GenreItems");
                if (genreItems.isArray()) {
                    for (JsonNode genreItem : genreItems) {
                        String genreName = textOrNull(genreItem.path("Name"));
                        if (genreName != null) {
                            genres.add(genreName);
                        }
                    }
                }
                if (id != null) {
                    albums.add(new Album(id, name, genres));
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return albums;
    }

    private String updateAlbumGenre(Album album, String originGenreName, String destinationGenreName) {
        // First, fetch the full album data
        String getPath = "/Users/" + getUserId() + "/Items/" + album.id();
        HttpRequest.Builder getBuilder = requestFactory.create(getPath).GET();

        JsonNode fullItem;
        try {
            HttpResponse<String> getResponse = httpClient.send(getBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (getResponse.statusCode() != 200) {
                return "Failed to fetch album: HTTP " + getResponse.statusCode();
            }
            fullItem = objectMapper.readTree(getResponse.body());
        } catch (IOException | InterruptedException e) {
            return "Fetch error: " + e.getMessage();
        }

        // Build new genres list
        List<String> newGenres = new ArrayList<>();
        for (String genre : album.genres()) {
            if (!genre.equalsIgnoreCase(originGenreName)) {
                newGenres.add(genre);
            }
        }
        if (!newGenres.contains(destinationGenreName)) {
            newGenres.add(destinationGenreName);
        }

        // Modify the item's Genres field
        try {
            ((tools.jackson.databind.node.ObjectNode) fullItem).set("Genres",
                    objectMapper.valueToTree(newGenres));
        } catch (Exception e) {
            return "JSON modification error: " + e.getMessage();
        }

        // POST the full modified item back
        String postPath = "/Items/" + album.id();
        String body;
        try {
            body = objectMapper.writeValueAsString(fullItem);
        } catch (tools.jackson.core.JacksonException e) {
            return "JSON write error: " + e.getMessage();
        }

        HttpRequest.Builder postBuilder = requestFactory.create(postPath)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        try {
            HttpResponse<String> response = httpClient.send(postBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 || response.statusCode() == 204) {
                return null; // success
            }
            return "HTTP " + response.statusCode() + ": " + response.body();
        } catch (IOException | InterruptedException e) {
            return "Request error: " + e.getMessage();
        }
    }

    private String getUserId() {
        String path = "/Users";
        HttpRequest.Builder builder = requestFactory.create(path).GET();

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch users: HTTP " + response.statusCode());
            }
            JsonNode users = objectMapper.readTree(response.body());
            if (users.isArray() && !users.isEmpty()) {
                return textOrNull(users.get(0).path("Id"));
            }
            throw new RuntimeException("No users found");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error fetching users: " + e.getMessage(), e);
        }
    }

    public record GenreWithCount(String id, String name, int albumCount) {
    }

    private record Genre(String id, String name) {
    }

    private record Album(String id, String name, List<String> genres) {
    }
}