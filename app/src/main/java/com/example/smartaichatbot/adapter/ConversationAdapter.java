package com.example.smartaichatbot.adapter;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartaichatbot.R;
import com.example.smartaichatbot.data.ConversationEntity;

import java.util.Objects;

public class ConversationAdapter
        extends ListAdapter<ConversationEntity, ConversationAdapter.ConversationViewHolder> {

    public interface Listener {
        void onConversationClicked(ConversationEntity conversation);

        void onConversationDeleteRequested(ConversationEntity conversation);
    }

    private static final DiffUtil.ItemCallback<ConversationEntity> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ConversationEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull ConversationEntity oldItem,
                                               @NonNull ConversationEntity newItem) {
                    return oldItem.conversationId.equals(newItem.conversationId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull ConversationEntity oldItem,
                                                  @NonNull ConversationEntity newItem) {
                    return Objects.equals(oldItem.title, newItem.title)
                            && oldItem.updatedAt == newItem.updatedAt;
                }
            };

    private final Listener listener;

    public ConversationAdapter(Listener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ConversationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ConversationViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvDate;
        private final ImageButton btnDelete;

        ConversationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDate = itemView.findViewById(R.id.tvDate);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        void bind(final ConversationEntity conversation) {
            tvTitle.setText(conversation.title);
            tvDate.setText(DateUtils.getRelativeTimeSpanString(
                    conversation.updatedAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS));
            itemView.setOnClickListener(v -> listener.onConversationClicked(conversation));
            btnDelete.setOnClickListener(v -> listener.onConversationDeleteRequested(conversation));
        }
    }
}
