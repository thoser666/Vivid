# Scenes and reusable widgets

The settings category **Scenes & widgets** owns scene management, the widget
library, automatic switching and privacy controls. With Privacy enabled, its
scene-management section offers automatic face pixelation alongside manual zones
and full-frame pixelation. Hiding these controls with the master switch preserves
the saved face-pixelation selection. The preview no longer opens a
separate scene sheet. Opening a scene shows its source, activation action and
ordered widget placements. Rows support long-press dragging, enable switches and
swipe-to-delete. A note above each scene’s widget list explains that opening a
widget adjusts its position in that scene. Accessibility actions also move rows up and down.

`StreamWidget` stores shared content and the library enable flag. A scene stores
`SceneWidget` references with per-scene enable flags, percentage coordinates and
scale. Changing the library enable switch updates the widget and all of its existing
placements in one transaction: off disables them in every scene, and on enables
them again, including previously disabled placements. Positions, scale, layer order
and scene enable flags are preserved; scenes without this widget stay unchanged.
Changing shared content updates every scene using it; changing a placement
changes only that scene. The widget editor also lists every scene with a visibility switch. Saving adds
missing placements or changes their enable flag in the same transaction as the
content update. Turning a placement off keeps its coordinates, scale and list
position, so it can be enabled again from either editor. When saving content without changing the global enable flag, only edited scene
switches are written; other scene changes remain intact. A note before the library
explains that global enable, disable and delete actions affect every scene using
the widget. Deleting a library definition requires confirmation and removes all
scene references atomically. Cancelling restores the swiped row. Removing a
placement from one scene takes effect immediately without a confirmation dialog.
Deleting or editing an item preserves the order of unrelated items. Disabling a
scene excludes it from automatic switching and hides its widget composition.

`SceneRepository` persists scenes and definitions in the same Preferences DataStore.
Library initialization is a single idempotent transaction: existing overlay settings
become definitions, enabled legacy overlays become placements, and each legacy
scene receives its own text definition to preserve its saved template. A null
placement list identifies a legacy scene. New scenes start with an explicit empty
placement list; they do not copy widgets from the active scene. Library migration
preserves explicit placements, including empty lists, and migrates only legacy
scenes. Activating a managed scene does not change the selected stream destination.

The encoder scene host renders placements in their stored order. Each widget uses its own
ViewModel store and settings flow, which combines shared content with visibility in
the active scene. Runtime collection is retired when the widget leaves the
composition. Chat readers are isolated per widget so one instance cannot stop another
instance or the bot session. Subtitle placements share one recognition session
for the active scene, avoiding competing microphone recognizers. The bot channel stays
editable in Chat-Bot settings and remains independent of individual chat widgets. Browser widgets use an
HTTPS WebView with a configurable page viewport in pixels; local file/content
access is disabled and the WebView is destroyed when removed. The editor rejects
HTTP addresses consistently with the app’s existing cleartext network policy.
JavaScript and WSS updates run in the scene host while settings are open or the
Activity is stopped, as long as the streaming process and GL remain alive.

The layout editor supports dragging, numeric coordinates, scale and nine anchor
presets. Save/load stores a layout in DataStore for reuse across scenes and widget
types. Portrait and landscape share the same placement coordinates and scale;
there are no separate layouts per orientation. Content extending beyond the scene
canvas is clipped. Scene source selection enumerates Camera2 IDs and facing information rather
than guessing front/back from camera numbers. Screen capture requests Android
consent when manually activated. Empty sources use a uniform in the persistent final scene filter, so removing or
adding creative filters cannot expose the camera. Scene activation closes this
gate before source/lens changes and restores it if switching is rejected.

`SceneLayerHost` mounts one Compose tree in a private, own-content virtual display.
It owns a resumed lifecycle and independent ViewModel stores while GL is alive;
navigation and Activity backgrounding do not suspend clocks, slideshows or browser
updates. Removed widgets clear their stores; GL shutdown disposes the composition,
WebViews, display and image sink. Hardware Canvas producers are released
synchronously before RootEncoder shuts EGL down; this prevents a stale HWUI
context during orientation or encoder restarts. The host retains one reference to
the shared default EGL display for the lifetime of Android’s cached HWUI renderer;
RootEncoder’s per-surface termination therefore cannot invalidate that renderer.
This reference owns no additional GL context or surface. Permission prompts remain an Activity concern.
The host draws invalidated content into a transparent hardware Canvas surface,
with updates capped at 30 Hz independently of the video encoder's 30/60 FPS.
The final GL pass composites this premultiplied surface after privacy and creative
effects, and supplies the same pixels to encoder, recording and preview. The scene
canvas uses encoder dimensions with a 360-dp short side, independent of phone
size/density; browser viewport dimensions remain physical pixels. Preview shows the whole frame with letterboxing; touch
coordinates follow this fit and are forwarded to the mounted scene. Local
controls have an explicit higher z-index and receive touches before widgets.
Settings, Controls and Start/Stop stay above widget content in the local UI and
are excluded from the encoded scene.

Before streaming, ordinary 1280×720 / 30-FPS camera capture feeds a GL scene
canvas resolved from the selected stream profile, including camera/encoder
fallbacks. The canvas follows the saved operating mode: portrait uses 9:16 and landscape
uses 16:9. Camera capture keeps its native landscape buffer, while the shared GL
scene and encoder output use the selected axes. The full composed frame is fitted
to the preview without rotating scene text a second time.
Saving/selecting a profile updates an idle canvas without changing browser viewport
pixels or widget placements. Changes made while streaming or recording are retained
for the next idle preview and do not resize the running encoder. Resizing preserves
the selected camera lens. High-speed capture and the experimental codec bridge
start only with streaming/recording: composition size matches in advance, but
exposure and camera detail can still differ, particularly with 60-FPS output.

Owned add/remove effect slots also free their framebuffer resources on removal.
The scene filter is retained during effect rebuilds; owned effects are replaced
before it without `clearFilters()`. GL restarts create a fresh scene surface.

## Layered compositions

The GPU compositor integrated from #330 through #355 adds an optional versioned
`SceneComposition` to each scene. Camera, USB/UVC, screen capture, image, video,
text and clock sources can be arranged as independently transformed layers.
The existing widget library remains the final transparent pass after the stable
capture/privacy/effects container, so its browser and other dynamic widgets keep
their existing lifetime, visibility and placement rules. Empty legacy scenes
migrate to an empty canvas without creating a camera. A stored EMPTY source does
not black out an explicitly configured composition or its editor draft.

Controls → Edit scene opens the layered editor for the active scene. It supports
fullscreen, picture-in-picture and side-by-side layouts, gestures, crop, rotation,
opacity, order, shared-source duplication and undo/redo. Save persists the whole
draft; Cancel restores the prior composition, camera and source-blackout state.
The settings scene/widget editors and local scene selector remain available.

Native scene output honors operating-mode orientation, requested FPS, bitrate
and keyframe interval. Its single encoder is shared by recording and RTMP targets.
Canvas producers retire before native scene GL shutdown as well as legacy GL
shutdown. Scene mutations reject malformed or unsupported future documents,
including global widget operations and initialization, while the first layered
save retains the original scene JSON as a recovery backup.

## Local preview controls

The preview has a scene selector containing only enabled scenes in their saved
order. Selecting one uses the same serialized scene activation path as settings.
Screen-capture scenes request Android consent before activation; cancelling the
request restores the previous source and leaves the active scene unchanged.
The zoom selector above it reads the saved rear/front preset list for the current
camera, including its order, and follows the actual zoom after preset changes,
pinch gestures and resets. Unsupported ratios are disabled instead of being
silently clamped. On a fresh app start, the camera applies the first supported
preset in the saved order for its facing once settings and camera capabilities are
ready. New installations default to rear-camera presets 1×, 1.5× and 2×; existing
saved preset lists retain their values and order. Manual zoom is retained across preview recreation and settings updates
within the same process. The zoom selector is hidden for empty/non-camera sources and
when camera zoom is unavailable. Both rows scroll horizontally when needed.

Interface → Operating mode fixes portrait or landscape only on the main streaming
screen and the encoded camera frame. Changing it during a running stream or
recording is retained for the next session, without resizing the active encoder.
Other navigation destinations, including all settings pages, follow the
device sensor in either orientation. Navigation restores the saved stream mode on
return, including after Activity recreation; changing the mode inside settings
does not rotate the menu.

Interface groups settings under Operating mode, Local overlays and Theme. Language
selection belongs to Theme alongside theme mode and accent color. Local overlays
has its own heading and a standard navigation row with a chevron and description,
opening independent switches for the scene selector, zoom controls, the FPS counter, bitrate and stream duration. Changes take
effect immediately and persist globally, independently of stream profiles and
scene widgets. FPS uses the encoder measurement; an unavailable measurement is
shown as pending. Stream duration follows the engine session start, including
time spent with the indicator hidden. Hiding bitrate affects only its local HUD
indicator; encoder statistics continue updating.

These controls and indicators are Activity UI, outside the encoder composition.
The selectors use translucent backgrounds and primary accent roles for selected
buttons; Controls uses the same primary/on-primary roles to show the selected
accent directly. The selector rows and Start/Stop share a vertical layout to prevent
overlap as their content changes.

On the first app launch, MainActivity requests camera, microphone and (Android
13+) notification permissions once. Returning to the preview after a camera grant
starts the idle camera and makes supported zoom presets available. Denied
permissions are not requested again on navigation or rotation; Start can retry the
missing permissions. Location and screen capture keep their contextual requests.

Configuration, connection, recording and camera feedback appears in the central
free area between the top actions and bottom selectors. The bounded message does
not cover those controls, including in landscape. Tapping dismisses it immediately;
otherwise it disappears after three seconds. A new message or repeated Start
attempt receives its own timeout. Dismissing feedback does not change the underlying
stream state or bypass configuration checks.

## Verification and current limits

Hardware RTMP checks on Galaxy S21 (Android 15) and POCO X7 Pro (Android 16)
verified widget rendering in both screen orientations, scene/library visibility,
live browser WSS updates during settings navigation and Activity backgrounding,
and touch access to Settings, Controls and Start/Stop above an opaque browser.
Decoded stable empty-scene intervals stayed black during effect changes; the
checks exclude transition boundaries and do not establish frame-exact switching.

The measured Debug runs sustained approximately 30 FPS. Experimental FHD60 was
applied without fallback but averaged approximately 56–58 FPS, including
two-minute continuous sections on POCO; sustained 60 FPS is not established.
The scene surface's separate 30-Hz refresh cap also applies when video targets
60 FPS. These checks do not establish long-term thermal or memory stability.

Separate Samsung MP4 checks verified portrait 720×1280 / landscape 1280×720 at
30 FPS and portrait 1080×1920 / landscape 1920×1080 in experimental 60-FPS mode.
Decoded frames retained an image-widget marker in both orientations. Repeated
recording/orientation transitions passed after the HWUI/EGL lifetime fix; the
short experimental runs measured approximately 58–59 FPS rather than proving
sustained 60 FPS.

Device coverage includes camera and empty sources. Hardware MediaProjection/UVC
transitions, authenticated live chat, live speech recognition and physical QR
scanning remain outside this acceptance coverage.
An empty subtitle layer and an unauthenticated chat placeholder only verify those
display states.

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
