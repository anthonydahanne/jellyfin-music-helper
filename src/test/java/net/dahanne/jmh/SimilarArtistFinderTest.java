package net.dahanne.jmh;

import net.dahanne.jmh.JellyfinArtistService.Artist;
import net.dahanne.jmh.SimilarArtistFinder.SimilarArtistPair;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimilarArtistFinderTest {

    @Test
    void findsArtistsSharingAtLeastFiveCharacters() {
        net.dahanne.jmh.config.JellyfinProperties properties = new net.dahanne.jmh.config.JellyfinProperties();
        net.dahanne.jmh.config.JellyfinProperties.SimilarArtist similarArtist = new net.dahanne.jmh.config.JellyfinProperties.SimilarArtist();
        similarArtist.setMinCommonLength(5);
        properties.setSimilarArtist(similarArtist);

        SimilarArtistFinder finder = new SimilarArtistFinder(null, properties);
        List<Artist> artists = List.of(
                new Artist("1", "Beyonce"),
                new Artist("2", "Beyoncé Knowles"),
                new Artist("3", "Gang Starr"),
                new Artist("4", "Gangstarr"),
                new Artist("5", "Totally Different")
        );

        List<SimilarArtistPair> pairs = finder.findSimilarArtists(artists);

        assertThat(pairs).contains(
                new SimilarArtistPair("Beyonce", "Beyoncé Knowles"),
                new SimilarArtistPair("Gang Starr", "Gangstarr")
        );
        assertThat(pairs).hasSize(2);
    }
}
