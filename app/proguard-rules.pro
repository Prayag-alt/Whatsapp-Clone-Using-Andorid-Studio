# ─── Firebase ───
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Keep Firestore model classes (POJOs deserialized via reflection)
-keep class com.example.chatapp.model.** { *; }

# ─── Glide ───
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}
-dontwarn com.bumptech.glide.**

# ─── Cloudinary ───
-keep class com.cloudinary.** { *; }
-dontwarn com.cloudinary.**
-keep class org.cloudinary.** { *; }
-dontwarn org.cloudinary.**

# ─── OkHttp (transitive dependency) ───
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ─── General Android ───
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
