package net.dahanne.jmh.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "jellyfin")
public class JellyfinProperties {

    private String baseUrl;
    private String apiToken;
    private SimilarArtist similarArtist = new SimilarArtist();
    private FeaturingArtists featuringArtists = new FeaturingArtists();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public SimilarArtist getSimilarArtist() {
        return similarArtist;
    }

    public void setSimilarArtist(SimilarArtist similarArtist) {
        this.similarArtist = similarArtist;
    }

    public FeaturingArtists getFeaturingArtists() {
        return featuringArtists;
    }

    public void setFeaturingArtists(FeaturingArtists featuringArtists) {
        this.featuringArtists = featuringArtists;
    }

    public static class SimilarArtist {
        private int minCommonLength = 5;

        public int getMinCommonLength() {
            return minCommonLength;
        }

        public void setMinCommonLength(int minCommonLength) {
            this.minCommonLength = minCommonLength;
        }
    }

    public static class FeaturingArtists {
        private List<String> markers = new ArrayList<>();

        public List<String> getMarkers() {
            return markers;
        }

        public void setMarkers(List<String> markers) {
            this.markers = markers;
        }
    }
}
