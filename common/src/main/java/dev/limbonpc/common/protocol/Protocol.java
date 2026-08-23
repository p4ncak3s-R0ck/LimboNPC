package dev.limbonpc.common.protocol;

public final class Protocol {
    public static final int VERSION = 1;
    public static final int CONNECT = 0x01;
    public static final int MAX_NPC_ID_LENGTH = 32;
    public static final int MAX_SERVER_NAME_LENGTH = 64;
    public static final int MAX_PACKET_BYTES = 256;

    private Protocol() {}
}
