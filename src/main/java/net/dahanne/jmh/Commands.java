package net.dahanne.jmh;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class Commands {

    private final SpotifyHistoryMatcher spotifyHistoryMatcher;
    private final FeaturingArtistsFinder featuringArtistsFinder;
    private final SimilarArtistFinder similarArtistFinder;

    public Commands(SpotifyHistoryMatcher spotifyHistoryMatcher,
                    FeaturingArtistsFinder featuringArtistsFinder,
                    SimilarArtistFinder similarArtistFinder) {
        this.spotifyHistoryMatcher = spotifyHistoryMatcher;
        this.featuringArtistsFinder = featuringArtistsFinder;
        this.similarArtistFinder = similarArtistFinder;
    }

    @ShellMethod(key = "compare-with-spotify-list",
            value = "Compare Spotify streaming history with the Jellyfin library and print matches.")
    public void compareWithSpotifyList() {
        spotifyHistoryMatcher.compareWithSpotifyList();
    }

    @ShellMethod(key = "find-featuring-artists",
            value = "List Jellyfin artists whose names contain feat./featuring/'vec, etc.")
    public void findFeaturingArtists() {
        featuringArtistsFinder.findFeaturingArtists();
    }

    @ShellMethod(key = "list-artists-with-similar-names",
            value = "List Jellyfin artists whose names share at least five characters.")
    public void listArtistsWithSimilarNames() {
        similarArtistFinder.listSimilarArtists()
                .forEach(pair -> System.out.println(pair.first() + " <> " + pair.second()));
    }
}
