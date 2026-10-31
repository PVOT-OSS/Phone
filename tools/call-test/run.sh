#!/bin/sh
# SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0
#
# Runs the instrumented call tests on a device and always restores it afterwards.
#   run.sh <serial> [extra gradle arguments]
set -eu

serial=${1:?usage: run.sh <serial> [gradle arguments]}
shift
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)

"$here/setup.sh" "$serial"
trap '"$here/setup.sh" "$serial" --restore' EXIT INT TERM
cd "$root"
ANDROID_SERIAL=$serial ./gradlew :app:connectedDebugAndroidTest "$@"
