#!/bin/bash
set -e

# --- Configuration ---
KEYSTORE_FILE="debug.keystore"
KEYSTORE_PROPS="keystore.properties"
KEY_ALIAS="androiddebugkey"
KEY_PASS="android"
STORE_PASS="android"
DNAME="CN=Android Debug,O=Android,C=US"

# --- Functions ---
function build_signed() {
    echo "Building SIGNED arm64 APK..."

    if [ ! -f "$KEYSTORE_PROPS" ]; then
        echo "Keystore properties not found. Creating $KEYSTORE_PROPS..."
        echo "storeFile=$KEYSTORE_FILE" > "$KEYSTORE_PROPS"
        echo "storePassword=$STORE_PASS" >> "$KEYSTORE_PROPS"
        echo "keyAlias=$KEY_ALIAS" >> "$KEYSTORE_PROPS"
        echo "keyPassword=$KEY_PASS" >> "$KEYSTORE_PROPS"
    fi

    if [ ! -f "$KEYSTORE_FILE" ]; then
        echo "Keystore not found. Creating $KEYSTORE_FILE..."
        keytool -genkey -v -keystore "$KEYSTORE_FILE" \
            -alias "$KEY_ALIAS" \
            -keyalg RSA -keysize 2048 \
            -validity 10000 \
            -storepass "$STORE_PASS" \
            -keypass "$KEY_PASS" \
            -dname "$DNAME"
    fi

    echo "Running Gradle build..."
    export ANDROID_HOME="/opt/android/sdk"
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
    export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
    ./gradlew assembleRelease
    echo "Build successful. Signed APK located in app/build/outputs/apk/release/"
}

function build_unsigned() {
    echo "Building UNSIGNED arm64 APK..."
    export ANDROID_HOME="/opt/android/sdk"
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
    export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
    ./gradlew assembleRelease -Punsigned=true
    echo "Build successful. Unsigned APK located in app/build/outputs/apk/release/"
}


# --- Main Script ---
if [ ! -f "./gradlew" ]; then
    echo "Gradle wrapper not found. Please run this script from the project root."
    exit 1
fi

chmod +x ./gradlew

if [ "$1" == "--unsigned" ]; then
    build_unsigned
else
    build_signed
fi

exit 0
