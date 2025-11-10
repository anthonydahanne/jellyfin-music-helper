package net.dahanne.jmh;

import net.dahanne.jmh.config.JellyfinProperties;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;

@Component
public class SimilarArtistFinder {

    private final JellyfinArtistService artistService;
    private final int minCommonLength;

    public SimilarArtistFinder(JellyfinArtistService artistService,
                               JellyfinProperties properties) {
        this.artistService = artistService;
        int configured = properties.getSimilarArtist().getMinCommonLength();
        if (configured <= 0) {
            configured = 5;
        }
        this.minCommonLength = configured;
    }

    public List<SimilarArtistPair> listSimilarArtists() {
        List<JellyfinArtistService.Artist> artists = artistService.fetchArtists();
        return findSimilarArtists(artists);
    }

    List<SimilarArtistPair> findSimilarArtists(List<JellyfinArtistService.Artist> artists) {
        List<NormalizedArtist> normalized = new ArrayList<>();
        for (JellyfinArtistService.Artist artist : artists) {
            String normalizedName = normalize(artist.name());
            Set<String> tokens = tokens(normalizedName);
            normalized.add(new NormalizedArtist(artist.name(), normalizedName, tokens));
        }

        List<SimilarArtistPair> pairs = new ArrayList<>();
        for (int i = 0; i < normalized.size(); i++) {
            NormalizedArtist left = normalized.get(i);
            if (left.tokens().isEmpty()) {
                continue;
            }
            for (int j = i + 1; j < normalized.size(); j++) {
                NormalizedArtist right = normalized.get(j);
                if (right.tokens().isEmpty()) {
                    continue;
                }
                if (hasCommonTokens(left.tokens(), right.tokens())) {
                    String first = left.originalName();
                    String second = right.originalName();
                    if (first.equalsIgnoreCase(second)) {
                        continue;
                    }
                    if (first.compareToIgnoreCase(second) > 0) {
                        String tmp = first;
                        first = second;
                        second = tmp;
                    }
                    SimilarArtistPair pair = new SimilarArtistPair(first, second);
                    if (!pairs.contains(pair)) {
                        pairs.add(pair);
                    }
                }
            }
        }

        pairs.sort(Comparator
                .comparing(SimilarArtistPair::first, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(SimilarArtistPair::second, String.CASE_INSENSITIVE_ORDER));
        return pairs;
    }

    private boolean hasCommonTokens(Set<String> left, Set<String> right) {
        Set<String> smaller = left.size() <= right.size() ? left : right;
        Set<String> larger = smaller == left ? right : left;
        for (String token : smaller) {
            if (larger.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        String normalized = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.replaceAll("[^a-z0-9]", "");
    }

    private Set<String> tokens(String normalized) {
        Set<String> tokens = new HashSet<>();
        if (normalized.length() < minCommonLength) {
            return tokens;
        }
        for (int i = 0; i <= normalized.length() - minCommonLength; i++) {
            tokens.add(normalized.substring(i, i + minCommonLength));
        }
        return tokens;
    }

    private record NormalizedArtist(String originalName, String normalizedName, Set<String> tokens) {
    }

    public record SimilarArtistPair(String first, String second) {
    }
}
