package ai.openclaw.app.ui.chat

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun chatTableCsvFileName(nowMillis: Long = System.currentTimeMillis()): String =
  "table-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(nowMillis)) + ".csv"

/**
 * Writes a rendered chat table to the system Downloads collection. MediaStore
 * owns the file URI, so no storage permission is involved on API 29+.
 */
internal suspend fun saveChatTableCsv(
  context: Context,
  csv: String,
): String =
  withContext(Dispatchers.IO) {
    val fileName = chatTableCsvFileName()
    val resolver = context.contentResolver
    val values =
      ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
      }
    val uri =
      resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: error("could not create Downloads item")
    try {
      val output =
        resolver.openOutputStream(uri, "w")
          ?: error("could not open Downloads item")
      output.use { stream -> stream.write(csv.toByteArray(Charsets.UTF_8)) }
      check(
        resolver.update(
          uri,
          ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
          null,
          null,
        ) == 1,
      ) { "could not publish Downloads item" }
    } catch (error: Throwable) {
      resolver.delete(uri, null, null)
      throw error
    }
    fileName
  }
