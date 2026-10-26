package com.example.smartaichatbot.network;

/**
 * Parses both response shapes from the backend:
 * success {"reply": "..."} and error {"error": {"code": "...", "message": "..."}}.
 */
public class ChatResponse {

    private String reply;
    private ErrorBody error;

    public String getReply() {
        return reply;
    }

    public ErrorBody getError() {
        return error;
    }

    public static class ErrorBody {
        private String code;
        private String message;

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
