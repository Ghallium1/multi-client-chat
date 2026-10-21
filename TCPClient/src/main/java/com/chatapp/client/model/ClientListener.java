package com.chatapp.client.model;

import com.chatapp.common.protocol.ChatMessage;

import java.util.List;

/**
 * Observer through which {@link ChatClient} reports what the server sent. This is the only link
 * between the client model and any user interface.
 *
 * <p>Callbacks arrive on the client's background listener thread, so a GUI implementation must
 * hand them over to its own UI thread.
 */
public interface ClientListener {

    /** The server accepted the handshake. {@code readOnly} is true when no username was given. */
    void onWelcome(String username, boolean readOnly);

    /** The server refused the handshake (e.g. the username is taken); the connection is closed. */
    void onRejected(String reason);

    void onChatMessage(ChatMessage message);

    /** A system notice such as "Bob joined the chat". */
    void onInfo(String time, String text);

    /** Reply to the {@code allUsers} command. */
    void onUserList(List<String> users);

    /** Something was not allowed, e.g. sending a message in READ-ONLY MODE. */
    void onError(String message);

    /** The connection is over: the user left, the server stopped, or the network failed. */
    void onDisconnected(String reason);
}
