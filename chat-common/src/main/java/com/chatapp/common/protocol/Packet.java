package com.chatapp.common.protocol;

import java.util.List;
import java.util.Objects;

/**
 * One protocol message: a {@link PacketType} plus an ordered list of text fields.
 *
 * @param type   what the packet means
 * @param fields its payload, in the order documented on {@link PacketType}
 */
public record Packet(PacketType type, List<String> fields) {

    public Packet {
        Objects.requireNonNull(type, "type");
        fields = List.copyOf(fields);
    }

    public static Packet of(PacketType type, String... fields) {
        return new Packet(type, List.of(fields));
    }

    /** Returns the field at {@code index}, or an empty string if the sender omitted it. */
    public String field(int index) {
        return index < fields.size() ? fields.get(index) : "";
    }
}
