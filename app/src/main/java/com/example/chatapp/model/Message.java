package com.example.chatapp.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single message stored at /chats/{chatId}/messages/{messageId}.
 */
public class Message {

    private String messageId;
    private String senderId;
    private String type;                // "text" or "image"
    private String text;
    private String imageUrl;            // Cloudinary URL (for type:"image")
    private long sentAt;                // epoch millis (server timestamp)
    private String status;              // "sent" / "delivered" / "read"

    // Phase 4 fields
    private List<String> starredBy;     // uids who starred this message
    private String replyToMessageId;    // nullable — the message being replied to
    private String forwardedFrom;       // nullable — "chatId/messageId" of the original
    private List<String> deletedFor;    // uids who "deleted for me"
    private boolean deleted;            // true → "deleted for everyone"

    // Transient field (populated client-side, not stored in Firestore)
    private String replyToText;         // preview text of the replied-to message
    private String replyToSenderName;   // sender name of the replied-to message

    // Transient: the chatId this message belongs to (needed for starred-messages screen)
    private String chatId;

    public Message() {
        // Required empty constructor for Firestore deserialization
    }

    public Message(String senderId, String type, String text, long sentAt, String status) {
        this.senderId = senderId;
        this.type = type;
        this.text = text;
        this.sentAt = sentAt;
        this.status = status;
    }

    // ── Getters ──

    public String getMessageId() {
        return messageId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public long getSentAt() {
        return sentAt;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getStarredBy() {
        return starredBy;
    }

    public String getReplyToMessageId() {
        return replyToMessageId;
    }

    public String getForwardedFrom() {
        return forwardedFrom;
    }

    public List<String> getDeletedFor() {
        return deletedFor;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public String getReplyToText() {
        return replyToText;
    }

    public String getReplyToSenderName() {
        return replyToSenderName;
    }

    public String getChatId() {
        return chatId;
    }

    // ── Setters ──

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setSentAt(long sentAt) {
        this.sentAt = sentAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setStarredBy(List<String> starredBy) {
        this.starredBy = starredBy;
    }

    public void setReplyToMessageId(String replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }

    public void setForwardedFrom(String forwardedFrom) {
        this.forwardedFrom = forwardedFrom;
    }

    public void setDeletedFor(List<String> deletedFor) {
        this.deletedFor = deletedFor;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public void setReplyToText(String replyToText) {
        this.replyToText = replyToText;
    }

    public void setReplyToSenderName(String replyToSenderName) {
        this.replyToSenderName = replyToSenderName;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    // ── Helpers ──

    public boolean isStarredBy(String uid) {
        return starredBy != null && starredBy.contains(uid);
    }

    public boolean isDeletedFor(String uid) {
        return deletedFor != null && deletedFor.contains(uid);
    }
}
