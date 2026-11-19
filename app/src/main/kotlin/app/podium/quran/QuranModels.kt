package app.podium.quran

enum class RevelationPlace(val arabicName: String, val englishName: String) {
    Makkah("مكية", "Meccan"),
    Madinah("مدنية", "Medinan"),
}

data class Surah(
    val id: Int,
    val nameArabic: String,
    val nameEnglish: String,
    val versesCount: Int,
    val revelationPlace: RevelationPlace,
    val bismillahPre: Boolean,
    val startPage: Int,
)

data class Reciter(
    val id: Int,
    val nameArabic: String,
    val nameEnglish: String,
    val style: String? = null,
)

data class Ayah(
    val id: Int,
    val verseKey: String, // e.g. "1:1"
    val ayahNumber: Int,
    val textUthmani: String,
    val timestampFromMs: Long = 0L,
    val timestampToMs: Long = 0L,
    val translationText: String? = null,
)

enum class QuranTranslationOption(
    val id: Int,
    val label: String,
    val isTafsir: Boolean = false,
) {
    ENGLISH(85, "English (Abdel Haleem)"),
    TURKISH(77, "Türkçe (Diyanet)"),
    RUSSIAN(45, "Русский (Кулиев)"),
    TAFSIR_MUYASSAR(16, "التفسير الميسر", isTafsir = true),
    TAFSIR_IBN_KATHIR(14, "تفسير ابن كثير", isTafsir = true),
}

data class QuranBookmark(
    val surahId: Int,
    val ayahNumber: Int,
    val surahName: String,
    val timestampMs: Long = 0L,
    val addedAt: Long = System.currentTimeMillis(),
)

data class QuranSearchResult(
    val verseKey: String,
    val surahId: Int,
    val ayahNumber: Int,
    val textArabic: String,
    val translation: String? = null,
)

sealed interface QuranPlace {
    val title: String

    data object Menu : QuranPlace {
        override val title: String get() = "القرآن الكريم"
    }
    data object SurahsList : QuranPlace {
        override val title: String get() = "السور"
    }
    data object TarjamaSurahsList : QuranPlace {
        override val title: String get() = "القرآن بالترجمة"
    }
    data class Mosshaf(val surahId: Int, val initialAyah: Int? = null, val showTranslation: Boolean = false) : QuranPlace {
        override val title: String get() {
            val s = AllSurahs.find { it.id == surahId }
            return s?.nameArabic ?: "المصحف"
        }
    }
    data object RecitersList : QuranPlace {
        override val title: String get() = "القراء"
    }
    data object TranslationSettings : QuranPlace {
        override val title: String get() = "لغة الترجمة والتفسير"
    }
    data object Search : QuranPlace {
        override val title: String get() = "البحث في القرآن"
    }
    data object Bookmarks : QuranPlace {
        override val title: String get() = "العلامات المرجعية"
    }

    companion object {
        fun encode(place: QuranPlace): String = when (place) {
            Menu -> "menu"
            SurahsList -> "surahs"
            TarjamaSurahsList -> "tarjama_surahs"
            is Mosshaf -> "mosshaf:${place.surahId}:${place.initialAyah ?: -1}:${if (place.showTranslation) 1 else 0}"
            RecitersList -> "reciters"
            TranslationSettings -> "translation_settings"
            Search -> "search"
            Bookmarks -> "bookmarks"
        }

        fun decode(raw: String): QuranPlace = when {
            raw == "menu" -> Menu
            raw == "surahs" -> SurahsList
            raw == "tarjama_surahs" -> TarjamaSurahsList
            raw.startsWith("mosshaf:") -> {
                val parts = raw.removePrefix("mosshaf:").split(":")
                val sId = parts.getOrNull(0)?.toIntOrNull() ?: 1
                val aNum = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
                val showTr = parts.getOrNull(2) == "1"
                Mosshaf(sId, aNum, showTr)
            }
            raw == "reciters" -> RecitersList
            raw == "translation_settings" -> TranslationSettings
            raw == "search" -> Search
            raw == "bookmarks" -> Bookmarks
            else -> Menu
        }
    }
}
