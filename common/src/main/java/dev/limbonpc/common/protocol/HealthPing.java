package dev.limbonpc.common.protocol;

import java.util.Objects;
import java.util.UUID;

public record HealthPing(long requestId, UUID playerUuid) implements ProtocolMessage {
    public HealthPing { Objects.requireNonNull(playerUuid, "playerUuid"); }
}
