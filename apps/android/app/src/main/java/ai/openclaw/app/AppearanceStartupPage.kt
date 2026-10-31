package ai.openclaw.app

/** Which page the app lands on after a cold start. */
enum class AppearanceStartupPage(
  val rawValue: String,
  val displayLabel: String,
) {
  Home(rawValue = "home", displayLabel = "主页"),
  NewChat(rawValue = "new-chat", displayLabel = "新对话"),
  LastClosed(rawValue = "last-closed", displayLabel = "上次关闭"),
  ;

  companion object {
    fun fromRawValue(value: String?): AppearanceStartupPage =
      entries.firstOrNull { it.rawValue == value?.trim()?.lowercase() } ?: Home
  }
}
