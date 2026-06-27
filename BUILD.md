# Building DresSecureComms from scratch

Linux (Debian) instructions. You do NOT install Gradle by hand: the project ships a
Gradle wrapper (`./gradlew`) that downloads the correct Gradle (8.7) on first run.
You only need a JDK and the Android SDK.

## 1. JDK 17
```
sudo apt update
sudo apt install -y openjdk-17-jdk
java -version            # should report 17
```

## 2. Android SDK command-line tools
```
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
# Download "Command line tools only" (Linux) from https://developer.android.com/studio
# then unzip so the path is ~/android-sdk/cmdline-tools/latest/bin
unzip ~/Downloads/commandlinetools-linux-*_latest.zip
mv cmdline-tools latest

# environment (add to ~/.bashrc to make permanent)
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$HOME/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

# accept licenses and install the packages this project needs
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

## 3. Point the project at your SDK
From the project root, either rely on `ANDROID_HOME` above, or create `local.properties`:
```
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

## 4. Build a debug APK
```
chmod +x gradlew
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 5. Build and sign a release APK
```
# one-time: make a release key (or reuse your DresOS release keystore)
keytool -genkeypair -v -keystore dres-release.jks -alias dres \
  -keyalg RSA -keysize 4096 -validity 10000

./gradlew assembleRelease
# output (unsigned): app/build/outputs/apk/release/app-release-unsigned.apk

zipalign -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk dsc-aligned.apk
apksigner sign --ks dres-release.jks --out DresSecureComms.apk dsc-aligned.apk
apksigner verify --print-certs DresSecureComms.apk
```

## Size
The app uses only lightweight libraries and limits native ABIs to arm64-v8a and
armeabi-v7a. The release APK lands comfortably under 30MB, so it qualifies for
IzzyOnDroid on size.
