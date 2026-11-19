package app.podium.insights

enum class InsightsPeriod(
    val labelEn: String,
    val labelAr: String,
    val daysBack: Int?, // null = All Time
) {
    TODAY("Today", "اليوم", 1),
    WEEK("Week", "الأسبوع", 7),
    MONTH("Month", "الشهر", 30),
    ALL_TIME("All Time", "الكل", null),
}

enum class InsightsCategory(
    val id: String,
    val titleEn: String,
    val titleAr: String,
    val accentHex: Long,
) {
    MUSIC("music", "Music", "الموسيقى", 0xFFFA2D48),
    YT_MUSIC("yt_music", "YT Music", "YT Music", 0xFFFF0033),
    YOUTUBE("youtube", "YouTube", "يوتيوب", 0xFFFF2D20),
    SOUNDCLOUD("soundcloud", "SoundCloud", "ساوند كلاود", 0xFFFF5500),
    RADIO("radio", "Radio", "الراديو", 0xFF34C759),
    PODCASTS("podcasts", "Podcasts", "البودكاست", 0xFFAF52DE),
    QURAN("quran", "Quran", "القرآن الكريم", 0xFFD4AF37),
    BOOKS("books", "Books", "الكتب", 0xFF0A84FF),
    GALLERY("gallery", "Gallery", "المعرض", 0xFF30B0C7),
}

data class TopActivityItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val count: Int,
    val durationMs: Long,
)

data class CategoryStats(
    val category: InsightsCategory,
    val durationMs: Long,
    val sessionsCount: Int,
    val topItems: List<TopActivityItem>,
)

data class OverallStats(
    val totalDurationMs: Long,
    val totalSessions: Int,
    val activeDays: Int,
    val currentStreakDays: Int,
    val byCategory: List<CategoryStats>,
    val topCategory: InsightsCategory?,
    val topOverallItems: List<Pair<InsightsCategory, TopActivityItem>>,
)

fun formatInsightsDuration(ms: Long): String {
    if (ms <= 0L) return "0m"
    val totalSec = ms / 1000L
    val hours = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return when {
        hours >= 10 -> "${hours}h ${mins}m"
        hours > 0 -> "${hours}h ${mins}m"
        mins > 0 -> "${mins}m"
        else -> "${secs}s"
    }
}
