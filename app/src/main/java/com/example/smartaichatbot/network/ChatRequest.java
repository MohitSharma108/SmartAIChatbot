package com.example.smartaichatbot.network;

import com.example.smartaichatbot.model.ChatMessage;

import java.util.List;

/** JSON body for POST /chat: {conversationId, message, history:[{role, content}]}. */
public class ChatRequest {

    private final String conversationId;
    private final String message;
    private final List<ChatMessage> history;

    public ChatRequest(String conversationId, String message, List<ChatMessage> history) {
        this.conversationId = conversationId;
        this.message = message;
        this.history = history;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getMessage() {
        return message;
    }

    public List<ChatMessage> getHistory() {
        return history;
    }
}
