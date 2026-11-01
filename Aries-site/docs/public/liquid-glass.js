(() => {
  var __defProp = Object.defineProperty;
  var __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : obj[key] = value;
  var __publicField = (obj, key, value) => __defNormalProp(obj, typeof key !== "symbol" ? key + "" : key, value);

  // shaders/sdf.ts
  var SDF_GLSL = (
    /* glsl */
    `
// Corner style: 0 = circular (standard arc), 1 = continuous (squircle/superellipse).
// Declared here (in SDF_GLSL) because sdShape references it, and SDF_GLSL is
// included by multiple shaders (element, shadow, highlight, plain-rect).
uniform float uCornerStyle;

// --- Continuous-curvature SDF texture (capsule shape) ---
// When uUseContinuousSdf > 0.5, sdShape() dispatches to sdContinuousCurvature
// which samples a precomputed SDF texture (generated from the G2-continuous
// Bezier path in continuous-curve.ts). Only the dialog card sets this to 1;
// other shaders that include SDF_GLSL leave it at the default 0 \u2014 sdShape
// falls through to the analytic sdRoundedRect / sdContinuousRoundedRect path.
uniform sampler2D uContinuousSdf;
uniform float uUseContinuousSdf;        // 0 or 1
uniform vec2  uContinuousSdfTexSize;    // SDF texture size in px (256, 256)
uniform vec2  uContinuousSdfElementSize; // element's original w,h in px

// radiusAt \u2014 picks the corner radius from cornerRadii based on which
// quadrant 'coord' is in. For uniform radii (the catalog case) this
// always returns the same value.
float radiusAt(vec2 coord, vec4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

// sdRoundedRect \u2014 signed distance to a rounded-rect boundary.
// Negative inside, positive outside, zero on the edge.
// Uses standard circular arcs for the corners.
float sdRoundedRect(vec2 coord, vec2 halfSize, float radius) {
    vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

// sdContinuousRoundedRect \u2014 continuous-curvature rounded rect.
// The original uses G2-continuous Bezier corners (ContinuousCurvatureRoundedRectangleCornerBuilder).
// The visual difference between Continuous and Circular is very subtle (only
// curvature continuity at the tangent points). For the SDF-based renderer,
// the circular arc SDF (sdRoundedRect) is a close enough approximation \u2014 the
// Bezier corners deviate from the arc by <0.5% of the radius, which is
// sub-pixel at typical element sizes.
//
// When uCornerStyle=1 (continuous), we use sdRoundedRect directly. The
// difference from the original is imperceptible. A future upgrade could
// implement exact Bezier SDF for pixel-perfect matching.
float sdContinuousRoundedRect(vec2 coord, vec2 halfSize, float radius) {
    return sdRoundedRect(coord, halfSize, radius);
}

// sampleClipMask \u2014 sample R channel (coverage) from the mask texture.
// Returns browser-native AA coverage [0,1] for clip + edgeAlpha.
float sampleClipMask(vec2 coord, vec2 halfSize, float radius) {
    float maxDim = max(max(uContinuousSdfElementSize.x, uContinuousSdfElementSize.y), 1e-4);
    float aspectW = uContinuousSdfElementSize.x / maxDim;
    float margin = 4.0;
    float drawW = (uContinuousSdfTexSize.x - 2.0 * margin) * aspectW;
    float scale = drawW / max(uContinuousSdfElementSize.x, 1e-4);
    vec2 tex = uContinuousSdfTexSize * 0.5 + coord * scale;
    vec2 uv = tex / uContinuousSdfTexSize;
    return texture2D(uContinuousSdf, uv).r;  // R = coverage [0,1]
}

// sampleClipSdf \u2014 sample G channel (SDF) from the mask texture.
// Returns signed distance: negative inside, positive outside, 0 at edge.
// Same shape as sampleClipMask (both from the same Bezier path), so clip
// and stroke shapes are always identical.
float sampleClipSdf(vec2 coord, vec2 halfSize, float radius) {
    float maxDim = max(max(uContinuousSdfElementSize.x, uContinuousSdfElementSize.y), 1e-4);
    float aspectW = uContinuousSdfElementSize.x / maxDim;
    float margin = 4.0;
    float drawW = (uContinuousSdfTexSize.x - 2.0 * margin) * aspectW;
    float scale = drawW / max(uContinuousSdfElementSize.x, 1e-4);
    vec2 tex = uContinuousSdfTexSize * 0.5 + coord * scale;
    vec2 uv = tex / uContinuousSdfTexSize;
    float g = texture2D(uContinuousSdf, uv).g;  // G = SDF [0,1]
    return (g * 2.0 - 1.0) * radius;  // decode to element-space distance
}

// sdClipShape \u2014 SDF for clip/discard when uUseContinuousSdf is OFF.
float sdClipShape(vec2 coord, vec2 halfSize, float radius) {
    return sdRoundedRect(coord, halfSize, radius);
}

// sdShape \u2014 SDF for refraction/highlight internal calculations.
// When uUseContinuousSdf=1, uses sampleClipSdf (same shape as clip mask).
// Otherwise uses sdRoundedRect.
float sdShape(vec2 coord, vec2 halfSize, float radius) {
    if (uUseContinuousSdf > 0.5) {
        return sampleClipSdf(coord, halfSize, radius);
    }
    return sdRoundedRect(coord, halfSize, radius);
}

// gradSdRoundedRect \u2014 gradient of the SDF (points outward from edge).
// Used both for refraction direction and highlight specular.
vec2 gradSdRoundedRect(vec2 coord, vec2 halfSize, float radius) {
    vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        vec2 v = max(cornerCoord, vec2(0.0));
        // Guard against normalize(0,0) -> NaN
        float len = length(v);
        if (len < 1e-6) return vec2(0.0);
        return sign(coord) * (v / len);
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * vec2(gradX, 1.0 - gradX);
    }
}

// rotateBy \u2014 rotate a 2D vector by angle (radians). Used to un-rotate the
// sample coord into the element's local space (so the SDF shape appears
// rotated by +uElementRotation), and to rotate refraction offsets back to
// screen space.
vec2 rotateBy(vec2 v, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec2(v.x * c - v.y * s, v.x * s + v.y * c);
}
`
  );
  var COVER_GLSL = (
    /* glsl */
    `
// Returns wallpaper UV for a canvas pixel coordinate (top-left origin).
vec2 coverUv(vec2 canvasPx) {
    float canvasAspect = uCanvasSize.x / uCanvasSize.y;
    float wpAspect = uWallpaperSize.x / uWallpaperSize.y;
    vec2 uv = canvasPx / uCanvasSize;
    if (wpAspect > canvasAspect) {
        // Wallpaper is wider than canvas \u2014 crop horizontally.
        float s = canvasAspect / wpAspect;
        uv.x = (uv.x - 0.5) * s + 0.5;
    } else {
        // Wallpaper is taller than canvas \u2014 crop vertically.
        float s = wpAspect / canvasAspect;
        uv.y = (uv.y - 0.5) * s + 0.5;
    }
    return uv;
}

// Per-axis scale: 1 canvas pixel in wallpaper UV units.
// Used to convert a blur radius (in canvas px) into UV-space offsets
// for poisson-disc sampling.
vec2 canvasPxToUvScale() {
    float canvasAspect = uCanvasSize.x / uCanvasSize.y;
    float wpAspect = uWallpaperSize.x / uWallpaperSize.y;
    if (wpAspect > canvasAspect) {
        return vec2(canvasAspect / wpAspect, 1.0) / uCanvasSize;
    } else {
        return vec2(1.0, wpAspect / canvasAspect) / uCanvasSize;
    }
}
`
  );

  // shaders/element-uniforms.ts
  var ELEMENT_UNIFORMS_GLSL = (
    /* glsl */
    `
uniform sampler2D uBackdrop;
uniform sampler2D uWallpaperSampler;  // wallpaper texture (unscaled backdrop for toggle knobs)
uniform sampler2D uTabsBackdropSampler;  // tabsBackdrop FBO (tinted scene for indicator CombinedBackdrop)
uniform vec2  uCanvasSize;        // canvas size in px
uniform vec2  uWallpaperSize;     // UNUSED \u2014 kept for uniform-set compatibility
uniform vec2  uElementOffset;     // element top-left in canvas px (SCALED rect \u2014 where the quad is drawn)
uniform vec2  uElementSize;       // element size in px (SCALED \u2014 includes graphicsLayer scaleX/scaleY)
uniform vec4  uCornerRadii;       // (topLeft, topRight, bottomRight, bottomLeft) in px (ORIGINAL, unscaled)
uniform float uRefractionHeight;  // px (ORIGINAL space \u2014 NOT scaled by layerScale, faithful to AGSL)
uniform float uRefractionAmount;  // px (ORIGINAL space \u2014 NOT scaled, faithful to AGSL)
// --- Layer transform (faithful to graphicsLayer { scaleX, scaleY }) ---
// The original applies the refraction shader at the ORIGINAL element size, THEN
// scales the entire rendered layer by (scaleX, scaleY) via graphicsLayer. To
// replicate this in a single-pass shader, we compute the SDF/refraction in
// ORIGINAL space (by dividing the screen-space centered coord by uLayerScale),
// then map the refraction offset back to screen space for backdrop sampling.
// This keeps the SDF shape correct (not stretched) while covering the scaled rect.
uniform vec2  uOriginalSize;        // element size in px (ORIGINAL, unscaled by graphicsLayer)
uniform float uOriginalCornerRadius; // corner radius in px (ORIGINAL, unscaled)
uniform vec2  uLayerScale;          // (scaleX, scaleY) from graphicsLayer \u2014 maps original\u2192screen
uniform float uElementRotation;    // rotation in radians (graphicsLayer rotationZ) \u2014 0 = none
uniform float uDepthEffect;       // 0 or 1
uniform float uChromaticAberration; // 0 or 1
uniform float uBlurRadius;        // px
uniform float uSaturation;        // vibrancy = 1.5
uniform float uBrightness;        // brightness offset (0 for vibrancy)
uniform float uContrast;          // 1.0 for vibrancy
uniform vec4  uTintColor;         // rgba; alpha 0 = no tint
uniform vec4  uSurfaceColor;      // rgba; alpha 0 = no surface
uniform vec4  uHighlightColor;    // rgb + 1.0 (alpha handled by uHighlightAlpha)
uniform float uHighlightAngle;    // radians
uniform float uHighlightFalloff;
uniform float uHighlightAlpha;
uniform float uHighlightMode;     // 0=default, 1=ambient, 2=plain
uniform float uHighlightStrokeWidth; // px (full stroke width, matching paint.strokeWidth)
uniform float uHighlightBlur;     // px (BlurMaskFilter radius)
// (Inner shadow removed \u2014 moved to Step 2b post-pass with Canvas2D ring mask.)
// Content scale (non-uniform, faithful to LiquidToggle.kt / LiquidSlider.kt):
//   scale(scaleX, scaleY) { drawBackdrop() }
// Toggle: X gooseLerp(2/3, 0.75, p), Y gooseLerp(0, 0.75, p)
// Slider: X gooseLerp(2/3, 1, p),    Y gooseLerp(0, 1, p)
// At rest Y=0 \u2192 backdrop sampled from a single horizontal line (degenerate),
// but the white overlay (alpha=1) hides it. When pressed, scales to full.
uniform float uContentScaleX;
uniform float uContentScaleY;
// --- Toggle knob CombinedBackdrop effect (faithful to LiquidToggle.kt) ---
// The knob's backdrop is a CombinedBackdrop of:
//   1. Outer backdrop (LayerBackdrop wallpaper OR CanvasBackdrop solid color)
//   2. Scaled trackBackdrop (track color rect, scaled by gooseLerp(2/3,0.75) x gooseLerp(0,0.75))
// uUseToggleBackdrop = 1.0 \u2192 sample outer backdrop + composite scaled track color
// uUseToggleBackdrop = 0.0 \u2192 sample scene (uBackdrop) as before
//
// uUseSolidBackdrop = 1.0 \u2192 outer backdrop is solid color (uSolidBackdropColor)
// uUseSolidBackdrop = 0.0 \u2192 outer backdrop is wallpaper texture (uWallpaperSampler)
// Faithful to ToggleContent.kt:
//   - t1 (on wallpaper): backdrop = LayerBackdrop \u2192 sample wallpaper texture
//   - t2 (on card):      backdrop = rememberCanvasBackdrop { drawRect(color) } \u2192 solid color
uniform float uUseToggleBackdrop;
uniform float uUseSolidBackdrop;
uniform vec4  uSolidBackdropColor;  // rgba 0..1; used when uUseSolidBackdrop = 1.0
uniform vec4  uTrackColor;        // rgba 0..1; alpha 0 = no track color
uniform vec4  uTrackRect;         // (centerX, centerY, halfW, halfH) in canvas px (dpr-scaled)
uniform float uTrackCornerRadius; // canvas px (dpr-scaled)
// --- Bottom tab \u6307\u793A\u5668 CombinedBackdrop (faithful to LiquidBottomTabs.kt) ---
// The \u6307\u793A\u5668's backdrop = CombinedBackdrop(wallpaper, \u5185\u5C42\u80CC\u666F\u677F) where
// \u5185\u5C42\u80CC\u666F\u677F (tabsBackdrop) is a hidden Row with ColorFilter.tint(accentColor). Only the
// opaque \u6807\u7B7E\u5185\u5BB9 (icons/labels) becomes blue after tint \u2014 the glass part
// is transparent. We pass up to 8 tab content rects; pixels inside any rect
// (clipped to the \u5BB9\u5668 capsule) are tinted accentColor.
uniform float uIndicatorBackdrop;    // 0 or 1
uniform vec4  uContainerRect;        // (centerX, centerY, halfW, halfH) in canvas px (dpr-scaled)
uniform float uContainerCornerRadius; // canvas px (dpr-scaled)
uniform vec4  uIndicatorAccent;      // (r, g, b, a) \u2014 accentColor + unused
uniform float uInsetPx;              // indicator backdrop inset in device px (4dp * dpr)
uniform float uIndicatorPressProgress; // 0..1 press progress (for 2nd-layer scale)
uniform float uIndicatorPanelOffset; // panel offset in device px (2nd-layer x translation)
uniform float uDpr;                 // device pixel ratio (for dp\u2192px conversion)
uniform vec2  uContainerCenter;      // container center (scale origin) in canvas px (dpr-scaled)
uniform float uContainerScale;       // container layerBlock scale (1 + 16dp/width * pressProgress)
// Tab content fgTextures (icon+label alpha masks) for blue tint. Up to 8 tabs.
// Only opaque icon/label pixels become blue \u2014 the container glass stays natural.
uniform sampler2D uTabContentTex0;
uniform sampler2D uTabContentTex1;
uniform sampler2D uTabContentTex2;
uniform sampler2D uTabContentTex3;
uniform sampler2D uTabContentTex4;
uniform sampler2D uTabContentTex5;
uniform sampler2D uTabContentTex6;
uniform sampler2D uTabContentTex7;
uniform vec4  uTabContentRects[8];   // (centerX, centerY, halfW, halfH) per tab, canvas px (dpr-scaled)
uniform float uTabContentCount;      // number of valid tab rects (0..8)
uniform sampler2D uTabsGlassLayer;   // scene snapshot BEFORE tab-content (wallpaper+glass only, no text)
// --- SDF texture glass (faithful to SdfShader.kt) ---
uniform sampler2D uSdfTexSampler;   // clock_sdf texture (R=SDF, GB=normal, A=shape alpha)
uniform float uUseSdfTexture;       // 0 or 1
uniform vec2  uSdfTexSize;          // texture natural dimensions (px)
uniform float uSdfLightAngle;       // bevel light angle (degrees)
uniform float uEnterAlpha;          // global element alpha (enterProgress, 0..1)
// When 1.0, skip applyColorControls in the element shader (colorControls was
// already applied as a fullscreen pass BEFORE the 2-pass blur on the backdrop
// FBO, matching the original's colorControls\u2192blur\u2192lens order). Used by
// backdropFbo + useSeparableBlur elements (dialog card).
uniform float uSkipColorControls;   // 0 or 1
// --- Magnifier glass (faithful to MagnifierContent.kt) ---
uniform float uUseMagnifier;        // 0 or 1
uniform float uMagnifierZoom;       // zoom factor (1.5)
uniform float uMagnifierOffsetY;    // sample Y offset to cursor (80dp, device px)
// --- Sample wallpaper directly (bypass scene FBO) ---
// When 1.0, sampleBackdrop uses coverUv + uWallpaperSampler (clean wallpaper)
// instead of sceneUv + uBackdrop (scene FBO). Used by elements that sit over
// a scrim/dim (Dialog card, ControlCenter tiles) so the glass refracts the
// clean wallpaper instead of the alpha-decayed scene FBO. Faithful to the
// original where LayerBackdrop captures the wallpaper Image (alpha=1).
uniform float uSampleWallpaper;     // 0 or 1
// --- Scrim color (applied to the wallpaper BEFORE colorControls/blur/lens) ---
// Faithful to DialogContent.kt / ControlCenterContent.kt where the scrim
// (drawRect(dimColor)) is painted onto the wallpaper Image (via
// BackdropDemoScaffold's modifier = drawWithContent { drawContent(); drawRect(dimColor) }),
// so the LayerBackdrop captures wallpaper+scrim as one opaque layer.
// In the port, when uSampleWallpaper=1 (clean wallpaper), we apply the scrim
// here in the shader to replicate that composited backdrop. uScrimColor.a=0
// means no scrim. Applied as SrcOver: backdrop.rgb = scrim.rgb*scrim.a + backdrop.rgb*(1-scrim.a).
uniform vec4 uScrimColor;           // rgba 0..1; a=0 = no scrim
`
  );

  // shaders/element-utils.ts
  function generateGaussianDisc(tapCount) {
    const taps = [];
    if (tapCount <= 1) {
      taps.push({ x: 0, y: 0, w: 1 });
      return taps;
    }
    const goldenAngle = Math.PI * (3 - Math.sqrt(5));
    const maxRadius = 3;
    let totalW = 0;
    for (let i = 0; i < tapCount; i++) {
      const t = (i + 0.5) / tapCount;
      const r = maxRadius * Math.sqrt(t);
      const angle = i * goldenAngle;
      const x = r * Math.cos(angle);
      const y = r * Math.sin(angle);
      const dist2 = x * x + y * y;
      const w = Math.exp(-0.5 * dist2);
      taps.push({ x, y, w });
      totalW += w;
    }
    if (totalW > 0) {
      for (const t of taps) t.w /= totalW;
    }
    return taps;
  }
  function generateBlurGLSL(taps, sampler, uvVar, pxToUvExpr) {
    if (taps.length === 1) {
      return `    return texture2D(${sampler}, ${uvVar});
`;
    }
    let code = "";
    for (const t of taps) {
      const ox = t.x.toFixed(6);
      const oy = t.y.toFixed(6);
      const w = t.w.toFixed(8);
      code += `    sum += texture2D(${sampler}, ${uvVar} + vec2(${ox}, ${oy}) * ${pxToUvExpr}) * ${w};
`;
    }
    return code;
  }
  var DEFAULT_BLUR_TAPS = 16;
  function generateElementUtilsGLSL(tapCount = DEFAULT_BLUR_TAPS) {
    const taps = generateGaussianDisc(tapCount);
    const backdropBlurCode = generateBlurGLSL(taps, "uBackdrop", "uv", "pxToUv");
    const wallpaperBlurCode = generateBlurGLSL(taps, "uWallpaperSampler", "uv", "pxToUv");
    return (
      /* glsl */
      `
// Forward declarations \u2014 blendHue/rgb2hsv/hsv2rgb are defined later but used
// by sampleIndicatorBackdrop (which must come before sampleToggleBackdrop in
// the file for readability). GLSL ES 1.00 requires declaration before use.
vec3 rgb2hsv(vec3 c);
vec3 hsv2rgb(vec3 c);
vec3 blendHue(vec3 dst, vec3 src);

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

// SDF-texture glass sampling (faithful to SdfShader.kt).
// Samples the clock_sdf texture at element-local coords.
// Returns vec4(intensity, maskAlpha, normalX, normalY); zeroes if outside.
vec4 sampleSdfTexture(vec2 localPx) {
    vec2 uv = vec2(localPx.x / uOriginalSize.x,
                   localPx.y / uOriginalSize.y);
    if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) {
        return vec4(0.0);
    }
    vec4 v = texture2D(uSdfTexSampler, uv);
    float sd = v.r * 2.0 - 1.0;
    float mask = smoothstep(0.5, 1.0, v.a);
    if (mask <= 0.0) return vec4(0.0);
    if (mask < 1.0) sd = 0.0;
    vec2 normal = normalize(v.gb * 2.0 - 1.0);
    float intensity = circleMap(1.0 - min(1.0, -sd * 1.5));
    return vec4(intensity, mask, normal.x, normal.y);
}

// Convert a canvas-pixel coordinate (top-left origin) to scene-texture UV.
// The scene texture is the same size as the canvas, and is rendered with
// gl_FragCoord (bottom-left origin). So UV = (canvasPx.x / canvasW, 1 -
// canvasPx.y / canvasH). The Y flip happens here so the rest of the shader
// can work in top-left-origin canvas px.
vec2 sceneUv(vec2 canvasPx) {
    return vec2(canvasPx.x / uCanvasSize.x, 1.0 - canvasPx.y / uCanvasSize.y);
}

// Gaussian disc blur \u2014 ${tapCount} taps, dynamically generated in JS.
// Offsets are in units of radius (sigma = radius), scaled at runtime.
// radius < 0.5 falls back to single tap (no visible blur).
//
// When uSampleWallpaper > 0.5, samples the CLEAN wallpaper (uWallpaperSampler
// via coverUv) instead of the scene FBO (uBackdrop via sceneUv), AND applies
// the scrim (uScrimColor) to replicate the original's wallpaper+scrim composited
// LayerBackdrop. The scrim is applied INSIDE sampleBackdrop so EVERY sampling
// site \u2014 the initial backdrop sample, the refraction re-sample, and each
// chromatic-aberration channel \u2014 gets the same wallpaper+scrim composite.
// This fixes the "scrim not applied at edges" bug where the refraction band
// re-sampled the clean wallpaper (without scrim), making the edge brighter
// than the interior.
vec4 sampleBackdrop(vec2 canvasPx, float radius) {
    if (uSampleWallpaper > 0.5) {
        vec2 uv = coverUv(canvasPx);
        vec4 c;
        if (radius < 0.5) {
            c = texture2D(uWallpaperSampler, uv);
        } else {
            vec2 pxToUv = radius * canvasPxToUvScale();
            vec4 sum = vec4(0.0);
${wallpaperBlurCode}            c = sum;
        }
        // Apply scrim (SrcOver) so the backdrop = wallpaper+scrim, opaque.
        if (uScrimColor.a > 0.001) {
            c.rgb = uScrimColor.rgb * uScrimColor.a + c.rgb * (1.0 - uScrimColor.a);
            c.a = 1.0;
        }
        return c;
    }
    vec2 uv = sceneUv(canvasPx);
    if (radius < 0.5) {
        return texture2D(uBackdrop, uv);
    }
    vec2 pxToUv = radius / uCanvasSize;
    vec4 sum = vec4(0.0);
${backdropBlurCode}    return sum;
}

// Gaussian disc blur of the WALLPAPER (uWallpaperSampler via coverUv).
// Used by the SDF-texture glass path (LockScreen) \u2014 faithful to the original's
// blur(2dp) effect applied before the SDF shader.
vec4 sampleWallpaperBlurred(vec2 canvasPx, float radius) {
    vec2 uv = coverUv(canvasPx);
    if (radius < 0.5) {
        return texture2D(uWallpaperSampler, uv);
    }
    vec2 pxToUv = radius * canvasPxToUvScale();
    vec4 sum = vec4(0.0);
${wallpaperBlurCode}    return sum;
}

// --- Toggle knob CombinedBackdrop sampling (faithful to LiquidToggle.kt) ---
// The knob's backdrop is a CombinedBackdrop of:
//   1. Outer backdrop:
//      - LayerBackdrop (wallpaper) for t1 \u2192 sample uWallpaperSampler
//      - CanvasBackdrop (solid color) for t2 \u2192 use uSolidBackdropColor
//   2. Scaled trackBackdrop (track color rect, clipped to Capsule, scaled
//      by gooseLerp(2/3, 0.75, pressProgress) x gooseLerp(0, 0.75, pressProgress)
//      around the knob's center)
//
// This function samples the outer backdrop (wallpaper OR solid color) with blur,
// then composites the scaled track color on top using a rounded-rect SDF
// at the uTrackRect position (center + half-size + corner radius).
//
// The track color SDF is also blurred by approximating the blur as a
// smoothstep over uBlurRadius \u2014 this matches the original where the blur
// effect is applied to the CombinedBackdrop (outer + track color).
vec4 sampleToggleBackdrop(vec2 canvasPx, float radius) {
    // 1. Sample outer backdrop with blur.
    vec4 wp;
    if (uUseSolidBackdrop > 0.5) {
        // CanvasBackdrop case (t2): solid color fills the entire knob area.
        // Faithful to: rememberCanvasBackdrop { drawRect(backgroundColor) }
        // The drawRect fills the DrawScope (knob's bounds) with the color,
        // so every pixel of the knob's backdrop is the solid color.
        wp = uSolidBackdropColor;
    } else if (radius < 0.5) {
        // LayerBackdrop case (t1): sample wallpaper texture unscaled.
        // IMPORTANT: use coverUv (cover-fit) to match the wallpaper background
        // pass (WALLPAPER_FRAGMENT_SHADER). Using sceneUv (raw normalization)
        // here would sample the wrong texel when the wallpaper aspect ratio
        // differs from the canvas \u2014 causing the knob to see a shifted/misaligned
        // wallpaper that doesn't match what's displayed behind it.
        vec2 uv = coverUv(canvasPx);
        wp = texture2D(uWallpaperSampler, uv);
    } else {
        // LayerBackdrop case (t1) with blur: 9-tap poisson disc on wallpaper.
        // Use coverUv for the center sample, and convert the blur radius from
        // canvas px to UV-space using canvasPxToUvScale() (which accounts for
        // the cover-fit aspect ratio cropping).
        vec2 uv = coverUv(canvasPx);
        vec2 pxToUv = radius * canvasPxToUvScale();
        vec4 sum = vec4(0.0);
        float total = 0.0;
        sum += texture2D(uWallpaperSampler, uv) * 0.25; total += 0.25;
        sum += texture2D(uWallpaperSampler, uv + vec2( 1.000,  0.000) * pxToUv) * 0.12; total += 0.12;
        sum += texture2D(uWallpaperSampler, uv + vec2(-1.000,  0.000) * pxToUv) * 0.12; total += 0.12;
        sum += texture2D(uWallpaperSampler, uv + vec2( 0.000,  1.000) * pxToUv) * 0.12; total += 0.12;
        sum += texture2D(uWallpaperSampler, uv + vec2( 0.000, -1.000) * pxToUv) * 0.12; total += 0.12;
        sum += texture2D(uWallpaperSampler, uv + vec2( 0.707,  0.707) * pxToUv) * 0.0675; total += 0.0675;
        sum += texture2D(uWallpaperSampler, uv + vec2( 0.707, -0.707) * pxToUv) * 0.0675; total += 0.0675;
        sum += texture2D(uWallpaperSampler, uv + vec2(-0.707,  0.707) * pxToUv) * 0.0675; total += 0.0675;
        sum += texture2D(uWallpaperSampler, uv + vec2(-0.707, -0.707) * pxToUv) * 0.0675; total += 0.0675;
        wp = sum / total;
    }

    // 2. Composite scaled track color on top.
    // The track rect is centered at uTrackRect.xy with half-size uTrackRect.zw,
    // and corner radius uTrackCornerRadius. We compute the SDF of this
    // rounded rect at canvasPx, then apply a smoothstep for edge AA + blur.
    // If uTrackColor.a == 0.0 OR the track rect is degenerate (halfW or
    // halfH < 0.5px, which happens at rest when scaleY=0), skip compositing.
    // Faithful to original: scale(scaleX, 0) { drawRect() } draws nothing.
    if (uTrackColor.a > 0.001 && uTrackRect.z > 0.5 && uTrackRect.w > 0.5) {
        vec2 trackCenter = uTrackRect.xy;
        vec2 trackHalf = uTrackRect.zw;
        vec2 trackLocal = canvasPx - trackCenter;
        // sdRoundedRect expects centered coord (relative to center).
        // Use uniform corner radius = uTrackCornerRadius.
        float tr = uTrackCornerRadius;
        // Approximate the rounded-rect SDF (matches sdRoundedRect from SDF_GLSL).
        vec2 q = abs(trackLocal) - trackHalf + vec2(tr);
        float trackSd = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - tr;
        // Blur the edge by uBlurRadius (approximate Gaussian edge feather).
        // Inside (trackSd < -radius) \u2192 mask=1; outside (trackSd > radius) \u2192 mask=0.
        float mask = 1.0 - smoothstep(-radius, radius, trackSd);
        // Composite: srcOver (track color over outer backdrop).
        float a = mask * uTrackColor.a;
        wp.rgb = mix(wp.rgb, uTrackColor.rgb, a);
        wp.a = mix(wp.a, 1.0, a);
    }
    return wp;
}

// sampleIndicatorBackdrop \u2014 faithful to LiquidBottomTabs.kt indicator.
//
// Naming convention (used throughout the bottom-tabs code):
//   - \u5BB9\u5668 (Container)  = outer visible glass bar (64dp), Container Row in Kotlin
//   - \u6307\u793A\u5668 (Indicator) = selected sliding glass capsule (56dp), Indicator Box in Kotlin
//   - \u5185\u5C42\u80CC\u666F\u677F (Inner backdrop) = hidden 56dp glass captured by tabsBackdrop,
//     tinted blue by ColorFilter.tint(accentColor), sampled by the indicator
//   - \u6807\u7B7E\u5185\u5BB9 (Tab content) = icon + label inside each tab slot
//
// Original: indicator.drawBackdrop(backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop))
//   - backdrop (outer) = LayerBackdrop = wallpaper (sampled via coverUv)
//   - tabsBackdrop (inner) = hidden Row's 56dp glass, inset 4dp from the
//     indicator's draw area on all sides.
//
// Implementation (mirrors sampleToggleBackdrop):
//   1. Sample wallpaper (outer backdrop) with blur \u2014 same as toggle's outer.
//   2. Composite the scene FBO (uBackdrop = container glass + content)
//      inside an INSET capsule SDF (containerRect shrunk 4dp each side).
//      This is the "smaller background plate" refracted inside the indicator.
vec4 sampleIndicatorBackdrop(vec2 canvasPx, float radius) {
    // 1. Sample wallpaper (outer LayerBackdrop) via coverUv (cover-fit).
    vec4 wp;
    if (radius < 0.5) {
        vec2 uv = coverUv(canvasPx);
        wp = texture2D(uWallpaperSampler, uv);
    } else {
        vec2 uv = coverUv(canvasPx);
        vec2 pxToUv = radius * canvasPxToUvScale();
        vec4 sum = vec4(0.0);
        float total = 0.0;
        sum += texture2D(uWallpaperSampler, uv) * 0.25; total += 0.25;
        sum += texture2D(uWallpaperSampler, uv + vec2( 1.000,  0.000) * pxToUv) * 0.12; total += 0.12;
        sum += texture2D(uWallpaperSampler, uv + vec2(-1.000,  0.000) * pxToUv) * 0.12; total += 0.12;
        sum += texture2D(uWallpaperSampler, uv + vec2( 0.000,  1.000) * pxToUv) * 0.12; total += 0.12;
        sum += texture2D(uWallpaperSampler, uv + vec2( 0.000, -1.000) * pxToUv) * 0.12; total += 0.12;
        sum += texture2D(uWallpaperSampler, uv + vec2( 0.707,  0.707) * pxToUv) * 0.0675; total += 0.0675;
        sum += texture2D(uWallpaperSampler, uv + vec2( 0.707, -0.707) * pxToUv) * 0.0675; total += 0.0675;
        sum += texture2D(uWallpaperSampler, uv + vec2(-0.707,  0.707) * pxToUv) * 0.0675; total += 0.0675;
        sum += texture2D(uWallpaperSampler, uv + vec2(-0.707, -0.707) * pxToUv) * 0.0675; total += 0.0675;
        wp = sum / total;
    }

    // 2. \u5185\u5C42\u80CC\u666F\u677F (Inner backdrop) SDF \u2014 the hidden Row's 56dp glass capsule.
    //    Faithful to LiquidBottomTabs.kt: the hidden Row has NO layerBlock,
    //    so its glass does NOT scale with the container. Only panelOffset
    //    shifts it (translationX = panelOffset).
    vec2 capsuleHalf = max(uContainerRect.zw, vec2(0.0));
    float cr = max(uContainerCornerRadius, 0.0);
    // Center = rectCenter + panelOffset (NO container scale).
    vec2 scaledCenter = uContainerRect.xy + vec2(uIndicatorPanelOffset, 0.0);
    vec2 capsuleLocal = canvasPx - scaledCenter;
    vec2 cq = abs(capsuleLocal) - capsuleHalf + vec2(cr);
    float capsuleSd = length(max(cq, vec2(0.0))) + min(max(cq.x, cq.y), 0.0) - cr;
    float mask = 1.0 - smoothstep(-radius, radius, capsuleSd);

    // 3. Sample the GLASS LAYER FBO (wallpaper + container glass, NO tab text).
    //    This is a snapshot taken after the container glass is rendered but
    //    before tab-content is drawn \u2014 so it has no white/black text to bleed
    //    through. The blue tab text is drawn on top via fgTexture (step 4).
    vec2 sceneUv2 = sceneUv(canvasPx - vec2(uIndicatorPanelOffset, 0.0));
    vec4 scene = texture2D(uTabsGlassLayer, sceneUv2);

    // 4. Draw blue \u6807\u7B7E\u5185\u5BB9 (tab content: icons/labels) on top of the glass layer.
    //    Use each tab's fgTexture alpha as a hard mask (step) \u2014 pixels inside
    //    the icon/label shape become blue, everything else stays the glass
    //    layer's natural color. No white edges (hard replace, no mix).
    //    Faithful to LiquidBottomTabs.kt: the hidden Row's tab content gets
    //    LocalLiquidBottomTabScale = gooseLerp(1, 1.2, pressProgress) + panelOffset
    //    (NOT the container scale \u2014 the hidden Row is a sibling of the
    //    container, not a child, so the container layerBlock doesn't apply).
    float contentScale = 1.0 + 0.2 * uIndicatorPressProgress;
    float tabMask = 0.0;
    for (int i = 0; i < 8; i++) {
        if (float(i) >= uTabContentCount) break;
        vec4 r = uTabContentRects[i];
        if (r.z > 0.5 && r.w > 0.5) {
            // Tab content scales around its OWN center (not container center)
            // by contentScale, then shifts by panelOffset.
            vec2 tabCenter = r.xy + vec2(uIndicatorPanelOffset, 0.0);
            vec2 scaledHalf = r.zw * contentScale;
            vec2 localPx = canvasPx - (tabCenter - scaledHalf);
            vec2 uv = localPx / (scaledHalf * 2.0);
            if (all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)))) {
                float a = 0.0;
                if (i == 0) a = texture2D(uTabContentTex0, uv).a;
                else if (i == 1) a = texture2D(uTabContentTex1, uv).a;
                else if (i == 2) a = texture2D(uTabContentTex2, uv).a;
                else if (i == 3) a = texture2D(uTabContentTex3, uv).a;
                else if (i == 4) a = texture2D(uTabContentTex4, uv).a;
                else if (i == 5) a = texture2D(uTabContentTex5, uv).a;
                else if (i == 6) a = texture2D(uTabContentTex6, uv).a;
                else if (i == 7) a = texture2D(uTabContentTex7, uv).a;
                tabMask = max(tabMask, a);
            }
        }
    }
    // Use fgTexture alpha directly as the blue compositing factor. fgTexture
    // is LINEAR-filtered so its alpha has smooth AA edges \u2014 no smoothstep
    // threshold needed (which caused jaggies by hard-clipping the AA gradient).
    vec3 sceneColor = mix(scene.rgb, uIndicatorAccent.rgb, tabMask);

    // 5. Composite scene over wallpaper inside the inset capsule (SrcOver).
    float a = scene.a * mask;
    vec3 resultRgb = mix(wp.rgb, sceneColor, a);

    // 6. \u5185\u5C42\u80CC\u666F\u677F rim highlight \u2014 faithful to LiquidBottomTabs.kt hidden Row:
    //    highlight = { Highlight.Default.copy(alpha = progress) }
    //    The HighlightModifier draws a STROKE (width=0.5dp, strokeWidth=2px)
    //    blurred by 0.25dp, clipped inside the capsule, colored by the
    //    DefaultHighlightShaderString AGSL shader:
    //      float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    //      float2 normal = float2(cos(angle), sin(angle));
    //      float d = dot(grad, normal);
    //      float intensity = pow(abs(d), falloff);
    //      return color * intensity;   // color = White(1.0), alpha=1*progress
    //    with angle=45\xB0, falloff=1, gradRadius = min(radius*1.5, min(halfW, halfH)).
    //    The stroke's outward half (capsuleSd > 0) is clipped, leaving the inner
    //    half. Final contribution = White(1.0) * intensity * strokeMask * progress,
    //    added with Plus blend (additive).
    //    NOTE: this is the SAME as the \u6307\u793A\u5668's own rim highlight (step 2f in
    //    post-passes) \u2014 both use Highlight.Default. The only difference is the
    //    SDF: here it's the \u5185\u5C42\u80CC\u666F\u677F capsule (inset 4dp), there it's the
    //    \u6307\u793A\u5668's own capsule. The shader math is identical.
    float highlightAlpha = uIndicatorPressProgress;
    if (highlightAlpha > 0.001) {
        // SDF gradient + Default highlight intensity (angle=45\xB0, falloff=1).
        float indRadius = max(cr, 0.0);
        float indHalfMin = min(capsuleHalf.x, capsuleHalf.y);
        float gradRadius = min(indRadius * 1.5, indHalfMin);
        vec2 grad = gradSdRoundedRect(capsuleLocal, capsuleHalf, gradRadius);
        vec2 normal = vec2(0.70710678, 0.70710678); // cos(45\xB0), sin(45\xB0)
        float d = dot(grad, normal);
        float intensity = pow(abs(d), 1.0);

        // Stroke mask \u2014 faithful to HighlightModifier.kt + BlurMaskFilter:
        //   paint.style = Stroke
        //   paint.strokeWidth = ceil(0.5dp * dpr) * 2  (device px)
        //   paint.blur(0.25dp * dpr)  \u2192 BlurMaskFilter(NORMAL, sigma=0.25*dpr)
        //   canvas.clipOutline \u2192 clip to INSIDE (capsuleSd <= 0)
        // In Skia/Android, BlurMaskFilter's radius param IS the Gaussian sigma.
        // capsuleSd is in device px (uContainerRect is dpr-scaled), so sigma
        // and strokeHalf must also be in device px.
        // Implementation: hard-edge stroke band convolved with Gaussian kernel
        // via adaptive SDF sampling (same approach as highlight.ts). Fixed 1px
        // tap spacing \u2014 tap count scales with sigma (2*ceil(3\u03C3)+1, max 64).
        float strokeHalf = ceil(0.5 * uDpr) * 2.0 * 0.5;  // = ceil(0.5*dpr)
        float sigma2 = max(0.25 * uDpr, 0.1);  // blurRadius = 0.25dp, sigma = blurRadius*dpr
        float tapSpacing2 = 1.0; // fixed 1px \u2014 tap count scales with sigma
        float threeSigma2 = sigma2 * 3.0;
        float strokeMask = 0.0;
        float wSum2 = 0.0;
        for (int j = -32; j <= 32; j++) {
            float offset = float(j) * tapSpacing2;
            if (abs(offset) <= threeSigma2) {
                float sampleSd = capsuleSd - offset;
                float hard = (abs(sampleSd) < strokeHalf) ? 1.0 : 0.0;
                float w = exp(-0.5 * (offset * offset) / (sigma2 * sigma2));
                strokeMask += hard * w;
                wSum2 += w;
            }
        }
        strokeMask /= wSum2;
        strokeMask *= 0.5;  // clip halves the symmetric stroke at the edge
        // Clip to inside (outside the \u5185\u5C42\u80CC\u666F\u677F \u2192 no highlight)
        strokeMask = (capsuleSd > 0.0) ? 0.0 : strokeMask;

        // White(1.0) * intensity * strokeMask * progress, Plus blend (additive).
        // (color.copy(alpha=1) * highlightLayer.alpha=progress \u2014 the 0.5 alpha
        // in HighlightStyle.Default.color is NOT used; the AGSL shader uses
        // color.copy(alpha=1f) and the layer alpha is highlight.alpha=progress.)
        resultRgb += vec3(1.0) * intensity * strokeMask * highlightAlpha;
    }

    return vec4(resultRgb, 1.0);
}

// Magnifier backdrop sampling \u2014 faithful to MagnifierContent.kt's
// onDrawBackdrop: withTransform({ scale(1.5); translate(top=-80dp) }, drawBackdrop).
// Zoom around the magnifier center, then offset Y toward cursor.
vec4 sampleMagnifier(vec2 canvasPx, float radius) {
    vec2 magCenter = uElementOffset + uElementSize * 0.5;
    vec2 zoomedCoord = magCenter + (canvasPx - magCenter) / uMagnifierZoom;
    vec2 cursorCoord = vec2(zoomedCoord.x, zoomedCoord.y + uMagnifierOffsetY);
    return sampleBackdrop(cursorCoord, radius);
}

// colorControls \u2014 exact port of ColorFilter.kt colorControlsColorFilter.
// saturation 1.5, brightness 0, contrast 1 -> pure saturation boost.
vec3 applyColorControls(vec3 c, float brightness, float contrast, float saturation) {
    float invSat = 1.0 - saturation;
    float r = 0.213 * invSat;
    float g = 0.715 * invSat;
    float b = 0.072 * invSat;
    float t = (0.5 - contrast * 0.5 + brightness) * 255.0;
    float cs = contrast * saturation;
    float cr = contrast * r;
    float cg = contrast * g;
    float cb = contrast * b;
    vec3 outc;
    outc.r = (cr + cs) * c.r + cg * c.g + cb * c.b + t / 255.0;
    outc.g = cr * c.r + (cg + cs) * c.g + cb * c.b + t / 255.0;
    outc.b = cr * c.r + cg * c.g + (cb + cs) * c.b + t / 255.0;
    return outc;
}

// --- HSV conversion + BlendMode.Hue ---------------------------
// Faithful port of Skia's BlendMode.Hue (non-separable blend).
// Hue blend: result takes hue from src, saturation+value from dst.
// Used by drawRect(tint, BlendMode.Hue) in onDrawSurface.
vec3 rgb2hsv(vec3 c) {
    float maxC = max(c.r, max(c.g, c.b));
    float minC = min(c.r, min(c.g, c.b));
    float delta = maxC - minC;
    float v = maxC;
    float s = maxC < 1e-6 ? 0.0 : delta / maxC;
    float h = 0.0;
    if (delta > 1e-6) {
        if (maxC == c.r) {
            h = mod((c.g - c.b) / delta, 6.0);
        } else if (maxC == c.g) {
            h = (c.b - c.r) / delta + 2.0;
        } else {
            h = (c.r - c.g) / delta + 4.0;
        }
        h *= 60.0;
        if (h < 0.0) h += 360.0;
    }
    return vec3(h / 360.0, s, v);
}

vec3 hsv2rgb(vec3 c) {
    float h = c.x * 6.0;
    float s = c.y;
    float v = c.z;
    float i = floor(h);
    float f = h - i;
    float p = v * (1.0 - s);
    float q = v * (1.0 - s * f);
    float t = v * (1.0 - s * (1.0 - f));
    i = mod(i, 6.0);
    if (i < 1.0) return vec3(v, t, p);
    if (i < 2.0) return vec3(q, v, p);
    if (i < 3.0) return vec3(p, v, t);
    if (i < 4.0) return vec3(p, q, v);
    if (i < 5.0) return vec3(t, p, v);
    return vec3(v, p, q);
}

// BlendMode.Hue: take hue from src, sat+val from dst.
vec3 blendHue(vec3 dst, vec3 src) {
    vec3 dh = rgb2hsv(dst);
    vec3 sh = rgb2hsv(src);
    return hsv2rgb(vec3(sh.x, dh.y, dh.z));
}
`
    );
  }

  // shaders/element.ts
  function generateElementFragmentShader(tapCount = DEFAULT_BLUR_TAPS) {
    const utilsGlsl = generateElementUtilsGLSL(tapCount);
    return (
      /* glsl */
      `
// Enable derivative functions (fwidth) for adaptive edge anti-aliasing.
// No-op on WebGL2; required on WebGL1 via OES_standard_derivatives.
#extension GL_OES_standard_derivatives : enable

#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

${ELEMENT_UNIFORMS_GLSL}

${SDF_GLSL}

${COVER_GLSL}

${utilsGlsl}

void main() {
    // gl_FragCoord origin is bottom-left in WebGL; flip to top-left.
    vec2 screenCoord = vec2(gl_FragCoord.x, uCanvasSize.y - gl_FragCoord.y);
    // Content scale (non-uniform): when < 1.0, compress the backdrop UV toward
    // the element center. Faithful to LiquidToggle.kt / LiquidSlider.kt:
    //   scale(scaleX, scaleY) { drawBackdrop() }
    // At rest (progress=0), Y scale = 0 \u2192 degenerate (single horizontal line),
    // but the white overlay hides it. When pressed, scales to full.
    vec2 contentScale = vec2(uContentScaleX, uContentScaleY);
    vec2 sampleCoord = screenCoord;
    if (uContentScaleX < 0.999 || uContentScaleY < 0.999) {
        vec2 elementCenter = uElementOffset + uElementSize * 0.5;
        sampleCoord = elementCenter + (screenCoord - elementCenter) * contentScale;
    }

    // --- ORIGINAL-SPACE SDF (faithful to graphicsLayer { scaleX, scaleY }) ---
    // The original applies the refraction shader at the ORIGINAL element size,
    // THEN scales the entire rendered layer by (scaleX, scaleY). To replicate
    // this in a single-pass shader, we:
    //   1. Compute the centered coord in SCREEN space (relative to element center)
    //   2. Divide by uLayerScale to map back to ORIGINAL space
    //   3. Compute SDF/refraction in ORIGINAL space (shape is correct, not stretched)
    //   4. Map the refraction offset back to SCREEN space for backdrop sampling
    //      (offset_screen = offset_orig * uLayerScale)
    //
    // elementCenter is the SAME for scaled and original rects (scaling is around
    // the center), so uElementOffset + uElementSize*0.5 gives the correct center.
    vec2 elementCenter = uElementOffset + uElementSize * 0.5;
    vec2 centeredScreen = screenCoord - elementCenter;
    // Map to original space (guard against divide-by-zero).
    vec2 layerScale = max(uLayerScale, vec2(1e-4));
    vec2 centeredOrig = centeredScreen / layerScale;
    // Apply element rotation (graphicsLayer rotationZ). Un-rotate the sample
    // coord into the element's local space so the SDF shape appears rotated
    // by +rotation. The layer is rotated AFTER shading, so we shade in local
    // (un-rotated) space. Refraction offsets computed in local space are
    // rotated BACK to screen space (by +rotation) before sampling the backdrop.
    float rot = uElementRotation;
    vec2 centeredOrigRot = rotateBy(centeredOrig, -rot);

    vec2 origHalfSize = uOriginalSize * 0.5;
    float origRadius = uOriginalCornerRadius;

    // --- SDF-texture glass path (faithful to SdfShader.kt) ---
    if (uUseSdfTexture > 0.5) {
        vec2 localPx = centeredOrigRot + uOriginalSize * 0.5;
        vec4 sdfData = sampleSdfTexture(localPx);
        if (sdfData.y <= 0.0) discard;
        float intensity = sdfData.x;
        float sdfMask = sdfData.y;
        vec2 normal = sdfData.zw;

        // Sample the WALLPAPER directly (not the scene FBO) \u2014 faithful to
        // LockScreenContent.kt's drawPlainBackdrop which uses the LayerBackdrop
        // (raw wallpaper, before the dark scrim is drawn).
        // The original applies blur(2dp) BEFORE the SDF shader (in the effects
        // block), so 'content' (the SDF shader's input) is already blurred.
        // We replicate by sampling the wallpaper with a 9-tap poisson blur at
        // the refracted coordinate.
        vec2 refractedOffsetOrig = intensity * uRefractionHeight * normal;
        vec2 refractedOffsetScreen = refractedOffsetOrig * layerScale;
        vec2 refractedScreen = screenCoord - refractedOffsetScreen;

        // Faithful to SdfShader.kt: color = content.eval(refractedCoord) * v.a
        // The content is the wallpaper after colorControls + blur(2dp).
        // FAITHFUL ORDERING: the original's onDrawBackdrop draws the wallpaper
        // AND drawRect(White 0.25) into the same buffer, THEN applies the
        // RenderEffect chain (colorControls, blur, SDF shader). So the white
        // overlay is PART of the SDF shader content input, and colorControls
        // is applied to the COMBINED (wallpaper + white) buffer.
        // We replicate: mix white into raw wallpaper FIRST, then apply
        // colorControls \u2014 so colorControls darkens the white too (matching
        // the original where contrast=0.75, brightness=-0.1 dims the white).
        vec4 content = sampleWallpaperBlurred(refractedScreen, uBlurRadius);
        vec3 rawContent = content.rgb;
        // Mix in white overlay (White 0.25 SrcOver) on RAW wallpaper first.
        if (uSurfaceColor.a > 0.001) {
            rawContent = uSurfaceColor.rgb * uSurfaceColor.a + rawContent * (1.0 - uSurfaceColor.a);
        }
        // THEN apply colorControls to the combined buffer.
        vec3 contentColor = applyColorControls(rawContent, uBrightness, uContrast, uSaturation);
        // Multiply by sdfMask (v.a) \u2014 faithful to content * v.a.
        vec3 color = contentColor * sdfMask;

        // Bevel lighting
        float angleRad = uSdfLightAngle * 3.1415926 / 180.0;
        vec2 lightDir = vec2(cos(angleRad), sin(angleRad));
        float bevel1 = clamp(dot(normal, lightDir), 0.0, 1.0);
        color.rgb *= 1.0 + 0.5 * intensity * bevel1;
        float bevel2 = clamp(dot(normal, -lightDir), 0.0, 1.0);
        color.rgb *= 1.0 + 0.5 * bevel2 * min(1.0, smoothstep(1.0, 0.0, abs(intensity - 0.25) * 6.0));

        gl_FragColor = vec4(color, sdfMask * uEnterAlpha);
        return;
    }

    // SDF for refraction/highlight \u2014 always analytic sdRoundedRect.
    float sd = sdShape(centeredOrigRot, origHalfSize, origRadius);
    // Clip + edgeAA: alpha mask (browser-native AA) when capsule enabled.
    float edgeAlpha;
    if (uUseContinuousSdf > 0.5) {
        float mask = sampleClipMask(centeredOrigRot, origHalfSize, origRadius);
        if (mask < 0.01) discard;
        edgeAlpha = mask;
    } else {
        if (sd > 1.5) discard;
        // \u6297\u952F\u9F7F\uFF1A\u8FC7\u6E21\u5E26\u5BBD\u5EA6\u81EA\u9002\u5E94\u5C4F\u5E55\u50CF\u7D20\uFF08\u7EA6 3 \u7269\u7406\u50CF\u7D20\uFF09\uFF0C\u534A\u900F\u660E\u73BB\u7483\u4E5F\u80FD\u770B\u6E05\u7FBD\u5316
        float aaWidth = max(fwidth(sd), 1.0) * 1.5;
        edgeAlpha = 1.0 - smoothstep(-aaWidth, aaWidth, sd);
    }

    // --- 1. Backdrop sample (before refraction) -------------------
    // Use sampleCoord (content-scaled) so the backdrop shrinks inward when
    // uContentScaleX/Y < 1.0 (toggle/slider knob press effect).
    vec4 backdrop;
    if (uIndicatorBackdrop > 0.5) {
        backdrop = sampleIndicatorBackdrop(screenCoord, uBlurRadius);
    } else if (uUseToggleBackdrop > 0.5) {
        backdrop = sampleToggleBackdrop(screenCoord, uBlurRadius);
    } else if (uUseMagnifier > 0.5) {
        backdrop = sampleMagnifier(screenCoord, uBlurRadius);
    } else {
        backdrop = sampleBackdrop(sampleCoord, uBlurRadius);
    }
    // colorControls: for backdropFbo+useSeparableBlur elements, cc was already
    // applied as a fullscreen pass BEFORE the 2-pass blur (uSkipColorControls=1),
    // matching the original's colorControls\u2192blur order. Skip here to avoid
    // double-applying. For inline-blur elements, apply here.
    vec3 color = (uSkipColorControls > 0.5) ? backdrop.rgb : applyColorControls(backdrop.rgb, uBrightness, uContrast, uSaturation);
    // Magnifier glass is always OPAQUE \u2014 faithful to the original which
    // samples rememberCombinedBackdrop (wallpaper + content + cursor all
    // composited onto the opaque wallpaper). The port's scene texture may
    // carry partial alpha (e.g. card 0.9), which would make the glass
    // translucent. Force alpha=1 for magnifier.
    float alpha = (uUseMagnifier > 0.5) ? 1.0 : backdrop.a;

    // --- 2. Lens refraction (SDF + circleMap) ---------------------
    // Faithful port of RoundedRectRefractionWithDispersionShaderString.
    // SDF/grad computed in ORIGINAL space; uRefractionHeight/Amount are in
    // original px (NOT scaled by layerScale \u2014 the original AGSL shader receives
    // the original size and the graphicsLayer scales the OUTPUT, not the params).
    // Early-out: if we're deeper than refractionHeight from the edge,
    // skip refraction entirely (the lens doesn't reach here).
    if (uRefractionHeight > 0.5 && (-sd) < uRefractionHeight) {
        float sdClamped = min(sd, 0.0);
        float d = circleMap(1.0 - (-sdClamped) / uRefractionHeight) * uRefractionAmount;

        float gradRadius = min(origRadius * 1.5, min(origHalfSize.x, origHalfSize.y));
        vec2 grad = gradSdRoundedRect(centeredOrigRot, origHalfSize, gradRadius);
        // AGSL: normalize(grad + depthEffect * normalize(centeredCoord))
        vec2 depthVec = vec2(0.0);
        if (uDepthEffect > 0.5) {
            float dirLen = length(centeredOrigRot);
            if (dirLen > 1e-6) depthVec = centeredOrigRot / dirLen;
        }
        vec2 gradSum = grad + uDepthEffect * depthVec;
        float gradLen = length(gradSum);
        if (gradLen > 1e-6) grad = gradSum / gradLen;

        // Refraction offset in ORIGINAL space, then map to SCREEN space.
        //   offset_orig = d * grad          (original px)
        //   offset_screen = offset_orig * layerScale  (screen px, for sampling)
        // Faithful to: AGSL computes offset in original space, then graphicsLayer
        // scales the rendered output \u2014 so a pixel at original position p samples
        // the backdrop at p + offset_orig, and the result appears at screen
        // position center + p*layerScale. The backdrop sample position in screen
        // space is therefore center + (p + offset_orig)*layerScale
        // = screenCoord + offset_orig * layerScale.
        vec2 refractedOffsetOrig = d * grad;
        // Rotate the local-space offset BACK to screen space (by +rotation),
        // then scale by layerScale. Without the rotation, refraction points
        // in the wrong direction when the element is rotated.
        vec2 refractedOffsetScreen = rotateBy(refractedOffsetOrig, rot) * layerScale;
        vec2 refractedScreen = screenCoord + refractedOffsetScreen;
        vec2 refractedSampleCoord = refractedScreen;
        if (uIndicatorBackdrop < 0.5 && uUseToggleBackdrop < 0.5 &&
            (uContentScaleX < 0.999 || uContentScaleY < 0.999)) {
            refractedSampleCoord = elementCenter + (refractedScreen - elementCenter) * contentScale;
        }

        if (uChromaticAberration > 0.5) {
            // Faithful 7-path chromatic dispersion (ROYGBV + purple).
            // Original AGSL: dispersionIntensity = chromaticAberration * (cx*cy)/(hx*hy)
            //                dispersedCoord = d * grad * dispersionIntensity
            // 7 samples at dispersedCoord * {1, 2/3, 1/3, 0, -1/3, -2/3, -1}
            // with weighted channel accumulation.
            float dispersionIntensity = 1.0 * ((centeredOrigRot.x * centeredOrigRot.y) / (origHalfSize.x * origHalfSize.y));
            vec2 dispersedOffsetOrig = refractedOffsetOrig * dispersionIntensity;
            vec2 dispersedOffsetScreen = rotateBy(dispersedOffsetOrig, rot) * layerScale;

            // Sample helper \u2014 pick the right backdrop sampler.
            #define SAMPLE_DISPERSED(offset)                 (uIndicatorBackdrop > 0.5 ? sampleIndicatorBackdrop(refractedScreen + (offset), uBlurRadius) :                  uUseToggleBackdrop > 0.5 ? sampleToggleBackdrop(refractedScreen + (offset), uBlurRadius) :                  uUseMagnifier > 0.5 ? sampleMagnifier(refractedScreen + (offset), uBlurRadius) :                  sampleBackdrop(refractedSampleCoord + (offset), uBlurRadius))

            vec4 sRed    = SAMPLE_DISPERSED(+dispersedOffsetScreen);
            vec4 sOrange = SAMPLE_DISPERSED(+dispersedOffsetScreen * (2.0 / 3.0));
            vec4 sYellow = SAMPLE_DISPERSED(+dispersedOffsetScreen * (1.0 / 3.0));
            vec4 sGreen  = SAMPLE_DISPERSED(vec2(0.0));
            vec4 sCyan   = SAMPLE_DISPERSED(-dispersedOffsetScreen * (1.0 / 3.0));
            vec4 sBlue   = SAMPLE_DISPERSED(-dispersedOffsetScreen * (2.0 / 3.0));
            vec4 sPurple = SAMPLE_DISPERSED(-dispersedOffsetScreen);

            #undef SAMPLE_DISPERSED

            // Faithful channel weighting from the original AGSL shader.
            vec3 dispColor = vec3(0.0);
            float dispAlpha = 0.0;
            // red
            dispColor.r += sRed.r / 3.5;
            dispAlpha  += sRed.a / 7.0;
            // orange
            dispColor.r += sOrange.r / 3.5;
            dispColor.g += sOrange.g / 7.0;
            dispAlpha  += sOrange.a / 7.0;
            // yellow
            dispColor.r += sYellow.r / 3.5;
            dispColor.g += sYellow.g / 3.5;
            dispAlpha  += sYellow.a / 7.0;
            // green
            dispColor.g += sGreen.g / 3.5;
            dispAlpha  += sGreen.a / 7.0;
            // cyan
            dispColor.g += sCyan.g / 3.5;
            dispColor.b += sCyan.b / 3.0;
            dispAlpha  += sCyan.a / 7.0;
            // blue
            dispColor.b += sBlue.b / 3.0;
            dispAlpha  += sBlue.a / 7.0;
            // purple
            dispColor.r += sPurple.r / 7.0;
            dispColor.b += sPurple.b / 3.0;
            dispAlpha  += sPurple.a / 7.0;

            color = (uSkipColorControls > 0.5) ? dispColor : applyColorControls(dispColor, uBrightness, uContrast, uSaturation);
            // Magnifier chromatic aberration also forces opaque.
            alpha = (uUseMagnifier > 0.5) ? 1.0 : dispAlpha;
        } else {
            vec4 refracted;
            if (uIndicatorBackdrop > 0.5) {
                refracted = sampleIndicatorBackdrop(refractedScreen, uBlurRadius);
            } else if (uUseToggleBackdrop > 0.5) {
                refracted = sampleToggleBackdrop(refractedScreen, uBlurRadius);
            } else if (uUseMagnifier > 0.5) {
                refracted = sampleMagnifier(refractedScreen, uBlurRadius);
            } else {
                refracted = sampleBackdrop(refractedSampleCoord, uBlurRadius);
            }
            color = (uSkipColorControls > 0.5) ? refracted.rgb : applyColorControls(refracted.rgb, uBrightness, uContrast, uSaturation);
            // Magnifier refraction also forces opaque (see backdrop sample above).
            alpha = (uUseMagnifier > 0.5) ? 1.0 : refracted.a;
        }
    }

    // --- 3. onDrawSurface: tint (BlendMode.Hue + 0.75 alpha) -----
    // Faithful port of LiquidButton.kt onDrawSurface:
    //   drawRect(tint, blendMode = BlendMode.Hue)
    //   drawRect(tint.copy(alpha = 0.75f))
    // First pass: replace backdrop hue with tint hue (Hue blend, alpha = tint.a).
    // Second pass: overlay tint color at 0.75*alpha (SrcOver blend).
    if (uTintColor.a > 0.001) {
        vec3 hueBlended = blendHue(color, uTintColor.rgb);
        color = mix(color, hueBlended, uTintColor.a);
        color = mix(color, uTintColor.rgb, 0.75 * uTintColor.a);
    }

    // --- 4. onDrawSurface: surfaceColor (drawRect(surfaceColor)) --
    if (uSurfaceColor.a > 0.001) {
        color = mix(color, uSurfaceColor.rgb, uSurfaceColor.a);
    }

    // --- 5. Highlight (edge specular) -----------------------------
    // NOTE: The rim highlight is drawn as a SEPARATE pass (see
    // RIM_HIGHLIGHT_FRAGMENT_SHADER) with true Plus/SrcOver blend,
    // matching the original HighlightModifier.kt which records a separate
    // graphics layer. Doing it inline here would dim the highlight via the
    // element's edge AA, which is wrong \u2014 the highlight layer is composited
    // on top with its own blend mode.

    // --- 6. Inner shadow (REMOVED) --------------------------------
    // Moved to Step 2b post-pass: Canvas2D blurred ring mask + separate
    // INNER_SHADOW_MASK_COMPOSITE pass (true Gaussian blur, faithful to
    // InnerShadowModifier.kt's BlurEffect). The old inline SDF approximation
    // was deleted.

    // --- 7. Edge anti-aliasing -----------------------------------
    // edgeAlpha was computed earlier (mask mode: direct coverage, analytic: smoothstep).
    gl_FragColor = vec4(color, alpha * edgeAlpha * uEnterAlpha);
}
`
    );
  }
  var ELEMENT_FRAGMENT_SHADER = generateElementFragmentShader(DEFAULT_BLUR_TAPS);

  // shaders/shadow.ts
  var SHADOW_FRAGMENT_SHADER = (
    /* glsl */
    `
precision highp float;

uniform vec2  uCanvasSize;
uniform vec2  uElementOffset;   // SCALED rect top-left (where the quad is drawn)
uniform vec2  uElementSize;     // SCALED size (includes graphicsLayer scale)
uniform vec4  uCornerRadii;     // SCALED corner radii
uniform float uShadowRadius;    // ORIGINAL px (NOT scaled \u2014 faithful to BlurMaskFilter at original size)
uniform vec2  uShadowOffset;    // ORIGINAL px (offsetX, offsetY; +Y = downward)
uniform vec4  uShadowColor;     // rgba
// --- ORIGINAL-SPACE SDF (faithful to graphicsLayer { scaleX, scaleY }) ---
// Same approach as the element shader: compute the shadow SDF in ORIGINAL
// space (shape is a correct capsule, not stretched), then the graphicsLayer
// scales the entire shadow layer by (scaleX, scaleY). The shadow offset is
// in ORIGINAL px; we multiply by uLayerScale to map it to screen space for
// the SDF evaluation (offset_screen = offset_orig * layerScale). The shadow
// radius (blur sigma) stays in ORIGINAL px because the Gaussian falloff is
// computed in original space \u2014 the graphicsLayer then stretches the blurred
// result, which is the faithful behavior (BlurMaskFilter blurs at original
// resolution, then graphicsLayer scales the blurred pixels).
uniform vec2  uOriginalSize;        // element size in px (ORIGINAL, unscaled)
uniform float uOriginalCornerRadius; // corner radius in px (ORIGINAL, unscaled)
uniform vec2  uLayerScale;          // (scaleX, scaleY) from graphicsLayer
uniform float uElementRotation;     // rotation in radians (graphicsLayer rotationZ)

${SDF_GLSL}

void main() {
    // Flip gl_FragCoord (bottom-left origin) to top-left origin, so +Y
    // points downward \u2014 matching CSS convention.
    vec2 screenCoord = vec2(gl_FragCoord.x, uCanvasSize.y - gl_FragCoord.y);
    // elementCenter is the SAME for scaled and original rects (scaling is
    // around the center), so uElementOffset + uElementSize*0.5 gives the
    // correct center.
    vec2 elementCenter = uElementOffset + uElementSize * 0.5;
    vec2 centeredScreen = screenCoord - elementCenter;
    // Map to ORIGINAL space (guard against divide-by-zero).
    vec2 layerScale = max(uLayerScale, vec2(1e-4));
    vec2 centeredOrig = centeredScreen / layerScale;
    // Un-rotate into local space so the shadow shape rotates with the element.
    // Also rotate the shadow offset into local space so it stays consistent.
    vec2 centeredOrigRot = rotateBy(centeredOrig, -uElementRotation);
    vec2 shadowOffsetRot = rotateBy(uShadowOffset, -uElementRotation);

    vec2 origHalfSize = uOriginalSize * 0.5;
    float origRadius = uOriginalCornerRadius;

    // Shadow offset: defined in ORIGINAL px, applied in screen space.
    // The original draws the shadow at original size with this offset, then
    // graphicsLayer scales the whole layer \u2014 so the offset effectively
    // becomes offset_orig * layerScale in screen space. We map it back to
    // original space for the SDF: offset_orig = offset_screen / layerScale,
    // which cancels \u2014 so we use uShadowOffset directly in original space.
    vec2 shadowCenteredOrig = centeredOrigRot - shadowOffsetRot;
    float sd = sdShape(shadowCenteredOrig, origHalfSize, origRadius);
    // SDF of the element itself (not offset) \u2014 used to mask the shadow
    // inside the element so it doesn't bleed through the AA edge.
    float elementSd = sdShape(centeredOrigRot, origHalfSize, origRadius);

    // Shadow intensity: Gaussian falloff from the shadow shape's edge.
    // uShadowRadius is in ORIGINAL px (faithful to BlurMaskFilter at original
    // size). sigma = radius/3 matches the BlurMaskFilter spread.
    float sigma = max(uShadowRadius / 3.0, 1.0);
    float shadow = 0.5 * exp(-sd * sd / (2.0 * sigma * sigma));
    // Mask out the shadow inside the element (the element covers it).
    shadow *= smoothstep(-1.0, 1.0, elementSd);

    gl_FragColor = vec4(uShadowColor.rgb, uShadowColor.a * shadow);
}
`
  );
  var INNER_SHADOW_FRAGMENT_SHADER = (
    /* glsl */
    `
precision highp float;

uniform vec2  uCanvasSize;
uniform vec2  uElementOffset;
uniform vec2  uElementSize;
uniform vec4  uCornerRadii;
uniform float uInnerShadowRadius;
uniform float uInnerShadowAlpha;
uniform vec2  uInnerShadowOffset;

${SDF_GLSL}

void main() {
    vec2 screenCoord = vec2(gl_FragCoord.x, uCanvasSize.y - gl_FragCoord.y);
    vec2 localCoord = screenCoord - uElementOffset;
    vec2 halfSize = uElementSize * 0.5;
    vec2 centeredCoord = localCoord - halfSize;

    float radius = radiusAt(centeredCoord, uCornerRadii);
    float sd = sdShape(centeredCoord, halfSize, radius);
    if (sd > 0.5) discard;

    vec2 innerCentered = centeredCoord - uInnerShadowOffset;
    float innerSd = sdShape(innerCentered, halfSize, radius);
    float band = smoothstep(uInnerShadowRadius, 0.0, innerSd);
    band *= step(0.0, innerSd);
    gl_FragColor = vec4(0.0, 0.0, 0.0, band * uInnerShadowAlpha * 0.5);
}
`
  );

  // shaders/highlight.ts
  var HIGHLIGHT_FRAGMENT_SHADER = (
    /* glsl */
    `
#extension GL_OES_standard_derivatives : enable
precision highp float;

uniform vec2  uCanvasSize;
uniform vec2  uOffset;       // element top-left in canvas px (top-left origin) \u2014 SCALED rect
uniform vec2  uSize;         // element size in canvas px \u2014 SCALED
uniform vec4  uCornerRadii;  // capsule radii (topLeft, topRight, bottomRight, bottomLeft) in px \u2014 SCALED
uniform vec4  uColor;        // rgba; usually white * (alpha = 0.15 * progress)
uniform float uRadius;       // glow radius in canvas px (= minDim * 1.5, SCALED space)
uniform vec2  uPosition;     // finger position in element-local px (top-left origin, SCALED space)
// --- ORIGINAL-SPACE SDF clip (faithful to graphicsLayer { scaleX, scaleY }) ---
// The press glow (InteractiveHighlight) is drawn INSIDE the graphicsLayer, so
// it is clipped to the ORIGINAL capsule shape, then scaled with the layer.
// The glow position + radius are in SCALED space (they track the finger in
// screen px), but the clip SDF is in original space so the capsule clip stays
// correct when the button is stretched.
uniform vec2  uOriginalSize;
uniform float uOriginalCornerRadius;
uniform vec2  uLayerScale;
uniform float uElementRotation;

${SDF_GLSL}

void main() {
    vec2 screenCoord = vec2(gl_FragCoord.x, uCanvasSize.y - gl_FragCoord.y);
    vec2 localCoord = screenCoord - uOffset;

    // --- Capsule clip in ORIGINAL space (faithful to graphicsLayer clip) ---
    vec2 elementCenter = uOffset + uSize * 0.5;
    vec2 centeredScreen = screenCoord - elementCenter;
    vec2 layerScale = max(uLayerScale, vec2(1e-4));
    vec2 centeredOrig = centeredScreen / layerScale;
    vec2 origHalfSize = uOriginalSize * 0.5;
    float sd = sdShape(rotateBy(centeredOrig, -uElementRotation), origHalfSize, uOriginalCornerRadius);
    if (sd > 0.5) discard;
    float clipAlpha = 1.0 - smoothstep(-0.5, 0.5, sd);

    // Faithful AGSL port: smoothstep(radius, radius*0.5, dist) means
    // intensity = 1 at dist <= radius*0.5, fading to 0 at dist >= radius.
    // dist + uPosition are in SCALED local space (finger tracks screen px).
    float dist = distance(localCoord, uPosition);
    float intensity = smoothstep(uRadius, uRadius * 0.5, dist);

    // Premultiplied Plus-blend contribution. Renderer uses blendFunc(ONE, ONE)
    // so result.rgb = contribution + dst.rgb (clamped to 1).
    vec3 contribution = uColor.rgb * uColor.a * intensity * clipAlpha;
    gl_FragColor = vec4(contribution, 1.0);
}
`
  );
  var TINT_FRAGMENT_SHADER = (
    /* glsl */
    `
#extension GL_OES_standard_derivatives : enable
precision highp float;

uniform vec2  uCanvasSize;
uniform vec2  uOffset;
uniform vec2  uSize;
uniform vec4  uCornerRadii;
uniform vec4  uColor;
// --- ORIGINAL-SPACE SDF clip (faithful to graphicsLayer { scaleX, scaleY }) ---
// The white overlay (onDrawSurface drawRect) is drawn INSIDE the graphicsLayer,
// so it is clipped to the ORIGINAL capsule shape, then scaled with the layer.
// Computing the clip SDF in original space keeps the capsule clip correct when
// the button is stretched (no corner bleed, no stretched-clip artifacts).
uniform vec2  uOriginalSize;
uniform float uOriginalCornerRadius;
uniform vec2  uLayerScale;
uniform float uElementRotation;

${SDF_GLSL}

void main() {
    vec2 screenCoord = vec2(gl_FragCoord.x, uCanvasSize.y - gl_FragCoord.y);
    vec2 elementCenter = uOffset + uSize * 0.5;
    vec2 centeredScreen = screenCoord - elementCenter;
    vec2 layerScale = max(uLayerScale, vec2(1e-4));
    vec2 centeredOrig = centeredScreen / layerScale;
    vec2 origHalfSize = uOriginalSize * 0.5;
    float sd = sdShape(rotateBy(centeredOrig, -uElementRotation), origHalfSize, uOriginalCornerRadius);
    if (sd > 0.5) discard;
    float clipAlpha = 1.0 - smoothstep(-0.5, 0.5, sd);

    gl_FragColor = vec4(uColor.rgb, uColor.a * clipAlpha);
}
`
  );
  var RIM_HIGHLIGHT_FRAGMENT_SHADER = (
    /* glsl */
    `
#extension GL_OES_standard_derivatives : enable
precision highp float;

uniform vec2  uCanvasSize;
uniform vec2  uOffset;          // element top-left in canvas px (top-left origin) \u2014 SCALED rect
uniform vec2  uSize;            // element size in canvas px \u2014 SCALED (includes graphicsLayer scale)
uniform vec4  uCornerRadii;     // (topLeft, topRight, bottomRight, bottomLeft) in px \u2014 SCALED
uniform vec4  uHighlightColor;  // rgb + 1.0
uniform float uHighlightAngle;  // radians
uniform float uHighlightFalloff;
uniform float uHighlightAlpha;
uniform float uHighlightMode;     // 0=Default, 1=Ambient, 2=Plain
uniform float uHighlightStrokeWidth;
uniform float uHighlightBlur;
// --- ORIGINAL-SPACE SDF (faithful to graphicsLayer { scaleX, scaleY }) ---
// Same approach as the element shader: compute SDF/stroke in ORIGINAL space
// (shape is correct, not stretched), so the highlight clip + stroke remain a
// correct capsule shape that is then scaled by graphicsLayer. Without this,
// a horizontally-stretched button would stretch the highlight clip too,
// making the stroke band uneven. See element.ts for the full rationale.
uniform vec2  uOriginalSize;        // element size in px (ORIGINAL, unscaled)
uniform float uOriginalCornerRadius; // corner radius in px (ORIGINAL, unscaled)
uniform vec2  uLayerScale;          // (scaleX, scaleY) from graphicsLayer
uniform float uElementRotation;     // rotation in radians (graphicsLayer rotationZ)

${SDF_GLSL}

void main() {
    vec2 screenCoord = vec2(gl_FragCoord.x, uCanvasSize.y - gl_FragCoord.y);
    // elementCenter is the SAME for scaled and original rects (scaling is
    // around the center), so uOffset + uSize*0.5 gives the correct center.
    vec2 elementCenter = uOffset + uSize * 0.5;
    vec2 centeredScreen = screenCoord - elementCenter;
    // Map to ORIGINAL space (guard against divide-by-zero).
    vec2 layerScale = max(uLayerScale, vec2(1e-4));
    vec2 centeredOrig = centeredScreen / layerScale;
    // Un-rotate into the element's local space so the SDF shape rotates.
    vec2 centeredOrigRot = rotateBy(centeredOrig, -uElementRotation);

    vec2 origHalfSize = uOriginalSize * 0.5;
    float origRadius = uOriginalCornerRadius;

    // SDF for stroke \u2014 analytic sdRoundedRect (matches the pre-capsule
    // highlight implementation). When capsule is OFF, this is the exact
    // shape. When capsule is ON, this is a close approximation (circular
    // arc vs G2 Bezier \u2014 the difference is sub-pixel within the 2px stroke
    // band, invisible in the highlight).
    float sd = sdRoundedRect(centeredOrigRot, origHalfSize, origRadius);

    // Outside the shape \u2014 clip (hard discard, matching pre-capsule behavior).
    if (sd > 0.0) discard;

    // Stroke mask \u2014 faithful to HighlightModifier.kt:
    //   paint.style = Stroke
    //   paint.strokeWidth = ceil(width.toPx()) * 2     // full stroke, centered on edge
    //   paint.blur(blurRadius.toPx())                   // BlurMaskFilter, Blur.NORMAL
    //   canvas.clipOutline(outline)                     // clip to inside the shape
    //   canvas.drawOutline(outline, paint)              // stroke centered on edge
    //
    // Implementation: first compute a HARD-EDGE stroke mask (1.0 inside the
    // stroke band, 0.0 outside), then convolve it with a Gaussian kernel by
    // sampling the SDF at multiple offsets along the gradient direction.
    // This mirrors the original's two-step process (draw stroke \u2192 blur),
    // rather than using an analytic erf approximation.
    //
    // The hard stroke band: sd in [-strokeHalf, +strokeHalf].
    // After clip (sd > 0 discarded by the outer if), only [-strokeHalf, 0] shows.
    //
    // Faithful to the original BlurMaskFilter:
    //   paint.blur(blurRadius.toPx())  \u2192  BlurMaskFilter(NORMAL, sigma=blurRadius_px)
    // In Skia/Android, BlurMaskFilter's radius param IS the Gaussian sigma
    // (not radius/3). blurRadius = width/2 = 0.25dp, so sigma = 0.25*dpr px.
    // uHighlightBlur is already in device px (set by the renderer as widthDp*dpr*0.5).
    float strokeHalf = uHighlightStrokeWidth * 0.5;
    float sigma = max(uHighlightBlur, 0.1);

    // Gaussian convolution of the hard stroke mask \u2014 3-tap (\u03C3-spaced).
    // The original's BlurMaskFilter has \u03C3 = blurRadius = 0.25dp \u2192 0.25px at
    // dpr=1. At this sub-pixel sigma, only 3 taps (at -\u03C3, 0, +\u03C3) are needed
    // \u2014 the Gaussian weight at \xB12\u03C3 is exp(-2) \u2248 0.14, negligible. This
    // replaces the old 65-tap loop (which computed 65 exp() calls per pixel,
    // ~650 cycles \u2014 the single biggest shader cost). 3 taps = 3 exp() = ~30
    // cycles, a 20\xD7 reduction with identical visual result at \u03C3=0.25.
    //   hardMask(sd) = 1.0 if |sd| < strokeHalf, else 0.0
    //   blurred(sd) = \u03A3 hardMask(sd - offset_k) * gauss(offset_k, \u03C3)
    // CLIP HALVING: the stroke is centered on sd=0; clip removes sd>0 (outer
    // half), so peak \u2248 0.5. We halve to match.
    float strokeMask = 0.0;
    float wSum = 0.0;
    for (int i = -1; i <= 1; i++) {
        float offset = float(i) * sigma;  // taps at -\u03C3, 0, +\u03C3
        float sampleSd = sd - offset;
        float hard = (abs(sampleSd) < strokeHalf) ? 1.0 : 0.0;
        float w = exp(-0.5 * (offset * offset) / (sigma * sigma));
        strokeMask += hard * w;
        wSum += w;
    }
    strokeMask /= wSum;
    strokeMask *= 0.5;  // clip halves the symmetric stroke at the edge

    if (uHighlightMode < 0.5) {
        // Default \u2014 shader returns color * intensity, Plus blend.
        float gradRadius = min(origRadius * 1.5, min(origHalfSize.x, origHalfSize.y));
        vec2 grad = gradSdRoundedRect(centeredOrigRot, origHalfSize, gradRadius);
        vec2 normal = vec2(cos(uHighlightAngle), sin(uHighlightAngle));
        float d = dot(grad, normal);
        float intensity = pow(abs(d), uHighlightFalloff);
        vec3 c = uHighlightColor.rgb * intensity * strokeMask * uHighlightAlpha;
        gl_FragColor = vec4(c, 1.0);
    } else if (uHighlightMode < 1.5) {
        // Ambient \u2014 shader returns half4(t,t,t,1.0)*intensity, SrcOver blend.
        float gradRadius = min(origRadius * 1.5, min(origHalfSize.x, origHalfSize.y));
        vec2 grad = gradSdRoundedRect(centeredOrigRot, origHalfSize, gradRadius);
        vec2 normal = vec2(cos(uHighlightAngle), sin(uHighlightAngle));
        float d = dot(grad, normal);
        float intensity = pow(abs(d), uHighlightFalloff);
        // No step(0,d) \u2014 use full intensity on both sides (no black edge).
        float i = intensity * strokeMask * uHighlightAlpha;
        gl_FragColor = vec4(uHighlightColor.rgb * i, i);
    } else {
        // Plain \u2014 even stroke, paint.color, Plus blend.
        vec3 c = uHighlightColor.rgb * strokeMask * uHighlightAlpha;
        gl_FragColor = vec4(c, 1.0);
    }
}
`
  );
  var HIGHLIGHT_STROKE_FRAGMENT_SHADER = (
    /* glsl */
    `
#extension GL_OES_standard_derivatives : enable
precision highp float;

uniform vec2  uCanvasSize;
uniform vec2  uOffset;          // element top-left (top-left origin) \u2014 SCALED
uniform vec2  uSize;            // element size \u2014 SCALED
uniform vec4  uCornerRadii;     // SCALED
uniform float uHighlightStrokeWidth;  // ceil(width*dpr)*2, device px
uniform vec2  uOriginalSize;
uniform float uOriginalCornerRadius;
uniform vec2  uLayerScale;
uniform float uElementRotation;
// uCornerStyle, uUseContinuousSdf, uContinuousSdf, uContinuousSdfTexSize,
// uContinuousSdfElementSize are declared in SDF_GLSL (do NOT redeclare here).

${SDF_GLSL}

void main() {
    vec2 screenCoord = vec2(gl_FragCoord.x, uCanvasSize.y - gl_FragCoord.y);
    vec2 elementCenter = uOffset + uSize * 0.5;
    vec2 centeredScreen = screenCoord - elementCenter;
    vec2 layerScale = max(uLayerScale, vec2(1e-4));
    vec2 centeredOrig = centeredScreen / layerScale;
    vec2 centeredOrigRot = rotateBy(centeredOrig, -uElementRotation);

    vec2 origHalfSize = uOriginalSize * 0.5;
    float origRadius = uOriginalCornerRadius;

    float sd = sdShape(centeredOrigRot, origHalfSize, origRadius);

    // clipOutline \u2014 clip to INSIDE the shape. Outside (sd > 0) is discarded.
    float edgeAA;
    if (uUseContinuousSdf > 0.5) {
        float mask = sampleClipMask(centeredOrigRot, origHalfSize, origRadius);
        if (mask < 0.01) discard;
        edgeAA = mask;
    } else {
        if (sd > 1.0) discard;
        float aaWidth = max(fwidth(sd), 1.0) * 1.5;
        edgeAA = 1.0 - smoothstep(-aaWidth, aaWidth, sd);
    }

    // Stroke band centered on the edge (sd = 0), with 0.5px coverage AA on
    // the inner boundary. The outer boundary (sd = +strokeHalf) is clipped
    // away by edgeAA above. Faithful to Skia Paint.Stroke's coverage AA.
    // The BlurMaskFilter pass (when sigma >= 0.5px) softens this further;
    // at sub-pixel sigma (0.25px) the blur is skipped and this 0.5px AA
    // is what matches the original's look (Skia's 0.25px blur is negligibly
    // soft \u2014 essentially just AA).
    float strokeHalf = uHighlightStrokeWidth * 0.5;
    float strokeAAWidth = max(fwidth(sd), 1.0) * 1.5;
    float strokeAA = 1.0 - smoothstep(strokeHalf - strokeAAWidth, strokeHalf, abs(sd));

    gl_FragColor = vec4(0.0, 0.0, 0.0, strokeAA * edgeAA);
}
`
  );
  var HIGHLIGHT_COMPOSITE_FRAGMENT_SHADER = (
    /* glsl */
    `
#extension GL_OES_standard_derivatives : enable
precision highp float;

uniform vec2  uCanvasSize;
uniform vec2  uOffset;
uniform vec2  uSize;
uniform vec4  uCornerRadii;
uniform sampler2D uBlurredMask;   // the 2-pass-blurred stroke mask FBO
uniform vec2  uMaskTexSize;       // size of the mask FBO (= canvas size)
uniform vec4  uHighlightColor;    // rgb + 1.0
uniform float uHighlightAngle;
uniform float uHighlightFalloff;
uniform float uHighlightAlpha;
uniform float uHighlightMode;     // 0=Default, 1=Ambient, 2=Plain
uniform vec2  uOriginalSize;
uniform float uOriginalCornerRadius;
uniform vec2  uLayerScale;
uniform float uElementRotation;
// uCornerStyle, uUseContinuousSdf, uContinuousSdf, uContinuousSdfTexSize,
// uContinuousSdfElementSize are declared in SDF_GLSL (do NOT redeclare here).

${SDF_GLSL}

void main() {
    vec2 screenCoord = vec2(gl_FragCoord.x, uCanvasSize.y - gl_FragCoord.y);

    // Sample the blurred stroke mask at this pixel. The mask FBO covers the
    // full canvas (same size), so UV = gl_FragCoord / maskTexSize.
    // Mask FBO is Y-down (top-left origin, like our scene FBOs), so flip Y
    // to match the screenCoord convention.
    vec2 maskUv = vec2(gl_FragCoord.x / uMaskTexSize.x, gl_FragCoord.y / uMaskTexSize.y);
    float mask = texture2D(uBlurredMask, maskUv).a;
    if (mask < 0.001) discard;

    // Compute intensity from the SDF gradient (AGSL DefaultHighlightShaderString).
    vec2 elementCenter = uOffset + uSize * 0.5;
    vec2 centeredScreen = screenCoord - elementCenter;
    vec2 layerScale = max(uLayerScale, vec2(1e-4));
    vec2 centeredOrig = centeredScreen / layerScale;
    vec2 centeredOrigRot = rotateBy(centeredOrig, -uElementRotation);
    vec2 origHalfSize = uOriginalSize * 0.5;
    float origRadius = uOriginalCornerRadius;

    // Faithful clip-after-blur: the original does clipOutline \u2192 stroke(blur),
    // but Skia applies clip at the canvas level AFTER the BlurMaskFilter
    // spreads alpha. So alpha that blurred OUTSIDE the shape is clipped away.
    // Our stroke shader clips before blur (discard sd>0), then blur spreads
    // alpha back outside \u2014 we must clip AGAIN here to match. Without this,
    // the highlight "leaks" outside the shape, making it brighter than the
    // original (which has zero contribution outside the clip region).
    float sd = sdShape(centeredOrigRot, origHalfSize, origRadius);
    float clipAA;
    if (uUseContinuousSdf > 0.5) {
        clipAA = sampleClipMask(centeredOrigRot, origHalfSize, origRadius);
    } else {
        clipAA = 1.0 - smoothstep(-0.5, 0.5, sd);
    }
    mask *= clipAA;
    if (mask < 0.001) discard;

    float intensity;
    if (uHighlightMode < 1.5) {
        // Default + Ambient use the SDF gradient \xB7 normal.
        float gradRadius = min(origRadius * 1.5, min(origHalfSize.x, origHalfSize.y));
        vec2 grad = gradSdRoundedRect(centeredOrigRot, origHalfSize, gradRadius);
        vec2 normal = vec2(cos(uHighlightAngle), sin(uHighlightAngle));
        float d = dot(grad, normal);
        intensity = pow(abs(d), uHighlightFalloff);
    } else {
        // Plain \u2014 no directional intensity (even stroke).
        intensity = 1.0;
    }

    float a = mask * uHighlightAlpha;

    if (uHighlightMode < 0.5) {
        // Default \u2014 Plus blend. Output premultiplied rgb (alpha=1 so blendFunc
        // (ONE, ONE) adds rgb directly).
        vec3 c = uHighlightColor.rgb * intensity * a;
        gl_FragColor = vec4(c, 1.0);
    } else if (uHighlightMode < 1.5) {
        // Ambient \u2014 SrcOver blend. Premultiplied output.
        // Ambient uses t = step(0, d) in the original, but we keep abs(d)
        // (both sides bright) to match the existing behavior. The original's
        // step gives a hard dark/bright split; our abs gives symmetric glow.
        float i = intensity * a;
        gl_FragColor = vec4(uHighlightColor.rgb * i, i);
    } else {
        // Plain \u2014 Plus blend, no intensity.
        vec3 c = uHighlightColor.rgb * a;
        gl_FragColor = vec4(c, 1.0);
    }
}
`
  );
  var STROKE_MASK_COMPOSITE_FRAGMENT_SHADER = (
    /* glsl */
    `
precision highp float;

uniform vec2  uCanvasSize;
uniform vec2  uOffset;
uniform vec2  uSize;
uniform vec4  uCornerRadii;
uniform sampler2D uStrokeMask;
uniform vec2  uMaskOffset;
uniform vec2  uMaskSize;
uniform vec4  uHighlightColor;
uniform float uHighlightAngle;
uniform float uHighlightFalloff;
uniform float uHighlightAlpha;
uniform float uHighlightMode;
uniform vec2  uOriginalSize;
uniform float uOriginalCornerRadius;
uniform vec2  uLayerScale;
uniform float uElementRotation;

${SDF_GLSL}

void main() {
    vec2 screenCoord = vec2(gl_FragCoord.x, uCanvasSize.y - gl_FragCoord.y);

    // Map screen coord \u2192 element-local ORIGINAL space (un-scale, un-rotate).
    // The stroke mask is drawn in original space (origSizeX \xD7 origSizeY + margin).
    // elementCenter is the same in scaled and original space (scaling is around center).
    vec2 elementCenter = uOffset + uSize * 0.5;
    vec2 centeredScreen = screenCoord - elementCenter;
    vec2 layerScale = max(uLayerScale, vec2(1e-4));
    vec2 centeredOrig = centeredScreen / layerScale;
    vec2 centeredOrigRot = rotateBy(centeredOrig, -uElementRotation);

    // Mask UV: map original-space coord \u2192 mask texture UV.
    // The mask was drawn with translate(margin, margin), so mask (0,0) =
    // element-local (-margin). Element-local coord 0..origSize maps to
    // mask UV (0+margin)/maskSize .. (origSize+margin)/maskSize.
    // uMaskOffset = margin (scalar, passed as vec2 for convenience).
    // uMaskSize = (origSize + 2*margin).
    vec2 origHalfSize = uOriginalSize * 0.5;
    vec2 maskTexCoord = centeredOrigRot + origHalfSize;  // 0..origSize (element-local)
    vec2 maskUv = (maskTexCoord + uMaskOffset) / uMaskSize;
    if (maskUv.x < 0.0 || maskUv.x > 1.0 || maskUv.y < 0.0 || maskUv.y > 1.0) discard;
    float mask = texture2D(uStrokeMask, maskUv).a;
    if (mask < 0.001) discard;

    float origRadius = uOriginalCornerRadius;

    float intensity;
    if (uHighlightMode < 1.5) {
        float gradRadius = min(origRadius * 1.5, min(origHalfSize.x, origHalfSize.y));
        vec2 grad = gradSdRoundedRect(centeredOrigRot, origHalfSize, gradRadius);
        vec2 normal = vec2(cos(uHighlightAngle), sin(uHighlightAngle));
        float d = dot(grad, normal);
        intensity = pow(abs(d), uHighlightFalloff);
    } else {
        intensity = 1.0;
    }

    float a = mask * uHighlightAlpha;
    if (uHighlightMode < 0.5) {
        gl_FragColor = vec4(uHighlightColor.rgb * intensity * a, 1.0);
    } else if (uHighlightMode < 1.5) {
        float i = intensity * a;
        gl_FragColor = vec4(uHighlightColor.rgb * i, i);
    } else {
        gl_FragColor = vec4(uHighlightColor.rgb * a, 1.0);
    }
}
`
  );

  // shaders/inner-shadow.ts
  var INNER_SHADOW_MASK_COMPOSITE_FRAGMENT_SHADER = (
    /* glsl */
    `
precision highp float;

uniform vec2  uCanvasSize;
uniform vec2  uOffset;           // element top-left in canvas px (top-left origin) \u2014 SCALED rect
uniform vec2  uSize;             // element size in canvas px \u2014 SCALED
uniform vec4  uCornerRadii;      // (topLeft, topRight, bottomRight, bottomLeft) \u2014 SCALED
uniform sampler2D uInnerShadowMask; // Canvas2D-generated blurred ring mask
uniform vec2  uMaskOffset;       // margin in device px (for UV mapping: element-local \u2192 mask UV)
uniform vec2  uMaskSize;         // total mask size in device px (w+2*margin, h+2*margin)
uniform vec3  uInnerShadowColor; // shadow color RGB
uniform float uInnerShadowAlpha; // shadow alpha
// --- ORIGINAL-SPACE SDF clip (faithful to graphicsLayer { scaleX, scaleY }) ---
uniform vec2  uOriginalSize;        // element size in px (ORIGINAL, unscaled)
uniform float uOriginalCornerRadius; // corner radius in px (ORIGINAL, unscaled)
uniform vec2  uLayerScale;          // (scaleX, scaleY) from graphicsLayer
uniform float uElementRotation;     // rotation in radians (graphicsLayer rotationZ)

${SDF_GLSL}

void main() {
    vec2 screenCoord = vec2(gl_FragCoord.x, uCanvasSize.y - gl_FragCoord.y);

    // Map screen coord \u2192 element-local ORIGINAL space (un-scale, un-rotate).
    // The inner shadow mask is drawn in original space (origSize + margin).
    // elementCenter is the same in scaled and original space (scaling is
    // around center).
    vec2 elementCenter = uOffset + uSize * 0.5;
    vec2 centeredScreen = screenCoord - elementCenter;
    vec2 layerScale = max(uLayerScale, vec2(1e-4));
    vec2 centeredOrig = centeredScreen / layerScale;
    vec2 centeredOrigRot = rotateBy(centeredOrig, -uElementRotation);

    // SDF for shape clip \u2014 faithful to InnerShadowModifier.kt's final
    // clipOutline call before drawLayer. The original uses Skia's
    // geometric clip with smooth AA (sub-pixel transition).
    // We replicate with smoothstep \u2014 NO hard discard.
    vec2 origHalfSize = uOriginalSize * 0.5;
    float sd = sdShape(centeredOrigRot, origHalfSize, uOriginalCornerRadius);

    // Smooth clipAlpha: 1.0 fully inside (sd \u2264 0), smoothly fading
    // across the boundary (sd 0\u21921.5), 0.0 outside (sd \u2265 1.5).
    // The 1.5px transition width matches Skia's clipOutline AA behavior
    // \u2014 pixels at the exact boundary (sd=0) retain FULL intensity, with
    // a gentle fade that removes outward blur leakage smoothly.
    // This is NOT a hard discard \u2014 it's a smooth clip that matches the
    // original's geometric clipOutline exactly.
    float clipAlpha = 1.0 - smoothstep(0.0, 1.5, sd);

    // Skip truly invisible pixels for performance (not a visual clip)
    if (clipAlpha < 0.004) discard;

    // Map to mask UV: original-space coord \u2192 mask texture UV.
    vec2 maskTexCoord = centeredOrigRot + origHalfSize;  // 0..origSize (element-local)
    vec2 maskUv = (maskTexCoord + uMaskOffset) / uMaskSize;

    // Sample the mask texture. CLAMP_TO_EDGE wrapping handles UV values
    // slightly outside (0..1) gracefully \u2014 returns transparent at edges.
    float mask = texture2D(uInnerShadowMask, maskUv).a;

    // Skip truly invisible pixels for performance (not a visual clip)
    // Threshold is very low to avoid cutting off faint but visible shadow edges.
    if (mask < 0.003) discard;

    // Premultiplied SrcOver composite: shadowColor \xD7 mask \xD7 shadowAlpha \xD7 clipAlpha.
    // clipAlpha provides smooth shape-boundary transition (faithful to original's
    // clipOutline AA). Output is premultiplied (rgb = color * alpha).
    // Renderer uses gl.blendFunc(ONE, ONE_MINUS_SRC_ALPHA) \u2014 premultiplied SrcOver.
    float a = mask * uInnerShadowAlpha * clipAlpha;
    gl_FragColor = vec4(uInnerShadowColor * a, a);
}
`
  );

  // shaders/scene-bg.ts
  var VERTEX_SHADER = (
    /* glsl */
    `
attribute vec2 aPos;
void main() {
    gl_Position = vec4(aPos, 0.0, 1.0);
}
`
  );
  var WALLPAPER_FRAGMENT_SHADER = (
    /* glsl */
    `
precision highp float;

uniform sampler2D uBackdrop;
uniform vec2 uCanvasSize;
uniform vec2 uWallpaperSize;

${COVER_GLSL}

void main() {
    vec2 screenCoord = vec2(gl_FragCoord.x, uCanvasSize.y - gl_FragCoord.y);
    vec2 uv = coverUv(screenCoord);
    gl_FragColor = texture2D(uBackdrop, uv);
}
`
  );
  var COPY_FRAGMENT_SHADER = (
    /* glsl */
    `
precision highp float;

uniform sampler2D uTexture;
uniform vec2 uCanvasSize;

void main() {
    vec2 uv = vec2(gl_FragCoord.x / uCanvasSize.x, gl_FragCoord.y / uCanvasSize.y);
    gl_FragColor = texture2D(uTexture, uv);
}
`
  );
  var SOLID_FILL_FRAGMENT_SHADER = (
    /* glsl */
    `
precision highp float;

uniform vec4 uColor;

void main() {
    gl_FragColor = uColor;
}
`
  );
  var COLOR_CONTROLS_FRAGMENT_SHADER = (
    /* glsl */
    `
precision highp float;

uniform sampler2D uTexture;
uniform vec2 uTexSize;
uniform float uBrightness;
uniform float uContrast;
uniform float uSaturation;

void main() {
    vec2 uv = vec2(gl_FragCoord.x / uTexSize.x, gl_FragCoord.y / uTexSize.y);
    vec4 c = texture2D(uTexture, uv);
    float invSat = 1.0 - uSaturation;
    float r = 0.213 * invSat;
    float g = 0.715 * invSat;
    float b = 0.072 * invSat;
    float t = (0.5 - uContrast * 0.5 + uBrightness);
    float cs = uContrast * uSaturation;
    float cr = uContrast * r;
    float cg = uContrast * g;
    float cb = uContrast * b;
    vec3 outc;
    outc.r = (cr + cs) * c.r + cg * c.g + cb * c.b + t;
    outc.g = cr * c.r + (cg + cs) * c.g + cb * c.b + t;
    outc.b = cr * c.r + cg * c.g + (cb + cs) * c.b + t;
    gl_FragColor = vec4(outc, c.a);
}
`
  );
  var SCENE_TINT_FRAGMENT_SHADER = (
    /* glsl */
    `
precision highp float;

uniform sampler2D uTexture;
uniform vec2 uCanvasSize;
uniform vec3 uTintColor;   // rgb 0..1 (accentColor)

// ColorFilter.tint(color, blendMode = BlendMode.SrcIn):
//   result.rgb = src.rgb (the tint color)
//   result.a   = dst.a * src.a
// SrcIn replaces the destination's RGB with the tint color while
// preserving its alpha \u2014 opaque content becomes solid tint, transparent
// areas stay transparent. This matches Compose's ColorFilter.tint default.
void main() {
    vec2 uv = vec2(gl_FragCoord.x / uCanvasSize.x, gl_FragCoord.y / uCanvasSize.y);
    vec4 src = texture2D(uTexture, uv);
    gl_FragColor = vec4(uTintColor, src.a);
}
`
  );

  // shaders/scene-fg.ts
  var FOREGROUND_FRAGMENT_SHADER = (
    /* glsl */
    `
precision highp float;

uniform sampler2D uTexture;
uniform vec2 uCanvasSize;
uniform vec2 uOffset;   // foreground texture top-left in canvas px (top-left origin) \u2014 SCALED rect
uniform vec2 uSize;     // foreground texture size in canvas px \u2014 SCALED
uniform vec4 uCornerRadii;  // capsule radii (topLeft, topRight, bottomRight, bottomLeft) in px \u2014 SCALED
uniform float uAlpha;   // global alpha multiplier (used for press fade)
// --- ORIGINAL-SPACE SDF clip (faithful to graphicsLayer { scaleX, scaleY }) ---
// The original wraps everything (text included) in a graphicsLayer clipped to
// the capsule shape, THEN scales the layer. So the clip shape is the ORIGINAL
// capsule, not the stretched one. We compute the clip SDF in original space so
// a stretched button keeps correct capsule clipping (no corner bleed). The
// texture UV still uses the scaled rect (uOffset/uSize) since the foreground
// texture is rendered at the element's scaled on-screen size.
uniform vec2  uOriginalSize;        // element size in px (ORIGINAL, unscaled)
uniform float uOriginalCornerRadius; // corner radius in px (ORIGINAL, unscaled)
uniform vec2  uLayerScale;          // (scaleX, scaleY) from graphicsLayer

${SDF_GLSL}

void main() {
    // gl_FragCoord is bottom-left origin in WebGL framebuffer space.
    // Flip Y to get top-left origin (matching CSS / 2D canvas convention).
    vec2 screenCoord = vec2(gl_FragCoord.x, uCanvasSize.y - gl_FragCoord.y);
    vec2 localCoord = screenCoord - uOffset;
    // Scissor to the (scaled) foreground rectangle.
    if (localCoord.x < 0.0 || localCoord.x > uSize.x ||
        localCoord.y < 0.0 || localCoord.y > uSize.y) {
        discard;
    }

    // --- Capsule clip in ORIGINAL space (faithful to graphicsLayer clip) ---
    // elementCenter is the SAME for scaled and original rects (scaling is
    // around the center). Map screen coord \u2192 original space for the SDF so
    // the clip shape is the original capsule, not the stretched one.
    vec2 elementCenter = uOffset + uSize * 0.5;
    vec2 centeredScreen = screenCoord - elementCenter;
    vec2 layerScale = max(uLayerScale, vec2(1e-4));
    vec2 centeredOrig = centeredScreen / layerScale;
    vec2 origHalfSize = uOriginalSize * 0.5;
    float clipAlpha;
    if (uUseContinuousSdf > 0.5) {
        float mask = sampleClipMask(centeredOrig, origHalfSize, uOriginalCornerRadius);
        if (mask < 0.01) discard;
        clipAlpha = mask;
    } else {
        float sdClip = sdClipShape(centeredOrig, origHalfSize, uOriginalCornerRadius);
        if (sdClip > 0.5) discard;
        clipAlpha = 1.0 - smoothstep(-0.5, 0.5, sdClip);
    }

    // The texture is uploaded from a 2D canvas with UNPACK_FLIP_Y_WEBGL=false,
    // so texture row 0 (= v=0) is the TOP row of the source canvas. Combined
    // with the Y flip above, uv.y=0 corresponds to the top of the button rect
    // (which is what we want \u2014 text drawn at the middle of the source canvas
    // appears at the middle of the button).
    //
    // The texture is uploaded with UNPACK_PREMULTIPLY_ALPHA_WEBGL=true, so
    // c is already in premultiplied form (c.rgb <= c.a). We scale both
    // rgb and a by uAlpha * clipAlpha and output premultiplied rgba, paired
    // with blendFunc(ONE, ONE_MINUS_SRC_ALPHA) at the draw site.
    vec2 uv = localCoord / uSize;
    vec4 c = texture2D(uTexture, uv);
    float a = c.a * uAlpha * clipAlpha;
    gl_FragColor = vec4(c.rgb * uAlpha * clipAlpha, a);
}
`
  );
  var PLAIN_RECT_FRAGMENT_SHADER = (
    /* glsl */
    `
precision highp float;

uniform vec2  uCanvasSize;
uniform vec2  uOffset;
uniform vec2  uSize;
uniform vec4  uCornerRadii;
uniform vec4  uColor;       // rgba (premultiplied not required; alpha used as-is)

${SDF_GLSL}

void main() {
    vec2 screenCoord = vec2(gl_FragCoord.x, uCanvasSize.y - gl_FragCoord.y);
    vec2 localCoord = screenCoord - uOffset;
    vec2 halfSize = uSize * 0.5;
    vec2 centeredCoord = localCoord - halfSize;

    float radius = radiusAt(centeredCoord, uCornerRadii);
    float alpha;
    if (uUseContinuousSdf > 0.5) {
        float mask = sampleClipMask(centeredCoord, halfSize, radius);
        if (mask < 0.01) discard;
        alpha = mask;
    } else {
        float sdClip = sdClipShape(centeredCoord, halfSize, radius);
        if (sdClip > 0.5) discard;
        alpha = 1.0 - smoothstep(-0.5, 0.5, sdClip);
    }
    gl_FragColor = vec4(uColor.rgb, uColor.a * alpha);
}
`
  );
  var PROGRESSIVE_BLUR_FRAGMENT_SHADER = (
    /* glsl */
    `
precision highp float;

uniform sampler2D uBackdrop;
uniform vec2  uCanvasSize;
uniform vec2  uWallpaperSize;
uniform vec2  uOffset;          // band top-left in canvas px (top-left origin)
uniform vec2  uSize;            // band size in canvas px
uniform float uBlurRadius;      // px in canvas space
uniform vec4  uTintColor;       // rgba
uniform float uTintIntensity;   // 0..1

${COVER_GLSL}

// 9-tap poisson disc \u2014 offsets are inlined because GLSL ES 1.00 (WebGL 1)
// does not support array constructors or const-array initializers.
// The offsets are normalized (unit disc), multiplied by step (radius in UV).
vec4 sampleBackdrop(vec2 canvasPx, float radius) {
    vec2 uvScale = canvasPxToUvScale();
    vec2 uv = coverUv(canvasPx);
    vec2 st = radius * uvScale;
    vec4 sum = vec4(0.0);
    sum += texture2D(uBackdrop, uv + vec2( 0.0000,  0.0000) * st);
    sum += texture2D(uBackdrop, uv + vec2( 0.5000,  0.0000) * st);
    sum += texture2D(uBackdrop, uv + vec2(-0.5000,  0.0000) * st);
    sum += texture2D(uBackdrop, uv + vec2( 0.0000,  0.5000) * st);
    sum += texture2D(uBackdrop, uv + vec2( 0.0000, -0.5000) * st);
    sum += texture2D(uBackdrop, uv + vec2( 0.3536,  0.3536) * st);
    sum += texture2D(uBackdrop, uv + vec2(-0.3536,  0.3536) * st);
    sum += texture2D(uBackdrop, uv + vec2( 0.3536, -0.3536) * st);
    sum += texture2D(uBackdrop, uv + vec2(-0.3536, -0.3536) * st);
    return sum / 9.0;
}

void main() {
    vec2 screenCoord = vec2(gl_FragCoord.x, uCanvasSize.y - gl_FragCoord.y);
    vec2 localCoord = screenCoord - uOffset;
    // Outside the band \u2014 nothing to draw.
    if (localCoord.x < 0.0 || localCoord.x > uSize.x ||
        localCoord.y < 0.0 || localCoord.y > uSize.y) {
        discard;
    }

    // Alpha mask: opaque at top (coord.y = size.y, i.e. BOTTOM in top-left
    // origin = size.y in AGSL coord), transparent at bottom. Matches the
    // Kotlin smoothstep(size.y, size.y * 0.5, coord.y).
    float a = smoothstep(uSize.y, uSize.y * 0.5, localCoord.y);

    // Sample the (cover-fit) backdrop at the canvas pixel, blurred.
    vec4 blurred = sampleBackdrop(screenCoord, uBlurRadius);

    // Faithful to AlphaMask shader: mix(content * blurAlpha, tint * tintAlpha, tintIntensity)
    // This is PREMULTIPLIED (rgb already scaled by alpha). The renderer uses
    // premultiplied alpha blending for the progressive blur pass, so we output
    // premultiplied rgb with the mask alpha.
    vec3 premulRgb = mix(blurred.rgb * a, uTintColor.rgb * a, uTintIntensity);
    gl_FragColor = vec4(premulRgb, a);
}
`
  );

  // shaders/siri-wave.ts
  var SIRI_WAVE_FRAGMENT_SHADER = (
    /* glsl */
    `
precision highp float;
uniform vec2 iResolution; uniform float iTime;
uniform float uSpeed; uniform float uScale;
const float PI = 3.14159265359;
const float AMPLITUDE   = 0.32;
const float FREQ        = 1.1;
const float ABER_FREQ   = 1.0;
const float SPEED       = 2.4;
const float WAVE_SCALE  = 0.6;
const float ABERRATION  = 2.6;
const float THICKNESS   = 3.0;
const float INTENSITY   = 2.;
const float FALLOFF     = 1.7;
const float EDGE_MASK   = 0.4;
const float EDGE_INSET  = 0.0;
const float BAND_FILL   = 30000.0;
const float BAND_THICK  = 0.08;
const float SOFTNESS    = 2.5;
const float LOW_AMP     = 6.0;
const float LOW_INT     = 1.5;
const float MID_ABER    = 0.8;
const float MID_ABAMP   = 0.05;
const float MID_BAND    = 20.0;
const float MID_SOFT    = 0.4;
const float HIGH_ABER   = 0.5;
const float HIGH_ABAMP  = 0.06;
const float RESOLVED    = 1.0;
const float UNRES_SCALE = 0.14;

vec3 spectral4(int s){
    float x = float(s);
    return clamp(vec3(abs(x-3.0)-1.0, 2.0-abs(x-2.0), 2.0-abs(x-4.0)), 0.0, 1.0);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord){
    vec2 R = iResolution.xy;
    float aspect = R.x / R.y;
    vec2 p = (fragCoord + 0.5) * 2.0 / R - 1.0;
    p.x *= aspect;
    float yScreen = p.y;
    p /= max(WAVE_SCALE * uScale, 0.1);

    float t   = iTime * uSpeed;
    float low  = clamp(0.45 + 0.45*sin(t*0.8)*sin(t*0.37+1.0), 0.0, 1.0);
    float mid  = clamp(0.40 + 0.40*sin(t*1.7+2.0)*sin(t*0.53), 0.0, 1.0);
    float high = clamp(0.30 + 0.30*sin(t*2.9+4.0)*sin(t*0.71+2.0), 0.0, 1.0);

    float res   = clamp(RESOLVED, 0.0, 1.0);
    float drift = mod(t, 20.0*PI) * SPEED;

    float xN  = p.x / max(aspect, 1.0);
    float env = cos(PI*0.5 * min(abs(0.9*xN), 1.0));
    env *= env;

    float A1    = AMPLITUDE + 0.01*low*LOW_AMP;
    float A2    = A1 + mid*MID_ABAMP + high*HIGH_ABAMP;
    float AB    = (ABERRATION + mid*MID_ABER + high*HIGH_ABER)*res;
    float th    = mix(0.1, 0.01*THICKNESS, res);
    float inten = mix(0.1, 0.01*(INTENSITY + low*LOW_INT), res);
    float soft  = 0.01*res*max(0.0, SOFTNESS + mid*MID_SOFT);

    float dUnres = max(length(p) - mix(0.14, UNRES_SCALE, res), 0.0);
    float yMain = A1 * env * res * sin(p.x*FREQ + drift);

    float bandFillTh = max(BAND_THICK, 1e-4);
    float bandAmt    = 1e-4 * BAND_FILL * inten;
    vec3 num = vec3(0.0), den = vec3(0.0);
    for(int s = 0; s < 4; s++){
        vec3 hue = mix(vec3(1.0), spectral4(s), res);
        den += hue;
        float ab = mix(-AB, AB, float(s)/3.0);
        float yL = A2 * env * res * sin(p.x*ABER_FREQ + drift + ab);
        float d   = mix(dUnres, abs(p.y - yL), res);
        float lor = mix(1.0/(1.0 + (0.02*d)*(0.02*d)), 1.0, res);
        float line = inten / (sqrt(d*d + soft*soft) + th);
        float lo = min(yMain, yL), hi = max(yMain, yL);
        float dBand = max(0.0, max(p.y - hi, lo - p.y));
        float band  = bandAmt / (dBand + bandFillTh);
        num += hue * lor * (line + band);
    }
    vec3 col = num / den;

    float dM    = mix(dUnres, abs(p.y - yMain), res);
    float lorM  = mix(1.0/(1.0 + (0.02*dM)*(0.02*dM)), 1.0, res);
    float boost = (1.0 - res) * (14.0*low + 4.0);
    col += 0.5 * inten * (lorM + boost) / (sqrt(dM*dM + soft*soft) + th);

    col = pow(max(col, 0.0), vec3(1.5));
    float emT = clamp((abs(yScreen) - 1.0 + EDGE_INSET) / (-max(EDGE_MASK, 1e-4)), 0.0, 1.0);
    float em  = emT*emT*(3.0 - 2.0*emT);
    float gauss = exp(-pow(xN*FALLOFF, 2.0));
    col *= mix(1.0, em*gauss, res);
    col *= res;
    float a = clamp(max(col.r, max(col.g, col.b)), 0.0, 1.0);
    fragColor = vec4(col * a, a);
}
void main(){ mainImage(gl_FragColor, gl_FragCoord.xy); }
`
  );

  // shaders/siri-orb.ts
  var SIRI_ORB_FRAGMENT_SHADER = (
    /* glsl */
    `
precision highp float;
uniform vec2 iResolution; uniform float iTime;
uniform float uSpeed; uniform float uScale;
const float TAU = 6.28318530718;
const int   N   = 6;
const float SMOOTH_K = 0.08;
const float INTENSITY  = 0.0025;
const float FALLOFF_P  = 1.35;
const float FADE_START = 0.02;
const float FADE_END   = 0.56;
const float ABERR = 0.005;
const vec3  SPECTRAL = vec3(0.0, 0.5, 1.0) * ABERR;
const float HUE_SPEED = 0.06;
const float COLOR_K   = 0.5;
const float SAT       = 0.01;
const float HUE_SPAN  = 0.667;
const float MERGE_PERIOD = 6.0;
const float T_MOVE   = 1.25;
const float STAGGER  = 0.33;
const float HOLD     = 0.0;
const float W = 4.6;
const float L = 3.2;
const float PIERCE  = 0.12;
const float RECOIL  = 0.035;
const float REC_LAG = 0.11;
const float GATHER_PERIOD = 12.0;
const float GATHER_START  = 9.2;
const float GATHER_HOLD   = 0.8;
const float GATHER_R      = 0.008;
const float GATHER_DIM    = 0.85;
const float GATHER_IN     = 1.8;
const float GATHER_IN_L   = 7.5;
const float BURST_W = 6.5;
const float BURST_L = 4.0;
const float CHARGE_T     = 0.30;
const float CHARGE_SHRK  = 0.18;
const float CHARGE_GLOW  = 0.35;
const float FLASH_GAIN   = 1.2;
const float FLASH_DECAY  = 7.0;

float hash11(float n){ return fract(sin(n*127.1 + 311.7)*43758.5453); }
float settleWL(float tau, float w, float l){
    if(tau <= 0.0) return 0.0;
    return 1.0 - exp(-l*tau)*cos(w*tau);
}
float settle(float tau){ return settleWL(tau, W, L); }
float settleCrit(float tau, float l){
    if(tau <= 0.0) return 0.0;
    return 1.0 - exp(-l*tau)*(1.0 + l*tau);
}
float smin(float a, float b, float k){
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h*h*k*0.25;
}
vec3 hue2rgb(float h){
    h = fract(h);
    float r = clamp(abs(h*6.0 - 3.0) - 1.0, 0.0, 1.0);
    float g = clamp(2.0 - abs(h*6.0 - 2.0), 0.0, 1.0);
    float b = clamp(2.0 - abs(h*6.0 - 4.0), 0.0, 1.0);
    return vec3(r, g, b);
}
float dotR(float fi, float seed, float t){
    return 0.036 + 0.010*sin(t*1.3 + seed*TAU) + 0.005*sin(t*2.4 + fi*1.3);
}
float dotSD(vec2 p, vec2 pos, float r, float t, float fi, float shapeDamp){
    vec2 d = p - pos;
    float sq = 0.075 * (0.5 + 0.5*sin(t*0.9 + fi*2.0)) * shapeDamp;
    float ca = cos(t*0.35 + fi), sa = sin(t*0.35 + fi);
    d = mat2(ca,-sa,sa,ca) * d;
    d *= vec2(1.0+sq, 1.0-sq);
    return length(d) - r;
}
vec3 scene(vec2 p, float t){
    float k  = floor(t/MERGE_PERIOD);
    float u  = fract(t/MERGE_PERIOD);
    float te = u * MERGE_PERIOD;
    float tg = mod(t, GATHER_PERIOD);
    float g  = settleCrit((tg - GATHER_START) * GATHER_IN, GATHER_IN_L)
             - settleWL(tg - GATHER_START - GATHER_HOLD, BURST_W, BURST_L);
    float gC = clamp(g, 0.0, 1.0);
    float tb     = tg - (GATHER_START + GATHER_HOLD);
    float charge = smoothstep(-CHARGE_T, 0.0, min(tb, 0.0)) * gC;
    float flash  = tb > 0.0 ? exp(-tb * FLASH_DECAY) : 0.0;
    float gBright = mix(1.0, GATHER_DIM, gC) * (1.0 + CHARGE_GLOW*charge + FLASH_GAIN*flash);
    vec3  total3 = vec3(1e5);
    vec3  cAcc   = vec3(0.0);
    float wAcc   = 1e-6;
    for(int i=0; i<N; i++){
        float fi   = float(i);
        float seed = hash11(fi);
        float ang = fi/float(N)*TAU + t*0.35;
        vec2 dir  = vec2(cos(ang), sin(ang));
        float R = 0.17 + 0.010*sin(t*1.0) + 0.007*sin(t*1.3 + seed*TAU);
        float pairId   = mod(fi, 3.0);
        float moverLow = mod(k + pairId, 2.0);
        float isMover  = (fi < 2.5) ? step(moverLow, 0.5) : step(0.5, moverLow);
        float goStart  = pairId * STAGGER;
        float retStart = 3.0*STAGGER + HOLD + pairId * STAGGER;
        float m   = (settle(te - goStart)           - settle(te - retStart))           * isMover;
        float rec = (settle(te - goStart - REC_LAG) - settle(te - retStart - REC_LAG)) * (1.0 - isMover);
        float rSelf = dotR(fi, seed, t);
        rSelf = mix(rSelf, 0.036, gC);
        rSelf *= 1.0 - CHARGE_SHRK * charge;
        float fj    = mod(fi + 3.0, 6.0);
        float rPart = dotR(fj, hash11(fj), t);
        float deep   = -(R + RECOIL) - PIERCE * rPart;
        float radial = mix(R, deep, m) + RECOIL * rec;
        radial = mix(radial, GATHER_R, g);
        vec2  pos    = radial * dir;
        float sdR = dotSD(p - SPECTRAL.r*dir, pos, rSelf, t, fi, 1.0 - gC);
        float sdG = dotSD(p - SPECTRAL.g*dir, pos, rSelf, t, fi, 1.0 - gC);
        float sdB = dotSD(p - SPECTRAL.b*dir, pos, rSelf, t, fi, 1.0 - gC);
        total3 = vec3( smin(total3.r, sdR, SMOOTH_K),
                       smin(total3.g, sdG, SMOOTH_K),
                       smin(total3.b, sdB, SMOOTH_K) );
        float hue = fract(fi/float(N) + t*HUE_SPEED) * HUE_SPAN;
        vec3 dotCol = mix(vec3(1.0), hue2rgb(hue), SAT);
        float w = exp(-sdG * COLOR_K);
        cAcc += w * dotCol;
        wAcc += w;
    }
    vec3 sd3    = max(total3, vec3(0.0)) + 1e-4;
    vec3 core3  = clamp(INTENSITY / pow(sd3, vec3(FALLOFF_P)), 0.0, 1.0);
    vec3 edge3  = 1.0 - smoothstep(vec3(FADE_START), vec3(FADE_END), sd3);
    vec3 bright = core3 * edge3 * gBright;
    return bright * (cAcc / wAcc);
}
void mainImage(out vec4 fragColor, in vec2 fragCoord){
    vec2 res = iResolution.xy;
    vec2 p = (2.0*fragCoord - res) / min(res.x, res.y);
    float t = iTime * uSpeed;
    p /= 1.0 + 0.03*sin(t*1.0);
    p /= uScale;
    vec3 col = scene(p, t);
    col *= 1.0 + 0.05*sin(t*1.0 + 1.0);
    col = pow(col, vec3(1.0/1.2));
    col = min(col, 1.0);
    float n = fract(sin(dot(fragCoord, vec2(12.9898,78.233)))*43758.5453);
    col += (n - 0.5)/255.0;
    float a = clamp(max(col.r, max(col.g, col.b)), 0.0, 1.0);
    fragColor = vec4(col * a, a);
}
void main(){ mainImage(gl_FragColor, gl_FragCoord.xy); }
`
  );

  // shaders/separable-blur.ts
  function generateGaussianKernel1D(tapCount) {
    if (tapCount <= 1) return [{ offset: 0, weight: 1 }];
    const taps = [];
    const half = Math.floor(tapCount / 2);
    const maxOffset = 3;
    let totalW = 0;
    for (let i = 0; i < tapCount; i++) {
      const t = tapCount % 2 === 1 ? i - half : i - half + 0.5;
      const offset = t / half * maxOffset;
      const w = Math.exp(-0.5 * offset * offset);
      taps.push({ offset, weight: w });
      totalW += w;
    }
    if (totalW > 0) {
      for (const t of taps) t.weight /= totalW;
    }
    return taps;
  }
  function generateSeparableBlurShader(tapCount, direction) {
    const kernel = generateGaussianKernel1D(tapCount);
    const isH = direction === "horizontal";
    const dirVec = isH ? "vec2(1.0, 0.0)" : "vec2(0.0, 1.0)";
    let sampleCode = "";
    if (kernel.length === 1) {
      sampleCode = `    gl_FragColor = texture2D(uTexture, uv);
`;
    } else {
      sampleCode = `    vec3 rgbSum = vec3(0.0);
    float rgbW = 0.0;
`;
      for (const t of kernel) {
        const off = t.offset.toFixed(6);
        const w = t.weight.toFixed(8);
        sampleCode += `    { vec4 s = texture2D(uTexture, uv + ${dirVec} * ${off} * pxToUv); float aw = s.a * ${w}; rgbSum += s.rgb * aw; rgbW += aw; }
`;
      }
      sampleCode += `    float origA = texture2D(uTexture, uv).a;
    gl_FragColor = vec4(rgbW > 0.001 ? rgbSum / rgbW : vec3(0.0), origA);
`;
    }
    return (
      /* glsl */
      `
precision highp float;

uniform sampler2D uTexture;
uniform vec2 uTexSize;
uniform float uRadius;

void main() {
    vec2 uv = vec2(gl_FragCoord.x / uTexSize.x, gl_FragCoord.y / uTexSize.y);
    if (uRadius < 0.5) {
        gl_FragColor = texture2D(uTexture, uv);
        return;
    }
    vec2 pxToUv = vec2(uRadius / uTexSize.x, uRadius / uTexSize.y);
${sampleCode}}
`
    );
  }
  function computeBlur1DTapCount(blurRadiusPx) {
    if (blurRadiusPx < 0.5) return 1;
    const sigma = blurRadiusPx * 0.57735 + 0.5;
    const n = 2 * Math.ceil(3 * sigma) + 1;
    return Math.min(33, Math.max(1, n));
  }
  function generateHighlightBlurKernel1D(tapCount) {
    if (tapCount <= 1) return [{ offset: 0, weight: 1 }];
    const taps = [];
    const half = Math.floor(tapCount / 2);
    let totalW = 0;
    for (let i = 0; i < tapCount; i++) {
      const offset = i - half;
      const w = Math.exp(-0.5 * offset * offset);
      taps.push({ offset, weight: w });
      totalW += w;
    }
    if (totalW > 0) {
      for (const t of taps) t.weight /= totalW;
    }
    return taps;
  }
  function generateHighlightBlurShader(tapCount, direction) {
    const kernel = generateHighlightBlurKernel1D(tapCount);
    const isH = direction === "horizontal";
    const dirVec = isH ? "vec2(1.0, 0.0)" : "vec2(0.0, 1.0)";
    let sampleCode = "";
    if (kernel.length === 1) {
      sampleCode = `    gl_FragColor = texture2D(uTexture, uv);
`;
    } else {
      sampleCode = `    float aSum = 0.0;
`;
      for (const t of kernel) {
        const off = t.offset.toFixed(6);
        const w = t.weight.toFixed(8);
        sampleCode += `    aSum += texture2D(uTexture, uv + ${dirVec} * ${off} * pxToUv).a * ${w};
`;
      }
      sampleCode += `    gl_FragColor = vec4(0.0, 0.0, 0.0, aSum);
`;
    }
    return (
      /* glsl */
      `
precision highp float;

uniform sampler2D uTexture;
uniform vec2 uTexSize;
uniform float uRadius;  // Gaussian sigma in pixels (Android BlurMaskFilter semantics)

void main() {
    vec2 uv = vec2(gl_FragCoord.x / uTexSize.x, gl_FragCoord.y / uTexSize.y);
    if (uRadius < 0.01) {
        gl_FragColor = texture2D(uTexture, uv);
        return;
    }
    // pxToUv converts a pixel offset to a UV offset. offset (in \u03C3 units) *
    // sigma_px = pixel offset; / uTexSize = UV offset.
    vec2 pxToUv = vec2(uRadius / uTexSize.x, uRadius / uTexSize.y);
${sampleCode}}
`
    );
  }
  function computeHighlightBlurTapCount(sigmaPx) {
    if (sigmaPx < 0.01) return 1;
    const n = 2 * Math.ceil(3 * sigmaPx) + 1;
    return Math.min(33, Math.max(3, n));
  }

  // renderer/gl-utils.ts
  function uploadCanvasAsPixels(gl, canvas, premultiply = false) {
    const w = Math.max(1, canvas.width);
    const h = Math.max(1, canvas.height);
    const ctx = canvas.getContext("2d", { willReadFrequently: true });
    if (!ctx) {
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, canvas);
      return;
    }
    const img = ctx.getImageData(0, 0, w, h);
    const data = img.data;
    if (premultiply) {
      for (let i = 0; i < data.length; i += 4) {
        const a = data[i + 3] / 255;
        data[i] = data[i] * a | 0;
        data[i + 1] = data[i + 1] * a | 0;
        data[i + 2] = data[i + 2] * a | 0;
      }
    }
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, data);
  }
  function compileShader(gl, type, src) {
    const sh = gl.createShader(type);
    gl.shaderSource(sh, src);
    gl.compileShader(sh);
    if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) {
      const log = gl.getShaderInfoLog(sh);
      gl.deleteShader(sh);
      throw new Error("Shader compile error: " + log);
    }
    return sh;
  }
  function createProgram(gl, vsSrc, fsSrc) {
    const vs = compileShader(gl, gl.VERTEX_SHADER, vsSrc);
    const fs = compileShader(gl, gl.FRAGMENT_SHADER, fsSrc);
    const p = gl.createProgram();
    gl.attachShader(p, vs);
    gl.attachShader(p, fs);
    gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) {
      const log = gl.getProgramInfoLog(p);
      gl.deleteProgram(p);
      throw new Error("Program link error: " + log);
    }
    return p;
  }
  function wrapText(ctx, text, maxW) {
    const words = text.split(/\s+/);
    const lines = [];
    let cur = "";
    for (const word of words) {
      const test = cur ? cur + " " + word : word;
      if (ctx.measureText(test).width <= maxW || !cur) {
        cur = test;
      } else {
        lines.push(cur);
        cur = word;
      }
    }
    if (cur) lines.push(cur);
    return lines;
  }
  function easeIn(t) {
    if (t <= 0) return 0;
    if (t >= 1) return 1;
    const x1 = 0.42, y1 = 0, x2 = 1, y2 = 1;
    let s = t;
    for (let i = 0; i < 8; i++) {
      const xs = 3 * (1 - s) * (1 - s) * s * x1 + 3 * (1 - s) * s * s * x2 + s * s * s;
      const dxs = 3 * (1 - s) * (1 - s) * x1 + 6 * (1 - s) * s * (x2 - x1) + 3 * s * s * (1 - x2);
      if (Math.abs(xs - t) < 1e-3) break;
      if (Math.abs(dxs) < 1e-6) break;
      s -= (xs - t) / dxs;
      s = Math.max(0, Math.min(1, s));
    }
    return 3 * (1 - s) * (1 - s) * s * y1 + 3 * (1 - s) * s * s * y2 + s * s * s;
  }

  // renderer/inner-shadow-cache.ts
  var MAX_CACHE_SIZE = 32;
  function gooseBuildInnerShadowMaskKey(shadowIndex, params) {
    return [
      "is",
      shadowIndex,
      params.useG2 ? "g2" : "rr",
      params.w.toFixed(3),
      params.h.toFixed(3),
      params.radius.toFixed(3),
      params.offsetX.toFixed(3),
      params.offsetY.toFixed(3),
      params.blurSigma.toFixed(3),
      params.margin,
      Math.ceil(params.w + 2 * params.margin),
      // maskW
      Math.ceil(params.h + 2 * params.margin),
      // maskH
      `ss${params.supersample}`
    ].join(":");
  }
  function gooseGetOrCreateMaskEntry(cache, gl, key, maskW, maskH) {
    let entry = cache.get(key);
    if (entry) return entry;
    const tex = gl.createTexture();
    if (!tex) throw new Error("WebGL texture allocation failed");
    entry = { tex, w: maskW, h: maskH, ready: false };
    cache.set(key, entry);
    if (cache.size > MAX_CACHE_SIZE) {
      const oldestKey = cache.keys().next().value;
      if (oldestKey && oldestKey !== key) {
        const oldest = cache.get(oldestKey);
        if (oldest) gl.deleteTexture(oldest.tex);
        cache.delete(oldestKey);
      }
    }
    return entry;
  }
  function gooseUploadMaskTexture(gl, entry, result) {
    gl.bindTexture(gl.TEXTURE_2D, entry.tex);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
    uploadCanvasAsPixels(gl, result.canvas);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    entry.ready = true;
  }
  function gooseDestroyInnerShadowCache(gl, cache) {
    for (const entry of cache.values()) {
      gl.deleteTexture(entry.tex);
    }
    cache.clear();
  }

  // renderer/methods-fbo.ts
  var fboMethods = {
    gooseFBO(w, h) {
      const gl = this.gl;
      const tex = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, tex);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      const fb = gl.createFramebuffer();
      gl.bindFramebuffer(gl.FRAMEBUFFER, fb);
      gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, tex, 0);
      gl.bindFramebuffer(gl.FRAMEBUFFER, null);
      return { fb, tex };
    },
    gooseResizeFBO(w, h) {
      if (this.fboW === w && this.fboH === h && this.fboA && this.fboB) return;
      const gl = this.gl;
      if (this.fboA) gl.deleteFramebuffer(this.fboA);
      if (this.fboATex) gl.deleteTexture(this.fboATex);
      if (this.fboB) gl.deleteFramebuffer(this.fboB);
      if (this.fboBTex) gl.deleteTexture(this.fboBTex);
      const a = this.gooseFBO(w, h);
      const b = this.gooseFBO(w, h);
      this.fboA = a.fb;
      this.fboATex = a.tex;
      this.fboB = b.fb;
      this.fboBTex = b.tex;
      if (this.tabsBackdropFbo) gl.deleteFramebuffer(this.tabsBackdropFbo);
      if (this.tabsBackdropTex) gl.deleteTexture(this.tabsBackdropTex);
      const tb = this.gooseFBO(w, h);
      this.tabsBackdropFbo = tb.fb;
      this.tabsBackdropTex = tb.tex;
      this.tabsBackdropDirty = true;
      if (this.gpElementFbo) gl.deleteFramebuffer(this.gpElementFbo);
      if (this.gpElementTex) gl.deleteTexture(this.gpElementTex);
      if (this.blurFboA) gl.deleteFramebuffer(this.blurFboA);
      if (this.blurFboATex) gl.deleteTexture(this.blurFboATex);
      if (this.blurFboB) gl.deleteFramebuffer(this.blurFboB);
      if (this.blurFboBTex) gl.deleteTexture(this.blurFboBTex);
      const ge = this.gooseFBO(w, h);
      const ba = this.gooseFBO(w, h);
      const bb = this.gooseFBO(w, h);
      this.gpElementFbo = ge.fb;
      this.gpElementTex = ge.tex;
      this.blurFboA = ba.fb;
      this.blurFboATex = ba.tex;
      this.blurFboB = bb.fb;
      this.blurFboBTex = bb.tex;
      if (this.highlightMaskFbo) gl.deleteFramebuffer(this.highlightMaskFbo);
      if (this.highlightMaskTex) gl.deleteTexture(this.highlightMaskTex);
      const hm = this.gooseFBO(w, h);
      this.highlightMaskFbo = hm.fb;
      this.highlightMaskTex = hm.tex;
      if (this.dialogBackdropFbo) gl.deleteFramebuffer(this.dialogBackdropFbo);
      if (this.dialogBackdropTex) gl.deleteTexture(this.dialogBackdropTex);
      const db = this.gooseFBO(w, h);
      this.dialogBackdropFbo = db.fb;
      this.dialogBackdropTex = db.tex;
      this.dialogBackdropKey = null;
      this.fboW = w;
      this.fboH = h;
    },
    /** Bind an FBO as the gooseRender target, set viewport to its size. */
    gooseBindFBO(fb) {
      const gl = this.gl;
      gl.bindFramebuffer(gl.FRAMEBUFFER, fb);
      gl.viewport(0, 0, this.fboW, this.fboH);
    },
    /** Fullscreen copy pass: copy src texture to the currently-bound FBO.
     *  Used for ping-pong blits (fboA → fboB) and the final blit to the
     *  default framebuffer (fboA → canvas). The caller must have already
     *  bound the destination FBO. */
    gooseCopy(srcTex) {
      const gl = this.gl;
      gl.useProgram(this.copyProgram);
      gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
      gl.enableVertexAttribArray(this.aPosLocCp);
      gl.vertexAttribPointer(this.aPosLocCp, 2, gl.FLOAT, false, 0, 0);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, srcTex);
      gl.uniform1i(this.uCp["uTexture"], 0);
      gl.uniform2f(this.uCp["uCanvasSize"], this.fboW, this.fboH);
      gl.disable(gl.BLEND);
      gl.drawArrays(gl.TRIANGLES, 0, 6);
    },
    /** Fullscreen solid-color fill — used when backgroundColor is set
     *  (e.g. black for the Home page). The caller must have already bound
     *  the destination FBO. */
    gooseFill(r, g, b, a) {
      const gl = this.gl;
      gl.useProgram(this.solidFillProgram);
      gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
      gl.enableVertexAttribArray(this.aPosLocSf);
      gl.vertexAttribPointer(this.aPosLocSf, 2, gl.FLOAT, false, 0, 0);
      gl.uniform4f(this.uSf["uColor"], r, g, b, a);
      gl.disable(gl.BLEND);
      gl.drawArrays(gl.TRIANGLES, 0, 6);
    },
    /** Fullscreen colorControls pass — copies srcTex to the bound FBO applying
     *  brightness/contrast/saturation. Caller must bind the destination FBO. */
    gooseColorCtrl(srcTex, brightness, contrast, saturation) {
      const gl = this.gl;
      gl.useProgram(this.colorControlsProgram);
      gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
      gl.enableVertexAttribArray(this.aPosLocCc);
      gl.vertexAttribPointer(this.aPosLocCc, 2, gl.FLOAT, false, 0, 0);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, srcTex);
      gl.uniform1i(this.uCc["uTexture"], 0);
      gl.uniform2f(this.uCc["uTexSize"], this.fboW, this.fboH);
      gl.uniform1f(this.uCc["uBrightness"], brightness);
      gl.uniform1f(this.uCc["uContrast"], contrast);
      gl.uniform1f(this.uCc["uSaturation"], saturation);
      gl.disable(gl.BLEND);
      gl.drawArrays(gl.TRIANGLES, 0, 6);
    }
  };

  // renderer/continuous-curve.ts
  var SQRT_2 = 1.4142135623730951;
  var FRAC_PI_4 = 0.7853981633974483;
  var FRAC_1_SQRT_2 = 0.7071067811865476;
  function solveCubicSingle(a, b, c, d) {
    const f = (3 * c / a - b * b / (a * a)) / 3;
    const g = (2 * b * b * b / (a * a * a) - 9 * b * c / (a * a) + 27 * d / a) / 27;
    const h = g * g / 4 + f * f * f / 27;
    const sqrtH = Math.sqrt(h);
    return Math.cbrt(-g / 2 + sqrtH) + Math.cbrt(-g / 2 - sqrtH) - b / (3 * a);
  }
  function solveDepressedQuarticSingle(p, q, r) {
    const b = -p / 2;
    const c = -r;
    const d = r * p / 2 - q * q / 8;
    const f = (3 * c - b * b) / 3;
    const g = (2 * b * b * b - 9 * b * c + 27 * d) / 27;
    const rVal = Math.sqrt(-f * f * f / 27);
    const phi = Math.acos(-g / (2 * rVal));
    const y = 2 * Math.sqrt(-f / 3) * Math.cos(phi / 3);
    const z = y - b / 3;
    const u = Math.sqrt(2 * z - p);
    return (u - Math.sqrt(u * u - 4 * (z + q / (2 * u)))) / 2;
  }
  var ContinuousCurvatureRoundedRectangleCornerBuilder = class {
    constructor(extendedFraction = 2 / 3, arcFraction = 0.5) {
      __publicField(this, "extendedFraction");
      __publicField(this, "arcFraction");
      __publicField(this, "theta");
      __publicField(this, "cos");
      __publicField(this, "sin");
      __publicField(this, "cot");
      __publicField(this, "cos2");
      __publicField(this, "sin2");
      __publicField(this, "cos3");
      __publicField(this, "sin3");
      __publicField(this, "k0");
      __publicField(this, "k1");
      __publicField(this, "k2");
      __publicField(this, "k3");
      this.extendedFraction = extendedFraction;
      this.arcFraction = arcFraction;
      this.theta = (1 - arcFraction) * FRAC_PI_4;
      this.cos = Math.cos(this.theta);
      this.sin = Math.sin(this.theta);
      this.cot = 1 / Math.tan(this.theta);
      this.cos2 = this.cos * this.cos;
      this.sin2 = this.sin * this.sin;
      this.cos3 = this.cos2 * this.cos;
      this.sin3 = this.sin2 * this.sin;
      const cos = this.cos;
      const sin = this.sin;
      const cot = this.cot;
      const cos2 = this.cos2;
      const sin2 = this.sin2;
      const cos3 = this.cos3;
      const sin3 = this.sin3;
      this.k0 = 27 * (SQRT_2 - 6 * cos + 6 * SQRT_2 * cos2 - 4 * cos3) * cot + 2 * sin * (-9 + 2 * (SQRT_2 - 2 * sin) * sin3 + 2 * SQRT_2 * cos * (9 + sin2) - 2 * cos2 * (9 + 2 * sin2));
      this.k1 = -81 * (-2 + SQRT_2 + 4 * (-1 + SQRT_2) * cos + 2 * (-2 + SQRT_2) * cos2) * cot - 4 * sin * (-9 + 9 * SQRT_2 + SQRT_2 * sin3 + (-2 + SQRT_2) * cos * (9 + sin2));
      this.k2 = 9 * (9 * (-4 + 3 * SQRT_2 + (-6 + 4 * SQRT_2) * cos) * cot + (-6 + 4 * SQRT_2) * sin);
      this.k3 = 27 * (10 - 7 * SQRT_2) * cot;
    }
    buildEvenCornerBezierPoints(t) {
      const k = this.extendedFraction * t;
      const kappa = solveCubicSingle(this.k3, this.k2, this.k1 + 8 * -k * this.sin3 * this.sin, this.k0);
      const x3 = FRAC_1_SQRT_2 + (-FRAC_1_SQRT_2 + this.sin) / kappa;
      const y3 = 1 - FRAC_1_SQRT_2 + (FRAC_1_SQRT_2 - this.cos) / kappa;
      const x2 = x3 - y3 * this.cot;
      const x1 = x2 - 1.5 * kappa * y3 * y3 / this.sin3;
      const x0 = -k;
      const x6 = 1 - y3;
      const y6 = 1 - x3;
      const y7 = 1 - x2;
      const y8 = 1 - x1;
      const y9 = 1 - x0;
      const a = 1.5 * kappa;
      const g = this.cos2 - this.sin2;
      const x36 = x6 - x3;
      const y36 = y6 - y3;
      const c = -(this.cos * y36 - this.sin * x36);
      const lambda = (-g + Math.sqrt(g * g - 4 * a * c)) / (2 * a);
      const x4 = x3 + lambda * this.cos;
      const y4 = y3 + lambda * this.sin;
      const x5 = x6 - lambda * this.sin;
      const y5 = y6 - lambda * this.cos;
      return [x0, 0, x1, 0, x2, 0, x3, y3, x4, y4, x5, y5, x6, y6, 1, y7, 1, y8, 1, y9];
    }
    buildUnevenCornerBezierPoints(tH, tV) {
      const kH = this.extendedFraction * tH;
      const kV = this.extendedFraction * tV;
      const kappa3 = solveCubicSingle(this.k3, this.k2, this.k1 + 8 * -kH * this.sin3 * this.sin, this.k0);
      const kappa6 = solveCubicSingle(this.k3, this.k2, this.k1 + 8 * -kV * this.sin3 * this.sin, this.k0);
      const x3 = FRAC_1_SQRT_2 + (-FRAC_1_SQRT_2 + this.sin) / kappa3;
      const y3 = 1 - FRAC_1_SQRT_2 + (FRAC_1_SQRT_2 - this.cos) / kappa3;
      const x2 = x3 - y3 * this.cot;
      const x1 = x2 - 1.5 * kappa3 * y3 * y3 / this.sin3;
      const x0 = -kH;
      const x3p = FRAC_1_SQRT_2 + (-FRAC_1_SQRT_2 + this.sin) / kappa6;
      const y3p = 1 - FRAC_1_SQRT_2 + (FRAC_1_SQRT_2 - this.cos) / kappa6;
      const x2p = x3p - y3p * this.cot;
      const x1p = x2p - 1.5 * kappa6 * y3p * y3p / this.sin3;
      const x0p = -kV;
      const x6 = 1 - y3p;
      const y6 = 1 - x3p;
      const y7 = 1 - x2p;
      const y8 = 1 - x1p;
      const y9 = 1 - x0p;
      const a = 1.5 * kappa3;
      const b = 1.5 * kappa6;
      const g = this.cos2 - this.sin2;
      const x36 = x6 - x3;
      const y36 = y6 - y3;
      const c = -(this.cos * y36 - this.sin * x36);
      const d = this.sin * y36 - this.cos * x36;
      const p = 2 * (d / b);
      const q = g * g * g / (a * b * b);
      const r = (a * d * d + c * g * g) / (a * b * b);
      const lambda6 = solveDepressedQuarticSingle(p, q, r);
      const lambda3 = (-d - b * lambda6 * lambda6) / g;
      const x4 = x3 + lambda3 * this.cos;
      const y4 = y3 + lambda3 * this.sin;
      const x5 = x6 - lambda6 * this.sin;
      const y5 = y6 - lambda6 * this.cos;
      return [x0, 0, x1, 0, x2, 0, x3, y3, x4, y4, x5, y5, x6, y6, 1, y7, 1, y8, 1, y9];
    }
    /** Returns 20 Bezier control point values (10 pairs) for one corner.
     *  tW = (w/2 - r) / r clamped to [0,1], tH = (h/2 - r) / r clamped to [0,1].
     *  These define 3 cubic Bezier segments forming the G2-continuous corner. */
    getCornerBezierPoints(tW, tV) {
      const i = tW === 0 ? 0 : tW === 1 ? 1 : -1;
      const j = tV === 0 ? 0 : tV === 1 ? 1 : -1;
      if (i >= 0 && j >= 0) {
        if (i === 0 && j === 0) return this.buildEvenCornerBezierPoints(0);
        if (i === 1 && j === 1) return this.buildEvenCornerBezierPoints(1);
        return this.buildUnevenCornerBezierPoints(i === 1 ? 1 : 0, j === 1 ? 1 : 0);
      }
      return this.buildUnevenCornerBezierPoints(
        Math.max(0, Math.min(1, tW)),
        Math.max(0, Math.min(1, tV))
      );
    }
  };
  function continuousCurvatureRoundedRectPath(ctx, w, h, radius) {
    const builder = new ContinuousCurvatureRoundedRectangleCornerBuilder();
    const r = radius;
    const tW = Math.max(0, Math.min(1, (w * 0.5 - r) / r));
    const tH = Math.max(0, Math.min(1, (h * 0.5 - r) / r));
    const p = builder.getCornerBezierPoints(tW, tH);
    if (p.length < 20) return new Path2D();
    const path = new Path2D();
    let x = w - r;
    let y = 0;
    path.moveTo(x + p[0] * r, y + p[1] * r);
    path.bezierCurveTo(x + p[2] * r, y + p[3] * r, x + p[4] * r, y + p[5] * r, x + p[6] * r, y + p[7] * r);
    path.bezierCurveTo(x + p[8] * r, y + p[9] * r, x + p[10] * r, y + p[11] * r, x + p[12] * r, y + p[13] * r);
    path.bezierCurveTo(x + p[14] * r, y + p[15] * r, x + p[16] * r, y + p[17] * r, x + p[18] * r, y + p[19] * r);
    x = w - r;
    y = h;
    path.lineTo(x + p[18] * r, y - p[19] * r);
    path.bezierCurveTo(x + p[16] * r, y - p[17] * r, x + p[14] * r, y - p[15] * r, x + p[12] * r, y - p[13] * r);
    path.bezierCurveTo(x + p[10] * r, y - p[11] * r, x + p[8] * r, y - p[9] * r, x + p[6] * r, y - p[7] * r);
    path.bezierCurveTo(x + p[4] * r, y - p[5] * r, x + p[2] * r, y - p[3] * r, x + p[0] * r, y - p[1] * r);
    x = r;
    y = h;
    path.lineTo(x - p[0] * r, y - p[1] * r);
    path.bezierCurveTo(x - p[2] * r, y - p[3] * r, x - p[4] * r, y - p[5] * r, x - p[6] * r, y - p[7] * r);
    path.bezierCurveTo(x - p[8] * r, y - p[9] * r, x - p[10] * r, y - p[11] * r, x - p[12] * r, y - p[13] * r);
    path.bezierCurveTo(x - p[14] * r, y - p[15] * r, x - p[16] * r, y - p[17] * r, x - p[18] * r, y - p[19] * r);
    x = r;
    y = 0;
    path.lineTo(x - p[18] * r, y + p[19] * r);
    path.bezierCurveTo(x - p[16] * r, y + p[17] * r, x - p[14] * r, y + p[15] * r, x - p[12] * r, y + p[13] * r);
    path.bezierCurveTo(x - p[10] * r, y + p[11] * r, x - p[8] * r, y + p[9] * r, x - p[6] * r, y + p[7] * r);
    path.bezierCurveTo(x - p[4] * r, y + p[5] * r, x - p[2] * r, y + p[3] * r, x - p[0] * r, y + p[1] * r);
    path.closePath();
    return path;
  }

  // renderer/continuous-mask.ts
  var maskCache = /* @__PURE__ */ new Map();
  function generateContinuousCurvatureMask(w, h, radius, dpr = 1) {
    const texSize = Math.min(1024, Math.max(256, Math.round(Math.max(w, h) * dpr * 2)));
    const key = `${w},${h},${radius},${texSize}`;
    const cached = maskCache.get(key);
    if (cached) return { tex: cached.tex, texSize };
    const maxDim = Math.max(w, h);
    const aspectW = w / maxDim;
    const aspectH = h / maxDim;
    const canvas = document.createElement("canvas");
    canvas.width = texSize;
    canvas.height = texSize;
    const ctx = canvas.getContext("2d");
    ctx.clearRect(0, 0, texSize, texSize);
    const margin = 4;
    const drawW = (texSize - 2 * margin) * aspectW;
    const drawH = (texSize - 2 * margin) * aspectH;
    const offsetX = (texSize - drawW) / 2;
    const offsetY = (texSize - drawH) / 2;
    const scale = drawW / w;
    const drawRadius = radius * scale;
    const path = continuousCurvatureRoundedRectPath(ctx, drawW, drawH, drawRadius);
    ctx.fillStyle = "white";
    ctx.translate(offsetX, offsetY);
    ctx.fill(path);
    ctx.translate(-offsetX, -offsetY);
    const imageData = ctx.getImageData(0, 0, texSize, texSize);
    const alpha = new Uint8Array(texSize * texSize);
    for (let i = 0; i < texSize * texSize; i++) {
      alpha[i] = imageData.data[i * 4 + 3];
    }
    const inside = new Float32Array(texSize * texSize);
    const outside = new Float32Array(texSize * texSize);
    const INF = 1e10;
    for (let i = 0; i < texSize * texSize; i++) {
      if (alpha[i] > 128) {
        inside[i] = 0;
        outside[i] = INF;
      } else {
        inside[i] = INF;
        outside[i] = 0;
      }
    }
    for (let y = 0; y < texSize; y++) {
      for (let x = 0; x < texSize; x++) {
        const idx = y * texSize + x;
        if (x > 0 && y > 1) {
          inside[idx] = Math.min(inside[idx], inside[idx - texSize - 1 - texSize] + 11);
          outside[idx] = Math.min(outside[idx], outside[idx - texSize - 1 - texSize] + 11);
        }
        if (x > 0) {
          inside[idx] = Math.min(inside[idx], inside[idx - 1] + 5);
          outside[idx] = Math.min(outside[idx], outside[idx - 1] + 5);
        }
        if (x > 0 && y > 0) {
          inside[idx] = Math.min(inside[idx], inside[idx - texSize - 1] + 7);
          outside[idx] = Math.min(outside[idx], outside[idx - texSize - 1] + 7);
        }
        if (y > 0) {
          inside[idx] = Math.min(inside[idx], inside[idx - texSize] + 5);
          outside[idx] = Math.min(outside[idx], outside[idx - texSize] + 5);
        }
        if (x < texSize - 1 && y > 0) {
          inside[idx] = Math.min(inside[idx], inside[idx - texSize + 1] + 7);
          outside[idx] = Math.min(outside[idx], outside[idx - texSize + 1] + 7);
        }
        if (x < texSize - 2 && y > 0) {
          inside[idx] = Math.min(inside[idx], inside[idx - texSize + 2] + 11);
          outside[idx] = Math.min(outside[idx], outside[idx - texSize + 2] + 11);
        }
      }
    }
    for (let y = texSize - 1; y >= 0; y--) {
      for (let x = texSize - 1; x >= 0; x--) {
        const idx = y * texSize + x;
        if (x < texSize - 1 && y < texSize - 2) {
          inside[idx] = Math.min(inside[idx], inside[idx + texSize + 1 + texSize] + 11);
          outside[idx] = Math.min(outside[idx], outside[idx + texSize + 1 + texSize] + 11);
        }
        if (x < texSize - 1) {
          inside[idx] = Math.min(inside[idx], inside[idx + 1] + 5);
          outside[idx] = Math.min(outside[idx], outside[idx + 1] + 5);
        }
        if (x < texSize - 1 && y < texSize - 1) {
          inside[idx] = Math.min(inside[idx], inside[idx + texSize + 1] + 7);
          outside[idx] = Math.min(outside[idx], outside[idx + texSize + 1] + 7);
        }
        if (y < texSize - 1) {
          inside[idx] = Math.min(inside[idx], inside[idx + texSize] + 5);
          outside[idx] = Math.min(outside[idx], outside[idx + texSize] + 5);
        }
        if (x > 0 && y < texSize - 1) {
          inside[idx] = Math.min(inside[idx], inside[idx + texSize - 1] + 7);
          outside[idx] = Math.min(outside[idx], outside[idx + texSize - 1] + 7);
        }
        if (x > 1 && y < texSize - 1) {
          inside[idx] = Math.min(inside[idx], inside[idx + texSize - 2] + 11);
          outside[idx] = Math.min(outside[idx], outside[idx + texSize - 2] + 11);
        }
      }
    }
    const refDist = drawRadius;
    const tex = new Uint8Array(texSize * texSize * 4);
    for (let i = 0; i < texSize * texSize; i++) {
      tex[i * 4] = alpha[i];
      const sd = (inside[i] - outside[i]) / 5;
      const normalized = Math.max(-1, Math.min(1, sd / refDist));
      tex[i * 4 + 1] = Math.round((normalized * 0.5 + 0.5) * 255);
      tex[i * 4 + 2] = 0;
      tex[i * 4 + 3] = 255;
    }
    maskCache.set(key, { tex, texSize });
    return { tex, texSize };
  }

  // renderer/methods-wallpaper.ts
  var wallpaperMethods = {
    /** Load the wallpaper image as a texture. */
    async gooseLoadWP(src) {
      const img = new Image();
      img.crossOrigin = "anonymous";
      await new Promise((resolve, reject) => {
        img.onload = () => resolve();
        img.onerror = () => reject(new Error("Failed to load wallpaper: " + src));
        img.src = src;
      });
      const gl = this.gl;
      if (this.wallpaperTexture) gl.deleteTexture(this.wallpaperTexture);
      const tex = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, tex);
      gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
      const w = img.naturalWidth;
      const h = img.naturalHeight;
      const isPOT = (w & w - 1) === 0 && (h & h - 1) === 0;
      if (isPOT) {
        gl.generateMipmap(gl.TEXTURE_2D);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
      } else {
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      }
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      this.wallpaperTexture = tex;
      this.wallpaperSize = [w || 1, h || 1];
      this.wallpaperReady = true;
      this.gooseReqRender();
    },
    /** Load the SDF texture (clock_sdf) for LockScreen glass. */
    async gooseLoadSDF(src) {
      const img = new Image();
      img.crossOrigin = "anonymous";
      await new Promise((resolve, reject) => {
        img.onload = () => resolve();
        img.onerror = () => reject(new Error("Failed to load SDF texture: " + src));
        img.src = src;
      });
      const gl = this.gl;
      if (this.sdfTexture) gl.deleteTexture(this.sdfTexture);
      const tex = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, tex);
      gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      this.sdfTexture = tex;
      this.sdfTextureSize = [img.naturalWidth || 1, img.naturalHeight || 1];
      this.sdfTextureReady = true;
      this.gooseReqRender();
    },
    /** Generate + upload a continuous-curvature SDF texture for the dialog
     *  card's capsule shape. The texture is cached by (w, h, radius); calling
     *  again with the same key is a no-op. The SDF encodes a G2-continuous
     *  Bezier rounded-rect path (faithful to kyant-shapes'
     *  ContinuousCurvatureRoundedRectangleCornerBuilder), normalized to [-1, 1]
     *  (negative inside, positive outside). Sampling it in the shader gives
     *  pixel-perfect squircle corners, vs the analytic sdRoundedRect which
     *  uses a circular arc approximation.
     *
     *  Texture format: RGBA, 256×256, LINEAR filtering, CLAMP_TO_EDGE.
     *  The R channel holds the normalized SDF (decoded as sample*2 - 1 in the
     *  shader); G and B mirror R; A = 255. */
    gooseLoadCSDF(w, h, radius) {
      const key = `${w},${h},${radius},${this.dpr}`;
      let entry = this.continuousSdfPool.get(key);
      if (!entry) {
        const { tex, texSize } = generateContinuousCurvatureMask(w, h, radius, this.dpr);
        const gl = this.gl;
        const texObj = gl.createTexture();
        gl.bindTexture(gl.TEXTURE_2D, texObj);
        gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
        gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, texSize, texSize, 0, gl.RGBA, gl.UNSIGNED_BYTE, tex);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
        entry = { tex: texObj, texSize };
        this.continuousSdfPool.set(key, entry);
        if (this.continuousSdfPool.size > 16) {
          const oldest = this.continuousSdfPool.keys().next().value;
          if (oldest) {
            const old = this.continuousSdfPool.get(oldest);
            if (old) gl.deleteTexture(old.tex);
            this.continuousSdfPool.delete(oldest);
          }
        }
      }
      this.continuousSdfTexture = entry.tex;
      this.continuousSdfTexSize = [entry.texSize, entry.texSize];
      this.continuousSdfKey = key;
    },
    /** Set canvas size (CSS pixels) + handle DPR.
     *  PERFORMANCE: DPR capped at 1.5 (was 2). On Retina displays (DPR=2),
     *  this reduces pixel count by 44% (4x → 2.25x) with minimal visual
     *  difference. The original Android app relies on hardware RenderEffect
     *  which is far cheaper per-pixel, so it can afford full DPR; our
     *  software shader pipeline cannot.
     */
    gooseResize(cssW, cssH) {
      if (this.dpr <= 0) {
        this.dpr = Math.min(window.devicePixelRatio || 1, 1.5);
      }
      const w = Math.round(cssW * this.dpr);
      const h = Math.round(cssH * this.dpr);
      if (this.canvas.width !== w || this.canvas.height !== h) {
        this.canvas.width = w;
        this.canvas.height = h;
        this.gl.viewport(0, 0, w, h);
        this.gooseResizeFBO(w, h);
      }
      for (const b of this.buttonConfigs) this.fgDirtyIds.add(b.id);
      this.cssWidth = cssW;
      this.cssHeight = cssH;
      this.gooseReqRender();
    }
  };

  // renderer/methods-scroll.ts
  var scrollMethods = {
    /** Total scrollable content height in CSS px (set by the React layer). */
    gooseContentH(h) {
      this.contentHeight = h;
      this.gooseClampY();
      this.gooseReqRender();
    },
    /**
     * Set the scroll offset directly (CSS px, positive = scrolled down).
     * Used during touch drag — the scroll position follows the finger with
     * no spring lag. Inertia velocity is reset to 0 (the finger is in control).
     * The value is clamped to [0, maxScroll].
     */
    gooseScrollY(y) {
      this.scrollVelocity = 0;
      this.scrollY = this.gooseClampScroll(y);
      this.gooseReqRender();
    },
    /**
     * Apply an inertia impulse to the scroll (CSS px / s). Used on touch
     * release — the drag velocity becomes the initial scroll velocity,
     * then exponentially decays. The renderer's animation loop applies
     * `scrollY += scrollVelocity * dt` each frame and decays the velocity.
     * No spring rebound at edges — scrolling just stops at the boundary.
     */
    gooseScrollV(v) {
      const MAX_VEL = 4e3;
      this.scrollVelocity = Math.max(-MAX_VEL, Math.min(MAX_VEL, v));
      this.gooseAnimStart();
    },
    /** Get current scroll offset (CSS px). */
    gooseGetScrollY() {
      return this.scrollY;
    },
    /** Get current scroll velocity (CSS px / s, for inertia). */
    gooseGetScrollV() {
      return this.scrollVelocity;
    },
    /** Clamp a scroll value to [0, maxScroll]. */
    gooseClampScroll(y) {
      const max = Math.max(0, this.contentHeight - this.cssHeight);
      if (y < 0) return 0;
      if (y > max) return max;
      return y;
    },
    /** Clamp current scrollY in place (called when content size changes). */
    gooseClampY() {
      this.scrollY = this.gooseClampScroll(this.scrollY);
    },
    /**
     * Set the background color override. If non-null, the renderer fills
     * the canvas with this color instead of drawing the wallpaper image.
     * Used for the Home page (black background) per the user's request.
     */
    gooseBG(color) {
      this.backgroundColor = color;
      this.gooseReqRender();
    },
    gooseGravAngle(angleRad) {
      if (this.gravityAngle === angleRad) return;
      this.gravityAngle = angleRad;
      this.gooseReqRender();
    }
  };

  // renderer/velocity-tracker.ts
  var MAX_SAMPLES = 20;
  var VelocityTracker1D = class {
    constructor() {
      __publicField(this, "samples", []);
    }
    resetTracking() {
      this.samples.length = 0;
    }
    addPosition(timeMillis, position) {
      this.samples.push({ t: timeMillis, p: position });
      if (this.samples.length > MAX_SAMPLES) {
        this.samples.shift();
      }
    }
    /**
     * Estimate velocity (units/second) at the latest sample using a
     * least-squares linear fit over samples within the last `windowMs`
     * (default 100ms, matching Compose's default cutoff).
     *
     * Returns 0 if fewer than 2 samples are in the window.
     */
    calculateVelocity(windowMs = 100) {
      const samples = this.samples;
      if (samples.length < 2) return 0;
      const now = samples[samples.length - 1].t;
      const cutoff = now - windowMs;
      let n = 0;
      let sumT = 0;
      let sumP = 0;
      let sumTT = 0;
      let sumTP = 0;
      for (let i = samples.length - 1; i >= 0; i--) {
        const s = samples[i];
        if (s.t < cutoff) break;
        const tt = (s.t - now) / 1e3;
        sumT += tt;
        sumP += s.p;
        sumTT += tt * tt;
        sumTP += tt * s.p;
        n++;
      }
      if (n < 2) return 0;
      const denom = n * sumTT - sumT * sumT;
      if (Math.abs(denom) < 1e-9) return 0;
      const b = (n * sumTP - sumT * sumP) / denom;
      return b;
    }
  };

  // renderer/methods-toggle.ts
  var toggleMethods = {
    /** Ensure a toggle group state exists, initialized to the given fraction.
     *  pressedScale / valueRangeSpan are only applied on first creation
     *  (or, for non-default values, re-applied on existing groups so tabs
     *  always get 78/56 and the correct span even if gooseTglTarget created
     *  the group first via the page.tsx toggleTargets sync). */
    gooseEnsureTgl(groupId, initialFraction, pressedScale = 1.5, valueRangeSpan = 1) {
      let st = this.toggleStates.get(groupId);
      if (!st) {
        st = {
          fraction: initialFraction,
          fractionVelocity: 0,
          targetFraction: initialFraction,
          pressProgress: 0,
          pressVelocity: 0,
          targetPress: 0,
          scaleX: 1,
          scaleXVelocity: 0,
          targetScaleX: 1,
          scaleY: 1,
          scaleYVelocity: 0,
          targetScaleY: 1,
          velocity: 0,
          velocityVelocity: 0,
          targetVelocity: 0,
          isDragging: false,
          trackVelocityAfterRelease: false,
          velocityTracker: new VelocityTracker1D(),
          lastFractionForVelocity: initialFraction,
          lastFractionTime: 0,
          pressedScale,
          valueRangeSpan,
          panelOffset: 0,
          panelOffsetVelocity: 0,
          targetPanelOffset: 0
        };
        this.toggleStates.set(groupId, st);
      } else {
        if (pressedScale !== 1.5) st.pressedScale = pressedScale;
        if (valueRangeSpan !== 1) st.valueRangeSpan = valueRangeSpan;
      }
      return st;
    },
    /**
     * Set the toggle's target fraction (0..1). Animates with critically
     * damped spring. Also triggers a quick press-and-release cycle to
     * match the original `animateToValue` behavior (which calls press()
     * + animateTo + release()).
     *
     * Used for tap-to-toggle: the React layer flips `toggleOn`, then calls
     * this method with the new target.
     *
     * NOTE: If the target is unchanged (e.g. React re-renders after a drag
     * end and pushes the same target back), this is a no-op — we don't
     * re-trigger the press animation. This prevents a feedback loop where
     * drag-end → setState → useEffect → gooseTglTarget would restart the
     * press animation that gooseTglDragEnd just played.
     */
    gooseTglTarget(groupId, target) {
      const st = this.gooseEnsureTgl(groupId, target);
      if (st.isDragging) return;
      if (st.targetFraction === target) return;
      st.targetFraction = target;
      st.trackVelocityAfterRelease = false;
      st.targetVelocity = 0;
      st.velocity = 0;
      st.velocityVelocity = 0;
      st.velocityTracker.resetTracking();
      if (st.targetPress === 0) {
        st.targetPress = 1;
        st.targetScaleX = st.pressedScale;
        st.targetScaleY = st.pressedScale;
      }
      this.gooseAnimStart();
    },
    /**
     * Begin a finger drag on a toggle group. Sets isDragging=true and
     * starts the press animation (scale → pressedScale, white overlay fades in).
     * The startFraction is recorded so drag deltas can be added to it.
     *
     * Faithful to DampedDragAnimation.press() which resets the VelocityTracker
     * (so samples from a previous gesture don't bleed into this one).
     */
    gooseTglDragStart(groupId, startFraction) {
      const st = this.gooseEnsureTgl(groupId, startFraction);
      st.isDragging = true;
      st.targetPress = 1;
      st.targetScaleX = st.pressedScale;
      st.targetScaleY = st.pressedScale;
      st.velocityTracker.resetTracking();
      st.targetVelocity = 0;
      st.velocity = 0;
      st.velocityVelocity = 0;
      this.gooseAnimStart();
    },
    /**
     * Update the toggle's target fraction based on finger movement.
     * The new target is computed as `startFraction + (currentX - startX) / dragWidth`,
     * clamped to [0, 1]. The animated fraction then springs toward this
     * target with critically damped spec — so the knob tracks the finger
     * with a tiny smooth lag (matches the original's `updateValue(fraction)`
     * which animates toward the latest fraction state).
     *
     * VELOCITY TRACKING happens in the animation loop (methods-animation.ts),
     * NOT here. Faithful to DampedDragAnimation.kt: the tracker is fed
     * (time, valueAnimation.value) inside the valueAnimation.animateTo
     * block's per-frame callback (updateVelocity). The tracker uses a
     * least-squares fit (Compose VelocityTracker) rather than a spike-prone
     * ΔtargetFraction/Δt difference.
     */
    gooseTglDrag(groupId, startFraction, currentX, startX, dragWidth) {
      const st = this.gooseEnsureTgl(groupId, startFraction);
      if (!st.isDragging) return;
      const delta = (currentX - startX) / Math.max(1, dragWidth);
      const newTarget = Math.max(0, Math.min(1, startFraction + delta));
      st.targetFraction = newTarget;
      this.gooseAnimStart();
    },
    /**
     * End a finger drag. Snaps the target to 0 or 1 based on the current
     * targetFraction (≥0.5 → 1, else 0). Returns the snapped value so the
     * React layer can sync its state.
     *
     * NOTE: We do NOT immediately release the press animation here. The
     * original `release()` waits for `value` to settle near `targetValue`
     * before animating press→0. Our animation loop's auto-release logic
     * handles this: when `isDragging === false` and `fraction` is within
     * 0.02 of `targetFraction`, it sets `targetPress = 0` and
     * `targetScaleX/Y = 1`. This gives a smooth "press stays until knob
     * settles, then releases" feel that matches the original.
     *
     * We also decay the velocity target to 0 (the drag is over).
     */
    gooseTglDragEnd(groupId) {
      const st = this.toggleStates.get(groupId);
      if (!st) return 0;
      st.isDragging = false;
      const finalTarget = st.targetFraction >= 0.5 ? 1 : 0;
      st.targetFraction = finalTarget;
      st.trackVelocityAfterRelease = true;
      this.gooseAnimStart();
      return finalTarget;
    },
    /**
     * End a finger drag on a SLIDER group. Unlike toggle (which snaps to 0/1),
     * a slider is a continuous (stepless) control — faithful to LiquidSlider.kt's
     * `onDragStopped = { if (didDrag) onValueChange(targetValue) }` which returns
     * the continuous targetValue WITHOUT snapping.
     *
     * Returns the continuous target fraction (0..1) so the React layer can sync
     * its state. The press animation auto-releases when the fraction settles.
     */
    gooseSldDragEnd(groupId) {
      const st = this.toggleStates.get(groupId);
      if (!st) return 0;
      st.isDragging = false;
      const finalTarget = st.targetFraction;
      st.trackVelocityAfterRelease = true;
      this.gooseAnimStart();
      return finalTarget;
    },
    /** Read the current animated fraction (0..1) for a toggle group. */
    gooseTglFrac(groupId) {
      var _a, _b;
      return (_b = (_a = this.toggleStates.get(groupId)) == null ? void 0 : _a.fraction) != null ? _b : 0;
    },
    /**
     * Set the fraction to an absolute value during a slider drag. Used by the
     * slider track drag handler so the knob jumps to the finger position and
     * follows it (absolute positioning, like a tap but continuous). This matches
     * the original LiquidSlider.kt track tap-to-position behavior, extended to
     * drag for better usability on a small knob.
     *
     * Unlike gooseTglTarget (which no-ops during isDragging), this directly
     * sets targetFraction so it works mid-drag.
     */
    gooseSldDragPos(groupId, fraction) {
      const st = this.toggleStates.get(groupId);
      if (!st) return;
      const clamped = Math.max(0, Math.min(1, fraction));
      if (st.targetFraction !== clamped) {
        st.targetFraction = clamped;
        this.gooseAnimStart();
      }
    },
    /** Read the current target fraction (0..1) for a toggle group. */
    gooseTglTargetGet(groupId) {
      var _a, _b;
      return (_b = (_a = this.toggleStates.get(groupId)) == null ? void 0 : _a.targetFraction) != null ? _b : 0;
    }
  };

  // renderer/spring.ts
  var gooseDP = 1;
  var SPRING_K = 300;
  var SPRING_DAMPING_RATIO = 0.5;
  var SPRING_OMEGA_N = Math.sqrt(SPRING_K);
  var SPRING_OMEGA_D = SPRING_OMEGA_N * Math.sqrt(1 - SPRING_DAMPING_RATIO * SPRING_DAMPING_RATIO);
  var SPRING_THRESHOLD = 5e-4;
  var TOGGLE_VALUE_K = 1e3;
  var TOGGLE_VALUE_OMEGA_N = Math.sqrt(TOGGLE_VALUE_K);
  var TOGGLE_SCALE_X_K = 250;
  var TOGGLE_SCALE_X_DAMPING_RATIO = 0.6;
  var TOGGLE_SCALE_X_OMEGA_N = Math.sqrt(TOGGLE_SCALE_X_K);
  var TOGGLE_SCALE_X_OMEGA_D = TOGGLE_SCALE_X_OMEGA_N * Math.sqrt(1 - TOGGLE_SCALE_X_DAMPING_RATIO * TOGGLE_SCALE_X_DAMPING_RATIO);
  var TOGGLE_SCALE_Y_K = 250;
  var TOGGLE_SCALE_Y_DAMPING_RATIO = 0.7;
  var TOGGLE_SCALE_Y_OMEGA_N = Math.sqrt(TOGGLE_SCALE_Y_K);
  var TOGGLE_SCALE_Y_OMEGA_D = TOGGLE_SCALE_Y_OMEGA_N * Math.sqrt(1 - TOGGLE_SCALE_Y_DAMPING_RATIO * TOGGLE_SCALE_Y_DAMPING_RATIO);
  var TOGGLE_VELOCITY_K = 300;
  var TOGGLE_VELOCITY_DAMPING_RATIO = 0.5;
  var TOGGLE_VELOCITY_OMEGA_N = Math.sqrt(TOGGLE_VELOCITY_K);
  var TOGGLE_VELOCITY_OMEGA_D = TOGGLE_VELOCITY_OMEGA_N * Math.sqrt(1 - TOGGLE_VELOCITY_DAMPING_RATIO * TOGGLE_VELOCITY_DAMPING_RATIO);
  function springStep1D(current, velocity, target, dt) {
    const x0 = current - target;
    const v0 = velocity;
    const decay = Math.exp(-SPRING_DAMPING_RATIO * SPRING_OMEGA_N * dt);
    const cosWd = Math.cos(SPRING_OMEGA_D * dt);
    const sinWd = Math.sin(SPRING_OMEGA_D * dt);
    const offset = x0 * decay * cosWd + (v0 + SPRING_DAMPING_RATIO * SPRING_OMEGA_N * x0) / SPRING_OMEGA_D * decay * sinWd;
    const b0 = (v0 + SPRING_DAMPING_RATIO * SPRING_OMEGA_N * x0) / SPRING_OMEGA_D;
    const newVel = -SPRING_DAMPING_RATIO * SPRING_OMEGA_N * offset + decay * (-x0 * SPRING_OMEGA_D * sinWd + b0 * SPRING_OMEGA_D * cosWd);
    return { current: target + offset, velocity: newVel };
  }
  function springStepCritical(current, velocity, target, dt, omegaN) {
    const x0 = current - target;
    const v0 = velocity;
    const decay = Math.exp(-omegaN * dt);
    const offset = x0 * decay + (v0 + omegaN * x0) * dt * decay;
    const newVel = -omegaN * x0 * decay + (v0 + omegaN * x0) * (decay - omegaN * dt * decay);
    return { current: target + offset, velocity: newVel };
  }
  function springStepUnderdamped(current, velocity, target, dt, omegaN, dampingRatio) {
    const x0 = current - target;
    const v0 = velocity;
    const omegaD = omegaN * Math.sqrt(1 - dampingRatio * dampingRatio);
    const decay = Math.exp(-dampingRatio * omegaN * dt);
    const cosWd = Math.cos(omegaD * dt);
    const sinWd = Math.sin(omegaD * dt);
    const offset = x0 * decay * cosWd + (v0 + dampingRatio * omegaN * x0) / omegaD * decay * sinWd;
    const b0 = (v0 + dampingRatio * omegaN * x0) / omegaD;
    const newVel = -dampingRatio * omegaN * offset + decay * (-x0 * omegaD * sinWd + b0 * omegaD * cosWd);
    return { current: target + offset, velocity: newVel };
  }

  // renderer/methods-tabs.ts
  var tabsMethods = {
    /**
     * Set the tab indicator's target index. Animates with critically
     * damped spring. Also triggers a quick press-and-release cycle.
     */
    gooseTabSel(groupId, tabIndex, tabsCount) {
      const st = this.gooseEnsureTgl(
        groupId,
        tabIndex,
        LiquidGlassRenderer.gooseTabScale,
        tabsCount - 1
        // valueRangeSpan — faithful to DampedDragAnimation valueRange 0..(tabsCount-1)
      );
      if (st.isDragging) return;
      if (st.targetFraction === tabIndex) return;
      st.targetFraction = tabIndex;
      st.trackVelocityAfterRelease = false;
      st.targetVelocity = 0;
      st.velocity = 0;
      st.velocityVelocity = 0;
      st.velocityTracker.resetTracking();
      if (st.targetPress === 0) {
        st.targetPress = 1;
        st.targetScaleX = st.pressedScale;
        st.targetScaleY = st.pressedScale;
      }
      this.gooseAnimStart();
    },
    /**
     * Begin a finger drag on the tab indicator. Sets isDragging=true and
     * starts the press animation (scale → 78/56).
     */
    gooseTabDragStart(groupId, startTabIndex, tabsCount) {
      const st = this.gooseEnsureTgl(
        groupId,
        startTabIndex,
        LiquidGlassRenderer.gooseTabScale,
        tabsCount - 1
        // valueRangeSpan — faithful to DampedDragAnimation valueRange 0..(tabsCount-1)
      );
      st.isDragging = true;
      st.targetPress = 1;
      st.targetScaleX = st.pressedScale;
      st.targetScaleY = st.pressedScale;
      st.velocityTracker.resetTracking();
      st.targetVelocity = 0;
      st.velocity = 0;
      st.velocityVelocity = 0;
      this.gooseAnimStart();
    },
    /**
     * Update the tab indicator's target based on finger movement.
     * newTarget = startTabIndex + (currentX - startX) / tabWidth, clamped to [0, tabsCount-1].
     * Also updates panelOffset: 4dp * sign(fraction) * EaseOut(|fraction|).
     *
     * VELOCITY TRACKING happens in the animation loop (methods-animation.ts),
     * faithful to DampedDragAnimation.updateVelocity() which feeds (time, value)
     * to the VelocityTracker inside the valueAnimation.animateTo block.
     */
    gooseTabDrag(groupId, startTabIndex, currentX, startX, tabWidth, tabsCount) {
      const st = this.gooseEnsureTgl(
        groupId,
        startTabIndex,
        LiquidGlassRenderer.gooseTabScale,
        tabsCount - 1
        // valueRangeSpan — faithful to DampedDragAnimation valueRange 0..(tabsCount-1)
      );
      if (!st.isDragging) return;
      const delta = (currentX - startX) / Math.max(1, tabWidth);
      const newTarget = Math.max(0, Math.min(tabsCount - 1, startTabIndex + delta));
      st.targetFraction = newTarget;
      const maxWidth = tabWidth * tabsCount;
      const offsetFraction = Math.max(-1, Math.min(1, (currentX - startX) / Math.max(1, maxWidth)));
      const easeOut = 1 - Math.pow(1 - Math.abs(offsetFraction), 2);
      st.targetPanelOffset = 4 * gooseDP * Math.sign(offsetFraction) * easeOut;
      this.gooseAnimStart();
    },
    /**
     * End a finger drag. Snaps to nearest tab index. Returns the snapped index.
     * panelOffset springs back to 0 (spring(1f, 300f) — critically damped).
     */
    gooseTabDragEnd(groupId, tabsCount) {
      const st = this.toggleStates.get(groupId);
      if (!st) return 0;
      st.isDragging = false;
      const finalTarget = Math.round(st.targetFraction);
      const clamped = Math.max(0, Math.min(tabsCount - 1, finalTarget));
      st.targetFraction = clamped;
      st.velocityTracker.resetTracking();
      st.trackVelocityAfterRelease = false;
      st.targetVelocity = 0;
      st.targetPanelOffset = 0;
      this.gooseAnimStart();
      return clamped;
    },
    /** Read the current animated tab fraction (0..tabsCount-1). */
    gooseTabFrac(groupId) {
      var _a, _b;
      return (_b = (_a = this.toggleStates.get(groupId)) == null ? void 0 : _a.fraction) != null ? _b : 0;
    },
    /** Read the current target tab index. */
    gooseTabTarget(groupId) {
      var _a, _b;
      return (_b = (_a = this.toggleStates.get(groupId)) == null ? void 0 : _a.targetFraction) != null ? _b : 0;
    }
  };

  // renderer/methods-elements.ts
  var elementMethods = {
    /** Set the element list. Triggers foreground re-raster for changed elements. */
    gooseElements(configs) {
      this.gooseButtons(configs);
    },
    /** Set the element list (legacy name; same as gooseElements). */
    gooseButtons(configs) {
      var _a, _b;
      const prevIds = new Set(this.buttonConfigs.map((b) => b.id));
      const nextIds = new Set(configs.map((b) => b.id));
      for (const id of nextIds) if (!prevIds.has(id)) this.fgDirtyIds.add(id);
      for (const next of configs) {
        const prev = this.buttonConfigs.find((b) => b.id === next.id);
        if (!prev) continue;
        const eq4 = (a, b) => {
          if (!a || !b) return a === b;
          if (a.length !== b.length) return false;
          for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
          return true;
        };
        const prevTextIcon = (_a = prev.text) == null ? void 0 : _a.icon;
        const nextTextIcon = (_b = next.text) == null ? void 0 : _b.icon;
        const textIconChanged = !!prevTextIcon !== !!nextTextIcon || prevTextIcon && nextTextIcon && (prevTextIcon.path !== nextTextIcon.path || prevTextIcon.size !== nextTextIcon.size || !eq4(prevTextIcon.color, nextTextIcon.color));
        const prevBtnIcon = prev.icon;
        const nextBtnIcon = next.icon;
        const btnIconChanged = !!prevBtnIcon !== !!nextBtnIcon || prevBtnIcon && nextBtnIcon && (prevBtnIcon.path !== nextBtnIcon.path || prevBtnIcon.size !== nextBtnIcon.size || !eq4(prevBtnIcon.color, nextBtnIcon.color));
        const pt = prev.text;
        const nt = next.text;
        const textPropsChanged = !!pt !== !!nt || pt && nt && (!eq4(pt.color, nt.color) || pt.halo !== nt.halo || pt.fontSizePx !== nt.fontSizePx || pt.fontWeight !== nt.fontWeight || pt.align !== nt.align || pt.wrap !== nt.wrap || pt.paddingPx !== nt.paddingPx || pt.valign !== nt.valign || pt.maxLines !== nt.maxLines);
        if (prev.label !== next.label || !eq4(prev.labelColor, next.labelColor) || prev.showChevron !== next.showChevron || prev.rect.w !== next.rect.w || prev.rect.h !== next.rect.h || next.text && prev.text && prev.text.content !== next.text.content || next.text && !prev.text || !next.text && prev.text || textIconChanged || btnIconChanged || textPropsChanged) {
          this.fgDirtyIds.add(next.id);
        }
      }
      for (const id of prevIds) {
        if (!nextIds.has(id)) {
          this.buttonStates.delete(id);
          const tex = this.fgTextures.get(id);
          if (tex) {
            this.gl.deleteTexture(tex);
            this.fgTextures.delete(id);
          }
          this.fgDirtyIds.delete(id);
        }
      }
      for (const c of configs) {
        if (!this.buttonStates.has(c.id)) {
          const initValue = 0;
          this.buttonStates.set(c.id, {
            pressProgress: 0,
            pressVelocity: 0,
            targetPress: 0,
            dragX: 0,
            dragY: 0,
            dragVx: 0,
            dragVy: 0,
            targetDragX: 0,
            targetDragY: 0,
            startDragX: 0,
            startDragY: 0,
            interactiveValue: initValue,
            interactiveVelocity: 0,
            targetInteractiveValue: initValue
          });
        }
      }
      this.buttonConfigs = configs;
      this.gooseReqRender();
    },
    /**
     * Set the interactive value (0..1 for toggle/slider; integer index for
     * tabbar) for an element. The renderer springs `interactiveValue` toward
     * this target so motion looks animated, not snapped.
     */
    gooseInterVal(id, value) {
      const st = this.buttonStates.get(id);
      if (!st) return;
      if (st.targetInteractiveValue !== value) {
        st.targetInteractiveValue = value;
        this.gooseAnimStart();
        this.gooseReqRender();
      }
    },
    /**
     * Set the pressed state for a button. `position` is the finger position
     * in canvas CSS pixels (top-left origin). When pressed=true, the position
     * is recorded as the drag start; subsequent calls with pressed=true update
     * the drag target. When pressed=false, the drag target springs back to
     * the start position.
     *
     * FAITHFUL TO InteractiveHighlight.kt:
     *   - onDragStart: positionAnimation.snapTo(down.position)  // instant snap
     *   - onDrag:      positionAnimation.snapTo(change.position) // instant snap
     *   - onDragEnd:   positionAnimation.animateTo(startPosition, springSpec) // spring back
     *
     * So during a drag the position FOLLOWS the finger instantly (no spring
     * lag); only on release does the spring kick in to return to start.
     */
    goosePress(id, pressed, position) {
      const st = this.buttonStates.get(id);
      if (!st) return;
      if (pressed) {
        const btn = this.buttonConfigs.find((b) => b.id === id);
        if (btn && position) {
          const localX = position.x - btn.rect.x;
          const localY = position.y - btn.rect.y;
          if (st.targetPress === 0) {
            st.startDragX = localX;
            st.startDragY = localY;
            st.dragX = localX;
            st.dragY = localY;
            st.dragVx = 0;
            st.dragVy = 0;
          }
          st.dragX = localX;
          st.dragY = localY;
          st.dragVx = 0;
          st.dragVy = 0;
          st.targetDragX = localX;
          st.targetDragY = localY;
        }
        st.targetPress = 1;
      } else {
        st.targetPress = 0;
        st.targetDragX = st.startDragX;
        st.targetDragY = st.startDragY;
      }
      this.gooseAnimStart();
    },
    /**
     * Update the drag position while pressed (without changing press state).
     * Used for pointermove during a drag.
     *
     * FAITHFUL TO InteractiveHighlight.kt: positionAnimation.snapTo(change.position)
     * — the position FOLLOWS the finger instantly with no spring lag. Only
     * on release (goosePress false) does the spring kick in to return to start.
     */
    gooseDragPos(id, position) {
      const st = this.buttonStates.get(id);
      if (!st || st.targetPress === 0) return;
      const btn = this.buttonConfigs.find((b) => b.id === id);
      if (!btn) return;
      const localX = position.x - btn.rect.x;
      const localY = position.y - btn.rect.y;
      st.dragX = localX;
      st.dragY = localY;
      st.dragVx = 0;
      st.dragVy = 0;
      st.targetDragX = localX;
      st.targetDragY = localY;
      this.gooseReqRender();
    }
  };

  // renderer/methods-animation.ts
  var animationMethods = {
    /**
     * Spring-based animation loop. Matches InteractiveHighlight.kt's
     * spring(0.5f, 300f) spec — underdamped, with a small overshoot on
     * release. Uses real wall-clock dt for frame-rate-independent timing.
     */
    gooseAnimStart() {
      if (this.animRafId !== null) return;
      let lastTime = performance.now();
      const tick = () => {
        const now = performance.now();
        const dt = Math.min((now - lastTime) / 1e3, 0.05);
        lastTime = now;
        let stillAnimating = false;
        for (const st of this.buttonStates.values()) {
          const pDelta = Math.abs(st.targetPress - st.pressProgress);
          if (pDelta > SPRING_THRESHOLD || Math.abs(st.pressVelocity) > SPRING_THRESHOLD) {
            const r = springStep1D(
              st.pressProgress,
              st.pressVelocity,
              st.targetPress,
              dt
            );
            st.pressProgress = r.current;
            st.pressVelocity = r.velocity;
            stillAnimating = true;
          } else {
            st.pressProgress = st.targetPress;
            st.pressVelocity = 0;
          }
          if (Math.abs(st.targetDragX - st.dragX) > SPRING_THRESHOLD || Math.abs(st.dragVx) > SPRING_THRESHOLD) {
            const r = springStep1D(st.dragX, st.dragVx, st.targetDragX, dt);
            st.dragX = r.current;
            st.dragVx = r.velocity;
            stillAnimating = true;
          } else {
            st.dragX = st.targetDragX;
            st.dragVx = 0;
          }
          if (Math.abs(st.targetDragY - st.dragY) > SPRING_THRESHOLD || Math.abs(st.dragVy) > SPRING_THRESHOLD) {
            const r = springStep1D(st.dragY, st.dragVy, st.targetDragY, dt);
            st.dragY = r.current;
            st.dragVy = r.velocity;
            stillAnimating = true;
          } else {
            st.dragY = st.targetDragY;
            st.dragVy = 0;
          }
          const iDelta = Math.abs(st.targetInteractiveValue - st.interactiveValue);
          if (iDelta > SPRING_THRESHOLD || Math.abs(st.interactiveVelocity) > SPRING_THRESHOLD) {
            const r = springStep1D(
              st.interactiveValue,
              st.interactiveVelocity,
              st.targetInteractiveValue,
              dt
            );
            st.interactiveValue = r.current;
            st.interactiveVelocity = r.velocity;
            stillAnimating = true;
          } else {
            st.interactiveValue = st.targetInteractiveValue;
            st.interactiveVelocity = 0;
          }
        }
        for (const tg of this.toggleStates.values()) {
          if (tg.targetPress === 1 && !tg.isDragging && Math.abs(tg.targetFraction - tg.fraction) < 0.02) {
            tg.targetPress = 0;
            tg.targetScaleX = 1;
            tg.targetScaleY = 1;
            this.gooseAnimStart();
          }
          const fDelta = Math.abs(tg.targetFraction - tg.fraction);
          if (fDelta > SPRING_THRESHOLD || Math.abs(tg.fractionVelocity) > SPRING_THRESHOLD) {
            const r = springStepCritical(
              tg.fraction,
              tg.fractionVelocity,
              tg.targetFraction,
              dt,
              TOGGLE_VALUE_OMEGA_N
            );
            tg.fraction = r.current;
            tg.fractionVelocity = r.velocity;
            if (tg.trackVelocityAfterRelease || tg.isDragging) {
              const nowMs = performance.now();
              tg.velocityTracker.addPosition(nowMs, tg.fraction);
              const tracked = tg.velocityTracker.calculateVelocity();
              const span = tg.valueRangeSpan || 1;
              tg.targetVelocity = tracked / span;
            }
            stillAnimating = true;
          } else {
            tg.fraction = tg.targetFraction;
            tg.fractionVelocity = 0;
            if (!tg.isDragging) {
              tg.targetVelocity = 0;
              tg.trackVelocityAfterRelease = false;
              tg.velocityTracker.resetTracking();
            }
          }
          const ppDelta = Math.abs(tg.targetPress - tg.pressProgress);
          if (ppDelta > SPRING_THRESHOLD || Math.abs(tg.pressVelocity) > SPRING_THRESHOLD) {
            const r = springStepCritical(
              tg.pressProgress,
              tg.pressVelocity,
              tg.targetPress,
              dt,
              TOGGLE_VALUE_OMEGA_N
            );
            tg.pressProgress = r.current;
            tg.pressVelocity = r.velocity;
            stillAnimating = true;
          } else {
            tg.pressProgress = tg.targetPress;
            tg.pressVelocity = 0;
          }
          const sx = Math.abs(tg.targetScaleX - tg.scaleX);
          if (sx > SPRING_THRESHOLD || Math.abs(tg.scaleXVelocity) > SPRING_THRESHOLD) {
            const r = springStepUnderdamped(
              tg.scaleX,
              tg.scaleXVelocity,
              tg.targetScaleX,
              dt,
              TOGGLE_SCALE_X_OMEGA_N,
              TOGGLE_SCALE_X_DAMPING_RATIO
            );
            tg.scaleX = r.current;
            tg.scaleXVelocity = r.velocity;
            stillAnimating = true;
          } else {
            tg.scaleX = tg.targetScaleX;
            tg.scaleXVelocity = 0;
          }
          const sy = Math.abs(tg.targetScaleY - tg.scaleY);
          if (sy > SPRING_THRESHOLD || Math.abs(tg.scaleYVelocity) > SPRING_THRESHOLD) {
            const r = springStepUnderdamped(
              tg.scaleY,
              tg.scaleYVelocity,
              tg.targetScaleY,
              dt,
              TOGGLE_SCALE_Y_OMEGA_N,
              TOGGLE_SCALE_Y_DAMPING_RATIO
            );
            tg.scaleY = r.current;
            tg.scaleYVelocity = r.velocity;
            stillAnimating = true;
          } else {
            tg.scaleY = tg.targetScaleY;
            tg.scaleYVelocity = 0;
          }
          const vDelta = Math.abs(tg.targetVelocity - tg.velocity);
          if (vDelta > SPRING_THRESHOLD || Math.abs(tg.velocityVelocity) > SPRING_THRESHOLD) {
            const r = springStepUnderdamped(
              tg.velocity,
              tg.velocityVelocity,
              tg.targetVelocity,
              dt,
              TOGGLE_VELOCITY_OMEGA_N,
              TOGGLE_VELOCITY_DAMPING_RATIO
            );
            tg.velocity = r.current;
            tg.velocityVelocity = r.velocity;
            stillAnimating = true;
          } else {
            tg.velocity = tg.targetVelocity;
            tg.velocityVelocity = 0;
          }
          const poDelta = Math.abs(tg.targetPanelOffset - tg.panelOffset);
          if (poDelta > SPRING_THRESHOLD || Math.abs(tg.panelOffsetVelocity) > SPRING_THRESHOLD) {
            const r = springStepCritical(
              tg.panelOffset,
              tg.panelOffsetVelocity,
              tg.targetPanelOffset,
              dt,
              Math.sqrt(300)
              // ω_n = sqrt(k) = sqrt(300) ≈ 17.32
            );
            tg.panelOffset = r.current;
            tg.panelOffsetVelocity = r.velocity;
            stillAnimating = true;
          } else {
            tg.panelOffset = tg.targetPanelOffset;
            tg.panelOffsetVelocity = 0;
          }
        }
        if (Math.abs(this.scrollVelocity) > 0.5) {
          const SCROLL_DECAY = 4;
          const newScrollY = this.scrollY + this.scrollVelocity * dt;
          const clamped = this.gooseClampScroll(newScrollY);
          if (clamped !== newScrollY) {
            this.scrollY = clamped;
            this.scrollVelocity = 0;
          } else {
            this.scrollY = clamped;
            this.scrollVelocity *= Math.exp(-SCROLL_DECAY * dt);
          }
          stillAnimating = true;
        } else {
          this.scrollVelocity = 0;
        }
        this.gooseReqRender();
        if (stillAnimating) {
          this.animRafId = requestAnimationFrame(tick);
        } else {
          this.animRafId = null;
        }
      };
      this.animRafId = requestAnimationFrame(tick);
    },
    gooseReqRender() {
      this.needsRedraw = true;
      if (this.rafId !== null) return;
      this.rafId = requestAnimationFrame(() => {
        this.rafId = null;
        this.gooseRender();
      });
    }
  };

  // renderer/methods-raster.ts
  var rasterMethods = {
    gooseRaster(cfg) {
      var _a, _b;
      if (cfg.kind === "text" && cfg.text) {
        this.gooseRasterText(cfg);
        return;
      }
      if (cfg.kind !== "button" && !cfg.label && !cfg.icon) {
        this.fgDirtyIds.delete(cfg.id);
        return;
      }
      const dpr = this.dpr;
      const w = Math.max(1, Math.round(cfg.rect.w * dpr));
      const h = Math.max(1, Math.round(cfg.rect.h * dpr));
      if (this.fgCanvas.width !== w) this.fgCanvas.width = w;
      if (this.fgCanvas.height !== h) this.fgCanvas.height = h;
      const ctx = this.fgCtx;
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, w, h);
      ctx.scale(dpr, dpr);
      const cssW = cfg.rect.w;
      const cssH = cfg.rect.h;
      if (cfg.icon) {
        const iconSize = cfg.icon.size;
        const ic = cfg.icon.color;
        ctx.save();
        ctx.translate(cssW / 2 - iconSize / 2, cssH / 2 - iconSize / 2);
        const vp = (_a = cfg.icon.viewport) != null ? _a : 24;
        ctx.scale(iconSize / vp, iconSize / vp);
        const p = new Path2D(cfg.icon.path);
        ctx.fillStyle = `rgba(${Math.round(ic[0] * 255)}, ${Math.round(
          ic[1] * 255
        )}, ${Math.round(ic[2] * 255)}, ${ic[3]})`;
        ctx.fill(p);
        ctx.restore();
        this.gooseUploadRaster(cfg.id);
        this.fgDirtyIds.delete(cfg.id);
        return;
      }
      const fontPx = (_b = cfg.labelFontSizePx) != null ? _b : cssH * (15 / 48);
      const fontFamily = '-apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';
      ctx.font = `400 ${fontPx}px ${fontFamily}`;
      ctx.textBaseline = "middle";
      ctx.textAlign = "center";
      const colorStr = `rgba(${Math.round(cfg.labelColor[0] * 255)}, ${Math.round(
        cfg.labelColor[1] * 255
      )}, ${Math.round(cfg.labelColor[2] * 255)}, ${cfg.labelColor[3]})`;
      const haloIsLight = cfg.labelColor[0] + cfg.labelColor[1] + cfg.labelColor[2] < 1.5;
      ctx.save();
      ctx.shadowColor = haloIsLight ? "rgba(255,255,255,0.45)" : "rgba(0,0,0,0.15)";
      ctx.shadowBlur = haloIsLight ? fontPx * 0.12 : fontPx * 0.05;
      ctx.fillStyle = colorStr;
      ctx.fillText(cfg.label, cssW / 2, cssH / 2 + 0.5);
      ctx.restore();
      if (cfg.showChevron) {
        const chevronSize = fontPx * 0.93;
        const labelWidth = ctx.measureText(cfg.label).width;
        const cx = cssW / 2 + labelWidth / 2 + fontPx * 0.53 + chevronSize / 2;
        const cy = cssH / 2;
        ctx.save();
        ctx.strokeStyle = colorStr;
        ctx.globalAlpha = 0.6;
        ctx.lineWidth = fontPx * 0.107;
        ctx.lineCap = "round";
        ctx.lineJoin = "round";
        ctx.beginPath();
        ctx.moveTo(cx - chevronSize * 0.3, cy - chevronSize * 0.4);
        ctx.lineTo(cx + chevronSize * 0.2, cy);
        ctx.lineTo(cx - chevronSize * 0.3, cy + chevronSize * 0.4);
        ctx.stroke();
        ctx.restore();
      }
      this.gooseUploadRaster(cfg.id);
      this.fgDirtyIds.delete(cfg.id);
    },
    /* ---------------------------------------------------------------- *
     * Text-element rasterizer — draws an arbitrary text label (with
     * optional word wrap) to the foreground texture. Used for section
     * titles, dialog body text, slider value labels, etc.
     * ---------------------------------------------------------------- */
    gooseRasterText(cfg) {
      var _a, _b, _c;
      if (!cfg.text) return;
      const dpr = this.dpr;
      const w = Math.max(1, Math.round(cfg.rect.w * dpr));
      const h = Math.max(1, Math.round(cfg.rect.h * dpr));
      if (this.fgCanvas.width !== w) this.fgCanvas.width = w;
      if (this.fgCanvas.height !== h) this.fgCanvas.height = h;
      const ctx = this.fgCtx;
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, w, h);
      ctx.scale(dpr, dpr);
      const t = cfg.text;
      const cssW = cfg.rect.w;
      const cssH = cfg.rect.h;
      const fontFamily = '-apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';
      ctx.font = `${t.fontWeight} ${t.fontSizePx}px ${fontFamily}`;
      ctx.textBaseline = "middle";
      const pad = (_a = t.paddingPx) != null ? _a : 0;
      let halo = "none";
      if (t.halo === "light") halo = "light";
      else if (t.halo === "dark") halo = "dark";
      else if (t.halo === "auto" || t.halo === void 0) {
        const bright = t.color[0] + t.color[1] + t.color[2];
        halo = bright < 1.5 ? "light" : "dark";
      }
      if (halo === "light") {
        ctx.shadowColor = "rgba(255,255,255,0.55)";
        ctx.shadowBlur = t.fontSizePx * 0.16;
      } else if (halo === "dark") {
        ctx.shadowColor = "rgba(0,0,0,0.28)";
        ctx.shadowBlur = t.fontSizePx * 0.1;
      } else {
        ctx.shadowColor = "transparent";
        ctx.shadowBlur = 0;
      }
      const colorStr = `rgba(${Math.round(t.color[0] * 255)}, ${Math.round(
        t.color[1] * 255
      )}, ${Math.round(t.color[2] * 255)}, ${t.color[3]})`;
      ctx.fillStyle = colorStr;
      let textYOffset = 0;
      if (t.icon) {
        const iconDrawSize = t.icon.size;
        const iconLayoutSize = (_b = t.icon.layoutSize) != null ? _b : iconDrawSize;
        const gap = t.content ? 2 : 0;
        const totalBlockH = iconLayoutSize + gap + (t.content ? t.fontSizePx : 0);
        const blockTop = cssH / 2 - totalBlockH / 2;
        const iconCx = cssW / 2;
        const iconCy = blockTop + iconLayoutSize / 2;
        ctx.save();
        ctx.translate(iconCx - iconDrawSize / 2, iconCy - iconDrawSize / 2);
        const vp = (_c = t.icon.viewport) != null ? _c : 24;
        ctx.scale(iconDrawSize / vp, iconDrawSize / vp);
        const p = new Path2D(t.icon.path);
        const ic = t.icon.color;
        ctx.fillStyle = `rgba(${Math.round(ic[0] * 255)}, ${Math.round(
          ic[1] * 255
        )}, ${Math.round(ic[2] * 255)}, ${ic[3]})`;
        ctx.fill(p);
        ctx.restore();
        textYOffset = (iconLayoutSize + gap) / 2;
      }
      if (t.align === "center") {
        ctx.textAlign = "center";
        if (t.wrap) {
          let lines = wrapText(ctx, t.content, cssW - pad * 2);
          if (t.maxLines != null && lines.length > t.maxLines) {
            lines = lines.slice(0, t.maxLines);
          }
          const lineH = t.fontSizePx * 1.35;
          const totalH = lineH * lines.length;
          let y;
          if (t.valign === "top") {
            y = lineH / 2 + textYOffset;
          } else if (t.valign === "bottom") {
            y = cssH - totalH + lineH / 2 + textYOffset;
          } else {
            y = cssH / 2 - totalH / 2 + lineH / 2 + textYOffset;
          }
          for (const line of lines) {
            ctx.fillText(line, cssW / 2, y);
            y += lineH;
          }
        } else {
          ctx.fillText(t.content, cssW / 2, cssH / 2 + 0.5 + textYOffset);
        }
      } else if (t.align === "left") {
        ctx.textAlign = "left";
        if (t.wrap) {
          let lines = wrapText(ctx, t.content, cssW - pad * 2);
          if (t.maxLines != null && lines.length > t.maxLines) {
            lines = lines.slice(0, t.maxLines);
          }
          const lineH = t.fontSizePx * 1.35;
          const totalH = lineH * lines.length;
          let y;
          if (t.valign === "top") {
            y = lineH / 2 + textYOffset;
          } else if (t.valign === "bottom") {
            y = cssH - totalH + lineH / 2 + textYOffset;
          } else {
            y = cssH / 2 - totalH / 2 + lineH / 2 + textYOffset;
          }
          for (const line of lines) {
            ctx.fillText(line, pad, y);
            y += lineH;
          }
        } else {
          ctx.fillText(t.content, pad, cssH / 2 + 0.5 + textYOffset);
        }
      } else {
        ctx.textAlign = "right";
        ctx.fillText(t.content, cssW - pad, cssH / 2 + 0.5 + textYOffset);
      }
      this.gooseUploadRaster(cfg.id);
      this.fgDirtyIds.delete(cfg.id);
    },
    gooseUploadRaster(id) {
      const gl = this.gl;
      let tex = this.fgTextures.get(id);
      if (!tex) {
        tex = gl.createTexture();
        this.fgTextures.set(id, tex);
      }
      gl.bindTexture(gl.TEXTURE_2D, tex);
      gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
      uploadCanvasAsPixels(gl, this.fgCanvas, true);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);
    }
  };

  // renderer/methods-render.ts
  var renderMethods = {
    gooseRender() {
      if (!this.needsRedraw) return;
      this.needsRedraw = false;
      if (!this.wallpaperReady && !this.backgroundColor) return;
      const gl = this.gl;
      this.gooseResizeFBO(this.canvas.width, this.canvas.height);
      for (const cfg of this.buttonConfigs) {
        if (this.fgDirtyIds.has(cfg.id)) {
          this.gooseRaster(cfg);
        }
      }
      this.gooseRenderBG();
      if (this.buttonConfigs.length === 0) {
        this.gooseBindFBO(null);
        this.gooseCopy(this.fboATex);
        return;
      }
      const sceneBlurEl = this.buttonConfigs.find((e) => {
        var _a;
        return ((_a = e.sceneBlurRadius) != null ? _a : 0) >= 0.5;
      });
      if (sceneBlurEl) {
        const r = sceneBlurEl.sceneBlurRadius * this.dpr;
        const blurred = this.gooseBlur(this.fboATex, r);
        this.gooseBindFBO(this.fboA);
        this.gooseCopy(blurred);
      }
      gl.enable(gl.BLEND);
      gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
      const scrollY = this.scrollY;
      const CULL_MARGIN = 120;
      const viewportTop = -CULL_MARGIN;
      const viewportBottom = this.cssHeight + CULL_MARGIN;
      const effRect = (el) => {
        const y = el.scroll ? el.rect.y - scrollY : el.rect.y;
        return { x: el.rect.x, y, w: el.rect.w, h: el.rect.h };
      };
      let curFbo = this.fboA;
      let curTex = this.fboATex;
      let otherFbo = this.fboB;
      let otherTex = this.fboBTex;
      for (const el of this.buttonConfigs) {
        if (el.renderOnTop) continue;
        const y = el.scroll ? el.rect.y - scrollY : el.rect.y;
        if (y + el.rect.h < viewportTop || y > viewportBottom) continue;
        const r = effRect(el);
        const st = this.buttonStates.get(el.id);
        if (this.gooseRenderPlain(el, r, st, curFbo)) continue;
        if (el.backdropFbo && el.scrimColor) {
          this.gooseRenderDlg(el.scrimColor, el.brightness, el.contrast, el.saturation);
        }
        if (el.useContinuousSdf) {
          this.gooseLoadCSDF(el.rect.w, el.rect.h, el.cornerRadius);
        }
        const result = this.gooseRenderGlass(el, st, curFbo, curTex, otherFbo, otherTex, r);
        curFbo = result.curFbo;
        curTex = result.curTex;
        otherFbo = result.otherFbo;
        otherTex = result.otherTex;
        if (el.isBottomTabContainer && this.tabsBackdropFbo && this.tabsBackdropTex) {
          this.gooseBindFBO(this.tabsBackdropFbo);
          this.gl.clearColor(0, 0, 0, 0);
          this.gl.clear(this.gl.COLOR_BUFFER_BIT);
          this.gooseCopy(curTex);
          this.gooseBindFBO(curFbo);
          this.gl.enable(this.gl.BLEND);
          this.gl.blendFunc(this.gl.SRC_ALPHA, this.gl.ONE_MINUS_SRC_ALPHA);
        }
      }
      for (const el of this.buttonConfigs) {
        if (!el.renderOnTop) continue;
        const y = el.scroll ? el.rect.y - scrollY : el.rect.y;
        if (y + el.rect.h < viewportTop || y > viewportBottom) continue;
        const r = effRect(el);
        const st = this.buttonStates.get(el.id);
        if (this.gooseRenderPlain(el, r, st, curFbo)) continue;
        const result = this.gooseRenderGlass(el, st, curFbo, curTex, otherFbo, otherTex, r);
        curFbo = result.curFbo;
        curTex = result.curTex;
        otherFbo = result.otherFbo;
        otherTex = result.otherTex;
      }
      this.gooseBindFBO(null);
      this.gooseCopy(curTex);
    },
    /** Helper to set SDF uniforms (canvasSize + offset + size + cornerRadii)
     *  for any of the SDF-using programs. */
    gooseSDFUniforms(u, aPosLoc, r, cornerRadius) {
      const gl = this.gl;
      gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
      gl.enableVertexAttribArray(aPosLoc);
      gl.vertexAttribPointer(aPosLoc, 2, gl.FLOAT, false, 0, 0);
      gl.uniform2f(u["uCanvasSize"], this.canvas.width, this.canvas.height);
      gl.uniform2f(u["uOffset"], r.x * this.dpr, r.y * this.dpr);
      gl.uniform2f(u["uSize"], r.w * this.dpr, r.h * this.dpr);
      gl.uniform4f(
        u["uCornerRadii"],
        cornerRadius * this.dpr,
        cornerRadius * this.dpr,
        cornerRadius * this.dpr,
        cornerRadius * this.dpr
      );
    },
    /** Render wallpaper or solid background color into fboA. */
    gooseRenderBG() {
      const gl = this.gl;
      this.gooseBindFBO(this.fboA);
      gl.disable(gl.BLEND);
      if (this.backgroundColor) {
        const [r, g, b] = this.backgroundColor;
        this.gooseFill(r, g, b, 1);
      } else {
        gl.useProgram(this.wallpaperProgram);
        gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
        gl.enableVertexAttribArray(this.aPosLocWp);
        gl.vertexAttribPointer(this.aPosLocWp, 2, gl.FLOAT, false, 0, 0);
        gl.activeTexture(gl.TEXTURE0);
        gl.bindTexture(gl.TEXTURE_2D, this.wallpaperTexture);
        gl.uniform1i(this.uWp["uBackdrop"], 0);
        gl.uniform2f(this.uWp["uCanvasSize"], this.canvas.width, this.canvas.height);
        gl.uniform2f(this.uWp["uWallpaperSize"], this.wallpaperSize[0], this.wallpaperSize[1]);
        gl.drawArrays(gl.TRIANGLES, 0, 6);
      }
    },
    /** Render wallpaper+scrim+colorControls into dialogBackdropFbo as ONE OPAQUE
     *  layer (alpha=1), replicating the original's LayerBackdrop (wallpaper+scrim)
     *  with colorControls applied — matching the original's colorControls→blur→lens
     *  effects order. The dialog card (backdropFbo + useSeparableBlur) 2-pass blurs
     *  this FBO then does lens refraction.
     *
     *  Order: wallpaper (opaque) → scrim (glBlendFuncSeparate, correct alpha) →
     *  colorControls (fullscreen pass). Cached by scrim+cc params. */
    gooseRenderDlg(scrim, brightness, contrast, saturation) {
      const key = `${scrim.join(",")}|${brightness},${contrast},${saturation}`;
      if (this.dialogBackdropKey === key) return;
      this.dialogBackdropKey = key;
      const gl = this.gl;
      this.gooseBindFBO(this.dialogBackdropFbo);
      gl.disable(gl.BLEND);
      if (this.backgroundColor) {
        const [r, g, b] = this.backgroundColor;
        this.gooseFill(r, g, b, 1);
      } else {
        gl.useProgram(this.wallpaperProgram);
        gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
        gl.enableVertexAttribArray(this.aPosLocWp);
        gl.vertexAttribPointer(this.aPosLocWp, 2, gl.FLOAT, false, 0, 0);
        gl.activeTexture(gl.TEXTURE0);
        gl.bindTexture(gl.TEXTURE_2D, this.wallpaperTexture);
        gl.uniform1i(this.uWp["uBackdrop"], 0);
        gl.uniform2f(this.uWp["uCanvasSize"], this.canvas.width, this.canvas.height);
        gl.uniform2f(this.uWp["uWallpaperSize"], this.wallpaperSize[0], this.wallpaperSize[1]);
        gl.drawArrays(gl.TRIANGLES, 0, 6);
      }
      if (scrim[3] > 1e-3) {
        gl.enable(gl.BLEND);
        gl.blendFuncSeparate(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA, gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
        this.gooseFill(scrim[0], scrim[1], scrim[2], scrim[3]);
        gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
      }
      this.gooseBindFBO(this.blurFboA);
      this.gooseColorCtrl(this.dialogBackdropTex, brightness, contrast, saturation);
      this.gooseBindFBO(this.dialogBackdropFbo);
      this.gooseCopy(this.blurFboATex);
    },
    /** Render a non-glass element (plain-rect / progressive-blur / text).
     *  Returns true if the element was handled (caller should `continue`).
     *  Returns false for glass elements (caller should run the ping-pong path). */
    gooseRenderPlain(el, r, st, curFbo) {
      var _a, _b, _c, _d;
      const gl = this.gl;
      let r2 = r;
      if (el.enterProgress != null) {
        const raw = el.enterProgress;
        const derived = raw < 0 ? (1 - Math.exp(-Math.abs(raw))) * -1 : raw <= 1 ? raw : 1 + (1 - Math.exp(-(raw - 1)));
        const ty = -48 * gooseDP * (1 - derived);
        const stretch = el.enterStretchFactor != null && derived > 1 ? el.enterStretchFactor * (derived - 1) * 32 * gooseDP : 0;
        r2 = { x: r.x, y: r.y + ty + stretch, w: r.w, h: r.h };
      }
      if (el.kind === "plain-rect" && el.plainRect) {
        const baseC = el.isToggleTrack ? null : el.plainRect.color;
        if (baseC && baseC[3] <= 0) return true;
        this.gooseBindFBO(curFbo);
        let c;
        if (el.isToggleTrack) {
          const tg = this.toggleStates.get(el.isToggleTrack.groupId);
          const f = tg ? tg.fraction : 0;
          const off = el.isToggleTrack.offColor;
          const on = el.isToggleTrack.onColor;
          c = [
            off[0] + (on[0] - off[0]) * f,
            off[1] + (on[1] - off[1]) * f,
            off[2] + (on[2] - off[2]) * f,
            off[3] + (on[3] - off[3]) * f
          ];
        } else {
          c = el.plainRect.color;
        }
        let fillRect = r2;
        if (el.isSliderFill) {
          const sf = this.toggleStates.get(el.isSliderFill.groupId);
          const fraction = sf ? sf.fraction : 0;
          const fillW = Math.max(el.isSliderFill.minW, el.isSliderFill.trackW * fraction);
          fillRect = { x: r.x, y: r.y, w: fillW, h: r.h };
        }
        gl.useProgram(this.plainRectProgram);
        this.gooseSDFUniforms(this.uPr, this.aPosLocPr, fillRect, el.cornerRadius);
        gl.blendFuncSeparate(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA, gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
        const enterA = el.enterProgress != null ? (() => {
          const sp = el.enterSafeProgress != null ? Math.max(0, Math.min(1, el.enterSafeProgress)) : Math.max(0, Math.min(1, el.enterProgress));
          return easeIn(sp);
        })() : 1;
        gl.uniform4f(this.uPr["uColor"], c[0], c[1], c[2], c[3] * enterA);
        gl.uniform1f(this.uPr["uCornerStyle"], this.cornerStyle);
        if (el.useContinuousSdf && this.continuousSdfTexture) {
          gl.activeTexture(gl.TEXTURE2);
          gl.bindTexture(gl.TEXTURE_2D, this.continuousSdfTexture);
          gl.uniform1i(this.uPr["uContinuousSdf"], 2);
          gl.uniform1f(this.uPr["uUseContinuousSdf"], 1);
          gl.uniform2f(this.uPr["uContinuousSdfTexSize"], this.continuousSdfTexSize[0], this.continuousSdfTexSize[1]);
          gl.uniform2f(this.uPr["uContinuousSdfElementSize"], r2.w * this.dpr, r2.h * this.dpr);
        } else {
          gl.uniform1f(this.uPr["uUseContinuousSdf"], 0);
        }
        gl.drawArrays(gl.TRIANGLES, 0, 6);
        return true;
      }
      if (el.kind === "progressive-blur" && el.progressiveBlur) {
        this.gooseBindFBO(curFbo);
        gl.useProgram(this.progressiveBlurProgram);
        this.gooseSDFUniforms(this.uPb, this.aPosLocPb, r2, el.cornerRadius);
        gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
        gl.activeTexture(gl.TEXTURE0);
        gl.bindTexture(gl.TEXTURE_2D, this.wallpaperTexture);
        gl.uniform1i(this.uPb["uBackdrop"], 0);
        gl.uniform2f(this.uPb["uWallpaperSize"], this.wallpaperSize[0], this.wallpaperSize[1]);
        gl.uniform1f(this.uPb["uBlurRadius"], el.progressiveBlur.blurRadius * this.dpr);
        const tc = el.progressiveBlur.tintColor;
        gl.uniform4f(this.uPb["uTintColor"], tc[0], tc[1], tc[2], tc[3]);
        gl.uniform1f(this.uPb["uTintIntensity"], el.progressiveBlur.tintIntensity);
        gl.drawArrays(gl.TRIANGLES, 0, 6);
        return true;
      }
      if (el.kind === "text") {
        this.gooseBindFBO(curFbo);
        let drawRect = r2;
        let fgScaleX = 1;
        let fgScaleY = 1;
        if (el.isBottomTabContent) {
          const tg = this.toggleStates.get(el.isBottomTabContent.groupId);
          if (tg) {
            const containerW = (_a = el.isBottomTabContent.containerWidth) != null ? _a : el.rect.w * 4;
            const containerScale = 1 + 16 * gooseDP / containerW * tg.pressProgress;
            fgScaleX = containerScale;
            fgScaleY = containerScale;
            const pivotX = (_b = el.isBottomTabContent.containerCenterX) != null ? _b : el.rect.x + el.rect.w / 2;
            const pivotY = (_c = el.isBottomTabContent.containerCenterY) != null ? _c : el.rect.y + el.rect.h / 2;
            const tabCenterX = el.rect.x + el.rect.w / 2;
            const tabCenterY = el.rect.y + el.rect.h / 2;
            const cx = pivotX + (tabCenterX - pivotX) * containerScale + tg.panelOffset;
            const cy = pivotY + (tabCenterY - pivotY) * containerScale;
            const sw = el.rect.w * fgScaleX;
            const sh = el.rect.h * fgScaleY;
            drawRect = { x: cx - sw / 2, y: cy - sh / 2, w: sw, h: sh };
          }
        }
        const pText = (_d = st == null ? void 0 : st.pressProgress) != null ? _d : 0;
        if (el.isInteractive && pText > 1e-3) {
          const pressTint = el.pressTintColor;
          gl.useProgram(this.tintProgram);
          gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
          gl.enableVertexAttribArray(this.aPosLocTn);
          gl.vertexAttribPointer(this.aPosLocTn, 2, gl.FLOAT, false, 0, 0);
          if (pressTint) {
            gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
          } else {
            gl.blendFunc(gl.SRC_ALPHA, gl.ONE);
          }
          gl.uniform2f(this.uTn["uCanvasSize"], this.canvas.width, this.canvas.height);
          gl.uniform2f(this.uTn["uOffset"], drawRect.x * this.dpr, drawRect.y * this.dpr);
          gl.uniform2f(this.uTn["uSize"], drawRect.w * this.dpr, drawRect.h * this.dpr);
          gl.uniform4f(this.uTn["uCornerRadii"], 0, 0, 0, 0);
          gl.uniform2f(this.uTn["uOriginalSize"], drawRect.w * this.dpr, drawRect.h * this.dpr);
          gl.uniform1f(this.uTn["uOriginalCornerRadius"], 0);
          gl.uniform2f(this.uTn["uLayerScale"], 1, 1);
          if (pressTint) {
            gl.uniform4f(this.uTn["uColor"], pressTint[0], pressTint[1], pressTint[2], 0.1 * pText);
          } else {
            gl.uniform4f(this.uTn["uColor"], 1, 1, 1, 0.1 * pText);
          }
          gl.drawArrays(gl.TRIANGLES, 0, 6);
          gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
        }
        const fgTex = this.fgTextures.get(el.id);
        if (fgTex) {
          gl.useProgram(this.foregroundProgram);
          gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
          gl.enableVertexAttribArray(this.aPosLocFg);
          gl.vertexAttribPointer(this.aPosLocFg, 2, gl.FLOAT, false, 0, 0);
          gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
          gl.activeTexture(gl.TEXTURE0);
          gl.bindTexture(gl.TEXTURE_2D, fgTex);
          gl.uniform1i(this.uFg["uTexture"], 0);
          gl.uniform2f(this.uFg["uCanvasSize"], this.canvas.width, this.canvas.height);
          gl.uniform2f(this.uFg["uOffset"], drawRect.x * this.dpr, drawRect.y * this.dpr);
          gl.uniform2f(this.uFg["uSize"], drawRect.w * this.dpr, drawRect.h * this.dpr);
          gl.uniform4f(
            this.uFg["uCornerRadii"],
            el.cornerRadius * this.dpr,
            el.cornerRadius * this.dpr,
            el.cornerRadius * this.dpr,
            el.cornerRadius * this.dpr
          );
          gl.uniform2f(this.uFg["uOriginalSize"], el.rect.w * this.dpr, el.rect.h * this.dpr);
          gl.uniform1f(this.uFg["uOriginalCornerRadius"], el.cornerRadius * this.dpr);
          gl.uniform2f(this.uFg["uLayerScale"], fgScaleX, fgScaleY);
          gl.uniform1f(this.uFg["uCornerStyle"], this.cornerStyle);
          gl.uniform1f(this.uFg["uAlpha"], el.enterProgress != null ? (() => {
            const sp = el.enterSafeProgress != null ? Math.max(0, Math.min(1, el.enterSafeProgress)) : Math.max(0, Math.min(1, el.enterProgress));
            return easeIn(sp);
          })() : 1);
          gl.drawArrays(gl.TRIANGLES, 0, 6);
          gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
        }
        return true;
      }
      return false;
    }
  };

  // renderer/methods-render-glass.ts
  var glassRenderMethods = {
    /** Render a glass element (button / glass-shape) via FBO ping-pong.
     *  Returns the swapped curFbo/curTex/otherFbo/otherTex so the caller
     *  can continue iteration with the new "current scene". */
    gooseRenderGlass(el, st, curFbo, curTex, otherFbo, otherTex, r) {
      var _a, _b, _c, _d;
      const gl = this.gl;
      const isButton = el.kind === "button";
      const p = (_a = st == null ? void 0 : st.pressProgress) != null ? _a : 0;
      const PRESS_SCALE_RATIO = 4 / 48;
      let scale = 1;
      let translationX = 0;
      let translationY = 0;
      let scaleX = 1;
      let scaleY = 1;
      if (el.enterProgress != null) {
        const raw = el.enterProgress;
        const derived = raw < 0 ? (1 - Math.exp(-Math.abs(raw))) * -1 : raw <= 1 ? raw : 1 + (1 - Math.exp(-(raw - 1)));
        translationY += -48 * gooseDP * (1 - derived);
        if (el.enterStretchFactor != null && derived > 1) {
          translationY += el.enterStretchFactor * (derived - 1) * 32 * gooseDP;
        }
        const sFactor = 1 + 0.1 * Math.max(0, derived - 1);
        scaleX /= sFactor;
        scaleY *= sFactor;
      }
      if (isButton && el.isInteractive && st) {
        const width = el.rect.w;
        const height = el.rect.h;
        const maxDim = Math.max(width, height);
        const minDim = Math.min(width, height);
        const maxOffset = minDim;
        const initialDerivative = 0.05;
        const maxDragScale = PRESS_SCALE_RATIO;
        scale = 1 + PRESS_SCALE_RATIO * p;
        const dx = st.dragX - st.startDragX;
        const dy = st.dragY - st.startDragY;
        translationX = maxOffset * Math.tanh(initialDerivative * dx / maxOffset);
        translationY = maxOffset * Math.tanh(initialDerivative * dy / maxOffset);
        const offsetAngle = Math.atan2(dy, dx);
        const whCap = Math.min(width / height, 1);
        const hwCap = Math.min(height / width, 1);
        scaleX = scale + maxDragScale * Math.abs(Math.cos(offsetAngle) * dx / maxDim) * whCap;
        scaleY = scale + maxDragScale * Math.abs(Math.sin(offsetAngle) * dy / maxDim) * hwCap;
      } else if (el.enterProgress == null) {
        scaleX = scale;
        scaleY = scale;
      }
      let toggleXOffset = 0;
      let toggleScaleX = 1;
      let toggleScaleY = 1;
      let togglePressProgress = 0;
      if (el.isToggleKnob) {
        const tg = this.toggleStates.get(el.isToggleKnob.groupId);
        if (tg) {
          toggleXOffset = tg.fraction * el.isToggleKnob.dragWidth;
          toggleScaleX = tg.scaleX;
          toggleScaleY = tg.scaleY;
          togglePressProgress = tg.pressProgress;
          const divisor = (_b = el.isToggleKnob.velocityDivisor) != null ? _b : 50;
          const vel = tg.velocity / divisor;
          const velX = Math.max(-0.2, Math.min(0.2, vel * 0.75));
          const velY = Math.max(-0.2, Math.min(0.2, vel * 0.25));
          toggleScaleX = toggleScaleX / (1 - velX);
          toggleScaleY = toggleScaleY * (1 - velY);
        }
      }
      scaleX *= toggleScaleX;
      scaleY *= toggleScaleY;
      if (el.isBottomTabContainer) {
        const tg = this.toggleStates.get(el.isBottomTabContainer.groupId);
        if (tg) {
          const containerScale = 1 + 16 * gooseDP / el.rect.w * tg.pressProgress;
          scaleX *= containerScale;
          scaleY *= containerScale;
          translationX += tg.panelOffset;
          togglePressProgress = tg.pressProgress;
        }
      }
      if (el.isBottomTabContent) {
        const tg = this.toggleStates.get(el.isBottomTabContent.groupId);
        if (tg) {
          const containerW = (_c = el.isBottomTabContent.containerWidth) != null ? _c : el.rect.w;
          const containerScale = 1 + 16 * gooseDP / containerW * tg.pressProgress;
          scaleX *= containerScale;
          const contentScale = 1 + 0.2 * tg.pressProgress;
          scaleX *= contentScale;
          scaleY *= containerScale * contentScale;
          translationX += tg.panelOffset;
        }
      }
      if (el.isBottomTabIndicator) {
        const tg = this.toggleStates.get(el.isBottomTabIndicator.groupId);
        if (tg) {
          toggleXOffset += tg.fraction * el.isBottomTabIndicator.dragWidth;
          toggleXOffset += tg.panelOffset;
          const indScaleX = tg.scaleX;
          const indScaleY = tg.scaleY;
          const vel = tg.velocity / 10;
          const velX = Math.max(-0.2, Math.min(0.2, vel * 0.75));
          const velY = Math.max(-0.2, Math.min(0.2, vel * 0.25));
          const finalIndScaleX = indScaleX / (1 - velX);
          const finalIndScaleY = indScaleY * (1 - velY);
          scaleX *= finalIndScaleX;
          scaleY *= finalIndScaleY;
          togglePressProgress = Math.max(togglePressProgress, tg.pressProgress);
        }
      }
      let cx, cy;
      cx = r.x + el.rect.w / 2 + translationX + toggleXOffset;
      cy = r.y + el.rect.h / 2 + translationY;
      const sw = el.rect.w * scaleX;
      const sh = el.rect.h * scaleY;
      const sx = cx - sw / 2;
      const sy = cy - sh / 2;
      const cornerRadius = el.cornerRadius * Math.min(scaleX, scaleY);
      const radii = [
        cornerRadius,
        cornerRadius,
        cornerRadius,
        cornerRadius
      ];
      this.gooseBindFBO(otherFbo);
      this.gooseCopy(curTex);
      gl.enable(gl.BLEND);
      gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
      const MARGIN_CSS = 60;
      const scissorX = Math.max(0, Math.round((sx - MARGIN_CSS) * this.dpr));
      const scissorY = Math.max(0, Math.round((this.cssHeight - (sy + sh + MARGIN_CSS)) * this.dpr));
      const scissorW = Math.min(this.fboW - scissorX, Math.round((sw + 2 * MARGIN_CSS) * this.dpr));
      const scissorH = Math.min(this.fboH - scissorY, Math.round((sh + 2 * MARGIN_CSS) * this.dpr));
      gl.enable(gl.SCISSOR_TEST);
      gl.scissor(scissorX, scissorY, scissorW, scissorH);
      const state = {
        el,
        st,
        isButton,
        p,
        sx,
        sy,
        sw,
        sh,
        radii,
        togglePressProgress,
        // For toggle knobs + bottom-tab indicators, the highlight alpha is
        // modulated by pressProgress (faithful to Highlight.Default.copy(alpha=progress)).
        // At rest (progress=0) the alpha should be 0 — so we initialize to 0
        // here, and gooseGlassPass overrides it to alpha*progress when
        // progress > 0. For non-toggle elements, use the static highlight alpha.
        elHighlightAlpha: el.isToggleKnob || el.isBottomTabIndicator ? 0 : el.highlight ? el.highlight.alpha : 0,
        enterAlpha: el.enterProgress != null ? (() => {
          const sp = el.enterSafeProgress != null ? Math.max(0, Math.min(1, el.enterSafeProgress)) : Math.max(0, Math.min(1, el.enterProgress));
          return easeIn(sp);
        })() : 1,
        layerScaleX: scaleX,
        layerScaleY: scaleY,
        layerScale: Math.min(scaleX, scaleY),
        // ORIGINAL geometry (unscaled) for the element-pass SDF. The shader
        // computes SDF/refraction in original space, then maps the refraction
        // offset to screen space via uLayerScale — faithful to the original
        // which shades at original size then scales via graphicsLayer.
        origW: el.rect.w,
        origH: el.rect.h,
        origCornerRadius: el.cornerRadius,
        elementRotation: (_d = el.elementRotation) != null ? _d : 0
      };
      this.gooseGlassShadow(state);
      if (el.useSeparableBlur && el.blurRadius >= 0.5) {
        const blurRadiusPx = el.blurRadius * state.layerScale * this.dpr;
        const backdropSrc = el.backdropFbo && this.dialogBackdropTex ? this.dialogBackdropTex : curTex;
        const blurredBackdrop = this.gooseBlur(backdropSrc, blurRadiusPx);
        this.gl.enable(this.gl.BLEND);
        this.gl.blendFunc(this.gl.SRC_ALPHA, this.gl.ONE_MINUS_SRC_ALPHA);
        this.gooseBindFBO(otherFbo);
        this.gl.viewport(0, 0, this.fboW, this.fboH);
        const passState = el.backdropFbo ? { ...state, el: { ...el, backdropFbo: false } } : state;
        this.gooseGlassPass(passState, blurredBackdrop);
      } else {
        this.gooseGlassPass(state, curTex);
      }
      this.gooseGlassPost(state);
      gl.disable(gl.SCISSOR_TEST);
      return {
        curFbo: otherFbo,
        curTex: otherTex,
        otherFbo: curFbo,
        otherTex: curTex
      };
    },
    gooseGlassShadow(state) {
      const gl = this.gl;
      const { el, sx, sy, sw, sh, radii } = state;
      if (!el.outerShadow || el.outerShadow.alpha <= 1e-3 || el.outerShadow.radius <= 0.5) return;
      let shadowAlpha = el.outerShadow.alpha;
      if (el.isBottomTabIndicator) {
        shadowAlpha = el.outerShadow.alpha * state.togglePressProgress;
        if (shadowAlpha <= 1e-3) return;
      }
      gl.useProgram(this.shadowProgram);
      gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
      gl.enableVertexAttribArray(this.aPosLocSh);
      gl.vertexAttribPointer(this.aPosLocSh, 2, gl.FLOAT, false, 0, 0);
      gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
      gl.uniform2f(this.uSh["uCanvasSize"], this.canvas.width, this.canvas.height);
      gl.uniform2f(this.uSh["uElementOffset"], sx * this.dpr, sy * this.dpr);
      gl.uniform2f(this.uSh["uElementSize"], sw * this.dpr, sh * this.dpr);
      gl.uniform4f(
        this.uSh["uCornerRadii"],
        radii[0] * this.dpr,
        radii[1] * this.dpr,
        radii[2] * this.dpr,
        radii[3] * this.dpr
      );
      gl.uniform2f(this.uSh["uOriginalSize"], state.origW * this.dpr, state.origH * this.dpr);
      gl.uniform1f(this.uSh["uOriginalCornerRadius"], state.origCornerRadius * this.dpr);
      gl.uniform2f(this.uSh["uLayerScale"], state.layerScaleX, state.layerScaleY);
      gl.uniform1f(this.uSh["uElementRotation"], state.elementRotation);
      gl.uniform1f(this.uSh["uCornerStyle"], this.cornerStyle);
      gl.uniform1f(this.uSh["uShadowRadius"], el.outerShadow.radius * this.dpr);
      gl.uniform2f(
        this.uSh["uShadowOffset"],
        el.outerShadow.offsetX * this.dpr,
        el.outerShadow.offsetY * this.dpr
      );
      gl.uniform4f(
        this.uSh["uShadowColor"],
        el.outerShadow.color[0],
        el.outerShadow.color[1],
        el.outerShadow.color[2],
        shadowAlpha
      );
      gl.drawArrays(gl.TRIANGLES, 0, 6);
    }
  };

  // renderer/methods-render-glass-element-pass.ts
  var glassElementPassMethods = {
    /** Step 2b: Element pass — refraction + vibrancy + tint + highlight.
     *  Samples `curTex` (the scene built up so far) to compute refraction
     *  of the actual colors behind the glass (track color, card background,
     *  other glass elements), not just the wallpaper. */
    gooseGlassPass(state, curTex) {
      var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k, _l, _m, _n, _o, _p, _q, _r, _s, _t, _u, _v, _w, _x, _y, _z, _A, _B, _C, _D, _E, _F, _G, _H, _I, _J, _K, _L, _M;
      const gl = this.gl;
      const { el, sx, sy, sw, sh, radii, togglePressProgress, layerScale } = state;
      gl.useProgram(this.elementProgram);
      gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
      gl.enableVertexAttribArray(this.aPosLocEl);
      gl.vertexAttribPointer(this.aPosLocEl, 2, gl.FLOAT, false, 0, 0);
      gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, curTex);
      gl.uniform1i(this.uEl["uBackdrop"], 0);
      if (this.wallpaperTexture) {
        gl.activeTexture(gl.TEXTURE1);
        gl.bindTexture(gl.TEXTURE_2D, this.wallpaperTexture);
        gl.uniform1i(this.uEl["uWallpaperSampler"], 1);
      }
      gl.uniform2f(this.uEl["uCanvasSize"], this.canvas.width, this.canvas.height);
      gl.uniform2f(this.uEl["uWallpaperSize"], this.wallpaperSize[0], this.wallpaperSize[1]);
      gl.uniform2f(this.uEl["uElementOffset"], sx * this.dpr, sy * this.dpr);
      gl.uniform2f(this.uEl["uElementSize"], sw * this.dpr, sh * this.dpr);
      gl.uniform4f(
        this.uEl["uCornerRadii"],
        radii[0] * this.dpr,
        radii[1] * this.dpr,
        radii[2] * this.dpr,
        radii[3] * this.dpr
      );
      gl.uniform2f(this.uEl["uOriginalSize"], state.origW * this.dpr, state.origH * this.dpr);
      gl.uniform1f(this.uEl["uOriginalCornerRadius"], state.origCornerRadius * this.dpr);
      gl.uniform2f(this.uEl["uLayerScale"], state.layerScaleX, state.layerScaleY);
      gl.uniform1f(this.uEl["uElementRotation"], (_a = el.elementRotation) != null ? _a : 0);
      let elRefractionHeight = el.refractionHeight;
      let elRefractionAmount = el.refractionAmount;
      let elBlurRadius = el.blurRadius;
      let elHighlightAlpha = el.highlight ? el.highlight.alpha : 0;
      let elInnerShadowAlpha = el.innerShadow ? el.innerShadow.alpha : 0;
      let elInnerShadowRadius = el.innerShadow ? el.innerShadow.radius : 0;
      let elInnerShadowOffsetX = el.innerShadow ? el.innerShadow.offsetX : 0;
      let elInnerShadowOffsetY = el.innerShadow ? el.innerShadow.offsetY : 0;
      let elSurfaceAlpha = el.surfaceColor[3];
      if (el.isBottomTabIndicator) {
        const progress = togglePressProgress;
        elRefractionHeight = el.refractionHeight * progress;
        elRefractionAmount = el.refractionAmount * progress;
        elBlurRadius = 0;
        elHighlightAlpha = ((_c = (_b = el.highlight) == null ? void 0 : _b.alpha) != null ? _c : 0) * progress;
        elInnerShadowAlpha = ((_e = (_d = el.innerShadow) == null ? void 0 : _d.alpha) != null ? _e : 0) * progress;
        elInnerShadowRadius = ((_g = (_f = el.innerShadow) == null ? void 0 : _f.radius) != null ? _g : 0) * progress;
        elInnerShadowOffsetX = ((_i = (_h = el.innerShadow) == null ? void 0 : _h.offsetX) != null ? _i : 0) * progress;
        elInnerShadowOffsetY = ((_k = (_j = el.innerShadow) == null ? void 0 : _j.offsetY) != null ? _k : 0) * progress;
      }
      let elContentScaleX = 1;
      let elContentScaleY = 1;
      let useToggleBackdrop = 0;
      let useSolidBackdrop = 0;
      let solidR = 1, solidG = 1, solidB = 1, solidA = 1;
      let trackColorR = 0, trackColorG = 0, trackColorB = 0, trackColorA = 0;
      let trackCenterX = 0, trackCenterY = 0, trackHalfW = 0, trackHalfH = 0;
      let trackCornerRadius = 0;
      if (el.isToggleKnob) {
        const progress = togglePressProgress;
        elRefractionHeight = el.refractionHeight * progress;
        elRefractionAmount = el.refractionAmount * progress;
        elBlurRadius = 8 * (1 - progress);
        elHighlightAlpha = ((_m = (_l = el.highlight) == null ? void 0 : _l.alpha) != null ? _m : 0) * progress;
        elInnerShadowAlpha = ((_o = (_n = el.innerShadow) == null ? void 0 : _n.alpha) != null ? _o : 0) * progress;
        elInnerShadowRadius = ((_q = (_p = el.innerShadow) == null ? void 0 : _p.radius) != null ? _q : 0) * progress;
        elInnerShadowOffsetX = ((_s = (_r = el.innerShadow) == null ? void 0 : _r.offsetX) != null ? _s : 0) * progress;
        elInnerShadowOffsetY = ((_u = (_t = el.innerShadow) == null ? void 0 : _t.offsetY) != null ? _u : 0) * progress;
        elSurfaceAlpha = 0;
        const isSlider = el.isToggleKnob.velocityDivisor === 10;
        const xEnd = isSlider ? 1 : 0.75;
        const yEnd = isSlider ? 1 : 0.75;
        elContentScaleX = 2 / 3 + (xEnd - 2 / 3) * progress;
        elContentScaleY = 0 + (yEnd - 0) * progress;
        if (el.isToggleKnob.trackColorOff && el.isToggleKnob.trackColorOn && el.isToggleKnob.trackW && el.isToggleKnob.trackH) {
          const tg = this.toggleStates.get(el.isToggleKnob.groupId);
          const fraction = tg ? tg.fraction : 0;
          const off = el.isToggleKnob.trackColorOff;
          const on = el.isToggleKnob.trackColorOn;
          trackColorR = off[0] + (on[0] - off[0]) * fraction;
          trackColorG = off[1] + (on[1] - off[1]) * fraction;
          trackColorB = off[2] + (on[2] - off[2]) * fraction;
          trackColorA = off[3] + (on[3] - off[3]) * fraction;
          const knobCenterX = (sx + sw / 2) * this.dpr;
          const knobCenterY = (sy + sh / 2) * this.dpr;
          const trackOrigX = (_v = el.isToggleKnob.trackOriginalX) != null ? _v : el.rect.x;
          const trackOrigY = (_w = el.isToggleKnob.trackOriginalY) != null ? _w : el.rect.y;
          const trackOrigCenterX = (trackOrigX + el.isToggleKnob.trackW / 2) * this.dpr;
          const trackOrigCenterY = (trackOrigY + el.isToggleKnob.trackH / 2) * this.dpr;
          const trackScaleX = 2 / 3 + (xEnd - 2 / 3) * progress;
          const trackScaleY = 0 + (yEnd - 0) * progress;
          trackCenterX = knobCenterX + (trackOrigCenterX - knobCenterX) * trackScaleX;
          trackCenterY = knobCenterY + (trackOrigCenterY - knobCenterY) * trackScaleY;
          const trackW = el.isToggleKnob.trackW * this.dpr;
          const trackH = el.isToggleKnob.trackH * this.dpr;
          trackHalfW = trackW * trackScaleX * 0.5;
          trackHalfH = trackH * trackScaleY * 0.5;
          trackCornerRadius = trackH * 0.5 * Math.min(trackScaleX, trackScaleY);
          useToggleBackdrop = 1;
          if (el.isToggleKnob.solidBackdropColor) {
            const sd = el.isToggleKnob.solidBackdropColor;
            solidR = sd[0];
            solidG = sd[1];
            solidB = sd[2];
            solidA = sd[3];
            useSolidBackdrop = 1;
          }
          elContentScaleX = 1;
          elContentScaleY = 1;
        }
      }
      let useIndicatorBackdrop = 0;
      let containerRectX = 0, containerRectY = 0, containerHalfW = 0, containerHalfH = 0;
      let containerCornerRadius = 0;
      let indicatorAccentR = 0, indicatorAccentG = 0, indicatorAccentB = 0, indicatorAccentA = 0;
      if (el.isBottomTabIndicator) {
        const progress = togglePressProgress;
        elRefractionHeight = el.refractionHeight * progress;
        elRefractionAmount = el.refractionAmount * progress;
        elHighlightAlpha = ((_y = (_x = el.highlight) == null ? void 0 : _x.alpha) != null ? _y : 0) * progress;
        elInnerShadowAlpha = ((_A = (_z = el.innerShadow) == null ? void 0 : _z.alpha) != null ? _A : 0) * progress;
        elInnerShadowRadius = ((_C = (_B = el.innerShadow) == null ? void 0 : _B.radius) != null ? _C : 0) * progress;
        elInnerShadowOffsetX = ((_E = (_D = el.innerShadow) == null ? void 0 : _D.offsetX) != null ? _E : 0) * progress;
        elInnerShadowOffsetY = ((_G = (_F = el.innerShadow) == null ? void 0 : _F.offsetY) != null ? _G : 0) * progress;
        if (el.isBottomTabIndicator.accentColor && el.isBottomTabIndicator.containerRect) {
          const ac = el.isBottomTabIndicator.accentColor;
          const cr = el.isBottomTabIndicator.containerRect;
          indicatorAccentR = ac[0];
          indicatorAccentG = ac[1];
          indicatorAccentB = ac[2];
          indicatorAccentA = 1;
          containerRectX = (cr.x + cr.w / 2) * this.dpr;
          containerRectY = (cr.y + cr.h / 2) * this.dpr;
          containerHalfW = cr.w / 2 * this.dpr;
          containerHalfH = cr.h / 2 * this.dpr;
          containerCornerRadius = cr.h / 2 * this.dpr;
          useIndicatorBackdrop = 1;
        }
      }
      gl.uniform1f(this.uEl["uUseToggleBackdrop"], useToggleBackdrop);
      gl.uniform1f(this.uEl["uUseSolidBackdrop"], useSolidBackdrop);
      gl.uniform4f(this.uEl["uSolidBackdropColor"], solidR, solidG, solidB, solidA);
      gl.uniform4f(this.uEl["uTrackColor"], trackColorR, trackColorG, trackColorB, trackColorA);
      gl.uniform4f(this.uEl["uTrackRect"], trackCenterX, trackCenterY, trackHalfW, trackHalfH);
      gl.uniform1f(this.uEl["uTrackCornerRadius"], trackCornerRadius);
      gl.uniform1f(this.uEl["uIndicatorBackdrop"], useIndicatorBackdrop);
      gl.uniform4f(this.uEl["uContainerRect"], containerRectX, containerRectY, containerHalfW, containerHalfH);
      gl.uniform1f(this.uEl["uContainerCornerRadius"], containerCornerRadius);
      gl.uniform4f(this.uEl["uIndicatorAccent"], indicatorAccentR, indicatorAccentG, indicatorAccentB, indicatorAccentA);
      gl.uniform1f(this.uEl["uInsetPx"], 4 * this.dpr);
      if (el.isBottomTabIndicator) {
        const tg = this.toggleStates.get(el.isBottomTabIndicator.groupId);
        gl.uniform1f(this.uEl["uIndicatorPressProgress"], tg ? tg.pressProgress : 0);
        gl.uniform1f(this.uEl["uIndicatorPanelOffset"], tg ? tg.panelOffset * this.dpr : 0);
        gl.uniform1f(this.uEl["uDpr"], this.dpr);
        const ccx = (_H = el.isBottomTabIndicator.containerCenterX) != null ? _H : 0;
        const ccy = (_I = el.isBottomTabIndicator.containerCenterY) != null ? _I : 0;
        const cw = (_J = el.isBottomTabIndicator.containerWidth) != null ? _J : el.rect.w;
        const cScale = tg ? 1 + 16 * gooseDP / cw * tg.pressProgress : 1;
        gl.uniform2f(this.uEl["uContainerCenter"], ccx * this.dpr, ccy * this.dpr);
        gl.uniform1f(this.uEl["uContainerScale"], cScale);
        const ids = (_K = el.isBottomTabIndicator.tabContentIds) != null ? _K : [];
        const rects = (_L = el.isBottomTabIndicator.tabContentRects) != null ? _L : [];
        const n = Math.min(ids.length, rects.length, 8);
        let boundCount = 0;
        for (let i = 0; i < 8; i++) {
          if (i < n) {
            const tex = this.fgTextures.get(ids[i]);
            if (tex) {
              gl.activeTexture(gl.TEXTURE3 + boundCount);
              gl.bindTexture(gl.TEXTURE_2D, tex);
              gl.uniform1i(this.uEl[`uTabContentTex${boundCount}`], 3 + boundCount);
              const r = rects[i];
              gl.uniform4f(
                this.uEl[`uTabContentRects[${boundCount}]`],
                (r.x + r.w / 2) * this.dpr,
                (r.y + r.h / 2) * this.dpr,
                r.w / 2 * this.dpr,
                r.h / 2 * this.dpr
              );
              boundCount++;
            }
          }
        }
        for (let i = boundCount; i < 8; i++) {
          gl.uniform4f(this.uEl[`uTabContentRects[${i}]`], 0, 0, 0, 0);
        }
        gl.uniform1f(this.uEl["uTabContentCount"], boundCount);
        if (this.tabsBackdropTex) {
          gl.activeTexture(gl.TEXTURE11);
          gl.bindTexture(gl.TEXTURE_2D, this.tabsBackdropTex);
          gl.uniform1i(this.uEl["uTabsGlassLayer"], 11);
        }
      } else {
        gl.uniform1f(this.uEl["uIndicatorPressProgress"], 0);
        gl.uniform1f(this.uEl["uIndicatorPanelOffset"], 0);
        gl.uniform1f(this.uEl["uDpr"], this.dpr);
        gl.uniform2f(this.uEl["uContainerCenter"], 0, 0);
        gl.uniform1f(this.uEl["uContainerScale"], 1);
        gl.uniform1f(this.uEl["uTabContentCount"], 0);
      }
      gl.uniform1f(this.uEl["uRefractionHeight"], elRefractionHeight * this.dpr);
      gl.uniform1f(this.uEl["uRefractionAmount"], elRefractionAmount * this.dpr);
      gl.uniform1f(this.uEl["uDepthEffect"], el.depthEffect ? 1 : 0);
      gl.uniform1f(this.uEl["uChromaticAberration"], el.chromaticAberration ? 1 : 0);
      const inlineBlurRadius = el.useSeparableBlur && el.blurRadius >= 0.5 ? 0 : elBlurRadius;
      gl.uniform1f(this.uEl["uBlurRadius"], inlineBlurRadius * layerScale * this.dpr);
      gl.uniform1f(this.uEl["uSaturation"], el.saturation);
      gl.uniform1f(this.uEl["uBrightness"], el.brightness);
      gl.uniform1f(this.uEl["uContrast"], el.contrast);
      gl.uniform1f(this.uEl["uContentScaleX"], elContentScaleX);
      gl.uniform1f(this.uEl["uContentScaleY"], elContentScaleY);
      gl.uniform4f(this.uEl["uTintColor"], el.tintColor[0], el.tintColor[1], el.tintColor[2], el.tintColor[3]);
      gl.uniform4f(this.uEl["uSurfaceColor"], el.surfaceColor[0], el.surfaceColor[1], el.surfaceColor[2], elSurfaceAlpha);
      if (el.highlight) {
        gl.uniform3f(this.uEl["uHighlightColor"], el.highlight.color[0], el.highlight.color[1], el.highlight.color[2]);
        gl.uniform1f(this.uEl["uHighlightAngle"], el.highlight.angle);
        gl.uniform1f(this.uEl["uHighlightFalloff"], el.highlight.falloff);
        gl.uniform1f(this.uEl["uHighlightAlpha"], elHighlightAlpha);
        gl.uniform1f(this.uEl["uHighlightMode"], el.highlight.mode);
        const elMinDimPx = Math.min(state.origW, state.origH) * this.dpr;
        const elWidthPx = Math.min(el.highlight.widthDp * this.dpr, elMinDimPx * 0.5);
        const elBlurPx = ((_M = el.highlight.blurRadiusDp) != null ? _M : el.highlight.widthDp / 2) * this.dpr;
        gl.uniform1f(this.uEl["uHighlightStrokeWidth"], Math.ceil(elWidthPx) * 2);
        gl.uniform1f(this.uEl["uHighlightBlur"], elBlurPx);
      } else {
        gl.uniform1f(this.uEl["uHighlightAlpha"], 0);
        gl.uniform1f(this.uEl["uHighlightMode"], 0);
        gl.uniform1f(this.uEl["uHighlightStrokeWidth"], 0);
        gl.uniform1f(this.uEl["uHighlightBlur"], 0);
      }
      if (el.isSdfTexture && this.sdfTexture) {
        gl.activeTexture(gl.TEXTURE2);
        gl.bindTexture(gl.TEXTURE_2D, this.sdfTexture);
        gl.uniform1i(this.uEl["uSdfTexSampler"], 2);
        gl.uniform1f(this.uEl["uUseSdfTexture"], 1);
        gl.uniform2f(this.uEl["uSdfTexSize"], this.sdfTextureSize[0], this.sdfTextureSize[1]);
        gl.uniform1f(this.uEl["uSdfLightAngle"], el.isSdfTexture.lightAngle);
        gl.uniform1f(this.uEl["uRefractionHeight"], el.isSdfTexture.refractionHeight * this.dpr);
      } else {
        gl.uniform1f(this.uEl["uUseSdfTexture"], 0);
      }
      if (el.useContinuousSdf && this.continuousSdfTexture) {
        gl.activeTexture(gl.TEXTURE2);
        gl.bindTexture(gl.TEXTURE_2D, this.continuousSdfTexture);
        gl.uniform1i(this.uEl["uContinuousSdf"], 2);
        gl.uniform1f(this.uEl["uUseContinuousSdf"], 1);
        gl.uniform2f(this.uEl["uContinuousSdfTexSize"], this.continuousSdfTexSize[0], this.continuousSdfTexSize[1]);
        gl.uniform2f(this.uEl["uContinuousSdfElementSize"], state.origW * this.dpr, state.origH * this.dpr);
      } else {
        gl.uniform1f(this.uEl["uUseContinuousSdf"], 0);
      }
      gl.uniform1f(this.uEl["uEnterAlpha"], state.enterAlpha);
      gl.uniform1f(this.uEl["uCornerStyle"], this.cornerStyle);
      if (el.isMagnifier) {
        gl.uniform1f(this.uEl["uUseMagnifier"], 1);
        gl.uniform1f(this.uEl["uMagnifierZoom"], el.isMagnifier.zoom);
        gl.uniform1f(this.uEl["uMagnifierOffsetY"], el.isMagnifier.sampleOffsetY * this.dpr);
      } else {
        gl.uniform1f(this.uEl["uUseMagnifier"], 0);
      }
      gl.uniform1f(this.uEl["uSkipColorControls"], el.backdropFbo && el.useSeparableBlur && el.blurRadius >= 0.5 ? 1 : 0);
      gl.drawArrays(gl.TRIANGLES, 0, 6);
      state.elHighlightAlpha = elHighlightAlpha;
    }
  };

  // renderer/inner-shadow-mask.ts
  function gooseBuildInnerShadowPath(w, h, radius, useG2) {
    if (useG2) {
      const dummyCanvas = document.createElement("canvas");
      dummyCanvas.width = 1;
      dummyCanvas.height = 1;
      const dummyCtx = dummyCanvas.getContext("2d");
      return continuousCurvatureRoundedRectPath(dummyCtx, w, h, radius);
    }
    const path = new Path2D();
    if (typeof path.roundRect === "function") {
      path.roundRect(0, 0, w, h, radius);
    } else {
      const r = Math.min(radius, w / 2, h / 2);
      path.moveTo(r, 0);
      path.lineTo(w - r, 0);
      path.arcTo(w, 0, w, r, r);
      path.lineTo(w, h - r);
      path.arcTo(w, h, w - r, h, r);
      path.lineTo(r, h);
      path.arcTo(0, h, 0, h - r, r);
      path.lineTo(0, r);
      path.arcTo(0, 0, r, 0, r);
      path.closePath();
    }
    return path;
  }
  function gooseCreateMaskCanvas(w, h) {
    const canvas = document.createElement("canvas");
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext("2d", { alpha: true, willReadFrequently: true });
    return { canvas, ctx };
  }
  function gooseGenerateInnerShadowMask(params) {
    const { w, h, radius, offsetX, offsetY, blurSigma, margin, useG2, supersample: SS } = params;
    const maskW = Math.max(1, Math.ceil(w + 2 * margin));
    const maskH = Math.max(1, Math.ceil(h + 2 * margin));
    const canvasW = maskW * SS;
    const canvasH = maskH * SS;
    const { canvas: tempCanvas, ctx: tCtx } = gooseCreateMaskCanvas(canvasW, canvasH);
    const { canvas: outputCanvas, ctx: oCtx } = gooseCreateMaskCanvas(canvasW, canvasH);
    tCtx.save();
    tCtx.scale(SS, SS);
    tCtx.translate(margin, margin);
    const path = gooseBuildInnerShadowPath(w, h, radius, useG2);
    tCtx.clip(path);
    tCtx.globalCompositeOperation = "source-over";
    tCtx.fillStyle = "white";
    tCtx.fill(path);
    tCtx.globalCompositeOperation = "destination-out";
    tCtx.save();
    tCtx.translate(offsetX, offsetY);
    tCtx.fill(path);
    tCtx.restore();
    tCtx.globalCompositeOperation = "source-over";
    tCtx.restore();
    if (blurSigma > 0.01) {
      oCtx.filter = `blur(${blurSigma * SS}px)`;
    } else {
      oCtx.filter = "none";
    }
    oCtx.drawImage(tempCanvas, 0, 0);
    oCtx.filter = "none";
    return { canvas: outputCanvas, maskW, maskH, margin };
  }

  // renderer/methods-render-glass-post-passes.ts
  var glassPostPassMethods = {
    /** Steps 2b–2g: Inner shadow, press glow, white overlay, foreground,
     *  rim highlight. These all composite on top of the glass body (already
     *  drawn to otherFbo by gooseGlassPass). */
    gooseGlassPost(state) {
      var _a, _b;
      const gl = this.gl;
      const { el, st, isButton, p, sx, sy, sw, sh, radii, togglePressProgress, elHighlightAlpha } = state;
      const origSizeX = state.origW * this.dpr;
      const origSizeY = state.origH * this.dpr;
      const origRadius = state.origCornerRadius * this.dpr;
      const layerScaleX = state.layerScaleX;
      const layerScaleY = state.layerScaleY;
      const gooseDrawInnerShadowPass = (shadowCfg, shadowIndex) => {
        var _a2;
        const progress = el.isToggleKnob || el.isBottomTabIndicator ? togglePressProgress : 1;
        let shadowAlpha = shadowCfg.alpha * progress * state.enterAlpha;
        let shadowRadius = shadowCfg.radius * progress;
        let shadowOffsetX = shadowCfg.offsetX * progress;
        let shadowOffsetY = shadowCfg.offsetY * progress;
        if (shadowAlpha <= 1e-3 || shadowRadius <= 0.5) return;
        const blurSigma = shadowRadius * this.dpr;
        const margin = Math.ceil(blurSigma * 3) + 2;
        const maskW = Math.max(1, Math.ceil(origSizeX + 2 * margin));
        const maskH = Math.max(1, Math.ceil(origSizeY + 2 * margin));
        const deviceDpr = window.devicePixelRatio || 1;
        const SS = Math.min(2, Math.max(1, Math.floor(deviceDpr / this.dpr)));
        const useG2 = !!el.useContinuousSdf;
        const offsetXDp = shadowOffsetX * this.dpr;
        const offsetYDp = shadowOffsetY * this.dpr;
        const maskParams = {
          w: origSizeX,
          h: origSizeY,
          radius: origRadius,
          offsetX: offsetXDp,
          offsetY: offsetYDp,
          blurSigma,
          margin,
          useG2,
          supersample: SS
        };
        const key = gooseBuildInnerShadowMaskKey(shadowIndex, maskParams);
        const entry = gooseGetOrCreateMaskEntry(this.innerShadowMaskCache, gl, key, maskW, maskH);
        if (!entry.ready) {
          const result = gooseGenerateInnerShadowMask(maskParams);
          gooseUploadMaskTexture(gl, entry, result);
        }
        gl.enable(gl.BLEND);
        gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
        gl.useProgram(this.innerShadowMaskCompositeProgram);
        gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
        gl.enableVertexAttribArray(this.aPosLocIs);
        gl.vertexAttribPointer(this.aPosLocIs, 2, gl.FLOAT, false, 0, 0);
        gl.uniform2f(this.uIs["uCanvasSize"], this.canvas.width, this.canvas.height);
        gl.uniform2f(this.uIs["uOffset"], sx * this.dpr, sy * this.dpr);
        gl.uniform2f(this.uIs["uSize"], sw * this.dpr, sh * this.dpr);
        gl.uniform4f(this.uIs["uCornerRadii"], radii[0] * this.dpr, radii[1] * this.dpr, radii[2] * this.dpr, radii[3] * this.dpr);
        gl.activeTexture(gl.TEXTURE0);
        gl.bindTexture(gl.TEXTURE_2D, entry.tex);
        gl.uniform1i(this.uIs["uInnerShadowMask"], 0);
        gl.uniform2f(this.uIs["uMaskOffset"], margin, margin);
        gl.uniform2f(this.uIs["uMaskSize"], entry.w, entry.h);
        const color = (_a2 = shadowCfg.color) != null ? _a2 : [0, 0, 0];
        gl.uniform3f(this.uIs["uInnerShadowColor"], color[0], color[1], color[2]);
        gl.uniform1f(this.uIs["uInnerShadowAlpha"], shadowAlpha);
        gl.uniform2f(this.uIs["uOriginalSize"], origSizeX, origSizeY);
        gl.uniform1f(this.uIs["uOriginalCornerRadius"], origRadius);
        gl.uniform2f(this.uIs["uLayerScale"], layerScaleX, layerScaleY);
        gl.uniform1f(this.uIs["uElementRotation"], state.elementRotation);
        gl.drawArrays(gl.TRIANGLES, 0, 6);
      };
      if (el.innerShadow) {
        gooseDrawInnerShadowPass(el.innerShadow, 0);
      }
      const isContainer = !!el.isBottomTabContainer;
      const glowP = isButton ? p : isContainer ? togglePressProgress : 0;
      if (isButton && el.isInteractive && st && p > 1e-3 || isContainer && togglePressProgress > 1e-3) {
        gl.useProgram(this.tintProgram);
        gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
        gl.enableVertexAttribArray(this.aPosLocTn);
        gl.vertexAttribPointer(this.aPosLocTn, 2, gl.FLOAT, false, 0, 0);
        gl.blendFunc(gl.SRC_ALPHA, gl.ONE);
        gl.uniform2f(this.uTn["uCanvasSize"], this.canvas.width, this.canvas.height);
        gl.uniform2f(this.uTn["uOffset"], sx * this.dpr, sy * this.dpr);
        gl.uniform2f(this.uTn["uSize"], sw * this.dpr, sh * this.dpr);
        gl.uniform4f(
          this.uTn["uCornerRadii"],
          radii[0] * this.dpr,
          radii[1] * this.dpr,
          radii[2] * this.dpr,
          radii[3] * this.dpr
        );
        gl.uniform2f(this.uTn["uOriginalSize"], origSizeX, origSizeY);
        gl.uniform1f(this.uTn["uOriginalCornerRadius"], origRadius);
        gl.uniform2f(this.uTn["uLayerScale"], layerScaleX, layerScaleY);
        gl.uniform1f(this.uTn["uElementRotation"], state.elementRotation);
        gl.uniform1f(this.uTn["uCornerStyle"], this.cornerStyle);
        gl.uniform4f(this.uTn["uColor"], 1, 1, 1, 0.08 * glowP);
        gl.drawArrays(gl.TRIANGLES, 0, 6);
        gl.useProgram(this.highlightProgram);
        gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
        gl.enableVertexAttribArray(this.aPosLocHl);
        gl.vertexAttribPointer(this.aPosLocHl, 2, gl.FLOAT, false, 0, 0);
        gl.blendFunc(gl.ONE, gl.ONE);
        gl.uniform2f(this.uHl["uCanvasSize"], this.canvas.width, this.canvas.height);
        gl.uniform2f(this.uHl["uOffset"], sx * this.dpr, sy * this.dpr);
        gl.uniform2f(this.uHl["uSize"], sw * this.dpr, sh * this.dpr);
        gl.uniform4f(
          this.uHl["uCornerRadii"],
          radii[0] * this.dpr,
          radii[1] * this.dpr,
          radii[2] * this.dpr,
          radii[3] * this.dpr
        );
        gl.uniform2f(this.uHl["uOriginalSize"], origSizeX, origSizeY);
        gl.uniform1f(this.uHl["uOriginalCornerRadius"], origRadius);
        gl.uniform2f(this.uHl["uLayerScale"], layerScaleX, layerScaleY);
        gl.uniform1f(this.uHl["uElementRotation"], state.elementRotation);
        gl.uniform1f(this.uHl["uCornerStyle"], this.cornerStyle);
        gl.uniform4f(this.uHl["uColor"], 1, 1, 1, 0.15 * glowP);
        const minDim = Math.min(sw, sh) * this.dpr;
        gl.uniform1f(this.uHl["uRadius"], minDim * 1.5);
        let px, py;
        if (isContainer) {
          const tg = this.toggleStates.get(el.isBottomTabContainer.groupId);
          const tabsCount = (_a = el.isBottomTabContainer.tabsCount) != null ? _a : 4;
          const tabW = el.rect.w / tabsCount;
          const fraction = tg ? tg.fraction : 0;
          const indCenterX = (fraction + 0.5) * tabW;
          const scaleToLocal = sw / el.rect.w;
          px = Math.max(0, Math.min(sw, indCenterX * scaleToLocal)) * this.dpr;
          py = sh / 2 * this.dpr;
        } else {
          px = Math.max(0, Math.min(sw, st.dragX * state.layerScaleX)) * this.dpr;
          py = Math.max(0, Math.min(sh, st.dragY * state.layerScaleY)) * this.dpr;
        }
        gl.uniform2f(this.uHl["uPosition"], px, py);
        gl.drawArrays(gl.TRIANGLES, 0, 6);
        gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
      }
      if (el.isToggleKnob && togglePressProgress < 0.999) {
        const whiteAlpha = 1 * (1 - togglePressProgress);
        gl.useProgram(this.tintProgram);
        gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
        gl.enableVertexAttribArray(this.aPosLocTn);
        gl.vertexAttribPointer(this.aPosLocTn, 2, gl.FLOAT, false, 0, 0);
        gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
        gl.uniform2f(this.uTn["uCanvasSize"], this.canvas.width, this.canvas.height);
        gl.uniform2f(this.uTn["uOffset"], sx * this.dpr, sy * this.dpr);
        gl.uniform2f(this.uTn["uSize"], sw * this.dpr, sh * this.dpr);
        gl.uniform4f(
          this.uTn["uCornerRadii"],
          radii[0] * this.dpr,
          radii[1] * this.dpr,
          radii[2] * this.dpr,
          radii[3] * this.dpr
        );
        gl.uniform2f(this.uTn["uOriginalSize"], origSizeX, origSizeY);
        gl.uniform1f(this.uTn["uOriginalCornerRadius"], origRadius);
        gl.uniform2f(this.uTn["uLayerScale"], layerScaleX, layerScaleY);
        gl.uniform1f(this.uTn["uElementRotation"], state.elementRotation);
        gl.uniform1f(this.uTn["uCornerStyle"], this.cornerStyle);
        gl.uniform4f(this.uTn["uColor"], 1, 1, 1, whiteAlpha);
        gl.drawArrays(gl.TRIANGLES, 0, 6);
      }
      if (el.isBottomTabIndicator && el.isBottomTabIndicator.dimColor) {
        const dc = el.isBottomTabIndicator.dimColor;
        const p2 = togglePressProgress;
        gl.useProgram(this.tintProgram);
        gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
        gl.enableVertexAttribArray(this.aPosLocTn);
        gl.vertexAttribPointer(this.aPosLocTn, 2, gl.FLOAT, false, 0, 0);
        gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
        gl.uniform2f(this.uTn["uCanvasSize"], this.canvas.width, this.canvas.height);
        gl.uniform2f(this.uTn["uOffset"], sx * this.dpr, sy * this.dpr);
        gl.uniform2f(this.uTn["uSize"], sw * this.dpr, sh * this.dpr);
        gl.uniform4f(this.uTn["uCornerRadii"], radii[0] * this.dpr, radii[1] * this.dpr, radii[2] * this.dpr, radii[3] * this.dpr);
        gl.uniform2f(this.uTn["uOriginalSize"], origSizeX, origSizeY);
        gl.uniform1f(this.uTn["uOriginalCornerRadius"], origRadius);
        gl.uniform2f(this.uTn["uLayerScale"], layerScaleX, layerScaleY);
        gl.uniform1f(this.uTn["uElementRotation"], state.elementRotation);
        gl.uniform1f(this.uTn["uCornerStyle"], this.cornerStyle);
        gl.uniform4f(this.uTn["uColor"], dc[0], dc[1], dc[2], 0.1 * (1 - p2));
        gl.drawArrays(gl.TRIANGLES, 0, 6);
        gl.uniform4f(this.uTn["uColor"], 0, 0, 0, 0.03 * p2);
        gl.drawArrays(gl.TRIANGLES, 0, 6);
        gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
      }
      if (isButton && (el.label || el.icon)) {
        const fgTex = this.fgTextures.get(el.id);
        if (fgTex) {
          gl.useProgram(this.foregroundProgram);
          gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
          gl.enableVertexAttribArray(this.aPosLocFg);
          gl.vertexAttribPointer(this.aPosLocFg, 2, gl.FLOAT, false, 0, 0);
          gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
          gl.activeTexture(gl.TEXTURE0);
          gl.bindTexture(gl.TEXTURE_2D, fgTex);
          gl.uniform1i(this.uFg["uTexture"], 0);
          gl.uniform2f(this.uFg["uCanvasSize"], this.canvas.width, this.canvas.height);
          gl.uniform2f(this.uFg["uOffset"], sx * this.dpr, sy * this.dpr);
          gl.uniform2f(this.uFg["uSize"], sw * this.dpr, sh * this.dpr);
          gl.uniform4f(
            this.uFg["uCornerRadii"],
            radii[0] * this.dpr,
            radii[1] * this.dpr,
            radii[2] * this.dpr,
            radii[3] * this.dpr
          );
          gl.uniform2f(this.uFg["uOriginalSize"], origSizeX, origSizeY);
          gl.uniform1f(this.uFg["uOriginalCornerRadius"], origRadius);
          gl.uniform2f(this.uFg["uLayerScale"], layerScaleX, layerScaleY);
          gl.uniform1f(this.uFg["uCornerStyle"], this.cornerStyle);
          if (el.useContinuousSdf && this.continuousSdfTexture) {
            gl.activeTexture(gl.TEXTURE2);
            gl.bindTexture(gl.TEXTURE_2D, this.continuousSdfTexture);
            gl.uniform1i(this.uFg["uContinuousSdf"], 2);
            gl.uniform1f(this.uFg["uUseContinuousSdf"], 1);
            gl.uniform2f(this.uFg["uContinuousSdfTexSize"], this.continuousSdfTexSize[0], this.continuousSdfTexSize[1]);
            gl.uniform2f(this.uFg["uContinuousSdfElementSize"], state.origW * this.dpr, state.origH * this.dpr);
          } else {
            gl.uniform1f(this.uFg["uUseContinuousSdf"], 0);
          }
          gl.uniform1f(this.uFg["uAlpha"], 1 - 0.15 * p);
          gl.drawArrays(gl.TRIANGLES, 0, 6);
          gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
        }
      }
      if (el.highlight && el.highlight.alpha > 1e-3) {
        const rimAlpha = el.isToggleKnob || el.isBottomTabIndicator ? elHighlightAlpha : el.highlight.alpha;
        const finalAlpha = rimAlpha * state.enterAlpha;
        if (finalAlpha > 1e-3) {
          const widthPx = Math.min(
            el.highlight.widthDp * this.dpr,
            Math.min(origSizeX, origSizeY) * 0.5
          );
          const strokeWidthDevice = Math.max(1, Math.ceil(widthPx) * 2);
          const blurPx = Math.max(0, ((_b = el.highlight.blurRadiusDp) != null ? _b : el.highlight.widthDp / 2) * this.dpr);
          const strokeMargin = Math.ceil(strokeWidthDevice) + 4;
          const maskW = Math.max(1, Math.ceil(origSizeX + 2 * strokeMargin));
          const maskH = Math.max(1, Math.ceil(origSizeY + 2 * strokeMargin));
          const useG2 = !!el.useContinuousSdf;
          const maskKey = [
            useG2 ? "g2" : "rr",
            origSizeX.toFixed(3),
            origSizeY.toFixed(3),
            origRadius.toFixed(3),
            strokeWidthDevice,
            blurPx.toFixed(3),
            strokeMargin,
            maskW,
            maskH
          ].join(":");
          let mask = this.strokeMaskCache.get(maskKey);
          if (!mask) {
            const canvas = document.createElement("canvas");
            canvas.width = maskW;
            canvas.height = maskH;
            const ctx = canvas.getContext("2d", { alpha: true, willReadFrequently: true });
            if (!ctx) throw new Error("2D canvas not supported");
            const tex = gl.createTexture();
            if (!tex) throw new Error("WebGL texture allocation failed");
            mask = { tex, canvas, ctx, w: maskW, h: maskH, ready: false };
            this.strokeMaskCache.set(maskKey, mask);
            if (this.strokeMaskCache.size > 32) {
              const oldestKey = this.strokeMaskCache.keys().next().value;
              if (oldestKey && oldestKey !== maskKey) {
                const oldest = this.strokeMaskCache.get(oldestKey);
                if (oldest) gl.deleteTexture(oldest.tex);
                this.strokeMaskCache.delete(oldestKey);
              }
            }
          }
          if (!mask.ready) {
            const smCtx = mask.ctx;
            smCtx.clearRect(0, 0, mask.w, mask.h);
            smCtx.save();
            smCtx.translate(strokeMargin, strokeMargin);
            let path;
            if (useG2) {
              path = continuousCurvatureRoundedRectPath(smCtx, origSizeX, origSizeY, origRadius);
            } else {
              path = new Path2D();
              const r = Math.min(origRadius, origSizeX / 2, origSizeY / 2);
              path.moveTo(r, 0);
              path.lineTo(origSizeX - r, 0);
              path.arcTo(origSizeX, 0, origSizeX, r, r);
              path.lineTo(origSizeX, origSizeY - r);
              path.arcTo(origSizeX, origSizeY, origSizeX - r, origSizeY, r);
              path.lineTo(r, origSizeY);
              path.arcTo(0, origSizeY, 0, origSizeY - r, r);
              path.lineTo(0, r);
              path.arcTo(0, 0, r, 0, r);
              path.closePath();
            }
            smCtx.clip(path);
            smCtx.lineWidth = strokeWidthDevice;
            smCtx.strokeStyle = "rgba(255,255,255,1)";
            smCtx.lineJoin = "round";
            smCtx.lineCap = "round";
            smCtx.filter = blurPx > 0.01 ? `blur(${blurPx}px)` : "none";
            smCtx.stroke(path);
            smCtx.filter = "none";
            smCtx.restore();
            gl.bindTexture(gl.TEXTURE_2D, mask.tex);
            gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
            uploadCanvasAsPixels(gl, mask.canvas);
            gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
            gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
            gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
            gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
            mask.ready = true;
          }
          gl.enable(gl.BLEND);
          if (el.highlight.mode === 1) {
            gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
          } else {
            gl.blendFunc(gl.ONE, gl.ONE);
          }
          gl.useProgram(this.strokeMaskCompositeProgram);
          gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
          gl.enableVertexAttribArray(this.aPosLocSm);
          gl.vertexAttribPointer(this.aPosLocSm, 2, gl.FLOAT, false, 0, 0);
          gl.uniform2f(this.uSm["uCanvasSize"], this.canvas.width, this.canvas.height);
          gl.uniform2f(this.uSm["uOffset"], sx * this.dpr, sy * this.dpr);
          gl.uniform2f(this.uSm["uSize"], sw * this.dpr, sh * this.dpr);
          gl.uniform4f(this.uSm["uCornerRadii"], radii[0] * this.dpr, radii[1] * this.dpr, radii[2] * this.dpr, radii[3] * this.dpr);
          gl.activeTexture(gl.TEXTURE0);
          gl.bindTexture(gl.TEXTURE_2D, mask.tex);
          gl.uniform1i(this.uSm["uStrokeMask"], 0);
          gl.uniform2f(this.uSm["uMaskOffset"], strokeMargin, strokeMargin);
          gl.uniform2f(this.uSm["uMaskSize"], mask.w, mask.h);
          gl.uniform4f(this.uSm["uHighlightColor"], el.highlight.color[0], el.highlight.color[1], el.highlight.color[2], 1);
          gl.uniform1f(this.uSm["uHighlightAngle"], el.useGravityAngle ? this.gravityAngle : el.highlight.angle);
          gl.uniform1f(this.uSm["uHighlightFalloff"], el.highlight.falloff);
          gl.uniform1f(this.uSm["uHighlightAlpha"], finalAlpha);
          gl.uniform1f(this.uSm["uHighlightMode"], el.highlight.mode);
          gl.uniform2f(this.uSm["uOriginalSize"], origSizeX, origSizeY);
          gl.uniform1f(this.uSm["uOriginalCornerRadius"], origRadius);
          gl.uniform2f(this.uSm["uLayerScale"], layerScaleX, layerScaleY);
          gl.uniform1f(this.uSm["uElementRotation"], state.elementRotation);
          gl.drawArrays(gl.TRIANGLES, 0, 6);
          gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
        }
      }
    }
  };

  // renderer/index.ts
  var LiquidGlassRenderer = class {
    constructor(canvas) {
      __publicField(this, "gl");
      __publicField(this, "elementProgram");
      __publicField(this, "shadowProgram");
      __publicField(this, "wallpaperProgram");
      __publicField(this, "foregroundProgram");
      __publicField(this, "highlightProgram");
      __publicField(this, "tintProgram");
      __publicField(this, "rimHighlightProgram");
      /** Pass 1: stroke mask (clip + hard stroke, no blur/intensity). */
      __publicField(this, "highlightStrokeProgram");
      /** Pass 3: composite (blurred mask * intensity * color). */
      __publicField(this, "highlightCompositeProgram");
      /** Stroke mask composite (Canvas2D stroke mask × intensity × color). */
      __publicField(this, "strokeMaskCompositeProgram");
      /** Inner shadow mask composite (Canvas2D blurred ring mask × color × alpha). */
      __publicField(this, "innerShadowMaskCompositeProgram");
      __publicField(this, "plainRectProgram");
      __publicField(this, "progressiveBlurProgram");
      __publicField(this, "copyProgram");
      __publicField(this, "solidFillProgram");
      __publicField(this, "colorControlsProgram");
      __publicField(this, "sceneTintProgram");
      __publicField(this, "quadBuffer");
      __publicField(this, "wallpaperTexture", null);
      __publicField(this, "wallpaperReady", false);
      __publicField(this, "wallpaperSize", [1, 1]);
      __publicField(this, "canvas");
      __publicField(this, "dpr", 0);
      // 0 = not yet set; gooseResize() sets default cap on first call
      __publicField(this, "buttonConfigs", []);
      __publicField(this, "buttonStates", /* @__PURE__ */ new Map());
      /** Toggle group state — keyed by groupId. Faithful port of DampedDragAnimation.kt. */
      __publicField(this, "toggleStates", /* @__PURE__ */ new Map());
      __publicField(this, "scrollY", 0);
      __publicField(this, "scrollVelocity", 0);
      __publicField(this, "contentHeight", 0);
      __publicField(this, "cssWidth", 0);
      __publicField(this, "cssHeight", 0);
      __publicField(this, "wheelTarget", null);
      __publicField(this, "backgroundColor", null);
      /** PERFORMANCE: Dirty flag — set by any state change that requires a redraw.
       *  gooseRender() checks this and early-exits if false, avoiding redundant
       *  full-scene re-gooseRender when requestAnimationFrame fires but nothing changed. */
      __publicField(this, "needsRedraw", true);
      // --- Scene FBO ping-pong infrastructure ---
      // See gooseRender() for the full ping-pong pipeline description.
      __publicField(this, "fboA", null);
      __publicField(this, "fboATex", null);
      __publicField(this, "fboB", null);
      __publicField(this, "fboBTex", null);
      __publicField(this, "fboW", 0);
      __publicField(this, "fboH", 0);
      // --- tabsBackdrop FBO (indicator's hidden tinted layer) ---
      // Faithful to LiquidBottomTabs.kt: the indicator's backdrop is
      //   rememberCombinedBackdrop(backdrop, tabsBackdrop)
      // where tabsBackdrop is a HIDDEN Row (alpha=0) that captures the container
      // glass + tab content with ColorFilter.tint(accentColor). We gooseRender the
      // current scene (container+tabs already drawn) into this FBO, apply a blue
      // tint pass, then the indicator shader samples it as the second backdrop
      // layer (composited over wallpaper).
      __publicField(this, "tabsBackdropFbo", null);
      __publicField(this, "tabsBackdropTex", null);
      __publicField(this, "tabsBackdropDirty", true);
      // --- Separable 2-pass blur infrastructure (Glass Playground only) ---
      // gpElementFbo: element pass renders here (refraction on CLEAR backdrop,
      // uBlurRadius=0) for useSeparableBlur elements. Transparent background;
      // the element shader's discard leaves only the glass shape's refracted content.
      // blurFboA/blurFboB: ping-pong for the 2-pass Gaussian (H then V).
      // The blurred result is alpha-composited back into the scene (otherFbo).
      __publicField(this, "gpElementFbo", null);
      __publicField(this, "gpElementTex", null);
      __publicField(this, "blurFboA", null);
      __publicField(this, "blurFboATex", null);
      __publicField(this, "blurFboB", null);
      __publicField(this, "blurFboBTex", null);
      // --- Highlight mask FBO (3-pass faithful highlight) ---
      // Pass 1: HIGHLIGHT_STROKE_FRAGMENT_SHADER renders the clipped stroke alpha
      //   mask here (transparent surround, alpha=1 in the stroke band).
      // Pass 2: gooseBlur(highlightMaskTex, sigma) → blurFboB (2-pass Gaussian,
      //   faithful to Skia BlurMaskFilter NORMAL).
      // Pass 3: HIGHLIGHT_COMPOSITE_FRAGMENT_SHADER samples blurFboB, multiplies
      //   by intensity+color, blends into the scene FBO.
      __publicField(this, "highlightMaskFbo", null);
      __publicField(this, "highlightMaskTex", null);
      // --- Dialog backdrop FBO ---
      // Holds wallpaper+scrim+colorControls as one opaque layer for the dialog
      // card's 2-pass blur path. Rendered by gooseRenderDlg; the dialog card
      // (backdropFbo=true + useSeparableBlur) samples this via 2-pass blur.
      __publicField(this, "dialogBackdropFbo", null);
      __publicField(this, "dialogBackdropTex", null);
      /** Cache key for dialogBackdropFbo (scrim+cc params) — skip re-gooseRender if unchanged. */
      __publicField(this, "dialogBackdropKey", null);
      /** Blur shader variants keyed by 1D tap count (H + V programs each). */
      __publicField(this, "blurPrograms", /* @__PURE__ */ new Map());
      /** Highlight blur programs — separate from blurPrograms because these blur
       *  ALPHA (mask), use Android BlurMaskFilter sigma semantics (uRadius=sigma),
       *  and support sub-pixel sigma (no 0.5 early-return). */
      __publicField(this, "highlightBlurPrograms", /* @__PURE__ */ new Map());
      /** Gravity angle for glass highlight direction, in RADIANS. Updated live via
       *  gooseGravAngle (no catalog rebuild). Default 45° = 0.785 rad.
       *  Elements with useGravityAngle=true read this at gooseRender time. */
      __publicField(this, "gravityAngle", 45 * Math.PI / 180);
      /** Max 1D taps per blur pass (1..33). Lower = faster, Higher = better quality.
       *  Set from GooseState.blurTapCap. Default 17. */
      __publicField(this, "blurTapCap", 17);
      /** Blur downsample factor (1=full-res, 2=half-res, 4=quarter). Higher = much
       *  faster but slightly lower quality. Set from GooseState.blurDownsample. */
      __publicField(this, "blurDownsample", 1);
      /** Corner style: 0 = circular, 1 = continuous (squircle). Set from
       *  GooseState.capsuleShape. Default 1 (Continuous, matching original). */
      __publicField(this, "cornerStyle", 1);
      // SDF texture (clock_sdf) for LockScreen glass
      __publicField(this, "sdfTexture", null);
      __publicField(this, "sdfTextureReady", false);
      __publicField(this, "sdfTextureSize", [1, 1]);
      // Continuous-curvature mask texture pool: each unique (w,h,radius,dpr) gets
      // its own texture. The currently-bound one is in continuousSdfTexture.
      __publicField(this, "continuousSdfPool", /* @__PURE__ */ new Map());
      __publicField(this, "continuousSdfTexture", null);
      __publicField(this, "continuousSdfTexSize", [256, 256]);
      __publicField(this, "continuousSdfKey", null);
      // Offscreen 2D canvas for the foreground (label + chevron). Reused
      // across buttons — we re-rasterize + re-upload per button per frame.
      __publicField(this, "fgCanvas");
      __publicField(this, "fgCtx");
      __publicField(this, "fgTextures", /* @__PURE__ */ new Map());
      __publicField(this, "fgDirtyIds", /* @__PURE__ */ new Set());
      /** Canvas2D stroke-mask cache for rim highlight. Keyed by exact geometry
       *  (element size + corner radius + stroke width + path style at current dpr).
       *  The mask is independent of highlight angle/alpha/press progress, so it can
       *  be reused across frames without a resolution ceiling or UV mismatch. */
      __publicField(this, "strokeMaskCache", /* @__PURE__ */ new Map());
      /** Canvas2D inner-shadow-mask cache. Keyed by exact geometry
       *  (size + radius + offset + blurSigma + margin + supersample at current dpr),
       *  bounded by MAX_CACHE_SIZE (32 entries). Masks are stable across frames
       *  unless the geometry or dpr changes. */
      __publicField(this, "innerShadowMaskCache", /* @__PURE__ */ new Map());
      __publicField(this, "rafId", null);
      __publicField(this, "animRafId", null);
      __publicField(this, "aPosLocEl");
      __publicField(this, "aPosLocSh");
      __publicField(this, "aPosLocWp");
      __publicField(this, "aPosLocFg");
      __publicField(this, "aPosLocHl");
      __publicField(this, "aPosLocTn");
      __publicField(this, "aPosLocRm");
      __publicField(this, "aPosLocHs");
      // highlight stroke
      __publicField(this, "aPosLocHc");
      // highlight composite
      __publicField(this, "aPosLocSm");
      // stroke mask composite
      __publicField(this, "aPosLocIs");
      // inner shadow mask composite
      __publicField(this, "aPosLocPr");
      __publicField(this, "aPosLocPb");
      __publicField(this, "aPosLocCp");
      __publicField(this, "aPosLocSf");
      __publicField(this, "aPosLocCc");
      __publicField(this, "aPosLocSt");
      // Program uniform locations (cached)
      __publicField(this, "uEl", {});
      __publicField(this, "uSh", {});
      __publicField(this, "uWp", {});
      __publicField(this, "uFg", {});
      __publicField(this, "uHl", {});
      __publicField(this, "uTn", {});
      __publicField(this, "uRm", {});
      __publicField(this, "uHs", {});
      __publicField(this, "uHc", {});
      __publicField(this, "uSm", {});
      __publicField(this, "uIs", {});
      __publicField(this, "uPr", {});
      __publicField(this, "uPb", {});
      __publicField(this, "uCp", {});
      __publicField(this, "uSf", {});
      __publicField(this, "uCc", {});
      __publicField(this, "uSt", {});
      var _a;
      this.canvas = canvas;
      const gl = canvas.getContext("webgl", {
        premultipliedAlpha: false,
        alpha: false,
        antialias: true,
        preserveDrawingBuffer: false
      });
      if (!gl) throw new Error("WebGL not supported");
      this.gl = gl;
      gl.getExtension("OES_standard_derivatives");
      this.elementProgram = createProgram(gl, VERTEX_SHADER, ELEMENT_FRAGMENT_SHADER);
      this.shadowProgram = createProgram(gl, VERTEX_SHADER, SHADOW_FRAGMENT_SHADER);
      this.wallpaperProgram = createProgram(gl, VERTEX_SHADER, WALLPAPER_FRAGMENT_SHADER);
      this.foregroundProgram = createProgram(gl, VERTEX_SHADER, FOREGROUND_FRAGMENT_SHADER);
      this.highlightProgram = createProgram(gl, VERTEX_SHADER, HIGHLIGHT_FRAGMENT_SHADER);
      this.tintProgram = createProgram(gl, VERTEX_SHADER, TINT_FRAGMENT_SHADER);
      this.rimHighlightProgram = createProgram(gl, VERTEX_SHADER, RIM_HIGHLIGHT_FRAGMENT_SHADER);
      this.highlightStrokeProgram = createProgram(gl, VERTEX_SHADER, HIGHLIGHT_STROKE_FRAGMENT_SHADER);
      this.highlightCompositeProgram = createProgram(gl, VERTEX_SHADER, HIGHLIGHT_COMPOSITE_FRAGMENT_SHADER);
      this.strokeMaskCompositeProgram = createProgram(gl, VERTEX_SHADER, STROKE_MASK_COMPOSITE_FRAGMENT_SHADER);
      this.innerShadowMaskCompositeProgram = createProgram(gl, VERTEX_SHADER, INNER_SHADOW_MASK_COMPOSITE_FRAGMENT_SHADER);
      this.plainRectProgram = createProgram(gl, VERTEX_SHADER, PLAIN_RECT_FRAGMENT_SHADER);
      this.progressiveBlurProgram = createProgram(gl, VERTEX_SHADER, PROGRESSIVE_BLUR_FRAGMENT_SHADER);
      this.copyProgram = createProgram(gl, VERTEX_SHADER, COPY_FRAGMENT_SHADER);
      this.solidFillProgram = createProgram(gl, VERTEX_SHADER, SOLID_FILL_FRAGMENT_SHADER);
      this.colorControlsProgram = createProgram(gl, VERTEX_SHADER, COLOR_CONTROLS_FRAGMENT_SHADER);
      this.sceneTintProgram = createProgram(gl, VERTEX_SHADER, SCENE_TINT_FRAGMENT_SHADER);
      this.quadBuffer = gl.createBuffer();
      gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
      gl.bufferData(
        gl.ARRAY_BUFFER,
        new Float32Array([-1, -1, 1, -1, -1, 1, -1, 1, 1, -1, 1, 1]),
        gl.STATIC_DRAW
      );
      this.aPosLocEl = gl.getAttribLocation(this.elementProgram, "aPos");
      this.aPosLocSh = gl.getAttribLocation(this.shadowProgram, "aPos");
      this.aPosLocWp = gl.getAttribLocation(this.wallpaperProgram, "aPos");
      this.aPosLocFg = gl.getAttribLocation(this.foregroundProgram, "aPos");
      this.aPosLocHl = gl.getAttribLocation(this.highlightProgram, "aPos");
      this.aPosLocTn = gl.getAttribLocation(this.tintProgram, "aPos");
      this.aPosLocRm = gl.getAttribLocation(this.rimHighlightProgram, "aPos");
      this.aPosLocHs = gl.getAttribLocation(this.highlightStrokeProgram, "aPos");
      this.aPosLocHc = gl.getAttribLocation(this.highlightCompositeProgram, "aPos");
      this.aPosLocSm = gl.getAttribLocation(this.strokeMaskCompositeProgram, "aPos");
      this.aPosLocIs = gl.getAttribLocation(this.innerShadowMaskCompositeProgram, "aPos");
      this.aPosLocPr = gl.getAttribLocation(this.plainRectProgram, "aPos");
      this.aPosLocPb = gl.getAttribLocation(this.progressiveBlurProgram, "aPos");
      this.aPosLocCp = gl.getAttribLocation(this.copyProgram, "aPos");
      this.aPosLocSf = gl.getAttribLocation(this.solidFillProgram, "aPos");
      this.aPosLocCc = gl.getAttribLocation(this.colorControlsProgram, "aPos");
      this.aPosLocSt = gl.getAttribLocation(this.sceneTintProgram, "aPos");
      this.fgCanvas = typeof document !== "undefined" ? document.createElement("canvas") : null;
      const fgCtx = (_a = this.fgCanvas) == null ? void 0 : _a.getContext("2d", { alpha: true, willReadFrequently: true });
      if (!fgCtx) throw new Error("2D canvas not supported");
      this.fgCtx = fgCtx;
      this.gooseUniforms();
    }
    gooseUniforms() {
      const gl = this.gl;
      const elNames = [
        "uBackdrop",
        "uWallpaperSampler",
        "uTabsBackdropSampler",
        "uCanvasSize",
        "uWallpaperSize",
        "uElementOffset",
        "uElementSize",
        "uCornerRadii",
        "uRefractionHeight",
        "uRefractionAmount",
        "uDepthEffect",
        "uChromaticAberration",
        "uBlurRadius",
        "uSaturation",
        "uBrightness",
        "uContrast",
        "uTintColor",
        "uSurfaceColor",
        "uHighlightColor",
        "uHighlightAngle",
        "uHighlightFalloff",
        "uHighlightAlpha",
        "uHighlightMode",
        "uHighlightStrokeWidth",
        "uHighlightBlur",
        "uInnerShadowRadius",
        "uInnerShadowAlpha",
        "uInnerShadowOffset",
        "uContentScaleX",
        "uContentScaleY",
        "uUseToggleBackdrop",
        "uUseSolidBackdrop",
        "uSolidBackdropColor",
        "uTrackColor",
        "uTrackRect",
        "uTrackCornerRadius",
        "uOriginalSize",
        "uOriginalCornerRadius",
        "uLayerScale",
        "uIndicatorBackdrop",
        "uContainerRect",
        "uContainerCornerRadius",
        "uIndicatorAccent",
        "uInsetPx",
        "uIndicatorPressProgress",
        "uIndicatorPanelOffset",
        "uDpr",
        "uContainerCenter",
        "uContainerScale",
        "uTabContentTex0",
        "uTabContentTex1",
        "uTabContentTex2",
        "uTabContentTex3",
        "uTabContentTex4",
        "uTabContentTex5",
        "uTabContentTex6",
        "uTabContentTex7",
        "uTabContentRects[0]",
        "uTabContentRects[1]",
        "uTabContentRects[2]",
        "uTabContentRects[3]",
        "uTabContentRects[4]",
        "uTabContentRects[5]",
        "uTabContentRects[6]",
        "uTabContentRects[7]",
        "uTabContentCount",
        "uTabsGlassLayer",
        "uSdfTexSampler",
        "uUseSdfTexture",
        "uSdfTexSize",
        "uSdfLightAngle",
        "uEnterAlpha",
        "uCornerStyle",
        "uSkipColorControls",
        "uUseMagnifier",
        "uMagnifierZoom",
        "uMagnifierOffsetY",
        "uElementRotation",
        "uContinuousSdf",
        "uUseContinuousSdf",
        "uContinuousSdfTexSize",
        "uContinuousSdfElementSize"
      ];
      for (const n of elNames) this.uEl[n] = gl.getUniformLocation(this.elementProgram, n);
      const shNames = [
        "uCanvasSize",
        "uElementOffset",
        "uElementSize",
        "uCornerRadii",
        "uShadowRadius",
        "uShadowOffset",
        "uShadowColor",
        "uOriginalSize",
        "uOriginalCornerRadius",
        "uLayerScale",
        "uElementRotation",
        "uCornerStyle"
      ];
      for (const n of shNames) this.uSh[n] = gl.getUniformLocation(this.shadowProgram, n);
      const wpNames = ["uBackdrop", "uCanvasSize", "uWallpaperSize"];
      for (const n of wpNames) this.uWp[n] = gl.getUniformLocation(this.wallpaperProgram, n);
      const fgNames = [
        "uTexture",
        "uCanvasSize",
        "uOffset",
        "uSize",
        "uCornerRadii",
        "uAlpha",
        "uOriginalSize",
        "uOriginalCornerRadius",
        "uLayerScale",
        "uCornerStyle",
        "uUseContinuousSdf",
        "uContinuousSdf",
        "uContinuousSdfTexSize",
        "uContinuousSdfElementSize"
      ];
      for (const n of fgNames) this.uFg[n] = gl.getUniformLocation(this.foregroundProgram, n);
      const hlNames = [
        "uCanvasSize",
        "uOffset",
        "uSize",
        "uCornerRadii",
        "uColor",
        "uRadius",
        "uPosition",
        "uOriginalSize",
        "uOriginalCornerRadius",
        "uLayerScale",
        "uElementRotation",
        "uCornerStyle"
      ];
      for (const n of hlNames) this.uHl[n] = gl.getUniformLocation(this.highlightProgram, n);
      const tnNames = [
        "uCanvasSize",
        "uOffset",
        "uSize",
        "uCornerRadii",
        "uColor",
        "uOriginalSize",
        "uOriginalCornerRadius",
        "uLayerScale",
        "uElementRotation",
        "uCornerStyle"
      ];
      for (const n of tnNames) this.uTn[n] = gl.getUniformLocation(this.tintProgram, n);
      const rmNames = [
        "uCanvasSize",
        "uOffset",
        "uSize",
        "uCornerRadii",
        "uHighlightColor",
        "uHighlightAngle",
        "uHighlightFalloff",
        "uHighlightAlpha",
        "uHighlightMode",
        "uHighlightStrokeWidth",
        "uHighlightBlur",
        "uOriginalSize",
        "uOriginalCornerRadius",
        "uLayerScale",
        "uElementRotation",
        "uCornerStyle",
        "uUseContinuousSdf",
        "uContinuousSdf",
        "uContinuousSdfTexSize",
        "uContinuousSdfElementSize"
      ];
      for (const n of rmNames) this.uRm[n] = gl.getUniformLocation(this.rimHighlightProgram, n);
      const hsNames = [
        "uCanvasSize",
        "uOffset",
        "uSize",
        "uCornerRadii",
        "uHighlightStrokeWidth",
        "uOriginalSize",
        "uOriginalCornerRadius",
        "uLayerScale",
        "uElementRotation",
        "uCornerStyle",
        "uUseContinuousSdf",
        "uContinuousSdf",
        "uContinuousSdfTexSize",
        "uContinuousSdfElementSize"
      ];
      for (const n of hsNames) this.uHs[n] = gl.getUniformLocation(this.highlightStrokeProgram, n);
      const hcNames = [
        "uCanvasSize",
        "uOffset",
        "uSize",
        "uCornerRadii",
        "uBlurredMask",
        "uMaskTexSize",
        "uHighlightColor",
        "uHighlightAngle",
        "uHighlightFalloff",
        "uHighlightAlpha",
        "uHighlightMode",
        "uOriginalSize",
        "uOriginalCornerRadius",
        "uLayerScale",
        "uElementRotation",
        "uCornerStyle",
        "uUseContinuousSdf",
        "uContinuousSdf",
        "uContinuousSdfTexSize",
        "uContinuousSdfElementSize"
      ];
      for (const n of hcNames) this.uHc[n] = gl.getUniformLocation(this.highlightCompositeProgram, n);
      const smNames = [
        "uCanvasSize",
        "uOffset",
        "uSize",
        "uCornerRadii",
        "uStrokeMask",
        "uMaskOffset",
        "uMaskSize",
        "uHighlightColor",
        "uHighlightAngle",
        "uHighlightFalloff",
        "uHighlightAlpha",
        "uHighlightMode",
        "uOriginalSize",
        "uOriginalCornerRadius",
        "uLayerScale",
        "uElementRotation"
      ];
      for (const n of smNames) this.uSm[n] = gl.getUniformLocation(this.strokeMaskCompositeProgram, n);
      const isNames = [
        "uCanvasSize",
        "uOffset",
        "uSize",
        "uCornerRadii",
        "uInnerShadowMask",
        "uMaskOffset",
        "uMaskSize",
        "uInnerShadowColor",
        "uInnerShadowAlpha",
        "uOriginalSize",
        "uOriginalCornerRadius",
        "uLayerScale",
        "uElementRotation"
      ];
      for (const n of isNames) this.uIs[n] = gl.getUniformLocation(this.innerShadowMaskCompositeProgram, n);
      const prNames = [
        "uCanvasSize",
        "uOffset",
        "uSize",
        "uCornerRadii",
        "uColor",
        "uCornerStyle",
        "uUseContinuousSdf",
        "uContinuousSdf",
        "uContinuousSdfTexSize",
        "uContinuousSdfElementSize"
      ];
      for (const n of prNames) this.uPr[n] = gl.getUniformLocation(this.plainRectProgram, n);
      const pbNames = [
        "uBackdrop",
        "uCanvasSize",
        "uWallpaperSize",
        "uOffset",
        "uSize",
        "uBlurRadius",
        "uTintColor",
        "uTintIntensity"
      ];
      for (const n of pbNames) this.uPb[n] = gl.getUniformLocation(this.progressiveBlurProgram, n);
      const cpNames = ["uTexture", "uCanvasSize"];
      for (const n of cpNames) this.uCp[n] = gl.getUniformLocation(this.copyProgram, n);
      const sfNames = ["uColor"];
      for (const n of sfNames) this.uSf[n] = gl.getUniformLocation(this.solidFillProgram, n);
      const ccNames = ["uTexture", "uTexSize", "uBrightness", "uContrast", "uSaturation"];
      for (const n of ccNames) this.uCc[n] = gl.getUniformLocation(this.colorControlsProgram, n);
      const stNames = ["uTexture", "uCanvasSize", "uTintColor"];
      for (const n of stNames) this.uSt[n] = gl.getUniformLocation(this.sceneTintProgram, n);
    }
    /** Lazy-compile horizontal + vertical blur programs for a 1D tap count. */
    gooseBlurProg(tapCount) {
      if (this.blurPrograms.has(tapCount)) return;
      const gl = this.gl;
      const hFs = compileShader(gl, gl.FRAGMENT_SHADER, generateSeparableBlurShader(tapCount, "horizontal"));
      const vFs = compileShader(gl, gl.FRAGMENT_SHADER, generateSeparableBlurShader(tapCount, "vertical"));
      const mk = (fs) => {
        const vs = compileShader(gl, gl.VERTEX_SHADER, VERTEX_SHADER);
        const p = gl.createProgram();
        gl.attachShader(p, vs);
        gl.attachShader(p, fs);
        gl.bindAttribLocation(p, 0, "aPos");
        gl.linkProgram(p);
        gl.deleteShader(vs);
        gl.deleteShader(fs);
        if (!gl.getProgramParameter(p, gl.LINK_STATUS)) {
          const log = gl.getProgramInfoLog(p);
          gl.deleteProgram(p);
          throw new Error("Blur program link error (taps=" + tapCount + "): " + log);
        }
        return p;
      };
      const hProg = mk(hFs);
      const vProg = mk(vFs);
      const uH = {
        uTexture: gl.getUniformLocation(hProg, "uTexture"),
        uTexSize: gl.getUniformLocation(hProg, "uTexSize"),
        uRadius: gl.getUniformLocation(hProg, "uRadius")
      };
      const uV = {
        uTexture: gl.getUniformLocation(vProg, "uTexture"),
        uTexSize: gl.getUniformLocation(vProg, "uTexSize"),
        uRadius: gl.getUniformLocation(vProg, "uRadius")
      };
      this.blurPrograms.set(tapCount, { hProg, vProg, uH, uV, aPosH: 0, aPosV: 0 });
    }
    /** 2-pass blur a source texture by `radius` px. Reads srcTex, writes the
     *  blurred result into blurFboB, returns blurFboBTex.
     *  Saves/restores the currently-bound framebuffer.
     *  Uses this.blurTapCap to cap 1D tap count (performance knob).
     *  (blurDownsample is reserved for future use — currently always full-res.) */
    gooseBlur(srcTex, radius) {
      const gl = this.gl;
      const w = this.fboW;
      const h = this.fboH;
      let taps = computeBlur1DTapCount(radius);
      taps = Math.min(taps, Math.max(1, this.blurTapCap | 0));
      this.gooseBlurProg(taps);
      const entry = this.blurPrograms.get(taps);
      const savedFb = gl.getParameter(gl.FRAMEBUFFER_BINDING);
      gl.disable(gl.BLEND);
      gl.bindFramebuffer(gl.FRAMEBUFFER, this.blurFboA);
      gl.viewport(0, 0, w, h);
      gl.useProgram(entry.hProg);
      gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
      gl.enableVertexAttribArray(entry.aPosH);
      gl.vertexAttribPointer(entry.aPosH, 2, gl.FLOAT, false, 0, 0);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, srcTex);
      gl.uniform1i(entry.uH["uTexture"], 0);
      gl.uniform2f(entry.uH["uTexSize"], w, h);
      gl.uniform1f(entry.uH["uRadius"], radius);
      gl.drawArrays(gl.TRIANGLES, 0, 6);
      gl.bindFramebuffer(gl.FRAMEBUFFER, this.blurFboB);
      gl.viewport(0, 0, w, h);
      gl.useProgram(entry.vProg);
      gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
      gl.enableVertexAttribArray(entry.aPosV);
      gl.vertexAttribPointer(entry.aPosV, 2, gl.FLOAT, false, 0, 0);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, this.blurFboATex);
      gl.uniform1i(entry.uV["uTexture"], 0);
      gl.uniform2f(entry.uV["uTexSize"], w, h);
      gl.uniform1f(entry.uV["uRadius"], radius);
      gl.drawArrays(gl.TRIANGLES, 0, 6);
      gl.bindFramebuffer(gl.FRAMEBUFFER, savedFb);
      gl.viewport(0, 0, w, h);
      return this.blurFboBTex;
    }
    /** Lazy-compile highlight blur programs (alpha-blurring, sigma semantics).
     *  Separate from gooseBlurProg because the shader is different
     *  (blurs alpha, no early-return, integer-σ-spaced taps). */
    gooseHLBlurProg(tapCount) {
      if (this.highlightBlurPrograms.has(tapCount)) return;
      const gl = this.gl;
      const hFs = compileShader(gl, gl.FRAGMENT_SHADER, generateHighlightBlurShader(tapCount, "horizontal"));
      const vFs = compileShader(gl, gl.FRAGMENT_SHADER, generateHighlightBlurShader(tapCount, "vertical"));
      const mk = (fs) => {
        const vs = compileShader(gl, gl.VERTEX_SHADER, VERTEX_SHADER);
        const p = gl.createProgram();
        gl.attachShader(p, vs);
        gl.attachShader(p, fs);
        gl.bindAttribLocation(p, 0, "aPos");
        gl.linkProgram(p);
        gl.deleteShader(vs);
        gl.deleteShader(fs);
        if (!gl.getProgramParameter(p, gl.LINK_STATUS)) {
          const log = gl.getProgramInfoLog(p);
          gl.deleteProgram(p);
          throw new Error("Highlight blur program link error (taps=" + tapCount + "): " + log);
        }
        return p;
      };
      const hProg = mk(hFs);
      const vProg = mk(vFs);
      const uH = {
        uTexture: gl.getUniformLocation(hProg, "uTexture"),
        uTexSize: gl.getUniformLocation(hProg, "uTexSize"),
        uRadius: gl.getUniformLocation(hProg, "uRadius")
      };
      const uV = {
        uTexture: gl.getUniformLocation(vProg, "uTexture"),
        uTexSize: gl.getUniformLocation(vProg, "uTexSize"),
        uRadius: gl.getUniformLocation(vProg, "uRadius")
      };
      this.highlightBlurPrograms.set(tapCount, { hProg, vProg, uH, uV, aPosH: 0, aPosV: 0 });
    }
    /** 2-pass Gaussian blur on a highlight stroke MASK (alpha only).
     *  Faithful to Android BlurMaskFilter(NORMAL, sigma):
     *    - sigma = blurRadiusPx (the Android radius param IS sigma)
     *    - convolves the mask's ALPHA with a Gaussian kernel
     *    - sub-pixel sigma (0.25px) still blurs (no 0.5 early-return)
     *  Reads srcTex (alpha mask), writes blurFboB, returns blurFboBTex.
     *  Saves/restores the currently-bound framebuffer. */
    gooseHLBlur(srcTex, sigmaPx) {
      const gl = this.gl;
      const w = this.fboW;
      const h = this.fboH;
      let taps = computeHighlightBlurTapCount(sigmaPx);
      taps = Math.min(taps, Math.max(3, this.blurTapCap | 0));
      this.gooseHLBlurProg(taps);
      const entry = this.highlightBlurPrograms.get(taps);
      const savedFb = gl.getParameter(gl.FRAMEBUFFER_BINDING);
      gl.disable(gl.BLEND);
      gl.bindFramebuffer(gl.FRAMEBUFFER, this.blurFboA);
      gl.viewport(0, 0, w, h);
      gl.useProgram(entry.hProg);
      gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
      gl.enableVertexAttribArray(entry.aPosH);
      gl.vertexAttribPointer(entry.aPosH, 2, gl.FLOAT, false, 0, 0);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, srcTex);
      gl.uniform1i(entry.uH["uTexture"], 0);
      gl.uniform2f(entry.uH["uTexSize"], w, h);
      gl.uniform1f(entry.uH["uRadius"], sigmaPx);
      gl.drawArrays(gl.TRIANGLES, 0, 6);
      gl.bindFramebuffer(gl.FRAMEBUFFER, this.blurFboB);
      gl.viewport(0, 0, w, h);
      gl.useProgram(entry.vProg);
      gl.bindBuffer(gl.ARRAY_BUFFER, this.quadBuffer);
      gl.enableVertexAttribArray(entry.aPosV);
      gl.vertexAttribPointer(entry.aPosV, 2, gl.FLOAT, false, 0, 0);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, this.blurFboATex);
      gl.uniform1i(entry.uV["uTexture"], 0);
      gl.uniform2f(entry.uV["uTexSize"], w, h);
      gl.uniform1f(entry.uV["uRadius"], sigmaPx);
      gl.drawArrays(gl.TRIANGLES, 0, 6);
      gl.bindFramebuffer(gl.FRAMEBUFFER, savedFb);
      gl.viewport(0, 0, w, h);
      return this.blurFboBTex;
    }
    gooseKill() {
      if (this.rafId !== null) cancelAnimationFrame(this.rafId);
      this.rafId = null;
      if (this.animRafId !== null) cancelAnimationFrame(this.animRafId);
      this.animRafId = null;
      const gl = this.gl;
      if (this.wallpaperTexture) gl.deleteTexture(this.wallpaperTexture);
      for (const tex of this.fgTextures.values()) gl.deleteTexture(tex);
      this.fgTextures.clear();
      for (const entry of this.strokeMaskCache.values()) gl.deleteTexture(entry.tex);
      this.strokeMaskCache.clear();
      gooseDestroyInnerShadowCache(gl, this.innerShadowMaskCache);
      if (this.fboA) gl.deleteFramebuffer(this.fboA);
      if (this.fboATex) gl.deleteTexture(this.fboATex);
      if (this.fboB) gl.deleteFramebuffer(this.fboB);
      if (this.fboBTex) gl.deleteTexture(this.fboBTex);
      this.fboA = this.fboB = null;
      this.fboATex = this.fboBTex = null;
      if (this.tabsBackdropFbo) gl.deleteFramebuffer(this.tabsBackdropFbo);
      if (this.tabsBackdropTex) gl.deleteTexture(this.tabsBackdropTex);
      this.tabsBackdropFbo = null;
      this.tabsBackdropTex = null;
      if (this.gpElementFbo) gl.deleteFramebuffer(this.gpElementFbo);
      if (this.gpElementTex) gl.deleteTexture(this.gpElementTex);
      if (this.blurFboA) gl.deleteFramebuffer(this.blurFboA);
      if (this.blurFboATex) gl.deleteTexture(this.blurFboATex);
      if (this.blurFboB) gl.deleteFramebuffer(this.blurFboB);
      if (this.blurFboBTex) gl.deleteTexture(this.blurFboBTex);
      this.gpElementFbo = this.blurFboA = this.blurFboB = null;
      this.gpElementTex = this.blurFboATex = this.blurFboBTex = null;
      if (this.highlightMaskFbo) gl.deleteFramebuffer(this.highlightMaskFbo);
      if (this.highlightMaskTex) gl.deleteTexture(this.highlightMaskTex);
      this.highlightMaskFbo = null;
      this.highlightMaskTex = null;
      if (this.dialogBackdropFbo) gl.deleteFramebuffer(this.dialogBackdropFbo);
      if (this.dialogBackdropTex) gl.deleteTexture(this.dialogBackdropTex);
      this.dialogBackdropFbo = null;
      this.dialogBackdropTex = null;
      this.dialogBackdropKey = null;
      for (const { hProg, vProg } of this.blurPrograms.values()) {
        gl.deleteProgram(hProg);
        gl.deleteProgram(vProg);
      }
      this.blurPrograms.clear();
      for (const { hProg, vProg } of this.highlightBlurPrograms.values()) {
        gl.deleteProgram(hProg);
        gl.deleteProgram(vProg);
      }
      this.highlightBlurPrograms.clear();
      if (this.sdfTexture) gl.deleteTexture(this.sdfTexture);
      this.sdfTexture = null;
      for (const { tex } of this.continuousSdfPool.values()) gl.deleteTexture(tex);
      this.continuousSdfPool.clear();
      this.continuousSdfTexture = null;
      this.continuousSdfKey = null;
      gl.deleteProgram(this.elementProgram);
      gl.deleteProgram(this.shadowProgram);
      gl.deleteProgram(this.wallpaperProgram);
      gl.deleteProgram(this.foregroundProgram);
      gl.deleteProgram(this.highlightProgram);
      gl.deleteProgram(this.tintProgram);
      gl.deleteProgram(this.rimHighlightProgram);
      gl.deleteProgram(this.highlightStrokeProgram);
      gl.deleteProgram(this.highlightCompositeProgram);
      gl.deleteProgram(this.strokeMaskCompositeProgram);
      gl.deleteProgram(this.innerShadowMaskCompositeProgram);
      gl.deleteProgram(this.plainRectProgram);
      gl.deleteProgram(this.progressiveBlurProgram);
      gl.deleteProgram(this.copyProgram);
      gl.deleteProgram(this.solidFillProgram);
      gl.deleteProgram(this.colorControlsProgram);
      gl.deleteProgram(this.sceneTintProgram);
      gl.deleteBuffer(this.quadBuffer);
    }
  };
  /** The pressed scale for bottom tabs indicator (78f/56f in Kotlin). */
  __publicField(LiquidGlassRenderer, "gooseTabScale", 78 / 56);
  Object.assign(
    LiquidGlassRenderer.prototype,
    fboMethods,
    wallpaperMethods,
    scrollMethods,
    toggleMethods,
    tabsMethods,
    elementMethods,
    animationMethods,
    rasterMethods,
    renderMethods,
    glassRenderMethods,
    glassElementPassMethods,
    glassPostPassMethods
  );

  // catalog/types.ts
  var gooseDP2 = 1;
  var gooseDragGroups = /* @__PURE__ */ new Set();
  var gooseBtnH = 48 * gooseDP2;
  var gooseBtnPad = 16 * gooseDP2;
  var gooseTextSz = 15 * gooseDP2;
  var gooseFont = '-apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';
  var gooseGlassP = {
    refractionHeight: 12 * gooseDP2,
    refractionAmount: -24 * gooseDP2,
    depthEffect: false,
    chromaticAberration: false,
    blurRadius: 2 * gooseDP2,
    saturation: 1.5,
    brightness: 0,
    contrast: 1
  };
  var gooseHL = {
    mode: 0,
    color: [1, 1, 1],
    angle: 45 * Math.PI / 180,
    falloff: 1,
    alpha: 1,
    widthDp: 0.5
  };
  var gooseShadow = {
    radius: 24 * gooseDP2,
    alpha: 0.1,
    offsetX: 0,
    offsetY: 24 / 6 * gooseDP2,
    color: [0, 0, 0]
  };
  var gooseLight = {
    homeContentColor: [0, 0, 0, 1],
    homeSubtitleColor: [0 / 255, 136 / 255, 255 / 255, 1],
    homeTextHalo: "dark",
    toggleAccent: [52 / 255, 199 / 255, 89 / 255],
    toggleTrackOff: [120 / 255, 120 / 255, 120 / 255, 0.2],
    toggleCardBg: [1, 1, 1, 1],
    sliderAccent: [0 / 255, 136 / 255, 255 / 255],
    sliderTrackOff: [120 / 255, 120 / 255, 120 / 255, 0.2],
    sliderCardBg: [1, 1, 1, 1],
    tabsContentColor: [0, 0, 0, 1],
    tabsAccent: [0 / 255, 136 / 255, 255 / 255],
    tabsContainer: [250 / 255, 250 / 255, 250 / 255, 0.4],
    tabsTextHalo: "dark",
    dialogContentColor: [0, 0, 0, 1],
    dialogAccent: [0 / 255, 136 / 255, 255 / 255, 1],
    dialogContainer: [250 / 255, 250 / 255, 250 / 255, 0.6],
    dialogDim: [41 / 255, 41 / 255, 58 / 255, 0.23],
    dialogBlurRadius: 16 * gooseDP2,
    dialogBrightness: 0.2,
    magnifierContentColor: [0, 0, 0, 1],
    magnifierAccent: [0 / 255, 136 / 255, 255 / 255, 1],
    magnifierCardBg: [1, 1, 1, 0.9],
    controlCenterAccent: [0 / 255, 136 / 255, 255 / 255, 1],
    progressiveContentColor: [0, 0, 0, 1],
    progressiveTint: [1, 1, 1, 1],
    progressiveTextHalo: "dark",
    adaptiveContentColor: [0, 0, 0, 1],
    backIconColor: [0, 0, 0, 1],
    buttonSurface: [1, 1, 1, 0.3]
  };
  var gooseDark = {
    homeContentColor: [1, 1, 1, 1],
    homeSubtitleColor: [0 / 255, 136 / 255, 255 / 255, 1],
    homeTextHalo: "light",
    toggleAccent: [48 / 255, 209 / 255, 88 / 255],
    toggleTrackOff: [120 / 255, 120 / 255, 128 / 255, 0.36],
    toggleCardBg: [18 / 255, 18 / 255, 18 / 255, 1],
    sliderAccent: [0 / 255, 145 / 255, 255 / 255],
    sliderTrackOff: [120 / 255, 120 / 255, 128 / 255, 0.36],
    sliderCardBg: [18 / 255, 18 / 255, 18 / 255, 1],
    tabsContentColor: [1, 1, 1, 1],
    tabsAccent: [0 / 255, 145 / 255, 255 / 255],
    tabsContainer: [18 / 255, 18 / 255, 18 / 255, 0.4],
    tabsTextHalo: "light",
    dialogContentColor: [1, 1, 1, 1],
    dialogAccent: [0 / 255, 145 / 255, 255 / 255, 1],
    dialogContainer: [18 / 255, 18 / 255, 18 / 255, 0.4],
    dialogDim: [18 / 255, 18 / 255, 18 / 255, 0.56],
    dialogBlurRadius: 8 * gooseDP2,
    dialogBrightness: 0,
    magnifierContentColor: [1, 1, 1, 1],
    magnifierAccent: [0 / 255, 145 / 255, 255 / 255, 1],
    magnifierCardBg: [18 / 255, 18 / 255, 18 / 255, 0.9],
    controlCenterAccent: [0 / 255, 145 / 255, 255 / 255, 1],
    progressiveContentColor: [1, 1, 1, 1],
    progressiveTint: [128 / 255, 128 / 255, 128 / 255, 1],
    progressiveTextHalo: "light",
    adaptiveContentColor: [1, 1, 1, 1],
    backIconColor: [1, 1, 1, 1],
    buttonSurface: [18 / 255, 18 / 255, 18 / 255, 0.4]
  };
  function goosePalette(isLightTheme) {
    return isLightTheme ? gooseLight : gooseDark;
  }
  var gooseLorem = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
  var gooseFlight = "M400 552 L147 653 q-24 10 -45.5 -4.5 T80 608 v-22 q0 -12 5.5 -23 t15.5 -18 l299 -209 v-176 q0 -33 23.5 -56.5 T480 80 q33 0 56.5 23.5 T560 160 v176 l299 209 q10 7 15.5 18 t5.5 23 v22 q0 26 -21.5 40.5 T813 653 L560 552 v144 l103 72 q8 6 12.5 14.5 T680 801 v24 q0 20 -16.5 32.5 T627 864 l-147 -44 l-147 44 q-20 6 -36.5 -6.5 T280 825 v-24 q0 -10 4.5 -18.5 T297 768 l103 -72 v-144 Z";
  var gooseDefState = {
    toggleOn: false,
    sliderValue: 50,
    selectedTab: 0,
    selectedTab2: 0,
    magnifierX: 0,
    magnifierY: 0,
    globalSeparableBlur: true,
    capsuleShape: true,
    hideOverlayButtons: false,
    ratingValue: 0,
    ringProgressValue: 50
  };
  var _measureCtx = null;
  function gooseTextW(text, fontPx, weight = 400) {
    if (typeof document !== "undefined") {
      if (!_measureCtx) {
        const c = document.createElement("canvas");
        _measureCtx = c.getContext("2d");
      }
      if (_measureCtx) {
        _measureCtx.font = `${weight} ${fontPx}px ${gooseFont}`;
        return _measureCtx.measureText(text).width;
      }
    }
    return text.length * fontPx * 0.55;
  }
  var gooseTrackH = 6 * gooseDP2;
  var gooseKnobW = 40 * gooseDP2;
  var gooseKnobH = 24 * gooseDP2;
  var gooseHitH = 48 * gooseDP2;

  // catalog/helpers.ts
  var dragStates = /* @__PURE__ */ new Map();
  function gooseDrag(opts) {
    const {
      groupId,
      trackX,
      dragW,
      rendererRef,
      onValueChange,
      onLiveValue,
      getFraction,
      beginDrag,
      drag,
      endDrag,
      setTarget,
      count,
      snap,
      liveUpdate = false,
      onTapJump = true,
      didDragThreshold = 3
    } = opts;
    if (!dragStates.has(groupId)) dragStates.set(groupId, { fraction: 0, x: 0, didDrag: false });
    const ds = dragStates.get(groupId);
    const fracFromPos = (px) => Math.max(0, Math.min(1, (px - trackX) / dragW));
    const applySnap = (f) => snap ? snap(f) : f;
    return {
      onTap: (pos) => {
        if (!onTapJump) return;
        const f = applySnap(fracFromPos(pos.x));
        const r = rendererRef == null ? void 0 : rendererRef.current;
        if (r) setTarget(r, groupId, f, count);
        onValueChange(f);
      },
      onDragStart: (pos) => {
        const r = rendererRef == null ? void 0 : rendererRef.current;
        if (!r) return;
        gooseDragGroups.add(groupId);
        ds.fraction = getFraction(r, groupId);
        ds.x = pos.x;
        ds.didDrag = false;
        beginDrag(r, groupId, ds.fraction, count);
      },
      onDrag: (pos) => {
        const r = rendererRef == null ? void 0 : rendererRef.current;
        if (!r) return;
        if (Math.abs(pos.x - ds.x) > didDragThreshold) ds.didDrag = true;
        drag(r, groupId, ds.fraction, pos.x, ds.x, dragW, count);
        const f = getFraction(r, groupId);
        if (onLiveValue) onLiveValue(f);
        if (liveUpdate) {
          onValueChange(f);
        }
      },
      onDragEnd: () => {
        const r = rendererRef == null ? void 0 : rendererRef.current;
        if (!r) return;
        const rawF = endDrag(r, groupId, count);
        const snappedF = applySnap(rawF);
        if (snap && count == null) setTarget(r, groupId, snappedF, count);
        onValueChange(snappedF);
        gooseDragGroups.delete(groupId);
      }
    };
  }
  var sliderDragBindings = {
    getFraction: (r, id) => r.gooseTglFrac(id),
    beginDrag: (r, id, f) => r.gooseTglDragStart(id, f),
    drag: (r, id, sf, cx, sx, dw) => r.gooseTglDrag(id, sf, cx, sx, dw),
    endDrag: (r, id) => r.gooseSldDragEnd(id),
    setTarget: (r, id, f) => r.gooseTglTarget(id, f)
  };
  var toggleDragBindings = {
    getFraction: (r, id) => r.gooseTglTargetGet(id),
    beginDrag: (r, id, f) => r.gooseTglDragStart(id, f),
    drag: (r, id, sf, cx, sx, dw) => r.gooseTglDrag(id, sf, cx, sx, dw),
    endDrag: (r, id) => r.gooseTglDragEnd(id),
    setTarget: (r, id, f) => r.gooseTglTarget(id, f)
  };
  function gooseBtn(id, rect, spec, scroll = true) {
    return {
      id,
      kind: "button",
      rect,
      ...gooseGlassP,
      cornerRadius: rect.h / 2,
      tintColor: spec.tintColor,
      surfaceColor: spec.surfaceColor,
      highlight: { ...gooseHL },
      outerShadow: { ...gooseShadow },
      label: spec.label,
      labelColor: spec.labelColor,
      labelFontSizePx: spec.labelFontSizePx,
      showChevron: false,
      isInteractive: true,
      scroll
    };
  }
  function gooseText(id, rect, text, opts = {}, scroll = true) {
    var _a, _b, _c, _d, _e, _f, _g;
    return {
      id,
      kind: "text",
      rect,
      cornerRadius: 0,
      refractionHeight: 0,
      refractionAmount: 0,
      depthEffect: false,
      chromaticAberration: false,
      blurRadius: 0,
      saturation: 1,
      brightness: 0,
      contrast: 1,
      tintColor: [0, 0, 0, 0],
      surfaceColor: [0, 0, 0, 0],
      highlight: null,
      outerShadow: null,
      label: "",
      labelColor: [0, 0, 0, 1],
      showChevron: false,
      isInteractive: false,
      pressTintColor: opts.pressTintColor,
      scroll,
      text: {
        content: text,
        color: (_a = opts.color) != null ? _a : [0, 0, 0, 1],
        fontSizePx: (_b = opts.fontSizePx) != null ? _b : gooseTextSz,
        fontWeight: (_c = opts.fontWeight) != null ? _c : 400,
        align: (_d = opts.align) != null ? _d : "left",
        wrap: (_e = opts.wrap) != null ? _e : false,
        paddingPx: (_f = opts.paddingPx) != null ? _f : 16,
        valign: opts.valign,
        maxLines: opts.maxLines,
        halo: (_g = opts.halo) != null ? _g : "auto",
        icon: opts.icon
      }
    };
  }
  function gooseRect(id, rect, color, cornerRadius = 0, scroll = true) {
    return {
      id,
      kind: "plain-rect",
      rect,
      cornerRadius,
      refractionHeight: 0,
      refractionAmount: 0,
      depthEffect: false,
      chromaticAberration: false,
      blurRadius: 0,
      saturation: 1,
      brightness: 0,
      contrast: 1,
      tintColor: [0, 0, 0, 0],
      surfaceColor: [0, 0, 0, 0],
      highlight: null,
      outerShadow: null,
      label: "",
      labelColor: [0, 0, 0, 1],
      showChevron: false,
      isInteractive: false,
      scroll,
      plainRect: { color }
    };
  }
  function gooseTabDrag(groupId, tabWidth, tabsCount, onSelect, rendererRef) {
    if (!dragStates.has(groupId)) dragStates.set(groupId, { fraction: 0, x: 0, didDrag: false });
    const ds = dragStates.get(groupId);
    return {
      onTap: () => {
      },
      onDragStart: (pos) => {
        const r = rendererRef == null ? void 0 : rendererRef.current;
        if (!r) return;
        gooseDragGroups.add(groupId);
        ds.fraction = r.gooseTabTarget(groupId);
        ds.x = pos.x;
        ds.didDrag = false;
        r.gooseTabDragStart(groupId, ds.fraction, tabsCount);
      },
      onDrag: (pos) => {
        const r = rendererRef == null ? void 0 : rendererRef.current;
        if (!r) return;
        if (Math.abs(pos.x - ds.x) > 3) ds.didDrag = true;
        r.gooseTabDrag(groupId, ds.fraction, pos.x, ds.x, tabWidth, tabsCount);
      },
      onDragEnd: () => {
        const r = rendererRef == null ? void 0 : rendererRef.current;
        if (!r) return;
        const finalIndex = r.gooseTabDragEnd(groupId, tabsCount);
        if (ds.didDrag) {
          onSelect(finalIndex);
        }
        gooseDragGroups.delete(groupId);
      }
    };
  }
  function gooseGlass(id, rect, opts = {}, scroll = true) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k;
    return {
      id,
      kind: "glass-shape",
      rect,
      cornerRadius: (_a = opts.cornerRadius) != null ? _a : rect.h / 2,
      refractionHeight: (_b = opts.refractionHeight) != null ? _b : 12 * gooseDP2,
      refractionAmount: (_c = opts.refractionAmount) != null ? _c : -24 * gooseDP2,
      depthEffect: (_d = opts.depthEffect) != null ? _d : false,
      chromaticAberration: (_e = opts.chromaticAberration) != null ? _e : false,
      blurRadius: (_f = opts.blurRadius) != null ? _f : 2 * gooseDP2,
      saturation: (_g = opts.saturation) != null ? _g : 1.5,
      brightness: (_h = opts.brightness) != null ? _h : 0,
      contrast: (_i = opts.contrast) != null ? _i : 1,
      tintColor: [0, 0, 0, 0],
      surfaceColor: (_j = opts.surfaceColor) != null ? _j : [0, 0, 0, 0],
      highlight: opts.highlight !== void 0 ? opts.highlight : { ...gooseHL },
      outerShadow: opts.outerShadow !== void 0 ? opts.outerShadow : null,
      label: "",
      labelColor: [0, 0, 0, 1],
      showChevron: false,
      isInteractive: false,
      scroll,
      innerShadow: (_k = opts.innerShadow) != null ? _k : null
    };
  }
  var ARROW_BACK_ICON_PATH = "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z";
  var SUN_ICON_PATH = "M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10zm0-5a1 1 0 0 1 1 1v2a1 1 0 1 1-2 0V3a1 1 0 0 1 1-1zm0 17a1 1 0 0 1 1 1v2a1 1 0 1 1-2 0v-2a1 1 0 0 1 1-1zM4.22 4.22a1 1 0 0 1 1.41 0l1.42 1.42a1 1 0 1 1-1.42 1.41L4.22 5.63a1 1 0 0 1 0-1.41zm12.73 12.73a1 1 0 0 1 1.41 0l1.42 1.42a1 1 0 1 1-1.42 1.41l-1.41-1.42a1 1 0 0 1 0-1.41zM2 12a1 1 0 0 1 1-1h2a1 1 0 1 1 0 2H3a1 1 0 0 1-1-1zm17 0a1 1 0 0 1 1-1h2a1 1 0 1 1 0 2h-2a1 1 0 0 1-1-1zM4.22 19.78a1 1 0 0 1 0-1.41l1.42-1.42a1 1 0 1 1 1.41 1.42l-1.41 1.41a1 1 0 0 1-1.42 0zM16.95 7.05a1 1 0 0 1 0-1.41l1.42-1.42a1 1 0 1 1 1.41 1.42l-1.41 1.41a1 1 0 0 1-1.42 0z";
  var MOON_ICON_PATH = "M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z";
  function gooseBack(onBack, palette, scroll = false) {
    const size = 56 * gooseDP2;
    const iconSize = 32 * gooseDP2;
    const element = {
      id: "__back__",
      kind: "button",
      rect: { x: 16, y: 16, w: size, h: size },
      ...gooseGlassP,
      cornerRadius: size / 2,
      // circular
      tintColor: [0, 0, 0, 0],
      surfaceColor: palette.buttonSurface,
      highlight: null,
      // no edge highlight on the back button
      outerShadow: { ...gooseShadow, radius: 12 * gooseDP2, alpha: 0.08 },
      label: "",
      // no text label — icon replaces it
      labelColor: palette.backIconColor,
      showChevron: false,
      isInteractive: true,
      scroll,
      icon: {
        path: ARROW_BACK_ICON_PATH,
        size: iconSize,
        color: palette.backIconColor
      }
    };
    return {
      element,
      interaction: { onTap: () => onBack() }
    };
  }
  function gooseThemeBtn(onToggleTheme, palette, isLightTheme, canvasW, scroll = false) {
    const size = 56 * gooseDP2;
    const iconSize = 32 * gooseDP2;
    const element = {
      id: "__theme__",
      kind: "button",
      rect: { x: canvasW - 16 - size, y: 16, w: size, h: size },
      ...gooseGlassP,
      cornerRadius: size / 2,
      // circular
      tintColor: [0, 0, 0, 0],
      surfaceColor: palette.buttonSurface,
      highlight: null,
      // no edge highlight (matches back button)
      outerShadow: { ...gooseShadow, radius: 12 * gooseDP2, alpha: 0.08 },
      label: "",
      labelColor: palette.backIconColor,
      showChevron: false,
      isInteractive: true,
      scroll,
      icon: {
        // Sun in dark mode (click → light); moon in light mode (click → dark).
        path: isLightTheme ? MOON_ICON_PATH : SUN_ICON_PATH,
        size: iconSize,
        color: palette.backIconColor
      }
    };
    return {
      element,
      interaction: { onTap: () => onToggleTheme() }
    };
  }
  function gooseCenter(elements, contentTop, contentHeight, H) {
    const contentSize = contentHeight - contentTop;
    if (contentSize >= H) return contentHeight;
    const yOffset = Math.max(0, (H - contentSize) / 2 - contentTop);
    if (yOffset <= 0) return contentHeight;
    for (const el of elements) {
      if (el.id === "__back__" || el.id === "__theme__") continue;
      if (el.scroll === false && el.id !== "__pickimage__") continue;
      el.rect = { ...el.rect, y: el.rect.y + yOffset };
      if (el.hitRect) {
        el.hitRect = { ...el.hitRect, y: el.hitRect.y + yOffset };
      }
      if (el.isToggleKnob && el.isToggleKnob.trackOriginalY != null) {
        el.isToggleKnob.trackOriginalY += yOffset;
      }
      if (el.isBottomTabIndicator && el.isBottomTabIndicator.containerRect) {
        el.isBottomTabIndicator.containerRect = {
          ...el.isBottomTabIndicator.containerRect,
          y: el.isBottomTabIndicator.containerRect.y + yOffset
        };
      }
      if (el.isBottomTabContent && el.isBottomTabContent.containerCenterY != null) {
        el.isBottomTabContent.containerCenterY += yOffset;
      }
      if (el.isBottomTabIndicator && el.isBottomTabIndicator.containerCenterY != null) {
        el.isBottomTabIndicator.containerCenterY += yOffset;
      }
      if (el.isBottomTabIndicator && el.isBottomTabIndicator.tabContentRects) {
        el.isBottomTabIndicator.tabContentRects = el.isBottomTabIndicator.tabContentRects.map((r) => ({
          ...r,
          y: r.y + yOffset
        }));
      }
    }
    return contentHeight + yOffset;
  }

  // catalog/build-buttons.ts
  var BUTTON_STYLES = {
    transparent: { tintColor: [0, 0, 0, 0], surfaceColor: [0, 0, 0, 0], labelColor: [0, 0, 0, 1] },
    surface: { tintColor: [0, 0, 0, 0], surfaceColor: [1, 1, 1, 0.3], labelColor: [0, 0, 0, 1] },
    blue: { tintColor: [0 / 255, 136 / 255, 255 / 255, 1], surfaceColor: [0, 0, 0, 0], labelColor: [1, 1, 1, 1] },
    orange: { tintColor: [255 / 255, 141 / 255, 40 / 255, 1], surfaceColor: [0, 0, 0, 0], labelColor: [1, 1, 1, 1] },
    red: { tintColor: [255 / 255, 77 / 255, 79 / 255, 1], surfaceColor: [0, 0, 0, 0], labelColor: [1, 1, 1, 1] },
    green: { tintColor: [52 / 255, 199 / 255, 75 / 255, 1], surfaceColor: [0, 0, 0, 0], labelColor: [1, 1, 1, 1] },
    purple: { tintColor: [156 / 255, 39 / 255, 176 / 255, 1], surfaceColor: [0, 0, 0, 0], labelColor: [1, 1, 1, 1] }
  };
  function buildButtons(W, H, onBack, palette, onButtonTap, buttonsConfig) {
    const elements = [];
    const interactions = {};
    const back = gooseBack(onBack, palette);
    elements.push(back.element);
    interactions[back.element.id] = back.interaction;
    const defaultSpecs = [
      {
        id: "btn-transparent",
        label: "Transparent Liquid Button",
        tintColor: [0, 0, 0, 0],
        surfaceColor: [0, 0, 0, 0],
        labelColor: [0, 0, 0, 1]
      },
      {
        id: "btn-surface",
        label: "Surface Liquid Button",
        tintColor: [0, 0, 0, 0],
        surfaceColor: [1, 1, 1, 0.3],
        labelColor: [0, 0, 0, 1]
      },
      {
        id: "btn-tinted-blue",
        label: "Tinted Liquid Button",
        tintColor: [0 / 255, 136 / 255, 255 / 255, 1],
        surfaceColor: [0, 0, 0, 0],
        labelColor: [1, 1, 1, 1]
      },
      {
        id: "btn-tinted-orange",
        label: "Tinted Liquid Button",
        tintColor: [255 / 255, 141 / 255, 40 / 255, 1],
        surfaceColor: [0, 0, 0, 0],
        labelColor: [1, 1, 1, 1]
      }
    ];
    const configs = buttonsConfig && buttonsConfig.length ? buttonsConfig.map((c, i) => {
      var _a;
      const st = typeof c.style === "string" ? BUTTON_STYLES[c.style] || BUTTON_STYLES.blue : c.style || BUTTON_STYLES.transparent;
      return {
        id: c.id || "btn-" + i,
        label: (_a = c.label) != null ? _a : "\u6309\u94AE " + (i + 1),
        tintColor: st.tintColor,
        surfaceColor: st.surfaceColor,
        labelColor: st.labelColor
      };
    }) : defaultSpecs;
    const spacing = 16 * gooseDP2;
    let cursorY = 0;
    for (const spec of configs) {
      const textW = gooseTextW(spec.label, gooseTextSz);
      const w = Math.ceil(textW + 2 * gooseBtnPad);
      const x = (W - w) / 2;
      const el = gooseBtn(spec.id, { x, y: cursorY, w, h: gooseBtnH }, spec);
      elements.push(el);
      if (onButtonTap) {
        interactions[spec.id] = { onTap: () => onButtonTap(spec.id) };
      }
      cursorY += gooseBtnH + spacing;
    }
    const contentHeight = cursorY - spacing;
    const finalHeight = gooseCenter(elements, 0, contentHeight, H);
    return { elements, interactions, contentHeight: finalHeight };
  }

  // catalog/build-toggle.ts
  function buildToggle(W, H, onBack, state, setState, rendererRef, palette = gooseLight, single = false, cardOnly = false) {
    const elements = [];
    const interactions = {};
    const back = gooseBack(onBack, palette);
    elements.push(back.element);
    interactions[back.element.id] = back.interaction;
    const TOGGLE_ACCENT_T = palette.toggleAccent;
    const TOGGLE_TRACK_T = palette.toggleTrackOff;
    const CARD_BG_T = palette.toggleCardBg;
    const TOGGLE_W = 64 * gooseDP2;
    const TOGGLE_H = 28 * gooseDP2;
    const TOGGLE_KNOB_W = 40 * gooseDP2;
    const TOGGLE_KNOB_H = 24 * gooseDP2;
    const TOGGLE_DRAG = 20 * gooseDP2;
    const TOGGLE_PADDING = 2 * gooseDP2;
    const KNOB_REFRACTION_HEIGHT = 5 * gooseDP2;
    const KNOB_REFRACTION_AMOUNT = -10 * gooseDP2;
    const KNOB_BLUR_RADIUS = 8 * gooseDP2;
    const KNOB_HIGHLIGHT = null;
    const KNOB_OUTER_SHADOW = {
      radius: 4 * gooseDP2,
      alpha: 0.05,
      // Black.copy(alpha=0.05f)
      offsetX: 0,
      offsetY: 4 / 6 * gooseDP2,
      // default offset = radius/6
      color: [0, 0, 0]
    };
    const KNOB_INNER_SHADOW = {
      radius: 4 * gooseDP2,
      alpha: 0.15,
      // InnerShadow default color alpha; renderer multiplies by progress
      offsetX: 0,
      offsetY: 4 * gooseDP2
      // default offset = radius; renderer modulates by progress
    };
    const drawWall = !single || !cardOnly;
    const drawCard = !single || cardOnly;
    const t1CenterX = W / 2;
    const t1TrackX = t1CenterX - TOGGLE_W / 2;
    const t1TrackY = 0;
    const t1KnobX = t1TrackX + TOGGLE_PADDING;
    const t1KnobY = t1TrackY + (TOGGLE_H - TOGGLE_KNOB_H) / 2;
    if (drawWall) {
      const t1TrackEl = gooseRect(
        "toggle1-track",
        { x: t1TrackX, y: t1TrackY, w: TOGGLE_W, h: TOGGLE_H },
        TOGGLE_TRACK_T,
        TOGGLE_H / 2
        // Capsule = height/2
      );
      t1TrackEl.isToggleTrack = {
        groupId: "toggle1",
        offColor: TOGGLE_TRACK_T,
        onColor: [...TOGGLE_ACCENT_T, 1]
      };
      elements.push(t1TrackEl);
      const t1KnobEl = gooseGlass(
        "toggle1-knob",
        { x: t1KnobX, y: t1KnobY, w: TOGGLE_KNOB_W, h: TOGGLE_KNOB_H },
        {
          cornerRadius: TOGGLE_KNOB_H / 2,
          // Capsule = height/2
          refractionHeight: KNOB_REFRACTION_HEIGHT,
          refractionAmount: KNOB_REFRACTION_AMOUNT,
          blurRadius: KNOB_BLUR_RADIUS,
          saturation: 1,
          // NO saturation boost — toggle effects block only has blur+lens
          surfaceColor: [0, 0, 0, 0],
          // no surface — white overlay handles the rest
          highlight: KNOB_HIGHLIGHT,
          outerShadow: KNOB_OUTER_SHADOW,
          innerShadow: KNOB_INNER_SHADOW,
          chromaticAberration: true
          // lens(chromaticAberration = true)
        }
      );
      t1KnobEl.isToggleKnob = {
        groupId: "toggle1",
        dragWidth: TOGGLE_DRAG,
        // CombinedBackdrop track color info (faithful to LiquidToggle.kt):
        //   backdrop = rememberCombinedBackdrop(backdrop, scaled trackBackdrop)
        //   - backdrop = LayerBackdrop (wallpaper) for t1
        //   - trackBackdrop captured at the TRACK's original screen position (FIXED)
        // The knob samples wallpaper (unscaled) + scaled track color rect.
        // Track color lerps between trackColor (off) and accentColor (on).
        //   trackColor   = Color(0xFF787878).copy(0.2f) → RGBA(120,120,120,0.2)
        //   accentColor  = Color(0xFF34C759)            → RGBA(52,199,89,1.0)
        trackColorOff: TOGGLE_TRACK_T,
        trackColorOn: [...TOGGLE_ACCENT_T, 1],
        trackW: TOGGLE_W,
        trackH: TOGGLE_H,
        // Track's original screen position (FIXED — does NOT move with knob).
        // Faithful to: trackBackdrop is captured at the track Box's position.
        // The scale's pivot is the knob's current center, so the scaled track
        // content moves PARTIALLY with the knob (rate = 1 - scale).
        trackOriginalX: t1TrackX,
        trackOriginalY: t1TrackY
        // No solidBackdropColor → samples wallpaper texture (LayerBackdrop case).
      };
      elements.push(t1KnobEl);
    }
    let cardBottom = 24 * gooseDP2;
    if (drawCard) {
      const VISIBLE_CARD_W = 176 * gooseDP2;
      const VISIBLE_CARD_H = 76 * gooseDP2;
      const cardX = (W - VISIBLE_CARD_W) / 2;
      const cardY = drawWall ? t1TrackY + TOGGLE_H + 16 + 24 : 24 * gooseDP2;
      const cardW = VISIBLE_CARD_W;
      const cardH = VISIBLE_CARD_H;
      const cardRadius = 32 * gooseDP2;
      const cardBg = CARD_BG_T;
      elements.push(gooseRect("toggle-card", { x: cardX, y: cardY, w: cardW, h: cardH }, cardBg, cardRadius));
      const t2TrackX = cardX + 24 + 32;
      const t2TrackY = cardY + 24;
      const t2KnobX = t2TrackX + TOGGLE_PADDING;
      const t2KnobY = t2TrackY + (TOGGLE_H - TOGGLE_KNOB_H) / 2;
      const t2TrackEl = gooseRect(
        "toggle2-track",
        { x: t2TrackX, y: t2TrackY, w: TOGGLE_W, h: TOGGLE_H },
        TOGGLE_TRACK_T,
        TOGGLE_H / 2
      );
      t2TrackEl.isToggleTrack = {
        groupId: "toggle2",
        offColor: TOGGLE_TRACK_T,
        onColor: [...TOGGLE_ACCENT_T, 1]
      };
      elements.push(t2TrackEl);
      const t2KnobEl = gooseGlass(
        "toggle2-knob",
        { x: t2KnobX, y: t2KnobY, w: TOGGLE_KNOB_W, h: TOGGLE_KNOB_H },
        {
          cornerRadius: TOGGLE_KNOB_H / 2,
          refractionHeight: KNOB_REFRACTION_HEIGHT,
          refractionAmount: KNOB_REFRACTION_AMOUNT,
          blurRadius: KNOB_BLUR_RADIUS,
          saturation: 1,
          surfaceColor: [0, 0, 0, 0],
          highlight: KNOB_HIGHLIGHT,
          outerShadow: KNOB_OUTER_SHADOW,
          innerShadow: KNOB_INNER_SHADOW,
          chromaticAberration: true
        }
      );
      t2KnobEl.isToggleKnob = {
        groupId: "toggle2",
        dragWidth: TOGGLE_DRAG,
        // CombinedBackdrop track color info (faithful to LiquidToggle.kt):
        //   backdrop = rememberCombinedBackdrop(backdrop, scaled trackBackdrop)
        //   - backdrop = rememberCanvasBackdrop { drawRect(backgroundColor) }
        //     → solid card color (NOT wallpaper) for t2
        //   - trackBackdrop captured at the TRACK's original screen position (FIXED)
        // The knob samples card color (solid) + scaled track color rect.
        // Track color lerps between trackColor (off) and accentColor (on).
        //   trackColor   = Color(0xFF787878).copy(0.2f) → RGBA(120,120,120,0.2)
        //   accentColor  = Color(0xFF34C759)            → RGBA(52,199,89,1.0)
        trackColorOff: TOGGLE_TRACK_T,
        trackColorOn: [...TOGGLE_ACCENT_T, 1],
        trackW: TOGGLE_W,
        trackH: TOGGLE_H,
        // Track's original screen position (FIXED — does NOT move with knob).
        trackOriginalX: t2TrackX,
        trackOriginalY: t2TrackY,
        // Solid backdrop color: the card's background color (faithful to
        // ToggleContent.kt's `rememberCanvasBackdrop { drawRect(backgroundColor) }`).
        // When set, the shader uses this color instead of sampling the wallpaper
        // texture for the outer backdrop portion of the CombinedBackdrop.
        solidBackdropColor: cardBg
      };
      elements.push(t2KnobEl);
      cardBottom = cardY + cardH + 24;
    }
    const makeToggleInteract = (groupId, dragWidth) => gooseDrag({
      groupId,
      trackX: 0,
      dragW: dragWidth,
      rendererRef,
      onValueChange: (f) => {
        const finalOn = f >= 0.5;
        setState((prev) => prev.toggleOn === finalOn ? prev : { toggleOn: finalOn });
      },
      ...toggleDragBindings,
      snap: (f) => f >= 0.5 ? 1 : 0,
      // snap to 0 or 1
      onTapJump: false
      // tap = flip (handled by custom onTap below)
    });
    if (drawWall) {
      const toggle1Interact = makeToggleInteract("toggle1", TOGGLE_DRAG);
      toggle1Interact.onTap = () => setState((prev) => ({ toggleOn: !prev.toggleOn }));
      interactions["toggle1-track"] = toggle1Interact;
      interactions["toggle1-knob"] = toggle1Interact;
    }
    if (drawCard) {
      const toggle2Interact = makeToggleInteract("toggle2", TOGGLE_DRAG);
      toggle2Interact.onTap = () => setState((prev) => ({ toggleOn: !prev.toggleOn }));
      interactions["toggle2-track"] = toggle2Interact;
      interactions["toggle2-knob"] = toggle2Interact;
    }
    const contentHeight = cardBottom;
    const finalHeight = gooseCenter(elements, 0, contentHeight, H);
    return { elements, interactions, contentHeight: finalHeight };
  }

  // catalog/build-slider.ts
  function buildSlider(W, H, onBack, state, setState, rendererRef, palette = gooseLight, single = false, cardOnly = false) {
    const elements = [];
    const interactions = {};
    const back = gooseBack(onBack, palette);
    elements.push(back.element);
    interactions[back.element.id] = back.interaction;
    const SLIDER_ACCENT_T = palette.sliderAccent;
    const SLIDER_TRACK_T = palette.sliderTrackOff;
    const CARD_BG_T = palette.sliderCardBg;
    const SLIDER_PAD = 32 * gooseDP2;
    const gooseTrackH3 = 6 * gooseDP2;
    const gooseKnobW3 = 40 * gooseDP2;
    const gooseKnobH3 = 24 * gooseDP2;
    const drawWall = !single || !cardOnly;
    const drawCard = !single || cardOnly;
    const s1TrackX = SLIDER_PAD;
    const s1TrackY = 0;
    const s1TrackW = W - 2 * SLIDER_PAD;
    const s1KnobBaseX = s1TrackX - gooseKnobW3 / 4;
    const s1KnobY = s1TrackY + (gooseTrackH3 - gooseKnobH3) / 2;
    const gooseHitH3 = 48 * gooseDP2;
    const SLIDER_KNOB_HIT_H = 48 * gooseDP2;
    if (drawWall) {
      const s1TrackEl = gooseRect("slider1-track", { x: s1TrackX, y: s1TrackY, w: s1TrackW, h: gooseTrackH3 }, SLIDER_TRACK_T, gooseTrackH3 / 2);
      s1TrackEl.hitRect = { x: s1TrackX, y: s1TrackY + (gooseTrackH3 - gooseHitH3) / 2, w: s1TrackW, h: gooseHitH3 };
      elements.push(s1TrackEl);
      const s1FillEl = gooseRect("slider1-fill", { x: s1TrackX, y: s1TrackY, w: gooseTrackH3, h: gooseTrackH3 }, [...SLIDER_ACCENT_T, 1], gooseTrackH3 / 2);
      s1FillEl.isSliderFill = { groupId: "slider1", trackX: s1TrackX, trackW: s1TrackW, knobW: gooseKnobW3, minW: 0 };
      elements.push(s1FillEl);
      const s1KnobEl = gooseGlass(
        "slider1-knob",
        { x: s1KnobBaseX, y: s1KnobY, w: gooseKnobW3, h: gooseKnobH3 },
        {
          cornerRadius: gooseKnobH3 / 2,
          refractionHeight: 10 * gooseDP2,
          // lens height when pressed (bigger than toggle)
          refractionAmount: -14 * gooseDP2,
          // lens amount when pressed
          blurRadius: 8 * gooseDP2,
          // frosted blur at rest (renderer modulates)
          saturation: 1,
          // NO saturation boost — slider effects block only has blur+lens
          surfaceColor: [0, 0, 0, 0],
          highlight: null,
          outerShadow: { radius: 4 * gooseDP2, alpha: 0.05, offsetX: 0, offsetY: 4 / 6 * gooseDP2, color: [0, 0, 0] },
          innerShadow: { radius: 4 * gooseDP2, alpha: 0.15, offsetX: 0, offsetY: 4 * gooseDP2 },
          chromaticAberration: true
        }
      );
      s1KnobEl.isToggleKnob = { groupId: "slider1", dragWidth: s1TrackW - gooseKnobW3 / 2, velocityDivisor: 10 };
      s1KnobEl.hitRect = {
        x: s1KnobBaseX,
        y: s1KnobY + (gooseKnobH3 - SLIDER_KNOB_HIT_H) / 2,
        w: gooseKnobW3,
        h: SLIDER_KNOB_HIT_H
      };
      elements.push(s1KnobEl);
    }
    let cardContentBottom = s1TrackY + gooseKnobH3 + 24 * gooseDP2;
    const VISIBLE_CARD_H = 24 + 48;
    const cardX = 24 * gooseDP2;
    const cardW = W - 2 * cardX;
    const cardH = VISIBLE_CARD_H;
    const cardY = drawWall ? s1TrackY + gooseKnobH3 + 16 + 24 : 24 * gooseDP2;
    const cardRadius = 32 * gooseDP2;
    const s2TrackX = cardX + 24 + SLIDER_PAD;
    const s2TrackW = cardW - 2 * 24 - 2 * SLIDER_PAD;
    const s2TrackY = cardY + 24 + (gooseKnobH3 - gooseTrackH3) / 2;
    const s2KnobBaseX = s2TrackX - gooseKnobW3 / 4;
    const s2KnobY = s2TrackY + (gooseTrackH3 - gooseKnobH3) / 2;
    if (drawCard) {
      elements.push(gooseRect("slider-card", { x: cardX, y: cardY, w: cardW, h: cardH }, CARD_BG_T, cardRadius));
      const s2TrackEl = gooseRect("slider2-track", { x: s2TrackX, y: s2TrackY, w: s2TrackW, h: gooseTrackH3 }, SLIDER_TRACK_T, gooseTrackH3 / 2);
      s2TrackEl.hitRect = { x: s2TrackX, y: s2TrackY + (gooseTrackH3 - gooseHitH3) / 2, w: s2TrackW, h: gooseHitH3 };
      elements.push(s2TrackEl);
      const s2FillEl = gooseRect("slider2-fill", { x: s2TrackX, y: s2TrackY, w: gooseTrackH3, h: gooseTrackH3 }, [...SLIDER_ACCENT_T, 1], gooseTrackH3 / 2);
      s2FillEl.isSliderFill = { groupId: "slider2", trackX: s2TrackX, trackW: s2TrackW, knobW: gooseKnobW3, minW: 0 };
      elements.push(s2FillEl);
      const s2KnobEl = gooseGlass(
        "slider2-knob",
        { x: s2KnobBaseX, y: s2KnobY, w: gooseKnobW3, h: gooseKnobH3 },
        {
          cornerRadius: gooseKnobH3 / 2,
          refractionHeight: 10 * gooseDP2,
          refractionAmount: -14 * gooseDP2,
          blurRadius: 8 * gooseDP2,
          saturation: 1,
          // NO saturation boost — slider effects block only has blur+lens
          surfaceColor: [0, 0, 0, 0],
          highlight: null,
          outerShadow: { radius: 4 * gooseDP2, alpha: 0.05, offsetX: 0, offsetY: 4 / 6 * gooseDP2, color: [0, 0, 0] },
          innerShadow: { radius: 4 * gooseDP2, alpha: 0.15, offsetX: 0, offsetY: 4 * gooseDP2 },
          chromaticAberration: true
        }
      );
      s2KnobEl.isToggleKnob = { groupId: "slider2", dragWidth: s2TrackW - gooseKnobW3 / 2, velocityDivisor: 10 };
      s2KnobEl.hitRect = {
        x: s2KnobBaseX,
        y: s2KnobY + (gooseKnobH3 - SLIDER_KNOB_HIT_H) / 2,
        w: gooseKnobW3,
        h: SLIDER_KNOB_HIT_H
      };
      elements.push(s2KnobEl);
      cardContentBottom = cardY + cardH + 24 * gooseDP2;
    }
    const SLIDER_DRAG_W1 = s1TrackW - gooseKnobW3 / 2;
    const makeSliderInteract = (groupId, trackX, dragW) => gooseDrag({
      groupId,
      trackX,
      dragW,
      rendererRef,
      onValueChange: (f) => setState({ sliderValue: f * 100 }),
      ...sliderDragBindings,
      // No liveUpdate — state synced on dragEnd only (avoids feedback loop
      // with toggleTargets effect that would fight the spring).
      liveUpdate: false
    });
    if (drawWall) {
      interactions["slider1-track"] = makeSliderInteract("slider1", s1TrackX, SLIDER_DRAG_W1);
      interactions["slider1-knob"] = interactions["slider1-track"];
    }
    if (drawCard) {
      const SLIDER_DRAG_W2 = s2TrackW - gooseKnobW3 / 2;
      interactions["slider2-track"] = makeSliderInteract("slider2", s2TrackX, SLIDER_DRAG_W2);
      interactions["slider2-knob"] = interactions["slider2-track"];
    }
    const contentHeight = cardContentBottom;
    const finalHeight = gooseCenter(elements, 0, contentHeight, H);
    return { elements, interactions, contentHeight: finalHeight };
  }

  // catalog/build-bottom-tabs.ts
  function buildBottomTabs(W, H, onBack, state, setState, rendererRef = null, palette = gooseLight, tabsConfig, single = false, second = false) {
    var _a, _b;
    const elements = [];
    const interactions = {};
    const back = gooseBack(onBack, palette);
    elements.push(back.element);
    interactions[back.element.id] = back.interaction;
    const TABS_PAD = 36 * gooseDP2;
    const TABS_W = W - 2 * TABS_PAD;
    const iconColor = palette.tabsContentColor;
    const containerColor = palette.tabsContainer;
    const accentT = palette.tabsAccent;
    const CONTAINER_H = 64 * gooseDP2;
    const GLASS_H = 56 * gooseDP2;
    const GLASS_PAD = 4 * gooseDP2;
    const containerX = TABS_PAD;
    const containerW = TABS_W;
    const containerR = CONTAINER_H / 2;
    const glassX = TABS_PAD + GLASS_PAD;
    const glassW = TABS_W - 2 * GLASS_PAD;
    const glassR = GLASS_H / 2;
    function buildTabBar(idPrefix, tabs, selectedTab, onSelect, y) {
      var _a2, _b2, _c;
      const tabsCount = tabs ? tabs.length : 3;
      const tabW = glassW / tabsCount;
      const glassY = y + GLASS_PAD;
      const containerEl = gooseGlass(
        `${idPrefix}-container`,
        { x: containerX, y, w: containerW, h: CONTAINER_H },
        {
          cornerRadius: containerR,
          // 32dp capsule
          refractionHeight: 24 * gooseDP2,
          refractionAmount: -24 * gooseDP2,
          blurRadius: 8 * gooseDP2,
          saturation: 1.5,
          surfaceColor: containerColor,
          // 容器 uses default Highlight.Default (alpha=1.0, width=0.5dp).
          // The original Container Row doesn't pass highlight= → uses DefaultHighlight.
          highlight: { ...gooseHL, alpha: 1 },
          outerShadow: null,
          depthEffect: true
        }
      );
      containerEl.isBottomTabContainer = { groupId: idPrefix, tabsCount };
      elements.push(containerEl);
      const dragInteractions = gooseTabDrag(idPrefix, tabW, tabsCount, onSelect, rendererRef);
      for (let i = 0; i < tabsCount; i++) {
        const id = `${idPrefix}-tab-${i}`;
        const cfg = tabs == null ? void 0 : tabs[i];
        const iconPath = (_a2 = cfg == null ? void 0 : cfg.icon) != null ? _a2 : gooseFlight;
        const label = (_b2 = cfg == null ? void 0 : cfg.label) != null ? _b2 : `Tab ${i + 1}`;
        const tabEl = gooseText(
          id,
          { x: glassX + tabW * i, y: glassY, w: tabW, h: GLASS_H },
          label,
          {
            color: palette.tabsContentColor,
            fontSizePx: 12,
            fontWeight: 400,
            align: "center",
            paddingPx: 0,
            halo: palette.tabsTextHalo,
            icon: { path: iconPath, size: 24, layoutSize: 28, color: iconColor, viewport: (_c = cfg == null ? void 0 : cfg.viewport) != null ? _c : 960 }
          }
        );
        tabEl.isBottomTabContent = {
          groupId: idPrefix,
          // Container center = scale origin for the whole bar. Tab content
          // scales around this point (not its own center), matching the
          // original where container is the parent and its transform applies
          // uniformly to all children.
          containerCenterX: containerX + containerW / 2,
          containerCenterY: y + CONTAINER_H / 2,
          containerWidth: containerW
        };
        elements.push(tabEl);
        interactions[id] = {
          onTap: () => onSelect(i),
          onDragStart: dragInteractions.onDragStart,
          onDrag: dragInteractions.onDrag,
          onDragEnd: dragInteractions.onDragEnd
        };
      }
      interactions[`${idPrefix}-container`] = dragInteractions;
      const indicatorEl = gooseGlass(
        `${idPrefix}-indicator`,
        // Indicator glass x = TABS_PAD + 4dp. The renderer adds fraction*tabW
        // via toggleXOffset (isBottomTabIndicator.dragWidth = tabW).
        { x: TABS_PAD + GLASS_PAD, y: glassY, w: tabW, h: GLASS_H },
        {
          cornerRadius: glassR,
          refractionHeight: 10 * gooseDP2,
          refractionAmount: -14 * gooseDP2,
          // Faithful to original: indicator has NO blur and NO vibrancy (only
          // lens when pressed). The original indicator's effects block contains
          // ONLY lens — no vibrancy(), no blur().
          blurRadius: 0,
          saturation: 1,
          // Indicator surface is TRANSPARENT (no tint, no surface color).
          // Faithful to LiquidBottomTabs.kt: the indicator's onDrawSurface is
          //   drawRect(dimColor 0.1, alpha=1-progress)  (dim at rest, clear pressed)
          //   drawRect(Black 0.03*progress)             (slight darken when pressed)
          // This dim overlay is handled by the isBottomTabIndicator dimColor path
          // in post-passes. The indicator is NOT blue — it's transparent glass
          // that refracts the content beneath. (The original's blue appearance
          // comes from CombinedBackdrop with a hidden tinted layer, which we
          // don't replicate — the indicator shows the scene as-is.)
          tintColor: [0, 0, 0, 0],
          surfaceColor: [0, 0, 0, 0],
          // Faithful to original: highlight = Highlight.Default.copy(alpha=progress).
          // alpha=0 at rest (no edge highlight), full when pressed.
          highlight: { ...gooseHL, alpha: 1 },
          // Shadow(alpha=progress) — faithful to Shadow.Default:
          //   radius=24dp, offset=(0, radius/6=4dp), color=Black(0.1), alpha=1*progress.
          // Renderer modulates alpha by pressProgress.
          outerShadow: { radius: 24 * gooseDP2, alpha: 0.1, offsetX: 0, offsetY: 24 / 6 * gooseDP2, color: [0, 0, 0] },
          // InnerShadow(radius=8dp*progress, alpha=progress) — color=Black(0.15), offset=(0, radius).
          innerShadow: { radius: 8 * gooseDP2, alpha: 0.15, offsetX: 0, offsetY: 8 * gooseDP2 },
          chromaticAberration: true
        }
      );
      indicatorEl.isBottomTabIndicator = {
        groupId: idPrefix,
        dragWidth: tabW,
        dimColor: palette.backIconColor,
        // CombinedBackdrop: faithful to LiquidBottomTabs.kt 指示器's
        //   rememberCombinedBackdrop(backdrop, tabsBackdrop)
        // - backdrop (outer) = LayerBackdrop (wallpaper)
        // - tabsBackdrop (inner) = 内层背景板 (hidden Row's 56dp glass),
        //   inset 4dp on all sides relative to the 指示器.
        // The 指示器 samples wallpaper (outer) + the scene FBO (容器
        // glass + content) composited inside an inset capsule SDF.
        accentColor: [...accentT],
        // containerRect = the 内层背景板 capsule (hidden Row's 56dp glass),
        // inset 4dp on all sides from the 容器. Faithful to LiquidBottomTabs.kt:
        //   hidden Row = height(56dp).fillMaxWidth().padding(horizontal=4dp)
        //   drawBackdrop paints 56dp × (TABS_W - 8dp) = 56dp × glassW.
        containerRect: { x: glassX, y: glassY, w: glassW, h: GLASS_H },
        // Container center + width — the indicator scales around the container
        // center (like tab-content), matching the original parent-child transform.
        containerCenterX: containerX + containerW / 2,
        containerCenterY: y + CONTAINER_H / 2,
        containerWidth: containerW,
        // Tab content IDs + rects — for the blue tint mask. The renderer looks
        // up each tab's fgTexture (icon+label alpha) and uses it to tint only
        // the opaque icon/label pixels blue inside the indicator.
        tabContentIds: Array.from({ length: tabsCount }, (_, i) => `${idPrefix}-tab-${i}`),
        tabContentRects: Array.from({ length: tabsCount }, (_, i) => ({
          x: glassX + tabW * i,
          y: glassY,
          w: tabW,
          h: GLASS_H
        }))
      };
      elements.push(indicatorEl);
    }
    const drawFirst = !second;
    const drawSecond = !single || second;
    const secondY = drawFirst ? CONTAINER_H + 32 : 0;
    if (drawFirst) {
      buildTabBar("tabs3", (_a = tabsConfig == null ? void 0 : tabsConfig[0]) != null ? _a : null, state.selectedTab, (i) => setState({ selectedTab: i }), 0);
    }
    if (drawSecond) {
      const defaultTabs4 = [
        { icon: gooseFlight, label: "Tab 1" },
        { icon: gooseFlight, label: "Tab 2" },
        { icon: gooseFlight, label: "Tab 3" },
        { icon: gooseFlight, label: "Tab 4" }
      ];
      buildTabBar("tabs4", (_b = tabsConfig == null ? void 0 : tabsConfig[1]) != null ? _b : defaultTabs4, state.selectedTab2, (i) => setState({ selectedTab2: i }), secondY);
    }
    const bars = (drawFirst ? 1 : 0) + (drawSecond ? 1 : 0);
    const contentHeight = bars === 2 ? 2 * CONTAINER_H + 32 : CONTAINER_H;
    const finalHeight = gooseCenter(elements, 0, contentHeight, H);
    return { elements, interactions, contentHeight: finalHeight };
  }

  // catalog/build-dialog.ts
  function buildDialog(W, H, onBack, state, palette = gooseLight, dialogConfig, onDialogTap) {
    var _a, _b, _c, _d;
    const elements = [];
    const interactions = {};
    const dlgTitle = (_a = dialogConfig == null ? void 0 : dialogConfig.title) != null ? _a : "Dialog Title";
    const dlgBody = (_b = dialogConfig == null ? void 0 : dialogConfig.body) != null ? _b : gooseLorem;
    const dlgCancel = (_c = dialogConfig == null ? void 0 : dialogConfig.cancelText) != null ? _c : "Cancel";
    const dlgOkay = (_d = dialogConfig == null ? void 0 : dialogConfig.okayText) != null ? _d : "Okay";
    const back = gooseBack(onBack, palette, true);
    elements.push(back.element);
    interactions[back.element.id] = back.interaction;
    const scrim = gooseRect(
      "dialog-scrim",
      { x: 0, y: 0, w: W, h: H },
      palette.dialogDim,
      0
    );
    scrim.scroll = false;
    elements.push(scrim);
    const PAD = 40 * gooseDP2;
    const MAX_CARD_W = 420 * gooseDP2;
    const CARD_W = Math.min(W - 2 * PAD, MAX_CARD_W);
    const CARD_X = (W - CARD_W) / 2;
    const CARD_H = 276 * gooseDP2;
    const CARD_Y = (H - CARD_H) / 2;
    const card = gooseGlass(
      "dialog-card",
      { x: CARD_X, y: CARD_Y, w: CARD_W, h: CARD_H },
      {
        cornerRadius: 48 * gooseDP2,
        refractionHeight: 24 * gooseDP2,
        refractionAmount: -48 * gooseDP2,
        blurRadius: palette.dialogBlurRadius,
        saturation: 1.5,
        brightness: palette.dialogBrightness,
        surfaceColor: palette.dialogContainer,
        // Highlight.Plain: style color is White.copy(alpha = 0.38f); Highlight.alpha stays 1.
        highlight: { ...gooseHL, mode: 2, color: [1, 1, 1], alpha: 0.38, widthDp: 0.5 },
        outerShadow: null,
        depthEffect: true
      }
    );
    card.useSeparableBlur = true;
    if (state.capsuleShape) {
      card.useContinuousSdf = true;
    }
    elements.push(card);
    elements.push(
      gooseText(
        "dialog-title",
        { x: CARD_X + 28, y: CARD_Y + 24, w: CARD_W - 56, h: 36 },
        dlgTitle,
        { color: palette.dialogContentColor, fontSizePx: 24, fontWeight: 500, align: "left", paddingPx: 0, halo: "none" }
      )
    );
    const isLight = palette.dialogBrightness > 0.1;
    const bodyAlpha = isLight ? 0.68 : 0.78;
    const bodyColor = [
      palette.dialogContentColor[0],
      palette.dialogContentColor[1],
      palette.dialogContentColor[2],
      bodyAlpha
    ];
    elements.push(
      gooseText(
        "dialog-body",
        { x: CARD_X + 24, y: CARD_Y + 68 + 12, w: CARD_W - 48, h: 100 },
        dlgBody,
        {
          color: bodyColor,
          fontSizePx: 15,
          fontWeight: 400,
          align: "left",
          wrap: true,
          valign: "top",
          maxLines: 5,
          paddingPx: 0,
          halo: "none"
        }
      )
    );
    const BTN_H = 48 * gooseDP2;
    const BTN_W = (CARD_W - 2 * 24 * gooseDP2 - 16 * gooseDP2) / 2;
    const BTN_Y = CARD_Y + CARD_H - 24 * gooseDP2 - BTN_H;
    const CANCEL_X = CARD_X + 24 * gooseDP2;
    const OKAY_X = CANCEL_X + BTN_W + 16 * gooseDP2;
    const cancelBtn = gooseBtn(
      "dialog-cancel",
      { x: CANCEL_X, y: BTN_Y, w: BTN_W, h: BTN_H },
      {
        label: "",
        tintColor: [0, 0, 0, 0],
        surfaceColor: [palette.dialogContainer[0], palette.dialogContainer[1], palette.dialogContainer[2], 0.2],
        labelColor: palette.dialogContentColor,
        saturation: 1,
        // no vibrancy — Cancel is a solid background, not glass
        brightness: 0,
        contrast: 1
      },
      false
    );
    cancelBtn.refractionHeight = 0;
    cancelBtn.refractionAmount = 0;
    cancelBtn.blurRadius = 0;
    cancelBtn.highlight = null;
    cancelBtn.outerShadow = null;
    elements.push(cancelBtn);
    interactions["dialog-cancel"] = { onTap: () => onDialogTap == null ? void 0 : onDialogTap("cancel") };
    elements.push(
      gooseText(
        "dialog-cancel-label",
        { x: CANCEL_X, y: BTN_Y, w: BTN_W, h: BTN_H },
        dlgCancel,
        { color: palette.dialogContentColor, fontSizePx: 16, fontWeight: 400, align: "center", paddingPx: 0, halo: "none" }
      )
    );
    const okayBtn = gooseBtn(
      "dialog-okay",
      { x: OKAY_X, y: BTN_Y, w: BTN_W, h: BTN_H },
      {
        label: "",
        tintColor: [0, 0, 0, 0],
        surfaceColor: palette.dialogAccent,
        labelColor: [1, 1, 1, 1],
        saturation: 1,
        // no vibrancy — Okay is a solid background, not glass
        brightness: 0,
        contrast: 1
      },
      false
    );
    okayBtn.refractionHeight = 0;
    okayBtn.refractionAmount = 0;
    okayBtn.blurRadius = 0;
    okayBtn.highlight = null;
    okayBtn.outerShadow = null;
    elements.push(okayBtn);
    interactions["dialog-okay"] = { onTap: () => onDialogTap == null ? void 0 : onDialogTap("okay") };
    elements.push(
      gooseText(
        "dialog-okay-label",
        { x: OKAY_X, y: BTN_Y, w: BTN_W, h: BTN_H },
        dlgOkay,
        { color: [1, 1, 1, 1], fontSizePx: 16, fontWeight: 400, align: "center", paddingPx: 0 }
      )
    );
    for (const el of elements) el.scroll = false;
    gooseCenter(elements, 0, H, H);
    return { elements, interactions, contentHeight: H };
  }

  // catalog/build-magnifier.ts
  var magDragStart = { x: 0, y: 0 };
  function measureWrappedHeight(text, fontPx, maxW) {
    const lineH = fontPx * 1.35;
    const words = text.split(/\s+/);
    let cur = "";
    let lines = 0;
    for (const word of words) {
      const test = cur ? cur + " " + word : word;
      if (gooseTextW(test, fontPx) <= maxW || !cur) {
        cur = test;
      } else {
        lines++;
        cur = word;
      }
    }
    if (cur) lines++;
    return lines * lineH;
  }
  function buildMagnifier(W, H, onBack, state, setState, palette = gooseLight) {
    const elements = [];
    const interactions = {};
    const back = gooseBack(onBack, palette);
    elements.push(back.element);
    interactions[back.element.id] = back.interaction;
    const cardX = 24 * gooseDP2;
    const cardY = 0;
    const cardW = W - 2 * cardX;
    const cardRadius = 32 * gooseDP2;
    const innerPad = 24 * gooseDP2;
    const textW = cardW - 2 * innerPad;
    const fontPx = 16;
    const textH = measureWrappedHeight(gooseLorem, fontPx, textW);
    const cardH = textH + 2 * innerPad;
    elements.push(gooseRect("mag-card", { x: cardX, y: cardY, w: cardW, h: cardH }, palette.magnifierCardBg, cardRadius));
    elements.push(
      gooseText(
        "mag-text",
        { x: cardX + innerPad, y: cardY + innerPad, w: textW, h: textH },
        gooseLorem,
        {
          color: palette.magnifierContentColor,
          fontSizePx: fontPx,
          fontWeight: 400,
          align: "left",
          wrap: true,
          paddingPx: 0,
          halo: "none"
          // card is solid; no halo needed
        }
      )
    );
    const cursorBaseX = W / 2 - 2;
    const cursorBaseY = cardY + cardH / 2 - 12 * gooseDP2;
    const cursorX = cursorBaseX + state.magnifierX;
    const cursorY = cursorBaseY + state.magnifierY;
    const cursorEl = gooseRect("mag-cursor", { x: cursorX, y: cursorY, w: 4 * gooseDP2, h: 24 * gooseDP2 }, palette.magnifierAccent, 2 * gooseDP2);
    cursorEl.hitRect = { x: cursorX - 22 * gooseDP2, y: cursorY - 12 * gooseDP2, w: 48 * gooseDP2, h: 48 * gooseDP2 };
    elements.push(cursorEl);
    const magW = 128 * gooseDP2;
    const magH = 96 * gooseDP2;
    const magX = cursorX + 2 - magW / 2;
    const magY = cursorY + 12 - 80 * gooseDP2 - magH / 2;
    const magGlass = gooseGlass(
      "mag-glass",
      { x: magX, y: magY, w: magW, h: magH },
      {
        cornerRadius: magH / 2,
        refractionHeight: 8 * gooseDP2,
        refractionAmount: -24 * gooseDP2,
        blurRadius: 0,
        saturation: 1,
        surfaceColor: [0, 0, 0, 0],
        // Faithful to MagnifierContent.kt: drawBackdrop uses default highlight
        // (Highlight.Default, alpha=1) and default shadow (Shadow.Default).
        highlight: { ...gooseHL },
        outerShadow: { ...gooseShadow },
        // Faithful to InnerShadow(radius = 16f.dp) — defaults: offset=(0,radius),
        // color=Black(0.15), alpha=1.
        innerShadow: { radius: 16 * gooseDP2, alpha: 0.15, offsetX: 0, offsetY: 16 * gooseDP2 },
        depthEffect: true,
        chromaticAberration: true
      }
    );
    magGlass.isMagnifier = { zoom: 1.5, sampleOffsetY: 80 * gooseDP2 };
    elements.push(magGlass);
    const magDragHandler = {
      onDragStart: () => {
        magDragStart.x = state.magnifierX;
        magDragStart.y = state.magnifierY;
      },
      onDrag: (_pos, delta) => {
        setState({
          magnifierX: magDragStart.x + delta.x,
          magnifierY: magDragStart.y + delta.y
        });
      },
      onDragEnd: () => {
      }
    };
    interactions["mag-glass"] = magDragHandler;
    interactions["mag-cursor"] = magDragHandler;
    const contentHeight = cardH;
    const finalHeight = gooseCenter(elements, 0, contentHeight, H);
    return { elements, interactions, contentHeight: finalHeight };
  }

  // catalog/build-scroll-container.ts
  function buildScrollContainer(W, onBack, count, palette = gooseLight, scrollConfig, onLinkTap) {
    const elements = [];
    const interactions = {};
    const back = gooseBack(onBack, palette);
    elements.push(back.element);
    interactions[back.element.id] = back.interaction;
    const pad = 16 * gooseDP2;
    const spacing = 16 * gooseDP2;
    const cardW = W - 2 * pad;
    const cardH = 160 * gooseDP2;
    const items = scrollConfig && scrollConfig.length > 0 ? scrollConfig : null;
    const n = items ? items.length : count;
    let y = 80;
    for (let i = 0; i < n; i++) {
      elements.push(
        gooseGlass(
          `sc-card-${i}`,
          { x: pad, y, w: cardW, h: cardH },
          {
            cornerRadius: 32 * gooseDP2,
            refractionHeight: 16 * gooseDP2,
            refractionAmount: -32 * gooseDP2,
            blurRadius: 0,
            // Original has NO blur — only vibrancy() + lens()
            saturation: 1.5,
            surfaceColor: [0, 0, 0, 0],
            highlight: { ...gooseHL },
            outerShadow: null
          }
        )
      );
      const item = items == null ? void 0 : items[i];
      if (item) {
        elements.push(
          gooseText(
            `sc-title-${i}`,
            { x: pad + 16 * gooseDP2, y: y + 18 * gooseDP2, w: cardW - 32 * gooseDP2, h: 24 * gooseDP2 },
            item.title,
            {
              color: palette.homeContentColor,
              fontSizePx: 17,
              fontWeight: 600,
              align: "left",
              valign: "top",
              paddingPx: 0,
              scroll: true,
              halo: palette.homeTextHalo
            }
          )
        );
        if (item.subtitle) {
          elements.push(
            gooseText(
              `sc-sub-${i}`,
              { x: pad + 16 * gooseDP2, y: y + 18 * gooseDP2 + 28 * gooseDP2, w: cardW - 32 * gooseDP2, h: 20 * gooseDP2 },
              item.subtitle,
              {
                color: palette.homeSubtitleColor,
                fontSizePx: 14,
                fontWeight: 400,
                align: "left",
                valign: "top",
                paddingPx: 0,
                scroll: true,
                halo: palette.homeTextHalo
              }
            )
          );
        }
        if (item.link && item.link.text) {
          const linkColor = palette.homeTextHalo === "dark" ? [0.1, 0.4, 0.9, 1] : [0.36, 0.58, 1, 1];
          const linkEl = gooseText(
            `sc-link-${i}`,
            { x: pad + 16 * gooseDP2, y: y + cardH - 34 * gooseDP2, w: cardW - 32 * gooseDP2, h: 22 * gooseDP2 },
            item.link.text,
            {
              color: linkColor,
              fontSizePx: 15,
              fontWeight: 600,
              align: "left",
              valign: "center",
              paddingPx: 0,
              scroll: true,
              halo: palette.homeTextHalo
            }
          );
          linkEl.isInteractive = true;
          elements.push(linkEl);
          if (onLinkTap) {
            interactions[`sc-link-${i}`] = { onTap: () => {
              var _a;
              return onLinkTap(i, (_a = item.link) == null ? void 0 : _a.href);
            } };
          }
        }
      }
      y += cardH + spacing;
    }
    return { elements, interactions, contentHeight: y + 16 };
  }

  // catalog/build-rating.ts
  var STAR_PATH = "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z";
  var STARS = 5;
  var STAR_SIZE = 36 * gooseDP2;
  var STAR_GAP = 12 * gooseDP2;
  var STAR_VIEWPORT = 24;
  var TOP_PAD = 60 * gooseDP2;
  function buildRating(W, H, onBack, state, setState, rendererRef, palette) {
    const p = palette != null ? palette : goosePalette(true);
    const elements = [];
    const interactions = {};
    const totalW = STARS * STAR_SIZE + (STARS - 1) * STAR_GAP;
    const startX = (W - totalW) / 2;
    const starY = Math.max(TOP_PAD, (H - STAR_SIZE) / 2);
    const back = gooseBack(onBack, p);
    elements.push(back.element);
    if (back.interaction) interactions[back.element.id] = back.interaction;
    const filledColor = [255 / 255, 204 / 255, 0 / 255, 1];
    const emptyColor = [0.4, 0.4, 0.4, 0.5];
    for (let i = 0; i < STARS; i++) {
      const starId = `star-${i}`;
      const x = startX + i * (STAR_SIZE + STAR_GAP);
      const centerX = x + STAR_SIZE / 2;
      const centerY = starY + STAR_SIZE / 2;
      const isFilled = state.ratingValue > i;
      const iconColor = isFilled ? filledColor : emptyColor;
      const starEl = {
        id: starId,
        kind: "button",
        rect: { x, y: starY, w: STAR_SIZE, h: STAR_SIZE },
        cornerRadius: STAR_SIZE / 2,
        // circular
        refractionHeight: 6 * gooseDP2,
        refractionAmount: -12 * gooseDP2,
        depthEffect: false,
        chromaticAberration: false,
        blurRadius: 2 * gooseDP2,
        saturation: 1.5,
        brightness: 0,
        contrast: 1,
        tintColor: [0, 0, 0, 0],
        surfaceColor: isFilled ? [255 / 255, 204 / 255, 0 / 255, 0.15] : [0.5, 0.5, 0.5, 0.1],
        highlight: gooseHL,
        outerShadow: { radius: 8 * gooseDP2, alpha: 0.15, offsetX: 0, offsetY: 2 * gooseDP2, color: [0, 0, 0] },
        label: "",
        labelColor: [1, 1, 1, 1],
        showChevron: false,
        isInteractive: true,
        icon: {
          path: STAR_PATH,
          size: STAR_SIZE * 0.7,
          color: iconColor,
          viewport: STAR_VIEWPORT,
          layoutSize: STAR_SIZE
        }
      };
      elements.push(starEl);
      interactions[starId] = {
        onTap: () => {
          const newVal = state.ratingValue === i + 1 ? 0 : i + 1;
          setState({ ratingValue: newVal });
        },
        onDragStart: () => {
        },
        onDrag: () => {
        },
        onDragEnd: () => {
        }
      };
    }
    const contentHeight = Math.max(H, starY + STAR_SIZE + 20 * gooseDP2);
    return { elements, interactions, contentHeight };
  }

  // catalog/build-ring-progress.ts
  var RING_SIZE = 180 * gooseDP2;
  var RING_THICKNESS = 20 * gooseDP2;
  var INNER_R = RING_SIZE / 2 - RING_THICKNESS;
  var OUTER_R = RING_SIZE / 2;
  var TOP_PAD2 = 60 * gooseDP2;
  function ringArcPath(cx, cy, r1, r2, a1, a2) {
    const adj = (a) => a - Math.PI / 2;
    const s1 = adj(a1), s2 = adj(a2);
    const cos1 = Math.cos(s1), sin1 = Math.sin(s1);
    const cos2 = Math.cos(s2), sin2 = Math.sin(s2);
    const x1o = cx + r2 * cos1, y1o = cy + r2 * sin1;
    const x2o = cx + r2 * cos2, y2o = cy + r2 * sin2;
    const x1i = cx + r1 * cos2, y1i = cy + r1 * sin2;
    const x2i = cx + r1 * cos1, y2i = cy + r1 * sin1;
    const sweep = a2 - a1;
    const largeArc = sweep > Math.PI ? 1 : 0;
    return [
      `M ${x1o} ${y1o}`,
      `A ${r2} ${r2} 0 ${largeArc} 1 ${x2o} ${y2o}`,
      `L ${x1i} ${y1i}`,
      `A ${r1} ${r1} 0 ${largeArc} 0 ${x2i} ${y2i}`,
      "Z"
    ].join(" ");
  }
  function buildRingProgress(W, H, onBack, state, setState, rendererRef, palette) {
    const p = palette;
    const elements = [];
    const interactions = {};
    const cx = W / 2;
    const cy = Math.max(TOP_PAD2 + RING_SIZE / 2, H / 2);
    const rx = cx - RING_SIZE / 2;
    const ry = cy - RING_SIZE / 2;
    const back = gooseBack(onBack, p);
    elements.push(back.element);
    if (back.interaction) interactions[back.element.id] = back.interaction;
    elements.push({
      id: "ring-bg",
      kind: "glass-shape",
      rect: { x: rx, y: ry, w: RING_SIZE, h: RING_SIZE },
      cornerRadius: RING_SIZE / 2,
      refractionHeight: 8 * gooseDP2,
      refractionAmount: -16 * gooseDP2,
      depthEffect: false,
      chromaticAberration: false,
      blurRadius: 3 * gooseDP2,
      saturation: 1.5,
      brightness: 0,
      contrast: 1,
      tintColor: [0, 0, 0, 0],
      surfaceColor: p.buttonSurface,
      highlight: gooseHL,
      outerShadow: gooseShadow,
      label: "",
      labelColor: [1, 1, 1, 1],
      showChevron: false,
      isInteractive: false
    });
    const progress = state.ringProgressValue;
    const frac = progress / 100;
    const fillAngle = frac * Math.PI * 2;
    const accentColor = [0 / 255, 145 / 255, 255 / 255, 1];
    const trackColor = [0.3, 0.3, 0.3, 0.3];
    const trackPath = ringArcPath(cx - rx, cy - ry, INNER_R, OUTER_R, 0, Math.PI * 2);
    elements.push({
      id: "ring-track",
      kind: "button",
      rect: { x: rx, y: ry, w: RING_SIZE, h: RING_SIZE },
      cornerRadius: RING_SIZE / 2,
      refractionHeight: 0,
      refractionAmount: 0,
      depthEffect: false,
      chromaticAberration: false,
      blurRadius: 0,
      saturation: 1,
      brightness: 0,
      contrast: 1,
      tintColor: [0, 0, 0, 0],
      surfaceColor: [0, 0, 0, 0],
      highlight: null,
      outerShadow: null,
      label: "",
      labelColor: [1, 1, 1, 1],
      showChevron: false,
      isInteractive: false,
      icon: {
        path: trackPath,
        size: RING_SIZE,
        color: trackColor,
        viewport: RING_SIZE
      }
    });
    if (fillAngle > 1e-3) {
      const fillPath = ringArcPath(cx - rx, cy - ry, INNER_R, OUTER_R, 0, fillAngle);
      elements.push({
        id: "ring-fill",
        kind: "button",
        rect: { x: rx, y: ry, w: RING_SIZE, h: RING_SIZE },
        cornerRadius: RING_SIZE / 2,
        refractionHeight: 0,
        refractionAmount: 0,
        depthEffect: false,
        chromaticAberration: false,
        blurRadius: 0,
        saturation: 1,
        brightness: 0,
        contrast: 1,
        tintColor: [0, 0, 0, 0],
        surfaceColor: [0, 0, 0, 0],
        highlight: null,
        outerShadow: null,
        label: "",
        labelColor: [1, 1, 1, 1],
        showChevron: false,
        isInteractive: false,
        icon: {
          path: fillPath,
          size: RING_SIZE,
          color: accentColor,
          viewport: RING_SIZE
        }
      });
    }
    const labelText = `${Math.round(progress)}%`;
    const labelFontPx = 36 * gooseDP2;
    elements.push({
      id: "ring-label",
      kind: "text",
      rect: { x: rx, y: ry, w: RING_SIZE, h: RING_SIZE },
      cornerRadius: 0,
      refractionHeight: 0,
      refractionAmount: 0,
      depthEffect: false,
      chromaticAberration: false,
      blurRadius: 0,
      saturation: 1,
      brightness: 0,
      contrast: 1,
      tintColor: [0, 0, 0, 0],
      surfaceColor: [0, 0, 0, 0],
      highlight: null,
      outerShadow: null,
      label: "",
      labelColor: [1, 1, 1, 1],
      showChevron: false,
      isInteractive: false,
      text: {
        content: labelText,
        color: p.homeContentColor,
        fontSizePx: labelFontPx,
        fontWeight: 700,
        align: "center",
        valign: "center"
      }
    });
    const halfW = RING_SIZE / 2;
    elements.push({
      id: "ring-dec",
      kind: "button",
      rect: { x: rx, y: ry, w: halfW, h: RING_SIZE },
      cornerRadius: 0,
      refractionHeight: 0,
      refractionAmount: 0,
      depthEffect: false,
      chromaticAberration: false,
      blurRadius: 0,
      saturation: 1,
      brightness: 0,
      contrast: 1,
      tintColor: [0, 0, 0, 0],
      surfaceColor: [0, 0, 0, 0],
      highlight: null,
      outerShadow: null,
      label: "",
      labelColor: [1, 1, 1, 1],
      showChevron: false,
      isInteractive: true
    });
    interactions["ring-dec"] = {
      onTap: () => {
        setState({ ringProgressValue: Math.max(0, state.ringProgressValue - 10) });
      },
      onDragStart: () => {
      },
      onDrag: () => {
      },
      onDragEnd: () => {
      }
    };
    elements.push({
      id: "ring-inc",
      kind: "button",
      rect: { x: rx + halfW, y: ry, w: halfW, h: RING_SIZE },
      cornerRadius: 0,
      refractionHeight: 0,
      refractionAmount: 0,
      depthEffect: false,
      chromaticAberration: false,
      blurRadius: 0,
      saturation: 1,
      brightness: 0,
      contrast: 1,
      tintColor: [0, 0, 0, 0],
      surfaceColor: [0, 0, 0, 0],
      highlight: null,
      outerShadow: null,
      label: "",
      labelColor: [1, 1, 1, 1],
      showChevron: false,
      isInteractive: true
    });
    interactions["ring-inc"] = {
      onTap: () => {
        setState({ ringProgressValue: Math.min(100, state.ringProgressValue + 10) });
      },
      onDragStart: () => {
      },
      onDrag: () => {
      },
      onDragEnd: () => {
      }
    };
    const contentHeight = Math.max(H, ry + RING_SIZE + 20 * gooseDP2);
    return { elements, interactions, contentHeight };
  }

  // catalog/build-siri-wave.ts
  function buildSiriWave(W, H) {
    return { elements: [], interactions: {}, contentHeight: H };
  }
  var SIRI_VERTEX_SHADER = "attribute vec2 aPos; void main(){ gl_Position=vec4(aPos,0.0,1.0); }";
  var RENDER_SCALE = 0.75;
  var SiriWaveRunner = class {
    constructor(canvas) {
      __publicField(this, "_canvas");
      __publicField(this, "_gl", null);
      __publicField(this, "_prog", null);
      __publicField(this, "_buf", null);
      __publicField(this, "_U", {});
      __publicField(this, "_raf", 0);
      __publicField(this, "_start", 0);
      __publicField(this, "_variant", "wave");
      __publicField(this, "_speed", 1);
      __publicField(this, "_scale", 1);
      __publicField(this, "_dpr", 1);
      __publicField(this, "_w", 0);
      __publicField(this, "_h", 0);
      __publicField(this, "_killed", false);
      this._canvas = canvas;
      try {
        this._gl = canvas.getContext("webgl", {
          alpha: true,
          premultipliedAlpha: true,
          antialias: false,
          depth: false,
          stencil: false
        });
      } catch {
        this._gl = null;
      }
      if (!this._gl) {
        console.warn("[liquid-glass] siri-wave: WebGL not available");
        return;
      }
      this._compile();
    }
    get variant() {
      return this._variant;
    }
    set variant(v) {
      if (v === this._variant) return;
      this._variant = v === "orb" ? "orb" : "wave";
      this._compile();
    }
    get speed() {
      return this._speed;
    }
    set speed(v) {
      this._speed = v > 0 ? v : 1;
    }
    get scale() {
      return this._scale;
    }
    set scale(v) {
      this._scale = v > 0 ? v : 1;
    }
    get dpr() {
      return this._dpr;
    }
    set dpr(v) {
      this._dpr = v > 0 ? v : 1;
      this._applySize();
    }
    /** 设置 CSS 尺寸；canvas 像素尺寸 = css * dpr * RENDER_SCALE。 */
    resize(w, h) {
      this._w = w;
      this._h = h;
      this._applySize();
    }
    _applySize() {
      const gl = this._gl;
      if (!gl || !this._w || !this._h) return;
      const bw = Math.max(1, Math.round(this._w * this._dpr * RENDER_SCALE));
      const bh = Math.max(1, Math.round(this._h * this._dpr * RENDER_SCALE));
      if (this._canvas.width !== bw) this._canvas.width = bw;
      if (this._canvas.height !== bh) this._canvas.height = bh;
      gl.viewport(0, 0, bw, bh);
    }
    _compile() {
      const gl = this._gl;
      if (!gl) return;
      if (this._prog) {
        gl.deleteProgram(this._prog);
        this._prog = null;
      }
      const vs = this._shader(gl.VERTEX_SHADER, SIRI_VERTEX_SHADER);
      const fsSrc = this._variant === "orb" ? SIRI_ORB_FRAGMENT_SHADER : SIRI_WAVE_FRAGMENT_SHADER;
      const fs = this._shader(gl.FRAGMENT_SHADER, fsSrc);
      if (!vs || !fs) return;
      const prog = gl.createProgram();
      gl.attachShader(prog, vs);
      gl.attachShader(prog, fs);
      gl.linkProgram(prog);
      gl.deleteShader(vs);
      gl.deleteShader(fs);
      if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
        console.warn("[liquid-glass] siri-wave: link failed:", gl.getProgramInfoLog(prog));
        gl.deleteProgram(prog);
        return;
      }
      this._prog = prog;
      if (!this._buf) {
        this._buf = gl.createBuffer();
        gl.bindBuffer(gl.ARRAY_BUFFER, this._buf);
        gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
      }
      gl.useProgram(prog);
      const aPos = gl.getAttribLocation(prog, "aPos");
      gl.enableVertexAttribArray(aPos);
      gl.vertexAttribPointer(aPos, 2, gl.FLOAT, false, 0, 0);
      this._U.iResolution = gl.getUniformLocation(prog, "iResolution");
      this._U.iTime = gl.getUniformLocation(prog, "iTime");
      this._U.uSpeed = gl.getUniformLocation(prog, "uSpeed");
      this._U.uScale = gl.getUniformLocation(prog, "uScale");
    }
    _shader(type, src) {
      const gl = this._gl;
      const s = gl.createShader(type);
      if (!s) return null;
      gl.shaderSource(s, src);
      gl.compileShader(s);
      if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
        console.warn("[liquid-glass] siri-wave: compile error:", gl.getShaderInfoLog(s));
        gl.deleteShader(s);
        return null;
      }
      return s;
    }
    /** 启动动画循环（幂等）。 */
    start() {
      if (this._raf || !this._gl) return;
      if (!this._start) this._start = performance.now();
      const frame = () => {
        if (this._killed) return;
        const gl = this._gl;
        const prog = this._prog;
        if (gl && prog) {
          const t = (performance.now() - this._start) / 1e3;
          gl.useProgram(prog);
          if (this._U.iResolution) gl.uniform2f(this._U.iResolution, this._canvas.width, this._canvas.height);
          if (this._U.iTime) gl.uniform1f(this._U.iTime, t);
          if (this._U.uSpeed) gl.uniform1f(this._U.uSpeed, this._speed);
          if (this._U.uScale) gl.uniform1f(this._U.uScale, this._scale);
          gl.drawArrays(gl.TRIANGLES, 0, 3);
        }
        this._raf = requestAnimationFrame(frame);
      };
      this._raf = requestAnimationFrame(frame);
    }
    /** 暂停循环。 */
    stop() {
      if (this._raf) {
        cancelAnimationFrame(this._raf);
        this._raf = 0;
      }
    }
    kill() {
      this._killed = true;
      this.stop();
      const gl = this._gl;
      if (gl) {
        if (this._prog) {
          gl.deleteProgram(this._prog);
          this._prog = null;
        }
        if (this._buf) {
          gl.deleteBuffer(this._buf);
          this._buf = null;
        }
      }
    }
  };

  // catalog/index.ts
  function gooseBuild(dest, W, H, state, setState, onNavigate, onBack, rendererRef, isLightTheme = true, onToggleTheme, onButtonTap, tabsConfig, buttonsConfig, dialogConfig, onDialogTap, scrollConfig, onLinkTap) {
    const palette = goosePalette(isLightTheme);
    let result;
    switch (dest) {
      case 0 /* Buttons */:
        result = buildButtons(W, H, onBack, palette, onButtonTap, buttonsConfig);
        break;
      case 1 /* Toggle */:
        result = buildToggle(W, H, onBack, state, setState, rendererRef, palette);
        break;
      case 3 /* Slider */:
        result = buildSlider(W, H, onBack, state, setState, rendererRef, palette);
        break;
      case 4 /* SingleSlider */:
        result = buildSlider(W, H, onBack, state, setState, rendererRef, palette, true);
        break;
      case 2 /* SingleToggle */:
        result = buildToggle(W, H, onBack, state, setState, rendererRef, palette, true);
        break;
      case 6 /* SingleBottomTabs */:
        result = buildBottomTabs(W, H, onBack, state, setState, rendererRef, palette, tabsConfig, true);
        break;
      case 7 /* ToggleCard */:
        result = buildToggle(W, H, onBack, state, setState, rendererRef, palette, true, true);
        break;
      case 8 /* SliderCard */:
        result = buildSlider(W, H, onBack, state, setState, rendererRef, palette, true, true);
        break;
      case 9 /* BottomTabs2 */:
        result = buildBottomTabs(W, H, onBack, state, setState, rendererRef, palette, tabsConfig, true, true);
        break;
      case 5 /* BottomTabs */:
        result = buildBottomTabs(W, H, onBack, state, setState, rendererRef, palette, tabsConfig);
        break;
      case 10 /* Dialog */:
        result = buildDialog(W, H, onBack, state, palette, dialogConfig, onDialogTap);
        break;
      case 11 /* Magnifier */:
        result = buildMagnifier(W, H, onBack, state, setState, palette);
        break;
      case 12 /* ScrollContainer */:
        result = buildScrollContainer(W, onBack, 20, palette, scrollConfig, onLinkTap);
        break;
      case 13 /* LazyScrollContainer */:
        result = buildScrollContainer(W, onBack, 100, palette, scrollConfig, onLinkTap);
        break;
      case 14 /* Rating */:
        result = buildRating(W, H, onBack, state, setState, rendererRef, palette);
        break;
      case 15 /* RingProgress */:
        result = buildRingProgress(W, H, onBack, state, setState, rendererRef, palette);
        break;
      case 16 /* SiriWave */:
        result = buildSiriWave(W, H);
        break;
      default:
        result = buildButtons(W, H, onBack, palette, onButtonTap);
        break;
    }
    const hideOverlays = state.hideOverlayButtons;
    const backIdx = result.elements.findIndex((e) => e.id === "__back__");
    if (backIdx >= 0) {
      if (hideOverlays) {
        result.elements.splice(backIdx, 1);
        delete result.interactions["__back__"];
      } else {
        const [backEl] = result.elements.splice(backIdx, 1);
        result.elements.push(backEl);
      }
    }
    if (onToggleTheme && !hideOverlays) {
      const themeBtn = gooseThemeBtn(onToggleTheme, palette, isLightTheme, W, false);
      if (state.globalSeparableBlur) {
        themeBtn.element.useSeparableBlur = true;
      }
      result.elements.push(themeBtn.element);
      result.interactions[themeBtn.element.id] = themeBtn.interaction;
    }
    if (state.globalSeparableBlur) {
      for (const el of result.elements) {
        if ((el.kind === "button" || el.kind === "glass-shape") && !el.isSdfTexture && !el.isToggleKnob && !el.isBottomTabIndicator && !el.isMagnifier) {
          el.useSeparableBlur = true;
        }
      }
    }
    return result;
  }

  // src-bundle/liquid-glass-search.ts
  var FRAG = `#version 300 es
precision highp float;
uniform vec2  uRes;
uniform vec4  uBlob;   // cx, cy, halfX, halfY (px, y \u5411\u4E0B)
uniform float uEdge;   // \u9876\u90E8\u9ED1\u8FB9\u9AD8\u5EA6
uniform float uK;      // \u878D\u5408\u534A\u5F84
uniform float uHeight, uRefract, uHlAmt, uAb, uDpr, uCont;
uniform float uDark;   // \u6DB2\u6EF4\u6750\u8D28 1=\u8D34\u8FB9\u9ED1\u73BB\u7483 0=\u843D\u5730\u6D45\u78E8\u7802
uniform float uValid;
uniform sampler2D uTex;
uniform float uHasTex;
uniform vec2  uTexSize;
out vec4 outColor;

// ---------- \u80CC\u666F:\u58C1\u7EB8\u7EB9\u7406 (cover-fit),\u52A0\u8F7D\u5931\u8D25\u964D\u7EA7\u7A0B\u5E8F\u5316\u5730\u56FE ----------
// blurR>0 \u65F6\u6309\u82F9\u679C\u7684 blur\u2192mip \u6620\u5C04\u53D6 LOD: lod = log2(r<2 ? r/2+1 : r)
vec3 bgcol(vec2 p, float blurR){
  if (uHasTex > 0.5) {
    float s  = max(uRes.x / uTexSize.x, uRes.y / uTexSize.y);
    vec2  uv = (p - 0.5 * (uRes - uTexSize * s)) / (uTexSize * s);
    float lod = max(0.0, log2(blurR < 2.0 ? blurR * 0.5 + 1.0 : blurR));
    return textureLod(uTex, clamp(uv, vec2(0.002), vec2(0.998)), lod).rgb;
  }
  vec2 st = p / uRes.y;
  vec2 g  = fract(st * 6.0) - 0.5;
  float street = smoothstep(0.43, 0.455, max(abs(g.x), abs(g.y)));
  vec3 c = mix(vec3(0.90, 0.885, 0.85), vec3(0.985, 0.975, 0.96), street);
  c = mix(c, vec3(0.78, 0.88, 0.71), 1.0 - smoothstep(0.10, 0.30, length(st - vec2(0.33, 0.62))));
  c = mix(c, vec3(0.69, 0.83, 0.93), 1.0 - smoothstep(0.15, 0.42, length(st - vec2(1.55, 0.40))));
  for (int i = 0; i < 6; i++) {
    vec2 q = vec2(0.21 + 0.27 * float(i), fract(0.37 + 0.61 * float(i)) * 0.8 + 0.12);
    float d = length(st - q) * uRes.y;
    c = mix(c, vec3(0.95, 0.45, 0.25), 1.0 - smoothstep(7.0, 9.0, d));
    c = mix(c, vec3(1.0), 1.0 - smoothstep(2.5, 4.0, d));
  }
  c *= 1.0 - 0.10 * st.y / (uRes.x / uRes.y);   // \u8F7B\u5FAE\u7EB5\u5411\u6E10\u53D8
  return c;
}

// ---------- rounded-box SDF + \u89E3\u6790\u68AF\u5EA6 ----------
// supercircle_sdf \u7684\u5706\u89D2\u5206\u652F(cornerFlags=\u5706\u89D2);\u5706/\u80F6\u56CA/\u5706\u89D2\u77E9\u5F62\u540C\u4E00\u539F\u8BED
vec4 sdRoundBox(vec2 p, vec2 c, vec2 b, float r){
  vec2 lp = p - c;
  vec2 q  = abs(lp) - b + r;
  vec2 mq = max(q, vec2(0.0));
  float dOut = length(mq);
  float d = dOut + min(max(q.x, q.y), 0.0) - r;
  vec2 grad = (dOut > 1e-4) ? (mq / dOut) * sign(lp)
            : ((q.x > q.y) ? vec2(sign(lp.x), 0.0) : vec2(0.0, sign(lp.y)));
  return vec4(d, grad, 1.0);
}

// ---------- \u82F9\u679C sdf_union:\u68AF\u5EA6\u611F\u77E5 smooth min ----------
// QuartzCore ShaderUtils_::sdf_union \u9010\u884C\u7FFB\u8BD1
vec4 sdfUnion(vec4 a, vec4 b, float k){
  if (b.w == 0.0) b = vec4(10000.0, 0.0, 0.0, 0.0);     // half 0x70E2
  float kEff = k * clamp(0.5 - 0.5 * dot(a.yz, b.yz), 0.0, 1.0) + 1e-4;
  float h = clamp(0.5 + 0.5 * (b.x - a.x) / kEff, 0.0, 1.0);
  float d = mix(b.x, a.x, h) - kEff * h * (1.0 - h);    // IQ \u591A\u9879\u5F0F smin
  vec2  g = mix(b.yz, a.yz, h);                         // \u68AF\u5EA6\u540C\u6B65\u6DF7\u5408
  return vec4(d, normalize(g + vec2(1e-5)), 1.0);
}

void main(){
  vec2 p = vec2(gl_FragCoord.x, uRes.y - gl_FragCoord.y);  // y \u5411\u4E0B,\u5BF9\u9F50 UI \u5750\u6807

  vec4 bar  = vec4(p.y - uEdge, 0.0, 1.0, 1.0);            // \u9876\u90E8\u9ED1\u8FB9 = \u534A\u5E73\u9762
  vec4 blob = sdRoundBox(p, uBlob.xy, uBlob.zw, min(uBlob.z, uBlob.w));
  blob.w = uValid;
  vec4 s = sdfUnion(bar, blob, uK);
  float d = s.x;  vec2 g = s.yz;

  // \u80CC\u666F + \u5916\u4FA7\u8F6F\u9634\u5F71 (sdf_shadow)
  float sh = (d > 0.0) ? exp(-d / 30.0) * 0.22 : 0.0;
  vec3 col = bgcol(p, 0.0) * (1.0 - sh);

  // ---------- \u73BB\u7483 = \u6298\u5C04 + \u9AD8\u5149,\u65E0 face color ----------
  // \u516C\u5F0F\u540C QuartzCore sdf_glass_displacement/highlight,\u53C2\u6570\u53D6 z1han siri27 \u6807\u5B9A
  float wb = uValid * clamp(0.5 + 0.5 * (bar.x - blob.x) / 24.0, 0.0, 1.0);
  float darkAmt = mix(1.0, uDark, wb);

  // \u68AF\u5EA6\u5411"\u4E2D\u5FC3\u5F84\u5411"\u5FAE\u6DF7 8%,\u900F\u955C\u66F4\u5706\u6DA6(\u53EA\u5BF9\u6DB2\u6EF4,bar \u533A\u57DF wb\u22480)
  vec2 lp = p - uBlob.xy;
  vec2 radial = normalize(vec2(lp.x, uBlob.z * lp.y / max(uBlob.w, 0.001)) + 1e-5);
  vec2 gr = normalize(mix(g, radial, 0.08 * wb));

  // \u5706\u5F27\u5256\u9762 (curvature=1),\u6298\u5C04\u91CF\u4E3A\u8D1F \u2192 \u8FB9\u7F18\u628A\u5916\u4FA7\u5185\u5BB9"\u62C9\u8FDB\u6765"(\u653E\u5927\u955C\u611F)
  float t   = clamp(-d / uHeight, 0.0, 1.0);
  float mag = 1.0 - sqrt(max(1.0 - (1.0 - t) * (1.0 - t), 0.0));
  vec2  dsp = -uRefract * mag * gr;
  // \u8FB9\u7F18\u73AF:\u8FD1\u6E05\u6670\u91C7\u6837 + \u8272\u5DEE
  vec3 sharp;
  sharp.r = bgcol(p + dsp * (1.0 - uAb), 3.0).r;
  sharp.g = bgcol(p + dsp, 3.0).g;
  sharp.b = bgcol(p + dsp * (1.0 + uAb), 3.0).b;
  // \u5185\u90E8:8 \u62BD\u6837\u5706\u76D8 + \u4E2D\u7B49 mip \u8054\u5408\u6A21\u7CCA\u2014\u2014\u7EAF mip \u4E09\u7EBF\u6027\u6709\u5757\u611F,\u5706\u76D8\u628A\u5B83\u62B9\u5300
  float br = mix(14.0, 60.0, clamp(-d / (50.0 * uDpr), 0.0, 1.0)) * uDpr;
  vec3 soft = bgcol(p + dsp, br * 0.4) * 0.2;
  for (int i = 0; i < 8; i++) {
    float a = 0.7854 * float(i);
    soft += bgcol(p + dsp + vec2(cos(a), sin(a)) * br * 0.7, br * 0.4) * 0.1;
  }
  float deep = clamp(-d / (40.0 * uDpr), 0.0, 1.0);   // \u8D8A\u6DF1\u5165\u8D8A\u7528\u6A21\u7CCA
  vec3 refr = mix(sharp, soft, deep);

  // face \u538B\u6697 = \u4E58\u6CD5\u7CFB\u6570\u7684\u5782\u76F4\u6E10\u53D8(\u771F\u673A\u622A\u56FE\u9010\u50CF\u7D20\u5B9E\u6D4B\u6807\u5B9A):
  // \u9876 \xD70.03(\u8FD1\u7EAF\u9ED1) \u4E2D \xD70.19 \u5E95 \xD70.34,\u7EBF\u6027 m \u2248 0.20 + 0.40\xB7ny;
  // \u7528\u4E58\u6CD5\u4E0D\u6B8B\u7559\u5E95\u56FE\u5BF9\u6BD4(\u4E0D\u53D1\u82B1),\u7528\u6E10\u53D8\u8FD8\u539F"\u4E0A\u9ED1\u4E0B\u900F"
  float ny = (p.y - uBlob.y) / max(uBlob.w, 1.0);   // -1=\u9876 +1=\u5E95
  float m  = clamp(0.20 + 0.40 * ny, 0.0, 1.0);
  float crush = mix(1.0, m, uCont * wb);
  refr = refr * crush + vec3(0.008, 0.010, 0.014) * uCont * wb;

  // edge_bleed(IR \u89E3\u7801):\u4EAE\u80CC\u666F\u4ECE\u8FB9\u7F18\u6E17\u5165\u73BB\u7483\u5185\u4FA7\u2014\u2014\u5706\u5256\u9762\u4F4D\u79FB + mip \u6A21\u7CCA
  // + \u8D34\u8FB9\u8DDD\u79BB\u5E26 + \u4EAE\u5EA6\u56DB\u6B21\u65B9\u95E8\u63A7(\u80CC\u666F\u8D8A\u4EAE\u6E17\u8D8A\u591A,\u6697\u5904\u51E0\u4E4E\u4E0D\u6E17)
  float xb  = clamp(-d / (10.0 * uDpr), 0.0, 1.0);
  float dbl = 24.0 * uDpr * (1.0 - sqrt(xb * (2.0 - xb)));
  vec3 bleed = bgcol(p + gr * dbl, 22.0);
  float wbd  = clamp((d + 26.0 * uDpr) / (20.0 * uDpr), 0.0, 1.0);
  float blum = dot(bleed, vec3(0.2125, 0.7154, 0.0721));
  float bm   = pow(clamp(blum * 1.2, 0.0, 1.0), 2.0) * wbd;
  refr = mix(refr, bleed, bm * bm * 0.85);

  vec3 glass = mix(refr, refr * 0.10 + vec3(0.016), darkAmt); // \u843D\u5730=\u7EAF\u73BB\u7483,\u8D34\u8FB9=\u9ED1\u73BB\u7483

  // \u9AD8\u5149:\u7EC6\u5E26 2.2px,key 45\xB0 + fill 225\xB0 \u5BF9\u89D2\u53CC\u5149,\u9510\u63A9\u7801 cut 0.52,\u538B\u7F29 norm 8
  float qd  = -d;
  float hw  = 2.2 * uDpr;
  float aaq = max(fwidth(qd), 1e-3);
  float band = (1.0 - clamp(qd / hw, 0.0, 1.0))
             * clamp(qd / aaq + 0.5, 0.0, 1.0)
             * clamp((hw - qd) / aaq + 0.5, 0.0, 1.0);
  vec2 kdir = vec2(0.7071, 0.7071);
  float key  = band * clamp((dot(kdir, gr) - 0.52) / 0.48, 0.0, 1.0);
  float fill = band * clamp((dot(-kdir, gr) - 0.52) / 0.48, 0.0, 1.0);
  key  = key  / (1.0 + (1.0 - key)  * 8.0);
  fill = fill / (1.0 + (1.0 - fill) * 8.0);
  glass += (key + fill) * uHlAmt * mix(1.0, 0.4, darkAmt);

  float aaw = max(fwidth(d), 1e-3);
  col = mix(col, glass, smoothstep(aaw, -aaw, d));
  outColor = vec4(col, 1.0);
}`;
  var VERT = `#version 300 es
void main(){ vec2 v = vec2((gl_VertexID<<1)&2, gl_VertexID&2);
  gl_Position = vec4(v*2.0-1.0, 0.0, 1.0); }`;
  var Spring = class {
    constructor(v) {
      __publicField(this, "x");
      __publicField(this, "v");
      __publicField(this, "t");
      __publicField(this, "om", 11);
      __publicField(this, "ze", 0.72);
      this.x = v;
      this.v = 0;
      this.t = v;
    }
    set(om, ze) {
      this.om = om;
      this.ze = ze;
      return this;
    }
    step(dt) {
      this.v += (this.om * this.om * (this.t - this.x) - 2 * this.ze * this.om * this.v) * dt;
      this.x += this.v * dt;
      return this.x;
    }
  };
  var EDGE = 26;
  var R = 34;
  var DEFAULT_PARAMS = { k: 64, height: 18, refract: 14, hl: 1.5, ab: 0.12, cont: 0.5, om: 11, ze: 0.72 };
  var MetaballSearchRunner = class {
    constructor(canvas) {
      __publicField(this, "_canvas");
      __publicField(this, "_gl", null);
      __publicField(this, "_prog", null);
      __publicField(this, "_U", {});
      __publicField(this, "_tex", null);
      __publicField(this, "_hasTex", 0);
      __publicField(this, "_texSize", [1, 1]);
      __publicField(this, "_dpr", 1);
      __publicField(this, "_w", 0);
      __publicField(this, "_h", 0);
      __publicField(this, "_raf", 0);
      __publicField(this, "_last", 0);
      __publicField(this, "_killed", false);
      __publicField(this, "_wallpaperUrl", "");
      __publicField(this, "state", "IDLE");
      __publicField(this, "pointer", { x: 0, y: 0 });
      __publicField(this, "_sp", {
        cx: new Spring(0),
        cy: new Spring(0),
        bx: new Spring(0),
        by: new Spring(0),
        k: new Spring(64),
        dark: new Spring(1),
        cont: new Spring(0)
      });
      __publicField(this, "_P", { ...DEFAULT_PARAMS });
      __publicField(this, "onCapsuleChange", null);
      this._canvas = canvas;
      try {
        this._gl = canvas.getContext("webgl2", { antialias: false });
      } catch {
        this._gl = null;
      }
      if (!this._gl) {
        console.warn("[liquid-glass-search] WebGL2 not available");
        return;
      }
      this._compile();
      this._initTexture();
    }
    get dpr() {
      return this._dpr;
    }
    set dpr(v) {
      this._dpr = v > 0 ? v : 1;
      this._applySize();
    }
    get wallpaper() {
      return this._wallpaperUrl;
    }
    set wallpaper(url) {
      if (url === this._wallpaperUrl) return;
      this._wallpaperUrl = url;
      if (url) this._loadWallpaper(url);
    }
    resize(w, h) {
      this._w = w;
      this._h = h;
      this._applySize();
    }
    _applySize() {
      const gl = this._gl;
      if (!gl || !this._w || !this._h) return;
      const bw = Math.max(1, Math.round(this._w * this._dpr));
      const bh = Math.max(1, Math.round(this._h * this._dpr));
      if (this._canvas.width !== bw) this._canvas.width = bw;
      if (this._canvas.height !== bh) this._canvas.height = bh;
      gl.viewport(0, 0, bw, bh);
    }
    _compile() {
      const gl = this._gl;
      if (!gl) return;
      const vs = this._shader(gl.VERTEX_SHADER, VERT);
      const fs = this._shader(gl.FRAGMENT_SHADER, FRAG);
      if (!vs || !fs) return;
      const prog = gl.createProgram();
      gl.attachShader(prog, vs);
      gl.attachShader(prog, fs);
      gl.linkProgram(prog);
      gl.deleteShader(vs);
      gl.deleteShader(fs);
      if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
        console.warn("[liquid-glass-search] link failed:", gl.getProgramInfoLog(prog));
        gl.deleteProgram(prog);
        return;
      }
      this._prog = prog;
      gl.useProgram(prog);
      const names = ["uRes", "uBlob", "uEdge", "uK", "uHeight", "uRefract", "uHlAmt", "uAb", "uDpr", "uDark", "uCont", "uValid", "uTex", "uHasTex", "uTexSize"];
      this._U = {};
      for (const n of names) this._U[n] = gl.getUniformLocation(prog, n);
      gl.uniform1i(this._U.uTex, 0);
    }
    _shader(type, src) {
      const gl = this._gl;
      const s = gl.createShader(type);
      if (!s) return null;
      gl.shaderSource(s, src);
      gl.compileShader(s);
      if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
        console.warn("[liquid-glass-search] compile error:", gl.getShaderInfoLog(s));
        gl.deleteShader(s);
        return null;
      }
      return s;
    }
    _initTexture() {
      const gl = this._gl;
      if (!gl) return;
      this._tex = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, this._tex);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    }
    _loadWallpaper(url) {
      const gl = this._gl;
      if (!gl || !this._tex) return;
      const img = new Image();
      img.crossOrigin = "anonymous";
      img.onload = () => {
        try {
          gl.bindTexture(gl.TEXTURE_2D, this._tex);
          gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
          gl.generateMipmap(gl.TEXTURE_2D);
          this._hasTex = 1;
          this._texSize = [img.width, img.height];
        } catch {
          this._hasTex = 0;
        }
      };
      img.onerror = () => {
        this._hasTex = 0;
      };
      img.src = url;
    }
    // ---- 手势（独立通道，不走主组件元素手势系统） ----
    onDown(x, y) {
      if (this.state === "CAPSULE") {
        const c = this.capsuleRect();
        const inCapsule = Math.abs(x - c.x) <= c.w / 2 + 8 && Math.abs(y - c.y) <= c.h / 2 + 8;
        if (!inCapsule) this.retract();
        return;
      }
      this.state = "DRAG";
      this.pointer = { x, y };
      const sp = this._sp;
      sp.cx.x = x;
      sp.cx.v = 0;
      sp.cy.x = EDGE * 0.5;
      sp.cy.v = 0;
      sp.bx.x = sp.by.x = 2;
      sp.cx.set(18, 0.95);
      sp.cy.set(18, 0.95);
      sp.bx.set(this._P.om, this._P.ze);
      sp.by.set(this._P.om, this._P.ze);
      sp.dark.t = 1;
      sp.k.t = this._P.k;
      sp.cont.t = 0;
    }
    onMove(x, y) {
      if (this.state !== "DRAG") return;
      this.pointer = { x, y };
    }
    onUp() {
      if (this.state !== "DRAG") return;
      if (this.pointer.y > this._h * 0.32) {
        this.state = "CAPSULE";
        const c = this.capsuleRect();
        const sp = this._sp;
        for (const s of [sp.cx, sp.cy, sp.bx, sp.by]) s.set(this._P.om, this._P.ze);
        sp.cx.t = c.x;
        sp.cy.t = c.y;
        sp.bx.t = c.w / 2;
        sp.by.t = c.h / 2;
        sp.dark.t = 0;
        sp.k.t = 10;
        sp.cont.t = 1;
      } else {
        this.retract();
      }
    }
    capsuleRect() {
      const sw = this._w;
      const w = sw < 600 ? sw - 44 : Math.min(560, sw * 0.62);
      const h = sw < 600 ? 50 : 56;
      return { x: sw / 2, y: EDGE + (sw < 600 ? 70 : 96), w, h };
    }
    retract() {
      var _a;
      if (this.state === "RETRACT") return;
      this.state = "RETRACT";
      const sp = this._sp;
      for (const s of [sp.cx, sp.cy, sp.bx, sp.by]) s.set(this._P.om, this._P.ze);
      sp.cy.t = EDGE * 0.3;
      sp.bx.t = sp.by.t = 1;
      sp.dark.t = 1;
      sp.k.t = this._P.k;
      sp.cont.t = 0;
      (_a = this.onCapsuleChange) == null ? void 0 : _a.call(this, false, { x: 0, y: 0, w: 0, h: 0 });
    }
    get capsuleActive() {
      return this.state === "CAPSULE";
    }
    /** 调整渲染/弹簧参数（对应原 demo 调参面板）。 */
    setParams(patch) {
      for (const key of Object.keys(patch)) {
        const v = patch[key];
        if (typeof v === "number" && isFinite(v)) this._P[key] = v;
      }
      if (patch.k != null && this.state !== "CAPSULE") this._sp.k.t = patch.k;
    }
    start() {
      if (this._raf || !this._gl) return;
      this._last = performance.now();
      const frame = (now) => {
        if (this._killed) return;
        this._tick(now);
        this._raf = requestAnimationFrame(frame);
      };
      this._raf = requestAnimationFrame(frame);
    }
    stop() {
      if (this._raf) {
        cancelAnimationFrame(this._raf);
        this._raf = 0;
      }
    }
    kill() {
      this._killed = true;
      this.stop();
      const gl = this._gl;
      if (gl) {
        if (this._prog) {
          gl.deleteProgram(this._prog);
          this._prog = null;
        }
        if (this._tex) {
          gl.deleteTexture(this._tex);
          this._tex = null;
        }
      }
    }
    _tick(now) {
      var _a;
      const gl = this._gl;
      const prog = this._prog;
      const cv = this._canvas;
      if (!gl || !prog) return;
      const dt = Math.min((now - this._last) / 1e3, 1 / 30);
      this._last = now;
      const sp = this._sp;
      const P = this._P;
      if (this.state === "DRAG") {
        sp.cx.t = this.pointer.x;
        sp.cy.t = Math.max(this.pointer.y, EDGE * 0.5);
        sp.bx.t = R + Math.min(26, Math.abs(sp.cx.v) * 0.045);
        sp.by.t = R + Math.min(26, Math.abs(sp.cy.v) * 0.045);
      }
      for (const key in sp) sp[key].step(dt);
      if (this.state === "RETRACT" && sp.by.x < 2.5) this.state = "IDLE";
      const dpr = this._dpr;
      gl.useProgram(prog);
      gl.uniform2f(this._U.uRes, cv.width, cv.height);
      gl.uniform4f(this._U.uBlob, sp.cx.x * dpr, sp.cy.x * dpr, Math.max(sp.bx.x, 0.5) * dpr, Math.max(sp.by.x, 0.5) * dpr);
      gl.uniform1f(this._U.uEdge, EDGE * dpr);
      gl.uniform1f(this._U.uK, sp.k.x * dpr);
      gl.uniform1f(this._U.uHeight, P.height * dpr);
      gl.uniform1f(this._U.uRefract, P.refract * dpr);
      gl.uniform1f(this._U.uHlAmt, P.hl);
      gl.uniform1f(this._U.uAb, P.ab);
      gl.uniform1f(this._U.uDpr, dpr);
      gl.uniform1f(this._U.uDark, Math.max(0, Math.min(1, sp.dark.x)));
      gl.uniform1f(this._U.uCont, Math.max(0, Math.min(1, sp.cont.x)) * P.cont);
      gl.uniform1f(this._U.uValid, this.state === "IDLE" ? 0 : 1);
      gl.uniform1f(this._U.uHasTex, this._hasTex);
      gl.uniform2f(this._U.uTexSize, this._texSize[0], this._texSize[1]);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      if (this.state === "CAPSULE") {
        const r = { x: sp.cx.x - sp.bx.x, y: sp.cy.x - sp.by.x, w: sp.bx.x * 2, h: sp.by.x * 2 };
        (_a = this.onCapsuleChange) == null ? void 0 : _a.call(this, true, r);
      }
    }
  };
  var SEARCH_ICON = '<svg viewBox="0 0 20 20" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><circle cx="8.5" cy="8.5" r="5.5"/><path d="M12.8 12.8 17 17"/></svg>';
  var MIC_ICON = '<svg viewBox="0 0 20 20" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><rect x="7.2" y="2.2" width="5.6" height="9.6" rx="2.8"/><path d="M4.6 9.6a5.4 5.4 0 0 0 10.8 0"/><path d="M10 15v2.6"/></svg>';
  var LiquidGlassSearch = class extends HTMLElement {
    constructor() {
      super();
      __publicField(this, "_canvas");
      __publicField(this, "_hint");
      __publicField(this, "_searchEl");
      __publicField(this, "_input");
      __publicField(this, "_caret");
      __publicField(this, "_runner", null);
      __publicField(this, "_ro", null);
      __publicField(this, "_w", 0);
      __publicField(this, "_h", 0);
      __publicField(this, "_disposed", false);
      __publicField(this, "_mctx", null);
      __publicField(this, "_caretReset", 0);
      __publicField(this, "_capsuleWas", false);
      __publicField(this, "_inputFocused", false);
      __publicField(this, "_onDown", (e) => {
        var _a, _b;
        const t = e.target;
        if (t && t.closest && t.closest("#search")) return;
        if ((_a = this._runner) == null ? void 0 : _a.capsuleActive) {
          this._runner.onDown(this._localPos(e).x, this._localPos(e).y);
          const p = this._localPos(e);
          const c = this._runner.capsuleRect();
          if (Math.abs(p.x - c.x) <= c.w / 2 && Math.abs(p.y - c.y) <= c.h / 2) {
            try {
              this._input.focus();
            } catch {
            }
          }
          return;
        }
        (_b = this._runner) == null ? void 0 : _b.onDown(this._localPos(e).x, this._localPos(e).y);
        this._hint.style.opacity = "0";
        try {
          this._canvas.setPointerCapture(e.pointerId);
        } catch {
        }
      });
      __publicField(this, "_onMove", (e) => {
        var _a;
        const p = this._localPos(e);
        (_a = this._runner) == null ? void 0 : _a.onMove(p.x, p.y);
      });
      __publicField(this, "_onUp", (e) => {
        var _a;
        (_a = this._runner) == null ? void 0 : _a.onUp();
        if (this._canvas.hasPointerCapture(e.pointerId)) {
          try {
            this._canvas.releasePointerCapture(e.pointerId);
          } catch {
          }
        }
      });
      __publicField(this, "_onWinKey", (e) => {
        var _a;
        if (e.key === "Escape" && ((_a = this._runner) == null ? void 0 : _a.capsuleActive)) this._runner.retract();
      });
      __publicField(this, "_onInput", () => {
        this._caret.style.animation = "none";
        clearTimeout(this._caretReset);
        this._caretReset = window.setTimeout(() => {
          this._caret.style.animation = "";
        }, 80);
        this._updateCaret(true);
        this.dispatchEvent(
          new CustomEvent("lg-search", { detail: { text: this._input.value }, bubbles: true })
        );
      });
      __publicField(this, "_onKey", (e) => {
        if (e.key === "Enter") {
          this.dispatchEvent(
            new CustomEvent("lg-search-submit", { detail: { text: this._input.value }, bubbles: true })
          );
        }
      });
      __publicField(this, "_onInputFocus", () => {
        this._inputFocused = true;
        this._updateCaret(true);
      });
      __publicField(this, "_onInputBlur", () => {
        this._inputFocused = false;
        this._caret.style.display = "none";
      });
      const shadow = this.attachShadow({ mode: "open" });
      const style = document.createElement("style");
      style.textContent = ':host{position:relative;display:block;overflow:hidden;background:#000;}canvas{display:block;width:100%;height:100%;touch-action:none;cursor:grab;}#hint{position:absolute;left:50%;top:42%;transform:translate(-50%,-50%);color:rgba(255,255,255,.5);font-size:14px;font-weight:600;pointer-events:none;transition:opacity .4s;font-family:-apple-system,system-ui,sans-serif;text-shadow:0 1px 6px rgba(0,0,0,.4);}#search{position:absolute;display:flex;align-items:center;opacity:0;pointer-events:none;transition:opacity .25s;box-sizing:border-box;}#search input{flex:1;min-width:0;height:100%;border:none;outline:none;background:transparent;font:400 17px/1 -apple-system,system-ui,"SF Pro Text",sans-serif;letter-spacing:.1px;color:#fff;caret-color:transparent;padding:0 10px;text-shadow:0 1px 5px rgba(0,0,0,.35);box-sizing:border-box;}#search input::placeholder{color:rgba(235,235,245,.45)}#search svg{flex:none;color:#fff;opacity:.72;filter:drop-shadow(0 1px 3px rgba(0,0,0,.3));}#search .ic{margin-left:20px}#search .mic{margin-right:20px}#caret{position:absolute;width:2px;height:21px;top:50%;transform:translateY(-50%);border-radius:1px;background:#fff;display:none;pointer-events:none;box-shadow:0 0 5px rgba(255,255,255,.95),0 0 14px rgba(255,255,255,.5);animation:lgc-blink 1.12s step-end infinite;}@keyframes lgc-blink{0%,60%{opacity:1}61%,100%{opacity:0}}@media (max-width:600px){#search input{font-size:16px;padding:0 8px}#search .ic{margin-left:14px}#search .mic{margin-right:14px}#caret{height:19px}}';
      this._canvas = document.createElement("canvas");
      this._hint = document.createElement("div");
      this._hint.id = "hint";
      this._searchEl = document.createElement("div");
      this._searchEl.id = "search";
      this._input = document.createElement("input");
      this._caret = document.createElement("span");
      this._caret.id = "caret";
      this._searchEl.innerHTML = SEARCH_ICON.replace("<svg", '<svg class="ic"');
      this._input.setAttribute("spellcheck", "false");
      this._input.setAttribute("autocomplete", "off");
      this._searchEl.appendChild(this._input);
      this._searchEl.appendChild(this._caret);
      this._searchEl.insertAdjacentHTML("beforeend", MIC_ICON.replace("<svg", '<svg class="ic mic"'));
      shadow.appendChild(style);
      shadow.appendChild(this._canvas);
      shadow.appendChild(this._hint);
      shadow.appendChild(this._searchEl);
    }
    static get observedAttributes() {
      return ["wallpaper", "dpr", "placeholder", "hint"];
    }
    connectedCallback() {
      if (this._runner) return;
      const runner = new MetaballSearchRunner(this._canvas);
      this._runner = runner;
      const deviceDpr = window.devicePixelRatio || 1;
      const dprAttr = this.getAttribute("dpr");
      runner.dpr = dprAttr ? Math.max(0.5, Math.min(deviceDpr, parseFloat(dprAttr) || deviceDpr, 2)) : Math.min(deviceDpr, 2);
      const wp = this.getAttribute("wallpaper");
      if (wp) runner.wallpaper = wp;
      const ph = this.getAttribute("placeholder");
      if (ph != null) this._input.placeholder = ph;
      else this._input.placeholder = "Search or Ask";
      const hint = this.getAttribute("hint");
      this._hint.textContent = hint != null ? hint : "\u4ECE\u9876\u90E8\u9ED1\u8FB9\u5F80\u4E0B\u62D6\u62FD";
      runner.onCapsuleChange = (active, rect) => {
        if (active) {
          this._searchEl.style.left = rect.x + "px";
          this._searchEl.style.top = rect.y + "px";
          this._searchEl.style.width = rect.w + "px";
          this._searchEl.style.height = rect.h + "px";
          this._searchEl.style.opacity = "1";
          this._searchEl.style.pointerEvents = "auto";
          if (!this._capsuleWas) {
            this._capsuleWas = true;
            try {
              this._input.focus();
            } catch {
            }
          }
          this._updateCaret(true);
        } else {
          this._capsuleWas = false;
          this._searchEl.style.opacity = "0";
          this._searchEl.style.pointerEvents = "none";
          this._caret.style.display = "none";
        }
      };
      this._canvas.addEventListener("pointerdown", this._onDown);
      this._canvas.addEventListener("pointermove", this._onMove);
      this._canvas.addEventListener("pointerup", this._onUp);
      this._canvas.addEventListener("pointercancel", this._onUp);
      this._input.addEventListener("input", this._onInput);
      this._input.addEventListener("keydown", this._onKey);
      this._input.addEventListener("focus", this._onInputFocus);
      this._input.addEventListener("blur", this._onInputBlur);
      window.addEventListener("keydown", this._onWinKey);
      const ro = new ResizeObserver(() => this._resize());
      ro.observe(this);
      this._ro = ro;
      this._resize();
    }
    disconnectedCallback() {
      this._disposed = true;
      if (this._ro) this._ro.disconnect();
      this._canvas.removeEventListener("pointerdown", this._onDown);
      this._canvas.removeEventListener("pointermove", this._onMove);
      this._canvas.removeEventListener("pointerup", this._onUp);
      this._canvas.removeEventListener("pointercancel", this._onUp);
      this._input.removeEventListener("input", this._onInput);
      this._input.removeEventListener("keydown", this._onKey);
      this._input.removeEventListener("focus", this._onInputFocus);
      this._input.removeEventListener("blur", this._onInputBlur);
      window.removeEventListener("keydown", this._onWinKey);
      if (this._runner) {
        this._runner.kill();
        this._runner = null;
      }
    }
    attributeChangedCallback(name, _old, val) {
      if (!this._runner) return;
      if (name === "wallpaper") {
        if (val) this._runner.wallpaper = val;
      } else if (name === "dpr") {
        const dv = parseFloat(val || "0");
        const deviceDpr = window.devicePixelRatio || 1;
        this._runner.dpr = dv > 0 ? Math.max(0.5, Math.min(deviceDpr, dv, 2)) : Math.min(deviceDpr, 2);
        this._resize();
      } else if (name === "placeholder") {
        this._input.placeholder = val != null ? val : "Search or Ask";
      } else if (name === "hint") {
        this._hint.textContent = val != null ? val : "\u4ECE\u9876\u90E8\u9ED1\u8FB9\u5F80\u4E0B\u62D6\u62FD";
        this._hint.style.opacity = val === "" ? "0" : "";
      }
    }
    _resize() {
      var _a, _b;
      const r = this.getBoundingClientRect();
      if (!r.width || !r.height) return;
      this._w = r.width;
      this._h = r.height;
      this._canvas.style.width = r.width + "px";
      this._canvas.style.height = r.height + "px";
      (_a = this._runner) == null ? void 0 : _a.resize(r.width, r.height);
      (_b = this._runner) == null ? void 0 : _b.start();
    }
    _localPos(e) {
      const rect = this._canvas.getBoundingClientRect();
      return { x: e.clientX - rect.left, y: e.clientY - rect.top };
    }
    /** 调整渲染/弹簧参数：`el.setParams({ k: 80, om: 14 })`，只传想改的项。 */
    setParams(patch) {
      var _a;
      (_a = this._runner) == null ? void 0 : _a.setParams(patch);
    }
    /** 读取当前搜索框文字。 */
    getValue() {
      return this._input.value;
    }
    /** 写入搜索框文字（并触发 lg-search 事件）。 */
    setValue(text) {
      this._input.value = text;
      this._updateCaret(true);
      this.dispatchEvent(
        new CustomEvent("lg-search", { detail: { text }, bubbles: true })
      );
    }
    // 发光光标：原生 caret 做不出辉光，量文本宽度自己画
    _updateCaret(visible) {
      var _a;
      if (!visible || !this._inputFocused) {
        this._caret.style.display = "none";
        return;
      }
      if (!this._mctx) {
        const c = document.createElement("canvas");
        this._mctx = c.getContext("2d");
      }
      if (!this._mctx) return;
      const cs = getComputedStyle(this._input);
      this._mctx.font = cs.font;
      const padL = parseFloat(cs.paddingLeft) || 0;
      const upto = this._input.value.slice(0, (_a = this._input.selectionStart) != null ? _a : this._input.value.length);
      const x = this._input.offsetLeft + padL + this._mctx.measureText(upto).width;
      this._caret.style.left = Math.min(x, this._input.offsetLeft + this._input.offsetWidth - 12) + "px";
      this._caret.style.display = "block";
    }
  };
  customElements.define("liquid-glass-search", LiquidGlassSearch);

  // src-bundle/liquid-glass.ts
  var MODE_MAP = {
    buttons: 0 /* Buttons */,
    toggle: 1 /* Toggle */,
    slider: 3 /* Slider */,
    "single-slider": 4 /* SingleSlider */,
    "single-toggle": 2 /* SingleToggle */,
    "bottom-tabs": 5 /* BottomTabs */,
    "single-bottom-tabs": 6 /* SingleBottomTabs */,
    "toggle-card": 7 /* ToggleCard */,
    "slider-card": 8 /* SliderCard */,
    "bottom-tabs-2": 9 /* BottomTabs2 */,
    dialog: 10 /* Dialog */,
    magnifier: 11 /* Magnifier */,
    "scroll-container": 12 /* ScrollContainer */,
    "lazy-scroll-container": 13 /* LazyScrollContainer */,
    rating: 14 /* Rating */,
    "rating-card": 14 /* Rating */,
    "ring-progress": 15 /* RingProgress */,
    "ring-progress-card": 15 /* RingProgress */,
    "siri-wave": 16 /* SiriWave */
  };
  var ENUM_TO_MODE = Object.fromEntries(
    Object.entries(MODE_MAP).map(([k, v]) => [v, k])
  );
  function genGradient(isLight, w, h) {
    const c = document.createElement("canvas");
    c.width = Math.max(2, w | 0);
    c.height = Math.max(2, h | 0);
    const ctx = c.getContext("2d");
    const g = ctx.createLinearGradient(0, 0, c.width, c.height);
    if (isLight) {
      g.addColorStop(0, "#3f6fd6");
      g.addColorStop(0.45, "#7b5cff");
      g.addColorStop(1, "#c44ad6");
    } else {
      g.addColorStop(0, "#0a1230");
      g.addColorStop(0.5, "#141a3a");
      g.addColorStop(1, "#2a1240");
    }
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, c.width, c.height);
    return c.toDataURL();
  }
  var LiquidGlass = class extends HTMLElement {
    // 进入 dialog 前的视图，点弹窗按钮后回退用
    constructor() {
      super();
      __publicField(this, "_canvas");
      __publicField(this, "_renderer", null);
      __publicField(this, "_siri", null);
      __publicField(this, "_state");
      __publicField(this, "_elements", []);
      __publicField(this, "_interactions", {});
      __publicField(this, "_gestures", /* @__PURE__ */ new Map());
      __publicField(this, "_prevPinch", null);
      __publicField(this, "_w", 0);
      __publicField(this, "_h", 0);
      __publicField(this, "_dark", false);
      __publicField(this, "_ro", null);
      __publicField(this, "_disposed", false);
      __publicField(this, "_gradientLoaded", false);
      __publicField(this, "_onWheel");
      __publicField(this, "_dbg", null);
      __publicField(this, "_tabsConfig", null);
      __publicField(this, "_buttonsConfig", null);
      __publicField(this, "_dialogConfig", null);
      __publicField(this, "_scrollConfig", null);
      __publicField(this, "_prevMode", "bottom-tabs");
      __publicField(this, "_onNavigate", (d) => {
        var _a;
        const name = (_a = ENUM_TO_MODE[d]) != null ? _a : "dest:" + d;
        this.dispatchEvent(new CustomEvent("lg-navigate", { detail: { dest: d, name }, bubbles: true }));
      });
      __publicField(this, "_onBack", () => {
        this.dispatchEvent(new CustomEvent("lg-back", { bubbles: true }));
      });
      __publicField(this, "_onButtonTap", (id) => {
        this.dispatchEvent(new CustomEvent("lg-buttontap", { detail: { id }, bubbles: true }));
      });
      __publicField(this, "_onDialogTap", (action) => {
        this.dispatchEvent(new CustomEvent("lg-dialogtap", { detail: { action }, bubbles: true }));
        this.setAttribute("mode", this._prevMode || "bottom-tabs");
        this._onBack();
      });
      __publicField(this, "_onLinkTap", (index, href) => {
        this.dispatchEvent(new CustomEvent("lg-linktap", { detail: { index, href }, bubbles: true }));
      });
      __publicField(this, "_onToggleTheme", () => {
        var _a;
        this._dark = !this._dark;
        (_a = this._renderer) == null ? void 0 : _a.gooseBG(null);
        this._gradientLoaded = false;
        this._maybeLoadGradient();
        this._emitState();
        this._rebuild();
      });
      __publicField(this, "_onDown", (e) => {
        var _a, _b, _c, _d, _e, _f;
        const renderer = this._renderer;
        if (!renderer) return;
        const { x, y } = this._localPos(e);
        const scrollY = renderer.gooseGetScrollY();
        const els = this._elements;
        const interactions = this._interactions;
        let hit = null;
        for (let i = els.length - 1; i >= 0; i--) {
          const el = els[i];
          const hr = (_a = el.hitRect) != null ? _a : el.rect;
          const visibleHY = el.scroll ? hr.y - scrollY : hr.y;
          let testX = x;
          let testY = y;
          const elRot = el.elementRotation;
          if (elRot && Math.abs(elRot) > 1e-3) {
            const cx = hr.x + hr.w * 0.5;
            const cy = (el.scroll ? hr.y - scrollY : hr.y) + hr.h * 0.5;
            const dx = x - cx;
            const dy = y - cy;
            const cos = Math.cos(-elRot);
            const sin = Math.sin(-elRot);
            testX = cx + dx * cos - dy * sin;
            testY = cy + dx * sin + dy * cos;
          }
          if (testX >= hr.x && testX <= hr.x + hr.w && testY >= visibleHY && testY <= visibleHY + hr.h) {
            const hasInteraction = !!(interactions == null ? void 0 : interactions[el.id]);
            if (!hasInteraction && !el.isInteractive) continue;
            hit = el;
            break;
          }
        }
        if (hit) {
          const hitId = hit.id;
          const existingEntry = Array.from(this._gestures.entries()).find(
            ([, g]) => g.pressedId === hitId && g.mode !== "transform"
          );
          if (existingEntry && ((_b = interactions == null ? void 0 : interactions[hitId]) == null ? void 0 : _b.onTransform)) {
            const [partnerPid, partnerGs] = existingEntry;
            if (hit.isInteractive && (hit.kind === "button" || hit.kind === "text")) {
              renderer.goosePress(hitId, false);
            }
            const p1 = { x: partnerGs.x, y: partnerGs.y };
            const p2 = { x, y };
            const dx = p2.x - p1.x;
            const dy = p2.y - p1.y;
            this._prevPinch = {
              dist: Math.hypot(dx, dy),
              angle: Math.atan2(dy, dx),
              cx: (p1.x + p2.x) / 2,
              cy: (p1.y + p2.y) / 2
            };
            partnerGs.mode = "transform";
            partnerGs.transformPartner = e.pointerId;
            this._gestures.set(e.pointerId, {
              pressedId: hitId,
              startX: x,
              startY: y,
              startClientY: e.clientY,
              startScrollY: renderer.gooseGetScrollY(),
              dragStarted: false,
              mode: "transform",
              hasDrag: !!((_c = interactions == null ? void 0 : interactions[hitId]) == null ? void 0 : _c.onDrag),
              velocitySamples: [{ t: performance.now(), x: e.clientX, y: e.clientY }],
              x,
              y,
              transformPartner: partnerPid
            });
            try {
              this._canvas.setPointerCapture(e.pointerId);
            } catch {
            }
            return;
          }
        }
        const hasDrag = !!(hit && ((_d = interactions == null ? void 0 : interactions[hit.id]) == null ? void 0 : _d.onDrag));
        if (!this._dbg) this._dbg = {};
        if (!this._dbg.downOnce) {
          this._dbg.downOnce = true;
          console.log("[lg-debug] down hitId=", hit == null ? void 0 : hit.id, "hasDrag=", hasDrag);
        }
        this._gestures.set(e.pointerId, {
          pressedId: hit ? hit.id : null,
          startX: x,
          startY: y,
          startClientY: e.clientY,
          startScrollY: renderer.gooseGetScrollY(),
          dragStarted: false,
          mode: "pending",
          hasDrag,
          velocitySamples: [{ t: performance.now(), x: e.clientX, y: e.clientY }],
          x,
          y,
          transformPartner: null
        });
        if (hit && hit.isInteractive) {
          const hasDrag0 = !!((_e = interactions == null ? void 0 : interactions[hit.id]) == null ? void 0 : _e.onDrag);
          if (hit.kind === "button" || hit.kind === "text" || hit.kind === "glass-shape" && !hasDrag0 && !!((_f = interactions == null ? void 0 : interactions[hit.id]) == null ? void 0 : _f.onTap)) {
            renderer.goosePress(hit.id, true, { x, y });
          }
        }
        try {
          this._canvas.setPointerCapture(e.pointerId);
        } catch {
        }
        window.addEventListener("pointermove", this._onMove);
        window.addEventListener("pointerup", this._onUp);
        window.addEventListener("pointercancel", this._onUp);
      });
      __publicField(this, "_onMove", (e) => {
        var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k, _l, _m, _n, _o, _p;
        const renderer = this._renderer;
        if (!renderer) return;
        const { x, y } = this._localPos(e);
        const gs = this._gestures.get(e.pointerId);
        if (!gs) return;
        gs.x = x;
        gs.y = y;
        if (gs.mode === "transform") {
          const partnerPid = gs.transformPartner;
          if (partnerPid == null) return;
          const partner = this._gestures.get(partnerPid);
          if (!partner) return;
          const id = gs.pressedId;
          if (!id) return;
          const dx2 = partner.x - gs.x;
          const dy2 = partner.y - gs.y;
          const dist = Math.hypot(dx2, dy2);
          const angle = Math.atan2(dy2, dx2);
          const cx = (gs.x + partner.x) / 2;
          const cy = (gs.y + partner.y) / 2;
          const prev = this._prevPinch;
          if (prev && prev.dist > 1e-3) {
            const gestureZoom = dist / prev.dist;
            let gestureRotate = angle - prev.angle;
            if (gestureRotate > Math.PI) gestureRotate -= 2 * Math.PI;
            if (gestureRotate < -Math.PI) gestureRotate += 2 * Math.PI;
            const pan = { x: cx - prev.cx, y: cy - prev.cy };
            (_c = (_b = (_a = this._interactions) == null ? void 0 : _a[id]) == null ? void 0 : _b.onTransform) == null ? void 0 : _c.call(_b, pan, gestureZoom, gestureRotate);
          }
          this._prevPinch = { dist, angle, cx, cy };
          return;
        }
        gs.velocitySamples.push({ t: performance.now(), x: e.clientX, y: e.clientY });
        if (gs.velocitySamples.length > 20) gs.velocitySamples.shift();
        const dx = x - gs.startX;
        const dy = y - gs.startY;
        const absDx = Math.abs(dx);
        const absDy = Math.abs(dy);
        if (gs.mode === "pending") {
          const MOVE_THRESHOLD = 4;
          const id0 = gs.pressedId;
          if (id0) {
            const el0 = this._elements.find((b) => b.id === id0);
            if ((el0 == null ? void 0 : el0.kind) === "button" && el0.isInteractive) {
              renderer.gooseDragPos(id0, { x, y });
            }
          }
          if (absDx < MOVE_THRESHOLD && absDy < MOVE_THRESHOLD) return;
          const id = gs.pressedId;
          const hitEl = id ? this._elements.find((b) => b.id === id) : null;
          const isButton = (hitEl == null ? void 0 : hitEl.kind) === "button" && (hitEl == null ? void 0 : hitEl.isInteractive);
          const hasDrag = !!hitEl && !!((_e = (_d = this._interactions) == null ? void 0 : _d[id]) == null ? void 0 : _e.onDrag);
          if (hasDrag && !((_f = this._dbg) == null ? void 0 : _f.dragOnce)) {
            this._dbg = this._dbg || {};
            this._dbg.dragOnce = true;
            console.log("[lg-debug] pending\u2192drag id=", id, "onDragType=", typeof ((_h = (_g = this._interactions) == null ? void 0 : _g[id]) == null ? void 0 : _h.onDrag));
          }
          const isShapeButton = !hasDrag && (hitEl == null ? void 0 : hitEl.kind) === "glass-shape" && (hitEl == null ? void 0 : hitEl.isInteractive) && !!((_j = (_i = this._interactions) == null ? void 0 : _i[id]) == null ? void 0 : _j.onTap);
          if (hasDrag) {
            gs.mode = "drag";
            gs.dragStarted = true;
            (_m = (_l = (_k = this._interactions) == null ? void 0 : _k[id]) == null ? void 0 : _l.onDragStart) == null ? void 0 : _m.call(_l, { x, y });
          } else if (isButton || isShapeButton) {
            renderer.gooseDragPos(id, { x, y });
          } else {
            const SCROLL_TAKEOVER_THRESHOLD = 14;
            const verticalDominant = absDy > absDx + 2 && absDy >= SCROLL_TAKEOVER_THRESHOLD;
            if (verticalDominant) {
              const otherScrolling = Array.from(this._gestures.entries()).some(
                ([pid, g]) => pid !== e.pointerId && g.mode === "scroll"
              );
              if (otherScrolling) return;
              if (id) {
                const el = this._elements.find((b) => b.id === id);
                if ((el == null ? void 0 : el.isInteractive) && el.kind === "text") renderer.goosePress(id, false);
              }
              gs.mode = "scroll";
              const scrollDelta = e.clientY - gs.startClientY;
              renderer.gooseScrollY(gs.startScrollY - scrollDelta);
              return;
            }
          }
        }
        if (gs.mode === "scroll") {
          const scrollDelta = e.clientY - gs.startClientY;
          renderer.gooseScrollY(gs.startScrollY - scrollDelta);
          return;
        }
        if (gs.mode === "drag") {
          const id = gs.pressedId;
          if (!id) return;
          const el = this._elements.find((b) => b.id === id);
          if (!el) return;
          if (el.kind === "button" && el.isInteractive) renderer.gooseDragPos(id, { x, y });
          (_p = (_o = (_n = this._interactions) == null ? void 0 : _n[id]) == null ? void 0 : _o.onDrag) == null ? void 0 : _p.call(_o, { x, y }, { x: dx, y: dy });
        }
      });
      __publicField(this, "_onUp", (e) => {
        var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k, _l, _m;
        const renderer = this._renderer;
        const gs = this._gestures.get(e.pointerId);
        if (!gs) {
          window.removeEventListener("pointermove", this._onMove);
          window.removeEventListener("pointerup", this._onUp);
          window.removeEventListener("pointercancel", this._onUp);
          if (this._canvas.hasPointerCapture(e.pointerId)) {
            try {
              this._canvas.releasePointerCapture(e.pointerId);
            } catch {
            }
          }
          return;
        }
        const mode = gs.mode;
        const id = gs.pressedId;
        if (mode === "transform") {
          const partnerPid = gs.transformPartner;
          this._gestures.delete(e.pointerId);
          this._prevPinch = null;
          if (partnerPid != null) {
            const partner = this._gestures.get(partnerPid);
            if (partner) {
              partner.transformPartner = null;
              partner.mode = "drag";
              partner.dragStarted = true;
              partner.startX = partner.x;
              partner.startY = partner.y;
              if (partner.pressedId) {
                (_c = (_b = (_a = this._interactions) == null ? void 0 : _a[partner.pressedId]) == null ? void 0 : _b.onDragStart) == null ? void 0 : _c.call(_b, { x: partner.x, y: partner.y });
              }
            }
          }
          if (this._canvas.hasPointerCapture(e.pointerId)) {
            try {
              this._canvas.releasePointerCapture(e.pointerId);
            } catch {
            }
          }
          return;
        }
        if (renderer) {
          if (id) {
            const el = this._elements.find((b) => b.id === id);
            if (el == null ? void 0 : el.isInteractive) {
              const hasDrag1 = !!((_e = (_d = this._interactions) == null ? void 0 : _d[id]) == null ? void 0 : _e.onDrag);
              if (el.kind === "button" || el.kind === "text" || el.kind === "glass-shape" && !hasDrag1 && !!((_g = (_f = this._interactions) == null ? void 0 : _f[id]) == null ? void 0 : _g.onTap)) {
                renderer.goosePress(id, false);
              }
            }
          }
          if (mode === "scroll") {
            const v = this._computeReleaseVelocity(gs.velocitySamples);
            if (Math.abs(v) > 50) renderer.gooseScrollV(v);
          }
          if (id) {
            const { x, y } = this._localPos(e);
            if (gs.dragStarted) {
              const { x: vx, y: vy } = this._computeReleaseVelocity2D(gs.velocitySamples);
              (_j = (_i = (_h = this._interactions) == null ? void 0 : _h[id]) == null ? void 0 : _i.onDragEnd) == null ? void 0 : _j.call(_i, { x, y }, { x: vx, y: vy });
            } else if (mode === "pending" || mode === "drag") {
              (_m = (_l = (_k = this._interactions) == null ? void 0 : _k[id]) == null ? void 0 : _l.onTap) == null ? void 0 : _m.call(_l, { x, y });
            }
          }
        }
        window.removeEventListener("pointermove", this._onMove);
        window.removeEventListener("pointerup", this._onUp);
        window.removeEventListener("pointercancel", this._onUp);
        this._gestures.delete(e.pointerId);
        if (this._canvas.hasPointerCapture(e.pointerId)) {
          try {
            this._canvas.releasePointerCapture(e.pointerId);
          } catch {
          }
        }
      });
      this._state = { ...gooseDefState };
      const shadow = this.attachShadow({ mode: "open" });
      const style = document.createElement("style");
      style.textContent = ":host{position:relative;display:block;overflow:hidden;}canvas{display:block;width:100%;height:100%;touch-action:none;cursor:pointer;}";
      this._canvas = document.createElement("canvas");
      shadow.appendChild(style);
      shadow.appendChild(this._canvas);
    }
    static get observedAttributes() {
      return ["mode", "dark", "wallpaper", "dpr", "corner-style", "blur-tap-cap", "overlay-buttons", "theme-button", "tabs", "buttons", "dialog", "scroll", "variant", "speed", "scale"];
    }
    _isSiri() {
      return this._mode() === 16 /* SiriWave */;
    }
    connectedCallback() {
      if (this._renderer || this._siri) return;
      this._dark = this.hasAttribute("dark");
      const overlayButtons = this.hasAttribute("overlay-buttons");
      this._showThemeButton = this.hasAttribute("theme-button");
      this._state = { ...gooseDefState, hideOverlayButtons: !overlayButtons };
      if (this._isSiri()) {
        this._initSiri();
        return;
      }
      const renderer = new LiquidGlassRenderer(this._canvas);
      this._renderer = renderer;
      const dprAttr = this.getAttribute("dpr");
      if (dprAttr != null) {
        const dv = parseFloat(dprAttr);
        const deviceDpr = window.devicePixelRatio || 1;
        renderer.dpr = dv > 0 ? Math.max(0.5, Math.min(deviceDpr, dv)) : deviceDpr;
      }
      const tapCap = this.getAttribute("blur-tap-cap");
      if (tapCap != null) renderer.blurTapCap = Math.max(1, Math.min(33, parseInt(tapCap) || 17));
      const corner = this.getAttribute("corner-style");
      if (corner != null) renderer.cornerStyle = parseFloat(corner);
      const mode = this._mode();
      renderer.gooseBG(null);
      const wp = this.getAttribute("wallpaper");
      if (wp && wp !== "gradient") {
        renderer.gooseLoadWP(wp).catch((e) => console.warn("[liquid-glass] wallpaper load failed:", e));
      }
      const ro = new ResizeObserver(() => this._resize());
      ro.observe(this);
      this._ro = ro;
      this._onWheel = (e) => {
        e.preventDefault();
        const delta = e.deltaY !== 0 ? e.deltaY : e.deltaX;
        renderer.gooseScrollY(renderer.gooseGetScrollY() + delta);
      };
      this._canvas.addEventListener("wheel", this._onWheel, { passive: false });
      this._canvas.addEventListener("pointerdown", this._onDown);
      this._resize();
      this._emitState();
    }
    disconnectedCallback() {
      this._disposed = true;
      if (this._ro) this._ro.disconnect();
      if (this._canvas) {
        this._canvas.removeEventListener("wheel", this._onWheel);
        this._canvas.removeEventListener("pointerdown", this._onDown);
        this._canvas.removeEventListener("pointermove", this._onMove);
        this._canvas.removeEventListener("pointerup", this._onUp);
        this._canvas.removeEventListener("pointerleave", this._onUp);
        this._canvas.removeEventListener("pointercancel", this._onUp);
      }
      if (this._siri) {
        this._siri.kill();
        this._siri = null;
      }
      if (this._renderer) {
        this._renderer.gooseKill();
        this._renderer = null;
      }
    }
    attributeChangedCallback(name, _old, val) {
      if (!this._renderer && !this._siri) return;
      if (name === "mode") {
        const oldM = (_old || "bottom-tabs").toLowerCase();
        if (oldM !== "dialog") this._prevMode = oldM;
        const isSiri = this._isSiri();
        if (isSiri) {
          if (this._renderer) {
            this._renderer.gooseKill();
            this._renderer = null;
            this._canvas.removeEventListener("wheel", this._onWheel);
            this._canvas.removeEventListener("pointerdown", this._onDown);
          }
          this._initSiri();
        } else if (this._siri) {
          this._siri.kill();
          this._siri = null;
          const renderer = new LiquidGlassRenderer(this._canvas);
          this._renderer = renderer;
          const dprAttr = this.getAttribute("dpr");
          if (dprAttr != null) {
            const dv = parseFloat(dprAttr);
            const deviceDpr = window.devicePixelRatio || 1;
            renderer.dpr = dv > 0 ? Math.max(0.5, Math.min(deviceDpr, dv)) : deviceDpr;
          }
          const wp = this.getAttribute("wallpaper");
          if (wp && wp !== "gradient") renderer.gooseLoadWP(wp).catch(() => {
          });
          else this._maybeLoadGradient();
          this._canvas.addEventListener("wheel", this._onWheel, { passive: false });
          this._canvas.addEventListener("pointerdown", this._onDown);
          renderer.gooseScrollY(0);
          this._rebuild();
        } else {
          this._renderer.gooseScrollY(0);
          this._rebuild();
        }
        return;
      }
      if (this._isSiri()) {
        if (name === "variant") {
          this._siri.variant = val === "orb" ? "orb" : "wave";
        } else if (name === "speed") {
          const sp = parseFloat(val || "");
          if (!isNaN(sp) && sp > 0) this._siri.speed = sp;
        } else if (name === "scale") {
          const sc = parseFloat(val || "");
          if (!isNaN(sc) && sc > 0) this._siri.scale = sc;
        } else if (name === "dpr") {
          const dv = parseFloat(val || "0");
          const deviceDpr = window.devicePixelRatio || 1;
          this._siri.dpr = dv > 0 ? Math.max(0.5, Math.min(deviceDpr, dv)) : deviceDpr;
          this._resize();
        }
        return;
      }
      const r = this._renderer;
      if (name === "dark") {
        this._dark = this.hasAttribute("dark");
        r.gooseBG(null);
        this._gradientLoaded = false;
        this._maybeLoadGradient();
        this._rebuild();
        this._emitState();
      } else if (name === "wallpaper") {
        this._gradientLoaded = false;
        if (val && val !== "gradient") r.gooseLoadWP(val).catch(() => {
        });
        else this._maybeLoadGradient();
      } else if (name === "dpr") {
        const dv = parseFloat(val || "0");
        const deviceDpr = window.devicePixelRatio || 1;
        r.dpr = dv > 0 ? Math.max(0.5, Math.min(deviceDpr, dv)) : deviceDpr;
        this._resize();
      } else if (name === "corner-style") {
        if (val != null) {
          r.cornerStyle = parseFloat(val);
          r.gooseReqRender();
        }
      } else if (name === "blur-tap-cap") {
        if (val != null) {
          r.blurTapCap = Math.max(1, Math.min(33, parseInt(val) || 17));
          r.gooseReqRender();
        }
      } else if (name === "overlay-buttons") {
        this._state = { ...this._state, hideOverlayButtons: !this.hasAttribute("overlay-buttons") };
        this._rebuild();
        this._emitState();
      } else if (name === "theme-button") {
        this._showThemeButton = this.hasAttribute("theme-button");
        this._rebuild();
        this._emitState();
      } else if (name === "tabs") {
        if (val) {
          try {
            this._tabsConfig = JSON.parse(val);
          } catch {
            this._tabsConfig = null;
          }
        } else {
          this._tabsConfig = null;
        }
        this._rebuild();
      } else if (name === "buttons") {
        if (val) {
          try {
            this._buttonsConfig = JSON.parse(val);
          } catch {
            this._buttonsConfig = null;
          }
        } else {
          this._buttonsConfig = null;
        }
        this._rebuild();
      } else if (name === "dialog") {
        if (val) {
          try {
            this._dialogConfig = JSON.parse(val);
          } catch {
            this._dialogConfig = null;
          }
        } else {
          this._dialogConfig = null;
        }
        this._rebuild();
      } else if (name === "scroll") {
        if (val) {
          try {
            this._scrollConfig = JSON.parse(val);
          } catch {
            this._scrollConfig = null;
          }
        } else {
          this._scrollConfig = null;
        }
        this._rebuild();
      }
    }
    _mode() {
      var _a;
      const m = (this.getAttribute("mode") || "bottom-tabs").toLowerCase();
      return (_a = MODE_MAP[m]) != null ? _a : 5 /* BottomTabs */;
    }
    /** 初始化 siri-wave 独立渲染路径（纯 shader 动画，不走玻璃管线）。 */
    _initSiri() {
      if (this._siri) return;
      const runner = new SiriWaveRunner(this._canvas);
      this._siri = runner;
      const deviceDpr = window.devicePixelRatio || 1;
      const dprAttr = this.getAttribute("dpr");
      runner.dpr = dprAttr ? Math.max(0.5, Math.min(deviceDpr, parseFloat(dprAttr) || deviceDpr)) : deviceDpr;
      const v = this.getAttribute("variant");
      if (v === "orb") runner.variant = "orb";
      const sp = parseFloat(this.getAttribute("speed") || "");
      if (!isNaN(sp) && sp > 0) runner.speed = sp;
      const sc = parseFloat(this.getAttribute("scale") || "");
      if (!isNaN(sc) && sc > 0) runner.scale = sc;
      const ro = new ResizeObserver(() => this._resize());
      ro.observe(this);
      this._ro = ro;
      this._resize();
    }
    _maybeLoadGradient() {
      var _a, _b;
      const wp = this.getAttribute("wallpaper");
      if (this._w <= 0 || this._gradientLoaded) return;
      if (wp === "gradient") {
        this._gradientLoaded = true;
        (_a = this._renderer) == null ? void 0 : _a.gooseLoadWP(genGradient(!this._dark, this._w, this._h)).catch(() => {
        });
      } else if (!wp) {
        this._gradientLoaded = true;
        const tc = document.createElement("canvas");
        tc.width = Math.max(2, this._w | 0);
        tc.height = Math.max(2, this._h | 0);
        (_b = this._renderer) == null ? void 0 : _b.gooseLoadWP(tc.toDataURL()).catch(() => {
        });
      }
    }
    _resize() {
      var _a;
      const r = this.getBoundingClientRect();
      if (!r.width || !r.height) return;
      this._w = r.width;
      this._h = r.height;
      this._canvas.style.width = r.width + "px";
      this._canvas.style.height = r.height + "px";
      if (this._siri) {
        this._siri.resize(r.width, r.height);
        this._siri.start();
        return;
      }
      (_a = this._renderer) == null ? void 0 : _a.gooseResize(r.width, r.height);
      this._maybeLoadGradient();
      this._rebuild();
    }
    _emitState() {
      this.dispatchEvent(
        new CustomEvent("lg-statechange", {
          detail: { ...this._state, dark: this._dark },
          bubbles: true
        })
      );
    }
    _setState(patch) {
      const next = typeof patch === "function" ? patch(this._state) : patch;
      this._state = { ...this._state, ...next };
      if (this._siri) {
        if (next.variant != null) this._siri.variant = next.variant;
        if (next.speed != null && next.speed > 0) this._siri.speed = next.speed;
        if (next.scale != null && next.scale > 0) this._siri.scale = next.scale;
      }
      this._emitState();
      this._rebuild();
    }
    /** 直接设置组件状态（供外部调用，如 setState({ ratingValue: 3 })）。 */
    setState(patch) {
      this._setState(patch);
    }
    /** 配置底部标签栏内容：[[{icon,label}...],[{icon,label}...]]（两组，可只传一组）。icon 为 SVG path 字符串。 */
    setTabs(config) {
      this._tabsConfig = config;
      this._rebuild();
    }
    /** 配置按钮组。注意：对外接口是 setButtons，不是 gooseButtons。 */
    setButtons(config) {
      this._buttonsConfig = config;
      this._rebuild();
    }
    /** 配置按钮组：[{id?, label?, style?}]，每个按钮独立（文字 + 样式）。style: 'transparent'|'surface'|'blue'|'orange' 或自定义 rgba 色。 */
    gooseButtons(config) {
      this._buttonsConfig = config;
      this._rebuild();
    }
    /** 配置弹窗内容：{ title?, body?, cancelText?, okayText? }。不传则回退原版默认文案。 */
    setDialog(config) {
      this._dialogConfig = config;
      this._rebuild();
    }
    /** 配置滚动容器列表项：[{ title, subtitle? }]。不传则回退原版空卡片。 */
    setScroll(config) {
      this._scrollConfig = config;
      this._rebuild();
    }
    _rebuild() {
      if (this._disposed) return;
      if (this._siri) {
        this._siri.start();
        return;
      }
      if (!this._renderer) return;
      const W = this._w;
      const H = this._h;
      if (!W || !H) return;
      const dest = this._mode();
      const result = gooseBuild(
        dest,
        W,
        H,
        this._state,
        (p) => this._setState(p),
        this._onNavigate,
        this._onBack,
        this._renderer ? { current: this._renderer } : void 0,
        !this._dark,
        this.hasAttribute("overlay-buttons") || this.hasAttribute("theme-button") ? this._onToggleTheme : void 0,
        this._onButtonTap,
        this._tabsConfig,
        this._buttonsConfig,
        this._dialogConfig,
        this._onDialogTap,
        this._scrollConfig,
        this._onLinkTap
      );
      this._elements = result.elements;
      this._interactions = result.interactions;
      this._renderer.gooseElements(this._elements);
      this._renderer.gooseContentH(result.contentHeight);
      this._renderer.gooseReqRender();
      this._syncTargets();
    }
    // Faithful port of context.tsx's toggleTargets/tabTargets useEffect sync.
    _syncTargets() {
      const r = this._renderer;
      if (!r) return;
      const sync = (id, fn) => {
        if (gooseDragGroups.has(id)) return;
        try {
          fn();
        } catch {
        }
      };
      sync("toggle1", () => r.gooseTglTarget("toggle1", this._state.toggleOn ? 1 : 0));
      sync("toggle2", () => r.gooseTglTarget("toggle2", this._state.toggleOn ? 1 : 0));
      sync("tabs3", () => {
        var _a, _b, _c;
        return r.gooseTabSel("tabs3", this._state.selectedTab, (_c = (_b = (_a = this._tabsConfig) == null ? void 0 : _a[0]) == null ? void 0 : _b.length) != null ? _c : 3);
      });
      sync("tabs4", () => {
        var _a, _b, _c;
        return r.gooseTabSel("tabs4", this._state.selectedTab2, (_c = (_b = (_a = this._tabsConfig) == null ? void 0 : _a[1]) == null ? void 0 : _b.length) != null ? _c : 4);
      });
      sync("slider1", () => r.gooseTglTarget("slider1", this._state.sliderValue / 100));
      sync("slider2", () => r.gooseTglTarget("slider2", this._state.sliderValue / 100));
    }
    // ---- pointer gesture system (faithful port of context.tsx) ----
    _localPos(e) {
      const rect = this._canvas.getBoundingClientRect();
      return { x: e.clientX - rect.left, y: e.clientY - rect.top };
    }
    _computeReleaseVelocity(samples) {
      if (samples.length < 2) return 0;
      const now = samples[samples.length - 1].t;
      const cutoff = now - 100;
      let oldest = samples[samples.length - 1];
      for (let i = samples.length - 1; i >= 0; i--) {
        if (samples[i].t < cutoff) break;
        oldest = samples[i];
      }
      const dt = (now - oldest.t) / 1e3;
      if (dt < 1e-3) return 0;
      const dy = samples[samples.length - 1].y - oldest.y;
      return -dy / dt;
    }
    _computeReleaseVelocity2D(samples) {
      if (samples.length < 2) return { x: 0, y: 0 };
      const last = samples[samples.length - 1];
      const now = last.t;
      const cutoff = now - 100;
      let oldest = last;
      for (let i = samples.length - 1; i >= 0; i--) {
        if (samples[i].t < cutoff) break;
        oldest = samples[i];
      }
      const dt = (now - oldest.t) / 1e3;
      if (dt < 1e-3) return { x: 0, y: 0 };
      return { x: (last.x - oldest.x) / dt, y: (last.y - oldest.y) / dt };
    }
  };
  customElements.define("liquid-glass", LiquidGlass);
})();
