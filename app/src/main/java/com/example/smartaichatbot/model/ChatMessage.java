package com.example.smartaichatbot.model;

/** One history item sent to the backend: role is "user" or "assistant". */
public class ChatMessage {

    private final String role;
    private final String content;

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }
}
