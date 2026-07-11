package com.mercury.chinesepinyinime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ComputerManagerSessionTest {
    @Test
    public void acceptsOnlyCurrentTokenAndIncreasingSequence() {
        ComputerManagerSession session = new ComputerManagerSession();
        String token = session.open();

        assertTrue(session.authorize(token, 1));
        assertFalse(session.authorize(token, 1));
        assertFalse(session.authorize(token, 0));
        assertFalse(session.authorize("wrong", 2));
        assertTrue(session.authorize(token, 2));
    }

    @Test
    public void reopeningInvalidatesPreviousToken() {
        ComputerManagerSession session = new ComputerManagerSession();
        String first = session.open();
        String second = session.open();

        assertNotEquals(first, second);
        assertFalse(session.authorize(first, 1));
        assertTrue(session.authorize(second, 1));
        session.close();
        assertFalse(session.authorize(second, 2));
    }
}
