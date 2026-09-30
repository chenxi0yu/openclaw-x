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
 * Same convention as the tool-chain icons in `LucideToolIcon`: 24x24 grid,
 * 2px round stroke, Lucide `d` strings kept verbatim.
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

internal val LucideSquarePenIcon: ImageVector by lazy {
  lucideIcon(
    name = "LucideSquarePen",
    paths =
      listOf(
        "M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7",
        "M18.375 2.625a1 1 0 0 1 3 3l-9.013 9.014a2 2 0 0 1-.853.505l-2.873.84a.5.5 0 0 1-.62-.62l.84-2.873a2 2 0 0 1 .506-.852z",
      ),
  )
}

internal val LucideEllipsisIcon: ImageVector by lazy {
  lucideIcon(
    name = "LucideEllipsis",
    paths =
      listOf(
        "M5 12h.01",
        "M12 12h.01",
        "M19 12h.01",
      ),
  )
}
