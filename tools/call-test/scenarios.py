#!/usr/bin/env python3
# SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0
"""Call scenarios over the real telephony stack, using the emulator's modem as the far end.

The emulator rings, answers and hangs up calls (adb emu gsm ...); the app is driven over adb through
its debug-only TestCallReceiver; results are checked in the system call log and the crash buffer.

    scenarios.py --serial emulator-5554 [--adb PATH] [--apk PATH] [names...]
"""

import argparse
import os
import random
import re
import subprocess
import sys
import time

PACKAGE = "org.prauga.phone"
RECEIVER = PACKAGE + "/org.prauga.phone.dialer.testing.TestCallReceiver"
ACTION_PREFIX = "org.prauga.phone.testing."
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))

MISSED, INCOMING, OUTGOING, REJECTED, BLOCKED = 3, 1, 2, 5, 6


class Device:
    def __init__(self, adb, serial):
        self.adb = adb
        self.serial = serial
        self.user = None

    def run(self, *args, check=True):
        result = subprocess.run([self.adb, "-s", self.serial, *args], capture_output=True, text=True)
        if check and result.returncode != 0:
            raise RuntimeError(f"adb {' '.join(args)} failed: {result.stderr.strip()}")
        return result.stdout.strip()

    def shell(self, command):
        return self.run("shell", command)

    def emu(self, command):
        return self.run("emu", *command.split())

    def broadcast(self, name, **extras):
        args = ["am", "broadcast", "--user", self.user, "-n", RECEIVER, "-a", ACTION_PREFIX + name]
        for key, value in extras.items():
            args += ["--es", key, value]
        output = self.shell(" ".join(args))
        match = re.search(r'data="(.*)"', output, re.S)
        return match.group(1) if match else ""

    def calls(self):
        return [line.split(" ", 1) for line in self.broadcast("DUMP").splitlines() if line.strip()]

    def latest_call_type(self, number, since_millis):
        output = self.shell(
            "content query --uri content://call_log/calls --projection type:date:number "
            f"--where \"number LIKE '%{number}' AND date >= {since_millis}\" --sort \"date DESC\""
        )
        match = re.search(r"type=(\d+)", output)
        return int(match.group(1)) if match else None


def wait_for(description, check, timeout=15):
    deadline = time.time() + timeout
    while time.time() < deadline:
        value = check()
        if value:
            return value
        time.sleep(0.3)
    raise AssertionError(f"timed out waiting for {description}")


def fresh_number():
    return "555" + str(random.randint(1000000, 9999999))


def now_millis(device):
    return int(device.shell("date +%s%3N"))


def has_call(device, state, number=None):
    return any(s == state and (number is None or n.endswith(number[-7:])) for s, n in device.calls())


def assert_logged(device, number, since, expected):
    actual = wait_for(f"call log row for {number}", lambda: device.latest_call_type(number, since))
    assert actual == expected, f"call log type {actual}, expected {expected}"


def incoming_missed(device):
    number, since = fresh_number(), now_millis(device)
    device.emu(f"gsm call {number}")
    wait_for("ringing call", lambda: has_call(device, "INCOMING", number))
    device.emu(f"gsm cancel {number}")
    assert_logged(device, number, since, MISSED)


def incoming_answered(device):
    number, since = fresh_number(), now_millis(device)
    device.emu(f"gsm call {number}")
    wait_for("ringing call", lambda: has_call(device, "INCOMING", number))
    device.broadcast("ANSWER")
    wait_for("active call", lambda: has_call(device, "ACTIVE", number))
    device.emu(f"gsm cancel {number}")
    assert_logged(device, number, since, INCOMING)


def incoming_declined(device):
    number, since = fresh_number(), now_millis(device)
    device.emu(f"gsm call {number}")
    wait_for("ringing call", lambda: has_call(device, "INCOMING", number))
    device.broadcast("DECLINE")
    assert_logged(device, number, since, REJECTED)


def screening_first_time_declined(device):
    device.broadcast("SET_SCREENING", category="first_time", action="decline")
    try:
        number, since = fresh_number(), now_millis(device)
        device.emu(f"gsm call {number}")
        assert_logged(device, number, since, BLOCKED)
    finally:
        device.broadcast("SET_SCREENING", category="first_time", action="ring")


def outgoing_answered(device):
    if not device.serial.startswith("emulator-"):
        raise AssertionError("outgoing scenarios only run on an emulator")
    number, since = fresh_number(), now_millis(device)
    device.shell(f"am start --user {device.user} -a android.intent.action.CALL -d tel:{number}")
    wait_for("dialing call", lambda: has_call(device, "DIALING", number) or has_call(device, "CONNECTING", number))
    device.emu(f"gsm accept {number}")
    wait_for("active call", lambda: has_call(device, "ACTIVE", number))
    device.broadcast("HANG_UP")
    assert_logged(device, number, since, OUTGOING)


def app_pid(device):
    return device.run("shell", f"pidof {PACKAGE}", check=False)


def call_survives_app_death(device):
    """Killing the app mid-call must not drop the call. Telecom moves it to the system dialer's UI
    for the rest of the call; it still ends normally and is logged."""
    number, since = fresh_number(), now_millis(device)
    device.emu(f"gsm call {number}")
    wait_for("ringing call", lambda: has_call(device, "INCOMING", number))
    device.broadcast("ANSWER")
    wait_for("active call", lambda: has_call(device, "ACTIVE", number))
    device.shell(f"kill -9 {app_pid(device)}")
    time.sleep(3)
    assert "mCallState=2" in device.shell("dumpsys telephony.registry"), "call dropped when the app died"
    device.emu(f"gsm cancel {number}")
    assert_logged(device, number, since, INCOMING)

SCENARIOS = [
    incoming_missed,
    incoming_answered,
    incoming_declined,
    screening_first_time_declined,
    outgoing_answered,
    call_survives_app_death,
]


def end_all_calls(device):
    modem_calls = device.emu("gsm list").splitlines() if device.serial.startswith("emulator-") else []
    for line in modem_calls:
        match = re.search(r"(\d{3,})", line)
        if match:
            device.emu(f"gsm cancel {match.group(1)}")
    for _ in range(5):
        if "mCallState=0" in device.shell("dumpsys telephony.registry") and not device.calls():
            break
        device.shell("input keyevent KEYCODE_ENDCALL")
        time.sleep(1)


def prepare(device, apk):
    subprocess.run([os.path.join(HERE, "setup.sh"), device.serial], check=True, env={**os.environ, "ADB": device.adb})
    device.user = device.shell("am get-current-user")
    device.run("install", "-r", "-d", "-g", "--user", device.user, apk)
    device.shell(f"cmd role add-role-holder --user {device.user} android.app.role.DIALER {PACKAGE}")


def restore(device):
    subprocess.run([os.path.join(HERE, "setup.sh"), device.serial, "--restore"], env={**os.environ, "ADB": device.adb})


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--apk", default=os.path.join(ROOT, "app/build/outputs/apk/debug/app-debug.apk"))
    parser.add_argument("names", nargs="*", help="scenarios to run (default: all)")
    args = parser.parse_args()

    selected = [s for s in SCENARIOS if not args.names or s.__name__ in args.names]
    device = Device(args.adb, args.serial)
    prepare(device, args.apk)
    failures = 0
    try:
        for scenario in selected:
            device.shell("logcat -b crash -c")
            try:
                scenario(device)
                crashes = device.shell("logcat -b crash -d")
                assert f"Process: {PACKAGE}" not in crashes, "app crashed:\n" + crashes
                print(f"PASS {scenario.__name__}")
            except Exception as error:
                failures += 1
                print(f"FAIL {scenario.__name__}: {error}")
            finally:
                end_all_calls(device)
    finally:
        restore(device)
    print(f"{len(selected) - failures}/{len(selected)} passed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
