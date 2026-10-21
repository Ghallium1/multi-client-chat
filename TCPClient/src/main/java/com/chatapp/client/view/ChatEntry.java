package com.chatapp.client.view;

import java.util.List;

/**
 * One row in the conversation, already prepared for display (a view model). The controller
 * creates these from model events; {@link MessageCell} draws them.
 */
public record ChatEntry(Kind kind, String time, String sender, String text, List<String> users) {

    public enum Kind {
        /** Message written by this user (right-aligned bubble). */
        OWN,
        /** Message written by someone else (left-aligned bubble). */
        OTHER,
        /** System notice: joins, leaves, connection status. */
        SYSTEM,
        /** Something was refused. */
        ERROR,
        /** Reply to the allUsers command. */
        USERS
    }

    public ChatEntry {
        users = users == null ? List.of() : List.copyOf(users);
    }

    public static ChatEntry own(String time, String text) {
        return new ChatEntry(Kind.OWN, time, "You", text, null);
    }

    public static ChatEntry other(String time, String sender, String text) {
        return new ChatEntry(Kind.OTHER, time, sender, text, null);
    }

    public static ChatEntry system(String time, String text) {
        return new ChatEntry(Kind.SYSTEM, time, null, text, null);
    }

    public static ChatEntry error(String time, String text) {
        return new ChatEntry(Kind.ERROR, time, null, text, null);
    }

    public static ChatEntry users(String time, List<String> users) {
        return new ChatEntry(Kind.USERS, time, null, null, users);
    }
}
