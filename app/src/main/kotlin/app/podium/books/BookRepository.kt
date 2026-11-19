package app.podium.books

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BookRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("chort_books_prefs", Context.MODE_PRIVATE)
    private val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    private val coversDir = File(context.cacheDir, "book_covers").apply { mkdirs() }

    private val _localBooks = MutableStateFlow<List<BookItem>>(emptyList())
    val localBooks: StateFlow<List<BookItem>> = _localBooks.asStateFlow()

    private val _onlineBooks = MutableStateFlow<List<BookItem>>(CURATED_ARABIC_BOOKS + CURATED_ONLINE_BOOKS)
    val onlineBooks: StateFlow<List<BookItem>> = _onlineBooks.asStateFlow()

    private val _islamicBooks = MutableStateFlow<List<BookItem>>(CURATED_ISLAMIC_BOOKS)
    val islamicBooks: StateFlow<List<BookItem>> = _islamicBooks.asStateFlow()

    private val _quranBooks = MutableStateFlow<List<BookItem>>(CURATED_QURAN_BOOKS)
    val quranBooks: StateFlow<List<BookItem>> = _quranBooks.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(
        prefs.getStringSet("fav_books", emptySet()) ?: emptySet(),
    )
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _progressMap = MutableStateFlow<Map<String, BookProgress>>(loadProgressMap())
    val progressMap: StateFlow<Map<String, BookProgress>> = _progressMap.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<BookBookmark>>(loadBookmarks())
    val bookmarks: StateFlow<List<BookBookmark>> = _bookmarks.asStateFlow()

    private val _theme = MutableStateFlow(loadTheme())
    val theme: StateFlow<BookTheme> = _theme.asStateFlow()

    private val _fontSizeSp = MutableStateFlow(prefs.getInt("font_size_sp", 18))
    val fontSizeSp: StateFlow<Int> = _fontSizeSp.asStateFlow()

    private val _selectedFolders = MutableStateFlow<Set<String>>(
        prefs.getStringSet("selected_book_folders", emptySet()) ?: emptySet(),
    )
    val selectedFolders: StateFlow<Set<String>> = _selectedFolders.asStateFlow()

    private val _customFolders = MutableStateFlow<Set<String>>(
        prefs.getStringSet("custom_book_folders", emptySet()) ?: emptySet(),
    )
    val customFolders: StateFlow<Set<String>> = _customFolders.asStateFlow()

    private val _deletedBookIds = MutableStateFlow<Set<String>>(
        prefs.getStringSet("deleted_book_ids", emptySet()) ?: emptySet(),
    )

    fun deleteBook(book: BookItem) {
        val nextDeleted = _deletedBookIds.value + setOfNotNull(book.id, book.path.takeIf { it.isNotBlank() })
        _deletedBookIds.value = nextDeleted
        prefs.edit().putStringSet("deleted_book_ids", nextDeleted).apply()

        _localBooks.value = _localBooks.value.filterNot { it.id == book.id || (book.path.isNotBlank() && it.path == book.path) }
        _onlineBooks.value = _onlineBooks.value.filterNot { it.id == book.id }
        _islamicBooks.value = _islamicBooks.value.filterNot { it.id == book.id }
        _quranBooks.value = _quranBooks.value.filterNot { it.id == book.id }

        if (book.id in _favoriteIds.value) {
            val nextFavs = _favoriteIds.value - book.id
            _favoriteIds.value = nextFavs
            prefs.edit().putStringSet("fav_books", nextFavs).apply()
        }
        if (book.id in _progressMap.value) {
            val nextProg = _progressMap.value.toMutableMap().apply { remove(book.id) }
            _progressMap.value = nextProg
        }
        if (_bookmarks.value.any { it.bookId == book.id }) {
            val nextBm = _bookmarks.value.filterNot { it.bookId == book.id }
            _bookmarks.value = nextBm
            saveBookmarks(nextBm)
        }

        if (book.path.isNotBlank()) {
            runCatching {
                val f = File(book.path)
                if (f.exists()) f.delete()
            }
            runCatching {
                @Suppress("DEPRECATION")
                context.contentResolver.delete(
                    android.provider.MediaStore.Files.getContentUri("external"),
                    "${android.provider.MediaStore.Files.FileColumns.DATA} = ?",
                    arrayOf(book.path),
                )
            }
        }
    }

    fun shareBook(book: BookItem) {
        runCatching {
            val file = if (book.path.isNotBlank()) File(book.path) else null
            if (file != null && file.exists()) {
                var contentUri: Uri? = null
                runCatching {
                    @Suppress("DEPRECATION")
                    val collection = android.provider.MediaStore.Files.getContentUri("external")
                    @Suppress("DEPRECATION")
                    val proj = arrayOf(android.provider.MediaStore.Files.FileColumns._ID)
                    @Suppress("DEPRECATION")
                    val sel = "${android.provider.MediaStore.Files.FileColumns.DATA} = ?"
                    context.contentResolver.query(collection, proj, sel, arrayOf(file.absolutePath), null)?.use { c ->
                        if (c.moveToFirst()) {
                            val id = c.getLong(0)
                            contentUri = android.content.ContentUris.withAppendedId(collection, id)
                        }
                    }
                }
                val builder = android.os.StrictMode.VmPolicy.Builder()
                android.os.StrictMode.setVmPolicy(builder.build())
                val streamUri = contentUri ?: Uri.fromFile(file)
                val mime = when (book.format) {
                    BookFormat.PDF -> "application/pdf"
                    BookFormat.EPUB -> "application/epub+zip"
                    BookFormat.TXT, BookFormat.MD -> "text/plain"
                    BookFormat.DOC -> "application/msword"
                    else -> "*/*"
                }
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(android.content.Intent.EXTRA_STREAM, streamUri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, book.title)
                    putExtra(android.content.Intent.EXTRA_TEXT, "${book.title} — ${book.author}")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = android.content.Intent.createChooser(send, book.title).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } else {
                val url = book.downloadUrl?.let { raw ->
                    if (raw.startsWith("archive://")) "https://archive.org/details/${raw.removePrefix("archive://")}"
                    else raw
                }.orEmpty()
                val text = if (url.startsWith("http")) "${book.title} (${book.author})\n$url" else "${book.title} — ${book.author}"
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, book.title)
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                }
                val chooser = android.content.Intent.createChooser(send, book.title).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        }
    }

    fun toggleSelectedFolder(folderPath: String) {
        val next = _selectedFolders.value.toMutableSet()
        if (folderPath in next) next.remove(folderPath) else next.add(folderPath)
        _selectedFolders.value = next
        prefs.edit().putStringSet("selected_book_folders", next).apply()
    }

    fun selectAllFolders() {
        _selectedFolders.value = emptySet()
        prefs.edit().putStringSet("selected_book_folders", emptySet()).apply()
    }

    fun addCustomFolderUri(treeUri: Uri): String? {
        val path = resolveTreeUriToPath(treeUri) ?: return null
        val nextCustom = _customFolders.value + path
        _customFolders.value = nextCustom
        prefs.edit().putStringSet("custom_book_folders", nextCustom).apply()

        val nextSel = _selectedFolders.value + path
        _selectedFolders.value = nextSel
        prefs.edit().putStringSet("selected_book_folders", nextSel).apply()
        return path
    }

    private fun resolveTreeUriToPath(treeUri: Uri): String? = runCatching {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
        if (docId.startsWith("primary:")) {
            val rel = docId.removePrefix("primary:").trimStart('/')
            val base = Environment.getExternalStorageDirectory().absolutePath
            if (rel.isEmpty()) base else "$base/$rel"
        } else if (docId.contains(":")) {
            val parts = docId.split(":", limit = 2)
            "/storage/${parts[0]}/${parts[1]}"
        } else {
            null
        }
    }.getOrNull()

    fun candidateFolders(): List<Pair<String, String>> {
        val ext = Environment.getExternalStorageDirectory()
        val defaults = listOf(
            File(ext, "Download") to "Download",
            File(ext, "Download/Telegram") to "Download / Telegram",
            File(ext, "Documents") to "Documents",
            File(ext, "Books") to "Books",
            File(ext, "Telegram") to "Telegram",
            booksDir to "Imported Books (App)",
        )
        val discovered = _customFolders.value.map { File(it) to File(it).name.ifBlank { it } }
        return (defaults + discovered)
            .distinctBy { it.first.absolutePath }
            .filter { it.first.exists() || it.first.absolutePath in _customFolders.value || it.first == booksDir }
            .map { it.first.absolutePath to it.second }
    }

    private fun loadTheme(): BookTheme {
        val name = prefs.getString("book_theme", BookTheme.DAY.name) ?: BookTheme.DAY.name
        return BookTheme.entries.find { it.name == name } ?: BookTheme.DAY
    }

    fun cycleTheme(): BookTheme {
        val next = when (_theme.value) {
            BookTheme.DAY -> BookTheme.SEPIA
            BookTheme.SEPIA -> BookTheme.NIGHT
            BookTheme.NIGHT -> BookTheme.DAY
        }
        _theme.value = next
        prefs.edit().putString("book_theme", next.name).apply()
        return next
    }

    fun adjustFontSize(delta: Int): Int {
        val next = (_fontSizeSp.value + delta).coerceIn(13, 32)
        _fontSizeSp.value = next
        prefs.edit().putInt("font_size_sp", next).apply()
        return next
    }

    fun toggleFavorite(bookId: String): Boolean {
        val next = _favoriteIds.value.toMutableSet()
        val added = if (bookId in next) {
            next.remove(bookId)
            false
        } else {
            next.add(bookId)
            true
        }
        _favoriteIds.value = next
        prefs.edit().putStringSet("fav_books", next).apply()
        return added
    }

    fun saveProgress(bookId: String, pageIndex: Int, totalPages: Int) {
        if (totalPages <= 0) return
        val next = _progressMap.value.toMutableMap()
        next[bookId] = BookProgress(bookId, pageIndex.coerceIn(0, totalPages - 1), totalPages)
        _progressMap.value = next
        val arr = JSONArray()
        next.values.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("bookId", p.bookId)
                    put("pageIndex", p.pageIndex)
                    put("totalPages", p.totalPages)
                    put("updatedAt", p.updatedAt)
                },
            )
        }
        prefs.edit().putString("book_progress_json", arr.toString()).apply()
    }

    private fun loadProgressMap(): Map<String, BookProgress> = runCatching {
        val raw = prefs.getString("book_progress_json", null) ?: return emptyMap()
        val arr = JSONArray(raw)
        val map = HashMap<String, BookProgress>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("bookId")
            if (id.isNotBlank()) {
                map[id] = BookProgress(
                    bookId = id,
                    pageIndex = o.optInt("pageIndex", 0),
                    totalPages = o.optInt("totalPages", 1),
                    updatedAt = o.optLong("updatedAt", 0L),
                )
            }
        }
        map
    }.getOrDefault(emptyMap())

    fun toggleBookmark(bookId: String, bookTitle: String, pageIndex: Int, totalPages: Int, snippet: String): Boolean {
        val list = _bookmarks.value.toMutableList()
        val existingIdx = list.indexOfFirst { it.bookId == bookId && it.pageIndex == pageIndex }
        val added = if (existingIdx >= 0) {
            list.removeAt(existingIdx)
            false
        } else {
            list.add(
                0,
                BookBookmark(
                    bookId = bookId,
                    bookTitle = bookTitle,
                    pageIndex = pageIndex,
                    totalPages = totalPages,
                    snippet = snippet.take(90),
                ),
            )
            true
        }
        _bookmarks.value = list
        saveBookmarks(list)
        return added
    }

    private fun saveBookmarks(list: List<BookBookmark>) {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("bookId", b.bookId)
                    put("bookTitle", b.bookTitle)
                    put("pageIndex", b.pageIndex)
                    put("totalPages", b.totalPages)
                    put("snippet", b.snippet)
                    put("addedAt", b.addedAt)
                },
            )
        }
        prefs.edit().putString("book_bookmarks_json", arr.toString()).apply()
    }

    private fun loadBookmarks(): List<BookBookmark> = runCatching {
        val raw = prefs.getString("book_bookmarks_json", null) ?: return emptyList()
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    BookBookmark(
                        bookId = o.optString("bookId"),
                        bookTitle = o.optString("bookTitle"),
                        pageIndex = o.optInt("pageIndex"),
                        totalPages = o.optInt("totalPages", 1),
                        snippet = o.optString("snippet"),
                        addedAt = o.optLong("addedAt"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    fun findBook(bookId: String): BookItem? {
        return _localBooks.value.find { it.id == bookId }
            ?: _onlineBooks.value.find { it.id == bookId }
            ?: _islamicBooks.value.find { it.id == bookId }
            ?: _quranBooks.value.find { it.id == bookId }
            ?: CURATED_QURAN_BOOKS.find { it.id == bookId }
            ?: CURATED_ISLAMIC_BOOKS.find { it.id == bookId }
            ?: CURATED_ARABIC_BOOKS.find { it.id == bookId }
            ?: CURATED_ONLINE_BOOKS.find { it.id == bookId }
    }

    fun continueReadingBooks(): List<BookItem> {
        val prog = _progressMap.value
        val all = (_localBooks.value + _onlineBooks.value + _islamicBooks.value + _quranBooks.value).distinctBy { it.id }
        return all
            .filter { it.id in prog }
            .sortedByDescending { prog[it.id]?.updatedAt ?: 0L }
    }

    fun favoriteBooks(): List<BookItem> {
        val favs = _favoriteIds.value
        val all = (_localBooks.value + _onlineBooks.value + _islamicBooks.value + _quranBooks.value).distinctBy { it.id }
        return all.filter { it.id in favs }
    }

    fun folders(filterFormat: BookFormat? = null): List<BookFolderGroup> {
        return _localBooks.value
            .filter { it.folderPath.isNotBlank() && (filterFormat == null || it.format == filterFormat) }
            .groupBy { it.folderPath }
            .map { (path, list) ->
                val name = File(path).name.ifBlank { "Books" }
                val fmtNote = filterFormat?.label?.let { " • $it" }.orEmpty()
                BookFolderGroup(
                    folderPath = path,
                    folderName = name,
                    subtitle = "${list.size} book(s)$fmtNote",
                    coverUri = list.firstOrNull()?.coverUri,
                    books = list,
                )
            }
            .sortedByDescending { it.books.size }
    }

    suspend fun scanLocalBooks() = withContext(Dispatchers.IO) {
        ensureBundledSampleBooks()
        val found = LinkedHashMap<String, BookItem>()

        val chosen = _selectedFolders.value
        val roots = if (chosen.isNotEmpty()) {
            (chosen.map { File(it) } + booksDir).distinctBy { it.absolutePath }
        } else {
            listOfNotNull(
                booksDir,
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                File(Environment.getExternalStorageDirectory(), "Books"),
                File(Environment.getExternalStorageDirectory(), "Download"),
                File(Environment.getExternalStorageDirectory(), "Download/Telegram"),
                File(Environment.getExternalStorageDirectory(), "Telegram"),
                File(Environment.getExternalStorageDirectory(), "Documents"),
            ) + _customFolders.value.map { File(it) }
        }

        for (root in roots) {
            if (root.exists() && root.canRead()) {
                scanDirectoryRecursive(root, depth = 0, maxDepth = 3, out = found)
            }
        }
        if (chosen.isEmpty()) {
            queryMediaStoreBooks(found)
        } else {
            val temp = LinkedHashMap<String, BookItem>()
            queryMediaStoreBooks(temp)
            temp.forEach { (p, item) ->
                if (chosen.any { sel -> p.startsWith(sel) }) {
                    found[p] = item
                }
            }
        }
        val deleted = _deletedBookIds.value
        _localBooks.value = found.values.filterNot { it.id in deleted || it.path in deleted }
    }

    @Suppress("DEPRECATION")
    private fun queryMediaStoreBooks(out: MutableMap<String, BookItem>) = runCatching {
        val collection = android.provider.MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME,
            android.provider.MediaStore.Files.FileColumns.DATA,
            android.provider.MediaStore.Files.FileColumns.SIZE,
        )
        val sort = "${android.provider.MediaStore.Files.FileColumns.DATE_ADDED} DESC"
        context.contentResolver.query(collection, projection, null, null, sort)?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.SIZE)
            while (cursor.moveToNext() && out.size < 400) {
                val name = cursor.getString(nameCol) ?: continue
                val fmt = BookFormat.fromFileName(name) ?: continue
                val path = cursor.getString(dataCol) ?: continue
                if (path in out) continue
                val f = File(path)
                if (!f.exists() || !f.canRead()) continue
                val cleanTitle = f.nameWithoutExtension.replace('_', ' ').replace('-', ' ').trim()
                val cover = when (fmt) {
                    BookFormat.PDF -> "book-pdf-cover://$path"
                    BookFormat.EPUB, BookFormat.CBZ -> extractZipCover(f)
                    else -> null
                }
                out[path] = BookItem(
                    id = "local_${path.hashCode()}",
                    title = cleanTitle.ifBlank { f.name },
                    author = f.parentFile?.name ?: "Local Book",
                    path = path,
                    format = fmt,
                    coverUri = cover,
                    sizeBytes = cursor.getLong(sizeCol),
                    folderPath = f.parent ?: "",
                )
            }
        }
    }

    private fun scanDirectoryRecursive(
        dir: File,
        depth: Int,
        maxDepth: Int,
        out: MutableMap<String, BookItem>,
    ) {
        if (depth > maxDepth || out.size >= 400) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                scanDirectoryRecursive(f, depth + 1, maxDepth, out)
            } else {
                val fmt = BookFormat.fromFileName(f.name) ?: continue
                val path = f.absolutePath
                if (path in out) continue
                val cleanTitle = f.nameWithoutExtension
                    .replace('_', ' ')
                    .replace('-', ' ')
                    .trim()
                val cover = when (fmt) {
                    BookFormat.PDF -> "book-pdf-cover://$path"
                    BookFormat.EPUB, BookFormat.CBZ -> extractZipCover(f)
                    else -> null
                }
                val id = "local_${path.hashCode()}"
                out[path] = BookItem(
                    id = id,
                    title = cleanTitle.ifBlank { f.name },
                    author = f.parentFile?.name ?: "Local Book",
                    path = path,
                    format = fmt,
                    coverUri = cover,
                    sizeBytes = f.length(),
                    folderPath = f.parent ?: "",
                )
            }
        }
    }

    private fun extractZipCover(file: File): String? = runCatching {
        val cacheFile = File(coversDir, "cover_${file.absolutePath.hashCode()}_${file.lastModified()}.jpg")
        if (cacheFile.exists() && cacheFile.length() > 0L) return "file://${cacheFile.absolutePath}"
        ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence().toList()
            val imageEntry = entries.firstOrNull { e ->
                !e.isDirectory &&
                    (e.name.contains("cover", ignoreCase = true) || e.name.endsWith(".jpg", true) || e.name.endsWith(".jpeg", true) || e.name.endsWith(".png", true)) &&
                    (e.name.endsWith(".jpg", true) || e.name.endsWith(".jpeg", true) || e.name.endsWith(".png", true))
            } ?: return null
            zip.getInputStream(imageEntry).use { input ->
                FileOutputStream(cacheFile).use { out -> input.copyTo(out) }
            }
            "file://${cacheFile.absolutePath}"
        }
    }.getOrNull()

    suspend fun importBookUri(uri: Uri): BookItem? = withContext(Dispatchers.IO) {
        runCatching {
            var displayName = ""
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) {
                        displayName = cursor.getString(idx).orEmpty()
                    }
                }
            }
            if (displayName.isBlank()) {
                displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Book_${System.currentTimeMillis()}"
            }
            val mime = runCatching { context.contentResolver.getType(uri).orEmpty().lowercase() }.getOrDefault("")
            val fmt = BookFormat.fromFileName(displayName) ?: when {
                mime.contains("pdf") -> BookFormat.PDF
                mime.contains("epub") -> BookFormat.EPUB
                mime.contains("cbz") || mime.contains("comic") -> BookFormat.CBZ
                mime.contains("fb2") -> BookFormat.FB2
                mime.contains("text") || mime.contains("txt") -> BookFormat.TXT
                else -> BookFormat.PDF
            }
            var safeName = displayName.replace(Regex("[^a-zA-Z0-9._\\-\\u0600-\\u06FF ]"), "_").trim()
            if (safeName.isBlank()) safeName = "Book_${System.currentTimeMillis()}"
            if (!safeName.lowercase().endsWith(".${fmt.ext}")) {
                safeName = "$safeName.${fmt.ext}"
            }
            val dest = File(booksDir, safeName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { out -> input.copyTo(out) }
            } ?: return@withContext null

            val path = dest.absolutePath
            val cleanTitle = dest.nameWithoutExtension.replace('_', ' ').replace('-', ' ').trim()
            val cover = when (fmt) {
                BookFormat.PDF -> "book-pdf-cover://$path"
                BookFormat.EPUB, BookFormat.CBZ -> extractZipCover(dest)
                else -> null
            }
            val importedItem = BookItem(
                id = "local_${path.hashCode()}",
                title = cleanTitle.ifBlank { dest.name },
                author = "Imported Book",
                path = path,
                format = fmt,
                coverUri = cover,
                sizeBytes = dest.length(),
                folderPath = booksDir.absolutePath,
            )
            val current = _localBooks.value.filterNot { it.id == importedItem.id }
            _localBooks.value = listOf(importedItem) + current
            importedItem
        }.getOrNull()
    }

    private fun fetchArchiveOrgBooks(rawQuery: String, rows: Int = 35): List<BookItem> = runCatching {
        val encodedQ = URLEncoder.encode(rawQuery, "UTF-8")
        val apiUrl = "https://archive.org/advancedsearch.php?q=$encodedQ&fl[]=identifier,title,creator&sort[]=downloads+desc&rows=$rows&page=1&output=json"
        val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("User-Agent", "CHORT/1.4")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val docs = JSONObject(body).optJSONObject("response")?.optJSONArray("docs") ?: JSONArray()
        buildList {
            for (i in 0 until docs.length()) {
                val obj = docs.optJSONObject(i) ?: continue
                val id = obj.optString("identifier").takeIf { it.isNotBlank() && !it.endsWith(".7z") } ?: continue
                val title = obj.optString("title").takeIf { it.isNotBlank() } ?: continue
                val creator = obj.optString("creator").takeIf { it.isNotBlank() } ?: "مكتبة الأرشيف العربي"
                val localFile = File(booksDir, "archive_${id.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")}.pdf")
                add(
                    BookItem(
                        id = "archive_$id",
                        title = title,
                        author = creator,
                        path = localFile.absolutePath,
                        format = BookFormat.PDF,
                        coverUri = "https://archive.org/services/img/$id",
                        folderPath = booksDir.absolutePath,
                        isOnline = !localFile.exists(),
                        downloadUrl = "archive://$id",
                        language = "ar",
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    suspend fun loadArabicCatalog(): List<BookItem> = withContext(Dispatchers.IO) {
        val archive = fetchArchiveOrgBooks(
            "language:ara AND mediatype:texts AND format:\"Text PDF\"",
            rows = 40,
        )
        val combined = (CURATED_ARABIC_BOOKS + archive + CURATED_ONLINE_BOOKS).distinctBy { it.id }
        _onlineBooks.value = combined
        combined
    }

    suspend fun loadIslamicCatalog(): List<BookItem> = withContext(Dispatchers.IO) {
        val archive = fetchArchiveOrgBooks(
            "(title:صحيح OR title:رياض OR title:فقه OR title:سيرة OR title:حديث OR title:ابن القيم OR title:النووي OR title:الغزالي) AND mediatype:texts AND format:\"Text PDF\"",
            rows = 40,
        )
        val combined = (CURATED_ISLAMIC_BOOKS + archive).distinctBy { it.id }
        _islamicBooks.value = combined
        combined
    }

    suspend fun loadQuranCatalog(): List<BookItem> = withContext(Dispatchers.IO) {
        val archive = fetchArchiveOrgBooks(
            "(title:تفسير OR title:مصحف OR title:القرآن OR title:تجويد OR title:ابن كثير OR title:السعدي OR title:القرطبي) AND mediatype:texts AND format:\"Text PDF\"",
            rows = 40,
        )
        val combined = (CURATED_QURAN_BOOKS + archive).distinctBy { it.id }
        _quranBooks.value = combined
        combined
    }

    suspend fun searchOnlineBooks(query: String): List<BookItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val allCurated = CURATED_QURAN_BOOKS + CURATED_ISLAMIC_BOOKS + CURATED_ARABIC_BOOKS + CURATED_ONLINE_BOOKS
        if (trimmed.isBlank()) {
            return@withContext loadArabicCatalog()
        }
        val curatedMatches = allCurated.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
                it.author.contains(trimmed, ignoreCase = true) ||
                it.language.contains(trimmed, ignoreCase = true)
        }
        val archiveMatches = fetchArchiveOrgBooks(
            "($trimmed) AND mediatype:texts AND format:\"Text PDF\"",
            rows = 35,
        )
        (curatedMatches + archiveMatches).distinctBy { it.id }
    }

    private fun resolveArchivePdfUrl(identifier: String): String? = runCatching {
        val metaUrl = "https://archive.org/metadata/$identifier/files"
        val conn = (URL(metaUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("User-Agent", "CHORT/1.4")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val result = JSONObject(body).optJSONArray("result") ?: return null
        var bestPdf: String? = null
        for (i in 0 until result.length()) {
            val f = result.optJSONObject(i) ?: continue
            val name = f.optString("name")
            if (name.endsWith(".pdf", ignoreCase = true)) {
                bestPdf = name
                if (!name.contains("_text.pdf", ignoreCase = true)) break
            }
        }
        val chosen = bestPdf ?: return null
        val encodedName = chosen.split("/").joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        "https://archive.org/download/$identifier/$encodedName"
    }.getOrNull()

    suspend fun ensureBookDownloaded(book: BookItem): BookItem? = withContext(Dispatchers.IO) {
        val targetPath = book.path.takeIf { it.isNotBlank() }
            ?: File(booksDir, "${book.id}.${book.format.ext}").absolutePath
        val file = File(targetPath)
        if (file.exists() && file.length() > 64L) return@withContext book.copy(path = targetPath, isOnline = false)
        val rawUrl = book.downloadUrl ?: return@withContext null
        if (rawUrl.startsWith("builtin://")) {
            val content = BUILTIN_BOOK_TEXTS[rawUrl.removePrefix("builtin://")] ?: book.title
            file.writeText(content)
            val updated = book.copy(path = targetPath, isOnline = false, sizeBytes = file.length())
            scanLocalBooks()
            return@withContext updated
        }
        val urlStr = if (rawUrl.startsWith("archive://")) {
            resolveArchivePdfUrl(rawUrl.removePrefix("archive://")) ?: return@withContext null
        } else {
            rawUrl
        }
        runCatching {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 45000
                setRequestProperty("User-Agent", "CHORT/1.4")
            }
            conn.inputStream.use { input ->
                FileOutputStream(file).use { out -> input.copyTo(out) }
            }
            val updated = book.copy(path = targetPath, isOnline = false, sizeBytes = file.length())
            scanLocalBooks()
            updated
        }.getOrNull()
    }

    /**
     * Loads the text pages of a TXT / EPUB / FB2 book (~1400 chars per page so each page reads
     * comfortably inside the screen and can be read aloud by TTS).
     */
    suspend fun loadTextPages(book: BookItem): List<String> = withContext(Dispatchers.IO) {
        val file = File(book.path)
        if (!file.exists()) return@withContext listOf("Book file not found.")
        val fullText = when (book.format) {
            BookFormat.TXT -> runCatching { file.readText() }.getOrDefault("")
            BookFormat.MD -> runCatching {
                file.readText()
                    .replace(Regex("</(p|div|h1|h2|h3|h4|li|br)[^>]*>", RegexOption.IGNORE_CASE), "\n")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&nbsp;", " ")
            }.getOrDefault("")
            BookFormat.DOC -> extractDocOrDocxText(file)
            BookFormat.FB2 -> runCatching {
                file.readText()
                    .replace(Regex("<section[^>]*>"), "\n\n")
                    .replace(Regex("<p[^>]*>"), "\n")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&nbsp;", " ")
            }.getOrDefault("")
            BookFormat.EPUB -> extractEpubText(file)
            else -> ""
        }.trim()

        if (fullText.isBlank()) return@withContext listOf(book.title)
        paginateText(fullText, charsPerPage = 1350)
    }

    private fun extractDocOrDocxText(file: File): String = runCatching {
        val lower = file.name.lowercase()
        if (lower.endsWith(".docx") || lower.endsWith(".odt")) {
            val sb = StringBuilder()
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("word/document.xml") ?: zip.getEntry("content.xml")
                if (entry != null) {
                    val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    val clean = xml
                        .replace(Regex("</(w:p|text:p)[^>]*>"), "\n\n")
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&#39;", "'")
                        .trim()
                    sb.append(clean)
                }
            }
            if (sb.isNotBlank()) return@runCatching sb.toString()
        }
        // Fallback for .doc / .rtf
        file.readText()
            .replace(Regex("\\{\\\\[^{}]+\\}"), "")
            .replace(Regex("\\\\[a-zA-Z]+\\d* ?"), " ")
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), " ")
            .trim()
    }.getOrDefault("")

    private fun extractEpubText(file: File): String = runCatching {
        val sb = StringBuilder()
        ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory && (it.name.endsWith(".xhtml", true) || it.name.endsWith(".html", true) || it.name.endsWith(".htm", true)) }
                .sortedBy { it.name }
                .toList()
            val tagRegex = Regex("<[^>]+>")
            for (entry in entries) {
                val raw = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                val body = raw.substringAfter("<body", raw).substringAfter(">")
                val clean = body
                    .replace(Regex("</(p|div|h1|h2|h3|h4|li|br)[^>]*>", RegexOption.IGNORE_CASE), "\n")
                    .replace(tagRegex, "")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace(Regex("\n{3,}"), "\n\n")
                    .trim()
                if (clean.length > 20) {
                    sb.append(clean).append("\n\n")
                }
            }
        }
        sb.toString()
    }.getOrDefault("")

    private fun paginateText(text: String, charsPerPage: Int): List<String> {
        val paragraphs = text.split(Regex("\n+"))
        val pages = ArrayList<String>()
        val current = StringBuilder()
        for (para in paragraphs) {
            val trimmed = para.trim()
            if (trimmed.isEmpty()) continue
            if (current.length + trimmed.length + 2 > charsPerPage && current.isNotEmpty()) {
                pages.add(current.toString().trim())
                current.clear()
            }
            if (trimmed.length > charsPerPage) {
                var idx = 0
                while (idx < trimmed.length) {
                    val end = minOf(idx + charsPerPage, trimmed.length)
                    pages.add(trimmed.substring(idx, end))
                    idx = end
                }
            } else {
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(trimmed)
            }
        }
        if (current.isNotEmpty()) pages.add(current.toString().trim())
        return pages.ifEmpty { listOf(text.take(charsPerPage)) }
    }

    suspend fun getPdfOrCbzPageCount(book: BookItem): Int = withContext(Dispatchers.IO) {
        val file = File(book.path)
        if (!file.exists()) return@withContext 0
        when (book.format) {
            BookFormat.PDF -> runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { it.pageCount }
                }
            }.getOrDefault(0)
            BookFormat.CBZ -> runCatching {
                ZipFile(file).use { zip ->
                    zip.entries().asSequence().count { e ->
                        !e.isDirectory && (e.name.endsWith(".jpg", true) || e.name.endsWith(".jpeg", true) || e.name.endsWith(".png", true) || e.name.endsWith(".webp", true))
                    }
                }
            }.getOrDefault(0)
            else -> 0
        }
    }

    suspend fun renderPdfOrCbzPage(book: BookItem, pageIndex: Int, maxWidthPx: Int = 1400): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(book.path)
        if (!file.exists()) return@withContext null
        when (book.format) {
            BookFormat.PDF -> runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex !in 0 until renderer.pageCount) return@use null
                        renderer.openPage(pageIndex).use { page ->
                            val w = maxWidthPx.coerceIn(400, 2200)
                            val h = ((w.toFloat() / page.width.coerceAtLeast(1)) * page.height).toInt().coerceAtLeast(400)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            }.getOrNull()
            BookFormat.CBZ -> runCatching {
                ZipFile(file).use { zip ->
                    val images = zip.entries().asSequence()
                        .filter { e -> !e.isDirectory && (e.name.endsWith(".jpg", true) || e.name.endsWith(".jpeg", true) || e.name.endsWith(".png", true) || e.name.endsWith(".webp", true)) }
                        .sortedBy { it.name }
                        .toList()
                    val entry = images.getOrNull(pageIndex) ?: return@use null
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }.getOrNull()
            else -> null
        }
    }

    private fun ensureBundledSampleBooks() {
        val arSample = File(booksDir, "مقدمة_ابن_خلدون_مختارات.txt")
        if (!arSample.exists()) {
            arSample.writeText(
                """
                مقدمة ابن خلدون - في فضل علم التاريخ وتحقيق مذاهبه
                
                اعلم أن فن التاريخ فن عزيز المذهب، جم الفوائد، شريف الغاية، إذ هو يوقفنا على أحوال الماضين من الأمم في أخلاقهم، والأنبياء في سيرهم، والملوك في دولهم وسياستهم، حتى تتم فائدة الاقتداء في ذلك لمن يرومه في أحوال الدين والدنيا.
                
                فهو محتاج إلى مآخذ متعددة ومعارف متنوعة وحسن نظر وتثبت يفضيان بصاحبهما إلى الحق وينكبان به عن المزلات والمغالط، لأن الأخبار إذا اعتمد فيها على مجرد النقل ولم تحكم أصول العادة وقواعد السياسة وطبيعة العمران والأحوال في الاجتماع الإنساني، ولا قيس الغائب منها بالشاهد والحاضر بالذاهب، فربما لم يؤمن فيها من العثور ومزلة القدم والحيد عن جادة الصدق.
                
                الفصل الثاني: في طبيعة العمران البشري
                إن الاجتماع الإنساني ضروري، ويعبر الحكماء عن هذا بقولهم: الإنسان مدني بالطبع، أي لا بد له من الاجتماع الذي هو المدينة في اصطلاحهم، وهو معنى العمران. وبيانه أن الله سبحانه خلق الإنسان وركبه على صورة لا يصح حياتها وبقاؤها إلا بالغذاء، وهداه إلى التماسه بفطرته وبما ركب فيه من القدرة على تحصيله، إلا أن قدرة الواحد من البشر قاصرة على تحصيل حاجته من ذلك الغذاء، فلا بد من اجتماع القدر الكثير من أبناء جنسه ليحصل القوت له ولهم، فيحصل بالتعاون قدر الكفاية من الحاجة لأكثر منهم بأضعاف.
                """.trimIndent(),
            )
        }
        val enSample = File(booksDir, "The_Art_of_War_Sun_Tzu.txt")
        if (!enSample.exists()) {
            enSample.writeText(
                """
                The Art of War — Sun Tzu
                
                Chapter I: Laying Plans
                
                1. Sun Tzu said: The art of war is of vital importance to the State.
                2. It is a matter of life and death, a road either to safety or to ruin. Hence it is a subject of inquiry which can on no account be neglected.
                3. The art of war, then, is governed by five constant factors, to be taken into account in one's deliberations, when seeking to determine the conditions obtaining in the field.
                4. These are: (1) The Moral Law; (2) Heaven; (3) Earth; (4) The Commander; (5) Method and discipline.
                
                5. The Moral Law causes the people to be in complete accord with their ruler, so that they will follow him regardless of their lives, undismayed by any danger.
                6. Heaven signifies night and day, cold and heat, times and seasons.
                7. Earth comprises distances, great and small; danger and security; open ground and narrow passes; the chances of life and death.
                8. The Commander stands for the virtues of wisdom, sincerity, benevolence, courage and strictness.
                9. By method and discipline are to be understood the marshaling of the army in its proper subdivisions, the graduations of rank among the officers, the maintenance of roads by which supplies may reach the army, and the control of military expenditure.
                """.trimIndent(),
            )
        }
    }

    companion object {
        private val BUILTIN_BOOK_TEXTS = mapOf(
            "tafsir_muyassar" to """
                التفسير الميسر للقرآن الكريم
                
                سورة الفاتحة:
                ﴿بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ﴾ سورة الفاتحة سميت هذه السورة بالفاتحة؛ لأنه يفتتح بها القرآن العظيم، وتسمى المثاني؛ لأنها تقرأ في كل ركعة، ولها أسماء أخر. أبتدئ قراءتي للقرآن باسم الله مستعينا به، (اللهِ) علم على الرب -تبارك وتعالى- المعبود بحق دون سواه، وهو أخص أسماء الله تعالى، ولا يسمى به غيره سبحانه. (الرَّحْمَنِ) ذي الرحمة العامة الذي وسعت رحمته جميع الخلق، (الرَّحِيمِ) بالمؤمنين، وهما اسمان من أسمائه تعالى يتضمنان إثبات صفة الرحمة لله تعالى كما يليق بجلاله.
                
                ﴿الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ﴾ الثناء الكامل، وبجميع أنواع المحامد من صفات الجلال والكمال هو لله وحده دون من سواه؛ إذ هو رب كل شيء وخالقه ومدبره. والعالمون جمع عالم، وهم كل ما سوى الله تعالى.
                
                ﴿الرَّحْمَنِ الرَّحِيمِ * مَالِكِ يَوْمِ الدِّينِ﴾ ثناء على الله تعالى بعد حمده في الآية السابقة، تمجيد له بأنه المالك لكل ما في يوم القيامة، حيث لا تملك نفس لنفس شيئا، ويوم الدين يوم الجزاء والحساب.
                
                ﴿إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ﴾ نخصك وحدك بالعبادة، ونستعين بك وحدك في جميع أمورنا، فالأمر كله بيدك، لا يملك منه أحد مثقال ذرة. وفي هذه الآية دليل على أن العبد لا يجوز له أن يصرف شيئا من أنواع العبادة كالدعاء والاستغاثة والذبح والطواف إلا لله وحده، وفيها شفاء القلوب من داء التعلق بغير الله، ومن أمراض الرياء والعجب والكبرياء.
                
                ﴿اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ * صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ﴾ دلنا، وأرشدنا، ووفقنا إلى الطريق المستقيم، وثبتنا عليه حتى نلقاك، وهو الإسلام، الذي هو الطريق الواضح الموصل إلى رضوان الله وإلى جنته، الذي دل عليه خاتم رسله وأنبيائه محمد صلى الله عليه وسلم.
            """.trimIndent(),
            "arbain_nawawi" to """
                الأربعون النووية — الإمام يحيى بن شرف النووي
                
                الحديث الأول: الأعمال بالنيات
                عن أمير المؤمنين أبي حفص عمر بن الخطاب رضي الله عنه قال: سمعت رسول الله صلى الله عليه وسلم يقول: «إنما الأعمال بالنيات، وإنما لكل امرئ ما نوى، فمن كانت هجرته إلى الله ورسوله فهجرته إلى الله ورسوله، ومن كانت هجرته لدنيا يصيبها أو امرأة ينكحها فهجرته إلى ما هاجر إليه» رواه إماما المحدثين أبو عبد الله محمد بن إسماعيل البخاري وأبو الحسين مسلم بن الحجاج النيسابوري في صحيحيهما.
                
                الحديث الثاني: مراتب الدين (الإسلام والإيمان والإحسان)
                عن عمر رضي الله عنه أيضا قال: بينما نحن جلوس عند رسول الله صلى الله عليه وسلم ذات يوم إذ طلع علينا رجل شديد بياض الثياب شديد سواد الشعر، لا يرى عليه أثر السفر، ولا يعرفه منا أحد، حتى جلس إلى النبي صلى الله عليه وسلم فأسند ركبتيه إلى ركبتيه ووضع كفيه على فخذيه، وقال: يا محمد أخبرني عن الإسلام؟ فقال رسول الله صلى الله عليه وسلم: «الإسلام أن تشهد أن لا إله إلا الله وأن محمدا رسول الله، وتقيم الصلاة، وتؤتي الزكاة، وتصوم رمضان، وتحج البيت إن استطعت إليه سبيلا».
                
                الحديث الثالث: أركان الإسلام
                عن أبي عبد الرحمن عبد الله بن عمر بن الخطاب رضي الله عنهما قال: سمعت رسول الله صلى الله عليه وسلم يقول: «بني الإسلام على خمس: شهادة أن لا إله إلا الله وأن محمدا رسول الله، وإقام الصلاة، وإيتاء الزكاة، وحج البيت، وصوم رمضان» رواه البخاري ومسلم.
                
                الحديث السادس: الحلال بين والحرام بين
                عن أبي عبد الله النعمان بن بشير رضي الله عنهما قال: سمعت رسول الله صلى الله عليه وسلم يقول: «إن الحلال بين وإن الحرام بين، وبينهما أمور مشتبهات لا يعلمهن كثير من الناس، فمن اتقى الشبهات فقد استبرأ لدينه وعرضه، ومن وقع في الشبهات وقع في الحرام... ألا وإن في الجسد مضغة إذا صلحت صلح الجسد كله وإذا فسدت فسد الجسد كله ألا وهي القلب» رواه البخاري ومسلم.
            """.trimIndent(),
            "riyad_salihin" to """
                رياض الصالحين من كلام سيد المرسلين — الإمام النووي
                
                باب الإخلاص وإحضار النية في جميع الأعمال والأقوال البارزة والخفية:
                قال الله تعالى: ﴿وَمَا أُمِرُوا إِلَّا لِيَعْبُدُوا اللَّهَ مُخْلِصِينَ لَهُ الدِّينَ حُنَفَاءَ وَيُقِيمُوا الصَّلَاةَ وَيُؤْتُوا الزَّكَاةَ وَذَلِكَ دِينُ الْقَيِّمَةِ﴾.
                وقال تعالى: ﴿لَنْ يَنَالَ اللَّهَ لُحُومُهَا وَلَا دِمَاؤُهَا وَلَكِنْ يَنَالُهُ التَّقْوَى مِنْكُمْ﴾.
                
                وعن أبي هريرة عبد الرحمن بن صخر رضي الله عنه قال: قال رسول الله صلى الله عليه وسلم: «إن الله لا ينظر إلى أجسامكم، ولا إلى صوركم، ولكن ينظر إلى قلوبكم وأعمالكم» رواه مسلم.
                
                باب التوبة:
                قال العلماء: التوبة واجبة من كل ذنب، فإن كانت المعصية بين العبد وبين الله تعالى لا تتعلق بحق آدمي فلها ثلاثة شروط: أحدها أن يقلع عن المعصية، والثاني أن يندم على فعلها، والثالث أن يعزم أن لا يعود إليها أبدا.
                
                باب الصبر:
                قال الله تعالى: ﴿يَا أَيُّهَا الَّذِينَ آمَنُوا اصْبِرُوا وَصَابِرُوا وَرَابِطُوا وَاتَّقُوا اللَّهَ لَعَلَّكُمْ تُفْلِحُونَ﴾.
                وعن أبي مالك الحارث بن عاصم الأشعري رضي الله عنه قال: قال رسول الله صلى الله عليه وسلم: «الطهور شطر الإيمان، والحمد لله تملأ الميزان، وسبحان الله والحمد لله تملآن أو تملأ ما بين السماوات والأرض، والصلاة نور، والصدقة برهان، والصبر ضياء، والقرآن حجة لك أو عليك» رواه مسلم.
            """.trimIndent(),
            "hisn_muslim" to """
                حصن المسلم من أذكار الكتاب والسنة — سعيد بن علي بن وهف القحطاني
                
                1. أذكار الاستيقاظ من النوم:
                - «الحمد لله الذي أحيانا بعد ما أماتنا وإليه النشور».
                - «لا إله إلا الله وحده لا شريك له، له الملك وله الحمد، وهو على كل شيء قدير، سبحان الله، والحمد لله، ولا إله إلا الله، والله أكبر، ولا حول ولا قوة إلا بالله العلي العظيم، رب اغفر لي».
                
                2. أذكار الصباح والمساء:
                - قراءة آية الكرسي: ﴿اللَّهُ لَا إِلَهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ...﴾.
                - قراءة المعوذات (سورة الإخلاص، وسورة الفلق، وسورة الناس) ثلاث مرات.
                - سيد الاستغفار: «اللهم أنت ربي لا إله إلا أنت، خلقتني وأنا عبدك، وأنا على عهدك ووعدك ما استطعت، أعوذ بك من شر ما صنعت، أبوء لك بنعمتك علي، وأبوء بذنبي فاغفر لي فإنه لا يغفر الذنوب إلا أنت».
                - «بسم الله الذي لا يضر مع اسمه شيء في الأرض ولا في السماء وهو السميع العليم» (ثلاث مرات).
                - «حسبي الله لا إله إلا هو عليه توكلت وهو رب العرش العظيم» (سبع مرات).
            """.trimIndent(),
            "kalila_dimna" to """
                كليلة ودمنة — عبد الله بن المقفع
                
                مقدمة الكتاب:
                هذا كتاب كليلة ودمنة، وهو مما وضعه علماء الهند من الأمثال والأحاديث التي ألهموا أن يدخلوها من أبلغ ما وجدوا من القول في النحو الذي أرادوا، ونقلوه إلى الفارسية، ثم ترجمه عبد الله بن المقفع إلى اللسان العربي الفصيح.
                
                باب الأسد والثور:
                زعموا أن تاجرا كان له بنون ثلاثة، فلما أدركوا أسرفوا في إتلاف المال، فوعظهم أبوهم وقال: يا بني، إن صاحب الدنيا يطلب ثلاثة أمور لن يدركها إلا بأربعة أشياء: أما الثلاثة التي يطلب فالسعة في المعيشة، والمنزلة في الناس، والزاد للآخرة. وأما الأربعة التي يحتاج إليها في درك هذه الثلاثة فاكتساب المال من أحسن وجه يكون، ثم حسن القيام على ما اكتسب منه، ثم استثماره، ثم إنفاقه فيما يصلح المعيشة ويرضي الأهل والإخوان، فيعود عليه نفعه في الآخرة.
            """.trimIndent(),
        )

        private val CURATED_QURAN_BOOKS = listOf(
            BookItem(
                id = "quran_mushaf_madinah",
                title = "المصحف الشريف - مصحف المدينة النبوية",
                author = "مجمع الملك فهد لطباعة المصحف الشريف",
                path = "",
                format = BookFormat.PDF,
                coverUri = "https://archive.org/services/img/bensaoud_gmail_20170308_0721",
                isOnline = true,
                downloadUrl = "archive://bensaoud_gmail_20170308_0721",
                language = "ar",
            ),
            BookItem(
                id = "quran_tafsir_muyassar_txt",
                title = "التفسير الميسر للقرآن الكريم",
                author = "نخبة من العلماء",
                path = "",
                format = BookFormat.TXT,
                coverUri = "https://archive.org/services/img/FP82265",
                isOnline = true,
                downloadUrl = "builtin://tafsir_muyassar",
                language = "ar",
            ),
            BookItem(
                id = "quran_tafsir_saadi",
                title = "تيسير الكريم الرحمن في تفسير كلام المنان (تفسير السعدي)",
                author = "الشيخ عبد الرحمن بن ناصر السعدي",
                path = "",
                format = BookFormat.PDF,
                coverUri = "https://archive.org/services/img/FP2021",
                isOnline = true,
                downloadUrl = "archive://FP2021",
                language = "ar",
            ),
            BookItem(
                id = "quran_tafsir_ibn_kathir",
                title = "تفسير القرآن العظيم (مختصر تفسير ابن كثير)",
                author = "الحافظ ابن كثير",
                path = "",
                format = BookFormat.PDF,
                coverUri = "https://archive.org/services/img/waq79569",
                isOnline = true,
                downloadUrl = "archive://waq79569",
                language = "ar",
            ),
            BookItem(
                id = "quran_tibyan_nawawi",
                title = "التبيان في آداب حملة القرآن",
                author = "الإمام النووي",
                path = "",
                format = BookFormat.PDF,
                coverUri = "https://archive.org/services/img/waq108620",
                isOnline = true,
                downloadUrl = "archive://waq108620",
                language = "ar",
            ),
            BookItem(
                id = "quran_asbab_nuzul",
                title = "أسباب النزول",
                author = "الإمام الواحدي",
                path = "",
                format = BookFormat.PDF,
                coverUri = "https://archive.org/services/img/waq15623",
                isOnline = true,
                downloadUrl = "archive://waq15623",
                language = "ar",
            ),
        )

        private val CURATED_ISLAMIC_BOOKS = listOf(
            BookItem(
                id = "islamic_arbain_nawawi",
                title = "الأربعون النووية (متن وشرح)",
                author = "الإمام يحيى بن شرف النووي",
                path = "",
                format = BookFormat.TXT,
                coverUri = "https://archive.org/services/img/FP48454",
                isOnline = true,
                downloadUrl = "builtin://arbain_nawawi",
                language = "ar",
            ),
            BookItem(
                id = "islamic_riyad_salihin",
                title = "رياض الصالحين من كلام سيد المرسلين",
                author = "الإمام النووي",
                path = "",
                format = BookFormat.TXT,
                coverUri = "https://archive.org/services/img/waq89021",
                isOnline = true,
                downloadUrl = "builtin://riyad_salihin",
                language = "ar",
            ),
            BookItem(
                id = "islamic_hisn_muslim",
                title = "حصن المسلم من أذكار الكتاب والسنة",
                author = "سعيد بن علي بن وهف القحطاني",
                path = "",
                format = BookFormat.TXT,
                coverUri = "https://archive.org/services/img/FP11999",
                isOnline = true,
                downloadUrl = "builtin://hisn_muslim",
                language = "ar",
            ),
            BookItem(
                id = "islamic_raheeq_makhtum",
                title = "الرحيق المختوم (السيرة النبوية)",
                author = "صفي الرحمن المباركفوري",
                path = "",
                format = BookFormat.PDF,
                coverUri = "https://archive.org/services/img/waq51292",
                isOnline = true,
                downloadUrl = "archive://waq51292",
                language = "ar",
            ),
            BookItem(
                id = "islamic_daa_dawaa",
                title = "الداء والدواء (الجواب الكافي)",
                author = "الإمام ابن قيم الجوزية",
                path = "",
                format = BookFormat.PDF,
                coverUri = "https://archive.org/services/img/waq47656",
                isOnline = true,
                downloadUrl = "archive://waq47656",
                language = "ar",
            ),
            BookItem(
                id = "islamic_fiqh_sunnah",
                title = "فقه السنة",
                author = "الشيخ سيد سابق",
                path = "",
                format = BookFormat.PDF,
                coverUri = "https://archive.org/services/img/waq16611",
                isOnline = true,
                downloadUrl = "archive://waq16611",
                language = "ar",
            ),
        )

        private val CURATED_ARABIC_BOOKS = listOf(
            BookItem(
                id = "ar_kalila_dimna",
                title = "كليلة ودمنة",
                author = "عبد الله بن المقفع",
                path = "",
                format = BookFormat.TXT,
                coverUri = "https://archive.org/services/img/kalilawadimna00bidp",
                isOnline = true,
                downloadUrl = "builtin://kalila_dimna",
                language = "ar",
            ),
            BookItem(
                id = "ar_paranormal_tawfik",
                title = "سلسلة ما وراء الطبيعة",
                author = "د. أحمد خالد توفيق",
                path = "",
                format = BookFormat.PDF,
                coverUri = "https://archive.org/services/img/KAT2018",
                isOnline = true,
                downloadUrl = "archive://KAT2018",
                language = "ar",
            ),
        )

        private val CURATED_ONLINE_BOOKS = listOf(
            BookItem(
                id = "gutenberg_132",
                title = "The Art of War",
                author = "Sun Tzu",
                path = "",
                format = BookFormat.TXT,
                coverUri = "https://www.gutenberg.org/cache/epub/132/pg132.cover.medium.jpg",
                isOnline = true,
                downloadUrl = "https://www.gutenberg.org/cache/epub/132/pg132.txt",
                language = "en",
            ),
            BookItem(
                id = "gutenberg_1661",
                title = "The Adventures of Sherlock Holmes",
                author = "Arthur Conan Doyle",
                path = "",
                format = BookFormat.TXT,
                coverUri = "https://www.gutenberg.org/cache/epub/1661/pg1661.cover.medium.jpg",
                isOnline = true,
                downloadUrl = "https://www.gutenberg.org/cache/epub/1661/pg1661.txt",
                language = "en",
            ),
            BookItem(
                id = "gutenberg_58585",
                title = "The Prophet (النبي)",
                author = "Kahlil Gibran (جبران خليل جبران)",
                path = "",
                format = BookFormat.TXT,
                coverUri = "https://www.gutenberg.org/cache/epub/58585/pg58585.cover.medium.jpg",
                isOnline = true,
                downloadUrl = "https://www.gutenberg.org/cache/epub/58585/pg58585.txt",
                language = "en",
            ),
            BookItem(
                id = "gutenberg_1342",
                title = "Pride and Prejudice",
                author = "Jane Austen",
                path = "",
                format = BookFormat.TXT,
                coverUri = "https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg",
                isOnline = true,
                downloadUrl = "https://www.gutenberg.org/cache/epub/1342/pg1342.txt",
                language = "en",
            ),
            BookItem(
                id = "gutenberg_11",
                title = "Alice's Adventures in Wonderland",
                author = "Lewis Carroll",
                path = "",
                format = BookFormat.TXT,
                coverUri = "https://www.gutenberg.org/cache/epub/11/pg11.cover.medium.jpg",
                isOnline = true,
                downloadUrl = "https://www.gutenberg.org/cache/epub/11/pg11.txt",
                language = "en",
            ),
            BookItem(
                id = "gutenberg_84",
                title = "Frankenstein",
                author = "Mary Shelley",
                path = "",
                format = BookFormat.TXT,
                coverUri = "https://www.gutenberg.org/cache/epub/84/pg84.cover.medium.jpg",
                isOnline = true,
                downloadUrl = "https://www.gutenberg.org/cache/epub/84/pg84.txt",
                language = "en",
            ),
        )
    }
}
