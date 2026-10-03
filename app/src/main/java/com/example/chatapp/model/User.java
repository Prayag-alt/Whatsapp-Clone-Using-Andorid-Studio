package com.example.chatapp.model;

/**
 * Represents a user stored at /users/{uid} in Firestore.
 */
public class User {

    private String uid;
    private String name;
    private String email;
    private String profileImageUrl;
    private String status;

    public User() {
        // Required empty constructor for Firestore deserialization
    }

    public User(String uid, String name, String email) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.profileImageUrl = "";
        this.status = "";
    }

    // ── Getters ──

    public String getUid() {
        return uid;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public String getStatus() {
        return status;
    }

    // ── Setters ──

    public void setUid(String uid) {
        this.uid = uid;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
