package dev.limbonpc.limbo.npc;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record NpcDefinition(String id, boolean enabled, String server, String displayName,
                            NpcLocation location, NpcSkin skin, List<String> hologram) {
    public static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_-]{1,32}$");

    public NpcDefinition {
        id = Objects.requireNonNull(id).toLowerCase(Locale.ROOT);
        if (!ID_PATTERN.matcher(id).matches()) throw new IllegalArgumentException("Invalid NPC ID: " + id);
        server = Objects.requireNonNull(server);
        if (server.isBlank() || server.length() > 64) throw new IllegalArgumentException("Invalid server name");
        displayName = displayName == null ? id : displayName;
        Objects.requireNonNull(location);
        skin = skin == null ? NpcSkin.none() : skin;
        hologram = hologram == null ? List.of() : List.copyOf(hologram);
    }

    public NpcDefinition withLocation(NpcLocation value) { return new NpcDefinition(id, enabled, server, displayName, value, skin, hologram); }
    public NpcDefinition withServer(String value) { return new NpcDefinition(id, enabled, value, displayName, location, skin, hologram); }
    public NpcDefinition withDisplayName(String value) { return new NpcDefinition(id, enabled, server, value, location, skin, hologram); }
    public NpcDefinition withSkin(NpcSkin value) { return new NpcDefinition(id, enabled, server, displayName, location, value, hologram); }
    public NpcDefinition withHologram(List<String> value) { return new NpcDefinition(id, enabled, server, displayName, location, skin, value); }
}
