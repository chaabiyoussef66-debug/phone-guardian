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
        try {
            val displayName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "ملف غير معروف"
            val mime = contentResolver.getType(uri)?.lowercase(Locale.ROOT) ?: "غير معروف"
            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    total += n
                    if (total > 25 * 1024 * 1024) throw IllegalArgumentException("حجم الملف أكبر من 25 ميغابايت.")
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            } ?: throw IllegalStateException("تعذرت قراءة الملف.")
            val findings = mutableListOf<String>()
            val lowerName = displayName.lowercase(Locale.ROOT)
            val ext = lowerName.substringAfterLast('.', "")
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
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
                has(0x50,0x4b,0x03,0x04) -> "أرشيف ZIP (قد يحتوي APK أو ملفات أخرى)"
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
            if (kind == "JPEG") {
                var end = -1
                for (i in 0 until bytes.size - 1) {
                    if ((bytes[i].toInt() and 255) == 0xff && (bytes[i + 1].toInt() and 255) == 0xd9) end = i + 2
                }
                if (end >= 0 && hasNonPaddingTail(end)) findings.add("توجد بيانات غير فارغة بعد علامة نهاية JPEG؛ قد تكون بيانات ملحقة، وهذا ليس دليلاً وحده على البرمجيات الخبيثة.")
            } else if (kind == "PNG") {
                val marker = "IEND".toByteArray(Charsets.US_ASCII)
                var pos = -1
                for (i in 0..bytes.size - marker.size) {
                    if (marker.indices.all { bytes[i + it] == marker[it] }) pos = i
                }
                val end = if (pos >= 0) pos + 8 else -1
                if (end >= 0 && hasNonPaddingTail(end)) findings.add("توجد بيانات غير فارغة بعد نهاية بنية PNG؛ تستحق المراجعة.")
            } else if (kind == "GIF") {
                val end = bytes.indexOf(0x3b.toByte()) + 1
                if (end > 0 && hasNonPaddingTail(end)) findings.add("توجد بيانات غير فارغة بعد علامة نهاية GIF؛ تستحق المراجعة.")
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
                isImage -> "تحليل الصورة: تم فحص التوقيع وبعض علامات نهاية الملف بحثاً عن بيانات ملحقة."
                kind == "PDF" -> "تحليل PDF: تم البحث عن مؤشرات JavaScript والإجراءات التلقائية والمرفقات والبيانات بعد EOF."
                isVideo -> "تحليل الفيديو: تم تحديد حاوية الوسائط من الترويسة ومقارنتها بالامتداد ونوع MIME."
                isAudio -> "تحليل الصوت: تم تحديد صيغة الصوت من الترويسة ومقارنتها بالامتداد ونوع MIME."
                else -> "تحليل الوسائط: لم تُعرف صيغة صورة أو PDF أو فيديو أو صوت مدعومة."
            }
            val report = "نتيجة فحص الملف\nالاسم: $displayName\nالنوع المعلن: $mime\nالترويسة المقروءة: $kind\nالحجم: \${bytes.size} بايت\nSHA-256: $digest\n\n$formatAnalysis\n\n$summary\n\n$pythonAnalysis\n\nالنصيحة: $advice\n\nملاحظة: فحص محلي أولي لا يشغّل الملف ولا يرفعه. التحليل البنيوي والمؤشرات النصية لا تعادل محرك مضاد فيروسات، وقد تفوّت التهديدات أو تعطي تحذيرات غير دقيقة."
            result.text = report
            saveReport("ملف: $displayName", report)
        } catch (e: Exception) {
            result.text = "تعذر إكمال الفحص: ${e.message ?: "خطأ غير معروف"}\n\nلم يتم تشغيل الملف."
        }
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
            val shorteners = setOf("bit.ly", "tinyurl.com", "t.co", "is.gd", "cutt.ly", "rb.gy", "rebrand.ly", "lnkd.in")
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
            val report = "مستوى التنبيه: $level ($score/10)\n\n$details\n\n$advice\n\nملاحظة: هذا تقييم تقريبي يعتمد على شكل الرابط فقط؛ لا يفحص محتوى الموقع ولا يضمن اكتشاف كل عمليات التصيد."
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
