package ai.openclaw.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Lucide glyphs for floating-menu rows, as stroke [ImageVector]s so they can
 * feed the existing `ImageVector` parameter of [ClawGlassMenuItem] untouched.
 *
 * 24x24 grid, 2px round stroke, Lucide `d` strings kept verbatim.
 */
private fun lucideIcon(
  name: String,
  paths: List<String>,
): ImageVector =
  ImageVector
    .Builder(
      name = name,
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).apply {
      paths.forEach { d ->
        addPath(
          pathData = PathParser().parsePathString(d).toNodes(),
          // Solid placeholder: Icon() tints over it with a color filter, the
          // same way it recolors the filled Material glyphs.
          stroke = SolidColor(Color.Black),
          strokeLineWidth = 2f,
          strokeLineCap = StrokeCap.Round,
          strokeLineJoin = StrokeJoin.Round,
        )
      }
    }.build()

internal val LucideCameraIcon: ImageVector by lazy {
  lucideIcon(
    name = "LucideCamera",
    paths =
      listOf(
        "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z",
        "M9 13a3 3 0 1 0 6 0 3 3 0 1 0 -6 0",
      ),
  )
}

internal val LucideImageIcon: ImageVector by lazy {
  lucideIcon(
    name = "LucideImage",
    paths =
      listOf(
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
        "M7 9a2 2 0 1 0 4 0 2 2 0 1 0 -4 0",
        "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
      ),
  )
}

internal val LucideVideoIcon: ImageVector by lazy {
  lucideIcon(
    name = "LucideVideo",
    paths =
      listOf(
        "m22 8-6 4 6 4V8Z",
        "M4 6h10a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",
      ),
  )
}

internal val LucidePaperclipIcon: ImageVector by lazy {
  lucideIcon(
    name = "LucidePaperclip",
    paths =
      listOf(
        "m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1 18 8.84l-8.59 8.57a2 2 0 0 1-2.83-2.83l8.49-8.48",
      ),
  )
}

internal val LucideXIcon: ImageVector by lazy {
  lucideIcon(
    name = "LucideX",
    paths =
      listOf(
        "M18 6 6 18",
        "m6 6 12 12",
      ),
  )
}

internal val LucideCheckIcon: ImageVector by lazy {
  lucideIcon(
    name = "LucideCheck",
    paths =
      listOf(
        "M20 6 9 17l-5-5",
      ),
  )
}

internal val LucideLinkIcon: ImageVector by lazy {
  lucideIcon(
    name = "LucideLink",
    paths =
      listOf(
        "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
        "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71",
      ),
  )
}

internal val LucideTextIcon: ImageVector by lazy {
  lucideIcon(
    name = "LucideText",
    paths =
      listOf(
        "M17 6.1H3",
        "M21 12.1H3",
        "M15.1 18H3",
      ),
  )
}

/** Lucide `panel-bottom`: a rounded panel with the composer's bar along its base. */
internal val LucideComposerBarIcon: ImageVector by lazy {
  lucideIcon(
    name = "LucideComposerBar",
    paths =
      listOf(
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
        "M3 15h18",
      ),
  )
}

/**
 * WeChat brand mark (filled, per the simple-icons glyph). Unlike the stroke
 * Lucide set above this is a filled path — the two-bubble logo only reads
 * correctly as solid shapes.
 */
internal val WeChatBrandIcon: ImageVector by lazy {
  ImageVector
    .Builder(
      name = "WeChatBrand",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).apply {
      addPath(
        pathData =
          PathParser().parsePathString(
            "M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 0 1 .213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .32.26.58.58.58a.56.56 0 0 0 .314-.099l1.917-1.16a.89.89 0 0 1 .717-.097 10.16 10.16 0 0 0 2.8.37c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.646 1.704-1.615 4.224-2.53 6.63-2.213-.486-3.067-3.415-5.39-6.787-5.39zm-2.592 2.622a1.055 1.055 0 1 1 0 2.11 1.055 1.055 0 0 1 0-2.11zm5.181 0a1.055 1.055 0 1 1 0 2.11 1.055 1.055 0 0 1 0-2.11zm5.96 5.539c-3.906 0-7.031 2.629-7.031 5.86 0 3.233 3.125 5.86 7.031 5.86.789 0 1.544-.109 2.262-.303a.72.72 0 0 1 .58.079l1.542.932a.45.45 0 0 0 .253.08c.257 0 .466-.208.466-.465 0-.057-.023-.114-.038-.17l-.313-1.186a.477.477 0 0 1 .17-.535A5.86 5.86 0 0 0 24 16.21c0-3.232-3.124-5.86-7.031-5.86zm-2.293 2.622a.89.89 0 1 1 0 1.78.89.89 0 0 1 0-1.78zm4.586 0a.89.89 0 1 1 0 1.78.89.89 0 0 1 0-1.78z",
          ).toNodes(),
        fill = SolidColor(Color.Black),
      )
    }.build()
}
