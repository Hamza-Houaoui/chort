package app.podium.core.designsystem.l10n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/** Strongly-typed localized strings for Podium / CHORT. */
interface AppStrings {
    // App & Shell
    val appName: String get() = "CHORT"
    val back: String
    val cancel: String
    val done: String
    val save: String
    val edit: String
    val delete: String
    val ok: String
    val close: String
    val search: String
    val play: String
    val pause: String
    val playing: String
    val paused: String
    val on: String
    val off: String

    // Main Menu / Home
    val music: String
    val online: String
    val radio: String
    val podcasts: String
    val quran: String
    val soundCloud: String get() = "SoundCloud"
    val youTube: String get() = "YouTube"
    val gallery: String
    val books: String
    val clock: String
    val insights: String
    val listenTogether: String
    val voiceMemos: String
    val lifestyle: String
    val tools: String
    val shuffleSongs: String
    val nowPlaying: String
    val settings: String

    // Library / Music
    val coverFlow: String
    val albums: String
    val artists: String
    val songs: String
    val favorites: String
    val playlists: String
    val downloads: String
    val genres: String
    val compilations: String
    val composers: String
    val addSongs: String
    val newPlaylist: String
    val renamePlaylist: String
    val deletePlaylist: String
    val playAll: String
    val playNext: String
    val addToQueue: String
    val noSongsFound: String
    val noFavoritesYet: String
    val noPlaylists: String
    fun songsCount(count: Int): String
    fun albumsCount(count: Int): String

    // Now Playing / Up Next / Lyrics
    val upNext: String
    val lyrics: String
    val noLyrics: String
    val loadingLyrics: String
    val repeat: String
    val repeatOff: String
    val repeatAll: String
    val repeatOne: String
    val shuffle: String
    val shuffleOn: String
    val shuffleOff: String

    // Settings Menu
    val language: String
    val systemDefault: String
    val appearance: String
    val deviceBody: String
    val fullScreen: String
    val batteryInTitleBar: String
    val nowPlayingLayout: String
    val hideNowPlayingTray: String
    val keyboard: String
    val musicFolders: String
    val onlineSources: String
    val customSources: String
    val smartDownloads: String
    val autoplay: String
    val recommendations: String
    val avoidRepeats: String
    val crossfade: String
    val equalizer: String
    val sleepTimer: String
    val onlineLyrics: String
    val lyricsCredit: String
    val haptics: String
    val clicks: String
    val startupSound: String
    val softwareUpdate: String

    // Settings Values / Options
    val shown: String
    val hidden: String
    val fullScreenOnly: String
    val nostalgic: String
    val nowPlayingArch: String
    val modern: String
    val hidesWhenIdle: String
    val alwaysShown: String
    val allFolders: String
    fun allOfFolder(name: String): String
    val checkNow: String
    val automaticChecks: String

    // Appearance
    val virtualDisplay: String
    val appearanceMode: String get() = "Mode"
    val menuGradient: String get() = "Menu Gradient"
    val menuGradientColor: String get() = "Gradient Color"
    val subMenuBlurPreview: String get() = "Sub-Menu Preview"
    val breadcrumbArch: String get() = "Back Arch"
    val showHeaderTitle: String get() = "Header Title"
    val showStatusPill: String get() = "Status Pill"
    val theme: String
    val font: String
    val lyricsFont: String
    val lyricsTheme: String
    val lyricsBackground: String
    val background: String
    val finish: String
    val customColor: String
    val backgroundColor: String
    val grain: String
    val glitter: String
    val contrast: String
    val glitterAmount: String get() = "Glitter amount"
    val glitterGlow: String get() = "Glitter glow"
    val followTilt: String get() = "Follow tilt"
    val glitterDensity: String get() = "Glitter density"
    val glitterSize: String get() = "Glitter size"
    val glitterOpacity: String get() = "Glitter opacity"
    val backgroundOpacity: String get() = "Background opacity"
    val preset: String get() = "Preset"
    val soundEffect: String get() = "Effect"
    val solidFinishesOnly: String get() = "Solid finishes only"
    val unavailable: String get() = "Unavailable"
    val backgroundsNotice: String get() = "Backgrounds come with Glass and Custom"
    val standardContrast: String get() = "Standard"
    val highContrast: String get() = "High contrast"
    val chooseScanFolders: String get() = "Choose Scan Folders"
    val importBookFile: String get() = "Import Book File"
    val telegramSync: String get() = "Telegram Sync"
    val connected: String get() = "Connected"
    val setup: String get() = "Setup"
    val noFoldersToChoose: String get() = "No folders to choose yet"
    val allowMusicAccessNotice: String get() = "Allow music access from Home, then come back."

    // Listen Together & Network
    val sessionCode: String
    val hostAddress: String
    val joining: String
    val hostSession: String
    val stations: String
    val startSession: String get() = hostSession
    val joinWithAddress: String get() = hostAddress
    val nearby: String get() = "Nearby"
    val join: String get() = "Join"
    val endSession: String get() = "End session"
    val guestsCanControl: String get() = "Guests can control"
    val searchingWifi: String get() = "Searching your Wi-Fi or hotspot for nearby sessions…"

    // Voice Memos
    val record: String
    val resume: String
    val recording: String
    val stopAndSave: String
    val saveName: String
    val rename: String

    // Local Metadata Editor
    val editSong: String
    val editAlbum: String
    val editArtist: String

    // Downloads
    val findSongsOnline: String
    val removeAllDownloads: String
    val retryFailedDownloads: String

    // Holy Quran
    val continueReading: String
    val reciters: String
    val tafsirAndTranslation: String
    val searchQuran: String
    val bookmarks: String

    // Clock
    val worldClock: String
    val alarms: String
    val stopwatch: String
    val timer: String

    // Gallery
    val photos: String
    val videos: String
    val folders: String
    val trash: String

    // Podcasts
    val topPodcasts: String
    val searchPodcasts: String
    val subscriptions: String
    val categories: String
    val addByRss: String
    val podcastSettings: String
    val recentEpisodes: String

    // Books
    val allBooks: String
    val islamicBooks: String
    val arabicBooks: String

    // Sub-menus & Actions Localization
    val downloadAll: String get() = "Download all"
    val download: String get() = "Download"
    val downloadAlbum: String get() = "Download album"
    val playPopularSongs: String get() = "Play popular songs"
    val downloadPopularSongs: String get() = "Download popular songs"
    val addAllToPlaylist: String get() = "Add all to a playlist"
    val addToPlaylist: String get() = "Add to playlist"
    val moreSongs: String get() = "More songs"
    val createPlaylist: String get() = "Create playlist"
    val clearHistory: String get() = "Clear history"
    val likedSongs: String get() = "Liked songs"
    val onThisPhone: String get() = "On this phone"
    val playedOnPodium: String get() = "Played on CHORT"
    val like: String get() = "Like"
    val removeFromLiked: String get() = "Remove from liked songs"
    val removeDownload: String get() = "Remove download"
    val cancelDownload: String get() = "Cancel download"
    val startRadio: String get() = "Start radio"
    val goToAlbum: String get() = "Go to album"
    val share: String get() = "Share"
    val subscribe: String get() = "Subscribe"
    val subscribed: String get() = "Subscribed"
    val history: String get() = "History"
    val library: String get() = "Library"

    val choose: String get() = "Choose"
    val none: String get() = "None"
    val solid: String get() = "Solid"
    val picture: String get() = "Picture"
    val choosePicture: String get() = "Choose a picture"
    val chooseAnotherPicture: String get() = "Choose another picture"
    val useThisColor: String get() = "Use this color"
    val hexColorCode: String get() = "Hex color code"
    val wheelAdjustNotice: String get() = "Turn the wheel to adjust. Press the center to keep it."

    val eqFlat: String get() = "Flat"
    val eqBass: String get() = "Bass boost"
    val eqBassCut: String get() = "Less bass"
    val eqTreble: String get() = "Treble boost"
    val eqVocal: String get() = "Vocal"
    val eqRock: String get() = "Rock"
    val eqPop: String get() = "Pop"
    val eqJazz: String get() = "Jazz"
    val eqClassical: String get() = "Classical"
    val eqElectronic: String get() = "Electronic"
    val eqHipHop: String get() = "Hip-hop"
    val eqAcoustic: String get() = "Acoustic"
    val eqQuiet: String get() = "Late night"
    val eqCustom: String get() = "Custom"

    val fxNone: String get() = "None"
    val fxCar: String get() = "Car stereo"
    val fxHall: String get() = "3D hall"
    val fxOrbit: String get() = "8D"

    val finishGlass: String get() = "Glass"
    val finishSteelGray: String get() = "Steel gray"
    val finishBurgundy: String get() = "Burgundy"
    val finishGlacierBlue: String get() = "Glacier blue"
    val finishSilver: String get() = "Silver"
    val finishCustom: String get() = "Custom color"

    val themeGlass: String get() = "Glass"
    val themeCarbon: String get() = "Carbon"
    val themeCustom: String get() = "Custom"

    val searchStations: String get() = "Search stations"
    val addCustomStation: String get() = "Add custom station"

    val resyncWithHost: String get() = "Resync with host"
    val playOnThisDevice: String get() = "Play on this CHORT"
    val leaveSession: String get() = "Leave session"
    val skipToNextSong: String get() = "Skip to next song"
    val previousSong: String get() = "Previous song"
    val addSongsToSession: String get() = "Add songs to session"
    val findSongsToPlay: String get() = "Find songs to play"
    val pauseSession: String get() = "Pause session"
    val resumeSession: String get() = "Resume session"

    val finishBlackenedSteel: String get() = "Blackened steel"
    val finishMatteBlack: String get() = "Matte black"

    val lyricsClassic: String get() = "Classic"
    val lyricsContinuousLine: String get() = "Continuous line"
    val lyricsScrolling: String get() = "Scrolling"

    val lyricsAutomatic: String get() = "Automatic"
    val lyricsBlack: String get() = "Black"
    val lyricsWhite: String get() = "White"

    val lyricsChoiceAutomatic: String get() = "Automatic: turns with each line"
    val lyricsChoiceBlack: String get() = "Always black"
    val lyricsChoiceWhite: String get() = "Always white"

    val hapticBalanced: String get() = "Balanced"
    val hapticTactile: String get() = "More tactile"
    val hapticReduced: String get() = "Reduced"
    val hapticSystem: String get() = "System default"

    fun hapticsTitle(profileLabel: String): String = "Haptics: $profileLabel"
    fun secondsCount(n: Int): String = if (n == 1) "1 second" else "$n seconds"

    val equalizerBandNotice: String get() = "Turn the wheel to boost or cut. Press the center when it sounds right."
    val hexDigitsNotice: String get() = "Use six hex digits, like 2F6F5E."
    fun colorChannelNotice(channelName: String): String = "Turn the Wheel to change $channelName. Skip forward or back for the next bar."

    val colorHue: String get() = "hue"
    val colorSaturation: String get() = "saturation"
    val colorBrightness: String get() = "brightness"

    val onlineMusic: String get() = "Online music"
    val account: String get() = "Account"
    val channel: String get() = "Channel"
    val signIn: String get() = "Sign in"
    val signInAgain: String get() = "Sign in again"
    val signingIn: String get() = "Signing in"
    val switching: String get() = "Switching"
    val switchChannel: String get() = "Switch channel"
    val switchAccount: String get() = "Switch account"
    val signOut: String get() = "Sign out"
    val staySignedIn: String get() = "Stay signed in"
    val installed: String get() = "Installed"
    val install: String get() = "Install"
    val mediaControls: String get() = "Media controls"
    val allowed: String get() = "Allowed"
    val allow: String get() = "Allow"
    val afterChoosingSong: String get() = "After choosing a song"
    val stayHere: String get() = "Stay here"
    val addSource: String get() = "Add a source"
    val useThisSource: String get() = "Use this source"
    val moveUp: String get() = "Move up"
    val moveDown: String get() = "Move down"
    val answering: String get() = "Answering"
    val notAnswering: String get() = "Not answering"
    val checking: String get() = "Checking"
    val address: String get() = "Address"
    val remove: String get() = "Remove"
    val keepIt: String get() = "Keep it"
    val paste: String get() = "Paste"
    val source: String get() = "Source"
    val connect: String get() = "Connect"
    val sourceAddress: String get() = "Source address"

    val signInToSoundCloud: String get() = "Sign in to SoundCloud"
    val stream: String get() = "Stream"
    val likes: String get() = "Likes"
    val topCharts: String get() = "Top 50 Charts"
    val searchSoundCloud: String get() = "Search SoundCloud"
    val liveRadio: String get() = "Live radio"
    val explore: String get() = "Explore"
    val home: String get() = "Home"

    fun lyricsThemeLabel(theme: app.podium.core.designsystem.theme.LyricsTheme): String = when (theme) {
        app.podium.core.designsystem.theme.LyricsTheme.CLASSIC -> lyricsClassic
        app.podium.core.designsystem.theme.LyricsTheme.CONTINUOUS_LINE -> lyricsContinuousLine
        app.podium.core.designsystem.theme.LyricsTheme.SCROLLING -> lyricsScrolling
    }

    fun lyricsBackgroundLabel(bg: app.podium.core.designsystem.theme.LyricsBackground): String = when (bg) {
        app.podium.core.designsystem.theme.LyricsBackground.AUTOMATIC -> lyricsAutomatic
        app.podium.core.designsystem.theme.LyricsBackground.BLACK -> lyricsBlack
        app.podium.core.designsystem.theme.LyricsBackground.WHITE -> lyricsWhite
    }

    fun lyricsBackgroundChoice(choice: app.podium.core.designsystem.theme.LyricsBackground): String = when (choice) {
        app.podium.core.designsystem.theme.LyricsBackground.AUTOMATIC -> lyricsChoiceAutomatic
        app.podium.core.designsystem.theme.LyricsBackground.BLACK -> lyricsChoiceBlack
        app.podium.core.designsystem.theme.LyricsBackground.WHITE -> lyricsChoiceWhite
    }

    fun displayBackgroundLabel(bg: app.podium.core.designsystem.theme.DisplayBackground): String = when (bg) {
        app.podium.core.designsystem.theme.DisplayBackground.NONE -> none
        app.podium.core.designsystem.theme.DisplayBackground.SOLID -> solid
        app.podium.core.designsystem.theme.DisplayBackground.IMAGE -> picture
    }

    fun hapticProfileLabel(profile: app.podium.core.interaction.HapticProfile): String = when (profile) {
        app.podium.core.interaction.HapticProfile.BALANCED -> hapticBalanced
        app.podium.core.interaction.HapticProfile.TACTILE -> hapticTactile
        app.podium.core.interaction.HapticProfile.REDUCED -> hapticReduced
        app.podium.core.interaction.HapticProfile.SYSTEM -> hapticSystem
        app.podium.core.interaction.HapticProfile.OFF -> off
    }

    fun finishPresetLabel(preset: app.podium.core.designsystem.shell.FinishPreset): String = when (preset) {
        app.podium.core.designsystem.shell.FinishPreset.GLASS -> finishGlass
        app.podium.core.designsystem.shell.FinishPreset.STEEL_GRAY -> finishSteelGray
        app.podium.core.designsystem.shell.FinishPreset.BURGUNDY -> finishBurgundy
        app.podium.core.designsystem.shell.FinishPreset.GLACIER_BLUE -> finishGlacierBlue
        app.podium.core.designsystem.shell.FinishPreset.SILVER -> finishSilver
        app.podium.core.designsystem.shell.FinishPreset.CUSTOM -> finishCustom
    }

    fun displayThemeLabel(theme: app.podium.core.designsystem.theme.DisplayTheme): String = when (theme) {
        app.podium.core.designsystem.theme.DisplayTheme.GLASS -> themeGlass
        app.podium.core.designsystem.theme.DisplayTheme.CARBON -> themeCarbon
        app.podium.core.designsystem.theme.DisplayTheme.CUSTOM -> themeCustom
    }
}

object EnglishStrings : AppStrings {
    override val back = "Back"
    override val cancel = "Cancel"
    override val done = "Done"
    override val save = "Save"
    override val edit = "Edit"
    override val delete = "Delete"
    override val ok = "OK"
    override val close = "Close"
    override val search = "Search"
    override val play = "Play"
    override val pause = "Pause"
    override val playing = "Playing"
    override val paused = "Paused"
    override val on = "On"
    override val off = "Off"

    override val music = "Music"
    override val online = "YT Music"
    override val radio = "Radio"
    override val podcasts = "Podcasts"
    override val quran = "Quran"
    override val gallery = "Gallery"
    override val books = "Books"
    override val clock = "Clock"
    override val insights = "Insights"
    override val listenTogether = "Listen together"
    override val voiceMemos = "Voice Memos"
    override val lifestyle = "Lifestyle"
    override val tools = "Tools"
    override val shuffleSongs = "Shuffle songs"
    override val nowPlaying = "Now Playing"
    override val settings = "Settings"

    override val coverFlow = "Cover Flow"
    override val albums = "Albums"
    override val artists = "Artists"
    override val songs = "Songs"
    override val favorites = "Favorites"
    override val playlists = "Playlists"
    override val downloads = "Downloads"
    override val genres = "Genres"
    override val compilations = "Compilations"
    override val composers = "Composers"
    override val addSongs = "Add songs"
    override val newPlaylist = "New playlist"
    override val renamePlaylist = "Rename"
    override val deletePlaylist = "Delete playlist"
    override val playAll = "Play all"
    override val playNext = "Play next"
    override val addToQueue = "Add to Up Next"
    override val noSongsFound = "No songs found"
    override val noFavoritesYet = "No favorites yet"
    override val noPlaylists = "No playlists"
    override fun songsCount(count: Int) = if (count == 1) "1 song" else "$count songs"
    override fun albumsCount(count: Int) = if (count == 1) "1 album" else "$count albums"

    override val upNext = "Up Next"
    override val lyrics = "Lyrics"
    override val noLyrics = "No lyrics available"
    override val loadingLyrics = "Looking for lyrics..."
    override val repeat = "Repeat"
    override val repeatOff = "Off"
    override val repeatAll = "All"
    override val repeatOne = "One"
    override val shuffle = "Shuffle"
    override val shuffleOn = "On"
    override val shuffleOff = "Off"

    override val language = "Language"
    override val systemDefault = "System default"
    override val appearance = "Appearance"
    override val deviceBody = "CHORT ART"
    override val fullScreen = "Full screen"
    override val batteryInTitleBar = "Battery in title bar"
    override val nowPlayingLayout = "Now Playing"
    override val hideNowPlayingTray = "Now Playing's tray"
    override val keyboard = "Keyboard"
    override val musicFolders = "Music folders"
    override val onlineSources = "Online music"
    override val customSources = "Custom sources"
    override val smartDownloads = "Smart downloads"
    override val autoplay = "Autoplay"
    override val recommendations = "Online recommendations"
    override val avoidRepeats = "Avoid repeats"
    override val crossfade = "Crossfade"
    override val equalizer = "Equaliser"
    override val sleepTimer = "Sleep timer"
    override val onlineLyrics = "Online lyrics"
    override val lyricsCredit = "Lyrics from LRCLIB, written by its community"
    override val haptics = "Haptics"
    override val clicks = "Click sound"
    override val startupSound = "Startup sound"
    override val softwareUpdate = "Software update"

    override val shown = "Shown"
    override val hidden = "Hidden"
    override val fullScreenOnly = "Full screen only"
    override val nostalgic = "Nostalgic"
    override val nowPlayingArch = "Arch"
    override val modern = "New"
    override val hidesWhenIdle = "Hides when idle"
    override val alwaysShown = "Always shown"
    override val allFolders = "All folders"
    override fun allOfFolder(name: String) = "All of $name"
    override val checkNow = "Check now"
    override val automaticChecks = "Automatic checks"

    override val virtualDisplay = "Virtual display"
    override val theme = "Theme"
    override val font = "Font"
    override val lyricsFont = "Lyrics font"
    override val lyricsTheme = "Lyrics theme"
    override val lyricsBackground = "Lyrics background"
    override val background = "Background"
    override val finish = "Finish"
    override val customColor = "Custom color"
    override val backgroundColor = "Background color"
    override val grain = "Grain"
    override val glitter = "Glitter"
    override val contrast = "Contrast"

    override val sessionCode = "Session code"
    override val hostAddress = "Host address"
    override val joining = "Joining..."
    override val hostSession = "Host session"
    override val stations = "Stations"

    override val record = "Record"
    override val resume = "Resume"
    override val recording = "Recording..."
    override val stopAndSave = "Stop & save"
    override val saveName = "Save name"
    override val rename = "Rename"

    override val editSong = "Edit song"
    override val editAlbum = "Edit album"
    override val editArtist = "Edit artist"

    override val findSongsOnline = "Find songs online"
    override val removeAllDownloads = "Remove all downloads"
    override val retryFailedDownloads = "Retry failed downloads"

    override val continueReading = "Continue Reading"
    override val reciters = "Reciters"
    override val tafsirAndTranslation = "Translation & Tafsir"
    override val searchQuran = "Search Quran"
    override val bookmarks = "Bookmarks"

    override val worldClock = "World Clock"
    override val alarms = "Alarms (WakeIQ)"
    override val stopwatch = "Stopwatch"
    override val timer = "Timer"

    override val photos = "Photos"
    override val videos = "Videos"
    override val folders = "Folders"
    override val trash = "Trash"

    override val topPodcasts = "Top podcasts"
    override val searchPodcasts = "Search podcasts"
    override val subscriptions = "My subscriptions"
    override val categories = "Categories"
    override val addByRss = "Add podcast by RSS"
    override val podcastSettings = "Podcast settings"
    override val recentEpisodes = "Recent episodes"

    override val allBooks = "All Books Cover Flow"
    override val islamicBooks = "Islamic Books Library"
    override val arabicBooks = "Arabic Books Library"
}

object ArabicStrings : AppStrings {
    override val back = "رجوع"
    override val cancel = "إلغاء"
    override val done = "تم"
    override val save = "حفظ"
    override val edit = "تعديل"
    override val delete = "حذف"
    override val ok = "حسناً"
    override val close = "إغلاق"
    override val search = "بحث"
    override val play = "تشغيل"
    override val pause = "إيقاف مؤقت"
    override val playing = "قيد التشغيل"
    override val paused = "متوقف مؤقتاً"
    override val on = "مفعل"
    override val off = "معطل"

    override val music = "الموسيقى"
    override val online = "YT Music"
    override val radio = "الراديو"
    override val podcasts = "البودكاست"
    override val quran = "القرآن الكريم"
    override val gallery = "المعرض"
    override val books = "الكتب"
    override val clock = "الساعة"
    override val insights = "الإحصائيات"
    override val listenTogether = "استماع مشترك"
    override val voiceMemos = "مذكرات صوتية"
    override val lifestyle = "نمط الحياة"
    override val tools = "أدوات"
    override val shuffleSongs = "خلط الأغاني"
    override val nowPlaying = "قيد التشغيل"
    override val settings = "الإعدادات"

    override val coverFlow = "Cover Flow"
    override val albums = "الألبومات"
    override val artists = "الفنانون"
    override val songs = "الأغاني"
    override val favorites = "المفضلة"
    override val playlists = "قوائم التشغيل"
    override val downloads = "التنزيلات"
    override val genres = "الأنواع"
    override val compilations = "التجميعات"
    override val composers = "الملحنون"
    override val addSongs = "إضافة أغانٍ"
    override val newPlaylist = "قائمة جديدة"
    override val renamePlaylist = "إعادة تسمية"
    override val deletePlaylist = "حذف قائمة التشغيل"
    override val playAll = "تشغيل الكل"
    override val playNext = "تشغيل التالي"
    override val addToQueue = "إضافة إلى التالي"
    override val noSongsFound = "لا توجد أغانٍ"
    override val noFavoritesYet = "لا توجد مفضلات بعد"
    override val noPlaylists = "لا توجد قوائم تشغيل"
    override fun songsCount(count: Int) = when (count) {
        1 -> "أغنية واحدة"
        2 -> "أغنيتان"
        in 3..10 -> "$count أغانٍ"
        else -> "$count أغنية"
    }
    override fun albumsCount(count: Int) = when (count) {
        1 -> "ألبوم واحد"
        2 -> "ألبومان"
        in 3..10 -> "$count ألبومات"
        else -> "$count ألبوماً"
    }

    override val upNext = "التالي"
    override val lyrics = "كلمات الأغاني"
    override val noLyrics = "لا تتوفر كلمات"
    override val loadingLyrics = "جارٍ البحث عن الكلمات..."
    override val repeat = "تكرار"
    override val repeatOff = "معطل"
    override val repeatAll = "الكل"
    override val repeatOne = "واحدة"
    override val shuffle = "خلط"
    override val shuffleOn = "مفعل"
    override val shuffleOff = "معطل"

    override val language = "اللغة"
    override val systemDefault = "تلقائي (حسب النظام)"
    override val appearance = "المظهر"
    override val deviceBody = "CHORT ART"
    override val fullScreen = "ملء الشاشة"
    override val batteryInTitleBar = "البطارية في شريط العناوين"
    override val nowPlayingLayout = "شاشة التشغيل"
    override val hideNowPlayingTray = "شريط شاشة التشغيل"
    override val keyboard = "لوحة المفاتيح"
    override val musicFolders = "مجلدات الموسيقى"
    override val onlineSources = "الموسيقى عبر الإنترنت"
    override val customSources = "مصادر مخصصة"
    override val smartDownloads = "تنزيلات ذكية"
    override val autoplay = "تشغيل تلقائي"
    override val recommendations = "توصيات عبر الإنترنت"
    override val avoidRepeats = "تجنب التكرار"
    override val crossfade = "تلاشي متداخل"
    override val equalizer = "الموازن الصوتي"
    override val sleepTimer = "مؤقت النوم"
    override val onlineLyrics = "كلمات عبر الإنترنت"
    override val lyricsCredit = "الكلمات مقدمة من مجتمع LRCLIB"
    override val haptics = "الاهتزاز اللمسي"
    override val clicks = "صوت النقر"
    override val startupSound = "صوت بدء التشغيل"
    override val softwareUpdate = "تحديث النظام"

    override val shown = "ظاهر"
    override val hidden = "مخفي"
    override val fullScreenOnly = "ملء الشاشة فقط"
    override val nostalgic = "كلاسيكي"
    override val nowPlayingArch = "قوس"
    override val modern = "حديث"
    override val hidesWhenIdle = "يختفي عند الخمول"
    override val alwaysShown = "ظاهر دائماً"
    override val allFolders = "جميع المجلدات"
    override fun allOfFolder(name: String) = "كل ما في $name"
    override val checkNow = "تحقق الآن"
    override val automaticChecks = "فحص تلقائي"

    override val virtualDisplay = "الشاشة الافتراضية"
    override val appearanceMode = "الوضع (Mode)"
    override val menuGradient = "تدرّج القائمة"
    override val menuGradientColor = "لون التدرّج"
    override val subMenuBlurPreview = "معاينة القوائم الفرعية"
    override val breadcrumbArch = "قوس العودة"
    override val showHeaderTitle = "عنوان الهيدر"
    override val showStatusPill = "شريط الحالة العلوي"
    override val theme = "السمة"
    override val font = "الخط"
    override val lyricsFont = "خط الكلمات"
    override val lyricsTheme = "سمة الكلمات"
    override val lyricsBackground = "خلفية الكلمات"
    override val background = "الخلفية"
    override val finish = "الملمس والمظهر"
    override val customColor = "لون مخصص"
    override val backgroundColor = "لون الخلفية"
    override val grain = "حبيبات"
    override val glitter = "بريق"
    override val contrast = "التباين"
    override val glitterAmount = "كمية البريق"
    override val glitterGlow = "توهج البريق"
    override val followTilt = "تتبع الإمالة"
    override val glitterDensity = "كثافة البريق"
    override val glitterSize = "حجم البريق"
    override val glitterOpacity = "شفافية البريق"
    override val backgroundOpacity = "شفافية الخلفية"
    override val preset = "الوضع المسبق"
    override val soundEffect = "المؤثر الصوتي"
    override val solidFinishesOnly = "للتشطيبات الصلبة فقط"
    override val unavailable = "غير متوفر"
    override val backgroundsNotice = "الخلفيات تتوفر مع الزجاجي والمخصص"
    override val standardContrast = "قياسي"
    override val highContrast = "تباين عالٍ"
    override val chooseScanFolders = "تحديد مجلدات الفحص"
    override val importBookFile = "استيراد ملف كتاب"
    override val telegramSync = "مزامنة تيليجرام"
    override val connected = "متصل"
    override val setup = "إعداد"
    override val noFoldersToChoose = "لا توجد مجلدات متاحة بعد"
    override val allowMusicAccessNotice = "امنح إذن الوصول للموسيقى من الصفحة الرئيسية، ثم عد هنا."

    override val sessionCode = "رمز الجلسة"
    override val hostAddress = "عنوان المضيف"
    override val joining = "جارٍ الانضمام..."
    override val hostSession = "بدء جلسة"
    override val stations = "المحطات"
    override val startSession = "بدء جلسة"
    override val joinWithAddress = "الانضمام عبر عنوان"
    override val nearby = "الجلسات القريبة"
    override val join = "انضمام"
    override val endSession = "إنهاء الجلسة"
    override val guestsCanControl = "تحكم الضيوف"
    override val searchingWifi = "جارٍ البحث في شبكة Wi-Fi عن جلسات قريبة..."

    override val record = "تسجيل"
    override val resume = "استئناف"
    override val recording = "جارٍ التسجيل..."
    override val stopAndSave = "إيقاف وحفظ"
    override val saveName = "حفظ الاسم"
    override val rename = "إعادة تسمية"

    override val editSong = "تعديل الأغنية"
    override val editAlbum = "تعديل الألبوم"
    override val editArtist = "تعديل الفنان"

    override val findSongsOnline = "البحث عن أغانٍ أونلاين"
    override val removeAllDownloads = "حذف جميع التنزيلات"
    override val retryFailedDownloads = "إعادة محاولة التنزيلات الفاشلة"

    override val continueReading = "متابعة القراءة"
    override val reciters = "القرّاء"
    override val tafsirAndTranslation = "التفسير والترجمة"
    override val searchQuran = "البحث في القرآن"
    override val bookmarks = "الإشارات المرجعية"

    override val worldClock = "الساعة العالمية"
    override val alarms = "المنبهات (WakeIQ)"
    override val stopwatch = "ساعة الإيقاف"
    override val timer = "المؤقت"

    override val photos = "الصور"
    override val videos = "الفيديوهات"
    override val folders = "المجلدات"
    override val trash = "سلة المحذوفات"

    override val topPodcasts = "أفضل البرامج"
    override val searchPodcasts = "البحث في البودكاست"
    override val subscriptions = "اشتراكاتي"
    override val categories = "التصنيفات"
    override val addByRss = "إضافة بودكاست عبر RSS"
    override val podcastSettings = "إعدادات البودكاست"
    override val recentEpisodes = "الحلقات الأخيرة"

    override val allBooks = "جميع الكتب Cover Flow"
    override val islamicBooks = "كتب إسلامية"
    override val arabicBooks = "مكتبة عربية"

    override val downloadAll = "تنزيل الكل"
    override val download = "تنزيل"
    override val downloadAlbum = "تنزيل الألبوم"
    override val playPopularSongs = "تشغيل الأغاني الشائعة"
    override val downloadPopularSongs = "تنزيل الأغاني الشائعة"
    override val addAllToPlaylist = "إضافة الكل إلى قائمة تشغيل"
    override val addToPlaylist = "إضافة إلى قائمة تشغيل"
    override val moreSongs = "المزيد من الأغاني"
    override val createPlaylist = "إنشاء قائمة تشغيل"
    override val clearHistory = "مسح السجل"
    override val likedSongs = "الأغاني المفضلة"
    override val onThisPhone = "على هذا الهاتف"
    override val playedOnPodium = "تم تشغيلها على CHORT"
    override val like = "إعجاب"
    override val removeFromLiked = "إزالة من المفضلة"
    override val removeDownload = "حذف التنزيل"
    override val cancelDownload = "إلغاء التنزيل"
    override val startRadio = "تشغيل المحطة"
    override val goToAlbum = "الانتقال إلى الألبوم"
    override val share = "مشاركة"
    override val subscribe = "اشتراك"
    override val subscribed = "مشترك"
    override val history = "السجل"
    override val library = "المكتبة"

    override val choose = "اختيار"
    override val none = "بدون"
    override val solid = "لون ثابت"
    override val picture = "صورة"
    override val choosePicture = "اختيار صورة"
    override val chooseAnotherPicture = "اختيار صورة أخرى"
    override val useThisColor = "استخدام هذا اللون"
    override val hexColorCode = "رمز اللون السداسي"
    override val wheelAdjustNotice = "أدِر العجلة للضبط. اضغط المنتصف للحفظ."

    override val eqFlat = "مستوٍ"
    override val eqBass = "تضخيم الجهير"
    override val eqBassCut = "تقليل الجهير"
    override val eqTreble = "تضخيم التريبل"
    override val eqVocal = "صوتي"
    override val eqRock = "روك"
    override val eqPop = "بوب"
    override val eqJazz = "جاز"
    override val eqClassical = "كلاسيكي"
    override val eqElectronic = "إلكتروني"
    override val eqHipHop = "هيب هوب"
    override val eqAcoustic = "أكوستيك"
    override val eqQuiet = "ليلي هادئ"
    override val eqCustom = "مخصص"

    override val fxNone = "بدون"
    override val fxCar = "ستيريو السيارة"
    override val fxHall = "قاعة ثلاثية الأبعاد 3D"
    override val fxOrbit = "صوت ثماني الأبعاد 8D"

    override val finishGlass = "زجاج"
    override val finishSteelGray = "رمادي فولاذي"
    override val finishBurgundy = "عنابي"
    override val finishGlacierBlue = "أزرق جليدي"
    override val finishSilver = "فضي"
    override val finishCustom = "لون مخصص"

    override val themeGlass = "زجاجي"
    override val themeCarbon = "كربون"
    override val themeCustom = "مخصص"

    override val searchStations = "البحث عن إذاعات"
    override val addCustomStation = "إضافة إذاعة خاصة"

    override val resyncWithHost = "إعادة المزامنة مع المضيف"
    override val playOnThisDevice = "التشغيل على هذا الجهاز"
    override val leaveSession = "مغادرة الجلسة"
    override val skipToNextSong = "الانتقال للأغنية التالية"
    override val previousSong = "الأغنية السابقة"
    override val addSongsToSession = "إضافة أغانٍ للجلسة"
    override val findSongsToPlay = "البحث عن أغانٍ للتشغيل"
    override val pauseSession = "إيقاف الجلسة مؤقتاً"
    override val resumeSession = "استئناف الجلسة"

    override val finishBlackenedSteel = "فولاذ مسود"
    override val finishMatteBlack = "أسود مطفي"

    override val lyricsClassic = "كلاسيكي"
    override val lyricsContinuousLine = "خط متصل"
    override val lyricsScrolling = "تمرير"

    override val lyricsAutomatic = "تلقائي"
    override val lyricsBlack = "أسود"
    override val lyricsWhite = "أبيض"

    override val lyricsChoiceAutomatic = "تلقائي: يتبدل مع كل سطر"
    override val lyricsChoiceBlack = "أسود دائمًا"
    override val lyricsChoiceWhite = "أبيض دائمًا"

    override val hapticBalanced = "متوازن"
    override val hapticTactile = "أكثر ملموسية"
    override val hapticReduced = "منخفض"
    override val hapticSystem = "افتراضي النظام"

    override fun hapticsTitle(profileLabel: String) = "الاهتزاز: $profileLabel"
    override fun secondsCount(n: Int) = if (n == 1) "ثانية واحدة" else if (n == 2) "ثانيتان" else if (n in 3..10) "$n ثوانٍ" else "$n ثانية"

    override val equalizerBandNotice = "أدر العجلة للرفع أو الخفض. اضغط على الوسط عندما يعجبك الصوت."
    override val hexDigitsNotice = "استخدم ستة خانات ست عشرية، مثل 2F6F5E."
    override fun colorChannelNotice(channelName: String) = "أدر العجلة لتعديل $channelName. تخط للأمام أو للخلف للشريط التالي."

    override val colorHue = "تدرج اللون"
    override val colorSaturation = "التشبع"
    override val colorBrightness = "السطوع"

    override val onlineMusic = "الموسيقى عبر الإنترنت"
    override val account = "الحساب"
    override val channel = "القناة"
    override val signIn = "تسجيل الدخول"
    override val signInAgain = "سجل الدخول مجددًا"
    override val signingIn = "جارٍ تسجيل الدخول"
    override val switching = "جارٍ التبديل"
    override val switchChannel = "تبديل القناة"
    override val switchAccount = "تبديل الحساب"
    override val signOut = "تسجيل الخروج"
    override val staySignedIn = "البقاء مسجلاً"
    override val installed = "مثبت"
    override val install = "تثبيت"
    override val mediaControls = "عناصر التحكم بالوسائط"
    override val allowed = "مسموح"
    override val allow = "سماح"
    override val afterChoosingSong = "بعد اختيار الأغنية"
    override val stayHere = "البقاء هنا"
    override val addSource = "إضافة مصدر"
    override val useThisSource = "استخدام هذا المصدر"
    override val moveUp = "نقل للأعلى"
    override val moveDown = "نقل للأسفل"
    override val answering = "يستجيب"
    override val notAnswering = "لا يستجيب"
    override val checking = "جارٍ الفحص"
    override val address = "العنوان"
    override val remove = "إزالة"
    override val keepIt = "الإبقاء عليه"
    override val paste = "لصق"
    override val source = "المصدر"
    override val connect = "اتصال"
    override val sourceAddress = "عنوان المصدر"

    override val signInToSoundCloud = "تسجيل الدخول إلى SoundCloud"
    override val stream = "البث المباشر"
    override val likes = "الإعجابات"
    override val topCharts = "أفضل 50 قائمة"
    override val searchSoundCloud = "بحث في SoundCloud"
    override val liveRadio = "راديو مباشر"
    override val explore = "استكشاف"
    override val home = "الرئيسية"
}

object FrenchStrings : AppStrings {
    override val back = "Retour"
    override val cancel = "Annuler"
    override val done = "Terminé"
    override val save = "Enregistrer"
    override val edit = "Modifier"
    override val delete = "Supprimer"
    override val ok = "OK"
    override val close = "Fermer"
    override val search = "Recherche"
    override val play = "Lecture"
    override val pause = "Pause"
    override val playing = "En cours"
    override val paused = "En pause"
    override val on = "Activé"
    override val off = "Désactivé"

    override val music = "Musique"
    override val online = "YT Music"
    override val radio = "Radio"
    override val podcasts = "Podcasts"
    override val quran = "Coran"
    override val gallery = "Galerie"
    override val books = "Livres"
    override val clock = "Horloge"
    override val insights = "Statistiques"
    override val listenTogether = "Écouter ensemble"
    override val voiceMemos = "Mémos vocaux"
    override val lifestyle = "Style de vie"
    override val tools = "Outils"
    override val shuffleSongs = "Lecture aléatoire"
    override val nowPlaying = "À l'écoute"
    override val settings = "Paramètres"

    override val coverFlow = "Cover Flow"
    override val albums = "Albums"
    override val artists = "Artistes"
    override val songs = "Morceaux"
    override val favorites = "Favoris"
    override val playlists = "Playlists"
    override val downloads = "Téléchargements"
    override val genres = "Genres"
    override val compilations = "Compilations"
    override val composers = "Compositeurs"
    override val addSongs = "Ajouter des morceaux"
    override val newPlaylist = "Nouvelle playlist"
    override val renamePlaylist = "Renommer"
    override val deletePlaylist = "Supprimer la playlist"
    override val playAll = "Tout lire"
    override val playNext = "Lire ensuite"
    override val addToQueue = "Ajouter à la file"
    override val noSongsFound = "Aucun morceau trouvé"
    override val noFavoritesYet = "Aucun favori pour l'instant"
    override val noPlaylists = "Aucune playlist"
    override fun songsCount(count: Int) = if (count == 1) "1 morceau" else "$count morceaux"
    override fun albumsCount(count: Int) = if (count == 1) "1 album" else "$count albums"

    override val upNext = "À suivre"
    override val lyrics = "Paroles"
    override val noLyrics = "Aucune parole disponible"
    override val loadingLyrics = "Recherche des paroles..."
    override val repeat = "Répéter"
    override val repeatOff = "Désactivé"
    override val repeatAll = "Tout"
    override val repeatOne = "Un"
    override val shuffle = "Aléatoire"
    override val shuffleOn = "Activé"
    override val shuffleOff = "Désactivé"

    override val language = "Langue"
    override val systemDefault = "Système par défaut"
    override val appearance = "Apparence"
    override val deviceBody = "CHORT ART"
    override val fullScreen = "Plein écran"
    override val batteryInTitleBar = "Batterie dans la barre"
    override val nowPlayingLayout = "Écran de lecture"
    override val hideNowPlayingTray = "Volet de lecture"
    override val keyboard = "Clavier"
    override val musicFolders = "Dossiers musicaux"
    override val onlineSources = "Musique en ligne"
    override val customSources = "Sources personnalisées"
    override val smartDownloads = "Téléchargements auto"
    override val autoplay = "Lecture automatique"
    override val recommendations = "Recommandations"
    override val avoidRepeats = "Éviter les répétitions"
    override val crossfade = "Fondu enchaîné"
    override val equalizer = "Égaliseur"
    override val sleepTimer = "Minuteur de veille"
    override val onlineLyrics = "Paroles en ligne"
    override val lyricsCredit = "Paroles fournies par la communauté LRCLIB"
    override val haptics = "Retour haptique"
    override val clicks = "Son des clics"
    override val startupSound = "Son de démarrage"
    override val softwareUpdate = "Mise à jour logicielle"

    override val shown = "Affichée"
    override val hidden = "Masquée"
    override val fullScreenOnly = "Plein écran uniquement"
    override val nostalgic = "Nostalgique"
    override val nowPlayingArch = "Arche"
    override val modern = "Moderne"
    override val hidesWhenIdle = "Masquer en veille"
    override val alwaysShown = "Toujours visible"
    override val allFolders = "Tous les dossiers"
    override fun allOfFolder(name: String) = "Tout de $name"
    override val checkNow = "Vérifier maintenant"
    override val automaticChecks = "Vérification automatique"

    override val virtualDisplay = "Écran virtuel"
    override val appearanceMode = "Mode"
    override val menuGradient = "Dégradé du menu"
    override val menuGradientColor = "Couleur du dégradé"
    override val subMenuBlurPreview = "Aperçu sous-menu"
    override val breadcrumbArch = "Arche retour"
    override val showHeaderTitle = "Titre d'en-tête"
    override val showStatusPill = "Pilule d'état"
    override val theme = "Thème"
    override val font = "Police"
    override val lyricsFont = "Police des paroles"
    override val lyricsTheme = "Thème des paroles"
    override val lyricsBackground = "Arrière-plan des paroles"
    override val background = "Arrière-plan"
    override val finish = "Finition"
    override val customColor = "Couleur personnalisée"
    override val backgroundColor = "Couleur d'arrière-plan"
    override val grain = "Grain"
    override val glitter = "Paillettes"
    override val contrast = "Contraste"
    override val glitterAmount = "Quantité de paillettes"
    override val glitterGlow = "Éclat des paillettes"
    override val followTilt = "Suivre l'inclinaison"
    override val glitterDensity = "Densité des paillettes"
    override val glitterSize = "Taille des paillettes"
    override val glitterOpacity = "Opacité des paillettes"
    override val backgroundOpacity = "Opacité de l'arrière-plan"
    override val preset = "Préréglage"
    override val soundEffect = "Effet"
    override val solidFinishesOnly = "Finitions unies uniquement"
    override val unavailable = "Indisponible"
    override val backgroundsNotice = "Les arrière-plans viennent avec Verre et Personnalisé"
    override val standardContrast = "Standard"
    override val highContrast = "Contraste élevé"
    override val chooseScanFolders = "Choisir les dossiers à analyser"
    override val importBookFile = "Importer un livre"
    override val telegramSync = "Synchronisation Telegram"
    override val connected = "Connecté"
    override val setup = "Configurer"
    override val noFoldersToChoose = "Aucun dossier à choisir"
    override val allowMusicAccessNotice = "Autorisez l'accès à la musique depuis l'accueil, puis revenez."

    override val sessionCode = "Code de session"
    override val hostAddress = "Adresse hôte"
    override val joining = "Connexion..."
    override val hostSession = "Démarrer une session"
    override val stations = "Stations"
    override val startSession = "Démarrer une session"
    override val joinWithAddress = "Rejoindre avec une adresse"
    override val nearby = "À proximité"
    override val join = "Rejoindre"
    override val endSession = "Terminer la session"
    override val guestsCanControl = "Contrôle des invités"
    override val searchingWifi = "Recherche de sessions à proximité…"

    override val record = "Enregistrer"
    override val resume = "Reprendre"
    override val recording = "Enregistrement..."
    override val stopAndSave = "Arrêter et enregistrer"
    override val saveName = "Enregistrer le nom"
    override val rename = "Renommer"

    override val editSong = "Modifier le morceau"
    override val editAlbum = "Modifier l'album"
    override val editArtist = "Modifier l'artiste"

    override val findSongsOnline = "Trouver des morceaux en ligne"
    override val removeAllDownloads = "Supprimer tous les téléchargements"
    override val retryFailedDownloads = "Réessayer les téléchargements échoués"

    override val continueReading = "Reprendre la lecture"
    override val reciters = "Récitateurs"
    override val tafsirAndTranslation = "Tafsir et traduction"
    override val searchQuran = "Rechercher dans le Coran"
    override val bookmarks = "Signets"

    override val worldClock = "Horloge mondiale"
    override val alarms = "Alarmes (WakeIQ)"
    override val stopwatch = "Chronomètre"
    override val timer = "Minuteur"

    override val photos = "Photos"
    override val videos = "Vidéos"
    override val folders = "Dossiers"
    override val trash = "Corbeille"

    override val topPodcasts = "Meilleurs podcasts"
    override val searchPodcasts = "Rechercher des podcasts"
    override val subscriptions = "Mes abonnements"
    override val categories = "Catégories"
    override val addByRss = "Ajouter par RSS"
    override val podcastSettings = "Paramètres de podcast"
    override val recentEpisodes = "Épisodes récents"

    override val allBooks = "Tous les livres"
    override val islamicBooks = "Livres islamiques"
    override val arabicBooks = "Livres arabes"

    override val downloadAll = "Tout télécharger"
    override val download = "Télécharger"
    override val downloadAlbum = "Télécharger l'album"
    override val playPopularSongs = "Lire les titres populaires"
    override val downloadPopularSongs = "Télécharger les titres populaires"
    override val addAllToPlaylist = "Tout ajouter à une playlist"
    override val addToPlaylist = "Ajouter à une playlist"
    override val moreSongs = "Plus de morceaux"
    override val createPlaylist = "Créer une playlist"
    override val clearHistory = "Effacer l'historique"
    override val likedSongs = "Titres aimés"
    override val onThisPhone = "Sur cet appareil"
    override val playedOnPodium = "Écoutés sur CHORT"
    override val like = "J'aime"
    override val removeFromLiked = "Retirer des titres aimés"
    override val removeDownload = "Supprimer le téléchargement"
    override val cancelDownload = "Annuler le téléchargement"
    override val startRadio = "Lancer la radio"
    override val goToAlbum = "Aller à l'album"
    override val share = "Partager"
    override val subscribe = "S'abonner"
    override val subscribed = "Abonné"
    override val history = "Historique"
    override val library = "Bibliothèque"

    override val choose = "Choisir"
    override val none = "Aucun"
    override val solid = "Couleur unie"
    override val picture = "Image"
    override val choosePicture = "Choisir une image"
    override val chooseAnotherPicture = "Choisir une autre image"
    override val useThisColor = "Utiliser cette couleur"
    override val hexColorCode = "Code couleur hexadécimal"
    override val wheelAdjustNotice = "Tournez la molette pour ajuster. Appuyez au centre pour valider."

    override val eqFlat = "Plat"
    override val eqBass = "Amplification des basses"
    override val eqBassCut = "Moins de basses"
    override val eqTreble = "Amplification des aigus"
    override val eqVocal = "Vocal"
    override val eqRock = "Rock"
    override val eqPop = "Pop"
    override val eqJazz = "Jazz"
    override val eqClassical = "Classique"
    override val eqElectronic = "Électronique"
    override val eqHipHop = "Hip-hop"
    override val eqAcoustic = "Acoustique"
    override val eqQuiet = "Fin de soirée"
    override val eqCustom = "Personnalisé"

    override val fxNone = "Aucun"
    override val fxCar = "Stéréo auto"
    override val fxHall = "Salle 3D"
    override val fxOrbit = "8D"

    override val finishGlass = "Verre"
    override val finishSteelGray = "Gris acier"
    override val finishBurgundy = "Bordeaux"
    override val finishGlacierBlue = "Bleu glacier"
    override val finishSilver = "Argent"
    override val finishCustom = "Couleur personnalisée"

    override val themeGlass = "Verre"
    override val themeCarbon = "Carbone"
    override val themeCustom = "Personnalisé"

    override val searchStations = "Rechercher des stations"
    override val addCustomStation = "Ajouter une station personnalisée"

    override val resyncWithHost = "Resynchroniser avec l'hôte"
    override val playOnThisDevice = "Écouter sur cet appareil"
    override val leaveSession = "Quitter la session"
    override val skipToNextSong = "Morceau suivant"
    override val previousSong = "Morceau précédent"
    override val addSongsToSession = "Ajouter à la session"
    override val findSongsToPlay = "Trouver des morceaux"
    override val pauseSession = "Suspendre la session"
    override val resumeSession = "Reprendre la session"

    override val finishBlackenedSteel = "Acier noirci"
    override val finishMatteBlack = "Noir mat"

    override val lyricsClassic = "Classique"
    override val lyricsContinuousLine = "Ligne continue"
    override val lyricsScrolling = "Défilement"

    override val lyricsAutomatic = "Automatique"
    override val lyricsBlack = "Noir"
    override val lyricsWhite = "Blanc"

    override val lyricsChoiceAutomatic = "Automatique : change à chaque ligne"
    override val lyricsChoiceBlack = "Toujours noir"
    override val lyricsChoiceWhite = "Toujours blanc"

    override val hapticBalanced = "Équilibré"
    override val hapticTactile = "Plus tactile"
    override val hapticReduced = "Réduit"
    override val hapticSystem = "Par défaut du système"

    override fun hapticsTitle(profileLabel: String) = "Retour haptique : $profileLabel"
    override fun secondsCount(n: Int) = if (n == 1) "1 seconde" else "$n secondes"

    override val equalizerBandNotice = "Tournez la molette pour amplifier ou atténuer. Appuyez au centre lorsque le son vous convient."
    override val hexDigitsNotice = "Utilisez six chiffres hexadécimaux, comme 2F6F5E."
    override fun colorChannelNotice(channelName: String) = "Tournez la molette pour modifier $channelName. Avancez ou reculez pour la barre suivante."

    override val colorHue = "la teinte"
    override val colorSaturation = "la saturation"
    override val colorBrightness = "la luminosité"

    override val onlineMusic = "Musique en ligne"
    override val account = "Compte"
    override val channel = "Chaîne"
    override val signIn = "Se connecter"
    override val signInAgain = "Se reconnecter"
    override val signingIn = "Connexion en cours"
    override val switching = "Changement en cours"
    override val switchChannel = "Changer de chaîne"
    override val switchAccount = "Changer de compte"
    override val signOut = "Se déconnecter"
    override val staySignedIn = "Rester connecté"
    override val installed = "Installé"
    override val install = "Installer"
    override val mediaControls = "Contrôles multimédias"
    override val allowed = "Autorisé"
    override val allow = "Autoriser"
    override val afterChoosingSong = "Après le choix d'un morceau"
    override val stayHere = "Rester ici"
    override val addSource = "Ajouter une source"
    override val useThisSource = "Utiliser cette source"
    override val moveUp = "Monter"
    override val moveDown = "Descendre"
    override val answering = "Répond"
    override val notAnswering = "Ne répond pas"
    override val checking = "Vérification en cours"
    override val address = "Adresse"
    override val remove = "Supprimer"
    override val keepIt = "Conserver"
    override val paste = "Coller"
    override val source = "Source"
    override val connect = "Se connecter"
    override val sourceAddress = "Adresse de la source"

    override val signInToSoundCloud = "Se connecter à SoundCloud"
    override val stream = "Flux"
    override val likes = "Titres likés"
    override val topCharts = "Top 50 des classements"
    override val searchSoundCloud = "Rechercher sur SoundCloud"
    override val liveRadio = "Radio en direct"
    override val explore = "Explorer"
    override val home = "Accueil"
}

object TurkishStrings : AppStrings {
    override val back = "Geri"
    override val cancel = "İptal"
    override val done = "Bitti"
    override val save = "Kaydet"
    override val edit = "Düzenle"
    override val delete = "Sil"
    override val ok = "Tamam"
    override val close = "Kapat"
    override val search = "Ara"
    override val play = "Çal"
    override val pause = "Duraklat"
    override val playing = "Çalıyor"
    override val paused = "Duraklatıldı"
    override val on = "Açık"
    override val off = "Kapalı"

    override val music = "Müzik"
    override val online = "YT Music"
    override val radio = "Radyo"
    override val podcasts = "Podcast'ler"
    override val quran = "Kuran-ı Kerim"
    override val gallery = "Galeri"
    override val books = "Kitaplar"
    override val clock = "Saat"
    override val insights = "İstatistikler"
    override val listenTogether = "Birlikte Dinle"
    override val voiceMemos = "Ses Kayıtları"
    override val lifestyle = "Yaşam Tarzı"
    override val tools = "Araçlar"
    override val shuffleSongs = "Karışık Çal"
    override val nowPlaying = "Şu An Çalınan"
    override val settings = "Ayarlar"

    override val coverFlow = "Cover Flow"
    override val albums = "Albümler"
    override val artists = "Sanatçılar"
    override val songs = "Şarkılar"
    override val favorites = "Favoriler"
    override val playlists = "Çalma Listeleri"
    override val downloads = "İndirilenler"
    override val genres = "Türler"
    override val compilations = "Derlemeler"
    override val composers = "Besteciler"
    override val addSongs = "Şarkı Ekle"
    override val newPlaylist = "Yeni Çalma Listesi"
    override val renamePlaylist = "Yeniden Adlandır"
    override val deletePlaylist = "Çalma Listesini Sil"
    override val playAll = "Tümünü Çal"
    override val playNext = "Sonrakini Çal"
    override val addToQueue = "Kuyruğa Ekle"
    override val noSongsFound = "Şarkı bulunamadı"
    override val noFavoritesYet = "Henüz favori yok"
    override val noPlaylists = "Çalma listesi yok"
    override fun songsCount(count: Int) = "$count şarkı"
    override fun albumsCount(count: Int) = "$count albüm"

    override val upNext = "Sıradaki"
    override val lyrics = "Şarkı Sözleri"
    override val noLyrics = "Şarkı sözü bulunamadı"
    override val loadingLyrics = "Sözler aranıyor..."
    override val repeat = "Tekrarla"
    override val repeatOff = "Kapalı"
    override val repeatAll = "Tümü"
    override val repeatOne = "Tek"
    override val shuffle = "Karışık"
    override val shuffleOn = "Açık"
    override val shuffleOff = "Kapalı"

    override val language = "Dil"
    override val systemDefault = "Sistem varsayılanı"
    override val appearance = "Görünüm"
    override val deviceBody = "CHORT ART"
    override val fullScreen = "Tam ekran"
    override val batteryInTitleBar = "Başlık çubuğunda pil"
    override val nowPlayingLayout = "Çalma ekranı"
    override val hideNowPlayingTray = "Çalma çekmecesi"
    override val keyboard = "Klavye"
    override val musicFolders = "Müzik klasörleri"
    override val onlineSources = "Çevrimiçi müzik"
    override val customSources = "Özel kaynaklar"
    override val smartDownloads = "Akıllı indirmeler"
    override val autoplay = "Otomatik oynat"
    override val recommendations = "Çevrimiçi öneriler"
    override val avoidRepeats = "Tekrarları engelle"
    override val crossfade = "Geçiş efekti"
    override val equalizer = "Ekolayzer"
    override val sleepTimer = "Uyku zamanlayıcısı"
    override val onlineLyrics = "Çevrimiçi sözler"
    override val lyricsCredit = "Sözler LRCLIB topluluğu tarafından sağlanmıştır"
    override val haptics = "Titreşim"
    override val clicks = "Tıklama sesi"
    override val startupSound = "Açılış sesi"
    override val softwareUpdate = "Yazılım güncellemesi"

    override val shown = "Gösteriliyor"
    override val hidden = "Gizli"
    override val fullScreenOnly = "Yalnızca tam ekran"
    override val nostalgic = "Nostaljik"
    override val nowPlayingArch = "Kemer"
    override val modern = "Yeni"
    override val hidesWhenIdle = "Boştayken gizle"
    override val alwaysShown = "Her zaman göster"
    override val allFolders = "Tüm klasörler"
    override fun allOfFolder(name: String) = "$name içindekiler"
    override val checkNow = "Şimdi denetle"
    override val automaticChecks = "Otomatik denetim"

    override val virtualDisplay = "Sanal ekran"
    override val appearanceMode = "Mod"
    override val menuGradient = "Menü gradyanı"
    override val menuGradientColor = "Gradyan rengi"
    override val subMenuBlurPreview = "Alt menü önizleme"
    override val breadcrumbArch = "Geri kemeri"
    override val showHeaderTitle = "Üst başlık"
    override val showStatusPill = "Durum çubuğu"
    override val theme = "Tema"
    override val font = "Yazı tipi"
    override val lyricsFont = "Söz yazı tipi"
    override val lyricsTheme = "Söz teması"
    override val lyricsBackground = "Söz arka planı"
    override val background = "Arka plan"
    override val finish = "Kasa görünümü"
    override val customColor = "Özel renk"
    override val backgroundColor = "Arka plan rengi"
    override val grain = "Gren"
    override val glitter = "Parıltı"
    override val contrast = "Kontrast"
    override val glitterAmount = "Parıltı miktarı"
    override val glitterGlow = "Parıltı ışıltısı"
    override val followTilt = "Eğimi takip et"
    override val glitterDensity = "Parıltı yoğunluğu"
    override val glitterSize = "Parıltı boyutu"
    override val glitterOpacity = "Parıltı opaklığı"
    override val backgroundOpacity = "Arka plan opaklığı"
    override val preset = "Önayar"
    override val soundEffect = "Efekt"
    override val solidFinishesOnly = "Yalnızca mat yüzeyler"
    override val unavailable = "Kullanılamıyor"
    override val backgroundsNotice = "Arka planlar Cam ve Özel ile birlikte gelir"
    override val standardContrast = "Standart"
    override val highContrast = "Yüksek kontrast"
    override val chooseScanFolders = "Tarama klasörlerini seç"
    override val importBookFile = "Kitap dosyası içe aktar"
    override val telegramSync = "Telegram Senkronizasyonu"
    override val connected = "Bağlandı"
    override val setup = "Ayarla"
    override val noFoldersToChoose = "Henüz seçilecek klasör yok"
    override val allowMusicAccessNotice = "Ana ekrandan müzik erişimine izin verin, sonra geri dönün."

    override val sessionCode = "Oturum kodu"
    override val hostAddress = "Sunucu adresi"
    override val joining = "Katılınıyor..."
    override val hostSession = "Oturum başlat"
    override val stations = "İstasyonlar"
    override val startSession = "Oturum başlat"
    override val joinWithAddress = "Adresle katıl"
    override val nearby = "Yakındakiler"
    override val join = "Katıl"
    override val endSession = "Oturumu sonlandır"
    override val guestsCanControl = "Misafir kontrolü"
    override val searchingWifi = "Yakındaki oturumlar aranıyor…"

    override val record = "Kaydet"
    override val resume = "Sürdür"
    override val recording = "Kaydediliyor..."
    override val stopAndSave = "Durdur ve kaydet"
    override val saveName = "İsmi kaydet"
    override val rename = "Yeniden adlandır"

    override val editSong = "Şarkıyı düzenle"
    override val editAlbum = "Albümü düzenle"
    override val editArtist = "Sanatçıyı düzenle"

    override val findSongsOnline = "Çevrimiçi şarkı bul"
    override val removeAllDownloads = "Tüm indirilenleri sil"
    override val retryFailedDownloads = "Başarısızları yeniden dene"

    override val continueReading = "Okumaya devam et"
    override val reciters = "Hafızlar"
    override val tafsirAndTranslation = "Tefsir ve Çeviri"
    override val searchQuran = "Kuran'da Ara"
    override val bookmarks = "Yer imleri"

    override val worldClock = "Dünya Saati"
    override val alarms = "Alarmlar (WakeIQ)"
    override val stopwatch = "Kronometre"
    override val timer = "Zamanlayıcı"

    override val photos = "Fotoğraflar"
    override val videos = "Videolar"
    override val folders = "Klasörler"
    override val trash = "Çöp Kutusu"

    override val topPodcasts = "Popüler podcast'ler"
    override val searchPodcasts = "Podcast ara"
    override val subscriptions = "Aboneliklerim"
    override val categories = "Kategoriler"
    override val addByRss = "RSS ile podcast ekle"
    override val podcastSettings = "Podcast ayarları"
    override val recentEpisodes = "Son bölümler"

    override val allBooks = "Tüm kitaplar"
    override val islamicBooks = "İslami kitaplar"
    override val arabicBooks = "Arapça kitaplar"

    override val downloadAll = "Tümünü indir"
    override val download = "İndir"
    override val downloadAlbum = "Albümü indir"
    override val playPopularSongs = "Popüler parçaları çal"
    override val downloadPopularSongs = "Popüler parçaları indir"
    override val addAllToPlaylist = "Tümünü çalma listesine ekle"
    override val addToPlaylist = "Çalma listesine ekle"
    override val moreSongs = "Daha fazla parça"
    override val createPlaylist = "Çalma listesi oluştur"
    override val clearHistory = "Geçmişi temizle"
    override val likedSongs = "Beğenilen şarkılar"
    override val onThisPhone = "Bu cihazda"
    override val playedOnPodium = "CHORT üzerinde çalınanlar"
    override val like = "Beğen"
    override val removeFromLiked = "Beğenilenlerden kaldır"
    override val removeDownload = "İndirmeyi sil"
    override val cancelDownload = "İndirmeyi iptal et"
    override val startRadio = "Radyo başlat"
    override val goToAlbum = "Albüm sayfasına git"
    override val share = "Paylaş"
    override val subscribe = "Abone ol"
    override val subscribed = "Abone olundu"
    override val history = "Geçmiş"
    override val library = "Kitaplık"

    override val choose = "Seç"
    override val none = "Yok"
    override val solid = "Düz renk"
    override val picture = "Resim"
    override val choosePicture = "Resim seç"
    override val chooseAnotherPicture = "Başka bir resim seç"
    override val useThisColor = "Bu rengi kullan"
    override val hexColorCode = "Hex renk kodu"
    override val wheelAdjustNotice = "Ayar için tekerleği çevirin. Kaydetmek için ortaya basın."

    override val eqFlat = "Düz"
    override val eqBass = "Bas güçlendirme"
    override val eqBassCut = "Daha az bas"
    override val eqTreble = "Tiz güçlendirme"
    override val eqVocal = "Vokal"
    override val eqRock = "Rock"
    override val eqPop = "Pop"
    override val eqJazz = "Caz"
    override val eqClassical = "Klasik"
    override val eqElectronic = "Elektronik"
    override val eqHipHop = "Hip-hop"
    override val eqAcoustic = "Akustik"
    override val eqQuiet = "Gece modu"
    override val eqCustom = "Özel"

    override val fxNone = "Yok"
    override val fxCar = "Araç stereosu"
    override val fxHall = "3B salon"
    override val fxOrbit = "8D"

    override val finishGlass = "Cam"
    override val finishSteelGray = "Çelik grisi"
    override val finishBurgundy = "Bordo"
    override val finishGlacierBlue = "Buzul mavisi"
    override val finishSilver = "Gümüş"
    override val finishCustom = "Özel renk"

    override val themeGlass = "Cam"
    override val themeCarbon = "Karbon"
    override val themeCustom = "Özel"

    override val searchStations = "İstasyon ara"
    override val addCustomStation = "Özel istasyon ekle"

    override val resyncWithHost = "Yeniden senkronize et"
    override val playOnThisDevice = "Bu cihazda çal"
    override val leaveSession = "Oturumdan ayrıl"
    override val skipToNextSong = "Sonraki şarkı"
    override val previousSong = "Önceki şarkı"
    override val addSongsToSession = "Oturuma şarkı ekle"
    override val findSongsToPlay = "Çalacak şarkı bul"
    override val pauseSession = "Oturumu duraklat"
    override val resumeSession = "Oturumu sürdür"

    override val finishBlackenedSteel = "Karartılmış çelik"
    override val finishMatteBlack = "Mat siyah"

    override val lyricsClassic = "Klasik"
    override val lyricsContinuousLine = "Sürekli çizgi"
    override val lyricsScrolling = "Kayan"

    override val lyricsAutomatic = "Otomatik"
    override val lyricsBlack = "Siyah"
    override val lyricsWhite = "Beyaz"

    override val lyricsChoiceAutomatic = "Otomatik: her satırda döner"
    override val lyricsChoiceBlack = "Her zaman siyah"
    override val lyricsChoiceWhite = "Her zaman beyaz"

    override val hapticBalanced = "Dengeli"
    override val hapticTactile = "Daha dokunsal"
    override val hapticReduced = "Azaltılmış"
    override val hapticSystem = "Sistem varsayılanı"

    override fun hapticsTitle(profileLabel: String) = "Titreşim: $profileLabel"
    override fun secondsCount(n: Int) = "$n saniye"

    override val equalizerBandNotice = "Yükseltmek veya kısmak için çarkı çevirin. Ses doğru geldiğinde merkeze basın."
    override val hexDigitsNotice = "2F6F5E gibi altı onaltılık basamak kullanın."
    override fun colorChannelNotice(channelName: String) = "$channelName değiştirmek için Çarkı çevirin. Sonraki çubuk için ileri veya geri atlayın."

    override val colorHue = "renk tonunu"
    override val colorSaturation = "doygunluğu"
    override val colorBrightness = "parlaklığı"

    override val onlineMusic = "Çevrimiçi müzik"
    override val account = "Hesap"
    override val channel = "Kanal"
    override val signIn = "Giriş yap"
    override val signInAgain = "Tekrar giriş yap"
    override val signingIn = "Giriş yapılıyor"
    override val switching = "Değiştiriliyor"
    override val switchChannel = "Kanalı değiştir"
    override val switchAccount = "Hesabı değiştir"
    override val signOut = "Çıkış yap"
    override val staySignedIn = "Oturumu açık tut"
    override val installed = "Yüklendi"
    override val install = "Yükle"
    override val mediaControls = "Medya kontrolleri"
    override val allowed = "İzin verildi"
    override val allow = "İzin ver"
    override val afterChoosingSong = "Şarkı seçtikten sonra"
    override val stayHere = "Burada kal"
    override val addSource = "Kaynak ekle"
    override val useThisSource = "Bu kaynağı kullan"
    override val moveUp = "Yukarı taşı"
    override val moveDown = "Aşağı taşı"
    override val answering = "Yanıt veriyor"
    override val notAnswering = "Yanıt vermiyor"
    override val checking = "Kontrol ediliyor"
    override val address = "Adres"
    override val remove = "Kaldır"
    override val keepIt = "Koru"
    override val paste = "Yapıştır"
    override val source = "Kaynak"
    override val connect = "Bağlan"
    override val sourceAddress = "Kaynak adresi"

    override val signInToSoundCloud = "SoundCloud'a giriş yap"
    override val stream = "Akış"
    override val likes = "Beğenilenler"
    override val topCharts = "En Popüler 50"
    override val searchSoundCloud = "SoundCloud'da ara"
    override val liveRadio = "Canlı radyo"
    override val explore = "Keşfet"
    override val home = "Ana sayfa"
}

object RussianStrings : AppStrings {
    override val back = "Назад"
    override val cancel = "Отмена"
    override val done = "Готово"
    override val save = "Сохранить"
    override val edit = "Изменить"
    override val delete = "Удалить"
    override val ok = "ОК"
    override val close = "Закрыть"
    override val search = "Поиск"
    override val play = "Воспроизведение"
    override val pause = "Пауза"
    override val playing = "Воспроизводится"
    override val paused = "На паузе"
    override val on = "Вкл"
    override val off = "Выкл"

    override val music = "Музыка"
    override val online = "YT Music"
    override val radio = "Радио"
    override val podcasts = "Подкасты"
    override val quran = "Коран"
    override val gallery = "Галерея"
    override val books = "Книги"
    override val clock = "Часы"
    override val insights = "Статистика"
    override val listenTogether = "Слушать вместе"
    override val voiceMemos = "Диктофон"
    override val lifestyle = "Стиль жизни"
    override val tools = "Инструменты"
    override val shuffleSongs = "Перемешать песни"
    override val nowPlaying = "Сейчас играет"
    override val settings = "Настройки"

    override val coverFlow = "Cover Flow"
    override val albums = "Альбомы"
    override val artists = "Исполнители"
    override val songs = "Песни"
    override val favorites = "Избранное"
    override val playlists = "Плейлисты"
    override val downloads = "Загрузки"
    override val genres = "Жанры"
    override val compilations = "Сборники"
    override val composers = "Композиторы"
    override val addSongs = "Добавить песни"
    override val newPlaylist = "Новый плейлист"
    override val renamePlaylist = "Переименовать"
    override val deletePlaylist = "Удалить плейлист"
    override val playAll = "Слушать все"
    override val playNext = "Слушать далее"
    override val addToQueue = "В конец очереди"
    override val noSongsFound = "Песни не найдены"
    override val noFavoritesYet = "Пока нет избранного"
    override val noPlaylists = "Нет плейлистов"
    override fun songsCount(count: Int): String {
        val mod10 = count % 10
        val mod100 = count % 100
        return when {
            mod100 in 11..19 -> "$count песен"
            mod10 == 1 -> "$count песня"
            mod10 in 2..4 -> "$count песни"
            else -> "$count песен"
        }
    }
    override fun albumsCount(count: Int): String {
        val mod10 = count % 10
        val mod100 = count % 100
        return when {
            mod100 in 11..19 -> "$count альбомов"
            mod10 == 1 -> "$count альбом"
            mod10 in 2..4 -> "$count альбома"
            else -> "$count альбомов"
        }
    }

    override val upNext = "Далее"
    override val lyrics = "Текст песни"
    override val noLyrics = "Текст недоступен"
    override val loadingLyrics = "Поиск текста..."
    override val repeat = "Повтор"
    override val repeatOff = "Выкл"
    override val repeatAll = "Все"
    override val repeatOne = "Один"
    override val shuffle = "Перемешать"
    override val shuffleOn = "Вкл"
    override val shuffleOff = "Выкл"

    override val language = "Язык"
    override val systemDefault = "По умолчанию"
    override val appearance = "Внешний вид"
    override val deviceBody = "CHORT ART"
    override val fullScreen = "Полноэкранный режим"
    override val batteryInTitleBar = "Батарея в строке заголовка"
    override val nowPlayingLayout = "Экран воспроизведения"
    override val hideNowPlayingTray = "Панель воспроизведения"
    override val keyboard = "Клавиатура"
    override val musicFolders = "Папки с музыкой"
    override val onlineSources = "Онлайн-музыка"
    override val customSources = "Свои источники"
    override val smartDownloads = "Умные загрузки"
    override val autoplay = "Автовоспроизведение"
    override val recommendations = "Онлайн-рекомендации"
    override val avoidRepeats = "Без повторов"
    override val crossfade = "Кроссфейд"
    override val equalizer = "Эквалайзер"
    override val sleepTimer = "Таймер сна"
    override val onlineLyrics = "Тексты онлайн"
    override val lyricsCredit = "Тексты предоставлены сообществом LRCLIB"
    override val haptics = "Вибрация"
    override val clicks = "Звук щелчков"
    override val startupSound = "Звук при включении"
    override val softwareUpdate = "Обновление ПО"

    override val shown = "Показано"
    override val hidden = "Скрыто"
    override val fullScreenOnly = "Только в полноэкранном"
    override val nostalgic = "Ностальгический"
    override val nowPlayingArch = "Дуга"
    override val modern = "Новый"
    override val hidesWhenIdle = "Скрывать при простое"
    override val alwaysShown = "Всегда показывать"
    override val allFolders = "Все папки"
    override fun allOfFolder(name: String) = "Все в $name"
    override val checkNow = "Проверить сейчас"
    override val automaticChecks = "Автопроверка"

    override val virtualDisplay = "Виртуальный дисплей"
    override val appearanceMode = "Режим"
    override val menuGradient = "Градиент меню"
    override val menuGradientColor = "Цвет градиента"
    override val subMenuBlurPreview = "Предпросмотр подменю"
    override val breadcrumbArch = "Дуга возврата"
    override val showHeaderTitle = "Заголовок"
    override val showStatusPill = "Индикатор статуса"
    override val theme = "Тема"
    override val font = "Шрифт"
    override val lyricsFont = "Шрифт текста"
    override val lyricsTheme = "Тема текста"
    override val lyricsBackground = "Фон текста"
    override val background = "Фон"
    override val finish = "Отделка"
    override val customColor = "Свой цвет"
    override val backgroundColor = "Цвет фона"
    override val grain = "Зернистость"
    override val glitter = "Блеск"
    override val contrast = "Контраст"
    override val glitterAmount = "Количество блеска"
    override val glitterGlow = "Свечение блеска"
    override val followTilt = "Следовать за наклоном"
    override val glitterDensity = "Плотность блеска"
    override val glitterSize = "Размер блеска"
    override val glitterOpacity = "Прозрачность блеска"
    override val backgroundOpacity = "Прозрачность фона"
    override val preset = "Пресет"
    override val soundEffect = "Эффект"
    override val solidFinishesOnly = "Только для матовых покрытий"
    override val unavailable = "Недоступно"
    override val backgroundsNotice = "Фоны доступны со Стеклом и Своим"
    override val standardContrast = "Стандартный"
    override val highContrast = "Высокий контраст"
    override val chooseScanFolders = "Выбрать папки для сканирования"
    override val importBookFile = "Импортировать книгу"
    override val telegramSync = "Синхронизация с Telegram"
    override val connected = "Подключено"
    override val setup = "Настроить"
    override val noFoldersToChoose = "Папки еще не выбраны"
    override val allowMusicAccessNotice = "Разрешите доступ к музыке на Главном экране, затем вернитесь."

    override val sessionCode = "Код сессии"
    override val hostAddress = "Адрес хоста"
    override val joining = "Подключение..."
    override val hostSession = "Создать сессию"
    override val stations = "Станции"
    override val startSession = "Создать сессию"
    override val joinWithAddress = "Войти по адресу"
    override val nearby = "Рядом"
    override val join = "Войти"
    override val endSession = "Завершить сессию"
    override val guestsCanControl = "Управление гостями"
    override val searchingWifi = "Поиск сессий поблизости…"

    override val record = "Запись"
    override val resume = "Продолжить"
    override val recording = "Идет запись..."
    override val stopAndSave = "Стоп и сохранить"
    override val saveName = "Сохранить имя"
    override val rename = "Переименовать"

    override val editSong = "Редактировать песню"
    override val editAlbum = "Редактировать альбом"
    override val editArtist = "Редактировать исполнителя"

    override val findSongsOnline = "Найти песни онлайн"
    override val removeAllDownloads = "Удалить все загрузки"
    override val retryFailedDownloads = "Повторить неудачные загрузки"

    override val continueReading = "Продолжить чтение"
    override val reciters = "Чтецы"
    override val tafsirAndTranslation = "Тафсир и перевод"
    override val searchQuran = "Поиск по Корану"
    override val bookmarks = "Закладки"

    override val worldClock = "Мировое время"
    override val alarms = "Будильники (WakeIQ)"
    override val stopwatch = "Секундомер"
    override val timer = "Таймер"

    override val photos = "Фото"
    override val videos = "Видео"
    override val folders = "Папки"
    override val trash = "Корзина"

    override val topPodcasts = "Популярные подкасты"
    override val searchPodcasts = "Поиск подкастов"
    override val subscriptions = "Мои подписки"
    override val categories = "Категории"
    override val addByRss = "Добавить по RSS"
    override val podcastSettings = "Настройки подкастов"
    override val recentEpisodes = "Недавние выпуски"

    override val allBooks = "Все книги"
    override val islamicBooks = "Исламские книги"
    override val arabicBooks = "Арабские книги"

    override val downloadAll = "Скачать всё"
    override val download = "Скачать"
    override val downloadAlbum = "Скачать альбом"
    override val playPopularSongs = "Воспроизвести популярные"
    override val downloadPopularSongs = "Скачать популярные"
    override val addAllToPlaylist = "Добавить всё в плейлист"
    override val addToPlaylist = "Добавить в плейлист"
    override val moreSongs = "Ещё треки"
    override val createPlaylist = "Создать плейлист"
    override val clearHistory = "Очистить историю"
    override val likedSongs = "Любимые треки"
    override val onThisPhone = "На этом устройстве"
    override val playedOnPodium = "Слушали в CHORT"
    override val like = "Нравится"
    override val removeFromLiked = "Удалить из любимых"
    override val removeDownload = "Удалить загрузку"
    override val cancelDownload = "Отменить загрузку"
    override val startRadio = "Запустить радио"
    override val goToAlbum = "Перейти к альбому"
    override val share = "Поделиться"
    override val subscribe = "Подписаться"
    override val subscribed = "Вы подписаны"
    override val history = "История"
    override val library = "Медиатека"

    override val choose = "Выбрать"
    override val none = "Нет"
    override val solid = "Сплошной цвет"
    override val picture = "Изображение"
    override val choosePicture = "Выбрать изображение"
    override val chooseAnotherPicture = "Выбрать другое изображение"
    override val useThisColor = "Использовать этот цвет"
    override val hexColorCode = "Шестнадцатеричный код"
    override val wheelAdjustNotice = "Вращайте колесо для настройки. Нажмите центр для сохранения."

    override val eqFlat = "Плоский"
    override val eqBass = "Усиление басов"
    override val eqBassCut = "Меньше басов"
    override val eqTreble = "Усиление ВЧ"
    override val eqVocal = "Вокал"
    override val eqRock = "Рок"
    override val eqPop = "Поп"
    override val eqJazz = "Джаз"
    override val eqClassical = "Классика"
    override val eqElectronic = "Электронная"
    override val eqHipHop = "Хип-хоп"
    override val eqAcoustic = "Акустический"
    override val eqQuiet = "Ночной"
    override val eqCustom = "Свой"

    override val fxNone = "Нет"
    override val fxCar = "Автостерео"
    override val fxHall = "3D зал"
    override val fxOrbit = "8D"

    override val finishGlass = "Стекло"
    override val finishSteelGray = "Стальной серый"
    override val finishBurgundy = "Бордовый"
    override val finishGlacierBlue = "Ледниковый синий"
    override val finishSilver = "Серебристый"
    override val finishCustom = "Свой цвет"

    override val themeGlass = "Стекло"
    override val themeCarbon = "Карбон"
    override val themeCustom = "Свой"

    override val searchStations = "Поиск станций"
    override val addCustomStation = "Добавить станцию"

    override val resyncWithHost = "Синхронизировать с хостом"
    override val playOnThisDevice = "Играть на этом устройстве"
    override val leaveSession = "Покинуть сессию"
    override val skipToNextSong = "Следующий трек"
    override val previousSong = "Предыдущий трек"
    override val addSongsToSession = "Добавить треки в сессию"
    override val findSongsToPlay = "Найти треки"
    override val pauseSession = "Приостановить сессию"
    override val resumeSession = "Возобновить сессию"

    override val finishBlackenedSteel = "Черненая сталь"
    override val finishMatteBlack = "Матовый черный"

    override val lyricsClassic = "Классический"
    override val lyricsContinuousLine = "Непрерывная линия"
    override val lyricsScrolling = "Прокрутка"

    override val lyricsAutomatic = "Автоматически"
    override val lyricsBlack = "Черный"
    override val lyricsWhite = "Белый"

    override val lyricsChoiceAutomatic = "Автоматически: меняется с каждой строкой"
    override val lyricsChoiceBlack = "Всегда черный"
    override val lyricsChoiceWhite = "Всегда белый"

    override val hapticBalanced = "Сбалансированный"
    override val hapticTactile = "Более тактильный"
    override val hapticReduced = "Уменьшенный"
    override val hapticSystem = "По умолчанию системы"

    override fun hapticsTitle(profileLabel: String) = "Виброотклик: $profileLabel"
    override fun secondsCount(n: Int) = if (n % 10 == 1 && n % 100 != 11) "$n секунда" else if (n % 10 in 2..4 && (n % 100 < 10 || n % 100 >= 20)) "$n секунды" else "$n секунд"

    override val equalizerBandNotice = "Поверните колесо для усиления или ослабления. Нажмите центр, когда звук вас устроит."
    override val hexDigitsNotice = "Используйте шесть шестнадцатеричных цифр, например 2F6F5E."
    override fun colorChannelNotice(channelName: String) = "Поверните колесо, чтобы изменить $channelName. Вперед или назад для следующей полосы."

    override val colorHue = "оттенок"
    override val colorSaturation = "насыщенность"
    override val colorBrightness = "яркость"

    override val onlineMusic = "Онлайн музыка"
    override val account = "Аккаунт"
    override val channel = "Канал"
    override val signIn = "Войти"
    override val signInAgain = "Войти снова"
    override val signingIn = "Вход..."
    override val switching = "Переключение..."
    override val switchChannel = "Сменить канал"
    override val switchAccount = "Сменить аккаунт"
    override val signOut = "Выйти"
    override val staySignedIn = "Оставаться в системе"
    override val installed = "Установлено"
    override val install = "Установить"
    override val mediaControls = "Управление медиа"
    override val allowed = "Разрешено"
    override val allow = "Разрешить"
    override val afterChoosingSong = "После выбора песни"
    override val stayHere = "Остаться здесь"
    override val addSource = "Добавить источник"
    override val useThisSource = "Использовать этот источник"
    override val moveUp = "Вверх"
    override val moveDown = "Вниз"
    override val answering = "Отвечает"
    override val notAnswering = "Не отвечает"
    override val checking = "Проверка..."
    override val address = "Адрес"
    override val remove = "Удалить"
    override val keepIt = "Оставить"
    override val paste = "Вставить"
    override val source = "Источник"
    override val connect = "Подключить"
    override val sourceAddress = "Адрес источника"

    override val signInToSoundCloud = "Войти в SoundCloud"
    override val stream = "Лента"
    override val likes = "Понравившиеся"
    override val topCharts = "Топ-50 чарт"
    override val searchSoundCloud = "Поиск в SoundCloud"
    override val liveRadio = "Прямой эфир"
    override val explore = "Обзор"
    override val home = "Главная"
}

val LocalAppStrings = staticCompositionLocalOf<AppStrings> { EnglishStrings }
val LocalAppLanguage = staticCompositionLocalOf<AppLanguage> { AppLanguage.ENGLISH }

@Composable
fun ProvideAppL10n(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val systemLocale = Locale.getDefault()
    val effective = remember(language, systemLocale) {
        AppLanguage.resolve(language, systemLocale)
    }
    val strings = remember(effective) {
        when (effective) {
            AppLanguage.ENGLISH, AppLanguage.SYSTEM -> EnglishStrings
            AppLanguage.ARABIC -> ArabicStrings
            AppLanguage.FRENCH -> FrenchStrings
            AppLanguage.TURKISH -> TurkishStrings
            AppLanguage.RUSSIAN -> RussianStrings
        }
    }
    // Maintain LTR for the iPod virtual display geometry so columns, the arc, preview window and checkboxes
    // keep their clean physical alignment while Arabic text renders naturally within its bounds.
    CompositionLocalProvider(
        LocalAppLanguage provides effective,
        LocalAppStrings provides strings,
        LocalLayoutDirection provides LayoutDirection.Ltr,
    ) {
        content()
    }
}
