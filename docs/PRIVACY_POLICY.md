# AutoMate – Task Automation: Privacy Policy

**Effective date:** 22 October 2026
**Developer:** [YOUR NAME / BUSINESS NAME]
**Contact:** [YOUR CONTACT EMAIL]
**App:** AutoMate – Task Automation (package `com.techy_pvk.automate`)

## 1. The short version

AutoMate lets you build "sequences" (taps, swipes, typing, switching ringer/Do Not Disturb/brightness, opening apps) and run them manually or automatically when something happens on your phone (you arrive at a place, you join a Wi-Fi network, a notification arrives, music starts, charging starts, and so on).

- Everything AutoMate reads or stores is used **only on your device**, to run the sequences you create.
- AutoMate has **no accounts, no servers of its own, no advertising and no analytics or crash-reporting libraries**. The developer does not receive your data.
- AutoMate asks for each permission only when you set up a feature that needs it, and shows a screen explaining why first.
- You can turn any permission off at any time in Android Settings, and delete everything by clearing the app's data or uninstalling.

The rest of this page explains, item by item, exactly what is accessed and why.

## 2. Information AutoMate accesses, and why

### Location (precise location, including in the background)

- **What:** your device's precise location (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`) and, for "arrive at / leave a place" sequences, location while the app is closed or not in use (`ACCESS_BACKGROUND_LOCATION`).
- **Why:** only for two features you set up yourself: (1) the **Location trigger**, which runs a sequence when you enter or leave a place you chose; (2) the **Wi-Fi trigger**, because Android only reveals the name of the Wi-Fi network you are connected to when an app has location access.
- **How:** AutoMate registers a circular geofence (a centre point and a radius that you pick) with **Google Play services**, which watches your device's location and tells AutoMate when you cross the boundary. AutoMate receives only the "entered" or "left" event for that geofence. Google Play services processes location under [Google's Privacy Policy](https://policies.google.com/privacy).
- **What AutoMate does not do:** it does not keep a history of where you have been, does not upload your location to the developer or to any third party, and never uses it for advertising or analytics.
- **What is stored:** the place you chose for a trigger (its label, latitude, longitude and radius) is saved inside that sequence, on your device.
- **Address lookup:** if you tap "Look Up" on an address when setting up a Location trigger, the address text you typed is sent to Android's geocoding service (provided by your device and Google) to find its coordinates. AutoMate does not store the result beyond the coordinates you save.
- **Your control:** location is requested only after an in-app disclosure screen where you choose **Allow** or **Don't Allow**. You can revoke it in Android Settings → Apps → AutoMate → Permissions → Location. Without it, Location and named-Wi-Fi triggers simply do not run.

### Wi-Fi network name (SSID)

- **What / why:** the name of the Wi-Fi network you are connected to, to run a sequence when you join a network you named (or any network). Needs location access (see above).
- **Stored:** the network name you typed into the trigger is saved inside that sequence on your device. AutoMate does not log the networks you connect to.

### Bluetooth

- **What / why:** when a Bluetooth audio device connects, AutoMate reads its **name** (`BLUETOOTH_CONNECT`) so a "Bluetooth headset connects" sequence can run, optionally only for a device name you entered.
- **Stored:** only the name filter you typed, inside that sequence.

### Notifications (Notification access)

- **What:** if you turn on Notification access, AutoMate can see notifications posted on your device (app, title and text).
- **Why:** to run a "Notification received" sequence when a notification from an app you chose, containing text you chose, arrives. The same access is what lets Android tell AutoMate about active media sessions (see "Music playback" below).
- **Stored:** notification content is **not** stored unless you tick "Store notification data" on that trigger. If you do, the app, title, text and time of matching notifications are saved in the **Notification Log** on your device (newest 100 entries). You can clear it any time from the Notification Log screen.
- **Not collected:** AutoMate does not read notifications to build a profile of you, and does not send them anywhere.

### Music playback

- **What / why:** whether music or media is playing or paused, and which app is playing it (from Android's active media sessions), to run a "Music playback" sequence when playback starts or genuinely stops. AutoMate does not read what is being played (no track names are stored) and does not record audio.

### Screen content and the foreground app (Accessibility Service)

AutoMate uses Android's **Accessibility Service API**. AutoMate is an automation tool, not an accessibility tool for people with disabilities. It is used for:

1. **Performing the gestures you recorded** (tap, long press, swipe, scroll, back, home, recent apps, typing text you wrote into a field) when you run a sequence or its trigger fires.
2. **Finding on-screen elements** by visible text, resource ID or content description so a "Click by Text / ID / Description" step can find its target.
3. **Inspect Screen** (only when you start it): lists the visible text, IDs and descriptions on the current screen so you can use them in a step. The latest result is kept **on your device only**, until you run another inspection or clear the app's data.
4. **Payment App Guard** (only if you turn it on): AutoMate notices which app has come to the front (the app's package name only, not what is inside it) so it can warn you before a payment app opens, because some payment apps refuse to run while an accessibility service is on.

AutoMate does not use the Accessibility Service to read passwords, to collect or transmit screen content, to change Android settings or permissions on your behalf, to block or uninstall other apps, or to record calls. Nothing it sees on your screen leaves your device. Accessibility is off until **you** enable it in Android Settings, after an in-app disclosure screen where you choose **Allow** or **Don't Allow**.

### Installed apps

- **What / why:** the list of apps that have a launcher icon, so you can pick an app for "Open an app", "Notification received" and "Music playback". The list is read on demand and not stored; only the app you pick (its name and package name) is saved in the sequence.

### Device state

- **Battery level, charging state, Battery Saver, headset plugged in, device boot:** used only to run the matching triggers (for example "charging starts", "Battery Saver turns on", "wired headset connects", "device boot completed").
- **Ringer mode, Do Not Disturb, screen brightness:** AutoMate changes these only when a sequence you built includes a step for it, using the matching Android permissions (Do Not Disturb access, Modify system settings). With "auto-restore" on, AutoMate remembers the previous setting on your device so it can put it back.
- **Exact alarms and boot completed:** used to run "Scheduled time" sequences on time and to set up your triggers again after a restart.

### Things you create

- **Sequences** (name, steps, any text you ask AutoMate to type, coordinates, chosen apps, trigger settings) and your **display name** (Profile screen) are stored in AutoMate's private storage on your device.
- **Overlay permission** (draw over other apps) is used only to show the floating marker for choosing a tap/swipe point and for the Payment App Guard warning.

## 3. Where your information is stored

All of the above is stored in AutoMate's private app storage on your device (a local database and preference files), which other apps cannot read.

**Android backup:** if Android's automatic backup is turned on for your device, Android may back up your saved sequences and display name to your Google Account (for example, to restore them on a new phone). That copy is handled by Google under [Google's Privacy Policy](https://policies.google.com/privacy) and your Google account settings. The Notification Log, Inspect Screen results, unsaved drafts and temporary state are **excluded** from backup. You can turn backup off in Android Settings → Google → Backup.

## 4. Sharing

- AutoMate does not sell your data, does not share it with advertisers, data brokers or analytics providers, and does not send it to the developer.
- **Sharing a sequence is your choice.** When you tap Share on a sequence, AutoMate creates a `.automateseq` file and hands it to the app you choose (WhatsApp, Drive, email…). That file contains the sequence's name, steps (including any text you asked it to type), trigger settings (including a chosen place's coordinates, Wi-Fi or Bluetooth name), and chosen app names. Check a sequence before sharing it.
- **Importing:** a sequence file you open from someone else is validated, size-limited and shown to you for confirmation before anything is saved. If it is set to start by itself, AutoMate tells you.
- **Service providers you already use:** Google Play services (geofencing) and Android's geocoding service (address lookup), as described above.

## 5. Permissions at a glance

| Permission / access | Used for |
|---|---|
| Accessibility Service | Performing your recorded gestures, finding on-screen elements, Inspect Screen, Payment App Guard |
| Location (precise, approximate, background) | Location trigger (geofence), reading the Wi-Fi network name |
| Wi-Fi / network state | Wi-Fi trigger |
| Bluetooth connect | Bluetooth headset trigger (device name) |
| Notification access | Notification trigger, Notification Log (optional), media playback state |
| Post notifications | Reminders from Payment App Guard |
| Do Not Disturb access | "Set ringer: Silent" and "Set Do Not Disturb" steps |
| Modify system settings | "Set brightness" step |
| Display over other apps | Floating point-picker marker, Payment App Guard warning |
| Exact alarms | "Scheduled time" trigger |
| Receive boot completed | Boot trigger; re-arming triggers after a restart |

## 6. Retention and deletion

- Sequences, profile name, Notification Log and Inspect results stay on your device until you delete them (delete a sequence from the home screen; "Clear" in the Notification Log) or clear AutoMate's storage / uninstall the app.
- Because the developer receives no data, there is nothing for the developer to delete on a server. To remove an Android backup copy, use Android Settings → Google → Backup.

## 7. Security

Data stays in AutoMate's private storage, protected by Android's app sandbox. Files you share or import are handled as untrusted input and validated before use. No method of storage is perfectly secure, so please share sequence files only with people you trust.

## 8. Children

AutoMate is not directed to children under 13 and does not knowingly collect personal information from children.

## 9. Changes to this policy

If AutoMate's data practices change (for example, if cloud sign-in or cloud backup is added in the future), this policy will be updated **before** the change ships and the effective date above will change. Continued use after an update means you accept the updated policy.

## 10. Contact

Questions or requests about this policy: **[YOUR CONTACT EMAIL]**
