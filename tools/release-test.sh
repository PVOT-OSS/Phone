#!/usr/bin/env bash
# SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0
#
# Builds a release APK (minified and shrunk, as shipped) and installs it on a connected device,
# to test the app as users get it.
#
#   tools/release-test.sh [serial] [user|system]
#
# The APK is signed with the key in keystore.properties (see keystore.properties.example) or, when
# there is none, with the debug key. A debug build already on the device is signed with the debug
# key too, so it is replaced in place; a build signed with another key is uninstalled first.
set -euo pipefail
cd "$(dirname "$0")/.."

serial="${1:-}"
flavor="${2:-user}"
Flavor="$(tr '[:lower:]' '[:upper:]' <<< "${flavor:0:1}")${flavor:1}"
adb=(adb)
[[ -n "$serial" ]] && adb+=(-s "$serial")

./gradlew -q ":app:assemble${Flavor}Release"
apk="$(ls app/build/outputs/apk/"$flavor"/release/*.apk | head -1)"
echo "Installing $apk"
if ! "${adb[@]}" install -r -g "$apk"; then
    echo "Install failed, likely a different signing key: uninstalling the old build and retrying"
    "${adb[@]}" uninstall org.prauga.phone || true
    "${adb[@]}" install -g "$apk"
fi
"${adb[@]}" shell monkey -p org.prauga.phone -c android.intent.category.LAUNCHER 1 > /dev/null
echo "Release build running. Make it the default phone app in Settings to test calls."
