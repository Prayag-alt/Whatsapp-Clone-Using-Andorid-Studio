package com.example.chatapp.model;

import java.util.List;

/**
 * Represents a 1:1 chat conversation stored at /chats/{chatId}.
 * chatId is deterministic: sorted "uidA_uidB".
 */
public class Chat {

    private String chatId;
    private List<String> participants;      // exactly 2 uids
    private String lastMessage;             // preview text
    private long lastMessageAt;             // epoch millis (server timestamp)
    private String lastSenderId;

    // ── Other participant's info (populated client-side, not stored) ──
    private String otherUserName;
    private String otherUserPhoto;

    // ── Pin state (populated client-side from /users/{uid}/chatMeta/{chatId}) ──
    private boolean pinned;
    private long pinnedAt;

    public Chat() {
        // Required empty constructor for Firestore deserialization
    }

    public Chat(String chatId, List<String> participants) {
        this.chatId = chatId;
        this.participants = participants;
        this.lastMessage = "";
        this.lastMessageAt = 0;
        this.lastSenderId = "";
    }

    // ── Getters ──

    public String getChatId() {
        return chatId;
    }

    public List<String> getParticipants() {
        return participants;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public long getLastMessageAt() {
        return lastMessageAt;
    }

    public String getLastSenderId() {
        return lastSenderId;
    }

    public String getOtherUserName() {
        return otherUserName;
    }

    public String getOtherUserPhoto() {
        return otherUserPhoto;
    }

    // ── Setters ──

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public void setParticipants(List<String> participants) {
        this.participants = participants;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public void setLastMessageAt(long lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public void setLastSenderId(String lastSenderId) {
        this.lastSenderId = lastSenderId;
    }

    public void setOtherUserName(String otherUserName) {
        this.otherUserName = otherUserName;
    }

    public void setOtherUserPhoto(String otherUserPhoto) {
        this.otherUserPhoto = otherUserPhoto;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public long getPinnedAt() {
        return pinnedAt;
    }

    public void setPinnedAt(long pinnedAt) {
        this.pinnedAt = pinnedAt;
    }
}
