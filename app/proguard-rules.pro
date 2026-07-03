-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn javax.annotation.**
-dontwarn okio.**
-adaptresourcefilenames okhttp3/internal/publicsuffix/PublicSuffixDatabase.gz

-dontobfuscate

-keep class java.security.** { *; }
-keep class javax.crypto.** { *; }
-keep class android.security.keystore.** { *; }
-keep class androidx.biometric.** { *; }
-keep class com.dresos.dressecurecomms.net.** { *; }

-keep class com.google.android.mms.** { *; }
-keep class com.android.mms.** { *; }
-keep class com.klinker.android.** { *; }
-keep class com.android.i18n.** { *; }
-keep class com.google.android.collect.** { *; }
-keep class org.w3c.dom.** { *; }
-dontwarn com.android.**
-dontwarn com.google.android.mms.**
-dontwarn org.w3c.dom.**
