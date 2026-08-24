package dev.limbonpc.common.protocol;

public final class Protocol {
    public static final int VERSION = 2;
    public static final int CONNECT = 0x01;
    public static final int TRANSFER_RESPONSE = 0x02;
    public static final int HEALTH_PING = 0x03;
    public static final int HEALTH_PONG = 0x04;
    public static final int MAX_NPC_ID_LENGTH = 32;
    public static final int MAX_SERVER_NAME_LENGTH = 64;
    public static final int MAX_DETAIL_LENGTH = 128;
    public static final int MAX_PACKET_BYTES = 768;

    private Protocol() {}
}
