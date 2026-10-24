# Scenes and reusable widgets

The settings category **Scenes & widgets** owns scene management, the widget
library, automatic switching and privacy controls. The preview no longer opens a
separate scene sheet. Opening a scene shows its source, activation action and
ordered widget placements. Rows support long-press dragging, enable switches and
swipe-to-delete. Accessibility actions also move rows up and down.

`StreamWidget` stores shared content and the library enable flag. A scene stores
`SceneWidget` references with independent enable flags, percentage coordinates and
scale. Changing shared content updates every scene using it; changing a placement
changes only that scene. The widget editor also lists every scene with a visibility switch. Saving adds
missing placements or changes their enable flag in the same transaction as the
content update. Turning a placement off keeps its coordinates, scale and list
position, so it can be enabled again from either editor. Only edited scene switches
are written; other scene changes remain intact. Deleting a definition removes references atomically.
Deleting or editing an item preserves the order of unrelated items. Disabling a
scene excludes it from automatic switching and hides its widget composition.

`SceneRepository` persists scenes and definitions in the same Preferences DataStore.
Library initialization is a single idempotent transaction: existing overlay settings
become definitions, enabled legacy overlays become placements, and each legacy
scene receives its own text definition to preserve its saved template. A null
placement list identifies a legacy scene. New scenes use an explicit placement
list and do not change the selected stream destination when activated.

The preview renders placements in their stored order. Each widget uses its own
ViewModel key and settings flow, which combines shared content with visibility in
the active scene. Runtime collection is retired when the widget leaves the
composition. Chat readers are isolated per widget so one instance cannot stop another
instance or the bot session. Subtitle placements share one recognition session
for the active scene, avoiding competing microphone recognizers. The bot channel stays
editable in Chat-Bot settings and remains independent of individual chat widgets. Browser widgets use an
HTTP(S) WebView with a configurable page viewport in pixels; local file/content
access is disabled and the WebView is destroyed when removed.

The layout editor supports dragging, numeric coordinates, scale and nine anchor
presets. Save/load stores a layout in DataStore for reuse across scenes and widget
types. Scene source selection enumerates Camera2 IDs and facing information rather
than guessing front/back from camera numbers. Screen capture requests Android
consent when manually activated. Empty sources use RootEncoder's black filter in
the video path, before the preview widget composition.

Widgets retain the existing preview overlay rendering path. This change does not
add an encoder compositor for Compose widgets; checking transmitted overlays and
camera/screen transitions on hardware remains a separate device acceptance step.

## Pomodoro scene widget

Pomodoro is a widget type, with independent work/break durations and one timer per
library definition. Its editor has a **Save and start** action that persists the
current durations and scene choices before starting; the widget itself provides
start/pause, skip and reset. Work completion increments the session counter and
moves to a break; break completion starts the next work interval. Phase completion
vibrates once. Hiding a scene placement does not pause its timer. Disabling the
library widget pauses it; changing durations resets it; deleting it removes its
runtime. State is kept while the app process lives and resets on process restart.

The old standalone menu and routes were removed. An idempotent migration imports
configured legacy durations into an existing library. An enabled legacy timer is
attached to the active scene. The old preference keys remain readable for this
migration, but no longer gate starting a widget. The tested controller is shared
from `domain`; the runtime and UI belong to `feature-widgets`.
