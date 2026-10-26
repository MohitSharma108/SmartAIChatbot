package com.example.smartaichatbot.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Deleting a conversation cascades to its messages, so orphaned messages cannot exist.
 */
@Entity(
        tableName = "messages",
        foreignKeys = @ForeignKey(
                entity = ConversationEntity.class,
                parentColumns = "conversationId",
                childColumns = "conversationId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("conversationId")})
public class ChatMessageEntity {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    @PrimaryKey(autoGenerate = true)
    public long messageId;

    @NonNull
    public String conversationId = "";

    public String role;
    public String content;
    public long timestamp;
    public String status;
}
