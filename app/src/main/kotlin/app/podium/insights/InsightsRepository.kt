package app.podium.insights

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Persistent daily-bucketed activity repository for Insights:
 * records real-time listening, watching, reading, and viewing duration & item counts
 * across Music, YT Music, YouTube, SoundCloud, Radio, Podcasts, Quran, Books, and Gallery.
 */
class InsightsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("podium_insights_v1", Context.MODE_PRIVATE)
    private val lock = Any()

    // dayKey ("YYYY-MM-DD") -> categoryId -> MutableCategoryBucket
    private val dailyData = LinkedHashMap<String, LinkedHashMap<String, MutableCategoryBucket>>()

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private var lastSavedAtMs = 0L

    private data class MutableItemBucket(
        var title: String,
        var subtitle: String,
        var count: Int = 0,
        var durationMs: Long = 0L,
    )

    private data class MutableCategoryBucket(
        var durationMs: Long = 0L,
        var sessionsCount: Int = 0,
        val items: LinkedHashMap<String, MutableItemBucket> = LinkedHashMap(),
    )

    init {
        loadFromPrefs()
    }

    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun dayKeyDaysAgo(daysAgo: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    fun recordItemOpened(
        category: InsightsCategory,
        itemKey: String,
        title: String,
        subtitle: String = "",
    ) {
        if (title.isBlank()) return
        synchronized(lock) {
            val day = todayKey()
            val catMap = dailyData.getOrPut(day) { LinkedHashMap() }
            val bucket = catMap.getOrPut(category.id) { MutableCategoryBucket() }
            bucket.sessionsCount += 1
            val item = bucket.items.getOrPut(itemKey.ifBlank { title }) {
                MutableItemBucket(title = title, subtitle = subtitle)
            }
            item.title = title
            if (subtitle.isNotBlank()) item.subtitle = subtitle
            item.count += 1
            trimTopItems(bucket)
            saveToPrefsLocked(force = true)
            _version.value = _version.value + 1
        }
    }

    fun addActiveTime(
        category: InsightsCategory,
        durationMs: Long,
        itemKey: String? = null,
        title: String? = null,
        subtitle: String = "",
    ) {
        if (durationMs <= 0L) return
        synchronized(lock) {
            val day = todayKey()
            val catMap = dailyData.getOrPut(day) { LinkedHashMap() }
            val bucket = catMap.getOrPut(category.id) { MutableCategoryBucket() }
            bucket.durationMs += durationMs
            if (!title.isNullOrBlank()) {
                val key = itemKey?.takeIf { it.isNotBlank() } ?: title
                val item = bucket.items.getOrPut(key) {
                    MutableItemBucket(title = title, subtitle = subtitle)
                }
                item.title = title
                if (subtitle.isNotBlank()) item.subtitle = subtitle
                item.durationMs += durationMs
                trimTopItems(bucket)
            }
            saveToPrefsLocked(force = false)
            _version.value = _version.value + 1
        }
    }

    fun resetAll() {
        synchronized(lock) {
            dailyData.clear()
            prefs.edit().remove(KEY_JSON).apply()
            _version.value = _version.value + 1
        }
    }

    fun computeCategory(category: InsightsCategory, period: InsightsPeriod): CategoryStats {
        synchronized(lock) {
            val allowedDays = allowedDayKeys(period)
            var totalMs = 0L
            var totalSessions = 0
            val mergedItems = LinkedHashMap<String, MutableItemBucket>()

            for ((day, catMap) in dailyData) {
                if (allowedDays != null && day !in allowedDays) continue
                val bucket = catMap[category.id] ?: continue
                totalMs += bucket.durationMs
                totalSessions += bucket.sessionsCount
                for ((k, v) in bucket.items) {
                    val m = mergedItems.getOrPut(k) {
                        MutableItemBucket(v.title, v.subtitle, 0, 0L)
                    }
                    m.count += v.count
                    m.durationMs += v.durationMs
                }
            }

            val top = mergedItems.entries
                .sortedWith(compareByDescending<Map.Entry<String, MutableItemBucket>> { it.value.durationMs }.thenByDescending { it.value.count })
                .take(5)
                .map { (k, v) ->
                    TopActivityItem(
                        key = k,
                        title = v.title,
                        subtitle = v.subtitle,
                        count = v.count,
                        durationMs = v.durationMs,
                    )
                }

            return CategoryStats(
                category = category,
                durationMs = totalMs,
                sessionsCount = totalSessions,
                topItems = top,
            )
        }
    }

    fun computeOverview(period: InsightsPeriod): OverallStats {
        synchronized(lock) {
            val allowedDays = allowedDayKeys(period)
            val perCategory = InsightsCategory.entries.map { computeCategory(it, period) }
            val totalMs = perCategory.sumOf { it.durationMs }
            val totalSessions = perCategory.sumOf { it.sessionsCount }
            val activeDays = dailyData.entries.count { (day, cats) ->
                (allowedDays == null || day in allowedDays) &&
                    cats.values.any { it.durationMs > 0L || it.sessionsCount > 0 }
            }

            // Compute current consecutive streak ending today or yesterday
            var streak = 0
            for (offset in 0..365) {
                val dk = dayKeyDaysAgo(offset)
                val hasActivity = dailyData[dk]?.values?.any { it.durationMs > 0L || it.sessionsCount > 0 } == true
                if (hasActivity) {
                    streak++
                } else if (offset > 0) {
                    break
                }
            }

            val topCategory = perCategory
                .filter { it.durationMs > 0L || it.sessionsCount > 0 }
                .maxWithOrNull(compareBy<CategoryStats> { it.durationMs }.thenBy { it.sessionsCount })
                ?.category

            val topItems = perCategory
                .flatMap { cs -> cs.topItems.map { cs.category to it } }
                .sortedWith(compareByDescending<Pair<InsightsCategory, TopActivityItem>> { it.second.durationMs }.thenByDescending { it.second.count })
                .take(5)

            return OverallStats(
                totalDurationMs = totalMs,
                totalSessions = totalSessions,
                activeDays = activeDays,
                currentStreakDays = streak,
                byCategory = perCategory.sortedWith(compareByDescending<CategoryStats> { it.durationMs }.thenByDescending { it.sessionsCount }),
                topCategory = topCategory,
                topOverallItems = topItems,
            )
        }
    }

    private fun allowedDayKeys(period: InsightsPeriod): Set<String>? {
        val days = period.daysBack ?: return null
        return (0 until days).map { dayKeyDaysAgo(it) }.toSet()
    }

    private fun trimTopItems(bucket: MutableCategoryBucket) {
        if (bucket.items.size <= 25) return
        val keep = bucket.items.entries
            .sortedWith(compareByDescending<Map.Entry<String, MutableItemBucket>> { it.value.durationMs }.thenByDescending { it.value.count })
            .take(20)
            .map { it.key }
            .toSet()
        bucket.items.keys.retainAll(keep)
    }

    private fun saveToPrefsLocked(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSavedAtMs < 5_000L) return
        lastSavedAtMs = now
        runCatching {
            val root = JSONObject()
            for ((day, catMap) in dailyData) {
                val dayObj = JSONObject()
                for ((catId, bucket) in catMap) {
                    val catObj = JSONObject()
                    catObj.put("ms", bucket.durationMs)
                    catObj.put("cnt", bucket.sessionsCount)
                    val arr = JSONArray()
                    for ((k, item) in bucket.items) {
                        arr.put(
                            JSONObject().apply {
                                put("k", k)
                                put("t", item.title)
                                put("s", item.subtitle)
                                put("c", item.count)
                                put("d", item.durationMs)
                            },
                        )
                    }
                    catObj.put("items", arr)
                    dayObj.put(catId, catObj)
                }
                root.put(day, dayObj)
            }
            prefs.edit().putString(KEY_JSON, root.toString()).apply()
        }
    }

    private fun loadFromPrefs() {
        val raw = prefs.getString(KEY_JSON, null) ?: return
        runCatching {
            val root = JSONObject(raw)
            val dayIter = root.keys()
            while (dayIter.hasNext()) {
                val day = dayIter.next()
                val dayObj = root.optJSONObject(day) ?: continue
                val catMap = LinkedHashMap<String, MutableCategoryBucket>()
                val catIter = dayObj.keys()
                while (catIter.hasNext()) {
                    val catId = catIter.next()
                    val catObj = dayObj.optJSONObject(catId) ?: continue
                    val bucket = MutableCategoryBucket(
                        durationMs = catObj.optLong("ms", 0L),
                        sessionsCount = catObj.optInt("cnt", 0),
                    )
                    val arr = catObj.optJSONArray("items")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val k = o.optString("k").takeIf { it.isNotBlank() } ?: continue
                            bucket.items[k] = MutableItemBucket(
                                title = o.optString("t", k),
                                subtitle = o.optString("s", ""),
                                count = o.optInt("c", 0),
                                durationMs = o.optLong("d", 0L),
                            )
                        }
                    }
                    catMap[catId] = bucket
                }
                dailyData[day] = catMap
            }
        }
    }

    private companion object {
        const val KEY_JSON = "insights_daily_json"
    }
}
