package com.example.smartaichatbot.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;

import com.example.smartaichatbot.data.ChatDao;
import com.example.smartaichatbot.data.ChatDatabase;
import com.example.smartaichatbot.data.ChatMessageEntity;
import com.example.smartaichatbot.data.ConversationDao;
import com.example.smartaichatbot.data.ConversationEntity;
import com.example.smartaichatbot.model.ChatMessage;
import com.example.smartaichatbot.network.ApiClient;
import com.example.smartaichatbot.network.ChatApi;
import com.example.smartaichatbot.network.ChatRequest;
import com.example.smartaichatbot.network.ChatResponse;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Coordinates Room and the backend. Everything that touches the database or network
 * runs on a background executor; callbacks are invoked on that background thread.
 */
public class ChatRepository {

    /** Callbacks arrive on a background thread: use postValue() on LiveData. */
    public interface SendCallback {
        void onConversationReady(String conversationId);

        void onSuccess();

        void onError(String message, String conversationId, long failedMessageId);
    }

    private static final int HISTORY_LIMIT = 20;
    private static final int TITLE_MAX_CHARS = 40;
    private static final String PREFS_NAME = "chat_prefs";
    private static final String KEY_ACTIVE_CONVERSATION = "active_conversation_id";

    // Network + DB work for sending lives on one thread; deletes use another so they never wait for a slow request.
    private static final ExecutorService SEND_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final ExecutorService DB_EXECUTOR = Executors.newSingleThreadExecutor();

    private final ChatDao chatDao;
    private final ConversationDao conversationDao;
    private final ChatApi api;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public ChatRepository(Context context) {
        Context appContext = context.getApplicationContext();
        ChatDatabase db = ChatDatabase.getInstance(appContext);
        this.chatDao = db.chatDao();
        this.conversationDao = db.conversationDao();
        this.api = ApiClient.getApi();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ---------- observing ----------

    public LiveData<List<ChatMessageEntity>> observeMessages(String conversationId) {
        return chatDao.observeMessages(conversationId);
    }

    public LiveData<List<ConversationEntity>> observeConversations() {
        return conversationDao.observeRecent();
    }

    // ---------- active conversation (restored after app restart) ----------

    public String getActiveConversationId() {
        return prefs.getString(KEY_ACTIVE_CONVERSATION, null);
    }

    public void setActiveConversationId(String conversationId) {
        SharedPreferences.Editor editor = prefs.edit();
        if (conversationId == null) {
            editor.remove(KEY_ACTIVE_CONVERSATION);
        } else {
            editor.putString(KEY_ACTIVE_CONVERSATION, conversationId);
        }
        editor.apply();
    }

    // ---------- deleting ----------

    public void deleteConversation(final String conversationId) {
        if (conversationId.equals(getActiveConversationId())) {
            setActiveConversationId(null);
        }
        DB_EXECUTOR.execute(() -> conversationDao.deleteById(conversationId));
    }

    // ---------- sending ----------

    /**
     * Sends a new message (retryMessageId = -1) or retries a failed one (retryMessageId > 0, text ignored).
     * If conversationId is null or unknown, a new conversation is created and titled from the first message.
     */
    public void sendMessage(final String conversationId, final String text,
                            final long retryMessageId, final SendCallback callback) {
        SEND_EXECUTOR.execute(() -> {
            String convId = conversationId;
            long messageId = -1;
            try {
                String content = text;
                long now = System.currentTimeMillis();

                if (retryMessageId > 0) {
                    ChatMessageEntity failed = chatDao.getById(retryMessageId);
                    if (failed == null) {
                        callback.onError("That message no longer exists.", convId, -1);
                        return;
                    }
                    content = failed.content;
                    convId = failed.conversationId;
                    messageId = retryMessageId;
                    chatDao.markPending(messageId, now);
                }

                ConversationEntity conversation = convId == null ? null : conversationDao.getById(convId);
                if (conversation == null) {
                    conversation = new ConversationEntity();
                    conversation.conversationId = UUID.randomUUID().toString();
                    conversation.title = makeTitle(content);
                    conversation.createdAt = now;
                    conversation.updatedAt = now;
                    conversationDao.insert(conversation);
                    convId = conversation.conversationId;
                }
                setActiveConversationId(convId);
                callback.onConversationReady(convId);

                // Context = last successfully sent messages (the new/pending one is excluded by status).
                List<ChatMessage> history = new ArrayList<>();
                for (ChatMessageEntity entity : chatDao.getRecentSent(convId, HISTORY_LIMIT)) {
                    history.add(new ChatMessage(entity.role, entity.content));
                }

                if (messageId <= 0) {
                    ChatMessageEntity userMessage = new ChatMessageEntity();
                    userMessage.conversationId = convId;
                    userMessage.role = ChatMessageEntity.ROLE_USER;
                    userMessage.content = content;
                    userMessage.timestamp = now;
                    userMessage.status = ChatMessageEntity.STATUS_PENDING;
                    messageId = chatDao.insert(userMessage);
                }
                conversationDao.updateActivity(convId, now);

                Response<ChatResponse> response =
                        api.chat(new ChatRequest(convId, content, history)).execute();

                if (!response.isSuccessful()) {
                    failAndReport(messageId, convId,
                            describeHttpError(response.code(), response.errorBody()), callback);
                    return;
                }

                ChatResponse body = response.body();
                String reply = body == null ? null : body.getReply();
                if (reply == null || reply.trim().isEmpty()) {
                    failAndReport(messageId, convId,
                            "The AI returned an empty answer. Please retry.", callback);
                    return;
                }

                chatDao.updateStatus(messageId, ChatMessageEntity.STATUS_SENT);

                long replyTime = System.currentTimeMillis();
                ChatMessageEntity aiMessage = new ChatMessageEntity();
                aiMessage.conversationId = convId;
                aiMessage.role = ChatMessageEntity.ROLE_ASSISTANT;
                aiMessage.content = reply.trim();
                aiMessage.timestamp = replyTime;
                aiMessage.status = ChatMessageEntity.STATUS_SENT;
                chatDao.insert(aiMessage);
                conversationDao.updateActivity(convId, replyTime);

                callback.onSuccess();

            } catch (SocketTimeoutException e) {
                failAndReport(messageId, convId,
                        "The request timed out. Check your connection and retry.", callback);
            } catch (ConnectException | UnknownHostException e) {
                failAndReport(messageId, convId,
                        "Cannot reach the server. Is the backend running? (Emulator URL: 10.0.2.2:3000)",
                        callback);
            } catch (IOException e) {
                failAndReport(messageId, convId, "Network error. Please retry.", callback);
            } catch (Exception e) {
                // Malformed JSON, database errors, etc.
                failAndReport(messageId, convId, "Something went wrong. Please retry.", callback);
            }
        });
    }

    private void failAndReport(long messageId, String conversationId, String message, SendCallback callback) {
        try {
            if (messageId > 0) {
                chatDao.updateStatus(messageId, ChatMessageEntity.STATUS_FAILED);
            }
        } catch (Exception ignored) {
            // The conversation may have been deleted meanwhile.
        }
        callback.onError(message, conversationId, messageId);
    }

    private String describeHttpError(int code, ResponseBody errorBody) {
        // The backend sends {"error": {"code", "message"}} with safe, user-facing text.
        try {
            if (errorBody != null) {
                ChatResponse parsed = gson.fromJson(errorBody.string(), ChatResponse.class);
                if (parsed != null && parsed.getError() != null) {
                    String serverMessage = parsed.getError().getMessage();
                    if (serverMessage != null && !serverMessage.trim().isEmpty()) {
                        return serverMessage;
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall through to the generic text below.
        }

        switch (code) {
            case 400:
                return "The request was rejected. Try a shorter message.";
            case 401:
            case 403:
                return "The server refused the request.";
            case 429:
                return "Too many requests. Please wait a moment and retry.";
            case 504:
                return "The AI took too long to answer. Please retry.";
            default:
                return "Server error (" + code + "). Please try again later.";
        }
    }

    private static String makeTitle(String firstMessage) {
        String oneLine = firstMessage.replaceAll("\\s+", " ").trim();
        if (oneLine.length() > TITLE_MAX_CHARS) {
            return oneLine.substring(0, TITLE_MAX_CHARS).trim() + "…";
        }
        return oneLine;
    }
}
