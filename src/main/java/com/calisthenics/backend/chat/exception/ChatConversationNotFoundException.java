package com.calisthenics.backend.chat.exception;

public class ChatConversationNotFoundException extends RuntimeException {
    public ChatConversationNotFoundException() {
        super("The requested chat conversation was not found");
    }
}
