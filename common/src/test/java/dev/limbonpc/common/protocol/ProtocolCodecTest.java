package dev.limbonpc.common.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProtocolCodecTest {
    @Test void roundTripsEveryMessage() throws Exception {
        UUID uuid = UUID.randomUUID();
        List<ProtocolMessage> messages = List.of(
                new ConnectRequest(1L, uuid, "survival", "survival-01"),
                new TransferResponse(1L, TransferResult.DISPATCHED, "survival-01"),
                new TransferResponse(2L, TransferResult.RATE_LIMITED, ""),
                new HealthPing(3L, uuid),
                new HealthPong(3L, "limbo", Protocol.VERSION));
        for (ProtocolMessage expected : messages) assertEquals(expected, ProtocolCodec.decodeMessage(ProtocolCodec.encode(expected)));
    }

    @Test void rejectsUnsupportedVersionTypeAndTrailingBytes() {
        byte[] valid = ProtocolCodec.encode(new ConnectRequest(1, UUID.randomUUID(), "npc", "server"));
        byte[] version = valid.clone(); version[0] = 1;
        assertThrows(ProtocolCodec.ProtocolException.class, () -> ProtocolCodec.decodeMessage(version));
        byte[] type = valid.clone(); type[1] = 99;
        assertThrows(ProtocolCodec.ProtocolException.class, () -> ProtocolCodec.decodeMessage(type));
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        assertThrows(ProtocolCodec.ProtocolException.class, () -> ProtocolCodec.decodeMessage(trailing));
    }

    @Test void rejectsTruncationOversizeAndWrongMessageKind() {
        byte[] valid = ProtocolCodec.encode(new ConnectRequest(1, UUID.randomUUID(), "npc", "server"));
        assertThrows(ProtocolCodec.ProtocolException.class, () -> ProtocolCodec.decodeMessage(Arrays.copyOf(valid, valid.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> new ConnectRequest(1, UUID.randomUUID(), "x".repeat(33), "server"));
        assertThrows(ProtocolCodec.ProtocolException.class, () -> ProtocolCodec.decodeMessage(new byte[Protocol.MAX_PACKET_BYTES + 1]));
        assertThrows(ProtocolCodec.ProtocolException.class, () -> ProtocolCodec.decode(ProtocolCodec.encode(new HealthPing(2, UUID.randomUUID()))));
    }
}
