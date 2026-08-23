package dev.limbonpc.common.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ProtocolCodec {
    private ProtocolCodec() {}

    public static byte[] encode(ConnectRequest request) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(Protocol.VERSION);
            out.writeByte(Protocol.CONNECT);
            out.writeLong(request.playerUuid().getMostSignificantBits());
            out.writeLong(request.playerUuid().getLeastSignificantBits());
            writeString(out, request.npcId(), Protocol.MAX_NPC_ID_LENGTH);
            writeString(out, request.serverName(), Protocol.MAX_SERVER_NAME_LENGTH);
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static ConnectRequest decode(byte[] payload) throws ProtocolException {
        if (payload == null || payload.length == 0 || payload.length > Protocol.MAX_PACKET_BYTES) {
            throw new ProtocolException("Invalid payload size");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            int version = in.readUnsignedByte();
            if (version != Protocol.VERSION) throw new ProtocolException("Unsupported protocol version: " + version);
            int type = in.readUnsignedByte();
            if (type != Protocol.CONNECT) throw new ProtocolException("Unsupported message type: " + type);
            UUID uuid = new UUID(in.readLong(), in.readLong());
            String npcId = readString(in, Protocol.MAX_NPC_ID_LENGTH);
            String server = readString(in, Protocol.MAX_SERVER_NAME_LENGTH);
            if (in.available() != 0) throw new ProtocolException("Trailing payload data");
            return new ConnectRequest(uuid, npcId, server);
        } catch (ProtocolException e) {
            throw e;
        } catch (EOFException e) {
            throw new ProtocolException("Truncated payload", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new ProtocolException("Malformed payload", e);
        }
    }

    private static void writeString(DataOutputStream out, String value, int maxCharacters) throws IOException {
        if (value.isEmpty() || value.length() > maxCharacters) throw new IllegalArgumentException("Invalid string length");
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        if (data.length > maxCharacters * 4) throw new IllegalArgumentException("Encoded string is too large");
        writeVarInt(out, data.length);
        out.write(data);
    }

    private static String readString(DataInputStream in, int maxCharacters) throws IOException, ProtocolException {
        int byteLength = readVarInt(in);
        if (byteLength <= 0 || byteLength > maxCharacters * 4) throw new ProtocolException("Invalid encoded string length");
        byte[] data = in.readNBytes(byteLength);
        if (data.length != byteLength) throw new EOFException();
        String value = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data)).toString();
        if (value.isEmpty() || value.length() > maxCharacters) throw new ProtocolException("Decoded string is too long");
        return value;
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException, ProtocolException {
        int value = 0;
        for (int position = 0; position < 5; position++) {
            int current = in.readUnsignedByte();
            value |= (current & 0x7F) << (position * 7);
            if ((current & 0x80) == 0) return value;
        }
        throw new ProtocolException("VarInt is too large");
    }

    public static final class ProtocolException extends Exception {
        public ProtocolException(String message) { super(message); }
        public ProtocolException(String message, Throwable cause) { super(message, cause); }
    }
}
