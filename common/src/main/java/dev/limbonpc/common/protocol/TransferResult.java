package dev.limbonpc.common.protocol;

public enum TransferResult {
    DISPATCHED,
    UNKNOWN_SERVER,
    RATE_LIMITED,
    PLAYER_MISMATCH,
    COMMAND_FAILED,
    MALFORMED_REQUEST,
    INTERNAL_ERROR
}
