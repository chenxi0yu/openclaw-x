package ai.openclaw.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRuntimeShaderSupported

@Composable
internal fun rememberClawBackdrop(): LayerBackdrop = rememberLayerBackdrop()

/**
 * Progressive blur over the header: the transcript underneath is blurred with a
 * radius that decays to zero at the bar's foot. Nothing else.
 *
 * This is deliberately NOT a glass slab. An earlier version painted a surface
 * tint on top of the blur (and before that a separate scrim layer), which made
 * the bar read as a pane of frosted glass sitting over the content and left a
 * brightness step at its bottom edge. The user's target is the iOS-style
 * treatment where the content itself softens toward the top: blur is a neutral
 * operation on the captured layer, so the canvas colour stays exactly where it
 * was and there is nothing to dissolve at the edge.
 *
 * Pipeline: 32-sample golden-angle dithered blur plus a separate denoise pass.
 * The dither is the noise source, so the denoise is not optional; a bare
 * multi-sample over live content reads as grain. The buffer padding equals the
 * max radius so edge samples reach real content outside the bar instead of
 * clamping into a smear.
 */
@Composable
internal fun Modifier.clawBackdropTopBar(backdrop: Backdrop?): Modifier {
  if (backdrop == null) return this
  return drawBackdrop(
    backdrop = backdrop,
    shape = { RectangleShape },
    // The library's default glass chrome is the whole reason a "white film with
    // a hard bottom edge" kept surviving every shader fix: `highlight` paints a
    // bright rim along the bar's foot and `shadow` drops a ~100px band under it.
    // A progressive blur must be chrome-free — it is a treatment of the content,
    // not a slab sitting on top of it.
    highlight = null,
    shadow = null,
    innerShadow = null,
    effects = {
      if (isRuntimeShaderSupported()) {
        val maxRadius = 12.dp.toPx()
        padding = maxRadius
        val pad = padding
        val contentW = size.width
        val contentH = size.height
        val bufferW = contentW + 2f * pad
        val bufferH = contentH + 2f * pad
        runtimeShaderEffect("ClawProgressiveTopBar", PROGRESSIVE_TOP_BAR_SHADER, "content") {
          setFloatUniform("contentOrigin", pad, pad)
          setFloatUniform("contentSize", contentW, contentH)
          setFloatUniform("bufferSize", bufferW, bufferH)
          setFloatUniform("maxRadius", maxRadius)
        }
        runtimeShaderEffect("ClawTopBarDenoise", PROGRESSIVE_DENOISE_SHADER, "content") {
          setFloatUniform("contentOrigin", pad, pad)
          setFloatUniform("contentSize", contentW, contentH)
          setFloatUniform("bufferSize", bufferW, bufferH)
          setFloatUniform("maxRadius", maxRadius)
        }
      } else {
        vibrancy()
        blur(12.dp.toPx())
      }
    },
  )
}

/**
 * Liquid glass for the floating header controls (the pill and the disc).
 *
 * Callers pass the backdrop only when it exists; the modifier falls back to the
 * plain surface fill so a missing source never paints a transparent hole.
 */
internal fun Modifier.clawLiquidButton(
  backdrop: Backdrop?,
  surface: Color,
  dark: Boolean,
): Modifier {
  if (backdrop == null) {
    return background(surface)
  }

  return drawBackdrop(
    backdrop = backdrop,
    shape = { RoundedCornerShape(percent = 50) },
    effects = {
      vibrancy()
      blur(3.dp.toPx())
      lens(8.dp.toPx(), 24.dp.toPx())
    },
    onDrawSurface = {
      drawRect(surface.copy(alpha = if (dark) 0.28f else 0.38f))
    },
  )
}

private const val PROGRESSIVE_TOP_BAR_SHADER = """
uniform shader content;
uniform float2 contentOrigin;
uniform float2 contentSize;
uniform float2 bufferSize;
uniform float maxRadius;

float hash12(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

half4 progressiveBlur(float2 coord, float radius) {
    if (radius < 0.5) {
        return content.eval(coord);
    }
    float h = hash12(coord);
    float2 dir = float2(cos(h * 6.2831853), sin(h * 6.2831853));
    float2 g = float2(cos(2.39996323), sin(2.39996323));
    half4 sum = half4(0.0);
    float wsum = 0.0;
    for (int i = 0; i < 32; i++) {
        float fi = float(i);
        float ff = (fi + 0.5) / 32.0;
        float r = radius * sqrt(ff);
        r *= 0.90 + 0.20 * fract(h * 93.9898 + fi * 0.7548776662);
        float2 o = dir * r;
        float w = exp(-ff / 0.85);
        float2 sc = clamp(coord + o, float2(0.0), max(bufferSize - 1.0, float2(0.0)));
        half4 c = content.eval(sc);
        if (c.a > 0.02) {
            sum += c * w;
            wsum += w;
        }
        dir = float2(dir.x * g.x - dir.y * g.y, dir.x * g.y + dir.y * g.x);
    }
    if (wsum < 0.0001) {
        return content.eval(coord);
    }
    return sum / wsum;
}

half4 main(float2 coord) {
    // Padding margin is off-screen: pass the source through untouched.
    float2 local = coord - contentOrigin;
    if (local.x < 0.0 || local.y < 0.0 ||
        local.x >= contentSize.x || local.y >= contentSize.y) {
        return content.eval(coord);
    }
    // Neutral blur only: no tint, no alpha dissolve. The radius decays to zero
    // exactly at the bar's foot, so the layer there already equals the sharp
    // content underneath and the boundary is invisible without any fade.
    float t = clamp(local.y / max(contentSize.y, 1.0), 0.0, 1.0);
    float radius = maxRadius * (1.0 - smoothstep(0.0, 1.0, t));
    return progressiveBlur(coord, radius);
}
"""

/** Small box average that flattens the dither grain; radius tracks the blur. */
private const val PROGRESSIVE_DENOISE_SHADER = """
uniform shader content;
uniform float2 contentOrigin;
uniform float2 contentSize;
uniform float2 bufferSize;
uniform float maxRadius;

half4 main(float2 coord) {
    float2 local = coord - contentOrigin;
    if (local.x < 0.0 || local.y < 0.0 ||
        local.x >= contentSize.x || local.y >= contentSize.y) {
        return content.eval(coord);
    }
    float t = clamp(local.y / max(contentSize.y, 1.0), 0.0, 1.0);
    float r = maxRadius * (1.0 - smoothstep(0.0, 1.0, t)) * 0.18;
    if (r < 0.4) {
        return content.eval(coord);
    }
    half4 center = content.eval(coord);
    // Where the capture itself is not opaque (the strip above the source's top
    // edge) averaging would reinflate alpha from the opaque neighbours and
    // print a band; pass the pixel through instead.
    if (center.a < 0.99) {
        return center;
    }
    half4 sum = center;
    float wsum = 1.0;
    float2 dir = float2(1.0, 0.0);
    float2 g = float2(cos(0.7853981634), sin(0.7853981634));
    for (int i = 0; i < 8; i++) {
        float2 sc = clamp(coord + dir * r, float2(0.0), max(bufferSize - 1.0, float2(0.0)));
        half4 c = content.eval(sc);
        if (c.a > 0.02) {
            sum += c;
            wsum += 1.0;
        }
        dir = float2(dir.x * g.x - dir.y * g.y, dir.x * g.y + dir.y * g.x);
    }
    return sum / wsum;
}
"""
