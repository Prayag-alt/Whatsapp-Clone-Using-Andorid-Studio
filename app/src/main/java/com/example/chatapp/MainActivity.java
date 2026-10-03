package com.example.chatapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.chatapp.adapter.ChatListAdapter;
import com.example.chatapp.data.ChatRepository;
import com.example.chatapp.data.UserRepository;
import com.example.chatapp.databinding.ActivityMainBinding;
import com.example.chatapp.model.Chat;
import com.example.chatapp.ui.chat.ChatActivity;
import com.example.chatapp.ui.chat.StarredMessagesActivity;
import com.example.chatapp.ui.chat.UserListActivity;
import com.example.chatapp.util.PresenceManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Main screen: list of conversations for the current user.
 * Displays pinned chats first, then unpinned chats.
 * Supports pinning via long-press and starred messages via the options menu.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ActivityMainBinding binding;
    private ChatListAdapter adapter;
    private ChatRepository chatRepository;
    private UserRepository userRepository;
    private PresenceManager presenceManager;
    private ListenerRegistration chatsListener;
    private ListenerRegistration chatMetaListener;
    private String currentUid;

    // State for combining /chats and /users/{uid}/chatMeta
    private List<Chat> currentChats = new ArrayList<>();
    private Map<String, Map<String, Object>> currentChatMeta;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        currentUid = user.getUid();

        chatRepository = new ChatRepository();
        userRepository = new UserRepository();
        presenceManager = new PresenceManager();

        setupRecyclerView();
        setupFab();
    }

    @Override
    protected void onStart() {
        super.onStart();
        attachChatsListener();
        attachChatMetaListener();
        presenceManager.goOnline();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachChatsListener();
        detachChatMetaListener();
        presenceManager.goOffline();
    }

    // ── Menu ──

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_starred) {
            startActivity(new Intent(this, StarredMessagesActivity.class));
            return true;
        } else if (id == R.id.action_logout) {
            FirebaseAuth.getInstance().signOut();
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── Setup ──

    private void setupRecyclerView() {
        adapter = new ChatListAdapter(new ChatListAdapter.OnChatClickListener() {
            @Override
            public void onChatClick(Chat chat) {
                openChat(chat);
            }

            @Override
            public void onChatLongClick(Chat chat) {
                chatRepository.togglePin(currentUid, chat.getChatId(), chat.isPinned());
                Toast.makeText(MainActivity.this,
                        chat.isPinned() ? "Chat unpinned" : "Chat pinned",
                        Toast.LENGTH_SHORT).show();
            }
        });
        binding.recyclerChats.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerChats.setAdapter(adapter);
    }

    private void setupFab() {
        binding.fabNewChat.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserListActivity.class);
            startActivity(intent);
        });
    }

    // ── Listeners ──

    private void attachChatsListener() {
        if (currentUid == null) return;
        chatsListener = chatRepository.listenToChats(currentUid, new ChatRepository.ChatsListener() {
            @Override
            public void onChatsUpdated(List<Chat> chats) {
                currentChats = chats;
                mergeAndSubmit();

                for (Chat chat : chats) {
                    if (chat.getLastSenderId() != null && !chat.getLastSenderId().equals(currentUid) && !chat.getLastSenderId().isEmpty()) {
                        chatRepository.markMessagesAsDelivered(chat.getChatId(), chat.getLastSenderId());
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error listening to chats", e);
            }
        });
    }

    private void detachChatsListener() {
        if (chatsListener != null) {
            chatsListener.remove();
            chatsListener = null;
        }
    }

    private void attachChatMetaListener() {
        if (currentUid == null) return;
        chatMetaListener = chatRepository.listenToChatMeta(currentUid, new ChatRepository.ChatMetaListener() {
            @Override
            public void onMetaUpdated(Map<String, Map<String, Object>> metaMap) {
                currentChatMeta = metaMap;
                mergeAndSubmit();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error listening to chat meta", e);
            }
        });
    }

    private void detachChatMetaListener() {
        if (chatMetaListener != null) {
            chatMetaListener.remove();
            chatMetaListener = null;
        }
    }

    // ── Combine & Sort ──

    private void mergeAndSubmit() {
        if (currentChats == null) return;

        List<Chat> merged = new ArrayList<>(currentChats);

        // Apply metadata (pin state)
        if (currentChatMeta != null) {
            for (Chat chat : merged) {
                Map<String, Object> meta = currentChatMeta.get(chat.getChatId());
                if (meta != null) {
                    Boolean pinned = (Boolean) meta.get("pinned");
                    if (pinned != null) chat.setPinned(pinned);

                    // PinnedAt comes in as a Timestamp from Firestore, handle safely if needed.
                    // If it's a Long or Timestamp:
                    Object pinnedAtObj = meta.get("pinnedAt");
                    if (pinnedAtObj instanceof Long) {
                        chat.setPinnedAt((Long) pinnedAtObj);
                    } else if (pinnedAtObj instanceof com.google.firebase.Timestamp) {
                        chat.setPinnedAt(((com.google.firebase.Timestamp) pinnedAtObj).toDate().getTime());
                    }
                } else {
                    chat.setPinned(false);
                }
            }
        }

        // Sort: pinned true (pinnedAt desc) > pinned false (lastMessageAt desc)
        Collections.sort(merged, (a, b) -> {
            if (a.isPinned() && !b.isPinned()) return -1;
            if (!a.isPinned() && b.isPinned()) return 1;
            if (a.isPinned() && b.isPinned()) {
                return Long.compare(b.getPinnedAt(), a.getPinnedAt());
            } else {
                return Long.compare(b.getLastMessageAt(), a.getLastMessageAt());
            }
        });

        resolveOtherUsers(merged);
    }

    private void resolveOtherUsers(List<Chat> chats) {
        if (chats.isEmpty()) {
            adapter.submitList(new ArrayList<>());
            updateEmptyState(true);
            return;
        }

        final int[] remaining = {chats.size()};

        for (Chat chat : chats) {
            String otherUid = getOtherUid(chat);
            if (otherUid == null) {
                remaining[0]--;
                if (remaining[0] == 0) submitChats(chats);
                continue;
            }

            userRepository.getUser(otherUid, new UserRepository.UserCallback() {
                @Override
                public void onUserLoaded(com.example.chatapp.model.User user) {
                    if (user != null) {
                        chat.setOtherUserName(user.getName());
                        chat.setOtherUserPhoto(user.getProfileImageUrl());
                    } else {
                        chat.setOtherUserName("Unknown");
                    }
                    remaining[0]--;
                    if (remaining[0] == 0) submitChats(chats);
                }

                @Override
                public void onError(Exception e) {
                    chat.setOtherUserName("Unknown");
                    remaining[0]--;
                    if (remaining[0] == 0) submitChats(chats);
                }
            });
        }
    }

    private void submitChats(List<Chat> chats) {
        runOnUiThread(() -> {
            adapter.submitList(new ArrayList<>(chats));
            updateEmptyState(chats.isEmpty());
        });
    }

    private void updateEmptyState(boolean empty) {
        binding.layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerChats.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.txtSectionHeader.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private String getOtherUid(Chat chat) {
        if (chat.getParticipants() == null) return null;
        for (String uid : chat.getParticipants()) {
            if (!uid.equals(currentUid)) return uid;
        }
        return null;
    }

    private void openChat(Chat chat) {
        String otherUid = getOtherUid(chat);
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CHAT_ID, chat.getChatId());
        intent.putExtra(ChatActivity.EXTRA_OTHER_UID, otherUid);
        intent.putExtra(ChatActivity.EXTRA_OTHER_NAME, chat.getOtherUserName());
        intent.putExtra(ChatActivity.EXTRA_OTHER_PHOTO, chat.getOtherUserPhoto());
        startActivity(intent);
    }
}
