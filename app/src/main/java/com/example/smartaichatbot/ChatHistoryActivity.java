package com.example.smartaichatbot;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartaichatbot.adapter.ConversationAdapter;
import com.example.smartaichatbot.data.ConversationEntity;
import com.example.smartaichatbot.viewmodel.ChatViewModel;
import com.example.smartaichatbot.viewmodel.ChatViewModelFactory;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ChatHistoryActivity extends AppCompatActivity implements ConversationAdapter.Listener {

    private ChatViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_history);

        viewModel = new ViewModelProvider(this, new ChatViewModelFactory(getApplication()))
                .get(ChatViewModel.class);

        MaterialToolbar toolbar = findViewById(R.id.toolbarHistory);
        toolbar.setNavigationOnClickListener(v -> finish());

        final View tvEmpty = findViewById(R.id.tvEmpty);
        RecyclerView recycler = findViewById(R.id.recyclerConversations);
        final ConversationAdapter adapter = new ConversationAdapter(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        viewModel.getConversations().observe(this, list -> {
            adapter.submitList(list);
            tvEmpty.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onConversationClicked(ConversationEntity conversation) {
        viewModel.selectConversation(conversation.conversationId);
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onConversationDeleteRequested(final ConversationEntity conversation) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_title)
                .setMessage(getString(R.string.delete_message, conversation.title))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    viewModel.deleteConversation(conversation.conversationId);
                    setResult(RESULT_OK);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
