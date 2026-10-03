package com.example.chatapp.adapter;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.chatapp.R;
import com.example.chatapp.databinding.ItemChatRowBinding;
import com.example.chatapp.model.Chat;

/**
 * ListAdapter + DiffUtil for the chat list on MainActivity.
 * Handles normal clicks (open chat) and long clicks (pin/unpin).
 */
public class ChatListAdapter extends ListAdapter<Chat, ChatListAdapter.ChatViewHolder> {

    public interface OnChatClickListener {
        void onChatClick(Chat chat);
        void onChatLongClick(Chat chat);
    }

    private final OnChatClickListener clickListener;

    public ChatListAdapter(OnChatClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
    }

    private static final DiffUtil.ItemCallback<Chat> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Chat>() {
                @Override
                public boolean areItemsTheSame(@NonNull Chat oldItem, @NonNull Chat newItem) {
                    return oldItem.getChatId() != null
                            && oldItem.getChatId().equals(newItem.getChatId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Chat oldItem, @NonNull Chat newItem) {
                    boolean sameMessage = strEquals(oldItem.getLastMessage(), newItem.getLastMessage());
                    boolean sameTime = oldItem.getLastMessageAt() == newItem.getLastMessageAt();
                    boolean sameName = strEquals(oldItem.getOtherUserName(), newItem.getOtherUserName());
                    boolean samePinned = oldItem.isPinned() == newItem.isPinned();
                    return sameMessage && sameTime && sameName && samePinned;
                }

                private boolean strEquals(String a, String b) {
                    if (a == null && b == null) return true;
                    if (a == null || b == null) return false;
                    return a.equals(b);
                }
            };

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChatRowBinding binding = ItemChatRowBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ChatViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ChatViewHolder extends RecyclerView.ViewHolder {

        private final ItemChatRowBinding binding;

        ChatViewHolder(ItemChatRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Chat chat) {
            // Name
            String name = chat.getOtherUserName();
            binding.txtName.setText(name != null && !name.isEmpty() ? name : "Unknown");

            // Last message
            String lastMsg = chat.getLastMessage();
            binding.txtLastMessage.setText(lastMsg != null && !lastMsg.isEmpty()
                    ? lastMsg : "No messages yet");

            // Timestamp
            long time = chat.getLastMessageAt();
            if (time > 0) {
                CharSequence relative = DateUtils.getRelativeTimeSpanString(
                        time,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE);
                binding.txtTimestamp.setText(relative);
            } else {
                binding.txtTimestamp.setText("");
            }

            // Avatar
            String photoUrl = chat.getOtherUserPhoto();
            if (photoUrl != null && !photoUrl.isEmpty()) {
                Glide.with(binding.imgAvatar.getContext())
                        .load(photoUrl)
                        .circleCrop()
                        .placeholder(R.mipmap.ic_launcher_round)
                        .into(binding.imgAvatar);
            } else {
                binding.imgAvatar.setImageResource(R.mipmap.ic_launcher_round);
            }

            // Pin state
            binding.imgPin.setVisibility(chat.isPinned() ? View.VISIBLE : View.GONE);

            // Clicks
            itemView.setOnClickListener(v -> clickListener.onChatClick(chat));
            itemView.setOnLongClickListener(v -> {
                clickListener.onChatLongClick(chat);
                return true;
            });
        }
    }
}
