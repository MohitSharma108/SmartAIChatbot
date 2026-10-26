package com.example.smartaichatbot.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ConversationDao {

    @Insert
    void insert(ConversationEntity conversation);

    @Query("SELECT * FROM conversations WHERE conversationId = :id")
    ConversationEntity getById(String id);

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    LiveData<List<ConversationEntity>> observeRecent();

    @Query("UPDATE conversations SET title = :title WHERE conversationId = :id")
    void updateTitle(String id, String title);

    @Query("UPDATE conversations SET updatedAt = :timestamp WHERE conversationId = :id")
    void updateActivity(String id, long timestamp);

    /** Messages are removed automatically by the foreign key CASCADE. */
    @Query("DELETE FROM conversations WHERE conversationId = :id")
    void deleteById(String id);
}
