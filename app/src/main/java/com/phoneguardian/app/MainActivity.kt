package com.phoneguardian.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.OpenableColumns
import java.security.MessageDigest
import java.util.zip.CRC32
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.graphics.Typeface
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private val navy = Color.rgb(15, 28, 52)
    private val blue = Color.rgb(31, 102, 190)
    private val pale = Color.rgb(239, 244, 250)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun baseLayout(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 24)
            setBackgroundColor(pale)
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        return root
    }

    private fun title(text: String, size: Float = 22f): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(navy)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(8, 14, 8, 14)
        }

    private fun paragraph(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.DKGRAY)
            setPadding(8, 10, 8, 16)
        }

    private fun actionButton(label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(blue)
            setOnClickListener { action() }
        }
        content.addView(button, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 12 })
    }

    private fun showHome() {
        content = baseLayout()
        content.addView(title("🛡️ Phone Guardian", 27f))
        content.addView(paragraph("حماية هاتفك تبدأ بمعرفة التطبيقات والإعدادات المهمة. هذه نسخة تطويرية بأدوات محلية ولا تحتاج إلى Root أو Shizuku."))
        actionButton("📱 معلومات الهاتف") { showDeviceInfo() }
        actionButton("📦 التطبيقات الظاهرة في القائمة") { showApps() }
        actionButton("🔐 مراقبة أذونات التطبيقات") { showPermissionAudit() }
        actionButton("🔐 إعدادات أمان Android") {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }
        actionButton("🔎 فحص رابط متقدم") { showLinkCheck() }
        actionButton("🛡️ فحص الصور وPDF والفيديو والصوت") { chooseFileToScan() }
        actionButton("🗂️ سجل تقارير الفحص") { showHistory() }
        content.addView(paragraph("فحص الروابط يعتمد على مؤشرات محلية ولا يفتح الرابط أو يتصل بأي خادم. النتيجة ليست ضماناً لسلامة الموقع."))
    }


    private val filePickerRequest = 4107
    private fun chooseFileToScan() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, filePickerRequest)
    }
    @Deprecated("Deprecated in Android API; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == filePickerRequest && resultCode == RESULT_OK) data?.data?.let { scanSelectedFile(it) }
    }
    private fun scanSelectedFile(uri: Uri) {
        content = baseLayout()
        content.addView(title("📁 فحص الملف"))
        val result = paragraph("جارٍ قراءة الملف وفحص مؤشرات نوعه...")
        content.addView(result)
        var selectedName = "ملف غير معروف"
        var selectedSize = -1L
        var selectedMime = "غير معروف"
        val scanTask = Thread({
        try {
            val metadata = contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0)
                    val size = if (c.isNull(1)) -1L else c.getLong(1)
                    name to size
                } else null
            }
            val displayName = metadata?.first ?: "ملف غير معروف"
            val reportedSize = metadata?.second ?: -1L
            selectedName = displayName
            selectedSize = reportedSize
            val maxScanBytes = 64L * 1024L * 1024L
            if (reportedSize > maxScanBytes) {
                throw IllegalStateException("حجم الملف يتجاوز حد الفحص المحلي (64 ميغابايت). لم يتم تحميله إلى الذاكرة أو تشغيله.")
            }
            val mime = contentResolver.getType(uri)?.lowercase(Locale.ROOT) ?: "غير معروف"
            selectedMime = mime
            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                if (reportedSize >= 0L) {
                    if (reportedSize > maxScanBytes) {
                        throw IllegalStateException("حجم الملف يتجاوز حد الفحص المحلي (64 ميغابايت).")
                    }
                    // Allocate once when the document provider supplies a trustworthy size.
                    // This avoids ByteArrayOutputStream.toByteArray() duplicating the whole file in memory.
                    val expectedSize = reportedSize.toInt()
                    val data = ByteArray(expectedSize)
                    var offset = 0
                    while (offset < expectedSize) {
                        val n = input.read(data, offset, expectedSize - offset)
                        if (n < 0) break
                        offset += n
                    }
                    if (offset == expectedSize && input.read() != -1) {
                        throw IllegalStateException("حجم المحتوى أكبر من الحجم المعلن؛ أوقف الفحص لحماية ذاكرة الهاتف.")
                    }
                    if (offset == expectedSize) data else data.copyOf(offset)
                } else {
                    // Unknown-size providers are read incrementally and stopped at the hard limit.
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (out.size().toLong() + n.toLong() > maxScanBytes) {
                            throw IllegalStateException("تجاوز الملف حد الفحص المحلي (64 ميغابايت). أوقف الفحص لحماية ذاكرة الهاتف.")
                        }
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                }
            } ?: throw IllegalStateException("تعذرت قراءة الملف.")
            val findings = mutableListOf<String>()
            val lowerName = displayName.lowercase(Locale.ROOT)
            // Ignore trailing dots/spaces when determining the effective extension.
            val normalizedName = lowerName.trimEnd(' ', '.')
            val ext = normalizedName.substringAfterLast('.', "")
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val fileSizeText = String.format(Locale.ROOT, "%,d بايت (%.2f MB)", bytes.size, bytes.size / (1024.0 * 1024.0))
            fun has(vararg v: Int): Boolean = bytes.size >= v.size && v.indices.all { (bytes[it].toInt() and 255) == v[it] }
            val kind = when {
                has(0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a) -> "PNG"
                has(0xff,0xd8,0xff) -> "JPEG"
                has(0x47,0x49,0x46,0x38) -> "GIF"
                bytes.size >= 12 && String(bytes,0,4,Charsets.US_ASCII) == "RIFF" && String(bytes,8,4,Charsets.US_ASCII) == "WEBP" -> "WEBP"
                bytes.size >= 12 && String(bytes,0,4,Charsets.US_ASCII) == "RIFF" && String(bytes,8,4,Charsets.US_ASCII) == "WAVE" -> "WAV"
                bytes.size >= 12 && String(bytes,0,4,Charsets.US_ASCII) == "RIFF" && String(bytes,8,4,Charsets.US_ASCII) == "AVI " -> "AVI"
                bytes.size >= 12 && String(bytes,4,4,Charsets.US_ASCII) == "ftyp" -> "ISO-BMFF"
                bytes.size >= 3 && String(bytes,0,3,Charsets.US_ASCII) == "ID3" -> "MP3"
                bytes.size >= 4 && String(bytes,0,4,Charsets.US_ASCII) == "OggS" -> "OGG"
                bytes.size >= 4 && String(bytes,0,4,Charsets.US_ASCII) == "fLaC" -> "FLAC"
                bytes.size >= 4 && has(0x1a,0x45,0xdf,0xa3) -> "Matroska/WebM"
                bytes.size >= 2 && (bytes[0].toInt() and 255) == 0xff && ((bytes[1].toInt() and 0xe0) == 0xe0) -> "MP3/ MPEG audio"
                has(0x25,0x50,0x44,0x46) -> "PDF"
                has(0x4d,0x5a) -> "ملف تنفيذي Windows (MZ/PE محتمل)"
                has(0x7f,0x45,0x4c,0x46) -> "ملف تنفيذي Linux/Android (ELF)"
                has(0x50,0x4b,0x03,0x04) || has(0x50,0x4b,0x05,0x06) || has(0x50,0x4b,0x07,0x08) -> "أرشيف ZIP (قد يحتوي APK أو ملفات أخرى)"
                has(0x64,0x65,0x78,0x0a) -> "ملف DEX/Android"
                else -> "نوع غير محدد من الترويسة"
            }
            val imageExt = setOf("jpg","jpeg","png","gif","webp","bmp","heic","heif","tif","tiff")
            val riskyExt = setOf("apk","exe","msi","bat","cmd","scr","js","jar","sh","dex")
            if (ext in riskyExt) findings.add("امتداد قابل للتنفيذ أو قد يشغّل تعليمات برمجية؛ لا تفتحه إلا من مصدر موثوق.")
            val misleadingExt = setOf(
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic",
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
                "txt", "rtf", "csv", "mp3", "mp4", "avi", "mov",
                "zip", "rar", "7z"
            )
            val executableExt = setOf(
                "apk", "exe", "msi", "scr", "bat", "cmd", "com",
                "pif", "js", "vbs", "vbe", "wsf", "hta", "ps1",
                "jar", "sh", "dex", "dll", "cpl", "reg", "lnk"
            )
            val nameParts = lowerName.trimEnd(' ', '.').split('.')
            if (nameParts.size >= 3 &&
                nameParts.drop(1).dropLast(1).any { it in misleadingExt } &&
                nameParts.last() in executableExt
            ) {
                findings.add("اسم الملف يحتوي امتداداً مزدوجاً أو مخادعاً قد يخفي ملفاً قابلاً للتنفيذ.")
            }
            val isImage = kind in setOf("PNG","JPEG","GIF","WEBP") || (kind == "ISO-BMFF" && ext in setOf("heic","heif"))
            val isVideo = kind in setOf("AVI","Matroska/WebM") || (kind == "ISO-BMFF" && ext in setOf("mp4","m4v","mov"))
            val isAudio = kind in setOf("WAV","MP3","OGG","FLAC") || (kind == "ISO-BMFF" && ext in setOf("m4a","aac"))
            if (ext in imageExt && !isImage) findings.add("الامتداد يوحي بأنها صورة لكن ترويسة المحتوى لا تطابق صورة معروفة.")
            if (isImage && !mime.startsWith("image/") && mime != "غير معروف") findings.add("نوع MIME المعلن لا يتوافق بوضوح مع ترويسة الصورة.")

            fun hasNonPaddingTail(start: Int): Boolean =
                start < bytes.size && (start until bytes.size).any {
                    val value = bytes[it].toInt() and 255
                    value != 0 && value != 9 && value != 10 && value != 13 && value != 32
                }
            fun readU32BE(pos: Int): Long =
                ((bytes[pos].toLong() and 255L) shl 24) or
                    ((bytes[pos + 1].toLong() and 255L) shl 16) or
                    ((bytes[pos + 2].toLong() and 255L) shl 8) or
                    (bytes[pos + 3].toLong() and 255L)

            fun readU32LE(pos: Int): Long =
                (bytes[pos].toLong() and 255L) or
                    ((bytes[pos + 1].toLong() and 255L) shl 8) or
                    ((bytes[pos + 2].toLong() and 255L) shl 16) or
                    ((bytes[pos + 3].toLong() and 255L) shl 24)

            if (kind == "JPEG") {
                var end = -1
                for (i in 0 until bytes.size - 1) {
                    if ((bytes[i].toInt() and 255) == 0xff && (bytes[i + 1].toInt() and 255) == 0xd9) end = i + 2
                }
                if (end < 0) {
                    findings.add("لم يُعثر على علامة نهاية JPEG (FFD9)؛ قد تكون الصورة مبتورة.")
                } else if (hasNonPaddingTail(end)) {
                    findings.add("توجد بيانات غير فارغة بعد علامة نهاية JPEG؛ قد تكون بيانات ملحقة، وهذا ليس دليلاً وحده على البرمجيات الخبيثة.")
                }
            } else if (kind == "PNG") {
                var offset = 8
                var sawIhdr = false
                var sawIdat = false
                var idatEnded = false
                var sawIend = false
                var malformed = false
                var badCrcCount = 0

                while (offset < bytes.size) {
                    if (bytes.size - offset < 12) {
                        malformed = true
                        break
                    }

                    val chunkLength = readU32BE(offset)
                    if (chunkLength > Int.MAX_VALUE.toLong() ||
                        chunkLength > bytes.size.toLong() - offset.toLong() - 12L) {
                        malformed = true
                        break
                    }

                    val length = chunkLength.toInt()
                    val chunkType = String(bytes, offset + 4, 4, Charsets.US_ASCII)
                    val validType = chunkType.all { it in 'A'..'Z' || it in 'a'..'z' } &&
                        chunkType[2] in 'A'..'Z'
                    if (!validType) {
                        findings.add("تحتوي PNG على اسم كتلة غير صالح.")
                        malformed = true
                    }

                    if (!sawIhdr && chunkType != "IHDR") {
                        findings.add("كتلة IHDR ليست أول كتلة في PNG كما تتطلب الصيغة.")
                        malformed = true
                    }

                    if (chunkType == "IHDR") {
                        if (sawIhdr || offset != 8 || length != 13) {
                            findings.add("كتلة IHDR مكررة أو بطول غير صحيح.")
                            malformed = true
                        }
                        sawIhdr = true
                    }

                    if (chunkType == "IDAT") {
                        if (idatEnded) {
                            findings.add("كتل IDAT في PNG غير متتابعة كما تتطلب الصيغة.")
                            malformed = true
                        }
                        sawIdat = true
                    } else if (sawIdat && chunkType != "IEND") {
                        idatEnded = true
                    }

                    val crc = CRC32()
                    crc.update(bytes, offset + 4, 4 + length)
                    val expectedCrc = readU32BE(offset + 8 + length)
                    if (crc.value != expectedCrc) badCrcCount++

                    val critical = chunkType.firstOrNull()?.let { it in 'A'..'Z' } == true
                    if (critical && chunkType !in setOf("IHDR", "PLTE", "IDAT", "IEND")) {
                        findings.add("تحتوي PNG على كتلة حرجة غير معروفة ($chunkType)؛ قد لا تدعمها بعض قارئات الصور.")
                    }

                    offset += 12 + length
                    if (chunkType == "IEND") {
                        if (length != 0) {
                            findings.add("كتلة IEND في PNG يجب أن تكون بطول صفر.")
                            malformed = true
                        }
                        sawIend = true
                        break
                    }
                }

                if (!sawIhdr) findings.add("كتلة IHDR الأساسية غير موجودة في PNG.")
                if (!sawIdat) findings.add("كتلة بيانات الصورة IDAT غير موجودة في PNG.")
                if (!sawIend) findings.add("بنية PNG غير مكتملة أو لا تحتوي على كتلة نهاية IEND.")
                if (malformed) findings.add("بنية كتل PNG تحتوي على ترتيب أو طول غير صالح.")
                if (badCrcCount > 0) {
                    findings.add("فشل التحقق من CRC لعدد $badCrcCount من كتل PNG؛ قد يكون الملف تالفاً أو معدلاً.")
                }
                if (sawIend && hasNonPaddingTail(offset)) {
                    findings.add("توجد بيانات غير فارغة بعد نهاية بنية PNG؛ تستحق المراجعة، ولا تثبت وحدها وجود برمجية خبيثة.")
                }
            } else if (kind == "WEBP") {
                val riffLength = readU32LE(4)
                val declaredEnd = 8L + riffLength
                if (riffLength < 4L || declaredEnd > bytes.size.toLong()) {
                    findings.add("حجم RIFF المعلن داخل WebP لا يتوافق مع حجم الملف؛ قد يكون الملف مبتوراً أو بنيته غير سليمة.")
                } else {
                    var offset = 12L
                    var chunkCount = 0
                    var imageChunkCount = 0
                    var malformed = false

                    while (offset < declaredEnd) {
                        if (declaredEnd - offset < 8L) {
                            malformed = true
                            break
                        }

                        val pos = offset.toInt()
                        val chunkType = String(bytes, pos, 4, Charsets.US_ASCII)
                        val chunkLength = readU32LE(pos + 4)
                        val dataEnd = offset + 8L + chunkLength
                        val paddedEnd = dataEnd + (chunkLength and 1L)
                        if (dataEnd < offset || paddedEnd > declaredEnd) {
                            malformed = true
                            break
                        }

                        if (chunkCount == 0 && chunkType !in setOf("VP8X", "VP8 ", "VP8L")) {
                            findings.add("أول كتلة في WebP ليست كتلة صورة أو ترويسة امتداد معروفة.")
                        }

                        when (chunkType) {
                            "VP8X" -> if (chunkLength != 10L) {
                                findings.add("طول كتلة VP8X في WebP غير صحيح.")
                                malformed = true
                            }
                            "VP8 " -> {
                                imageChunkCount++
                                if (chunkLength < 10L) malformed = true
                            }
                            "VP8L" -> {
                                imageChunkCount++
                                if (chunkLength < 5L) malformed = true
                                else if ((bytes[pos + 8].toInt() and 255) != 0x2f) {
                                    findings.add("توقيع بيانات VP8L داخل WebP غير صحيح.")
                                    malformed = true
                                }
                            }
                        }

                        chunkCount++
                        offset = paddedEnd
                    }

                    if (offset != declaredEnd) malformed = true
                    if (chunkCount == 0) malformed = true
                    if (imageChunkCount == 0) {
                        findings.add("لم يُعثر على كتلة بيانات صورة VP8 أو VP8L داخل WebP.")
                    } else if (imageChunkCount > 1) {
                        findings.add("تحتوي WebP على أكثر من كتلة بيانات صورة رئيسية؛ يلزم التحقق من بنيتها.")
                    }
                    if (malformed) {
                        findings.add("بنية كتل WebP/RIFF تحتوي على طول أو ترتيب غير صالح.")
                    }
                    if (declaredEnd < bytes.size.toLong() && hasNonPaddingTail(declaredEnd.toInt())) {
                        findings.add("توجد بيانات غير فارغة بعد نهاية RIFF المعلنة في WebP؛ تستحق المراجعة ولا تثبت وحدها وجود برمجية خبيثة.")
                    }
                }
            } else if (kind == "GIF") {
                val end = bytes.indexOf(0x3b.toByte()) + 1
                if (end <= 0) {
                    findings.add("لم يُعثر على علامة نهاية GIF؛ قد تكون الصورة مبتورة.")
                } else if (hasNonPaddingTail(end)) {
                    findings.add("توجد بيانات غير فارغة بعد علامة نهاية GIF؛ تستحق المراجعة.")
                }
            }

            val isZipArchive = kind.startsWith("أرشيف ZIP")
            val isWindowsExecutable = kind.startsWith("ملف تنفيذي Windows")
            val isNativeExecutable = kind.startsWith("ملف تنفيذي Linux")
            val isDexExecutable = kind.startsWith("ملف DEX")
            val isExecutableContent = isWindowsExecutable || isNativeExecutable || isDexExecutable

            // A harmless-looking extension must not hide a recognizable executable signature.
            val harmlessLookingExt = setOf(
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif",
                "pdf", "txt", "rtf", "csv", "mp3", "mp4", "avi", "mov",
                "doc", "docx", "xls", "xlsx", "ppt", "pptx"
            )
            if (isExecutableContent && ext in harmlessLookingExt) {
                findings.add("محتوى الملف يحمل توقيعاً تنفيذياً (Windows/ELF/DEX) رغم أن امتداده يبدو صورة أو مستنداً أو وسائط؛ لا تفتحه.")
            }

            // Verify common executable/container extensions against their leading signatures.
            if (ext in setOf("exe", "dll", "scr", "com", "cpl", "pif") && !isWindowsExecutable) {
                findings.add("الامتداد يوحي بملف تنفيذي Windows لكن توقيع MZ/PE غير موجود؛ قد يكون الملف مزيفاً أو غير مكتمل.")
            }
            if (ext == "dex" && !isDexExecutable) {
                findings.add("الامتداد DEX لكن توقيع ملف Android DEX غير موجود.")
            }
            if (ext in setOf("apk", "jar", "zip", "rar", "7z") && !isZipArchive &&
                ext in setOf("apk", "jar", "zip")) {
                findings.add("الامتداد يوحي بأرشيف ZIP أو حزمة APK/JAR لكن توقيع ZIP المعتاد غير موجود.")
            }
            if (ext in setOf("apk", "jar") && isZipArchive) {
                findings.add("تم التعرف على حاوية ZIP فقط؛ هذا لا يثبت أن APK أو JAR صالح أو آمن.")
            }

            // Unicode bidirectional overrides can make the visible filename misleading.
            if (displayName.any { it.code in setOf(0x202A, 0x202B, 0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069) }) {
                findings.add("اسم الملف يحتوي محارف تحكم باتجاه الكتابة قد تخفي الامتداد الحقيقي بصرياً؛ تحقق من الاسم الكامل.")
            }

            if (kind == "PDF") {
                val pdfSample = String(bytes.copyOfRange(0, minOf(bytes.size, 5 * 1024 * 1024)), Charsets.ISO_8859_1)
                val pdfChecks = listOf(
                    Regex("""/JavaScript\b|/JS\b""", RegexOption.IGNORE_CASE) to "يحتوي PDF على مؤشرات JavaScript مضمّن.",
                    Regex("""/OpenAction\b|/AA\b""", RegexOption.IGNORE_CASE) to "يحتوي PDF على إجراءات تلقائية عند الفتح أو أحداث إضافية.",
                    Regex("""/Launch\b""", RegexOption.IGNORE_CASE) to "يحتوي PDF على إجراء Launch قد يحاول تشغيل عنصر خارجي.",
                    Regex("""/EmbeddedFile\b|/Filespec\b""", RegexOption.IGNORE_CASE) to "يحتوي PDF على مؤشرات ملفات مضمّنة أو مرفقات.",
                    Regex("""/RichMedia\b""", RegexOption.IGNORE_CASE) to "يحتوي PDF على عناصر RichMedia تحتاج إلى مراجعة."
                )
                pdfChecks.forEach { (pattern, message) -> if (pattern.containsMatchIn(pdfSample)) findings.add(message) }
                val tail = String(bytes.copyOfRange(maxOf(0, bytes.size - 65536), bytes.size), Charsets.ISO_8859_1)
                val eof = tail.lastIndexOf("%%EOF")
                if (eof >= 0 && tail.substring(eof + 5).any { !it.isWhitespace() && it.code >= 32 }) {
                    findings.add("توجد بيانات غير بيضاء بعد علامة نهاية PDF الأخيرة؛ قد تكون بيانات ملحقة أو تحديثاً غير معتاد.")
                }
                if (bytes.size > 5 * 1024 * 1024) findings.add("اقتصر تحليل مؤشرات PDF البنيوية على أول 5 ميغابايت وآخر 64 كيلوبايت.")
            }

            val mediaExtensions = mapOf(
                "AVI" to setOf("avi"), "ISO-BMFF" to setOf("mp4","m4v","mov","heic","heif","m4a","aac"),
                "Matroska/WebM" to setOf("mkv","webm"), "WAV" to setOf("wav"),
                "MP3" to setOf("mp3"), "MP3/ MPEG audio" to setOf("mp3","mp2"),
                "OGG" to setOf("ogg","oga","ogv"), "FLAC" to setOf("flac")
            )
            val expected = mediaExtensions[kind]
            if (expected != null && ext !in expected) findings.add("امتداد الملف لا يطابق صيغة الوسائط التي تشير إليها الترويسة ($kind).")
            if (isVideo && !mime.startsWith("video/") && mime != "غير معروف" && !mime.startsWith("application/octet-stream")) {
                findings.add("نوع MIME المعلن لا يتوافق بوضوح مع حاوية الفيديو المكتشفة.")
            }
            if (isAudio && !mime.startsWith("audio/") && mime != "غير معروف" && !mime.startsWith("application/octet-stream")) {
                findings.add("نوع MIME المعلن لا يتوافق بوضوح مع صيغة الصوت المكتشفة.")
            }
            if (ext == "pdf" && kind != "PDF") findings.add("الامتداد PDF لكن توقيع الملف لا يطابق ترويسة PDF المعتادة.")
            if (ext in setOf("mp4","m4v","mov","avi","mkv","webm") && !isVideo) findings.add("الامتداد يوحي بفيديو لكن الترويسة لا تطابق حاوية فيديو معروفة.")
            if (ext in setOf("mp3","wav","ogg","oga","flac","m4a","aac") && !isAudio && kind != "ISO-BMFF") {
                findings.add("الامتداد يوحي بملف صوتي لكن الترويسة لا تطابق صيغة صوت معروفة.")
            }

            // Static, non-executing inspection for Python source files.
            // These patterns are warning indicators only and can produce false positives.
            var pythonAnalysis = "لم يُجرَ تحليل محتوى Python لهذا الملف."
            var pythonHasIndicators = false
            if (ext == "py" || mime == "text/x-python") {
                val source = String(bytes.copyOfRange(0, minOf(bytes.size, 1024 * 1024)), Charsets.UTF_8)
                val codeFindings = mutableListOf<String>()
                fun checkCode(pattern: String, message: String) {
                    if (Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).containsMatchIn(source)) {
                        codeFindings.add(message)
                    }
                }
                checkCode("""\b(?:eval|exec)\s*\(""", "يستعمل eval أو exec لتنفيذ نص برمجي؛ راجع مصدر النص قبل تشغيل الملف.")
                checkCode("""\b(?:os\.system|os\.popen|subprocess\.(?:Popen|run|call|check_call|check_output))\s*\(""", "يستدعي أوامر النظام أو عمليات خارجية؛ قد يكون ذلك مشروعاً لكنه يحتاج مراجعة.")
                checkCode("""\b(?:os\.remove|os\.unlink|shutil\.rmtree)\s*\(""", "يحتوي على أوامر قد تحذف ملفات أو مجلدات.")
                checkCode("""\b(?:pickle\.loads|marshal\.loads)\s*\(""", "يستعمل تحميل بيانات Python متسلسلة؛ لا تستخدم بيانات من مصدر غير موثوق.")
                val hasNetwork = Regex("""\b(?:urlopen|requests\.(?:get|post|request)|httpx\.(?:get|post|request)|socket\.(?:create_connection|connect))\s*\(""", RegexOption.IGNORE_CASE).containsMatchIn(source)
                if (hasNetwork) codeFindings.add("يجري اتصالاً بالشبكة؛ تحقق من عنوان الوجهة والبيانات التي تُرسل أو تُستقبل.")
                val hasExecution = Regex("""\b(?:exec|eval|os\.system|subprocess\.)""", RegexOption.IGNORE_CASE).containsMatchIn(source)
                if (hasNetwork && hasExecution) codeFindings.add("يجمع بين الاتصال بالشبكة وتنفيذ أوامر/نصوص؛ تحقق من الوجهة والغرض.")
                pythonHasIndicators = codeFindings.isNotEmpty()
                pythonAnalysis = if (codeFindings.isEmpty()) {
                    "تحليل محتوى Python: لم تظهر الأنماط المحددة في هذا الفحص النصي المحدود."
                } else {
                    "تحليل محتوى Python - مؤشرات تستحق المراجعة:\n" + codeFindings.distinct().joinToString("\n") { "• $it" }
                }
                if (bytes.size > 1024 * 1024) {
                    pythonAnalysis += "\nملاحظة: اقتصر تحليل النص على أول 1 ميغابايت."
                }
            }
            if (bytes.isEmpty()) findings.add("الملف فارغ.")
            if (bytes.size < 12 && bytes.isNotEmpty()) findings.add("الملف صغير جداً أو ترويسـته غير مكتملة.")
            val summary = if (findings.isEmpty()) "لم تظهر مؤشرات نوع/امتداد مريبة في الفحص المحلي."
                else findings.joinToString("\n") { "• $it" }
            val advice = if (findings.isEmpty() && !pythonHasIndicators) "لم نعثر على علامة مريبة من الاختبارات المحدودة. هذا لا يعني أن الملف خالٍ من الفيروسات."
                else if (pythonHasIndicators) "تم العثور على مؤشرات في محتوى Python تستحق المراجعة. لا تشغّل الملف قبل فهم هذه الأجزاء والتحقق من مصدره؛ المؤشرات ليست دليلاً قاطعاً على وجود فيروس."
                else "ظهرت مؤشرات في نوع الملف أو امتداده. لا تفتحه إذا لم تكن تثق بمصدره، واستخدم حماية موثوقة ومحدّثة."
            val formatAnalysis = when {
                isImage -> "تحليل الصورة: تم فحص التوقيع وبنية كتل PNG/ WebP وعلامات نهاية JPEG/ GIF، مع التحقق من CRC في PNG."
                kind == "PDF" -> "تحليل PDF: تم البحث عن مؤشرات JavaScript والإجراءات التلقائية والمرفقات والبيانات بعد EOF."
                isVideo -> "تحليل الفيديو: تم تحديد حاوية الوسائط من الترويسة ومقارنتها بالامتداد ونوع MIME."
                isAudio -> "تحليل الصوت: تم تحديد صيغة الصوت من الترويسة ومقارنتها بالامتداد ونوع MIME."
                else -> "تحليل الوسائط: لم تُعرف صيغة صورة أو PDF أو فيديو أو صوت مدعومة."
            }
            val report = "نتيجة فحص الملف\nالاسم: $displayName\nالنوع المعلن: $mime\nالترويسة المقروءة: $kind\nالحجم: $fileSizeText\nSHA-256: $digest\n\n$formatAnalysis\n\n$summary\n\n$pythonAnalysis\n\nالنصيحة: $advice\n\nملاحظة: فحص محلي أولي لا يشغّل الملف ولا يرفعه. التحليل البنيوي والمؤشرات النصية لا تعادل محرك مضاد فيروسات، وقد تفوّت التهديدات أو تعطي تحذيرات غير دقيقة."
            runOnUiThread {
                result.text = report
                saveReport("ملف: $displayName", report)
            }
        } catch (e: Exception) {
            val reason = e.message ?: "خطأ غير معروف"
            val sizeText = if (selectedSize >= 0L) String.format(Locale.ROOT, "%,d بايت (%.2f MB)", selectedSize, selectedSize / (1024.0 * 1024.0)) else "غير متاح من مزود الملفات"
            val rejectedReport = "نتيجة فحص الملف\nالاسم: $selectedName\nالنوع المعلن: $selectedMime\nالحجم: $sizeText\n\nحالة الفحص: لم يكتمل\nالسبب: $reason\n\nلم يتم تشغيل الملف أو رفعه. لم يُجرَ تحليل المحتوى أو حساب SHA-256 لأن الفحص توقف قبل اكتماله.\n\nملاحظة: هذا التقرير يوثق رفض الفحص أو تعذّر القراءة، ولا يحدد إن كان الملف آمناً أو ضاراً."
            runOnUiThread {
                result.text = rejectedReport
                saveReport("ملف: $selectedName", rejectedReport)
            }
        }
        }, "phone-guardian-file-scan")
        scanTask.start()
        actionButton("📋 نسخ تقرير الفحص") {
            val report = result.text.toString()
            if (!report.startsWith("نتيجة فحص الملف")) Toast.makeText(this,"لا يوجد تقرير لنسخه.",Toast.LENGTH_SHORT).show()
            else {
                val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("Phone Guardian - فحص ملف",report))
                Toast.makeText(this,"تم نسخ التقرير.",Toast.LENGTH_SHORT).show()
            }
        }
        actionButton("رجوع") { showHome() }
    }

    private fun showDeviceInfo() {
        content = baseLayout()
        content.addView(title("معلومات الهاتف"))
        content.addView(paragraph(
            "الشركة المصنّعة: ${Build.MANUFACTURER}\n" +
            "طراز الجهاز: ${Build.MODEL}\n" +
            "إصدار Android: ${Build.VERSION.RELEASE}\n" +
            "مستوى API: ${Build.VERSION.SDK_INT}"
        ))
        actionButton("رجوع") { showHome() }
    }

    @Suppress("DEPRECATION")
    private fun showApps() {
        content = baseLayout()
        content.addView(title("التطبيقات الظاهرة"))
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0)
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.ROOT) }

        content.addView(paragraph("عدد التطبيقات التي تظهر في قائمة التشغيل: ${apps.size}. هذه ليست قائمة بكل حزم النظام."))
        for (item in apps) {
            val appName = item.loadLabel(packageManager).toString()
            val row = TextView(this).apply {
                text = "• $appName"
                textSize = 16f
                setTextColor(navy)
                setPadding(10, 9, 10, 9)
            }
            row.setOnClickListener {
                val packageName = item.activityInfo.packageName
                val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"))
                startActivity(details)
            }
            content.addView(row)
        }
        content.addView(paragraph("اضغط على اسم أي تطبيق لفتح صفحة معلوماته وأذوناته في إعدادات Android."))
        actionButton("رجوع") { showHome() }
    }


    @Suppress("DEPRECATION")
    private fun showPermissionAudit() {
        content = baseLayout()
        content.addView(title("🔐 مراقبة أذونات التطبيقات"))
        content.addView(paragraph(
            "يفحص هذا القسم الأذونات الخطيرة التي يعلن التطبيق أنه يحتاجها، ويبيّن هل مُنحت له حالياً أم لا. " +
            "طلب الإذن لا يعني أن التطبيق خبيث؛ راجع هل الصلاحية منطقية بالنسبة لوظيفة التطبيق."
        ))

        val launcherIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcherIntent, 0)
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.ROOT) }

        content.addView(paragraph("عدد التطبيقات الظاهرة التي سيجري فحصها: ${apps.size}. لا تشمل هذه القائمة بالضرورة كل تطبيقات النظام أو التطبيقات التي لا تملك أيقونة تشغيل."))

        fun permissionLabel(permission: String): String = when (permission) {
            "android.permission.CAMERA" -> "الكاميرا"
            "android.permission.RECORD_AUDIO" -> "الميكروفون وتسجيل الصوت"
            "android.permission.ACCESS_FINE_LOCATION" -> "الموقع الدقيق"
            "android.permission.ACCESS_COARSE_LOCATION" -> "الموقع التقريبي"
            "android.permission.ACCESS_BACKGROUND_LOCATION" -> "الموقع في الخلفية"
            "android.permission.READ_CONTACTS" -> "قراءة جهات الاتصال"
            "android.permission.WRITE_CONTACTS" -> "تعديل جهات الاتصال"
            "android.permission.READ_CALENDAR" -> "قراءة التقويم"
            "android.permission.WRITE_CALENDAR" -> "تعديل التقويم"
            "android.permission.READ_CALL_LOG" -> "قراءة سجل المكالمات"
            "android.permission.WRITE_CALL_LOG" -> "تعديل سجل المكالمات"
            "android.permission.CALL_PHONE" -> "إجراء المكالمات"
            "android.permission.READ_PHONE_STATE" -> "حالة الهاتف ومعرّفاته"
            "android.permission.READ_SMS" -> "قراءة الرسائل SMS"
            "android.permission.SEND_SMS" -> "إرسال الرسائل SMS"
            "android.permission.RECEIVE_SMS" -> "استقبال الرسائل SMS"
            "android.permission.RECEIVE_MMS" -> "استقبال رسائل MMS"
            "android.permission.READ_EXTERNAL_STORAGE" -> "قراءة مساحة التخزين (الإصدارات الأقدم)"
            "android.permission.WRITE_EXTERNAL_STORAGE" -> "تعديل مساحة التخزين (الإصدارات الأقدم)"
            "android.permission.READ_MEDIA_IMAGES" -> "قراءة الصور"
            "android.permission.READ_MEDIA_VIDEO" -> "قراءة الفيديو"
            "android.permission.READ_MEDIA_AUDIO" -> "قراءة الملفات الصوتية"
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" -> "الوصول إلى الصور/الفيديو المختارة"
            "android.permission.POST_NOTIFICATIONS" -> "إرسال الإشعارات"
            "android.permission.BODY_SENSORS" -> "مستشعرات الجسم"
            "android.permission.ACTIVITY_RECOGNITION" -> "التعرّف على النشاط البدني"
            "android.permission.BLUETOOTH_CONNECT" -> "الاتصال بأجهزة Bluetooth"
            "android.permission.NEARBY_WIFI_DEVICES" -> "الأجهزة القريبة عبر Wi-Fi"
            else -> permission.substringAfterLast('.')
        }

        var scanned = 0
        var appsWithSensitive = 0
        for (item in apps) {
            val packageName = item.activityInfo.packageName
            val appName = item.loadLabel(packageManager).toString()
            scanned++
            val requested = try {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
                    .requestedPermissions?.toList().orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
            val sensitive = requested.distinct().mapNotNull { permission ->
                try {
                    val info = packageManager.getPermissionInfo(permission, 0)
                    val baseProtection = info.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE
                    if (baseProtection != android.content.pm.PermissionInfo.PROTECTION_DANGEROUS) null
                    else permission to (packageManager.checkPermission(permission, packageName) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                } catch (_: Exception) {
                    null
                }
            }

            if (sensitive.isNotEmpty()) appsWithSensitive++
            val grantedCount = sensitive.count { it.second }
            val row = TextView(this).apply {
                text = if (sensitive.isEmpty()) {
                    "• $appName\n  لم يُعلن عن أذونات خطيرة ضمن القائمة المفحوصة"
                } else {
                    "• $appName\n  أذونات حساسة: ${sensitive.size} | ممنوحة حالياً: $grantedCount"
                }
                textSize = 15f
                setTextColor(navy)
                setPadding(12, 12, 12, 12)
                setBackgroundColor(Color.WHITE)
                isClickable = true
                isFocusable = true
            }
            row.setOnClickListener {
                val permissionDetails = if (sensitive.isEmpty()) {
                    "لم يُعثر على أذونات من نوع Dangerous ضمن الأذونات المعلنة التي أمكن قراءتها. قد يطلب التطبيق أذونات أخرى عادية أو خاصة."
                } else {
                    sensitive.joinToString("\n") { (permission, granted) ->
                        val state = if (granted) "ممنوحة حالياً" else "غير ممنوحة حالياً"
                        "• ${permissionLabel(permission)} — $state"
                    }
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(appName)
                    .setMessage("اسم الحزمة: $packageName\n\n$permissionDetails\n\nتنبيه: وجود الإذن في القائمة لا يثبت إساءة استخدامه. تحقق من حاجة التطبيق إليه ومن مصدره.")
                    .setPositiveButton("فتح إعدادات التطبيق") { _, _ ->
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    }
                    .setNegativeButton("إغلاق", null)
                    .show()
            }
            content.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 })
        }

        content.addView(paragraph("اكتمل الفحص: $scanned تطبيق ظاهر، منها $appsWithSensitive تطبيق أعلن عن إذن حساس واحد أو أكثر. الأذونات غير الممنوحة قد يطلبها التطبيق لاحقاً."))

        content.addView(title("صلاحيات الوصول الخاصة", 19f))
        content.addView(paragraph("بعض الصلاحيات القوية لا تظهر كأذونات Dangerous عادية، مثل إمكانية الوصول والظهور فوق التطبيقات والوصول إلى الاستخدام. افتح إعدادات Android لمراجعتها يدوياً؛ لا يصنّفها هذا الفحص تلقائياً كتطبيقات ضارة."))
        actionButton("فتح إعدادات إمكانية الوصول") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        actionButton("فتح إعدادات الظهور فوق التطبيقات") {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
                .onFailure { Toast.makeText(this, "تعذر فتح هذه الصفحة على هذا الجهاز.", Toast.LENGTH_SHORT).show() }
        }
        actionButton("فتح إعدادات الوصول إلى الاستخدام") {
            runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                .onFailure { Toast.makeText(this, "تعذر فتح هذه الصفحة على هذا الجهاز.", Toast.LENGTH_SHORT).show() }
        }
        actionButton("فتح إعدادات مستمع الإشعارات") {
            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                .onFailure { Toast.makeText(this, "تعذر فتح هذه الصفحة على هذا الجهاز.", Toast.LENGTH_SHORT).show() }
        }
        actionButton("رجوع") { showHome() }
    }

    private fun ipv4Parts(host: String): List<Int>? {
        val parts = host.split(".")
        if (parts.size != 4) return null
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        if (numbers.any { it !in 0..255 }) return null
        return numbers
    }

    private fun isPrivateOrLocalIpv4(host: String): Boolean {
        val parts = ipv4Parts(host) ?: return false
        val a = parts[0]
        val b = parts[1]
        return a == 10 || (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) || a == 127 ||
            (a == 169 && b == 254) || (a == 100 && b in 64..127) || a == 0
    }

    private fun isLocalHostname(host: String): Boolean =
        host == "localhost" || host.endsWith(".localhost") ||
            host.endsWith(".local") || host.endsWith(".lan") ||
            host == "router" || host == "gateway"

    // Match one insertion, deletion, substitution, or adjacent transposition.
    private fun isOneTypoAway(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        if (a.length == b.length) {
            val mismatches = a.indices.filter { a[it] != b[it] }
            return mismatches.isEmpty() || mismatches.size == 1 ||
                (mismatches.size == 2 && mismatches[1] == mismatches[0] + 1 &&
                    a[mismatches[0]] == b[mismatches[1]] &&
                    a[mismatches[1]] == b[mismatches[0]])
        }
        val shorter = if (a.length < b.length) a else b
        val longer = if (a.length < b.length) b else a
        var i = 0
        var j = 0
        var skipped = false
        while (i < shorter.length && j < longer.length) {
            if (shorter[i] == longer[j]) {
                i++
                j++
            } else {
                if (skipped) return false
                skipped = true
                j++
            }
        }
        return true
    }

    // Use the label immediately before common multi-part public suffixes.
    // This avoids comparing "co" in domains such as example.co.uk.
    private fun registrableDomainLabel(host: String): String {
        val labels = host.split(".")
        if (labels.size < 2) return host
        val multiPartSuffixes = setOf(
            "co.uk", "org.uk", "ac.uk", "gov.uk",
            "com.au", "net.au", "org.au",
            "co.nz", "org.nz",
            "co.jp", "ne.jp",
            "com.br", "com.mx", "com.tr", "com.cn",
            "com.sg", "com.my", "com.hk", "com.tw",
            "co.in", "firm.in", "net.in", "org.in",
            "co.za", "co.kr", "com.ar"
        )
        val lastTwo = labels.takeLast(2).joinToString(".")
        val index = if (lastTwo in multiPartSuffixes) labels.size - 3 else labels.size - 2
        return labels.getOrElse(index) { host }
    }

    private fun isPrivateOrLocalIpv6(host: String): Boolean {
        val value = host.removePrefix("[").removeSuffix("]").lowercase(Locale.ROOT)
        return value == "::1" || value == "::" || value.startsWith("fc") ||
            value.startsWith("fd") || value.startsWith("fe80:")
    }

    // Detect mixed writing systems and common Cyrillic/Greek lookalike letters.
    // This is a warning heuristic, not proof that a domain is malicious.
    private fun containsInvisibleFormatCharacters(value: String): Boolean {
        return value.any { ch ->
            Character.getType(ch) == Character.FORMAT.toInt() ||
                ch.code in 0x202A..0x202E || ch.code in 0x2066..0x2069
        }
    }

    private fun hasMixedScripts(label: String): Boolean {
        val scripts = label.filter { it.isLetter() }
            .map { Character.UnicodeScript.of(it.code) }
            .filter { it != Character.UnicodeScript.COMMON &&
                it != Character.UnicodeScript.INHERITED }
            .distinct()
        return scripts.size > 1
    }

    private fun containsConfusableLookalike(label: String): Boolean {
        val lookalikes = "аАеЕоОрРсСхХуУіІјЈԁԛ"
        return label.any { it in lookalikes }
    }

    private fun saveReport(url: String, report: String) {
        val prefs = getSharedPreferences("phone_guardian_history", MODE_PRIVATE)
        val oldItems = runCatching { JSONArray(prefs.getString("reports", "[]") ?: "[]") }
            .getOrDefault(JSONArray())
        val updated = JSONArray()
        updated.put(JSONObject().apply {
            put("date", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
            put("url", url)
            put("report", report)
        })
        for (i in 0 until minOf(oldItems.length(), 19)) {
            updated.put(oldItems.getJSONObject(i))
        }
        prefs.edit().putString("reports", updated.toString()).apply()
    }

    private fun showHistory() {
        content = baseLayout()
        content.addView(title("🗂️ سجل تقارير الفحص"))
        val prefs = getSharedPreferences("phone_guardian_history", MODE_PRIVATE)
        val reports = runCatching { JSONArray(prefs.getString("reports", "[]") ?: "[]") }
            .getOrDefault(JSONArray())

        if (reports.length() == 0) {
            content.addView(paragraph("لا توجد تقارير محفوظة بعد. افحص رابطاً أولاً وسيظهر تقريره هنا."))
        } else {
            content.addView(paragraph("التقارير محفوظة محلياً على هذا الهاتف. العدد: ${reports.length()}"))
            for (i in 0 until reports.length()) {
                val item = reports.getJSONObject(i)
                val reportDate = item.optString("date")
                val reportUrl = item.optString("url")
                val reportText = item.optString("report")
                val card = TextView(this).apply {
                    text = "📅 $reportDate\n🔗 $reportUrl\n\nاضغط لعرض تفاصيل التقرير"
                    textSize = 15f
                    setTextColor(navy)
                    setPadding(14, 14, 14, 18)
                    setBackgroundColor(Color.WHITE)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("تفاصيل تقرير الفحص")
                            .setMessage("التاريخ: $reportDate\n\nالرابط:\n$reportUrl\n\n$reportText")
                            .setPositiveButton("إغلاق", null)
                            .show()
                    }
                }
                content.addView(card, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 14 })
            }
            actionButton("🗑️ مسح سجل التقارير") {
                AlertDialog.Builder(this)
                    .setTitle("مسح سجل التقارير")
                    .setMessage("هل تريد حذف جميع التقارير المحفوظة من هذا الهاتف؟")
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("مسح") { _, _ ->
                        prefs.edit().remove("reports").apply()
                        showHistory()
                    }
                    .show()
            }
        }
        actionButton("رجوع") { showHome() }
    }

    private fun showLinkCheck() {
        content = baseLayout()
        content.addView(title("فحص رابط متقدم"))
        content.addView(paragraph("يفحص هذا الإصدار مؤشرات محلية فقط. لا يفتح الرابط ولا يرسل بياناتك إلى الإنترنت."))
        val input = EditText(this).apply {
            hint = "https://example.com"
            textSize = 16f
            setSingleLine(true)
        }
        content.addView(input)
        val result = paragraph("")
        content.addView(result)

        actionButton("افحص الرابط") {
            val raw = input.text.toString().trim()
            val findings = mutableListOf<Pair<Int, String>>()
            if (raw.isBlank()) {
                result.text = "أدخل رابطاً أولاً."
                return@actionButton
            }
            if (raw.any { it.isWhitespace() || it.isISOControl() }) {
                result.text = "صيغة الرابط غير صحيحة: احذف الفراغات أو الرموز غير المرئية ثم أعد المحاولة."
                return@actionButton
            }

            val explicitScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:").find(raw)?.value
            val normalized = when {
                explicitScheme == null -> "https://$raw"
                raw.contains("://") -> raw
                else -> {
                    result.text = "نوع الرابط غير مدعوم. استخدم رابطاً يبدأ بـ http:// أو https://."
                    return@actionButton
                }
            }
            // Detect invisible Unicode formatting controls in the authority before URI parsing.
            // Decode at most three percent-encoding layers for inspection only; never rewrite the URL.
            val rawAuthorityCandidate = Regex("(?i)^[A-Za-z][A-Za-z0-9+.-]*://([^/?#]*)")
                .find(normalized)?.groupValues?.get(1).orEmpty()
            var inspectedAuthority = rawAuthorityCandidate
            repeat(3) {
                val decoded = runCatching {
                    URLDecoder.decode(inspectedAuthority.replace("+", "%2B"), "UTF-8")
                }.getOrDefault(inspectedAuthority)
                if (decoded == inspectedAuthority) return@repeat
                inspectedAuthority = decoded
            }
            if (containsInvisibleFormatCharacters(inspectedAuthority)) {
                val report = "مستوى التنبيه: متوسط (4/10)\\n\\n• اسم الموقع يحتوي محارف Unicode خفية أو محارف تنسيق قد تغيّر عرضه أو تفسيره.\\n• تم إيقاف التحليل قبل تفسير اسم المضيف؛ لم يتم فتح الرابط أو تعديله.\\n\\nالنصيحة: لا تفتح الرابط. اطلب العنوان من مصدر موثوق واستخدم اسم النطاق الرسمي المكتوب بوضوح.\\n\\nملاحظة: هذا فحص محلي إرشادي ولا يثبت أن الموقع ضار أو آمن."
                result.text = report
                saveReport(raw, report)
                return@actionButton
            }

            // Report browser-interpretation backslashes before java.net.URI rejects them.
            // Never rewrite or normalize this ambiguous input into a different URL.
            if (raw.contains('\\')) {
                val warning = "الرابط يحتوي شرطة مائلة عكسية قد تغيّر طريقة تفسير العنوان بين التطبيقات والمتصفحات."
                val report = "مستوى التنبيه: متوسط (3/10)\n\n• $warning\n• تعذّر تحليل الرابط بصيغته الحالية؛ لم يتم فتحه أو محاولة تصحيحه تلقائياً.\n\nالنصيحة: لا تفتح الرابط. أعد الحصول على العنوان من مصدر موثوق وتحقق من اسم النطاق حرفاً بحرف.\n\nملاحظة: هذا تقييم إرشادي لشكل الرابط فقط. لا يفتح الموقع، ولا يتبع التحويلات، ولا يستعلم عن قواعد بيانات السمعة؛ لذلك لا يثبت أن الموقع آمن ولا يضمن اكتشاف كل عمليات التصيد."
                result.text = report
                saveReport(raw, report)
                return@actionButton
            }

            val parsed = try { URI(normalized) } catch (_: Exception) { null }
            if (parsed == null || parsed.rawAuthority.isNullOrBlank()) {
                result.text = "تعذّر قراءة الرابط. تأكد من كتابة اسم الموقع بشكل صحيح، مثل https://example.com"
                return@actionButton
            }

            // java.net.URI may leave host null for a valid Unicode/IDN authority.
            // Read the raw authority and extract the hostname without requiring ASCII.
            val authority = parsed.rawAuthority ?: ""
            val authorityWithoutUserInfo = authority.substringAfterLast('@')
            val extractedHost = when {
                authorityWithoutUserInfo.startsWith("[") -> {
                    authorityWithoutUserInfo.substringBefore("]").removePrefix("[")
                }
                authorityWithoutUserInfo.count { it == ':' } == 1 ->
                    authorityWithoutUserInfo.substringBeforeLast(':')
                else -> authorityWithoutUserInfo
            }
            if (extractedHost.isBlank()) {
                result.text = "تعذّر قراءة اسم الموقع. تأكد من كتابة الرابط بشكل صحيح."
                return@actionButton
            }

            val host = extractedHost.lowercase(Locale.ROOT).trimEnd('.')
            val scheme = parsed.scheme?.lowercase(Locale.ROOT) ?: ""
            val userInfo = parsed.rawUserInfo
            if (raw.length > 180) findings.add(1 to "الرابط طويل بشكل غير معتاد؛ تحقق من مصدره.")
            if (!userInfo.isNullOrBlank() || authority.substringBeforeLast('@', "").isNotEmpty()) {
                findings.add(4 to "يحتوي جزء العنوان على بيانات قبل علامة @؛ قد تُستعمل لخداعك بشأن الموقع الحقيقي.")
            }
            if (scheme == "http") {
                findings.add(2 to "الاتصال يستخدم HTTP غير المشفر. لا تدخل كلمات المرور أو البيانات الحساسة.")
            } else if (scheme != "https") {
                findings.add(3 to "نوع الاتصال غير معتاد لهذا الفاحص؛ لا تتابع إلا إذا كنت تعرف سبب استخدامه.")
            }

            val authorityRaw = parsed.rawAuthority ?: ""
            if (authorityRaw.contains('%')) {
                findings.add(4 to "جزء اسم الموقع يحتوي ترميزاً مئوياً؛ قد يخفي أحرفاً أو يسبب اختلاف تفسير العنوان.")
            }
            val hasBidiControls = raw.any { ch ->
                ch.code in 0x202A..0x202E || ch.code in 0x2066..0x2069 ||
                    ch.code == 0x200E || ch.code == 0x200F
            }
            if (hasBidiControls) {
                findings.add(4 to "الرابط يحتوي محارف اتجاه كتابة خفية قد تقلب ترتيب الأحرف بصرياً.")
            }
            if (host.length > 253) {
                findings.add(3 to "اسم المضيف أطول من الحد المعتاد لأسماء DNS.")
            }
            if (!host.contains(":") && ipv4Parts(host) == null) {
                val asciiHost = runCatching {
                    java.net.IDN.toASCII(host, java.net.IDN.USE_STD3_ASCII_RULES)
                }.getOrNull()
                val validDns = asciiHost != null && asciiHost.length <= 253 &&
                    asciiHost.split('.').all { label ->
                        label.isNotEmpty() && label.length <= 63 &&
                            label.firstOrNull()?.let { it.isLetterOrDigit() } == true &&
                            label.lastOrNull()?.let { it.isLetterOrDigit() } == true &&
                            label.all { it.isLetterOrDigit() || it == '-' }
                    }
                if (!validDns) {
                    findings.add(4 to "اسم المضيف لا يطابق قواعد أسماء DNS المعتادة؛ قد يكون مكتوباً بصيغة مضللة أو غير صالحة.")
                }
            }

            if (host.startsWith("xn--") || host.split(".").any { it.startsWith("xn--") }) {
                findings.add(2 to "اسم النطاق يستخدم ترميز IDN؛ افحص تهجئة الموقع بعناية لاحتمال تشابه الحروف.")
            }

            val unicodeLabels = host.split(".").map { label ->
                runCatching { java.net.IDN.toUnicode(label) }.getOrDefault(label)
            }
            val mixedScriptLabels = unicodeLabels.filter { hasMixedScripts(it) }
            val confusableLabels = unicodeLabels.filter { containsConfusableLookalike(it) }
            if (mixedScriptLabels.isNotEmpty() || confusableLabels.isNotEmpty()) {
                findings.add(3 to "اسم النطاق يحتوي حروفاً قد تتشابه بصرياً أو يمزج أنظمة كتابة مختلفة؛ دقّق في كل حرف وتأكد من النطاق الرسمي.")
            }
            if (isLocalHostname(host)) {
                findings.add(0 to "اسم محلي أو داخلي؛ يُستخدم عادة داخل الشبكة ولا يعني وحده وجود خطر.")
            }

            val ipv4 = ipv4Parts(host)
            if (ipv4 != null) {
                if (isPrivateOrLocalIpv4(host)) {
                    findings.add(0 to "عنوان IP محلي أو خاص؛ شائع في الراوترات والشبكات الداخلية، ولا يعني وحده وجود خطر.")
                } else {
                    findings.add(2 to "الرابط يستخدم عنوان IPv4 عاماً مباشرة بدل اسم نطاق؛ هذه علامة تستحق التحقق وليست دليلاً قاطعاً.")
                }
            } else if (host.contains(":")) {
                if (isPrivateOrLocalIpv6(host)) {
                    findings.add(0 to "عنوان IPv6 محلي أو خاص؛ قد يكون طبيعياً داخل الشبكة ولا يعني وحده وجود خطر.")
                } else {
                    findings.add(2 to "الرابط يستخدم عنوان IPv6 مباشرة بدل اسم نطاق؛ تحقق من مصدره.")
                }
            } else if (host.matches(Regex("""[0-9.]+""")) ||
                host.startsWith("0x") || host.contains(".0x")) {
                findings.add(3 to "صيغة عنوان IP رقمية غير مألوفة؛ قد تُستخدم لإخفاء الوجهة الحقيقية.")
            }

            if (host.count { it == '.' } >= 4) findings.add(1 to "اسم النطاق يحتوي على مستويات فرعية كثيرة؛ تحقق من اسم الموقع الأساسي.")
            val shorteners = setOf(
                "bit.ly", "tinyurl.com", "t.co", "is.gd", "cutt.ly", "rb.gy",
                "rebrand.ly", "lnkd.in", "ow.ly", "buff.ly", "shorturl.at",
                "tiny.cc", "s.id", "soo.gd", "bl.ink", "v.gd", "trib.al",
                "forms.gle", "bit.do", "clck.ru", "t.ly"
            )
            if (host in shorteners) findings.add(2 to "هذا رابط مختصر يخفي الوجهة النهائية؛ لا تفتحه إذا لم تكن تثق بالمرسل.")

            // Local heuristics: brand-like subdomains and redirect parameters.
            val protectedBrandWords = setOf(
                "paypal", "google", "apple", "microsoft", "facebook",
                "instagram", "whatsapp", "amazon", "netflix", "bankofamerica"
            )
            val hostLabels = host.split(".")
            val mainHostLabel = registrableDomainLabel(host)
            val impersonatedBrands = protectedBrandWords.filter { brand ->
                hostLabels.dropLast(2).contains(brand) ||
                    (hostLabels.size >= 3 && hostLabels.dropLast(1).any { it == brand } && mainHostLabel != brand)
            }
            if (impersonatedBrands.isNotEmpty()) {
                findings.add(3 to "اسم علامة معروفة يظهر داخل نطاق فرعي وليس في اسم النطاق الأساسي (${impersonatedBrands.joinToString(", ")}). تحقق من العنوان الكامل.")
            }

            val lookalikeBrands = protectedBrandWords.filter { brand ->
                mainHostLabel != brand && mainHostLabel.length >= 4 &&
                    isOneTypoAway(mainHostLabel, brand)
            }
            if (lookalikeBrands.isNotEmpty()) {
                findings.add(3 to "اسم النطاق يشبه في الكتابة علامة معروفة (${lookalikeBrands.joinToString(", ")}) مع اختلاف حرف تقريباً؛ تحقق من العنوان الرسمي.")
            }

            // Detect brand names embedded in deceptive labels, not only exact labels or one-edit typos.
            val deceptiveBrandLabels = hostLabels.filter { label ->
                protectedBrandWords.any { brand ->
                    label != brand && label.length >= brand.length + 2 &&
                        (label.startsWith(brand) || label.endsWith(brand) ||
                            label.contains("$brand-") || label.contains("-$brand"))
                }
            }
            if (deceptiveBrandLabels.isNotEmpty()) {
                findings.add(3 to "أحد أجزاء اسم الموقع يضم اسم علامة معروفة مع كلمات إضافية (${deceptiveBrandLabels.distinct().joinToString(", ")}). قد يُستعمل ذلك للإيهام بالانتماء إلى العلامة؛ تحقق من النطاق الأساسي.")
            }

            if (host.contains("--") || host.startsWith("-") || host.endsWith("-")) {
                findings.add(1 to "اسم النطاق يحتوي شرطات غير معتادة؛ تحقق من تهجئته.")
            }

            // Decode query parameter names before checking redirect indicators.
            // This catches simple percent-encoded spellings such as %72edirect.
            val queryKeys = (parsed.rawQuery ?: "").substringBefore("#")
                .split("&")
                .mapNotNull { part ->
                    val encodedKey = part.substringBefore("=")
                    val decodedKey = runCatching {
                        URLDecoder.decode(encodedKey, "UTF-8")
                    }.getOrDefault(encodedKey)
                    decodedKey.lowercase(Locale.ROOT).takeIf { it.isNotBlank() }
                }
            val redirectKeys = setOf("redirect", "redirect_url", "return", "returnurl", "next", "continue", "destination", "dest", "target", "url")
            if (queryKeys.any { it in redirectKeys }) {
                findings.add(1 to "الرابط يحتوي معامل تحويل إلى وجهة أخرى؛ تأكد من الموقع الذي سيُفتح قبل المتابعة.")
            }

            // Warn when secrets are placed in the URL itself; URLs may be retained in
            // browser history, screenshots, analytics, and copied messages.
            val sensitiveQueryKeys = setOf(
                "password", "passwd", "pwd", "token", "access_token",
                "refresh_token", "api_key", "apikey", "secret", "session",
                "sessionid", "auth", "authorization", "otp", "code"
            )
            val exposedKeys = (parsed.rawQuery ?: "").split("&").mapNotNull { part ->
                val key = runCatching {
                    URLDecoder.decode(part.substringBefore("="), "UTF-8")
                        .lowercase(Locale.ROOT)
                }.getOrDefault("").trim()
                key.takeIf { it in sensitiveQueryKeys && part.contains("=") &&
                    part.substringAfter("=", "").isNotBlank() }
            }.distinct()
            if (exposedKeys.isNotEmpty()) {
                findings.add(2 to "الرابط يبدو أنه يحمل قيمة حساسة داخل query (${exposedKeys.joinToString(", ")}). لا تشارك الرابط؛ قد تبقى هذه القيم في السجل أو الرسائل.")
            }

            // Inspect redirect values through up to three decoding layers without following them.
            // '+' is preserved because it is a valid literal in URL query values.
            // Extract from rawAuthority as a fallback: URI.host can be null for IDNs
            // and some authority forms even when the authority itself is parseable.
            fun extractRedirectHost(value: String): String? {
                val trimmed = value.trim()
                val absolute = when {
                    trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> trimmed
                    trimmed.startsWith("//") -> "https:$trimmed"
                    else -> return null
                }
                val uri = runCatching { URI(absolute) }.getOrNull()
                val rawAuthority = uri?.rawAuthority ?: absolute.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
                if (rawAuthority.isBlank()) return null
                val withoutUserInfo = rawAuthority.substringAfterLast('@')
                val extracted = when {
                    withoutUserInfo.startsWith("[") && "]" in withoutUserInfo ->
                        withoutUserInfo.substringAfter("[").substringBefore("]")
                    withoutUserInfo.count { it == ':' } == 1 ->
                        withoutUserInfo.substringBeforeLast(':')
                    else -> withoutUserInfo
                }
                return extracted.lowercase(Locale.ROOT).trimEnd('.').takeIf { it.isNotBlank() }
            }

            val redirectTargets = mutableSetOf<String>()
            var sawNestedEncoding = false
            (parsed.rawQuery ?: "").split("&").forEach { part ->
                val key = runCatching {
                    URLDecoder.decode(part.substringBefore("="), "UTF-8").lowercase(Locale.ROOT)
                }.getOrDefault("")
                if (key !in redirectKeys || !part.contains("=")) return@forEach
                var candidate = part.substringAfter("=")
                // Inspect up to five layers. Each pass is local string decoding only.
                repeat(5) { depth ->
                    val decoded = runCatching {
                        URLDecoder.decode(candidate.replace("+", "%2B"), "UTF-8")
                    }.getOrDefault(candidate)
                    if (decoded != candidate && depth > 0) sawNestedEncoding = true
                    candidate = decoded
                    extractRedirectHost(candidate)?.let { redirectTargets.add(it) }
                    // Some redirectors wrap destinations in JSON/text instead of a bare URL.
                    Regex("""(?i)(?:https?://|//)[^\\s"'<>\\]+""")
                        .findAll(candidate)
                        .take(8)
                        .forEach { match ->
                            extractRedirectHost(match.value.trimEnd('.', ',', ';', ')', ']'))?.let {
                                redirectTargets.add(it)
                            }
                        }
                }
            }
            val externalRedirectHosts = redirectTargets.filter { it != host }.sorted()
            if (externalRedirectHosts.isNotEmpty()) {
                findings.add(3 to "تم استخراج نطاق وجهة تحويل خارجية: ${externalRedirectHosts.joinToString(", ")}؛ لم تتم زيارة الوجهة.")
            }
            if (redirectTargets.size > 1) {
                findings.add(1 to "الرابط يحتوي أكثر من وجهة تحويل محتملة؛ راجع العنوان كاملاً قبل المتابعة.")
            }
            if (sawNestedEncoding) {
                findings.add(1 to "قيمة تحويل تحتوي أكثر من طبقة ترميز؛ قد تُخفي الوجهة المقصودة عند معالجتها.")
            }
            if (Regex("(?i)%25(?:2f|3a|40|2e)").containsMatchIn(raw)) {
                findings.add(2 to "الرابط يحتوي ترميزاً مئوياً مزدوجاً قد يخفي فواصل أو رموزاً مهمة عند فك الترميز.")
            }
            val fragment = parsed.rawFragment.orEmpty()
            if (fragment.contains("http://", true) || fragment.contains("https://", true)) {
                findings.add(1 to "جزء fragment يعرض عنواناً آخر؛ النص بعد # لا يغيّر المضيف الفعلي.")
            }

            val suspiciousTlds = setOf("zip", "mov", "top", "click", "work", "country", "gq", "tk")
            if (host.substringAfterLast('.', "") in suspiciousTlds) {
                findings.add(1 to "امتداد النطاق يستحق تحققاً إضافياً؛ الامتداد وحده لا يثبت أن الموقع ضار.")
            }
            if (host.split(".").any { it.length > 25 }) findings.add(1 to "جزء من اسم النطاق طويل بشكل غير معتاد.")

            val port = parsed.port
            if (port != -1 && port !in setOf(80, 443)) {
                findings.add(1 to "الرابط يستخدم منفذاً غير افتراضي ($port)؛ قد يكون طبيعياً، لكن تحقق من الجهة.")
            }

            val pathAndQuery = ((parsed.rawPath ?: "") + " " + (parsed.rawQuery ?: "")).lowercase(Locale.ROOT)
            val baitWords = listOf("login", "verify", "verification", "account", "password", "secure", "update", "gift", "prize", "free", "wallet", "bank", "signin", "confirm")
            val matched = baitWords.filter { word ->
                Regex("(^|[^a-z])" + Regex.escape(word) + "([^a-z]|$)").containsMatchIn(pathAndQuery)
            }
            if (matched.isNotEmpty()) {
                findings.add(1 to "مسار الرابط يحتوي كلمات مرتبطة بتسجيل الدخول أو الحساب (${matched.joinToString(", ")}). وجودها وحده لا يعني الاحتيال.")
            }

            // Check hostname labels separately; words split by hyphens are also detected.
            val hostWords = host.split(Regex("[^a-z]+"))
                .filter { it.isNotBlank() && it in baitWords }
                .distinct()
            if (hostWords.isNotEmpty()) {
                findings.add(1 to "اسم النطاق يحتوي كلمات مرتبطة بتسجيل الدخول أو الحساب (${hostWords.joinToString(", ")}). تحقق من العنوان قبل المتابعة.")
            }

            val filePath = (parsed.rawPath ?: "").lowercase(Locale.ROOT)
            val riskyExtensions = setOf(".apk", ".exe", ".msi", ".bat", ".cmd", ".scr", ".js")
            if (riskyExtensions.any { filePath.endsWith(it) }) {
                findings.add(2 to "الرابط يبدو أنه يشير إلى ملف قابل للتنفيذ؛ لا تحمّله إلا من مصدر رسمي موثوق.")
            }

            val score = findings.sumOf { it.first }.coerceAtMost(10)
            val level = when {
                score >= 5 -> "مرتفع"
                score >= 2 -> "متوسط"
                score >= 1 -> "منخفض"
                else -> "لم تظهر مؤشرات واضحة"
            }
            val details = if (findings.isEmpty()) {
                "لم نعثر على مؤشرات التحذير التي يبحث عنها هذا الفاحص. هذا لا يثبت أن الموقع آمن."
            } else {
                findings.joinToString("\n") { "• ${it.second}" }
            }
            val advice = when {
                score >= 5 -> "النصيحة: لا تفتح الرابط ولا تدخل أي معلومات قبل التأكد من المرسل والموقع الرسمي."
                score >= 2 -> "النصيحة: تحقق من اسم النطاق والمرسل، ولا تدخل بيانات حساسة."
                else -> "النصيحة: تأكد دائماً من عنوان الموقع والمرسل قبل إدخال أي معلومات."
            }
            val report = "مستوى التنبيه: $level ($score/10)\n\n$details\n\n$advice\n\nملاحظة: هذا تقييم إرشادي لشكل الرابط فقط. لا يفتح الموقع، ولا يتبع التحويلات، ولا يستعلم عن قواعد بيانات السمعة؛ لذلك لا يثبت أن الموقع آمن ولا يضمن اكتشاف كل عمليات التصيد."
            result.text = report
            saveReport(raw, report)
        }
        actionButton("📋 نسخ تقرير الفحص") {
            val report = result.text.toString()
            if (!report.startsWith("مستوى التنبيه:")) {
                Toast.makeText(this, "افحص رابطاً أولاً حتى تتمكن من نسخ التقرير.", Toast.LENGTH_SHORT).show()
            } else {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Phone Guardian - تقرير الفحص", report))
                Toast.makeText(this, "تم نسخ تقرير الفحص.", Toast.LENGTH_SHORT).show()
            }
        }
        actionButton("رجوع") { showHome() }
    }
}