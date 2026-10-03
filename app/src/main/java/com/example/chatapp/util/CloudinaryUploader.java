package com.example.chatapp.util;

import android.content.Context;
import android.util.Log;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Wraps the Cloudinary Android SDK for uploading images.
 * Must call init() once before uploading (typically in Application.onCreate
 * or before first use).
 *
 * Cloudinary credentials are configured via cloudinary_url in strings.xml
 * or via a Map passed to init().
 */
public class CloudinaryUploader {

    private static final String TAG = "CloudinaryUploader";
    private static boolean initialized = false;

    public interface UploadListener {
        void onSuccess(String imageUrl);
        void onError(String errorMessage);
    }

    /**
     * Initialize Cloudinary MediaManager. Call once.
     * Expects res/values/strings.xml to have:
     *   <string name="cloudinary_cloud_name">YOUR_CLOUD_NAME</string>
     *
     * Or pass config directly.
     */
    public static void init(Context context, String cloudName) {
        if (initialized) return;
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("cloud_name", cloudName);
            config.put("secure", true);
            MediaManager.init(context, config);
            initialized = true;
        } catch (Exception e) {
            Log.e(TAG, "Cloudinary init failed", e);
        }
    }

    /**
     * Checks if the MediaManager has been initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Uploads a compressed image file to Cloudinary using unsigned upload.
     *
     * @param file           The compressed image file
     * @param folder         Cloudinary folder (e.g., "chat_images")
     * @param uploadPreset   The unsigned upload preset name configured in Cloudinary
     * @param listener       Callback for success/error
     */
    public static void uploadImage(File file, String folder, String uploadPreset,
                                    UploadListener listener) {
        if (!initialized) {
            listener.onError("Cloudinary not initialized");
            return;
        }

        MediaManager.get().upload(file.getAbsolutePath())
                .unsigned(uploadPreset)
                .option("folder", folder)
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                        Log.d(TAG, "Upload started: " + requestId);
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        // Could be used for a progress bar
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String secureUrl = (String) resultData.get("secure_url");
                        if (secureUrl != null) {
                            listener.onSuccess(secureUrl);
                        } else {
                            String url = (String) resultData.get("url");
                            listener.onSuccess(url != null ? url : "");
                        }
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Log.e(TAG, "Upload error: " + error.getDescription());
                        listener.onError(error.getDescription());
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                        Log.w(TAG, "Upload rescheduled: " + error.getDescription());
                    }
                })
                .dispatch();
    }
}
