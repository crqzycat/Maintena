package crqzycat.maintena.disguise;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Looks up the profile (canonical name + signed skin) of ANY Minecraft account by name, even if the
 * player never joined this server. Runs on a background thread (never blocks the server thread) and
 * caches results for a while, because the session server rate-limits profile requests.
 *
 * <p>Uses plain {@link HttpURLConnection} (java.base) so it also works on trimmed Java runtimes that
 * don't ship the {@code java.net.http} module.
 */
public final class SkinLookup {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final long CACHE_MILLIS = 30L * 60L * 1000L;

    /** Tried in this order for name -> UUID. */
    private static final String[] NAME_ENDPOINTS = {
            "https://api.minecraftservices.com/minecraft/profile/lookup/name/",
            "https://api.mojang.com/users/profiles/minecraft/"
    };

    private static final String PROFILE_ENDPOINT = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private static final Executor EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "Maintena-SkinLookup");
        thread.setDaemon(true);
        return thread;
    });

    private record Cached(GameProfile profile, long time) {}

    private record Response(int status, String body) {}

    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private SkinLookup() {}

    /** Minecraft account names: 1-16 characters of letters, digits and underscore. */
    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    /**
     * @return a future with the profile, or an empty Optional if no such account exists; completes
     *         exceptionally (with a readable message) if Mojang can't be reached or rate-limits us
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

        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<GameProfile> result = fetch(name);
                result.ifPresent(profile -> CACHE.put(key, new Cached(profile, System.currentTimeMillis())));
                return result;
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, EXECUTOR);
    }

    private static Optional<GameProfile> fetch(String name) throws IOException {
        JsonObject idJson = null;
        IOException lastError = null;

        for (String endpoint : NAME_ENDPOINTS) {
            try {
                Response response = get(endpoint + name);

                if (response.status() == 200) {
                    idJson = JsonParser.parseString(response.body()).getAsJsonObject();
                    break;
                }

                if (response.status() == 204 || response.status() == 404) {
                    return Optional.empty(); // no such account
                }

                lastError = new IOException("HTTP " + response.status() + " from " + URI.create(endpoint).getHost());
            } catch (IOException e) {
                lastError = e;
            }
        }

        if (idJson == null) {
            throw lastError != null ? lastError : new IOException("no response from Mojang");
        }

        String undashed = idJson.get("id").getAsString();
        String realName = idJson.get("name").getAsString();

        Response profileResponse = get(PROFILE_ENDPOINT + undashed + "?unsigned=false");

        if (profileResponse.status() == 429) {
            throw new IOException("rate limited by Mojang (HTTP 429), try again in a minute");
        }

        if (profileResponse.status() != 200) {
            throw new IOException("HTTP " + profileResponse.status() + " from sessionserver.mojang.com");
        }

        return Optional.of(parseProfile(
                JsonParser.parseString(profileResponse.body()).getAsJsonObject(), undashed, realName));
    }

    private static Response get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();

        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Maintena/1.0");
            connection.setRequestProperty("Accept", "application/json");

            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = "";

            if (stream != null) {
                try (stream) {
                    body = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }

            return new Response(status, body);
        } finally {
            connection.disconnect();
        }
    }

    private static GameProfile parseProfile(JsonObject json, String undashed, String fallbackName) {
        String name = json.has("name") ? json.get("name").getAsString() : fallbackName;

        // GameProfile / PropertyMap are immutable: collect the properties first, then build.
        ImmutableMultimap.Builder<String, Property> properties = ImmutableMultimap.builder();

        if (json.has("properties")) {
            for (JsonElement element : json.getAsJsonArray("properties")) {
                JsonObject property = element.getAsJsonObject();
                String propertyName = property.get("name").getAsString();
                String value = property.get("value").getAsString();
                String signature = property.has("signature") ? property.get("signature").getAsString() : null;

                properties.put(propertyName, new Property(propertyName, value, signature));
            }
        }

        return new GameProfile(parseUuid(undashed), name, new PropertyMap(properties.build()));
    }

    private static UUID parseUuid(String undashed) {
        return UUID.fromString(undashed.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5"));
    }
}
