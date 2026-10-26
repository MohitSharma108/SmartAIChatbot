package com.example.smartaichatbot.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.example.smartaichatbot.data.ChatMessageEntity;
import com.example.smartaichatbot.data.ConversationEntity;
import com.example.smartaichatbot.repository.ChatRepository;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Survives rotation. After process death the active conversation is restored from the repository. */
public class ChatViewModel extends ViewModel {

    public static final int SEND_OK = 0;
    public static final int SEND_EMPTY = 1;
    public static final int SEND_TOO_LONG = 2;
    public static final int SEND_BUSY = 3;

    /** Keep equal to MAX_MESSAGE_CHARS in backend/server.js. */
    public static final int MAX_MESSAGE_LENGTH = 4000;

    private final ChatRepository repository;
    private final MutableLiveData<String> conversationId = new MutableLiveData<>();
    private final LiveData<List<ChatMessageEntity>> messages;
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private volatile String failedConversationId;
    private volatile long failedMessageId = -1;

    private final ChatRepository.SendCallback sendCallback = new ChatRepository.SendCallback() {
        @Override
        public void onConversationReady(String id) {
            conversationId.postValue(id);
        }

        @Override
        public void onSuccess() {
            failedMessageId = -1;
            busy.set(false);
            loading.postValue(false);
        }

        @Override
        public void onError(String message, String convId, long messageId) {
            failedConversationId = convId;
            failedMessageId = messageId;
            busy.set(false);
            loading.postValue(false);
            error.postValue(message);
        }
    };

    public ChatViewModel(@NonNull ChatRepository repository) {
        this.repository = repository;
        conversationId.setValue(repository.getActiveConversationId());
        messages = Transformations.switchMap(conversationId,
                id -> repository.observeMessages(id == null ? "" : id));
    }

    // ---------- state for the UI ----------

    public LiveData<List<ChatMessageEntity>> getMessages() {
        return messages;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    /** Null means "no error". */
    public LiveData<String> getError() {
        return error;
    }

    public LiveData<List<ConversationEntity>> getConversations() {
        return repository.observeConversations();
    }

    // ---------- actions (call from the main thread) ----------

    public int send(String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            return SEND_EMPTY;
        }
        if (text.length() > MAX_MESSAGE_LENGTH) {
            return SEND_TOO_LONG;
        }
        if (!busy.compareAndSet(false, true)) {
            return SEND_BUSY;
        }
        error.setValue(null);
        loading.setValue(true);
        repository.sendMessage(conversationId.getValue(), text, -1, sendCallback);
        return SEND_OK;
    }

    /** Retry the message that failed most recently (error banner button). */
    public void retryLast() {
        retryMessage(failedConversationId, failedMessageId);
    }

    /** Retry a specific failed message (tap on its "Not sent" label). */
    public void retryMessage(String convId, long messageId) {
        if (convId == null || messageId <= 0) {
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        error.setValue(null);
        loading.setValue(true);
        repository.sendMessage(convId, null, messageId, sendCallback);
    }

    public void dismissError() {
        error.setValue(null);
    }

    public void newChat() {
        repository.setActiveConversationId(null);
        conversationId.setValue(null);
        error.setValue(null);
    }

    /** Called after the history screen changed the active conversation. */
    public void reloadActiveConversation() {
        conversationId.setValue(repository.getActiveConversationId());
        error.setValue(null);
    }

    public void selectConversation(String id) {
        repository.setActiveConversationId(id);
    }

    public void deleteConversation(String id) {
        repository.deleteConversation(id);
    }
}
