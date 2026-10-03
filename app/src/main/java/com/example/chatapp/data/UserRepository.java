package com.example.chatapp.data;

import com.example.chatapp.model.User;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Single point of access for the /users Firestore collection.
 */
public class UserRepository {

    private static final String COLLECTION_USERS = "users";
    private final FirebaseFirestore db;

    public UserRepository() {
        db = FirebaseFirestore.getInstance();
    }

    /**
     * Callback for async operations returning a list of users.
     */
    public interface UsersCallback {
        void onUsersLoaded(List<User> users);
        void onError(Exception e);
    }

    /**
     * Callback for async operations returning a single user.
     */
    public interface UserCallback {
        void onUserLoaded(User user);
        void onError(Exception e);
    }

    /**
     * Fetches all users except the one with the given uid.
     * One-shot query (no real-time listener needed for the user list).
     */
    public void getAllUsersExcept(String currentUid, UsersCallback callback) {
        db.collection(COLLECTION_USERS)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<User> users = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        User user = doc.toObject(User.class);
                        user.setUid(doc.getId());
                        if (!doc.getId().equals(currentUid)) {
                            users.add(user);
                        }
                    }
                    callback.onUsersLoaded(users);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Fetches a single user by uid.
     */
    public void getUser(String uid, UserCallback callback) {
        db.collection(COLLECTION_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            user.setUid(doc.getId());
                        }
                        callback.onUserLoaded(user);
                    } else {
                        callback.onUserLoaded(null);
                    }
                })
                .addOnFailureListener(callback::onError);
    }
}
