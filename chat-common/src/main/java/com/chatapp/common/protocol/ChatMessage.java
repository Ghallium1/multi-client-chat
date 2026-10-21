package com.chatapp.common.protocol;

/**
 * A chat message after the server has stamped it with the sender and the time.
 *
 * @param time   when the server received it, formatted as {@code HH:mm}
 * @param sender username of the author
 * @param text   message body (may contain newlines)
 */
public record ChatMessage(String time, String sender, String text) {

    public static ChatMessage fromPacket(Packet packet) {
        if (packet.type() != PacketType.CHAT) {
            throw new IllegalArgumentException("Not a CHAT packet: " + packet.type());
        }
        return new ChatMessage(packet.field(0), packet.field(1), packet.field(2));
    }

    public Packet toPacket() {
        return Packet.of(PacketType.CHAT, time, sender, text);
    }

    /** Human readable form, e.g. {@code [14:05] Alice: hello}. */
    public String format() {
        return "[" + time + "] " + sender + ": " + text;
    }
}
