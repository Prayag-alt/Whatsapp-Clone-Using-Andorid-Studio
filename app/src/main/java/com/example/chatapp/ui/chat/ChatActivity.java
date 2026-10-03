package com.example.chatapp.ui.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.chatapp.R;
import com.example.chatapp.adapter.MessageAdapter;
import com.example.chatapp.data.ChatRepository;
import com.example.chatapp.databinding.ActivityChatBinding;
import com.example.chatapp.model.Message;
import com.example.chatapp.util.CloudinaryUploader;
import com.example.chatapp.util.ImageCompressor;
import com.example.chatapp.util.PresenceManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.ListenerRegistration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 1:1 chat conversation screen.
 * Real-time messages via ChatRepository, presence/typing via PresenceManager,
 * read receipts, image messages via Cloudinary, and all Phase 4 actions.
 */
public class ChatActivity extends AppCompatActivity implements MessageAdapter.OnMessageActionListener {

    private static final String TAG = "ChatActivity";

    public static final String EXTRA_CHAT_ID = "extra_chat_id";
    public static final String EXTRA_OTHER_UID = "extra_other_uid";
    public static final String EXTRA_OTHER_NAME = "extra_other_name";
    public static final String EXTRA_OTHER_PHOTO = "extra_other_photo";

    // TODO: Replace with your Cloudinary cloud name and unsigned upload preset
    private static final String CLOUDINARY_CLOUD_NAME = "h05nzkcj";
    private static final String CLOUDINARY_UPLOAD_PRESET = "chat_unsigned";

    private ActivityChatBinding binding;
    private MessageAdapter adapter;
    private ChatRepository chatRepository;
    private PresenceManager presenceManager;
    private ListenerRegistration messagesListener;

    // RTDB listeners (must be detached)
    private ValueEventListener presenceListener;
    private ValueEventListener typingListener;

    private String currentUid;
    private String chatId;
    private String otherUid;
    private String otherName;
    private String otherPhoto;

    // Track whether the other user is typing (to prioritize over presence text)
    private boolean otherIsTyping = false;
    private String currentPresenceText = "";

    // Reply state
    private String pendingReplyToMessageId = null;

    // Image picker launcher
    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        currentUid = user.getUid();

        chatId = getIntent().getStringExtra(EXTRA_CHAT_ID);
        otherUid = getIntent().getStringExtra(EXTRA_OTHER_UID);
        otherName = getIntent().getStringExtra(EXTRA_OTHER_NAME);
        otherPhoto = getIntent().getStringExtra(EXTRA_OTHER_PHOTO);

        if (chatId == null || otherUid == null) {
            Toast.makeText(this, "Error loading chat", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        chatRepository = new ChatRepository();
        presenceManager = new PresenceManager();

        // Initialize Cloudinary (safe to call multiple times — it no-ops after first)
        CloudinaryUploader.init(getApplicationContext(), CLOUDINARY_CLOUD_NAME);

        // Register image picker
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::onImagePicked
        );

        setupToolbar();
        setupRecyclerView();
        setupInput();
    }

    @Override
    protected void onStart() {
        super.onStart();
        attachMessagesListener();
        attachPresenceListener();
        attachTypingListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachMessagesListener();
        detachPresenceListener();
        detachTypingListener();
        // Clear our own typing indicator when leaving
        presenceManager.clearTyping(chatId);
    }

    // ── Setup ──

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.txtToolbarName.setText(
                otherName != null && !otherName.isEmpty() ? otherName : "Unknown");

        if (otherPhoto != null && !otherPhoto.isEmpty()) {
            Glide.with(this)
                    .load(otherPhoto)
                    .circleCrop()
                    .placeholder(R.mipmap.ic_launcher_round)
                    .into(binding.imgToolbarAvatar);
        } else {
            binding.imgToolbarAvatar.setImageResource(R.mipmap.ic_launcher_round);
        }
    }

    private void setupRecyclerView() {
        adapter = new MessageAdapter(currentUid);
        adapter.setActionListener(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.recyclerMessages.setLayoutManager(layoutManager);
        binding.recyclerMessages.setAdapter(adapter);
    }

    private void setupInput() {
        binding.btnCloseReply.setOnClickListener(v -> clearReplyState());

        // Attach button — opens system image picker
        binding.btnAttach.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        binding.editMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean hasText = s != null && s.toString().trim().length() > 0;
                binding.btnSend.setEnabled(hasText);

                // Typing indicator: signal when non-empty, clear when empty
                if (hasText) {
                    presenceManager.setTyping(chatId);
                } else {
                    presenceManager.clearTyping(chatId);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnSend.setOnClickListener(v -> sendMessage());
    }

    // ── Image Handling ──

    private void onImagePicked(Uri uri) {
        if (uri == null) return;

        Toast.makeText(this, "Compressing image…", Toast.LENGTH_SHORT).show();

        // Compress and upload on a background thread
        new Thread(() -> {
            try {
                File compressed = ImageCompressor.compress(this, uri);
                if (compressed == null) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "Failed to compress image", Toast.LENGTH_SHORT).show());
                    return;
                }

                runOnUiThread(() -> Toast.makeText(this,
                        "Uploading image…", Toast.LENGTH_SHORT).show());

                CloudinaryUploader.uploadImage(compressed, "chat_images",
                        CLOUDINARY_UPLOAD_PRESET,
                        new CloudinaryUploader.UploadListener() {
                            @Override
                            public void onSuccess(String imageUrl) {
                                // Send the image message
                                String replyId = pendingReplyToMessageId;
                                runOnUiThread(() -> clearReplyState());

                                chatRepository.sendImageMessage(chatId, currentUid,
                                        imageUrl, replyId,
                                        new ChatRepository.SendMessageCallback() {
                                            @Override
                                            public void onSuccess() {
                                                // Clean up temp file
                                                compressed.delete();
                                            }

                                            @Override
                                            public void onError(Exception e) {
                                                Log.e(TAG, "Error sending image message", e);
                                                runOnUiThread(() -> Toast.makeText(
                                                        ChatActivity.this,
                                                        "Failed to send image",
                                                        Toast.LENGTH_SHORT).show());
                                            }
                                        });
                            }

                            @Override
                            public void onError(String errorMessage) {
                                Log.e(TAG, "Cloudinary upload error: " + errorMessage);
                                runOnUiThread(() -> Toast.makeText(ChatActivity.this,
                                        "Upload failed: " + errorMessage,
                                        Toast.LENGTH_SHORT).show());
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Image compression error", e);
                runOnUiThread(() -> Toast.makeText(this,
                        "Failed to process image", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ── Actions ──

    private void sendMessage() {
        String text = binding.editMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        binding.editMessage.setText("");
        presenceManager.clearTyping(chatId);

        String replyId = pendingReplyToMessageId;
        clearReplyState();

        chatRepository.sendTextMessage(chatId, currentUid, text, replyId, null,
                new ChatRepository.SendMessageCallback() {
                    @Override
                    public void onSuccess() {
                        // Listener will pick it up
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Error sending message", e);
                        runOnUiThread(() -> {
                            Toast.makeText(ChatActivity.this,
                                    "Failed to send message", Toast.LENGTH_SHORT).show();
                            binding.editMessage.setText(text);
                        });
                    }
                });
    }

    // ── Messages real-time listener + read receipts ──

    private void attachMessagesListener() {
        messagesListener = chatRepository.listenToMessages(chatId,
                new ChatRepository.MessagesListener() {
                    @Override
                    public void onMessagesUpdated(List<Message> messages) {
                        List<Message> visibleMessages = new ArrayList<>();
                        for (Message msg : messages) {
                            if (!msg.isDeletedFor(currentUid)) {
                                visibleMessages.add(msg);
                            }
                        }

                        resolveReplies(visibleMessages);
                        markIncomingAsRead();
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Error listening to messages", e);
                    }
                });
    }

    private void resolveReplies(List<Message> messages) {
        if (messages.isEmpty()) {
            submitMessages(messages);
            return;
        }

        final int[] remaining = {0};
        for (Message msg : messages) {
            if (msg.getReplyToMessageId() != null && msg.getReplyToText() == null) {
                remaining[0]++;
                chatRepository.getMessage(chatId, msg.getReplyToMessageId(), new ChatRepository.MessageCallback() {
                    @Override
                    public void onMessage(Message fetchedMsg) {
                        if (fetchedMsg != null) {
                            // For image replies, show "📷 Photo" as the reply text
                            if ("image".equals(fetchedMsg.getType())) {
                                msg.setReplyToText("\uD83D\uDCF7 Photo");
                            } else {
                                msg.setReplyToText(fetchedMsg.getText());
                            }
                            msg.setReplyToSenderName(fetchedMsg.getSenderId().equals(currentUid) ? "You" : otherName);
                        } else {
                            msg.setReplyToText("Original message deleted");
                            msg.setReplyToSenderName("System");
                        }
                        remaining[0]--;
                        if (remaining[0] == 0) submitMessages(messages);
                    }

                    @Override
                    public void onError(Exception e) {
                        remaining[0]--;
                        if (remaining[0] == 0) submitMessages(messages);
                    }
                });
            }
        }
        if (remaining[0] == 0) {
            submitMessages(messages);
        }
    }

    private void submitMessages(List<Message> messages) {
        runOnUiThread(() -> {
            adapter.submitList(messages, () -> {
                if (!messages.isEmpty()) {
                    binding.recyclerMessages.scrollToPosition(messages.size() - 1);
                }
            });
        });
    }

    private void detachMessagesListener() {
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
    }

    /**
     * When the chat is open, mark all incoming messages (from the other user)
     * as "read". This updates Firestore, and the sender's real-time listener
     * will see the status change and update their ticks to blue.
     */
    private void markIncomingAsRead() {
        chatRepository.markMessagesAsRead(chatId, otherUid);
    }

    // ── Presence listener ──

    private void attachPresenceListener() {
        presenceListener = presenceManager.listenToPresence(otherUid,
                (state, lastSeen) -> runOnUiThread(() -> {
                    currentPresenceText = PresenceManager.formatPresence(state, lastSeen);
                    updateSubtitle();
                }));
    }

    private void detachPresenceListener() {
        if (presenceListener != null) {
            presenceManager.stopListeningToPresence(otherUid, presenceListener);
            presenceListener = null;
        }
    }

    // ── Typing listener ──

    private void attachTypingListener() {
        typingListener = presenceManager.listenToTyping(chatId, otherUid,
                isTyping -> runOnUiThread(() -> {
                    otherIsTyping = isTyping;
                    updateSubtitle();
                }));
    }

    private void detachTypingListener() {
        if (typingListener != null) {
            presenceManager.stopListeningToTyping(chatId, otherUid, typingListener);
            typingListener = null;
        }
    }

    // ── Subtitle (typing takes priority over presence) ──

    private void updateSubtitle() {
        if (otherIsTyping) {
            binding.txtToolbarSubtitle.setText("typing…");
            binding.txtToolbarSubtitle.setVisibility(View.VISIBLE);
        } else if (currentPresenceText != null && !currentPresenceText.isEmpty()) {
            binding.txtToolbarSubtitle.setText(currentPresenceText);
            binding.txtToolbarSubtitle.setVisibility(View.VISIBLE);
        } else {
            binding.txtToolbarSubtitle.setVisibility(View.GONE);
        }
    }

    // ── Message Actions ──

    @Override
    public void onReply(Message message) {
        pendingReplyToMessageId = message.getMessageId();
        binding.layoutReplyPreview.setVisibility(View.VISIBLE);
        binding.txtReplyPreviewSender.setText(message.getSenderId().equals(currentUid) ? "You" : otherName);

        // Show "📷 Photo" for image replies
        if ("image".equals(message.getType())) {
            binding.txtReplyPreviewText.setText("\uD83D\uDCF7 Photo");
        } else {
            binding.txtReplyPreviewText.setText(message.getText());
        }
        binding.editMessage.requestFocus();
    }

    private void clearReplyState() {
        pendingReplyToMessageId = null;
        binding.layoutReplyPreview.setVisibility(View.GONE);
        binding.txtReplyPreviewSender.setText("");
        binding.txtReplyPreviewText.setText("");
    }

    @Override
    public void onForward(Message message) {
        Intent intent = new Intent(this, ForwardActivity.class);
        intent.putExtra(ForwardActivity.EXTRA_ORIGINAL_TEXT, message.getText());
        intent.putExtra(ForwardActivity.EXTRA_ORIGINAL_IMAGE_URL, message.getImageUrl());
        intent.putExtra(ForwardActivity.EXTRA_ORIGINAL_TYPE, message.getType());
        intent.putExtra(ForwardActivity.EXTRA_ORIGINAL_CHAT_ID, chatId);
        intent.putExtra(ForwardActivity.EXTRA_ORIGINAL_MESSAGE_ID, message.getMessageId());
        startActivity(intent);
    }

    @Override
    public void onStar(Message message, boolean isCurrentlyStarred) {
        if (isCurrentlyStarred) {
            chatRepository.unstarMessage(chatId, message.getMessageId(), currentUid);
        } else {
            chatRepository.starMessage(chatId, message.getMessageId(), currentUid);
        }
    }

    @Override
    public void onCopy(Message message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("Copied message", message.getText());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDeleteForMe(Message message) {
        chatRepository.deleteForMe(chatId, message.getMessageId(), currentUid);
    }

    @Override
    public void onDeleteForEveryone(Message message) {
        boolean success = chatRepository.deleteForEveryone(chatId, message.getMessageId(), currentUid, message.getSenderId(), message.getSentAt());
        if (!success) {
            Toast.makeText(this, "Cannot delete for everyone", Toast.LENGTH_SHORT).show();
        }
    }
}
