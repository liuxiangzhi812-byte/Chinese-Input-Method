package com.mercury.chinesepinyinime;

import java.security.SecureRandom;
import java.util.Base64;

final class ComputerManagerSession {
    private final SecureRandom random = new SecureRandom();
    private String token;
    private long lastSequence;

    synchronized String open() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        lastSequence = 0;
        return token;
    }

    synchronized boolean authorize(String suppliedToken, long sequence) {
        if (token == null || suppliedToken == null || sequence <= lastSequence) {
            return false;
        }
        if (!constantTimeEquals(token, suppliedToken)) {
            return false;
        }
        lastSequence = sequence;
        return true;
    }

    synchronized boolean isOpen() {
        return token != null;
    }

    synchronized void close() {
        token = null;
        lastSequence = 0;
    }

    private static boolean constantTimeEquals(String expected, String supplied) {
        if (expected.length() != supplied.length()) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < expected.length(); i++) {
            difference |= expected.charAt(i) ^ supplied.charAt(i);
        }
        return difference == 0;
    }
}
