# AutoMate architecture (MVVM)

```
ui/            Activities, adapters, dialogs      - draw state, forward input. No storage, no business rules.
viewmodel/     *ViewModel, TriggerSummaryFormatter - hold screen state (LiveData), make decisions, emit one-shot Events.
repository/    *Repository, PermissionState, ...   - the only layer the ViewModels use to reach Room, SharedPreferences, system APIs.
data/          Room (AppDatabase/Dao/Entity), *Store, SequenceMapper, TriggerMatcher, schedulers.
model/         Plain data classes + enums (AutomationSequence, GestureStep, SequenceTrigger, ...).
service/       AutomationService (accessibility), FloatingPickerService, NotificationTriggerService, SequenceRunner.
receiver/      Boot / Bluetooth / geofence / alarm / payment-reminder receivers.
util/          Event, EventObserver, AppExecutors, PrefsLiveData, LiveDataUtil, UiMessage, ToastUtil, InputParsers.
```

## Rules of thumb

1. **Dependencies point one way:** `ui -> viewmodel -> repository -> data -> model`. A ViewModel never holds a View/Activity; an Activity never touches Room or SharedPreferences.
2. **State = LiveData, one-shot things = `Event<T>`.** A toast, "close this screen", or "open this dialog" is an `Event` observed with `EventObserver`, so a rotation does not replay it.
3. **Threading:** the UI thread only touches LiveData. Disk work runs on `AppExecutors.diskIO()` (a single thread, so operations keep their order) and comes back through `AppExecutors.mainThread()`.
4. **Services and receivers** (no lifecycle, may run with no UI) keep using the synchronous `SequenceStore`. `SequenceMapper` is shared by both paths so the stored format cannot diverge.
5. **ViewModels are plain Java classes with a public `(Application)` or `(Application, SavedStateHandle)` constructor** (what the default `ViewModelProvider` factory needs) and a package-private constructor that takes its dependencies, for tests.
6. **Anything that is pure logic goes in a small class with unit tests** (`SequenceSanitizer`, `DraftPolicy`, `TriggerSummaryFormatter`, `InputParsers`, `SequenceMapper`).

## Adding a feature (example: a new trigger)

1. Add the enum value in `model/TriggerType` and its fields in `model/SequenceTrigger`.
2. Add its dialog in `ui/dialog/TriggerDialogs` and a line in its chooser (`TRIGGER_TYPE_LABELS`) and in `SequenceBuilderActivity.onTriggerTypeChosen`.
3. Add its wording in `viewmodel/TriggerSummaryFormatter` (+ a test).
4. Teach `data/TriggerMatcher` (and the service/receiver that detects it) to run it.
5. If it needs a permission: add it to `PermissionRepository` / `PermissionState`, a row in Settings, and update the in-app disclosure and `docs/PRIVACY_POLICY.md`.

## Location / privacy-sensitive features

Any location permission request must go through `LocationDisclosureActivity` (prominent disclosure, Allow / Don't Allow, Back = deny). The disclosure text lives in `res/values/strings.xml` (`location_disclosure_*`) and must always match `docs/PRIVACY_POLICY.md`.
