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
