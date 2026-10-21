package com.chatapp.server.model;

/**
 * Observer through which {@link ChatServer} reports what happens, without knowing who listens.
 *
 * <p>This interface is the only link between the model and any user interface. Callbacks arrive
 * on the server's network threads (virtual threads), so a GUI implementation must hand them over
 * to its own UI thread.
 */
public interface ServerListener {

    void onLog(LogType type, String message);

    void onClientConnected(String username, boolean readOnly);

    void onClientDisconnected(String username);
}
