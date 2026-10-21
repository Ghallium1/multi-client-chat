package com.chatapp.server.model;

/** Category of a server log entry, so a view can style entries without parsing their text. */
public enum LogType {
    /** Server lifecycle: started, waiting for client, stopped. */
    SYSTEM,
    /** A client connected, joined or left. */
    CONNECTION,
    /** A chat message that was distributed to the group. */
    MESSAGE,
    /** Something went wrong or a request was refused. */
    WARNING
}
