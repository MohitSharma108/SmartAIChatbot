package com.example.smartaichatbot;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartaichatbot.adapter.ChatAdapter;
import com.example.smartaichatbot.data.ChatMessageEntity;
import com.example.smartaichatbot.viewmodel.ChatViewModel;
import com.example.smartaichatbot.viewmodel.ChatViewModelFactory;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

public class MainActivity extends AppCompatActivity implements ChatAdapter.Listener {

    private ChatViewModel viewModel;
    private ChatAdapter adapter;

    private RecyclerView recyclerMessages;
    private View layoutWelcome;
    private View layoutError;
    private View progressLoading;
    private TextView tvError;
    private EditText etMessage;
    private ImageButton btnSend;

    private final ActivityResultLauncher<Intent> historyLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    viewModel.reloadActiveConversation();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this, new ChatViewModelFactory(getApplication()))
                .get(ChatViewModel.class);

        bindViews();
        setupToolbar();
        setupMessageList();
        setupInput();
        observeState();
    }

    private void bindViews() {
        recyclerMessages = findViewById(R.id.recyclerMessages);
        layoutWelcome = findViewById(R.id.layoutWelcome);
        layoutError = findViewById(R.id.layoutError);
        progressLoading = findViewById(R.id.progressLoading);
        tvError = findViewById(R.id.tvError);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_main);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_new_chat) {
                viewModel.newChat();
                etMessage.setText("");
                return true;
            }
            if (id == R.id.action_history) {
                historyLauncher.launch(new Intent(this, ChatHistoryActivity.class));
                return true;
            }
            return false;
        });
    }

    private void setupMessageList() {
        adapter = new ChatAdapter(this);
        recyclerMessages.setLayoutManager(new LinearLayoutManager(this));
        recyclerMessages.setAdapter(adapter);

        // Keep the newest message visible when the keyboard opens and the list shrinks.
        recyclerMessages.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    if (bottom < oldBottom && adapter.getItemCount() > 0) {
                        recyclerMessages.post(() ->
                                recyclerMessages.scrollToPosition(adapter.getItemCount() - 1));
                    }
                });
    }

    private void setupInput() {
        btnSend.setOnClickListener(v -> submit(etMessage.getText().toString()));

        int[] exampleIds = {R.id.btnExample1, R.id.btnExample2, R.id.btnExample3};
        for (int id : exampleIds) {
            findViewById(id).setOnClickListener(v -> submit(((Button) v).getText().toString()));
        }

        findViewById(R.id.btnRetry).setOnClickListener(v -> viewModel.retryLast());
        findViewById(R.id.btnDismiss).setOnClickListener(v -> viewModel.dismissError());
    }

    private void submit(String text) {
        switch (viewModel.send(text)) {
            case ChatViewModel.SEND_OK:
                etMessage.setText("");
                break;
            case ChatViewModel.SEND_EMPTY:
                etMessage.setError(getString(R.string.err_empty));
                break;
            case ChatViewModel.SEND_TOO_LONG:
                etMessage.setError(getString(R.string.err_too_long, ChatViewModel.MAX_MESSAGE_LENGTH));
                break;
            case ChatViewModel.SEND_BUSY:
                Toast.makeText(this, R.string.busy_wait, Toast.LENGTH_SHORT).show();
                break;
            default:
                break;
        }
    }

    private void observeState() {
        viewModel.getMessages().observe(this, this::renderMessages);

        viewModel.getLoading().observe(this, loading -> {
            boolean isLoading = Boolean.TRUE.equals(loading);
            progressLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            btnSend.setEnabled(!isLoading);
            btnSend.setAlpha(isLoading ? 0.4f : 1f);
        });

        viewModel.getError().observe(this, message -> {
            boolean hasError = message != null && !message.isEmpty();
            layoutError.setVisibility(hasError ? View.VISIBLE : View.GONE);
            if (hasError) {
                tvError.setText(message);
            }
        });
    }

    private void renderMessages(final List<ChatMessageEntity> list) {
        final boolean empty = list == null || list.isEmpty();
        layoutWelcome.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerMessages.setVisibility(empty ? View.GONE : View.VISIBLE);
        adapter.submitList(list, () -> {
            if (!empty) {
                recyclerMessages.scrollToPosition(adapter.getItemCount() - 1);
            }
        });
    }

    @Override
    public void onRetryRequested(ChatMessageEntity message) {
        viewModel.retryMessage(message.conversationId, message.messageId);
    }
}
