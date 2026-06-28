# Use the optimizing config (set in build.gradle). Manifest-declared Activities,
# Services and BroadcastReceivers are kept automatically by AGP's default rules.

# OkHttp 4.x references optional platform providers that are not present.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn javax.annotation.**
-dontwarn okio.**
-adaptresourcefilenames okhttp3/internal/publicsuffix/PublicSuffixDatabase.gz

# --- DresSecureComms Security & Anti-Heuristic Rules ---

# Keep Android Keystore and Cryptography classes to prevent R8 from stripping 
# reflection calls. Stripped crypto classes can cause runtime crashes, which 
# automated AV sandboxes sometimes flag as "evasive malware behavior".
-keep class java.security.** { *; }
-keep class javax.crypto.** { *; }
-keep class android.security.keystore.** { *; }

# Keep BiometricPrompt to ensure the hardware-backed CryptoObject flow 
# (which fixes the CodeQL warning) is not obfuscated or broken by R8.
-keep class androidx.biometric.** { *; }

# Keep the VirusTotal client network models so OkHttp/Gson can parse the JSON 
# responses correctly without crashing the sandbox analyzer.
-keep class com.dresos.dressecurecomms.net.** { *; }
