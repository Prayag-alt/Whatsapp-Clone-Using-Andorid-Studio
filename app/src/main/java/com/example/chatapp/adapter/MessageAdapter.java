package com.example.chatapp.adapter;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.chatapp.R;
import com.example.chatapp.databinding.ItemMessageReceivedBinding;
import com.example.chatapp.databinding.ItemMessageSentBinding;
import com.example.chatapp.model.Message;

import java.util.Date;

/**
 * ListAdapter + DiffUtil for messages in ChatActivity.
 * Handles: tick status, star icons, forwarded labels, reply quotes,
 * image messages, deleted placeholder, and long-press context menu via callback.
 */
public class MessageAdapter extends ListAdapter<Message, RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;

    private final String currentUserId;
    private OnMessageActionListener actionListener;

    public interface OnMessageActionListener {
        void onReply(Message message);
        void onForward(Message message);
        void onStar(Message message, boolean isCurrentlyStarred);
        void onCopy(Message message);
        void onDeleteForMe(Message message);
        void onDeleteForEveryone(Message message);
    }

    public MessageAdapter(String currentUserId) {
        super(DIFF_CALLBACK);
        this.currentUserId = currentUserId;
    }

    public void setActionListener(OnMessageActionListener listener) {
        this.actionListener = listener;
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
                    boolean sameStatus = strEquals(oldItem.getStatus(), newItem.getStatus());
                    boolean sameDeleted = oldItem.isDeleted() == newItem.isDeleted();
                    boolean sameReply = strEquals(oldItem.getReplyToText(), newItem.getReplyToText());
                    boolean sameImage = strEquals(oldItem.getImageUrl(), newItem.getImageUrl());
                    return sameText && sameStatus && sameDeleted && sameReply && sameImage;
                }

                private boolean strEquals(String a, String b) {
                    if (a == null && b == null) return true;
                    if (a == null || b == null) return false;
                    return a.equals(b);
                }
            };

    @Override
    public int getItemViewType(int position) {
        Message msg = getItem(position);
        if (msg.getSenderId() != null && msg.getSenderId().equals(currentUserId)) {
            return VIEW_TYPE_SENT;
        }
        return VIEW_TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_SENT) {
            ItemMessageSentBinding binding =
                    ItemMessageSentBinding.inflate(inflater, parent, false);
            return new SentViewHolder(binding);
        } else {
            ItemMessageReceivedBinding binding =
                    ItemMessageReceivedBinding.inflate(inflater, parent, false);
            return new ReceivedViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message msg = getItem(position);
        if (holder instanceof SentViewHolder) {
            ((SentViewHolder) holder).bind(msg, currentUserId, actionListener);
        } else {
            ((ReceivedViewHolder) holder).bind(msg, currentUserId, actionListener);
        }
    }

    // ── Sent message view holder ──

    static class SentViewHolder extends RecyclerView.ViewHolder {
        private final ItemMessageSentBinding binding;

        SentViewHolder(ItemMessageSentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Message msg, String currentUid, OnMessageActionListener listener) {
            // Deleted state
            if (msg.isDeleted()) {
                binding.txtMessage.setText("🚫 This message was deleted");
                binding.txtMessage.setAlpha(0.5f);
                binding.txtMessage.setVisibility(View.VISIBLE);
                binding.imgContent.setVisibility(View.GONE);
                binding.imgStatus.setVisibility(View.GONE);
                binding.imgStar.setVisibility(View.GONE);
                binding.layoutForwarded.setVisibility(View.GONE);
                binding.layoutReplyQuote.setVisibility(View.GONE);
                binding.txtTimestamp.setText(formatTime(msg.getSentAt()));
                itemView.setOnLongClickListener(null);
                return;
            }

            binding.txtMessage.setAlpha(1.0f);
            binding.txtTimestamp.setText(formatTime(msg.getSentAt()));
            binding.imgStatus.setVisibility(View.VISIBLE);

            // Image or text
            boolean isImage = "image".equals(msg.getType())
                    && msg.getImageUrl() != null && !msg.getImageUrl().isEmpty();

            if (isImage) {
                binding.imgContent.setVisibility(View.VISIBLE);
                binding.txtMessage.setVisibility(View.GONE);
                Glide.with(binding.imgContent.getContext())
                        .load(msg.getImageUrl())
                        .transform(new CenterCrop(), new RoundedCorners(24))
                        .placeholder(R.drawable.bg_chat_sent)
                        .error(R.drawable.bg_chat_sent)
                        .into(binding.imgContent);
            } else {
                binding.imgContent.setVisibility(View.GONE);
                binding.txtMessage.setVisibility(View.VISIBLE);
                binding.txtMessage.setText(msg.getText());
            }

            // Tick status
            String status = msg.getStatus();
            if (status == null) status = "sent";
            switch (status) {
                case "read":
                    binding.imgStatus.setImageResource(R.drawable.ic_double_check);
                    break;
                case "delivered":
                    binding.imgStatus.setImageResource(R.drawable.ic_double_check_grey);
                    break;
                default:
                    binding.imgStatus.setImageResource(R.drawable.ic_single_check);
                    break;
            }

            // Star icon
            boolean starred = msg.isStarredBy(currentUid);
            binding.imgStar.setVisibility(starred ? View.VISIBLE : View.GONE);

            // Forwarded label
            binding.layoutForwarded.setVisibility(
                    msg.getForwardedFrom() != null && !msg.getForwardedFrom().isEmpty()
                            ? View.VISIBLE : View.GONE);

            // Reply quote
            if (msg.getReplyToText() != null && !msg.getReplyToText().isEmpty()) {
                binding.layoutReplyQuote.setVisibility(View.VISIBLE);
                binding.txtReplySender.setText(
                        msg.getReplyToSenderName() != null ? msg.getReplyToSenderName() : "");
                binding.txtReplyText.setText(msg.getReplyToText());
            } else {
                binding.layoutReplyQuote.setVisibility(View.GONE);
            }

            // Long-press context menu
            if (listener != null) {
                setupLongPress(itemView, msg, currentUid, listener, true);
            }
        }
    }

    // ── Received message view holder ──

    static class ReceivedViewHolder extends RecyclerView.ViewHolder {
        private final ItemMessageReceivedBinding binding;

        ReceivedViewHolder(ItemMessageReceivedBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Message msg, String currentUid, OnMessageActionListener listener) {
            // Deleted state
            if (msg.isDeleted()) {
                binding.txtMessage.setText("🚫 This message was deleted");
                binding.txtMessage.setAlpha(0.5f);
                binding.txtMessage.setVisibility(View.VISIBLE);
                binding.imgContent.setVisibility(View.GONE);
                binding.imgStar.setVisibility(View.GONE);
                binding.layoutForwarded.setVisibility(View.GONE);
                binding.layoutReplyQuote.setVisibility(View.GONE);
                binding.txtTimestamp.setText(formatTime(msg.getSentAt()));
                itemView.setOnLongClickListener(null);
                return;
            }

            binding.txtMessage.setAlpha(1.0f);
            binding.txtTimestamp.setText(formatTime(msg.getSentAt()));

            // Image or text
            boolean isImage = "image".equals(msg.getType())
                    && msg.getImageUrl() != null && !msg.getImageUrl().isEmpty();

            if (isImage) {
                binding.imgContent.setVisibility(View.VISIBLE);
                binding.txtMessage.setVisibility(View.GONE);
                Glide.with(binding.imgContent.getContext())
                        .load(msg.getImageUrl())
                        .transform(new CenterCrop(), new RoundedCorners(24))
                        .placeholder(R.drawable.bg_chat_received)
                        .error(R.drawable.bg_chat_received)
                        .into(binding.imgContent);
            } else {
                binding.imgContent.setVisibility(View.GONE);
                binding.txtMessage.setVisibility(View.VISIBLE);
                binding.txtMessage.setText(msg.getText());
            }

            // Star icon
            boolean starred = msg.isStarredBy(currentUid);
            binding.imgStar.setVisibility(starred ? View.VISIBLE : View.GONE);

            // Forwarded label
            binding.layoutForwarded.setVisibility(
                    msg.getForwardedFrom() != null && !msg.getForwardedFrom().isEmpty()
                            ? View.VISIBLE : View.GONE);

            // Reply quote
            if (msg.getReplyToText() != null && !msg.getReplyToText().isEmpty()) {
                binding.layoutReplyQuote.setVisibility(View.VISIBLE);
                binding.txtReplySender.setText(
                        msg.getReplyToSenderName() != null ? msg.getReplyToSenderName() : "");
                binding.txtReplyText.setText(msg.getReplyToText());
            } else {
                binding.layoutReplyQuote.setVisibility(View.GONE);
            }

            // Long-press context menu
            if (listener != null) {
                setupLongPress(itemView, msg, currentUid, listener, false);
            }
        }
    }

    // ── Long-press popup menu ──

    private static void setupLongPress(View itemView, Message msg, String currentUid,
                                        OnMessageActionListener listener, boolean isSent) {
        itemView.setOnLongClickListener(v -> {
            android.widget.PopupMenu popup = new android.widget.PopupMenu(v.getContext(), v);

            popup.getMenu().add("Reply");
            popup.getMenu().add("Forward");

            // Copy only for text messages
            if (!"image".equals(msg.getType())) {
                popup.getMenu().add("Copy");
            }

            boolean starred = msg.isStarredBy(currentUid);
            popup.getMenu().add(starred ? "Unstar" : "Star");

            popup.getMenu().add("Delete for me");

            // Delete for everyone: sender only, within 1 hour
            if (isSent && msg.getSentAt() > 0) {
                long elapsed = System.currentTimeMillis() - msg.getSentAt();
                if (elapsed < 60 * 60 * 1000) {
                    popup.getMenu().add("Delete for everyone");
                }
            }

            popup.setOnMenuItemClickListener(item -> {
                String title = item.getTitle().toString();
                switch (title) {
                    case "Reply":
                        listener.onReply(msg);
                        return true;
                    case "Forward":
                        listener.onForward(msg);
                        return true;
                    case "Copy":
                        listener.onCopy(msg);
                        return true;
                    case "Star":
                    case "Unstar":
                        listener.onStar(msg, starred);
                        return true;
                    case "Delete for me":
                        listener.onDeleteForMe(msg);
                        return true;
                    case "Delete for everyone":
                        listener.onDeleteForEveryone(msg);
                        return true;
                }
                return false;
            });

            popup.show();
            return true;
        });
    }

    // ── Utility ──

    private static String formatTime(long millis) {
        if (millis == 0) return "";
        return DateFormat.format("h:mm a", new Date(millis)).toString();
    }
}
