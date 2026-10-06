package crqzycat.maintena.disguise;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Looks up the profile (canonical name + signed skin) of ANY Minecraft account by name, even if the
 * player never joined this server. Asks the Mojang API asynchronously (never blocks the server
 * thread) and caches results for a while, because the session server rate-limits profile requests.
 */
public final class SkinLookup {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final long CACHE_MILLIS = 30L * 60L * 1000L;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private record Cached(GameProfile profile, long time) {}

    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private SkinLookup() {}

    /** Minecraft account names: 1-16 characters of letters, digits and underscore. */
    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    /**
     * @return a future with the profile, or an empty Optional if no such account exists; completes
     *         exceptionally if the Mojang API can't be reached (offline, rate limit, ...)
     */
    public static CompletableFuture<Optional<GameProfile>> lookup(String name) {
        if (!isValidName(name)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String key = name.toLowerCase(Locale.ROOT);
        Cached cached = CACHE.get(key);

        if (cached != null && System.currentTimeMillis() - cached.time() < CACHE_MILLIS) {
            return CompletableFuture.completedFuture(Optional.of(cached.profile()));
        }

        HttpRequest idRequest = HttpRequest.newBuilder(
                        URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        return HTTP.sendAsync(idRequest, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    int status = response.statusCode();

                    if (status == 204 || status == 404) {
                        return CompletableFuture.completedFuture(Optional.<GameProfile>empty());
                    }

                    if (status != 200) {
                        return CompletableFuture.<Optional<GameProfile>>failedFuture(
                                new IOException("Mojang API returned HTTP " + status));
                    }

                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String undashed = json.get("id").getAsString();
                    String realName = json.get("name").getAsString();

                    HttpRequest profileRequest = HttpRequest.newBuilder(URI.create(
                                    "https://sessionserver.mojang.com/session/minecraft/profile/"
                                            + undashed + "?unsigned=false"))
                            .timeout(Duration.ofSeconds(8))
                            .GET()
                            .build();

                    return HTTP.sendAsync(profileRequest, HttpResponse.BodyHandlers.ofString())
                            .thenApply(profileResponse -> {
                                if (profileResponse.statusCode() != 200) {
                                    throw new CompletionException(new IOException(
                                            "Session server returned HTTP " + profileResponse.statusCode()));
                                }

                                GameProfile profile = parseProfile(
                                        JsonParser.parseString(profileResponse.body()).getAsJsonObject(),
                                        undashed,
                                        realName);

                                CACHE.put(key, new Cached(profile, System.currentTimeMillis()));
                                return Optional.of(profile);
                            });
                });
    }

    private static GameProfile parseProfile(JsonObject json, String undashed, String fallbackName) {
        String name = json.has("name") ? json.get("name").getAsString() : fallbackName;
        GameProfile profile = new GameProfile(parseUuid(undashed), name);

        if (json.has("properties")) {
            JsonArray properties = json.getAsJsonArray("properties");

            for (JsonElement element : properties) {
                JsonObject property = element.getAsJsonObject();
                String propertyName = property.get("name").getAsString();
                String value = property.get("value").getAsString();
                String signature = property.has("signature") ? property.get("signature").getAsString() : null;

                profile.properties().put(propertyName, new Property(propertyName, value, signature));
            }
        }

        return profile;
    }

    private static UUID parseUuid(String undashed) {
        return UUID.fromString(undashed.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5"));
    }
}
