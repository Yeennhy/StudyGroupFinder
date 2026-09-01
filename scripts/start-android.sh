#!/usr/bin/env sh

set -eu

APP_ID="com.studyfinder.app/.MainActivity"
EMULATOR_LOG_FILE="./emulator_log.txt"
PACKAGE_NAME="${APP_ID%%/*}"
PACKAGE_NAME=$(printf '%s' "$PACKAGE_NAME" | tr -d '\r')
CLEAN_MODE=false

if [ "${1:-}" = ":clean" ]; then
	CLEAN_MODE=true
fi

log() {
	echo "[start-android-win] $1"
}

resolve_sdk_from_local_properties() {
	if [ ! -f "./local.properties" ]; then
		return 1
	fi

	SDK_DIR_RAW="$(grep '^sdk.dir=' ./local.properties | head -n 1 | sed 's/^sdk.dir=//')"
	if [ -z "$SDK_DIR_RAW" ]; then
		return 1
	fi

	# Convert Gradle escaped path (e.g. C\:\\Users\\...) to a normal path.
	SDK_DIR_NORMALIZED="$(printf '%s' "$SDK_DIR_RAW" | sed 's#\\\\#\\#g')"

	case "$SDK_DIR_NORMALIZED" in
		[A-Za-z]:\\*)
			DRIVE_LETTER="$(printf '%s' "$SDK_DIR_NORMALIZED" | cut -c1 | tr 'A-Z' 'a-z')"
			DRIVE_PATH_REST="$(printf '%s' "$SDK_DIR_NORMALIZED" | cut -c3- | sed 's#\\#/#g')"
			echo "/$DRIVE_LETTER$DRIVE_PATH_REST"
			return 0
			;;
		*)
			echo "$SDK_DIR_NORMALIZED"
			return 0
			;;
	esac
}

resolve_emulator_bin() {
	if command -v emulator >/dev/null 2>&1; then
		command -v emulator
		return 0
	fi

	if command -v emulator.exe >/dev/null 2>&1; then
		command -v emulator.exe
		return 0
	fi

	if [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/emulator/emulator" ]; then
		echo "$ANDROID_HOME/emulator/emulator"
		return 0
	fi

	if [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/emulator/emulator.exe" ]; then
		echo "$ANDROID_HOME/emulator/emulator.exe"
		return 0
	fi

	if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -x "$ANDROID_SDK_ROOT/emulator/emulator" ]; then
		echo "$ANDROID_SDK_ROOT/emulator/emulator"
		return 0
	fi

	if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -x "$ANDROID_SDK_ROOT/emulator/emulator.exe" ]; then
		echo "$ANDROID_SDK_ROOT/emulator/emulator.exe"
		return 0
	fi

	LOCAL_SDK_DIR="$(resolve_sdk_from_local_properties || true)"
	if [ -n "$LOCAL_SDK_DIR" ] && [ -x "$LOCAL_SDK_DIR/emulator/emulator" ]; then
		echo "$LOCAL_SDK_DIR/emulator/emulator"
		return 0
	fi

	if [ -n "$LOCAL_SDK_DIR" ] && [ -x "$LOCAL_SDK_DIR/emulator/emulator.exe" ]; then
		echo "$LOCAL_SDK_DIR/emulator/emulator.exe"
		return 0
	fi

	if [ -x "$HOME/Library/Android/sdk/emulator/emulator" ]; then
		echo "$HOME/Library/Android/sdk/emulator/emulator"
		return 0
	fi

	return 1
}

has_connected_device() {
	adb devices | awk 'NR>1 && $2=="device" {print $1}' | grep -q .
}

wait_for_boot_complete() {
	while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
		sleep 1
	done
}

start_first_available_avd() {
	EMULATOR_BIN="$(resolve_emulator_bin || true)"
	if [ -z "$EMULATOR_BIN" ]; then
		echo "Android emulator binary not found. Add emulator to PATH or set ANDROID_HOME/ANDROID_SDK_ROOT."
		exit 1
	fi

	AVD_NAME="$($EMULATOR_BIN -list-avds | head -n 1)"
	if [ -z "$AVD_NAME" ]; then
		echo "No connected devices and no AVDs available."
		exit 1
	fi

	echo "No connected device. Starting emulator: $AVD_NAME"
	{
		echo ""
		echo "===== $(date '+%Y-%m-%d %H:%M:%S') | Starting emulator: $AVD_NAME ====="
	} >>"$EMULATOR_LOG_FILE"
	if [ "$CLEAN_MODE" = "true" ]; then
		"$EMULATOR_BIN" -avd "$AVD_NAME" -wipe-data >>"$EMULATOR_LOG_FILE" 2>&1 &
	else
		"$EMULATOR_BIN" -avd "$AVD_NAME" >>"$EMULATOR_LOG_FILE" 2>&1 &
	fi
	adb wait-for-device
	wait_for_boot_complete
}

clean_environment() {
	log "Deep clean mode enabled (:clean)."
	log "Stopping connected emulator/device sessions..."
	adb devices | awk 'NR>1 && $2=="device" {print $1}' | while read -r serial; do
		if [ -n "$serial" ]; then
			adb -s "$serial" emu kill >/dev/null 2>&1 || true
		fi
	done
	log "Restarting adb server..."
	adb kill-server >/dev/null 2>&1 || true
}

resolve_studio_jbr() {
	for CANDIDATE in \
		"${PROGRAMFILES:-C:\Program Files}/Android/Android Studio/jbr" \
		"${LOCALAPPDATA:-}/Programs/Android Studio/jbr" \
		"${PROGRAMFILES:-C:\Program Files}/Android/Android Studio/jre" \
		; do
		if [ -x "$CANDIDATE/bin/java.exe" ]; then
			echo "$CANDIDATE"
			return 0
		fi
	done

	return 1
}

ensure_gradle_java_home() {
	if [ -n "${JAVA_HOME:-}" ]; then
		return 0
	fi

	# AGP 8.10 (this project) requires a JDK 11+ runtime, but on some
	# machines the ambient `java` on PATH is an old JRE 8 (e.g. from a
	# separate Oracle Java install), which makes Gradle configuration fail
	# with "Dependency requires at least JVM runtime version 11". Android
	# Studio always ships a compatible JDK, so fall back to that.
	STUDIO_JBR="$(resolve_studio_jbr || true)"
	if [ -n "$STUDIO_JBR" ]; then
		log "JAVA_HOME not set; using Android Studio's bundled JDK: $STUDIO_JBR"
		export JAVA_HOME="$STUDIO_JBR"
	else
		log "Warning: JAVA_HOME not set and no JDK 11+ found. Gradle may fail (AGP requires JDK 11+)."
	fi
}

install_and_launch() {
	ensure_gradle_java_home
	log "Installing debug build..."
	# Use gradlew.bat directly: the POSIX ./gradlew wrapper shells out to
	# `cygpath` to translate paths for Windows, and on machines where a
	# non-Git-Bash `cygpath` (e.g. Anaconda's) sits earlier on PATH, that
	# translation produces a bogus classpath and Java fails with
	# "Could not find or load main class org.gradle.wrapper.GradleWrapperMain".
	# gradlew.bat needs no such translation.
	./gradlew.bat installDebug
	log "Build/install success. Launching app..."
	adb shell am start -n "$APP_ID"
}

stream_logcat() {
	echo "----------------------------------------------------"
	echo "Bắt đầu theo dõi Logcat cho: $PACKAGE_NAME"
	echo "Nhấn Ctrl+C để dừng."
	echo "----------------------------------------------------"

	adb logcat -c
	set +e
	if [ -n "$PACKAGE_NAME" ]; then
		adb logcat -v time | grep --line-buffered --color=never -F "$PACKAGE_NAME"
	else
		echo "Không tách được package từ APP_ID=$APP_ID — in toàn bộ log."
		adb logcat -v time
	fi
}

if [ "$CLEAN_MODE" = "true" ]; then
	clean_environment
fi

if ! has_connected_device; then
	start_first_available_avd
fi

install_and_launch

sleep 2

stream_logcat