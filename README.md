# Auto Clicker Task

Build a named **Sequence** of steps in whatever order you want, then run it - either
manually with a ▶ button, or automatically via a **Trigger**. Triggers now cover Bluetooth
headset connected, notification received, location entered/exited, Wi-Fi network connected,
a scheduled time, device boot, and battery level/charging - a Tasker-style automation
library. The previous version could do Tap, Swipe, Wait, or Open App, only run manually, and
offered no way to find the resource IDs some steps need. Since then this has grown long-press,
scrolling, system navigation, finding/acting on elements by text/resource ID/content
description, typing text into fields, all seven trigger types above, an **Inspect Screen**
tool that finds resource IDs for you, and **App Shortcuts** that bridge into Samsung's Modes
and Routines (and any other launcher/automation tool that supports Android shortcuts).

## What changed from the previous version

| | Before | Now |
|---|---|---|
| Step types | Tap, Swipe, Wait, Open App | Those, plus **Long Click, Scroll, Click by Text, Click by Resource ID, Click by Content Description, Type Text, Back, Home, Recent Apps** - 13 total |
| Adding a step | One button per step type | A single **+ Add Step** button opens a chooser list |
| Targeting | Only fixed (x, y) coordinates | Coordinates still work, *or* target an element by matching its text/id/content-description |
| Screen reading | Accessibility service never read screen content | Now reads it on demand, needed for Click-by-* and Type Text |
| Running a sequence | Only manually, by tapping ▶ | Still manually by default, *or* automatically via any of **7 trigger types** - see below |
| Finding a resource ID | No help offered | **Inspect Screen**: pick an app, tap the exact element (or dump everything on screen), tap any result to copy |
| Connecting to other automation tools | None | **App Shortcuts** - every sequence is a long-press-icon shortcut, and a practical bridge into Samsung's Modes and Routines |

## Files

This project has grown across several rounds of changes; rather than list every file from
every round, here's the current shape of it.

New since the original tap-and-swipe version:
- **Models**: `StepType`, `GestureStep` (13 step types), `ScrollDirection`, `AppInfo`,
  `SequenceTrigger` + `TriggerType` (8 trigger types), `LocationTransition`,
  `BatteryConditionType`, `NotificationEvent`, `NotificationLogEntry`, `InspectedElement`
- **Data helpers**: `SequenceStore` (JSON-in-SharedPreferences persistence),
  `InstalledApps` (shared launchable-apps list), `NotificationLogStore`, `AppShortcuts`,
  `GeofenceHelper`, `AlarmScheduler`
- **Trigger plumbing**: `TriggerMatcher` (matches an incoming event to every sequence's
  trigger and starts a run), `BluetoothTriggerReceiver`, `NotificationTriggerService`,
  `GeofenceBroadcastReceiver`, `ScheduledTriggerReceiver`, `BootCompletedReceiver` - plus
  Wi-Fi and battery monitoring registered directly inside `AutomationService` (see below)
- **Activities**: `SequenceBuilderActivity` (the editor), `NotificationLogActivity`,
  `InspectorResultActivity`, `RunSequenceShortcutActivity` (no-UI shortcut trampoline)
- **UI adapters**: `ui/SequenceAdapter`, `ui/StepAdapter`, `ui/NotificationLogAdapter`,
  `ui/InspectedElementAdapter`
- Roughly 20 dialog/screen layouts under `res/layout/` - one per step type and one per
  trigger type, following the same "chooser list → dedicated dialog" pattern throughout

Rewritten repeatedly as features were added:
- `AutomationService.java` - now the one long-lived component doing the most work: dispatches
  all 13 step types; finds elements by text/id/description with retry-polling; handles Type
  Text's click-through-then-find-editable-field flow with a clipboard-paste fallback;
  captures notification context for `{notification_title}`/`{notification_text}`/
  `{notification_app}` placeholders; serves both Inspect Screen scan modes; and registers a
  Wi-Fi `NetworkCallback` plus battery/charging receivers on top of its original
  gesture-dispatch job
- `FloatingPickerService.java` - the marker-drag picker now serves Tap, Long Click (orange
  "H"), Swipe, and Inspect Screen's point mode (purple "I", hands off to AutomationService
  instead of the sequence builder)
- `MainActivity.java` - permission buttons for Overlay, Accessibility, Notification Access,
  and a consolidated **More Permissions** dialog (Location, Background Location, Exact
  Alarm); Inspect Screen and View Notification Log entry points; requests `BLUETOOTH_CONNECT`
  at runtime (see the bug-fix note below); re-syncs app shortcuts, geofences, and scheduled
  alarms every time the sequence list changes
- `SequenceBuilderActivity.java` - **+ Add Step** and **Change Trigger** both open a chooser
  list that routes to a dedicated dialog per type
- `AndroidManifest.xml` - permissions and component declarations for everything above
- `accessibility_service_config.xml` - `canRetrieveWindowContent=true`, needed for
  element-search steps and Inspect Screen
- `gradle/libs.versions.toml`, `app/build.gradle.kts` - added
  `com.google.android.gms:play-services-location` for the Location trigger's geofencing

## Opening the project

Open this folder in Android Studio and let it sync - the Gradle wrapper is included, so no
extra setup should be needed. minSdk is unchanged at 30 (Android 11+). No new dependencies
were needed for this update - the new steps use only platform Accessibility APIs.

I wrote this by hand in this environment (no Android SDK here to compile it against), so
treat it as a strong build rather than something guaranteed to compile with zero edits - if
Android Studio flags a small issue on first import it should be quick to fix.

## How to use the app

1. **Grant permissions** (once): open the app, tap **Grant Overlay Permission**, then
   **Enable Accessibility Service** and turn on "Auto Clicker Task" in the system list that
   opens. Both are required - overlay for the point picker, accessibility for dispatching
   gestures *and* for reading the screen to find elements by text/id/description. If you
   plan to use a notification trigger, also tap **Grant Notification Access** and enable it
   in the list that opens - this is a separate grant from Accessibility (Android treats
   reading notification content as its own special permission). Bluetooth triggers need no
   extra in-app step beyond the normal system permission prompt the first time one fires.

2. **Build a sequence**: tap **+ New Sequence**, name it, set a repeat count (`1` = run
   once, `0` = loop until stopped), then tap **+ Add Step** and pick a step type:
   - **Tap** / **Long Click**: the app minimizes and a draggable marker (green "A" for Tap,
     orange "H" for Long Click) appears over whatever's on screen. Drag it into place, then
     **Save** on the floating panel (or **Cancel**).
   - **Swipe**: same idea, but two markers - **A** (start) and **B** (end).
   - **Scroll**: pick a direction (Up/Down/Left/Right) and a swipe duration; this simulates
     a full-screen swipe rather than targeting a specific scrollable view.
   - **Wait**: a pause in milliseconds.
   - **Open App**: pick any installed app from the list.
   - **Click by Text** / **Click by Resource ID** / **Click by Content Description**: type
     the value to match (e.g. the button's visible label, its `resource-id`, or its
     accessibility description) and how long to keep watching for it to appear. Not sure
     what the resource ID is? Use **Inspect Screen** from the main screen first (see below).
     The step clicks the first matching element it finds - or, if nothing on it is directly
     clickable, taps its on-screen position instead.
   - **Type Text**: the text to enter, and optionally a resource ID or exact text to locate
     the target field - leave that blank to type into whatever field is currently focused.
     If this sequence has a notification trigger, you can also use
     `{notification_title}`, `{notification_text}`, or `{notification_app}` here.
   - **Back** / **Home** / **Recent Apps**: fire the matching system navigation action, no
     extra input needed.
   - Use the up/down arrows to reorder steps, or the X to remove one. **Save Sequence** when
     done.

3. **Set a Trigger** (optional): tap **Change Trigger** to pick when this sequence runs -
   see the Triggers section below for details on each option. Leave it on **Manually** if
   you just want the ▶ button.

4. **Run it manually**: from the main list, tap the ▶ button on a sequence. The app
   minimizes immediately - so switch to whatever app you want the sequence to act on
   *before* tapping Run (or add an "Open App" step as step 1). A notification appears while
   it's running with a **Stop** button. A sequence with an automatic Trigger runs the exact
   same way when that trigger fires - you don't also need to tap ▶ for it.

5. **Edit or delete**: the pencil icon reopens a sequence in the builder with all its steps
   intact; the trash icon deletes it after a confirmation.

## Triggers - running a sequence automatically

Every sequence has a **Trigger**, set from its **Change Trigger** button in the builder:

- **Manually** (default): unchanged from before - it only runs when you tap ▶.
- **When a Bluetooth headset connects**: fires when a Bluetooth *audio* device (headset,
  earbuds, speaker) completes a connection - keyboards, smartwatches, etc. are filtered out
  by the device's reported Bluetooth class. Optionally filter by device name (e.g.
  "AirPods") if you only want this to fire for one specific device.
- **When a notification is received**: fires when a notification is posted that matches
  this trigger's filters - optionally scoped to one app, and optionally requiring the
  title or text to contain some text (e.g. "OTP" to catch one-time-passcode messages).
  Turning on **"Also save each matching notification"** logs it (app, title, text, time) to
  the on-device **Notification Log**, viewable/clearable from the main screen. The
  notification's title/text/app are also available to a **Type Text** step in the same
  sequence via `{notification_title}` / `{notification_text}` / `{notification_app}` -
  useful for e.g. auto-filling a one-time code somewhere.
- **Location (enter/exit a place)**: fires when you cross a radius (default 150m) around a
  saved point - type an address and tap **Look Up** to convert it to coordinates (uses the
  device's built-in geocoder, no API key needed), or paste a latitude/longitude directly
  (long-press a spot in Google Maps and tap the coordinates that pop up to copy them).
  Choose **Arrive** (entering) or **Leave** (exiting). Needs Location access, and
  **background** location ("Allow all the time") specifically - see permissions below.
- **Wi-Fi network connected**: fires when the device connects to Wi-Fi - leave the network
  name blank for any Wi-Fi network, or set one (the **Use Current** button fills in whatever
  you're connected to right now). Needs Location access to read the actual network name;
  without it, Android only reports `<unknown ssid>`.
- **Scheduled time**: fires at a specific time of day, on whichever days of the week you
  pick (defaults to every day). Uses an exact alarm when granted (see permissions below),
  falling back to an inexact one (which can drift by several minutes under Doze) otherwise.
- **Device boot completed**: fires once whenever the phone finishes starting up.
- **Battery level / charging**: fires when charging starts, when it stops, or when the
  battery level drops to (or below) a percentage you set - the level check only fires once
  per crossing, not continuously while sitting below the threshold.

Example for the morning-routine case: a sequence with steps *Open App (Spotify) → Wait →
Click by Text ("Daily Mix") → Wait → Click by Text/Content-Description (the play button)*,
with its trigger set to **Bluetooth headset connects** and no device-name filter (or a
filter matching your specific earbuds' name), starts playback automatically the moment you
put your headset on.

### Permissions the newer triggers need

Tap **More Permissions** on the main screen - it's worth granting these in the order shown:

1. **Location Access** - needed for the Location trigger and for the Wi-Fi trigger to read
   the real network name.
2. **Background Location ("Allow all the time")** - only becomes grantable on many devices
   *after* step 1 is done; needed for the Location trigger to fire while the app isn't open.
   Android doesn't let apps show a single combined dialog for both, hence two buttons.
3. **Exact Alarm Permission** - needed for the Scheduled Time trigger to fire precisely;
   denied by default on Android 14+. Without it, scheduled sequences still run, just with
   up to a few minutes of possible drift.

The Location, Wi-Fi, and Battery triggers all depend on `AutomationService` (the
Accessibility service) staying alive in the background, same as the Bluetooth/notification
triggers - see the battery-optimization note below if they seem unreliable.

### Bug fix: Bluetooth trigger never fired

`BLUETOOTH_CONNECT` was declared in the manifest but never actually **requested at runtime**
on Android 12+ (API 31+) - it's a dangerous/runtime permission there, not something granted
just by declaring it, the same way `POST_NOTIFICATIONS` needs a runtime prompt on Android 13+.
Without it, every check the Bluetooth receiver made silently evaluated to "no permission,"
so it always ignored every device that connected, regardless of what it was.
`MainActivity` now requests both permissions together on first launch - open the app once
after updating so the system permission dialog appears, then confirm your target device
still connects properly afterward (reconnect it once if it was already connected before you
granted the permission).

### If a trigger still isn't firing

Since this app can't see your device's actual state remotely, here's the checklist, most
likely culprit first:

1. **Re-open the app once** after updating so the new permission prompts actually appear,
   and grant them (see "Permissions the newer triggers need" above). If you dismissed one
   accidentally, reinstalling or clearing the app's data will make it ask again, or grant it
   manually under Settings → Apps → Auto Clicker Task → Permissions.
2. **Samsung's battery management is unusually aggressive.** Go to Settings → Apps → Auto
   Clicker Task → Battery, and set it to **Unrestricted**. Also check Settings → Battery and
   device care → Battery → Background usage limits, and make sure this app isn't in the
   "Sleeping apps" or "Deep sleeping apps" list - any of these can silently kill the
   notification listener, stop Bluetooth/Wi-Fi/battery broadcasts from reaching the app, or
   prevent geofences/alarms from firing.
3. **Confirm Accessibility is still enabled.** Android (and Samsung especially) can
   auto-revoke permissions for apps that haven't been opened in a while. If Accessibility
   got toggled off, triggers can still "fire" internally but the run itself won't happen,
   since that's what actually dispatches the sequence - and the Wi-Fi/battery triggers
   specifically stop listening entirely, since they're registered inside that same service.
4. **Notification trigger specifically**: confirm Settings → Apps → Special access →
   Notification access still shows this app enabled. If it looks enabled but nothing fires,
   toggling it off and back on forces Android to rebind the listener, which clears up most
   stuck states.
5. **Location trigger specifically**: double-check background location is actually set to
   "Allow all the time," not just "Allow while using the app" - a geofence registered
   without it silently never fires. Some Samsung One UI versions also hide the "Alarms &
   Reminders" settings entry entirely if you deny the exact-alarm prompt once, making the
   Scheduled Time trigger's precise timing hard to re-grant later except by reinstalling.
6. **Test the underlying condition in isolation** - e.g. connect/disconnect the Bluetooth
   device or Wi-Fi network with no sequence attached yet, or send yourself a test
   notification matching the filter, to confirm the trigger side works before assuming the
   sequence itself is wrong.

## App Shortcuts - a bridge to Samsung's Modes and Routines (and Tasker, and your launcher)

Every saved sequence now also publishes as a normal Android **app shortcut** - long-press the
app icon to see them, each one running that sequence immediately with no screen shown.
This also happens to be the most practical way to connect this app to **Samsung's Modes and
Routines**, so here's the honest picture of what that integration can and can't do:

- Samsung Routines' "Other apps" action list is built on Android/Google's general **App
  Actions** system, not a Samsung-specific plugin API - there's no public API for a
  third-party app to register a fully custom Routines *condition* (like "if my app does X"),
  and no simple way to register a custom *action* beyond what that system already exposes.
  A forum thread from a developer asking exactly this ("is it possible to add 3rd party app
  support to routines?") went unanswered - it isn't a documented, ready-made path today.
- What **is** solid and well-documented: Routines' native conditions already cover Bluetooth
  device connected, Wi-Fi network connected, location entering/leaving, time of day, and
  more - all without needing this app's Bluetooth/notification triggers at all for those
  cases. Use Samsung's own condition, and set the action to **Other apps → Auto Clicker
  Task**.
- Whether that action picker lets you choose one of this app's *specific shortcuts* (i.e.
  "Run Morning Routine" instead of just "Open app") depends on your One UI version - some
  versions of the Routines app do surface an installed app's shortcuts as separate pickable
  actions, others only offer a plain "Open app." **Check on your own device** after
  installing this update; if a specific shortcut shows up in the list, pick it and you're
  fully hands-off. If only "Open app" is offered, the guaranteed fallback is: the Routine
  opens Auto Clicker Task, and you tap ▶ on the sequence you want - one manual tap instead of
  zero, but the condition-matching (location/time/Wi-Fi/etc.) is still fully automatic.

## Inspect Screen - finding resource IDs (and text/descriptions) without another tool

Click by Resource ID needs the exact `resource-id` a developer gave that element, which
isn't visible just by looking at the screen. Rather than requiring Appium, `adb shell
uiautomator dump`, or Android Studio's Layout Inspector, this app can tell you directly:

1. Tap **Inspect Screen** on the main screen.
2. Pick which app to inspect - "Currently foreground app" (whatever's already open) or any
   installed app, which gets launched for you.
3. Pick how: **Tap the exact element** (recommended) drops a single purple "I" marker, just
   like a Tap step - drag it exactly onto the button/field/label you care about, then tap
   **Inspect** on the floating panel. **Show everything on screen** instead dumps every
   element the whole screen has to offer, no targeting needed.
4. It reads that screen and shows results:
   - **Tap the exact element**: the element you marked, plus its ancestor chain ("Tapped
     element", "1 level up", "2 levels up", ...) - useful because the exact widget you
     tapped (an icon, say) often has no id/text/description of its own, while a parent row
     a level or two up does.
   - **Show everything on screen**: every element found, in one flat list.
5. Tap any row to copy the most useful value (ID, then text, then description, whichever
   exists first) straight to the clipboard - paste it right into a Click-by-* step.

If nothing on the chain has anything identifying at all (some custom-drawn UI does this),
Click by Text or Click by Content Description on a nearby labeled element, or a plain
coordinate-based Tap, are the fallbacks.

## How each new step works, under the hood

- **Long Click**: identical mechanism to Tap (a stationary gesture via `dispatchGesture`),
  just held for longer (700ms by default) - long enough to clear the platform's long-press
  timeout, so the target app treats it as a long-press rather than a tap.
- **Scroll**: a full-screen swipe computed from the display size and the chosen direction -
  no element-finding involved, so it works even on screens with unusual widget structure.
- **Click by Text / Resource ID / Content Description**: reads the current screen via
  `getRootInActiveWindow()` and searches it for a matching element, polling every 250ms up
  to that step's timeout in case the screen is still loading (e.g. right after an Open App
  step). If the match itself isn't clickable, the nearest clickable *ancestor* is clicked
  instead (common for icons/text nested inside a clickable row); if nothing in that chain is
  clickable, it falls back to a plain coordinate tap on the match's on-screen bounds.
- **Type Text**: if the matched resource ID/text is already an editable field, types straight
  into it via `ACTION_SET_TEXT`. If it's not directly editable (a "Search" button that opens
  a dedicated search screen, say), it's clicked through first (same clickable-ancestor logic
  as Click by Resource ID above), then the resulting screen is polled for whichever node is
  actually editable - not just whatever claims input focus, since some apps' custom text
  fields (Compose-based ones especially) don't reliably register that the classic way. If
  `ACTION_SET_TEXT` is rejected outright by the target (some custom widgets don't implement
  it), it falls back to selecting any existing text and pasting via the clipboard instead. If
  no target is given, it types into whatever field currently has focus.
- **Back / Home / Recent Apps**: the three standard `performGlobalAction()` calls - no
  screen-reading needed.

## Notes on behavior

- **Timing**: each step's delay is "wait this long, *then* do the step" - so a Tap step
  with `delayBeforeMs = 500` means "wait half a second, then tap." Default delay is 500ms
  for every step type except Wait (which uses the entered value as its own delay).
- **After "Open App"**: the sequence doesn't know when the app has finished loading - give
  the *next* step a generous delay (or a Wait step), or lean on a Click-by-* / Type Text
  step's own match-timeout to wait out the load instead.
- **Repeat count `0`**: loops indefinitely until you tap Stop on the notification. Good for
  "keep tapping this button every 2 seconds," but make sure you actually want that before
  saving it that way.
- **Where actions land**: everything - gestures, clicks, and typed text - targets whatever
  app is currently in the foreground when the step fires; there's no per-step app targeting
  beyond the optional "Open App" step type.
- **Triggered runs share the same "one at a time" rule as manual runs**: if a Bluetooth or
  notification trigger fires while a sequence (any sequence) is already running, it doesn't
  queue - the new run supersedes whatever was in progress, same as tapping ▶ twice would.
- **A frequent trigger + repeat count `0` is a bad combination**: e.g. a notification
  trigger matching a busy chat app, on a sequence set to loop forever, will keep restarting
  before finishing. Keep looping sequences on Manual, or give notification/Bluetooth
  triggers a finite repeat count.
- **Battery optimization**: some phone manufacturers aggressively kill background apps to
  save power, which can stop the Bluetooth receiver or notification listener from firing
  reliably. If triggers seem flaky, check your phone's battery settings for an "unrestricted
  background usage" or "don't optimize" option for this app.
- **Debugging a stubborn Type Text step**: filter Logcat for tag `AutoClickerType` to see
  which node was matched, whether it reported itself as editable/focused, and whether
  `ACTION_SET_TEXT` or the clipboard-paste fallback succeeded. A field that fails both is
  usually one of two things: it hasn't appeared/gained focus yet (raise the step's
  match-timeout, or add a Wait before it) or it's a secure field (password boxes, some
  banking-app inputs) that deliberately blocks programmatic text entry - there's no
  accessibility-API workaround for that by design.

## A note on how this kind of app gets used

This dispatches synthetic touch events and accessibility actions through Android's
Accessibility API - the same mechanism legitimate macro/QA-testing tools use, and it only
works after you explicitly enable the accessibility service yourself. Worth knowing: using
it to automate interactions inside another app (games, social apps, etc.) can violate *that
app's* terms of service even though nothing here is doing anything to your device that
Android doesn't already allow.

