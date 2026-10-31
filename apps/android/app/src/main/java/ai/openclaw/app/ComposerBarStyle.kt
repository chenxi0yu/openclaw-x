package ai.openclaw.app

/** Which input bar the chat screen renders. */
enum class ComposerBarStyle(
  val rawValue: String,
  val displayLabel: String,
) {
  Classic(rawValue = "classic", displayLabel = "经典"),
  Simple(rawValue = "simple", displayLabel = "简约"),
  ;

  companion object {
    fun fromRawValue(value: String?): ComposerBarStyle =
      entries.firstOrNull { it.rawValue == value?.trim()?.lowercase() } ?: Classic
  }
}
