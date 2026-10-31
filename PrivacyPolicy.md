# Privacy Policy — Phone

_Effective 1 November 2026_

Phone (package `org.prauga.phone`) is a dialer and in-call app based on the LineageOS Dialer,
published by Saalim Quadri for PVOT-OSS. This policy explains what the app does with your data.

**In short: the app doesn't collect, store on a server, share or sell any personal data. It has
no accounts, no ads, no analytics and no tracking. Your calls, contacts and call history stay on
your device; the few features that contact an online service are listed below, and the lookup
features are off until you turn them on.**

## What the app uses on your device

- **Calls, call history, contacts and voicemail:** read from and written to Android's own call
  log, contacts and voicemail storage, so you can place, answer and review calls. The app doesn't
  copy them anywhere else.
- **Its own data:** your settings, blocked numbers, speed-dial and preferred-SIM choices, call
  statistics, and caches of contact details and lookup results.
- **Call recordings,** if call recording is available on your device and you start one: saved as
  audio files in `Recordings/Call recordings` on your device, where other apps with access to your
  audio files can see them.
- **Helpline numbers:** on LineageOS devices, the app reads the list of sensitive and helpline
  numbers that comes with the system, to list helplines and to hide those numbers on the in-call
  screen.

None of this leaves your device, except through Android's own backup if you have turned it on:
the app's settings and databases may then be backed up to your Google account, under Google's
terms. Clearing the app's storage or uninstalling it deletes the app's own data; your call log,
contacts, voicemails and recordings stay in Android's storage.

## What the app sends over the network

| When | Sent to | What is sent |
|---|---|---|
| **Visual voicemail**, if your carrier supports it | Your carrier's voicemail server | What's needed to activate the service and download your voicemails and greeting, using the SIM's credentials |
| **Forward lookup** — off by default, Settings → Lookup | OpenStreetMap's Overpass API (`overpass-api.de`) | The text you type in the dialer search and your approximate location, to show nearby places |
| **Reverse lookup** — off by default, Settings → Lookup | The provider you choose: YellowPages (US or Canada), ZabaSearch or Das Telefonbuch | The phone number of an incoming caller who isn't in your contacts, to show their name |

These requests carry nothing else about you beyond what any app or browser sends: your IP
address, the time and basic technical details. The services that receive them may log them under
their own policies. Calls themselves are carried by your mobile carrier, as with any phone app.

To format numbers for the country you're in, the app may use your last known location (it never
turns on GPS by itself) and Android's geocoder, which on some devices asks the device's location
service provider, such as Google, for the country.

## Permissions

- **Phone, call log, contacts and voicemail:** to place and manage calls, show your call history,
  search contacts and play voicemail.
- **SMS:** visual voicemail activation and status messages exchanged with your carrier.
- **Microphone and camera:** call recording and video calls, only while you use them.
- **Location:** country detection for number formatting and, if you enable it, forward lookup.
- **Nearby devices (Bluetooth):** routing call audio to headsets and car kits.
- **Notifications and full-screen notifications:** incoming and ongoing calls, missed calls and
  voicemail.
- **Internet and network state:** visual voicemail and the lookup features above.
- Other permissions in the app let it work as your default phone app (run during calls, show the
  in-call screen, manage audio). Some only take effect when the app is part of the system image.

## Children

The app is not directed at children and collects no data from anyone, children included.

## Changes

If the app starts handling data differently, this policy will be updated before that version is
released, with a new effective date at the top.

## Contact

Questions about this policy: danascape@gmail.com, or open an issue at
https://github.com/PVOT-OSS/Phone/issues.
