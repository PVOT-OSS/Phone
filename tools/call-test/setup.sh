#!/bin/sh
# SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0
#
# Prepares a device for the call tests and puts it back afterwards.
#   setup.sh <serial>            save the default phone app and keep the screen on
#   setup.sh <serial> --restore  give the default phone app role back and let the screen sleep
set -eu

serial=${1:?usage: setup.sh <serial> [--restore]}
mode=${2:-}
adb=${ADB:-adb}
here=$(cd "$(dirname "$0")" && pwd)
state="$here/.device-state/$serial"
role=android.app.role.DIALER

a() { "$adb" -s "$serial" "$@"; }

a root >/dev/null 2>&1 || true
a wait-for-device
user=$(a shell am get-current-user | tr -d '\r')

if [ "$mode" = "--restore" ]; then
    [ -f "$state/dialer" ] || { echo "no saved state for $serial" >&2; exit 1; }
    holder=$(cat "$state/dialer")
    if [ -n "$holder" ]; then
        a shell cmd role add-role-holder --user "$user" "$role" "$holder"
    fi
    a shell svc power stayon false
    echo "restored default phone app for user $user: ${holder:-none}"
    exit 0
fi

mkdir -p "$state"
if [ ! -f "$state/dialer" ]; then
    a shell cmd role get-role-holders --user "$user" "$role" | tr -d '\r' | grep -v '^org\.prauga\.phone$' | head -n 1 > "$state/dialer" || true
fi
a shell svc power stayon true
a shell wm dismiss-keyguard
echo "saved default phone app for user $user: $(cat "$state/dialer")"
