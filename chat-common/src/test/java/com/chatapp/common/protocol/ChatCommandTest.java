package com.chatapp.common.protocol;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatCommandTest {

    @Test
    void recognizesCommandsIgnoringCaseAndSpaces() {
        assertEquals(Optional.of(ChatCommand.ALL_USERS), ChatCommand.parse("allUsers"));
        assertEquals(Optional.of(ChatCommand.ALL_USERS), ChatCommand.parse("  ALLUSERS "));
        assertEquals(Optional.of(ChatCommand.END), ChatCommand.parse("end"));
        assertEquals(Optional.of(ChatCommand.BYE), ChatCommand.parse("Bye"));
    }

    @Test
    void ordinaryTextIsNotACommand() {
        assertEquals(Optional.empty(), ChatCommand.parse("bye everyone!"));
        assertEquals(Optional.empty(), ChatCommand.parse("the end is near"));
        assertEquals(Optional.empty(), ChatCommand.parse(null));
    }

    @Test
    void onlyEndAndByeDisconnect() {
        assertTrue(ChatCommand.END.isDisconnect());
        assertTrue(ChatCommand.BYE.isDisconnect());
        assertFalse(ChatCommand.ALL_USERS.isDisconnect());
    }
}
