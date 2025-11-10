# Jellyfin Music Helper

A Spring Shell app that helps you manage your Jellyfin music library.

## Prerequisites

- Java 25
- Jellyfin server URL and API token
- Exported `StreamingHistory.json` (Spotify)

## Running the app

```
./mvnw spring-boot:run
```

Then use any of the shell commands below. If you want to suppress JLine’s
warning about native access on Java 25+, launch the JVM with
`--enable-native-access=ALL-UNNAMED`, e.g.:

```
JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED" ./mvnw spring-boot:run
```

### Commands

1. `compare-with-spotify-list`
   - Reads `StreamingHistory.json` and looks up each track in Jellyfin (using the configured base URL/token).
   - Output format: `Artist - Track <count-as-emoji> ✅/❌ <album or reason>`.
2. `find-featuring-artists`
   - Lists all Jellyfin artists whose names contain any configured marker (`jellyfin.featuring-artists.markers`).
   - Shows aggregated album titles for each matching artist.
3. `list-artists-with-similar-names`
   - Finds pairs of artists whose names share at least `jellyfin.similar-artist.min-common-length` characters (default 5).
   - Normalizes by removing accents/punctuation before comparing.

## Configuration

Primary settings live in `src/main/resources/application.yml`. Override them via env vars, profiles, or additional YAML files.

| Property | Env. Var Property | Description |
|----------|-------------------|-------------|
| `streaming.history.file` | `STREAMING_HISTORY_FILE` | Path to the exported Spotify history JSON. |
| `jellyfin.base-url` | `JELLYFIN_BASE_URL` | Base URL of the Jellyfin server (required). |
| `jellyfin.api-token` | `JELLYFIN_API_TOKEN` | Jellyfin API token (required if auth is enforced). |
| `jellyfin.featuring-artists.markers` | `JELLYFIN_FEATURING_ARTISTS_MARKERS` | Comma-separated substrings used to detect “featuring” artists. |
| `jellyfin.similar-artist.min-common-length` | `JELLYFIN_SIMILAR_ARTIST_MIN_COMMON_LENGTH` | Required substring length for the similar-name check. |

To run non-interactively, pass any command as an argument:

```
java -jar target/jellyfin-music-helper-0.0.1-SNAPSHOT.jar compare-with-spotify-list
```
