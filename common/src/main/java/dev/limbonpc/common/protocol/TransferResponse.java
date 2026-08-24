package dev.limbonpc.common.protocol;

import java.util.Objects;

public record TransferResponse(long requestId, TransferResult result, String detail) implements ProtocolMessage {
    public TransferResponse {
        Objects.requireNonNull(result, "result");
        detail = detail == null ? "" : detail;
        if (detail.length() > Protocol.MAX_DETAIL_LENGTH) throw new IllegalArgumentException("Response detail is too long");
    }
}
