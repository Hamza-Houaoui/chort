package app.podium.clock

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.podium.MainActivity
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object ActiveAlarmController {
    var ringingAlarm by mutableStateOf<WakeAlarm?>(null)
    var rampProgress by mutableFloatStateOf(0f)
    var fallbackPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquireWakeLock(context: Context) {
        runCatching {
            if (wakeLock?.isHeld == true) return
            val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "CHORT:WakeIQAlarmWakeLock",
            )
            wl.acquire(15 * 60 * 1000L)
            wakeLock = wl
        }
    }

    fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    fun startFallbackChime(context: Context) {
        stopFallbackChime()
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            fallbackPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(context.applicationContext, uri)
                isLooping = true
                prepare()
                start()
            }
        }
    }

    fun stopFallbackChime() {
        runCatching {
            fallbackPlayer?.stop()
            fallbackPlayer?.release()
        }
        fallbackPlayer = null
    }

    fun dismiss(context: Context? = null) {
        stopFallbackChime()
        releaseWakeLock()
        ringingAlarm = null
        rampProgress = 0f
        if (context != null) {
            runCatching {
                context.stopService(Intent(context.applicationContext, WakeIQAlarmService::class.java))
            }
        }
    }
}

class WakeIQAlarmService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "ACTION_DISMISS") {
            ActiveAlarmController.dismiss(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val alarmId = intent?.getStringExtra("alarm_id").orEmpty()
        val repo = ClockRepository(applicationContext)
        val alarm = repo.findAlarm(alarmId) ?: ActiveAlarmController.ringingAlarm

        ActiveAlarmController.acquireWakeLock(applicationContext)
        if (alarm != null) {
            ActiveAlarmController.ringingAlarm = alarm
        }

        val channelId = "chort_wakeiq_alarm_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val chan = NotificationChannel(
                channelId,
                "WakeIQ Alarms",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "WakeIQ gentle smart alarms"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            nm.createNotificationChannel(chan)
        }

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("trigger_alarm_id", alarmId)
        }
        val fullScreenPi = PendingIntent.getActivity(
            this,
            9001,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissIntent = Intent(this, WakeIQAlarmService::class.java).apply {
            this.action = "ACTION_DISMISS"
        }
        val dismissPi = PendingIntent.getService(
            this,
            9002,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarm?.label ?: "WakeIQ Alarm")
            .setContentText("${alarm?.timeFormatted.orEmpty()} • ${alarm?.soundTitle ?: "Waking up gently..."}")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreenPi)
            .setFullScreenIntent(fullScreenPi, true)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Dismiss",
                    dismissPi,
                ).build(),
            )
            .build()

        runCatching {
            startForeground(9001, notification)
        }

        // Start fallback chime if Melodic Chime is selected (or while MainActivity boots up)
        if (alarm == null || alarm.soundType == AlarmSoundType.MELODIC_CHIME) {
            ActiveAlarmController.startFallbackChime(applicationContext)
        }

        runCatching {
            startActivity(fullScreenIntent)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        ActiveAlarmController.stopFallbackChime()
        ActiveAlarmController.releaseWakeLock()
        super.onDestroy()
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appCtx = context.applicationContext
        val repo = ClockRepository(appCtx)
        val action = intent?.action.orEmpty()

        // Reschedule all enabled alarms after phone reboot, locked boot, or time/timezone change
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == "android.app.action.SCHEDULE_EXACT_ALARM_STATE_CHANGED"
        ) {
            repo.rescheduleAllEnabledAlarms()
            return
        }

        val alarmId = intent?.getStringExtra("alarm_id") ?: return
        val alarm = repo.findAlarm(alarmId) ?: return
        ActiveAlarmController.acquireWakeLock(appCtx)
        ActiveAlarmController.ringingAlarm = alarm

        // If recurring, immediately schedule the next occurrence; if one-shot, mark disabled
        if (alarm.daysOfWeek.isNotEmpty()) {
            repo.scheduleNextRecurring(alarm)
        } else {
            repo.updateAlarm(alarm.copy(enabled = false))
        }

        val serviceIntent = Intent(appCtx, WakeIQAlarmService::class.java).apply {
            putExtra("alarm_id", alarmId)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                appCtx.startForegroundService(serviceIntent)
            } else {
                appCtx.startService(serviceIntent)
            }
        }
    }
}

class ClockRepository(context: Context) {
    private val appContext = context.applicationContext

    /**
     * Uses Device-Protected Storage on API 24+ so alarms can be read and rescheduled
     * immediately on LOCKED_BOOT_COMPLETED after a phone reboot, even before unlock!
     */
    private val storageContext: Context = if (Build.VERSION.SDK_INT >= 24) {
        appContext.createDeviceProtectedStorageContext()
    } else {
        appContext
    }

    private val prefs = storageContext.getSharedPreferences("chort_clock_prefs", Context.MODE_PRIVATE)

    private val _selectedCityIds = MutableStateFlow<List<String>>(loadSelectedCityIds())
    val selectedCityIds: StateFlow<List<String>> = _selectedCityIds.asStateFlow()

    private val _alarms = MutableStateFlow<List<WakeAlarm>>(loadAlarms())
    val alarms: StateFlow<List<WakeAlarm>> = _alarms.asStateFlow()

    private val _weatherReport = MutableStateFlow<WeatherReport>(loadCachedWeather())
    val weatherReport: StateFlow<WeatherReport> = _weatherReport.asStateFlow()

    init {
        rescheduleAllEnabledAlarms()
    }

    private fun loadSelectedCityIds(): List<String> {
        val raw = prefs.getString("world_cities", null)
        if (raw.isNullOrBlank()) {
            return listOf("tunis", "makkah", "istanbul", "paris", "new_york", "tokyo")
        }
        return raw.split(",").filter { it.isNotBlank() }
    }

    private fun saveSelectedCityIds(ids: List<String>) {
        prefs.edit().putString("world_cities", ids.joinToString(",")).apply()
    }

    fun worldCities(): List<WorldCity> {
        val ids = _selectedCityIds.value
        return ids.mapNotNull { id -> ALL_WORLD_CITIES.find { it.id == id } }
    }

    fun toggleWorldCity(cityId: String) {
        val current = _selectedCityIds.value.toMutableList()
        if (cityId in current) {
            if (current.size > 1) current.remove(cityId)
        } else {
            current.add(cityId)
        }
        _selectedCityIds.value = current
        saveSelectedCityIds(current)
    }

    fun findAlarm(id: String): WakeAlarm? = _alarms.value.find { it.id == id }

    fun rescheduleAllEnabledAlarms() {
        _alarms.value.filter { it.enabled }.forEach { alarm ->
            scheduleSystemAlarm(alarm)
        }
    }

    fun scheduleNextRecurring(alarm: WakeAlarm) {
        scheduleSystemAlarm(alarm, minDelayMs = 65_000L)
    }

    fun createAlarm(): WakeAlarm {
        val cal = Calendar.getInstance()
        val nextHour = (cal.get(Calendar.HOUR_OF_DAY) + 1) % 24
        val newAlarm = WakeAlarm(
            id = "alarm_${System.currentTimeMillis()}",
            hour = nextHour,
            minute = 0,
            enabled = true,
            label = "WakeIQ Alarm",
            smartWindowMinutes = 15,
            rampDurationMinutes = 5,
        )
        val updated = _alarms.value + newAlarm
        _alarms.value = updated
        saveAlarms(updated)
        scheduleSystemAlarm(newAlarm)
        return newAlarm
    }

    fun updateAlarm(alarm: WakeAlarm) {
        val updated = _alarms.value.map { if (it.id == alarm.id) alarm else it }
        _alarms.value = updated
        saveAlarms(updated)
        if (alarm.enabled) {
            scheduleSystemAlarm(alarm)
        } else {
            cancelSystemAlarm(alarm.id)
        }
    }

    fun deleteAlarm(alarmId: String) {
        cancelSystemAlarm(alarmId)
        val updated = _alarms.value.filterNot { it.id == alarmId }
        _alarms.value = updated
        saveAlarms(updated)
    }

    fun triggerAlarmNow(alarm: WakeAlarm) {
        ActiveAlarmController.acquireWakeLock(appContext)
        ActiveAlarmController.ringingAlarm = alarm
        val serviceIntent = Intent(appContext, WakeIQAlarmService::class.java).apply {
            putExtra("alarm_id", alarm.id)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                appContext.startForegroundService(serviceIntent)
            } else {
                appContext.startService(serviceIntent)
            }
        }
    }

    fun snoozeAlarm(alarm: WakeAlarm, minutes: Int = 9) {
        ActiveAlarmController.dismiss(appContext)
        val cal = Calendar.getInstance().apply {
            add(Calendar.MINUTE, minutes)
        }
        val snoozed = alarm.copy(
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            enabled = true,
        )
        scheduleSystemAlarm(snoozed, overrideTriggerMs = System.currentTimeMillis() + minutes * 60_000L)
    }

    fun nextTriggerMillis(alarm: WakeAlarm, minDelayMs: Long = 1000L): Long {
        val nowMs = System.currentTimeMillis() + minDelayMs
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (alarm.daysOfWeek.isEmpty()) {
            while (target.timeInMillis <= nowMs) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            val smartMs = alarm.smartWindowMinutes * 60_000L
            val smartStart = target.timeInMillis - smartMs
            return if (smartStart > nowMs + 10_000L) smartStart else target.timeInMillis
        }

        for (offset in 0..8) {
            val candidate = (target.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val dow = when (candidate.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }
            if (dow in alarm.daysOfWeek && candidate.timeInMillis > nowMs) {
                val smartMs = alarm.smartWindowMinutes * 60_000L
                val smartStart = candidate.timeInMillis - smartMs
                return if (smartStart > nowMs + 10_000L) smartStart else candidate.timeInMillis
            }
        }
        return target.timeInMillis + 24 * 3600_000L
    }

    private fun scheduleSystemAlarm(
        alarm: WakeAlarm,
        overrideTriggerMs: Long? = null,
        minDelayMs: Long = 1000L,
    ) {
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAt = overrideTriggerMs ?: nextTriggerMillis(alarm, minDelayMs)
        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            putExtra("alarm_id", alarm.id)
        }
        val pi = PendingIntent.getBroadcast(
            appContext,
            alarm.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val showIntent = Intent(appContext, MainActivity::class.java)
        val showPi = PendingIntent.getActivity(
            appContext,
            alarm.id.hashCode() + 1,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showPi), pi)
        }.onFailure {
            runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
        }
    }

    private fun cancelSystemAlarm(alarmId: String) {
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appContext, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            appContext,
            alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching { am.cancel(pi) }
    }

    private fun saveAlarms(list: List<WakeAlarm>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject().apply {
                    put("id", a.id)
                    put("hour", a.hour)
                    put("minute", a.minute)
                    put("enabled", a.enabled)
                    put("label", a.label)
                    put("days", JSONArray(a.daysOfWeek.toList()))
                    put("smartWindowMinutes", a.smartWindowMinutes)
                    put("rampDurationMinutes", a.rampDurationMinutes)
                    put("soundType", a.soundType.name)
                    put("soundId", a.soundId)
                    put("soundTitle", a.soundTitle)
                    put("soundSubtitle", a.soundSubtitle)
                },
            )
        }
        prefs.edit().putString("wake_alarms_json", arr.toString()).apply()
    }

    private fun loadAlarms(): List<WakeAlarm> = runCatching {
        val raw = prefs.getString("wake_alarms_json", null)
        if (raw.isNullOrBlank()) {
            return listOf(
                WakeAlarm(
                    id = "default_morning",
                    hour = 7,
                    minute = 0,
                    enabled = false,
                    label = "Morning WakeIQ",
                    daysOfWeek = setOf(1, 2, 3, 4, 5),
                    smartWindowMinutes = 15,
                    rampDurationMinutes = 5,
                ),
            )
        }
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val daysArr = o.optJSONArray("days") ?: JSONArray()
                val days = mutableSetOf<Int>()
                for (j in 0 until daysArr.length()) days.add(daysArr.optInt(j))
                val sType = AlarmSoundType.entries.find { it.name == o.optString("soundType") }
                    ?: AlarmSoundType.MELODIC_CHIME
                add(
                    WakeAlarm(
                        id = o.optString("id"),
                        hour = o.optInt("hour", 7),
                        minute = o.optInt("minute", 0),
                        enabled = o.optBoolean("enabled", false),
                        label = o.optString("label", "Alarm"),
                        daysOfWeek = days,
                        smartWindowMinutes = o.optInt("smartWindowMinutes", 15),
                        rampDurationMinutes = o.optInt("rampDurationMinutes", 5),
                        soundType = sType,
                        soundId = o.optString("soundId", ""),
                        soundTitle = o.optString("soundTitle", "Gentle Morning Chime"),
                        soundSubtitle = o.optString("soundSubtitle", "WakeIQ Default"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun defaultWeather(): WeatherReport {
        val primary = worldCities().firstOrNull() ?: ALL_WORLD_CITIES.first()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val cond = if (hour in 6..18) WeatherCondition.SUNNY else WeatherCondition.CLEAR_NIGHT
        return WeatherReport(
            cityName = primary.name,
            cityNameArabic = primary.nameArabic,
            temperatureC = 22,
            highC = 26,
            lowC = 16,
            condition = cond,
        )
    }

    private fun loadCachedWeather(): WeatherReport = runCatching {
        val raw = prefs.getString("cached_weather_json", null) ?: return defaultWeather()
        val o = JSONObject(raw)
        val condName = o.optString("condition", WeatherCondition.SUNNY.name)
        val cond = WeatherCondition.entries.find { it.name == condName } ?: WeatherCondition.SUNNY
        WeatherReport(
            cityName = o.optString("cityName", "Tunis"),
            cityNameArabic = o.optString("cityNameArabic", o.optString("cityName", "تونس")),
            temperatureC = o.optInt("temperatureC", 22),
            highC = o.optInt("highC", 26),
            lowC = o.optInt("lowC", 16),
            condition = cond,
        )
    }.getOrDefault(defaultWeather())

    private fun saveCachedWeather(report: WeatherReport) {
        runCatching {
            val o = JSONObject().apply {
                put("cityName", report.cityName)
                put("cityNameArabic", report.cityNameArabic)
                put("temperatureC", report.temperatureC)
                put("highC", report.highC)
                put("lowC", report.lowC)
                put("condition", report.condition.name)
            }
            prefs.edit().putString("cached_weather_json", o.toString()).apply()
        }
    }

    private fun resolveGpsOrPrimaryCoordinates(): Triple<Double, Double, Pair<String, String>> {
        val primary = worldCities().firstOrNull() ?: ALL_WORLD_CITIES.first()
        runCatching {
            val hasFine = appContext.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarse = appContext.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasFine || hasCoarse) {
                val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                if (lm != null) {
                    val providers = listOf(
                        android.location.LocationManager.GPS_PROVIDER,
                        android.location.LocationManager.NETWORK_PROVIDER,
                        android.location.LocationManager.PASSIVE_PROVIDER,
                    )
                    val loc = providers.mapNotNull { p ->
                        runCatching { lm.getLastKnownLocation(p) }.getOrNull()
                    }.maxByOrNull { it.time }
                    if (loc != null) {
                        // Match closest world city or reverse geocode
                        val closest = ALL_WORLD_CITIES.minByOrNull { c ->
                            val dLat = c.latitude - loc.latitude
                            val dLon = c.longitude - loc.longitude
                            dLat * dLat + dLon * dLon
                        }
                        val distSq = closest?.let {
                            val dLat = it.latitude - loc.latitude
                            val dLon = it.longitude - loc.longitude
                            dLat * dLat + dLon * dLon
                        } ?: Double.MAX_VALUE
                        val names = if (closest != null && distSq < 4.0) {
                            closest.name to closest.nameArabic
                        } else {
                            val geocoded = runCatching {
                                @Suppress("DEPRECATION")
                                android.location.Geocoder(appContext, java.util.Locale.ENGLISH)
                                    .getFromLocation(loc.latitude, loc.longitude, 1)
                                    ?.firstOrNull()?.locality
                            }.getOrNull()
                            val geocodedAr = runCatching {
                                @Suppress("DEPRECATION")
                                android.location.Geocoder(appContext, java.util.Locale("ar"))
                                    .getFromLocation(loc.latitude, loc.longitude, 1)
                                    ?.firstOrNull()?.locality
                            }.getOrNull()
                            (geocoded ?: primary.name) to (geocodedAr ?: geocoded ?: primary.nameArabic)
                        }
                        return Triple(loc.latitude, loc.longitude, names)
                    }
                }
            }
        }
        return Triple(primary.latitude, primary.longitude, primary.name to primary.nameArabic)
    }

    suspend fun refreshWeather() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val (lat, lon, names) = resolveGpsOrPrimaryCoordinates()
                val urlStr = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=auto"
                val conn = (java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    requestMethod = "GET"
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(body)
                    val current = root.optJSONObject("current")
                    val daily = root.optJSONObject("daily")
                    val temp = current?.optDouble("temperature_2m", 22.0)?.let { kotlin.math.round(it).toInt() } ?: 22
                    val code = current?.optInt("weather_code", 0) ?: 0
                    val isDay = (current?.optInt("is_day", 1) ?: 1) == 1
                    val maxArr = daily?.optJSONArray("temperature_2m_max")
                    val minArr = daily?.optJSONArray("temperature_2m_min")
                    val high = maxArr?.optDouble(0, (temp + 3).toDouble())?.let { kotlin.math.round(it).toInt() } ?: (temp + 3)
                    val low = minArr?.optDouble(0, (temp - 4).toDouble())?.let { kotlin.math.round(it).toInt() } ?: (temp - 4)
                    val cond = when (code) {
                        0 -> if (isDay) WeatherCondition.SUNNY else WeatherCondition.CLEAR_NIGHT
                        1, 2 -> WeatherCondition.PARTLY_CLOUDY
                        3, 45, 48 -> WeatherCondition.CLOUDY
                        in 51..67, in 80..82 -> WeatherCondition.RAINY
                        in 71..77, 85, 86 -> WeatherCondition.SNOWY
                        in 95..99 -> WeatherCondition.STORM
                        else -> if (isDay) WeatherCondition.SUNNY else WeatherCondition.CLEAR_NIGHT
                    }
                    val report = WeatherReport(
                        cityName = names.first,
                        cityNameArabic = names.second,
                        temperatureC = temp,
                        highC = maxOf(high, temp),
                        lowC = minOf(low, temp),
                        condition = cond,
                    )
                    _weatherReport.value = report
                    saveCachedWeather(report)
                }
                conn.disconnect()
            }
        }
    }
}
