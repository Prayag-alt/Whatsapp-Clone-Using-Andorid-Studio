package com.example.chatapp.data;

import com.example.chatapp.model.Chat;
import com.example.chatapp.model.Message;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single point of access for /chats, /chats/{chatId}/messages,
 * and /users/{uid}/chatMeta/{chatId}.
 */
public class ChatRepository {

    private static final String COLLECTION_CHATS = "chats";
    private static final String SUBCOLLECTION_MESSAGES = "messages";
    private static final String COLLECTION_USERS = "users";
    private static final String SUBCOLLECTION_CHAT_META = "chatMeta";
    private static final int MESSAGE_LIMIT = 50;
    private static final long DELETE_FOR_EVERYONE_WINDOW_MS = 60 * 60 * 1000; // 1 hour

    private final FirebaseFirestore db;

    public ChatRepository() {
        db = FirebaseFirestore.getInstance();
    }

    // ──────────────────────────────────────────────────
    // Callbacks
    // ──────────────────────────────────────────────────

    public interface ChatsListener {
        void onChatsUpdated(List<Chat> chats);
        void onError(Exception e);
    }

    public interface MessagesListener {
        void onMessagesUpdated(List<Message> messages);
        void onError(Exception e);
    }

    public interface ChatCreatedCallback {
        void onChatReady(String chatId);
        void onError(Exception e);
    }

    public interface SendMessageCallback {
        void onSuccess();
        void onError(Exception e);
    }

    public interface ChatMetaListener {
        void onMetaUpdated(Map<String, Map<String, Object>> metaMap);
        void onError(Exception e);
    }

    public interface StarredMessagesCallback {
        void onStarredMessages(List<Message> messages);
        void onError(Exception e);
    }

    public interface MessageCallback {
        void onMessage(Message message);
        void onError(Exception e);
    }

    // ──────────────────────────────────────────────────
    // Chat list — real-time listener
    // ──────────────────────────────────────────────────

    public ListenerRegistration listenToChats(String currentUid, ChatsListener listener) {
        return db.collection(COLLECTION_CHATS)
                .whereArrayContains("participants", currentUid)
                .orderBy("lastMessageAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }
                    if (snapshots == null) return;

                    List<Chat> chats = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Chat chat = doc.toObject(Chat.class);
                        if (chat != null) {
                            chat.setChatId(doc.getId());
                            chats.add(chat);
                        }
                    }
                    listener.onChatsUpdated(chats);
                });
    }

    // ──────────────────────────────────────────────────
    // Chat Meta — per-user pinning
    // ──────────────────────────────────────────────────

    /**
     * Listens to /users/{uid}/chatMeta for all chat metadata (pinned state).
     */
    public ListenerRegistration listenToChatMeta(String uid, ChatMetaListener listener) {
        return db.collection(COLLECTION_USERS)
                .document(uid)
                .collection(SUBCOLLECTION_CHAT_META)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }
                    if (snapshots == null) return;

                    Map<String, Map<String, Object>> metaMap = new HashMap<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        metaMap.put(doc.getId(), doc.getData());
                    }
                    listener.onMetaUpdated(metaMap);
                });
    }

    /**
     * Toggles the pin state for a chat in the current user's chatMeta.
     */
    public void togglePin(String uid, String chatId, boolean currentlyPinned) {
        DocumentReference ref = db.collection(COLLECTION_USERS)
                .document(uid)
                .collection(SUBCOLLECTION_CHAT_META)
                .document(chatId);

        if (currentlyPinned) {
            // Unpin
            Map<String, Object> data = new HashMap<>();
            data.put("pinned", false);
            data.put("pinnedAt", 0L);
            ref.set(data);
        } else {
            // Pin
            Map<String, Object> data = new HashMap<>();
            data.put("pinned", true);
            data.put("pinnedAt", FieldValue.serverTimestamp());
            ref.set(data);
        }
    }

    // ──────────────────────────────────────────────────
    // Messages — real-time listener
    // ──────────────────────────────────────────────────

    public ListenerRegistration listenToMessages(String chatId, MessagesListener listener) {
        return db.collection(COLLECTION_CHATS)
                .document(chatId)
                .collection(SUBCOLLECTION_MESSAGES)
                .orderBy("sentAt", Query.Direction.ASCENDING)
                .limitToLast(MESSAGE_LIMIT)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }
                    if (snapshots == null) return;

                    List<Message> messages = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Message msg = doc.toObject(Message.class);
                        if (msg != null) {
                            msg.setMessageId(doc.getId());
                            messages.add(msg);
                        }
                    }
                    listener.onMessagesUpdated(messages);
                });
    }

    // ──────────────────────────────────────────────────
    // Create chat
    // ──────────────────────────────────────────────────

    public void getOrCreateChat(String uid1, String uid2, ChatCreatedCallback callback) {
        String chatId = generateChatId(uid1, uid2);
        DocumentReference chatRef = db.collection(COLLECTION_CHATS).document(chatId);

        chatRef.get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                callback.onChatReady(chatId);
            } else {
                Map<String, Object> chatData = new HashMap<>();
                chatData.put("chatId", chatId);
                chatData.put("participants", Arrays.asList(uid1, uid2));
                chatData.put("lastMessage", "");
                chatData.put("lastMessageAt", 0L);
                chatData.put("lastSenderId", "");

                chatRef.set(chatData)
                        .addOnSuccessListener(aVoid -> callback.onChatReady(chatId))
                        .addOnFailureListener(callback::onError);
            }
        }).addOnFailureListener(callback::onError);
    }

    public static String generateChatId(String uid1, String uid2) {
        if (uid1.compareTo(uid2) < 0) {
            return uid1 + "_" + uid2;
        } else {
            return uid2 + "_" + uid1;
        }
    }

    // ──────────────────────────────────────────────────
    // Send message (with optional reply/forward metadata)
    // ──────────────────────────────────────────────────

    public void sendTextMessage(String chatId, String senderId, String text,
                                SendMessageCallback callback) {
        sendTextMessage(chatId, senderId, text, null, null, callback);
    }

    /**
     * Sends a text message with optional replyToMessageId and forwardedFrom.
     */
    public void sendTextMessage(String chatId, String senderId, String text,
                                String replyToMessageId, String forwardedFrom,
                                SendMessageCallback callback) {

        DocumentReference chatRef = db.collection(COLLECTION_CHATS).document(chatId);
        DocumentReference msgRef = chatRef.collection(SUBCOLLECTION_MESSAGES).document();

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("messageId", msgRef.getId());
        messageData.put("senderId", senderId);
        messageData.put("type", "text");
        messageData.put("text", text);
        messageData.put("sentAt", FieldValue.serverTimestamp());
        messageData.put("status", "sent");
        messageData.put("starredBy", new ArrayList<>());
        messageData.put("deletedFor", new ArrayList<>());
        messageData.put("deleted", false);

        if (replyToMessageId != null) {
            messageData.put("replyToMessageId", replyToMessageId);
        }
        if (forwardedFrom != null) {
            messageData.put("forwardedFrom", forwardedFrom);
        }

        msgRef.set(messageData).addOnSuccessListener(aVoid -> {
            Map<String, Object> chatUpdate = new HashMap<>();
            chatUpdate.put("lastMessage", text);
            chatUpdate.put("lastMessageAt", FieldValue.serverTimestamp());
            chatUpdate.put("lastSenderId", senderId);

            chatRef.update(chatUpdate)
                    .addOnSuccessListener(aVoid2 -> callback.onSuccess())
                    .addOnFailureListener(callback::onError);
        }).addOnFailureListener(callback::onError);
    }

    // ──────────────────────────────────────────────────
    // Send image message
    // ──────────────────────────────────────────────────

    /**
     * Sends an image message with the given Cloudinary URL.
     */
    public void sendImageMessage(String chatId, String senderId, String imageUrl,
                                  String replyToMessageId, SendMessageCallback callback) {

        DocumentReference chatRef = db.collection(COLLECTION_CHATS).document(chatId);
        DocumentReference msgRef = chatRef.collection(SUBCOLLECTION_MESSAGES).document();

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("messageId", msgRef.getId());
        messageData.put("senderId", senderId);
        messageData.put("type", "image");
        messageData.put("text", "");
        messageData.put("imageUrl", imageUrl);
        messageData.put("sentAt", FieldValue.serverTimestamp());
        messageData.put("status", "sent");
        messageData.put("starredBy", new ArrayList<>());
        messageData.put("deletedFor", new ArrayList<>());
        messageData.put("deleted", false);

        if (replyToMessageId != null) {
            messageData.put("replyToMessageId", replyToMessageId);
        }

        msgRef.set(messageData).addOnSuccessListener(aVoid -> {
            Map<String, Object> chatUpdate = new HashMap<>();
            chatUpdate.put("lastMessage", "\uD83D\uDCF7 Photo");
            chatUpdate.put("lastMessageAt", FieldValue.serverTimestamp());
            chatUpdate.put("lastSenderId", senderId);

            chatRef.update(chatUpdate)
                    .addOnSuccessListener(aVoid2 -> callback.onSuccess())
                    .addOnFailureListener(callback::onError);
        }).addOnFailureListener(callback::onError);
    }

    // ──────────────────────────────────────────────────
    // Star / Unstar
    // ──────────────────────────────────────────────────

    public void starMessage(String chatId, String messageId, String uid) {
        db.collection(COLLECTION_CHATS)
                .document(chatId)
                .collection(SUBCOLLECTION_MESSAGES)
                .document(messageId)
                .update("starredBy", FieldValue.arrayUnion(uid));
    }

    public void unstarMessage(String chatId, String messageId, String uid) {
        db.collection(COLLECTION_CHATS)
                .document(chatId)
                .collection(SUBCOLLECTION_MESSAGES)
                .document(messageId)
                .update("starredBy", FieldValue.arrayRemove(uid));
    }

    // ──────────────────────────────────────────────────
    // Starred messages — collectionGroup query
    // ──────────────────────────────────────────────────

    /**
     * Queries all messages across all chats that are starred by the given uid.
     * Requires a Firestore composite index on the "messages" collectionGroup.
     */
    public void getStarredMessages(String uid, StarredMessagesCallback callback) {
        db.collectionGroup(SUBCOLLECTION_MESSAGES)
                .whereArrayContains("starredBy", uid)
                .orderBy("sentAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Message> messages = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Message msg = doc.toObject(Message.class);
                        if (msg != null) {
                            msg.setMessageId(doc.getId());
                            // Extract chatId from the document path:
                            // chats/{chatId}/messages/{messageId}
                            String path = doc.getReference().getPath();
                            String[] parts = path.split("/");
                            if (parts.length >= 2) {
                                msg.setChatId(parts[1]);
                            }
                            messages.add(msg);
                        }
                    }
                    callback.onStarredMessages(messages);
                })
                .addOnFailureListener(callback::onError);
    }

    // ──────────────────────────────────────────────────
    // Delete for me
    // ──────────────────────────────────────────────────

    public void deleteForMe(String chatId, String messageId, String uid) {
        db.collection(COLLECTION_CHATS)
                .document(chatId)
                .collection(SUBCOLLECTION_MESSAGES)
                .document(messageId)
                .update("deletedFor", FieldValue.arrayUnion(uid));
    }

    // ──────────────────────────────────────────────────
    // Delete for everyone (sender only, within 1 hour)
    // ──────────────────────────────────────────────────

    /**
     * Deletes a message for everyone. Only allowed if the current user is the
     * sender and the message was sent less than 1 hour ago.
     *
     * @return false if not allowed (wrong sender or expired)
     */
    public boolean deleteForEveryone(String chatId, String messageId,
                                      String currentUid, String senderId,
                                      long sentAt) {
        // Guard: sender only
        if (!currentUid.equals(senderId)) return false;

        // Guard: within 1 hour
        long now = System.currentTimeMillis();
        if (sentAt > 0 && (now - sentAt) > DELETE_FOR_EVERYONE_WINDOW_MS) return false;

        Map<String, Object> updates = new HashMap<>();
        updates.put("deleted", true);
        updates.put("text", "");
        updates.put("type", "text");

        db.collection(COLLECTION_CHATS)
                .document(chatId)
                .collection(SUBCOLLECTION_MESSAGES)
                .document(messageId)
                .update(updates);

        return true;
    }

    // ──────────────────────────────────────────────────
    // Forward message
    // ──────────────────────────────────────────────────

    /**
     * Forwards a message by creating a new message in the target chat.
     * Supports both text and image message types.
     */
    public void forwardMessage(String targetChatId, String senderId,
                                String originalText, String originalImageUrl,
                                String originalType, String originalChatId,
                                String originalMessageId,
                                SendMessageCallback callback) {
        String forwardedFrom = originalChatId + "/" + originalMessageId;

        if ("image".equals(originalType) && originalImageUrl != null) {
            // Forward as image: reuse the same Cloudinary URL
            DocumentReference chatRef = db.collection(COLLECTION_CHATS).document(targetChatId);
            DocumentReference msgRef = chatRef.collection(SUBCOLLECTION_MESSAGES).document();

            Map<String, Object> messageData = new HashMap<>();
            messageData.put("messageId", msgRef.getId());
            messageData.put("senderId", senderId);
            messageData.put("type", "image");
            messageData.put("text", "");
            messageData.put("imageUrl", originalImageUrl);
            messageData.put("sentAt", FieldValue.serverTimestamp());
            messageData.put("status", "sent");
            messageData.put("starredBy", new ArrayList<>());
            messageData.put("deletedFor", new ArrayList<>());
            messageData.put("deleted", false);
            messageData.put("forwardedFrom", forwardedFrom);

            msgRef.set(messageData).addOnSuccessListener(aVoid -> {
                Map<String, Object> chatUpdate = new HashMap<>();
                chatUpdate.put("lastMessage", "\uD83D\uDCF7 Photo");
                chatUpdate.put("lastMessageAt", FieldValue.serverTimestamp());
                chatUpdate.put("lastSenderId", senderId);

                chatRef.update(chatUpdate)
                        .addOnSuccessListener(aVoid2 -> callback.onSuccess())
                        .addOnFailureListener(callback::onError);
            }).addOnFailureListener(callback::onError);
        } else {
            sendTextMessage(targetChatId, senderId, originalText, null, forwardedFrom, callback);
        }
    }

    // ──────────────────────────────────────────────────
    // Fetch single message (for reply preview)
    // ──────────────────────────────────────────────────

    public void getMessage(String chatId, String messageId, MessageCallback callback) {
        db.collection(COLLECTION_CHATS)
                .document(chatId)
                .collection(SUBCOLLECTION_MESSAGES)
                .document(messageId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Message msg = doc.toObject(Message.class);
                        if (msg != null) {
                            msg.setMessageId(doc.getId());
                        }
                        callback.onMessage(msg);
                    } else {
                        callback.onMessage(null);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    // ──────────────────────────────────────────────────
    // Read receipts
    // ──────────────────────────────────────────────────

    public void markMessagesAsDelivered(String chatId, String otherSenderUid) {
        db.collection(COLLECTION_CHATS)
                .document(chatId)
                .collection(SUBCOLLECTION_MESSAGES)
                .whereEqualTo("senderId", otherSenderUid)
                .whereEqualTo("status", "sent")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        doc.getReference().update("status", "delivered");
                    }
                });
    }

    public void markMessagesAsRead(String chatId, String otherSenderUid) {
        db.collection(COLLECTION_CHATS)
                .document(chatId)
                .collection(SUBCOLLECTION_MESSAGES)
                .whereEqualTo("senderId", otherSenderUid)
                .whereIn("status", Arrays.asList("sent", "delivered"))
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        doc.getReference().update("status", "read");
                    }
                });
    }
}
