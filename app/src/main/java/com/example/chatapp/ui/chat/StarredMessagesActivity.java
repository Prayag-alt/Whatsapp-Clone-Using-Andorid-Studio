package com.example.chatapp.ui.chat;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.chatapp.adapter.StarredMessagesAdapter;
import com.example.chatapp.data.ChatRepository;
import com.example.chatapp.data.UserRepository;
import com.example.chatapp.databinding.ActivityStarredMessagesBinding;
import com.example.chatapp.model.Message;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class StarredMessagesActivity extends AppCompatActivity {

    private ActivityStarredMessagesBinding binding;
    private StarredMessagesAdapter adapter;
    private ChatRepository chatRepository;
    private UserRepository userRepository; // To resolve names if we wanted to
    private String currentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStarredMessagesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        currentUid = user.getUid();

        chatRepository = new ChatRepository();
        userRepository = new UserRepository();

        setupToolbar();
        setupRecyclerView();
        loadStarredMessages();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new StarredMessagesAdapter(currentUid, message -> {
            if (message.getChatId() != null) {
                // Open ChatActivity for this chat
                Intent intent = new Intent(this, ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_CHAT_ID, message.getChatId());
                
                // Note: to fully populate the other user's name/photo we'd need to parse the 
                // chatId to find the other uid.
                String otherUid = extractOtherUid(message.getChatId(), currentUid);
                intent.putExtra(ChatActivity.EXTRA_OTHER_UID, otherUid);
                startActivity(intent);
            }
        });
        binding.recyclerStarred.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerStarred.setAdapter(adapter);
    }

    private void loadStarredMessages() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.layoutEmpty.setVisibility(View.GONE);

        chatRepository.getStarredMessages(currentUid, new ChatRepository.StarredMessagesCallback() {
            @Override
            public void onStarredMessages(List<Message> messages) {
                binding.progressBar.setVisibility(View.GONE);
                if (messages.isEmpty()) {
                    binding.layoutEmpty.setVisibility(View.VISIBLE);
                } else {
                    binding.layoutEmpty.setVisibility(View.GONE);
                }
                adapter.submitList(messages);
            }

            @Override
            public void onError(Exception e) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(StarredMessagesActivity.this, "Error loading starred messages", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String extractOtherUid(String chatId, String currentUid) {
        // chatId is uid1_uid2
        String[] parts = chatId.split("_");
        if (parts.length == 2) {
            return parts[0].equals(currentUid) ? parts[1] : parts[0];
        }
        return null;
    }
}
