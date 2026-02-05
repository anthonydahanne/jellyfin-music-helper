package net.dahanne.jmh;

import org.springframework.context.annotation.Bean;
import org.springframework.shell.core.command.ExitStatus;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.shell.core.command.exit.ExitStatusExceptionMapper;
import org.springframework.stereotype.Component;

@Component
public class Commands {

    @Bean
    public ExitStatusExceptionMapper exceptionMapper() {
        return exception -> {
            Throwable cause = exception;
            StringBuilder context = new StringBuilder();
            while (cause.getCause() != null) {
                if (cause.getMessage() != null) {
                    context.append(cause.getMessage()).append(" -> ");
                }
                cause = cause.getCause();
            }
            String message = cause.getMessage() != null
                    ? cause.getMessage()
                    : cause.getClass().getSimpleName();
            return new ExitStatus(1, context + message);
        };
    }

    private final SpotifyHistoryMatcher spotifyHistoryMatcher;
    private final FeaturingArtistsFinder featuringArtistsFinder;
    private final SimilarArtistFinder similarArtistFinder;
    private final JellyfinGenreService genreService;

    public Commands(SpotifyHistoryMatcher spotifyHistoryMatcher,
                    FeaturingArtistsFinder featuringArtistsFinder,
                    SimilarArtistFinder similarArtistFinder,
                    JellyfinGenreService genreService) {
        this.spotifyHistoryMatcher = spotifyHistoryMatcher;
        this.featuringArtistsFinder = featuringArtistsFinder;
        this.similarArtistFinder = similarArtistFinder;
        this.genreService = genreService;
    }

    @Command(name = "compare-with-spotify-list",
            description = "Compare Spotify streaming history with the Jellyfin library and print matches.",
            exitStatusExceptionMapper = "exceptionMapper")
    public void compareWithSpotifyList() {
        spotifyHistoryMatcher.compareWithSpotifyList();
    }

    @Command(name = "find-featuring-artists",
            description = "List Jellyfin artists whose names contain feat./featuring/'vec, etc.",
            exitStatusExceptionMapper = "exceptionMapper")
    public void findFeaturingArtists() {
        featuringArtistsFinder.findFeaturingArtists();
    }

    @Command(name = "missing-artists-from-spotify",
            description = "List Spotify artists that are missing from the Jellyfin library.",
            exitStatusExceptionMapper = "exceptionMapper")
    public void missingArtistsFromSpotify() {
        spotifyHistoryMatcher.listMissingArtistsFromSpotify();
    }

    @Command(name = "list-artists-with-similar-names",
            description = "List Jellyfin artists whose names share at least five characters.",
            exitStatusExceptionMapper = "exceptionMapper")
    public void listArtistsWithSimilarNames() {
        similarArtistFinder.listSimilarArtists()
                .forEach(pair -> System.out.println(pair.first() + " <> " + pair.second()));
    }

    @Command(name = "list-genres",
            description = "List all music genres with album counts.",
            exitStatusExceptionMapper = "exceptionMapper")
    public void listGenres() {
        genreService.fetchGenresWithAlbumCount()
                .forEach(genre -> System.out.println(genre.name() + "(" + genre.id() + "): " + genre.albumCount() + " albums"));
    }

    @Command(name = "clear-empty-genres",
            description = "Delete all music genres that have 0 albums.",
            exitStatusExceptionMapper = "exceptionMapper")
    public void clearEmptyGenres() {
        var deleted = genreService.deleteEmptyGenres();
        if (deleted.isEmpty()) {
            System.out.println("No empty genres found.");
        } else {
            System.out.println("Deleted " + deleted.size() + " empty genre(s):");
            deleted.forEach(genre -> System.out.println("  - " + genre.name()));
        }
    }

    @Command(name = "move-genre",
            description = "Move all albums from one genre to another.",
            exitStatusExceptionMapper = "exceptionMapper")
    public void moveGenre(
            @Option(longName = "origin", shortName = 'o', required = true,
                    description = "The genre ID to move albums from") String origin,
            @Option(longName = "destination", shortName = 'd', required = true,
                    description = "The genre ID to move albums to") String destination) {
        var result = genreService.moveGenre(origin, destination);
        System.out.println("Found " + result.totalAlbums() + " album(s) in genre '" + result.originGenreName() + "'");
        System.out.println("Moved " + result.movedCount() + " album(s) to genre '" + result.destinationGenreName() + "'");
        if (!result.movedAlbums().isEmpty()) {
            System.out.println("Successfully moved:");
            result.movedAlbums().forEach(album -> System.out.println("  - " + album));
        }
        if (!result.failedAlbums().isEmpty()) {
            System.out.println("Failed to move:");
            result.failedAlbums().forEach(album -> System.out.println("  - " + album));
        }
    }
}
