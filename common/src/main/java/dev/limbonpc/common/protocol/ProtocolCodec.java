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

    public static byte[] encode(ProtocolMessage message) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(Protocol.VERSION);
            if (message instanceof ConnectRequest request) {
                out.writeByte(Protocol.CONNECT);
                out.writeLong(request.requestId());
                writeUuid(out, request.playerUuid());
                writeString(out, request.npcId(), Protocol.MAX_NPC_ID_LENGTH, false);
                writeString(out, request.serverName(), Protocol.MAX_SERVER_NAME_LENGTH, false);
            } else if (message instanceof TransferResponse response) {
                out.writeByte(Protocol.TRANSFER_RESPONSE);
                out.writeLong(response.requestId());
                out.writeByte(response.result().ordinal());
                writeString(out, response.detail(), Protocol.MAX_DETAIL_LENGTH, true);
            } else if (message instanceof HealthPing ping) {
                out.writeByte(Protocol.HEALTH_PING);
                out.writeLong(ping.requestId());
                writeUuid(out, ping.playerUuid());
            } else if (message instanceof HealthPong pong) {
                out.writeByte(Protocol.HEALTH_PONG);
                out.writeLong(pong.requestId());
                writeString(out, pong.backend(), Protocol.MAX_SERVER_NAME_LENGTH, false);
                out.writeByte(pong.protocolVersion());
            } else {
                throw new IllegalArgumentException("Unsupported protocol message " + message.getClass().getName());
            }
            byte[] payload = bytes.toByteArray();
            if (payload.length > Protocol.MAX_PACKET_BYTES) throw new IllegalArgumentException("Encoded payload is too large");
            return payload;
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static ProtocolMessage decodeMessage(byte[] payload) throws ProtocolException {
        if (payload == null || payload.length == 0 || payload.length > Protocol.MAX_PACKET_BYTES) throw new ProtocolException("Invalid payload size");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            int version = in.readUnsignedByte();
            if (version != Protocol.VERSION) throw new ProtocolException("Unsupported protocol version: " + version);
            int type = in.readUnsignedByte();
            long requestId = in.readLong();
            ProtocolMessage message = switch (type) {
                case Protocol.CONNECT -> new ConnectRequest(requestId, readUuid(in),
                        readString(in, Protocol.MAX_NPC_ID_LENGTH, false),
                        readString(in, Protocol.MAX_SERVER_NAME_LENGTH, false));
                case Protocol.TRANSFER_RESPONSE -> new TransferResponse(requestId,
                        readResult(in.readUnsignedByte()), readString(in, Protocol.MAX_DETAIL_LENGTH, true));
                case Protocol.HEALTH_PING -> new HealthPing(requestId, readUuid(in));
                case Protocol.HEALTH_PONG -> new HealthPong(requestId,
                        readString(in, Protocol.MAX_SERVER_NAME_LENGTH, false), in.readUnsignedByte());
                default -> throw new ProtocolException("Unsupported message type: " + type);
            };
            if (in.available() != 0) throw new ProtocolException("Trailing payload data");
            return message;
        } catch (ProtocolException e) {
            throw e;
        } catch (EOFException e) {
            throw new ProtocolException("Truncated payload", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new ProtocolException("Malformed payload", e);
        }
    }

    public static ConnectRequest decode(byte[] payload) throws ProtocolException {
        ProtocolMessage message = decodeMessage(payload);
        if (!(message instanceof ConnectRequest request)) throw new ProtocolException("Payload is not a CONNECT request");
        return request;
    }

    private static TransferResult readResult(int ordinal) throws ProtocolException {
        TransferResult[] values = TransferResult.values();
        if (ordinal < 0 || ordinal >= values.length) throw new ProtocolException("Unknown transfer result: " + ordinal);
        return values[ordinal];
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits()); out.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }

    private static void writeString(DataOutputStream out, String value, int maxCharacters, boolean allowEmpty) throws IOException {
        if ((!allowEmpty && value.isEmpty()) || value.length() > maxCharacters) throw new IllegalArgumentException("Invalid string length");
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        if (data.length > maxCharacters * 4) throw new IllegalArgumentException("Encoded string is too large");
        writeVarInt(out, data.length); out.write(data);
    }

    private static String readString(DataInputStream in, int maxCharacters, boolean allowEmpty) throws IOException, ProtocolException {
        int byteLength = readVarInt(in);
        if (byteLength < 0 || (!allowEmpty && byteLength == 0) || byteLength > maxCharacters * 4) throw new ProtocolException("Invalid encoded string length");
        byte[] data = in.readNBytes(byteLength);
        if (data.length != byteLength) throw new EOFException();
        String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
        if ((!allowEmpty && value.isEmpty()) || value.length() > maxCharacters) throw new ProtocolException("Decoded string is too long");
        return value;
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) { out.writeByte((value & 0x7F) | 0x80); value >>>= 7; }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException, ProtocolException {
        int value = 0;
        for (int position = 0; position < 5; position++) {
            int current = in.readUnsignedByte(); value |= (current & 0x7F) << (position * 7);
            if ((current & 0x80) == 0) return value;
        }
        throw new ProtocolException("VarInt is too large");
    }

    public static final class ProtocolException extends Exception {
        public ProtocolException(String message) { super(message); }
        public ProtocolException(String message, Throwable cause) { super(message, cause); }
    }
}
