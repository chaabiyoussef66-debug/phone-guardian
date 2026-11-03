package com.phoneguardian.app

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Offline structural inspection only. It never executes or extracts the selected file.
 * This is not a full antivirus engine and cannot prove a file is safe.
 */
object FileScanner {
    private const val MAX_BYTES = 25 * 1024 * 1024

    fun scan(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        var name = "ملف مختار"
        var declaredSize: Long? = null
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val ni = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (ni >= 0) name = cursor.getString(ni) ?: name
                val si = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (si >= 0 && !cursor.isNull(si)) declaredSize = cursor.getLong(si)
            }
        }
        val mime = resolver.getType(uri)?.lowercase(Locale.ROOT) ?: "غير معروف"
        if (declaredSize != null && declaredSize!! > MAX_BYTES)
            return "الملف: " + name + "\n⚠️ لم يُفحص: الحجم يتجاوز 25 MB."

        val out = ByteArrayOutputStream()
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) return "الملف: " + name + "\n⚠️ توقف الفحص: الحجم يتجاوز 25 MB."
                out.write(buffer, 0, n)
            }
        } ?: return "تعذّرت قراءة الملف."
        val data = out.toByteArray()
        if (data.isEmpty()) return "الملف فارغ."

        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val actual = when {
            starts(data, 0xFF, 0xD8, 0xFF) -> "JPEG"
            starts(data, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "PNG"
            ascii(data, 0, 6) in setOf("GIF87a", "GIF89a") -> "GIF"
            ascii(data, 0, 4) == "RIFF" && ascii(data, 8, 4) == "WEBP" -> "WEBP"
            ascii(data, 0, 2) == "BM" -> "BMP"
            ascii(data, 0, 5) == "%PDF-" -> "PDF"
            starts(data, 0x4D, 0x5A) -> "ملف تنفيذي Windows"
            starts(data, 0x7F, 0x45, 0x4C, 0x46) -> "ملف تنفيذي Linux"
            starts(data, 0x50, 0x4B, 0x03, 0x04) -> "أرشيف ZIP أو APK"
            starts(data, 0x52, 0x61, 0x72, 0x21) -> "أرشيف RAR"
            starts(data, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> "أرشيف 7z"
            else -> "غير معروف"
        }
        val expected = mapOf(
            "jpg" to "JPEG", "jpeg" to "JPEG", "png" to "PNG", "gif" to "GIF",
            "webp" to "WEBP", "bmp" to "BMP", "pdf" to "PDF",
            "apk" to "أرشيف ZIP أو APK", "zip" to "أرشيف ZIP أو APK",
            "rar" to "أرشيف RAR", "7z" to "أرشيف 7z"
        )[ext]
        val notes = mutableListOf<String>()
        if (expected != null && expected != actual)
            notes.add("امتداد الملف لا يطابق محتواه الحقيقي؛ قد يكون متنكرّاً.")
        val images = setOf("JPEG", "PNG", "GIF", "WEBP", "BMP")
        if (mime.startsWith("image/") && actual !in images)
            notes.add("نوع MIME يقول إنها صورة لكن توقيع المحتوى لا يطابق صورة معروفة.")

        if (actual in images) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { BitmapFactory.decodeByteArray(data, 0, data.size, opts) }
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                notes.add("تعذّر قراءة أبعاد الصورة؛ قد تكون تالفة أو غير مدعومة.")
            } else {
                val pixels = opts.outWidth.toLong() * opts.outHeight.toLong()
                if (opts.outWidth > 20000 || opts.outHeight > 20000 || pixels > 100000000L)
                    notes.add("أبعاد الصورة كبيرة بشكل غير معتاد: " + opts.outWidth + " × " + opts.outHeight)
                else
                    notes.add("أبعاد الصورة: " + opts.outWidth + " × " + opts.outHeight)
            }
            if (actual == "JPEG" && !(data.size >= 4 && data[data.size - 2] == 0xFF.toByte() && data.last() == 0xD9.toByte()))
                notes.add("نهاية JPEG غير متوقعة؛ قد يكون الملف مبتوراً أو يحتوي بيانات إضافية.")
            if (actual == "PNG") {
                val marker = byteArrayOf(0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte())
                val end = lastIndex(data, marker)
                if (end < 0) notes.add("لم يتم العثور على علامة نهاية PNG القياسية.")
                else if (end + marker.size < data.size) notes.add("توجد بيانات بعد نهاية PNG؛ هذا يستحق التحقق ولا يثبت وحده وجود فيروس.")
            }
            if (actual == "GIF" && data.last() != 0x3B.toByte())
                notes.add("نهاية GIF غير متوقعة؛ قد يكون الملف غير مكتمل.")
        } else if (actual.startsWith("ملف تنفيذي")) {
            notes.add("هذا ملف قابل للتنفيذ وليس صورة. لا تشغّله إذا لم تكن تثق بالمصدر.")
        } else if (actual.startsWith("أرشيف")) {
            notes.add("هذا أرشيف مضغوط؛ لم يتم فكّه أو فحص محتوياته.")
        } else if (actual == "غير معروف") {
            notes.add("لم يتم التعرف على توقيع الملف؛ اعتبره غير موثوق حتى تتحقق من مصدره.")
        }

        val hash = MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it) }
        val suspicious = notes.any { it.contains("لا يطابق") || it.contains("غير متوقعة") || it.contains("بيانات بعد") || it.contains("تنفيذي") || it.contains("غير معروف") }
        return buildString {
            append("الملف: " + name + "\n")
            append("النوع المعلن: " + mime + "\n")
            append("النوع المكتشف: " + actual + "\n")
            append("الحجم: " + data.size + " بايت\n")
            append("SHA-256: " + hash + "\n\n")
            append(if (suspicious) "النتيجة: يحتاج إلى الحذر\n\n" else "النتيجة: لم تظهر مؤشرات بنيوية واضحة\n\n")
            if (notes.isEmpty()) append("لم تظهر مؤشرات بنيوية ضمن الفحوصات المحلية.")
            else notes.forEach { append("• " + it + "\n") }
            append("\nتنبيه: هذا فحص محلي أولي للتوقيع والبنية، وليس مضاد فيروسات كاملاً. لا يستطيع تأكيد خلو الصورة من البرمجيات الخبيثة أو اكتشاف كل استغلال للثغرات. لا تفتح الملفات المشبوهة.")
        }
    }

    private fun starts(data: ByteArray, vararg sig: Int): Boolean =
        data.size >= sig.size && sig.indices.all { (data[it].toInt() and 255) == sig[it] }

    private fun ascii(data: ByteArray, offset: Int, length: Int): String =
        if (offset < 0 || offset + length > data.size) "" else
            String(data, offset, length, Charsets.US_ASCII)

    private fun lastIndex(data: ByteArray, marker: ByteArray): Int {
        if (marker.size > data.size) return -1
        for (i in data.size - marker.size downTo 0) {
            var match = true
            for (j in marker.indices) if (data[i + j] != marker[j]) { match = false; break }
            if (match) return i
        }
        return -1
    }
}
