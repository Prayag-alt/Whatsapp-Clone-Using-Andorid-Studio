package com.example.chatapp.adapter;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatapp.databinding.ItemStarredMessageBinding;
import com.example.chatapp.model.Message;

import java.util.Date;

public class StarredMessagesAdapter extends ListAdapter<Message, StarredMessagesAdapter.StarredViewHolder> {

    private final OnStarredMessageClickListener listener;
    private final String currentUid;

    public interface OnStarredMessageClickListener {
        void onClick(Message message);
    }

    public StarredMessagesAdapter(String currentUid, OnStarredMessageClickListener listener) {
        super(DIFF_CALLBACK);
        this.currentUid = currentUid;
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Message> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Message>() {
                @Override
                public boolean areItemsTheSame(@NonNull Message oldItem, @NonNull Message newItem) {
                    return oldItem.getMessageId() != null
                            && oldItem.getMessageId().equals(newItem.getMessageId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Message oldItem, @NonNull Message newItem) {
                    boolean sameText = strEquals(oldItem.getText(), newItem.getText());
                    boolean sameChat = strEquals(oldItem.getChatId(), newItem.getChatId());
                    return sameText && sameChat;
                }

                private boolean strEquals(String a, String b) {
                    if (a == null && b == null) return true;
                    if (a == null || b == null) return false;
                    return a.equals(b);
                }
            };

    @NonNull
    @Override
    public StarredViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStarredMessageBinding binding =
                ItemStarredMessageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new StarredViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull StarredViewHolder holder, int position) {
        holder.bind(getItem(position), currentUid, listener);
    }

    static class StarredViewHolder extends RecyclerView.ViewHolder {
        private final ItemStarredMessageBinding binding;

        StarredViewHolder(ItemStarredMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Message msg, String currentUid, OnStarredMessageClickListener listener) {
            // Deleted state
            if (msg.isDeleted()) {
                binding.txtSnippet.setText("🚫 This message was deleted");
            } else {
                binding.txtSnippet.setText(msg.getText());
            }

            // Sender name (We don't have the user object here easily, so we just say "You" or their ID,
            // in a full app we'd resolve it via UserRepository like MainActivity does. 
            // For now we'll do "You" or "Other")
            if (currentUid.equals(msg.getSenderId())) {
                binding.txtSender.setText("You");
            } else {
                binding.txtSender.setText("Other user"); // Or we could add a transient field to Message
            }

            binding.txtTimestamp.setText(formatTime(msg.getSentAt()));

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onClick(msg);
            });
        }

        private String formatTime(long millis) {
            if (millis == 0) return "";
            return DateFormat.format("MMM d, h:mm a", new Date(millis)).toString();
        }
    }
}
