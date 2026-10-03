package com.example.chatapp.ui.chat;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.chatapp.adapter.UserListAdapter;
import com.example.chatapp.data.ChatRepository;
import com.example.chatapp.data.UserRepository;
import com.example.chatapp.databinding.ActivityUserListBinding;
import com.example.chatapp.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Lists all registered users (except the current user) so the user can
 * start a new 1:1 chat. Tapping a user creates or opens the chat via
 * ChatRepository.getOrCreateChat().
 */
public class UserListActivity extends AppCompatActivity {

    private static final String TAG = "UserListActivity";

    private ActivityUserListBinding binding;
    private UserListAdapter adapter;
    private UserRepository userRepository;
    private ChatRepository chatRepository;
    private String currentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        currentUid = user.getUid();

        userRepository = new UserRepository();
        chatRepository = new ChatRepository();

        setupToolbar();
        setupRecyclerView();
        loadUsers();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new UserListAdapter(this::onUserTapped);
        binding.recyclerUsers.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerUsers.setAdapter(adapter);
    }

    private void loadUsers() {
        binding.progressBar.setVisibility(View.VISIBLE);

        userRepository.getAllUsersExcept(currentUid, new UserRepository.UsersCallback() {
            @Override
            public void onUsersLoaded(java.util.List<User> users) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    adapter.submitList(users);
                });
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error loading users", e);
                runOnUiThread(() -> binding.progressBar.setVisibility(View.GONE));
            }
        });
    }

    /**
     * When a user is tapped, create (or find) the chat and open ChatActivity.
     */
    private void onUserTapped(User user) {
        chatRepository.getOrCreateChat(currentUid, user.getUid(),
                new ChatRepository.ChatCreatedCallback() {
                    @Override
                    public void onChatReady(String chatId) {
                        runOnUiThread(() -> {
                            Intent intent = new Intent(UserListActivity.this, ChatActivity.class);
                            intent.putExtra(ChatActivity.EXTRA_CHAT_ID, chatId);
                            intent.putExtra(ChatActivity.EXTRA_OTHER_UID, user.getUid());
                            intent.putExtra(ChatActivity.EXTRA_OTHER_NAME, user.getName());
                            intent.putExtra(ChatActivity.EXTRA_OTHER_PHOTO, user.getProfileImageUrl());
                            startActivity(intent);
                            finish();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Error creating/finding chat", e);
                    }
                });
    }
}
