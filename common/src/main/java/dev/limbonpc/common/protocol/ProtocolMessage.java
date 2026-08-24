package dev.limbonpc.common.protocol;

public sealed interface ProtocolMessage permits ConnectRequest, TransferResponse, HealthPing, HealthPong {
    long requestId();
}
