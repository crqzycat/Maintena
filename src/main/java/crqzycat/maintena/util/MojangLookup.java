package crqzycat.maintena.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Prüft bei Mojang, ob es einen Minecraft-Account mit genau diesem Namen gibt (auch wenn der
 * Spieler nie auf dem Server war) und liefert die exakte Schreibweise. Läuft asynchron, der
 * Server-Thread wird nicht blockiert. Das ist eine Abfrage nach dem ganzen Namen, keine Suche
 * nach Namensteilen.
 */
public final class MojangLookup {

    private static final String URL = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private MojangLookup() {
    }

    public enum Status {
        FOUND, NOT_FOUND, ERROR
    }

    public record Result(Status status, String name) {
    }

    public static CompletableFuture<Result> lookup(String name) {
        if (!PlayerNames.isValidName(name)) {
            return CompletableFuture.completedFuture(new Result(Status.NOT_FOUND, null));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(URL + name))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .GET()
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(MojangLookup::parse)
                .exceptionally(error -> new Result(Status.ERROR, null));
    }

    private static Result parse(HttpResponse<String> response) {
        int code = response.statusCode();

        if (code == 200) {
            try {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                if (json.has("name")) {
                    return new Result(Status.FOUND, json.get("name").getAsString());
                }
            } catch (Exception ignored) {
                // fällt unten auf ERROR
            }

            return new Result(Status.ERROR, null);
        }

        if (code == 404 || code == 204) {
            return new Result(Status.NOT_FOUND, null);
        }

        return new Result(Status.ERROR, null);
    }
}
