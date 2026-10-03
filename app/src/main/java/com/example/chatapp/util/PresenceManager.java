package com.example.chatapp.util;

import android.text.format.DateUtils;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;

/**
 * Manages user presence (online/offline) and typing indicators
 * via Firebase Realtime Database.
 *
 * <p>Presence: /presence/{uid} = {state:"online"|"offline", lastSeen: timestamp}
 * <p>Typing:   /typing/{chatId}/{uid} = true|false
 *
 * <p>Call {@link #goOnline()} in onStart/foreground and
 * {@link #goOffline()} in onStop/background. The onDisconnect handler
 * ensures offline state even on process kill.
 */
public class PresenceManager {

    private static final String TAG = "PresenceManager";
    private static final String REF_PRESENCE = "presence";
    private static final String REF_TYPING = "typing";

    private static final long TYPING_DEBOUNCE_MS = 2000;

    private final DatabaseReference presenceRef;
    private final DatabaseReference connectedRef;
    private final FirebaseDatabase rtdb;
    private ValueEventListener connectedListener;

    // Typing debounce
    private final android.os.Handler typingHandler = new android.os.Handler(
            android.os.Looper.getMainLooper());
    private Runnable clearTypingRunnable;
    private String currentTypingChatId;
    private long lastTypingWrite = 0;

    public PresenceManager() {
        rtdb = FirebaseDatabase.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user != null ? user.getUid() : null;
        if (uid != null) {
            presenceRef = rtdb.getReference(REF_PRESENCE).child(uid);
        } else {
            presenceRef = null;
        }
        connectedRef = rtdb.getReference(".info/connected");
    }

    // ──────────────────────────────────────────────────
    // Presence: online / offline
    // ──────────────────────────────────────────────────

    /**
     * Sets the current user as "online" and registers an onDisconnect
     * handler that flips to "offline" with a server timestamp.
     */
    public void goOnline() {
        if (presenceRef == null) return;

        connectedListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean connected = snapshot.getValue(Boolean.class);
                if (connected != null && connected) {
                    // Set online
                    Map<String, Object> onlineData = new HashMap<>();
                    onlineData.put("state", "online");
                    onlineData.put("lastSeen", ServerValue.TIMESTAMP);
                    presenceRef.setValue(onlineData);

                    // On disconnect, set offline
                    Map<String, Object> offlineData = new HashMap<>();
                    offlineData.put("state", "offline");
                    offlineData.put("lastSeen", ServerValue.TIMESTAMP);
                    presenceRef.onDisconnect().setValue(offlineData);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Connected listener cancelled", error.toException());
            }
        };
        connectedRef.addValueEventListener(connectedListener);
    }

    /**
     * Explicitly sets the current user as "offline".
     * Call in onStop or when the app is backgrounded.
     */
    public void goOffline() {
        if (presenceRef == null) return;

        Map<String, Object> offlineData = new HashMap<>();
        offlineData.put("state", "offline");
        offlineData.put("lastSeen", ServerValue.TIMESTAMP);
        presenceRef.setValue(offlineData);

        if (connectedListener != null) {
            connectedRef.removeEventListener(connectedListener);
            connectedListener = null;
        }
    }

    // ──────────────────────────────────────────────────
    // Presence listener for the other user
    // ──────────────────────────────────────────────────

    public interface PresenceCallback {
        void onPresenceChanged(String state, long lastSeen);
    }

    /**
     * Listens to /presence/{otherUid} in real time.
     * Returns the ValueEventListener so it can be detached later.
     */
    public ValueEventListener listenToPresence(String otherUid, PresenceCallback callback) {
        DatabaseReference otherRef = rtdb.getReference(REF_PRESENCE).child(otherUid);
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String state = "offline";
                long lastSeen = 0;
                if (snapshot.exists()) {
                    Object stateObj = snapshot.child("state").getValue();
                    Object lastSeenObj = snapshot.child("lastSeen").getValue();
                    if (stateObj != null) state = stateObj.toString();
                    if (lastSeenObj instanceof Long) lastSeen = (Long) lastSeenObj;
                }
                callback.onPresenceChanged(state, lastSeen);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Presence listener cancelled", error.toException());
            }
        };
        otherRef.addValueEventListener(listener);
        return listener;
    }

    /**
     * Detaches a presence listener for the given otherUid.
     */
    public void stopListeningToPresence(String otherUid, ValueEventListener listener) {
        if (listener != null) {
            rtdb.getReference(REF_PRESENCE).child(otherUid).removeEventListener(listener);
        }
    }

    /**
     * Formats presence state into a human-readable subtitle string.
     */
    public static String formatPresence(String state, long lastSeen) {
        if ("online".equals(state)) {
            return "Online";
        }
        if (lastSeen > 0) {
            CharSequence relative = DateUtils.getRelativeTimeSpanString(
                    lastSeen,
                    System.currentTimeMillis(),
                    DateUtils.SECOND_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE);
            return "Last seen " + relative;
        }
        return "Offline";
    }

    // ──────────────────────────────────────────────────
    // Typing indicators
    // ──────────────────────────────────────────────────

    /**
     * Signals that the current user is typing in the given chat.
     * Debounced: writes at most once per TYPING_DEBOUNCE_MS.
     * Call from a TextWatcher when text is non-empty.
     */
    public void setTyping(String chatId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();

        long now = System.currentTimeMillis();

        // Debounce: only write if enough time has passed
        if (now - lastTypingWrite < TYPING_DEBOUNCE_MS
                && chatId.equals(currentTypingChatId)) {
            // Reset the clear timer
            resetTypingClearTimer(chatId, uid);
            return;
        }

        lastTypingWrite = now;
        currentTypingChatId = chatId;
        rtdb.getReference(REF_TYPING).child(chatId).child(uid).setValue(true);

        // Schedule clearing after 3 seconds of no new calls
        resetTypingClearTimer(chatId, uid);
    }

    /**
     * Clears the typing indicator immediately.
     * Call when the input becomes empty or the activity stops.
     */
    public void clearTyping(String chatId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();

        rtdb.getReference(REF_TYPING).child(chatId).child(uid).setValue(false);
        currentTypingChatId = null;
        lastTypingWrite = 0;

        if (clearTypingRunnable != null) {
            typingHandler.removeCallbacks(clearTypingRunnable);
            clearTypingRunnable = null;
        }
    }

    private void resetTypingClearTimer(String chatId, String uid) {
        if (clearTypingRunnable != null) {
            typingHandler.removeCallbacks(clearTypingRunnable);
        }
        clearTypingRunnable = () -> {
            rtdb.getReference(REF_TYPING).child(chatId).child(uid).setValue(false);
            currentTypingChatId = null;
            lastTypingWrite = 0;
        };
        typingHandler.postDelayed(clearTypingRunnable, 3000);
    }

    /**
     * Listens to /typing/{chatId}/{otherUid} for the other user's typing state.
     */
    public interface TypingCallback {
        void onTypingChanged(boolean isTyping);
    }

    public ValueEventListener listenToTyping(String chatId, String otherUid,
                                              TypingCallback callback) {
        DatabaseReference ref = rtdb.getReference(REF_TYPING).child(chatId).child(otherUid);
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean typing = snapshot.getValue(Boolean.class);
                callback.onTypingChanged(typing != null && typing);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Typing listener cancelled", error.toException());
            }
        };
        ref.addValueEventListener(listener);
        return listener;
    }

    public void stopListeningToTyping(String chatId, String otherUid,
                                       ValueEventListener listener) {
        if (listener != null) {
            rtdb.getReference(REF_TYPING).child(chatId).child(otherUid)
                    .removeEventListener(listener);
        }
    }
}
