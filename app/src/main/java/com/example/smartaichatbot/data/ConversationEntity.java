package com.example.smartaichatbot.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "conversations")
public class ConversationEntity {

    @PrimaryKey
    @NonNull
    public String conversationId = "";

    public String title;
    public long createdAt;
    public long updatedAt;
}
