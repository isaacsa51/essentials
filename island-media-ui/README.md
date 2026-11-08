# Island Media UI

A standalone Compose rendering module for Island Media. It does not depend on `:app`, MediaSession, notification listeners, accessibility services, or the settings repository.

The main Island player uses the Figma foreground layout, a translucent background surface, progressive blur, and an independent AGSL particle layer. The production host draws the visual layers outside the animated card container and supplies the live card outline in window coordinates.

## Customization entry points

`src/main/java/com/sameerasw/island/media/ui/IslandMediaPlayerRenderer.kt` is the customization entry point for the main Island player:

- `Background`: an independent background that can replace the blurred artwork. `fallback` renders the original host background.
- `Underlay`: the top fade mask from Figma. Drawing outside the card does not enlarge its touch region.
- `Effects`: Shader / Canvas effects above the background, without interactive controls.
- `Content`: the foreground layout. Use media information in `scope.state` and playback callbacks in `scope.actions` to replace the layout.

`MediaPlayerBackdrop` draws the underlay, background, and effects in that order. Media registers them through `DetachedSurfaceVisuals` in a dedicated root visual slot; the foreground is laid out separately above them. Brief, Calendar, and other content keep the original `SurfaceBackdrop` slot.

Effects must remain draw-only and must not install touch handlers that intercept playback controls or Island gestures.

`DefaultMediaPlayerRenderer` and `DefaultMediaPlayerForeground` retain the original layout for Brief and comparison previews. Changing only `IslandMediaPlayerRenderer` leaves Brief unchanged; editing shared components or the default foreground can affect both entry points. Compact artwork, the equalizer, and gestures remain managed by the app's `MediaPlugin`.

## Host boundaries

`MediaPlayerContract.kt` defines media state, action callbacks, layout and motion parameters, and shared component interfaces. The host injects fonts, icons, progress controls, the equalizer, and haptics through `MediaPlayerEnvironment`. The module does not reference app resources or its global theme.

The app adapter is `island/plugins/media/MediaExpanded.kt`:

- Maps Android Bitmap, IslandExpandedScope, and shared components to the module interfaces.
- Registers background and effects through `DetachedSurfaceVisuals` for layered Media. Other renderers use `SurfaceBackdrop`. Without a host slot, visuals render locally for debugging.
- Defaults to the original renderer. `MediaPlugin` explicitly selects `IslandMediaPlayerRenderer`; Brief keeps the original.
- `drawBackground=false` skips both the background and its effects, preserving Brief's external background.

Media items select a transparent host surface with `ExpandedSurfaceStyle.Layered`. The foreground is clipped separately. Background and Shader are siblings of the card at the host root, so they do not inherit its clip, `contentScale`, `contentAlpha`, or `growGate`. All layers share the live card position, size, and corner radius. The black compact pill and expanded surface crossfade according to the actual size progress; particle radius and spacing stay in physical pixel coordinates. Other items retain their default black surface.

Width, corner radius, position, scale, and font scale remain controlled by user settings. The 448-wide Figma frame is a reference with 1 design unit = 1dp.

### Background and camera protection

The background uses a black radial transparency gradient. Two inner shadows use 35% of the album-derived Material dark Tonal Spot Primary: radius 24 with offset (0, -8), and radius 2 with offset (0, 0). These reproduce the edge effects in Figma nodes `199:867` / `199:838`. Inner shadows render in a zero-origin child measured to the live card size, then move into position on the independent canvas. Progressive blur composites on the full outer canvas to retain the fine stable edge and pulse softening. Disabled outer shadows from the design are omitted.

Camera protection intentionally overrides the design's top highlight. A post-blur alpha mask removes the inner shadows from the top edge and upper corners through at least the camera's lower edge plus 4dp, then restores them smoothly over 24dp. The radial gradient is centered on the fixed host camera position, independent of card translation or scale. Its first opaque black stop covers the camera radius plus 4dp, normalized by the ellipse's shorter axis. Remaining stops are remapped into the remaining interval so they stay strictly ordered during small-size transitions.

`MediaPlayerSurface.camera` supplies `MediaCameraGeometry(center, radius)` in physical pixels within the current canvas. Production converts `CameraGeometry` to drawing-window coordinates; previews use unanimated target geometry. A pure black camera guard is drawn after blur. Particles avoid the same 4dp camera margin. Local surface previews compensate for the expanded Effects canvas origin.

The app adapter extracts color in the background using the existing Material Components pipeline: QuantizerCelebi -> Score -> SchemeTonalSpot. This module receives the resolved colors.

### Pulse and detached visuals

`LocalIslandPulse` passes the original root pulse to the Media background. No new production trigger or timer is introduced. At peak pulse, spatial progressive blur ranges from 2dp at the top to 16dp at the bottom. It is disabled at pulse zero and in power-saving mode.

The original pulse setting, triggers, and timing remain in place. Legacy blur, spread, offset, and opacity settings do not control the new Media rendering. Other items retain their existing effects.

The production host uses a non-touchable drawing window covering the display, still horizontally anchored to the camera. Interaction windows and touch-coordinate forwarding retain their existing behavior. Text-input plugins restore the original bounded input window while text input is active. The background fade spans the visual canvas width; inner shadows and progressive blur use the explicit card outline. Particles sit above the background and below the media foreground.

`IslandVisualSlot` retains a single Media visual registration, preserving the Shader and point set during the handoff to the compact pill. It clears after hiding, removal, or switching to another expanded item. `MediaPlayerSurface.geometry` supplies `MediaVisualGeometry(bounds, cornerRadius)` in physical pixels, read during drawing. Standalone previews without a geometry provider use the local surface size and reserve space with `MediaParticleOutset` (72dp) and `effectOutset`. These values no longer determine the production window or size-animation boundaries.

The default style uses the new layout. A path difference cuts out the artwork beneath the 32dp app badge, leaving a 4dp transparent gap. Right-side artwork mirrors the geometry. ArtControls continues to reuse the original foreground to preserve artwork playback indicators and favorites. Brief keeps the original renderer.

### Nonelastic Media motion

Layered Media uses the Material Components `m3_sys_motion_easing_emphasized` interpolator, including its complete two-segment path:

```text
M 0,0 C 0.05,0 0.133333,0.06 0.166666,0.4 C 0.208333,0.82 0.25,1 1,1
```

`IslandSurfaceMotion` uses duration-based transitions: 500ms for expansion and 400ms for collapse, with shorter Material duration tokens for content, release, and queue effects. All durations respect the existing Island animation-speed setting. The profile is shared with compact slots, placement, queue bubbles, and the swipe-intent indicator. Elastic liquid deformation, liquid bridges, and compact jelly transforms are disabled for this profile. Other content keeps its original animation profile. Pulse timing and particle flow are independent of this change.

## Particle Shader

The implementation follows the **actual render loop** in the user-provided `dot_wave_effect_demo.html`. Although that HTML defines a Voronoi F1 function, its renderer does not call it. Radius depends on velocity and a small distance factor; distance primarily controls opacity.

- `PoissonDisk.kt` generates a fixed Bridson Poisson point set in the background. Toroidal distance constraints support seamless tiling. Reproducible sampling avoids the jumps caused by rebuilding random points on every adjustment. Only points in the inner/outer capsule emission band are selected.
- `ParticleFieldMath.kt` ports the reference Perlin gradients, two-layer FBM (0.7/0.3, second frequency 2.02), central-difference Curl with epsilon 1, and an additional 0.6x FBM displacement. A seeded random byte table supports numerical comparisons. Animation time is multiplied by `speed`.
- Displacements at `t` and `t+0.05` estimate instantaneous velocity as in the reference. The velocity baseline is 20 CSS px per time unit, converted for Android density.
- SDF distance is evaluated at the displaced point center. Inner `smoothstep(-innerBand,0,d)` and outer `1-smoothstep(0,outerBand,d)` determine per-point opacity; values below 0.015 are skipped. Radius is `clamp(maxRadius*(1-0.25*normSpeed)*(0.85+0.15*opacity), minRadius, maxRadius)`, so distance does not collapse points into tiny specks.
- `RuntimeParticlePainter.kt` reuses triangle meshes and vertex-color buffers for a single batched draw. White vertex colors carry opacity and multiply the monochrome AGSL output. Complete point shapes are not clipped at the emission-band boundary. `GeminiParticleShader.kt` handles only the local circle/diamond SDF and 1 physical pixel of antialiasing.

Defaults match the user's tuned Studio values (2026-11-09): minimum spacing 6dp, radius 0-1.5dp, displacement 12dp, noise frequency 0.012/dp, speed 1x, inner band 48dp, outer band 64dp, opacity falloff 4, overall opacity 35%, and circular points. `noiseScale` is the reciprocal frequency (about 83.333dp). Studio and production Media share `ParticleFieldStyle()` defaults. Studio starts with pulse 100% and card width 432dp; Reset restores this configuration.

`uShapeType=0` selects circles; `1` selects 45-degree diamonds. Following the HTML, diamond axis radius is multiplied by 1.25 and distance is divided by sqrt(2) to retain 1px antialiasing.

`falloffExponent` controls distance fade: the default is 4; 1 preserves the base curve; 0 disables distance fade inside the emission band; values above 1 accelerate fading and values between 0 and 1 slow it. It affects opacity only, not position or radius. `opacity` controls layer opacity from 0 to 1, default 0.35 (35% in Studio). Final alpha is `pulse * opacity * smoothDistanceAlpha^falloffExponent * pointAA`. No points are generated beyond the emission band.

Color comes from the artwork's Material dark Primary. Rendering is monochrome SourceOver, without Bloom or Gaussian glow, between the background and media foreground.

`activation` uses the original root pulse. The frame clock runs only when pulse is positive, points are ready, overall opacity is positive, the page is STARTED, and power-saving mode is off. Refresh stops when pulse reaches zero or the page/expanded content is left.

API 26-32 and AGSL compilation failures use the same point set at `t=0`, with cached paths grouped by opacity. Power-saving mode disables particles. `timeSeconds` freezes time; `forceFallback` previews the fallback. `capsuleInset` defaults to `MediaParticleOutset` (72dp); an explicit `capsuleRect` uses canvas-local pixels.

Tests inject the same fixed random table into the HTML's pure math functions and compare three sets of offset, velocity, and radius values with Kotlin. Additional checks cover density conversion, alpha/radius separation, Poisson minimum distance, and absence of axis alignment.

## Debug Studio

`app/src/debug` contains `MediaPlayerPreviewActivity`, included only in debug APKs. It needs no notification-listener or accessibility permissions.

Debug-only Figma reference artwork and wallpaper support comparison. Controls switch the original/custom renderer, background, particles, circle/diamond shape, and artwork side. Numeric inputs control card width, pulse strength, minimum/maximum radius, spacing, displacement, speed, noise frequency, inner/outer bands, opacity falloff, and overall opacity.

Playback, favorites, and seeking use simulated state. Normal Studio starts with pulse 1 for continuous particle preview, background and wallpaper off, and Fit on. Production Media background behavior is unchanged. Capture mode defaults to pulse 0. Constant Studio pulse is only for tuning; the real-host preview uses the root's original pulse setting, triggers, and decay.

```powershell
.\gradlew.bat :island-media-ui:compileDebugKotlin :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.sameerasw.essentials/.island.preview.MediaPlayerPreviewActivity
# Capture: stable pulse=0 or peak pulse=1; optionally add --ei width_dp 320
adb shell am start -S -n com.sameerasw.essentials/.island.preview.MediaPlayerPreviewActivity --ez capture true --ef pulse 1
# Freeze the Shader phase without changing pulse
adb shell am start -S -n com.sameerasw.essentials/.island.preview.MediaPlayerPreviewActivity --ez capture true --ef pulse 1 --ef shader_time 0.8
# Preview the static fallback
adb shell am start -S -n com.sameerasw.essentials/.island.preview.MediaPlayerPreviewActivity --ez capture true --ef pulse 1 --ez shader_fallback true
# Bottom capsule without wallpaper, blur, or background glow; shader_shape=1 selects diamonds
adb shell am start -S -n com.sameerasw.essentials/.island.preview.MediaPlayerPreviewActivity --ez capture true --ez capsule true --ei shader_shape 1
# shader_disabled=true gives a no-Shader baseline at the same pulse; paused=true stops simulated playback animation
# Real IslandRoot with simulated data, expansion/collapse, updates, and the original pulse switch
adb shell am start -S -n com.sameerasw.essentials/.island.preview.MediaPlayerPreviewActivity --ez host true
# Move a narrow card away from the screen edge to distinguish container clipping from display boundaries
adb shell am start -S -n com.sameerasw.essentials/.island.preview.MediaPlayerPreviewActivity --ez capture true --ez host true --ei width_dp 320 --ef top_dp 160 --ef shader_time 0.8
```

Studio has a solid dark settings panel, a fixed preview, and separately scrolling controls. Landscape uses two columns. Fit reserves space for the full effect; 1:1 checks actual size. Wallpaper affects only the preview wallpaper.

All numeric controls are unit-labeled text inputs. Valid values update the preview immediately; Done/Enter or a focus change finishes editing. Empty, incomplete decimal, and invalid input retain the last valid preview value. Reset restores visual parameters and clears drafts and errors.

Studio normally demonstrates the local backdrop fallback. **Test Island transitions** opens the real IslandRoot with the current particle settings; returning preserves them. Host mode starts compact (expanded in capture mode), using real artwork, the original pulse, and collapse gestures to verify detached visuals. Debug switches do not change application settings.

Actual notification/media listeners, WindowManager touch windows, and multi-player queues still require verification in the fully authorized app.
