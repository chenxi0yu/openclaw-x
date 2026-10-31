package ai.openclaw.app.ui.chat

import ai.openclaw.app.R
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat

/**
 * The composer's text input, backed by a real [EditText].
 *
 * Compose text fields can only raise Compose's own selection menu, which is an unstyleable popup
 * the app cannot dismiss from outside. The system selection menu — the HyperOS one on this
 * device — attaches to native text views, so the composer edits through one while everything
 * around it stays Compose.
 */
@Composable
internal fun ChatComposerEditText(
  value: String,
  onValueChange: (String) -> Unit,
  enabled: Boolean,
  textStyle: TextStyle,
  cursorColor: Color,
  sendEnabled: Boolean,
  onSend: () -> Unit,
  focusRequestId: Int,
  modifier: Modifier = Modifier,
  /** Fixed-height bars center the single visible line inside the capsule. */
  gravityCenterVertical: Boolean = false,
  /** Large writing surfaces start their text at the top edge. */
  gravityTop: Boolean = false,
  /** Reports the rendered line count so a growing bar can size itself. */
  onLineCountChanged: ((Int) -> Unit)? = null,
) {
  val context = LocalContext.current
  val keyboard = LocalSoftwareKeyboardController.current
  val latestValueChange by rememberUpdatedState(onValueChange)
  val latestSend by rememberUpdatedState(onSend)
  val latestSendEnabled by rememberUpdatedState(sendEnabled)
  val latestLineCount by rememberUpdatedState(onLineCountChanged)
  var field by remember { mutableStateOf<EditText?>(null) }
  val textColor = textStyle.color
  val textSizeSp = textStyle.fontSize.value
  val typeface = remember(context) { ResourcesCompat.getFont(context, R.font.misans_regular) }
  val highlight = cursorColor.copy(alpha = 0.22f)

  AndroidView(
    factory = { viewContext ->
      EditText(viewContext).apply {
        background = null
        setPadding(0, 0, 0, 0)
        includeFontPadding = false
        gravity =
          when {
            gravityCenterVertical -> Gravity.CENTER_VERTICAL
            gravityTop -> Gravity.TOP or Gravity.START
            else -> gravity
          }
        isSingleLine = false
        maxLines = 6
        inputType =
          InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        // Enter sends, exactly as the Compose field's hardware-key filter used to; a hardware
        // Shift+Enter still inserts a newline below.
        imeOptions = EditorInfo.IME_ACTION_SEND
        this.typeface = typeface
        textSize = textSizeSp
        setTextColor(textColor.toArgb())
        highlightColor = highlight.toArgb()
        textCursorDrawable?.setTint(cursorColor.toArgb())
        setOnEditorActionListener { _, actionId, event ->
          if (actionId != EditorInfo.IME_ACTION_SEND && event?.keyCode != KeyEvent.KEYCODE_ENTER) {
            return@setOnEditorActionListener false
          }
          if (!latestSendEnabled) return@setOnEditorActionListener false
          latestSend()
          true
        }
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
          latestLineCount?.invoke(lineCount)
        }
        addTextChangedListener(
          object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
              latestValueChange(s?.toString().orEmpty())
              post { latestLineCount?.invoke(lineCount) }
            }
          },
        )
        setOnKeyListener { _, keyCode, event ->
          if (keyCode != KeyEvent.KEYCODE_ENTER || event.action != KeyEvent.ACTION_DOWN) {
            return@setOnKeyListener false
          }
          val modified =
            event.isShiftPressed || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed
          // Modified or repeating enters stay with the field, so newlines and held keys work.
          if (modified || event.repeatCount > 0) return@setOnKeyListener false
          if (!latestSendEnabled) return@setOnKeyListener false
          latestSend()
          true
        }
        field = this
      }
    },
    update = { view ->
      if (view.text.toString() != value) {
        view.setText(value)
        view.setSelection(view.text.length)
      }
      view.isEnabled = enabled
      view.setTextColor(textColor.toArgb())
      view.highlightColor = highlight.toArgb()
      view.textCursorDrawable?.setTint(cursorColor.toArgb())
    },
    modifier = modifier,
  )

  LaunchedEffect(focusRequestId) {
    if (focusRequestId <= 0) return@LaunchedEffect
    field?.requestFocus()
    keyboard?.show()
  }
}
