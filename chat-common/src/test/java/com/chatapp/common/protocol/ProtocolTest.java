package com.chatapp.common.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolTest {

    @Test
    void encodesTypeAndFieldsSeparatedByTabs() {
        assertEquals("CHAT\t14:05\tAlice\thello",
                Protocol.encode(Packet.of(PacketType.CHAT, "14:05", "Alice", "hello")));
    }

    @Test
    void encodesPacketWithoutFields() {
        assertEquals("BYE", Protocol.encode(Packet.of(PacketType.BYE)));
        assertEquals(List.of(), Protocol.decode("BYE").fields());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "plain text",
            "line one\nline two",
            "tab\there",
            "back\\slash and \\n literal",
            "trailing backslash \\",
            "windows\r\nnewline",
            "émojis 👋 and accents çà",
            ""
    })
    void roundTripsAnyText(String text) {
        Packet original = Packet.of(PacketType.MESSAGE, text);
        String line = Protocol.encode(original);

        assertFalse(line.contains("\n"), "an encoded packet must stay on one line");
        assertEquals(original, Protocol.decode(line));
    }

    @Test
    void keepsEmptyFields() {
        Packet decoded = Protocol.decode("HELLO\t");
        assertEquals(PacketType.HELLO, decoded.type());
        assertEquals("", decoded.field(0));
    }

    @Test
    void missingFieldsReadAsEmpty() {
        assertEquals("", Protocol.decode("WELCOME").field(1));
    }

    @Test
    void rejectsUnknownType() {
        assertThrows(ProtocolException.class, () -> Protocol.decode("HACK\tme"));
    }

    @Test
    void rejectsBlankLine() {
        assertThrows(ProtocolException.class, () -> Protocol.decode("  "));
    }

    @Test
    void chatMessageConvertsToAndFromPacket() {
        ChatMessage message = new ChatMessage("09:30", "Bob", "hi\nthere");
        assertEquals(message, ChatMessage.fromPacket(Protocol.decode(Protocol.encode(message.toPacket()))));
        assertEquals("[09:30] Bob: hi\nthere", message.format());
    }
}
