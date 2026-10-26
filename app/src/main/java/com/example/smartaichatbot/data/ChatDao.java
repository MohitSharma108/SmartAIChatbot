package com.example.smartaichatbot.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ChatDao {

    @Insert
    long insert(ChatMessageEntity message);

    @Query("SELECT * FROM messages WHERE messageId = :id")
    ChatMessageEntity getById(long id);

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId "
            + "ORDER BY timestamp ASC, messageId ASC")
    LiveData<List<ChatMessageEntity>> observeMessages(String conversationId);

    /** Last N successfully sent messages, oldest first. Used as AI context. */
    @Query("SELECT * FROM (SELECT * FROM messages WHERE conversationId = :conversationId "
            + "AND status = 'SENT' ORDER BY timestamp DESC, messageId DESC LIMIT :limit) "
            + "ORDER BY timestamp ASC, messageId ASC")
    List<ChatMessageEntity> getRecentSent(String conversationId, int limit);

    @Query("UPDATE messages SET status = :status WHERE messageId = :id")
    void updateStatus(long id, String status);

    @Query("UPDATE messages SET status = 'PENDING', timestamp = :timestamp WHERE messageId = :id")
    void markPending(long id, long timestamp);
}
