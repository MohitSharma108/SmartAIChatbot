package com.example.smartaichatbot.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Version 1. When you change an entity, bump the version and add a Migration with
 * .addMigrations(...) below. Do NOT use fallbackToDestructiveMigration(): it would wipe chat history.
 * Schemas are exported to app/schemas (see build.gradle).
 */
@Database(
        entities = {ConversationEntity.class, ChatMessageEntity.class},
        version = 1,
        exportSchema = true)
public abstract class ChatDatabase extends RoomDatabase {

    private static volatile ChatDatabase instance;

    public abstract ConversationDao conversationDao();

    public abstract ChatDao chatDao();

    public static ChatDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (ChatDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            ChatDatabase.class,
                            "smart_ai_chatbot.db")
                            .build();
                }
            }
        }
        return instance;
    }
}
