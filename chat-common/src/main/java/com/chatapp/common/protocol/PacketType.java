package com.chatapp.common.protocol;

/**
 * Every kind of packet that can travel over the wire.
 *
 * <pre>
 * Client -> Server                 Server -> Client
 * ----------------                 ----------------
 * HELLO   [username]               WELCOME  [username, readOnly]
 * MESSAGE [text]                   REJECTED [reason]
 * BYE     []                       CHAT     [time, sender, text]
 *                                  INFO     [time, text]
 *                                  USERS    [user1, user2, ...]
 *                                  ERROR    [reason]
 * </pre>
 */
public enum PacketType {

    /** First packet a client sends. An empty username requests READ-ONLY MODE. */
    HELLO,
    /** A line typed by the user: either chat text or a command such as {@code allUsers}. */
    MESSAGE,
    /** The client is leaving and the server should release its connection. */
    BYE,

    /** Handshake accepted: carries the final username and whether the session is read-only. */
    WELCOME,
    /** Handshake refused (e.g. username already taken); the server closes the socket afterwards. */
    REJECTED,
    /** A chat message formatted by the server with the sender's name and the time. */
    CHAT,
    /** A system notice such as "Alice joined the chat". */
    INFO,
    /** Reply to the {@code allUsers} command, sent only to the client that asked. */
    USERS,
    /** Something the client asked for was not allowed. */
    ERROR
}
