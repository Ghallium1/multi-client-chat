package com.chatapp.common.protocol;

import java.util.Optional;

/** Special words a user can type instead of a chat message. Matching ignores case and surrounding spaces. */
public enum ChatCommand {

    /** Ask the server for the list of everyone currently connected. */
    ALL_USERS("allUsers"),
    /** Leave the chat. */
    END("end"),
    /** Leave the chat. */
    BYE("bye");

    private final String keyword;

    ChatCommand(String keyword) {
        this.keyword = keyword;
    }

    public String keyword() {
        return keyword;
    }

    public boolean isDisconnect() {
        return this == END || this == BYE;
    }

    public static Optional<ChatCommand> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String trimmed = input.strip();
        for (ChatCommand command : values()) {
            if (command.keyword.equalsIgnoreCase(trimmed)) {
                return Optional.of(command);
            }
        }
        return Optional.empty();
    }
}
