package dev.limbonpc.common.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProtocolCodecTest {
    @Test void roundTrips() throws Exception {
        ConnectRequest expected = new ConnectRequest(UUID.randomUUID(), "survival", "survival-01");
        assertEquals(expected, ProtocolCodec.decode(ProtocolCodec.encode(expected)));
    }

    @Test void rejectsUnsupportedVersionAndTrailingBytes() {
        byte[] valid = ProtocolCodec.encode(new ConnectRequest(UUID.randomUUID(), "npc", "server"));
        byte[] version = valid.clone(); version[0] = 2;
        assertThrows(ProtocolCodec.ProtocolException.class, () -> ProtocolCodec.decode(version));
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        assertThrows(ProtocolCodec.ProtocolException.class, () -> ProtocolCodec.decode(trailing));
    }

    @Test void rejectsTruncationAndOversize() {
        byte[] valid = ProtocolCodec.encode(new ConnectRequest(UUID.randomUUID(), "npc", "server"));
        assertThrows(ProtocolCodec.ProtocolException.class, () -> ProtocolCodec.decode(Arrays.copyOf(valid, valid.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> new ConnectRequest(UUID.randomUUID(), "x".repeat(33), "server"));
        assertThrows(ProtocolCodec.ProtocolException.class, () -> ProtocolCodec.decode(new byte[Protocol.MAX_PACKET_BYTES + 1]));
    }
}
