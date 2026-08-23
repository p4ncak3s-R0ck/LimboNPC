package dev.limbonpc.limbo.skin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.limbonpc.limbo.npc.NpcSkin;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public final class SkinService {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private final Path cacheDirectory;
    private final Gson gson = new Gson();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public SkinService(Path cacheDirectory) { this.cacheDirectory = cacheDirectory; }

    public NpcSkin cached(String username) {
        if (!USERNAME.matcher(username).matches()) return null;
        Path file = cacheDirectory.resolve(username.toLowerCase() + ".json");
        if (Files.notExists(file)) return null;
        try {
            Cache cache = gson.fromJson(Files.readString(file), Cache.class);
            return NpcSkin.username(cache.username, cache.value, cache.signature);
        } catch (Exception e) { return null; }
    }

    public CompletableFuture<NpcSkin> resolve(String username) {
        if (!USERNAME.matcher(username).matches()) return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid Minecraft username"));
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject profile = getJson("https://api.mojang.com/users/profiles/minecraft/" + username);
                if (profile == null || !profile.has("id")) throw new IOException("Minecraft account not found");
                String id = profile.get("id").getAsString();
                JsonObject session = getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false");
                if (session == null || !session.has("properties") || session.getAsJsonArray("properties").size() == 0) throw new IOException("Skin data unavailable");
                JsonObject property = session.getAsJsonArray("properties").get(0).getAsJsonObject();
                NpcSkin skin = NpcSkin.username(username, property.get("value").getAsString(), property.get("signature").getAsString());
                Files.createDirectories(cacheDirectory);
                Files.writeString(cacheDirectory.resolve(username.toLowerCase() + ".json"), gson.toJson(new Cache(username, skin.value(), skin.signature(), Instant.now().toString())));
                return skin;
            } catch (Exception e) { throw new RuntimeException(e.getMessage(), e); }
        });
    }

    private JsonObject getJson(String uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(10)).header("User-Agent", "LimboNPC/1.0").build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Mojang API returned HTTP " + response.statusCode());
        return gson.fromJson(response.body(), JsonObject.class);
    }

    private record Cache(String username, String value, String signature, String resolvedAt) {}
}
