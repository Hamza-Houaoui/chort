package app.podium.clock

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class WorldCity(
    val id: String,
    val name: String,
    val country: String,
    val timeZoneId: String,
    val latitude: Double = 36.8065,
    val longitude: Double = 10.1815,
    val nameArabic: String = name,
) {
    fun currentTimeFormatted(): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone(timeZoneId))
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    fun isDaytime(): Boolean {
        val cal = Calendar.getInstance(TimeZone.getTimeZone(timeZoneId))
        val h = cal.get(Calendar.HOUR_OF_DAY)
        return h in 6..18
    }

    fun relativeOffsetLabel(): String {
        val now = System.currentTimeMillis()
        val localTz = TimeZone.getDefault()
        val targetTz = TimeZone.getTimeZone(timeZoneId)
        val diffMs = targetTz.getOffset(now) - localTz.getOffset(now)
        val diffHours = diffMs / (3600 * 1000)
        val sign = if (diffHours >= 0) "+" else ""
        val dayIcon = if (isDaytime()) "☀" else "🌙"
        return if (diffHours == 0) "$dayIcon Local Time" else "$dayIcon ${sign}${diffHours} HRS"
    }
}

enum class AlarmSoundType(val label: String) {
    RADIO("Radio Station"),
    ALBUM("Music Album"),
    LOCAL_SONG("Library Song"),
    DOWNLOADED("Downloaded Song"),
    YTM_LIKED("YouTube Music Liked"),
    SOUNDCLOUD("SoundCloud Track"),
    QURAN("Quran Surah"),
    MELODIC_CHIME("WakeIQ Melodic Chime"),
}

data class WakeAlarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val label: String = "WakeIQ Alarm",
    /** 1 = Mon .. 7 = Sun; empty = One-shot */
    val daysOfWeek: Set<Int> = emptySet(),
    /** WakeIQ Smart Window before set time (0, 5, 10, 15, 30 min) */
    val smartWindowMinutes: Int = 15,
    /** WakeIQ Gradual Sound & Brightness Ramp (1, 3, 5, 10, 15 min) */
    val rampDurationMinutes: Int = 5,
    val soundType: AlarmSoundType = AlarmSoundType.MELODIC_CHIME,
    val soundId: String = "",
    val soundTitle: String = "Gentle Morning Chime",
    val soundSubtitle: String = "WakeIQ Default",
) {
    val timeFormatted: String
        get() = String.format(Locale.US, "%02d:%02d", hour, minute)

    val daysSummary: String
        get() {
            if (daysOfWeek.isEmpty()) return "Once"
            if (daysOfWeek.size == 7) return "Every day"
            if (daysOfWeek == setOf(1, 2, 3, 4, 5)) return "Weekdays"
            if (daysOfWeek == setOf(6, 7)) return "Weekends"
            val names = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
            return daysOfWeek.sorted().mapNotNull { names[it] }.joinToString(" ")
        }
}

sealed interface ClockPlace {
    val title: String

    data object Menu : ClockPlace {
        override val title: String = "Clock"
    }

    data object WorldClock : ClockPlace {
        override val title: String = "World Clock"
    }

    data object AddWorldCity : ClockPlace {
        override val title: String = "Add City"
    }

    data object AlarmsList : ClockPlace {
        override val title: String = "Alarms"
    }

    data class EditAlarm(val alarmId: String) : ClockPlace {
        override val title: String = "Edit Alarm"
    }

    data class PickSoundType(val alarmId: String) : ClockPlace {
        override val title: String = "Wake Sound Source"
    }

    data class PickSoundItem(
        val alarmId: String,
        val soundType: AlarmSoundType,
    ) : ClockPlace {
        override val title: String = soundType.label
    }

    data object Stopwatch : ClockPlace {
        override val title: String = "Stopwatch"
    }

    data object Timer : ClockPlace {
        override val title: String = "Timer"
    }

    companion object {
        fun encode(place: ClockPlace): String = when (place) {
            Menu -> "menu"
            WorldClock -> "world"
            AddWorldCity -> "add_city"
            AlarmsList -> "alarms"
            is EditAlarm -> "edit:${place.alarmId}"
            is PickSoundType -> "sound_type:${place.alarmId}"
            is PickSoundItem -> "sound_item:${place.alarmId}:${place.soundType.name}"
            Stopwatch -> "stopwatch"
            Timer -> "timer"
        }

        fun decode(raw: String): ClockPlace = when {
            raw == "world" -> WorldClock
            raw == "add_city" -> AddWorldCity
            raw == "alarms" -> AlarmsList
            raw.startsWith("edit:") -> EditAlarm(raw.removePrefix("edit:"))
            raw.startsWith("sound_type:") -> PickSoundType(raw.removePrefix("sound_type:"))
            raw.startsWith("sound_item:") -> {
                val parts = raw.removePrefix("sound_item:").split(":", limit = 2)
                val type = AlarmSoundType.entries.find { it.name == parts.getOrNull(1) } ?: AlarmSoundType.MELODIC_CHIME
                PickSoundItem(parts.getOrElse(0) { "" }, type)
            }
            raw == "stopwatch" -> Stopwatch
            raw == "timer" -> Timer
            else -> Menu
        }
    }
}

val ALL_WORLD_CITIES = listOf(
    WorldCity("tunis", "Tunis", "Tunisia", "Africa/Tunis", 36.8065, 10.1815, "تونس"),
    WorldCity("makkah", "Makkah", "Saudi Arabia", "Asia/Riyadh", 21.3891, 39.8579, "مكة المكرمة"),
    WorldCity("medina", "Medina", "Saudi Arabia", "Asia/Riyadh", 24.5247, 39.5692, "المدينة المنورة"),
    WorldCity("cairo", "Cairo", "Egypt", "Africa/Cairo", 30.0444, 31.2357, "القاهرة"),
    WorldCity("algiers", "Algiers", "Algeria", "Africa/Algiers", 36.7538, 3.0588, "الجزائر"),
    WorldCity("casablanca", "Casablanca", "Morocco", "Africa/Casablanca", 33.5731, -7.5898, "الدار البيضاء"),
    WorldCity("istanbul", "Istanbul", "Turkey", "Europe/Istanbul", 41.0082, 28.9784, "إسطنبول"),
    WorldCity("jerusalem", "Al-Quds", "Palestine", "Asia/Jerusalem", 31.7683, 35.2137, "القدس"),
    WorldCity("doha", "Doha", "Qatar", "Asia/Qatar", 25.2854, 51.5310, "الدوحة"),
    WorldCity("dubai", "Dubai", "UAE", "Asia/Dubai", 25.2048, 55.2708, "دبي"),
    WorldCity("paris", "Paris", "France", "Europe/Paris", 48.8566, 2.3522, "باريس"),
    WorldCity("london", "London", "UK", "Europe/London", 51.5072, -0.1276, "لندن"),
    WorldCity("berlin", "Berlin", "Germany", "Europe/Berlin", 52.5200, 13.4050, "برلين"),
    WorldCity("moscow", "Moscow", "Russia", "Europe/Moscow", 55.7558, 37.6173, "موسكو"),
    WorldCity("new_york", "New York", "USA", "America/New_York", 40.7128, -74.0060, "نيويورك"),
    WorldCity("los_angeles", "Los Angeles", "USA", "America/Los_Angeles", 34.0522, -118.2437, "لوس أنجلوس"),
    WorldCity("tokyo", "Tokyo", "Japan", "Asia/Tokyo", 35.6762, 139.6503, "طوكيو"),
    WorldCity("seoul", "Seoul", "South Korea", "Asia/Seoul", 37.5665, 126.9780, "سيول"),
    WorldCity("beijing", "Beijing", "China", "Asia/Shanghai", 39.9042, 116.4074, "بكين"),
    WorldCity("sydney", "Sydney", "Australia", "Australia/Sydney", -33.8688, 151.2093, "سيدني"),
)

enum class WeatherCondition {
    SUNNY,
    CLEAR_NIGHT,
    PARTLY_CLOUDY,
    CLOUDY,
    RAINY,
    STORM,
    SNOWY,
}

data class WeatherReport(
    val cityName: String,
    val cityNameArabic: String = cityName,
    val temperatureC: Int,
    val highC: Int,
    val lowC: Int,
    val condition: WeatherCondition,
) {
    fun conditionLabel(langCode: String): String = when (langCode) {
        "ar" -> when (condition) {
            WeatherCondition.SUNNY -> "مشمس"
            WeatherCondition.CLEAR_NIGHT -> "ليل صافي"
            WeatherCondition.PARTLY_CLOUDY -> "غائم جزئياً"
            WeatherCondition.CLOUDY -> "غائم"
            WeatherCondition.RAINY -> "ممطر"
            WeatherCondition.STORM -> "عاصف وممطر"
            WeatherCondition.SNOWY -> "مثلج"
        }
        "fr" -> when (condition) {
            WeatherCondition.SUNNY -> "Ensoleillé"
            WeatherCondition.CLEAR_NIGHT -> "Nuit claire"
            WeatherCondition.PARTLY_CLOUDY -> "Partiellement nuageux"
            WeatherCondition.CLOUDY -> "Nuageux"
            WeatherCondition.RAINY -> "Pluvieux"
            WeatherCondition.STORM -> "Orageux"
            WeatherCondition.SNOWY -> "Neigeux"
        }
        "tr" -> when (condition) {
            WeatherCondition.SUNNY -> "Güneşli"
            WeatherCondition.CLEAR_NIGHT -> "Açık Gece"
            WeatherCondition.PARTLY_CLOUDY -> "Parçalı Bulutlu"
            WeatherCondition.CLOUDY -> "Bulutlu"
            WeatherCondition.RAINY -> "Yağmurlu"
            WeatherCondition.STORM -> "Fırtınalı"
            WeatherCondition.SNOWY -> "Karlı"
        }
        "ru" -> when (condition) {
            WeatherCondition.SUNNY -> "Солнечно"
            WeatherCondition.CLEAR_NIGHT -> "Ясная ночь"
            WeatherCondition.PARTLY_CLOUDY -> "Переменная облачность"
            WeatherCondition.CLOUDY -> "Облачно"
            WeatherCondition.RAINY -> "Дождливо"
            WeatherCondition.STORM -> "Гроза"
            WeatherCondition.SNOWY -> "Снег"
        }
        else -> when (condition) {
            WeatherCondition.SUNNY -> "Sunny"
            WeatherCondition.CLEAR_NIGHT -> "Clear Night"
            WeatherCondition.PARTLY_CLOUDY -> "Partly Cloudy"
            WeatherCondition.CLOUDY -> "Cloudy"
            WeatherCondition.RAINY -> "Rainy"
            WeatherCondition.STORM -> "Thunderstorm"
            WeatherCondition.SNOWY -> "Snowy"
        }
    }

    fun formattedSummary(langCode: String): String {
        val city = if (langCode == "ar") cityNameArabic else cityName
        val cond = conditionLabel(langCode)
        return "$city • ${temperatureC}°C $cond • ${highC}°/${lowC}°"
    }
}
