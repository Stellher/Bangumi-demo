# Official Kyant AndroidLiquidGlass Catalog components

Upstream: https://github.com/Kyant0/AndroidLiquidGlass
Tag: **1.0.0**
Author: **Kyant**
License: **Apache License, Version 2.0** — see `KYANT_APACHE_2.0_LICENSE.txt`.

The following files are included verbatim (unchanged) from the above tag under
`app/src/main/java/com/kyant/backdrop/catalog/`:

- `components/LiquidBottomTab.kt`
- `components/LiquidBottomTabs.kt`
- `components/LiquidButton.kt`
- `components/LiquidSlider.kt`
- `components/LiquidToggle.kt`
- `utils/DampedDragAnimation.kt`
- `utils/DragGestureInspector.kt`
- `utils/InteractiveHighlight.kt`

Also depends on the official upstream Maven artifacts:
`io.github.kyant0:backdrop:1.0.0` and `io.github.kyant0:capsule:2.1.1`.

**Integration changes to the Bangumi app** (under
`com/example/bangumioauthdemo/`, not in the vendored files):
- App navigation is rendered with Kyant's unmodified `LiquidBottomTabs`
  + `LiquidBottomTab`, with its spring, drag, highlight, refraction,
  chromatic aberration, and velocity-sensitive glass geometry.
- All generic glass buttons are routed to Kyant's actual `LiquidButton`.
- Selection rails use the same upstream bottom-tabs pair instead of
  recreating a glass thumb.
- Rating and privacy controls directly use `LiquidSlider` and `LiquidToggle`.
- The reusable floating popup uses the original `DialogContent.kt` shader
  parameters (colorControls, blurred/deep refractive continuous rounded
  rectangle, plain highlight), adapted from a centered Catalog sample to
  Bangumi's editable bottom sheet. The Catalog itself does not ship an
  importable high-level `LiquidBottomSheet` or `LiquidDialog` component.
  Reference: https://github.com/Kyant0/AndroidLiquidGlass/blob/1.0.0/catalog/src/main/java/com/kyant/backdrop/catalog/destinations/DialogContent.kt

The original license and copyright attribution are preserved.
