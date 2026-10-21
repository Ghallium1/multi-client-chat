package com.chatapp.common.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts {@link Packet}s to and from single lines of text.
 *
 * <p>Wire format: {@code TYPE<TAB>field1<TAB>field2...} terminated by a newline. Inside a field,
 * backslash, tab, carriage return and newline are escaped ({@code \\ \t \r \n}), so a field can
 * never break the framing. That lets users send multi-line messages over a line-based stream.
 */
public final class Protocol {

    private static final char SEPARATOR = '\t';

    private Protocol() {
    }

    public static String encode(Packet packet) {
        StringBuilder line = new StringBuilder(packet.type().name());
        for (String field : packet.fields()) {
            line.append(SEPARATOR).append(escape(field));
        }
        return line.toString();
    }

    public static Packet decode(String line) {
        if (line == null || line.isBlank()) {
            throw new ProtocolException("Received an empty packet");
        }
        String[] parts = line.split(String.valueOf(SEPARATOR), -1);

        PacketType type;
        try {
            type = PacketType.valueOf(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new ProtocolException("Unknown packet type: " + parts[0]);
        }

        List<String> fields = new ArrayList<>(parts.length - 1);
        for (int i = 1; i < parts.length; i++) {
            fields.add(unescape(parts[i]));
        }
        return new Packet(type, fields);
    }

    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 == value.length()) {
                out.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case '\\' -> out.append('\\');
                case 't' -> out.append('\t');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                default -> out.append('\\').append(next);
            }
        }
        return out.toString();
    }
}
