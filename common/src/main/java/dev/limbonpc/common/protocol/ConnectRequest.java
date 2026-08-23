package dev.limbonpc.common.protocol;

import java.util.Objects;
import java.util.UUID;

public record ConnectRequest(UUID playerUuid, String npcId, String serverName) {
    public ConnectRequest {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(npcId, "npcId");
        Objects.requireNonNull(serverName, "serverName");
        if (npcId.isEmpty() || npcId.length() > Protocol.MAX_NPC_ID_LENGTH) {
            throw new IllegalArgumentException("NPC ID length is invalid");
        }
        if (serverName.isEmpty() || serverName.length() > Protocol.MAX_SERVER_NAME_LENGTH) {
            throw new IllegalArgumentException("Server name length is invalid");
        }
    }
}
