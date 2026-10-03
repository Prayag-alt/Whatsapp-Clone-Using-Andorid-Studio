package com.example.chatapp.ui.chat;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.chatapp.adapter.UserListAdapter;
import com.example.chatapp.data.ChatRepository;
import com.example.chatapp.data.UserRepository;
import com.example.chatapp.databinding.ActivityForwardBinding;
import com.example.chatapp.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ForwardActivity extends AppCompatActivity {

    private static final String TAG = "ForwardActivity";

    public static final String EXTRA_ORIGINAL_TEXT = "extra_original_text";
    public static final String EXTRA_ORIGINAL_IMAGE_URL = "extra_original_image_url";
    public static final String EXTRA_ORIGINAL_TYPE = "extra_original_type";
    public static final String EXTRA_ORIGINAL_CHAT_ID = "extra_original_chat_id";
    public static final String EXTRA_ORIGINAL_MESSAGE_ID = "extra_original_message_id";

    private ActivityForwardBinding binding;
    private UserListAdapter adapter;
    private UserRepository userRepository;
    private ChatRepository chatRepository;
    private String currentUid;

    private String originalText;
    private String originalImageUrl;
    private String originalType;
    private String originalChatId;
    private String originalMessageId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForwardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        currentUid = user.getUid();

        originalText = getIntent().getStringExtra(EXTRA_ORIGINAL_TEXT);
        originalImageUrl = getIntent().getStringExtra(EXTRA_ORIGINAL_IMAGE_URL);
        originalType = getIntent().getStringExtra(EXTRA_ORIGINAL_TYPE);
        originalChatId = getIntent().getStringExtra(EXTRA_ORIGINAL_CHAT_ID);
        originalMessageId = getIntent().getStringExtra(EXTRA_ORIGINAL_MESSAGE_ID);

        if (originalChatId == null || originalMessageId == null) {
            Toast.makeText(this, "Error: Missing message data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

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

    private void onUserTapped(User user) {
        binding.progressBar.setVisibility(View.VISIBLE);

        chatRepository.getOrCreateChat(currentUid, user.getUid(), new ChatRepository.ChatCreatedCallback() {
            @Override
            public void onChatReady(String targetChatId) {
                chatRepository.forwardMessage(targetChatId, currentUid,
                        originalText, originalImageUrl, originalType,
                        originalChatId, originalMessageId,
                        new ChatRepository.SendMessageCallback() {
                            @Override
                            public void onSuccess() {
                                runOnUiThread(() -> {
                                    binding.progressBar.setVisibility(View.GONE);
                                    Toast.makeText(ForwardActivity.this, "Message forwarded", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                            }

                            @Override
                            public void onError(Exception e) {
                                runOnUiThread(() -> {
                                    binding.progressBar.setVisibility(View.GONE);
                                    Toast.makeText(ForwardActivity.this, "Error forwarding message", Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(ForwardActivity.this, "Error creating chat", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
