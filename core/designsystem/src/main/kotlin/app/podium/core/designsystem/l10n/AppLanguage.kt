package app.podium.core.designsystem.l10n

import java.util.Locale

/** Supported languages for Podium / CHORT interface. */
enum class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val isRtl: Boolean = false,
) {
    SYSTEM("system", "System", "System Default"),
    ENGLISH("en", "English", "English"),
    ARABIC("ar", "العربية", "Arabic", isRtl = true),
    FRENCH("fr", "Français", "French"),
    TURKISH("tr", "Türkçe", "Turkish"),
    RUSSIAN("ru", "Русский", "Russian");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SYSTEM

        fun resolve(lang: AppLanguage, systemLocale: Locale = Locale.getDefault()): AppLanguage {
            if (lang != SYSTEM) return lang
            val code = systemLocale.language.lowercase()
            return when {
                code == "ar" -> ARABIC
                code == "fr" -> FRENCH
                code == "tr" -> TURKISH
                code == "ru" -> RUSSIAN
                else -> ENGLISH
            }
        }
    }
}
