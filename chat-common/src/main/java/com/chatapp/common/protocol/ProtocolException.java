package com.chatapp.common.protocol;

/** Thrown when a line received from the network is not a valid {@link Packet}. */
public class ProtocolException extends RuntimeException {

    public ProtocolException(String message) {
        super(message);
    }
}
