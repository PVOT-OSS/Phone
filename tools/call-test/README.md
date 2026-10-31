# Call tests

End-to-end tests for Phone that make real calls through Telecom without a carrier.

- `app/src/androidTest` — Kotlin instrumented tests. Calls come from the debug-only fake carrier
  (`app/src/debug/.../testing/FakeCarrier`), which rings and answers calls through the real Telecom
  stack, so screening, the in-call UI, notifications and the call log all run for real.
- `run.sh <serial> [gradle arguments]` — runs the instrumented tests on a device: saves the device's
  default phone app, makes Phone the default for the run, and gives the role back afterwards even
  when the run fails.
- `setup.sh <serial> [--restore]` — the save and restore steps on their own.

```sh
ADB=~/Android/Sdk/platform-tools/adb tools/call-test/run.sh <serial>
ADB=... tools/call-test/run.sh <serial> -Pandroid.testInstrumentationRunnerArguments.class=org.prauga.phone.dialer.e2e.ScreeningTest
```

Notes:
- The device's call log keeps the test calls (numbers in +1 555 01xx and +1 555 xxx xxxx).
- Tests never place calls on a SIM account; outgoing calls go to the fake carrier only.
- Saved state lives in `.device-state/<serial>/` (not committed).
