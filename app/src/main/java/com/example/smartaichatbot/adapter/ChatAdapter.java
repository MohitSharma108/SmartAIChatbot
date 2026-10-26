package com.example.smartaichatbot.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartaichatbot.R;
import com.example.smartaichatbot.data.ChatMessageEntity;

import java.util.Objects;

/** Two view types: user bubbles (right) and AI bubbles (left, with a copy button). */
public class ChatAdapter extends ListAdapter<ChatMessageEntity, RecyclerView.ViewHolder> {

    public interface Listener {
        void onRetryRequested(ChatMessageEntity message);
    }

    private static final int TYPE_USER = 1;
    private static final int TYPE_AI = 2;

    private static final DiffUtil.ItemCallback<ChatMessageEntity> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ChatMessageEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull ChatMessageEntity oldItem,
                                               @NonNull ChatMessageEntity newItem) {
                    return oldItem.messageId == newItem.messageId;
                }

                @Override
                public boolean areContentsTheSame(@NonNull ChatMessageEntity oldItem,
                                                  @NonNull ChatMessageEntity newItem) {
                    return Objects.equals(oldItem.content, newItem.content)
                            && Objects.equals(oldItem.status, newItem.status)
                            && Objects.equals(oldItem.role, newItem.role);
                }
            };

    private final Listener listener;

    public ChatAdapter(Listener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return ChatMessageEntity.ROLE_USER.equals(getItem(position).role) ? TYPE_USER : TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            return new UserViewHolder(inflater.inflate(R.layout.item_user_message, parent, false));
        }
        return new AiViewHolder(inflater.inflate(R.layout.item_ai_message, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessageEntity message = getItem(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).bind(message);
        } else {
            ((AiViewHolder) holder).bind(message);
        }
    }

    private class UserViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvMessage;
        private final TextView tvStatus;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }

        void bind(final ChatMessageEntity message) {
            tvMessage.setText(message.content);

            if (ChatMessageEntity.STATUS_PENDING.equals(message.status)) {
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setText(R.string.status_sending);
                tvStatus.setOnClickListener(null);
                tvStatus.setClickable(false);
            } else if (ChatMessageEntity.STATUS_FAILED.equals(message.status)) {
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setText(R.string.status_failed);
                tvStatus.setTextColor(itemView.getContext().getColor(R.color.error));
                tvStatus.setOnClickListener(v -> listener.onRetryRequested(message));
            } else {
                tvStatus.setVisibility(View.GONE);
                tvStatus.setOnClickListener(null);
                tvStatus.setClickable(false);
            }
            if (!ChatMessageEntity.STATUS_FAILED.equals(message.status)) {
                tvStatus.setTextColor(itemView.getContext().getColor(R.color.text_secondary));
            }
        }
    }

    private static class AiViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvMessage;
        private final ImageButton btnCopy;

        AiViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            btnCopy = itemView.findViewById(R.id.btnCopy);
        }

        void bind(final ChatMessageEntity message) {
            tvMessage.setText(message.content);
            btnCopy.setOnClickListener(v -> {
                Context context = v.getContext();
                ClipboardManager clipboard =
                        (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("AI response", message.content));
                    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
