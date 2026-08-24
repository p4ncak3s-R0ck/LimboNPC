package dev.limbonpc.common.protocol;

import java.util.Objects;

public record HealthPong(long requestId, String backend, int protocolVersion) implements ProtocolMessage {
    public HealthPong {
        Objects.requireNonNull(backend, "backend");
        if (backend.isEmpty() || backend.length() > Protocol.MAX_SERVER_NAME_LENGTH) throw new IllegalArgumentException("Backend name length is invalid");
    }
}
