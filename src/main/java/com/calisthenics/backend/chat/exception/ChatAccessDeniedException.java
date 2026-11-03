package com.calisthenics.backend.chat.exception;

public class ChatAccessDeniedException extends RuntimeException {
    public ChatAccessDeniedException() {
        super("You do not have access to this conversation");
    }
}
