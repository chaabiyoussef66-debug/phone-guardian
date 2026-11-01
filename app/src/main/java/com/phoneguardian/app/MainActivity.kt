package com.phoneguardian.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import java.net.URI
import java.util.Locale

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
        content.addView(paragraph("فحص الروابط يعتمد على مؤشرات محلية ولا يفتح الرابط أو يتصل بأي خادم. النتيجة ليست ضماناً لسلامة الموقع."))
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

    private fun showLinkCheck() {
        content = baseLayout()
        content.addView(title("فحص رابط متقدم"))
        content.addView(paragraph("أدخل الرابط كاملاً. سنفحص مؤشرات مشبوهة على الهاتف فقط، ولن نفتح الرابط أو نتصل به."))
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

            val normalized = if (raw.contains("://")) raw else "https://$raw"
            val parsed = try { URI(normalized) } catch (_: Exception) { null }

            if (raw.length > 180) findings.add(1 to "الرابط طويل بشكل غير معتاد.")
            if (raw.contains("@")) findings.add(3 to "يحتوي الرابط على @؛ قد تُستخدم لإخفاء اسم الموقع الحقيقي.")
            if (parsed == null || parsed.host.isNullOrBlank()) {
                findings.add(3 to "تعذّر قراءة اسم النطاق. تحقق من صيغة الرابط.")
            } else {
                val host = parsed.host.lowercase(Locale.ROOT).trimEnd('.')
                val scheme = parsed.scheme?.lowercase(Locale.ROOT) ?: ""
                if (scheme != "https") findings.add(2 to "الرابط لا يستخدم HTTPS.")
                if (host.startsWith("xn--") || host.split(".").any { it.startsWith("xn--") }) {
                    findings.add(2 to "اسم النطاق يحتوي على ترميز دولي قد يُستخدم لتقليد أسماء مواقع.")
                }
                if (host.matches(Regex("""(?:\d{1,3}\.){3}\d{1,3}"""))) {
                    findings.add(2 to "الرابط يستخدم عنوان IP مباشرة بدل اسم نطاق معروف.")
                }
                if (host.count { it == '.' } >= 4) findings.add(1 to "اسم النطاق يحتوي على مستويات فرعية كثيرة.")
                val shorteners = setOf("bit.ly", "tinyurl.com", "t.co", "is.gd", "cutt.ly", "rb.gy", "rebrand.ly")
                if (host in shorteners) findings.add(2 to "هذا رابط مختصر يخفي الوجهة النهائية.")
                val suspiciousTlds = setOf("zip", "mov", "top", "click", "work", "country", "gq", "tk")
                if (host.substringAfterLast('.', "") in suspiciousTlds) {
                    findings.add(1 to "امتداد النطاق يستحق التحقق الإضافي؛ الامتداد وحده لا يثبت الخطر.")
                }
                val pathAndQuery = ((parsed.rawPath ?: "") + " " + (parsed.rawQuery ?: "")).lowercase(Locale.ROOT)
                val baitWords = listOf("login", "verify", "verification", "account", "password", "secure", "update", "gift", "prize", "free", "wallet", "bank")
                val matched = baitWords.filter { word -> Regex("(^|[^a-z])" + Regex.escape(word) + "([^a-z]|$)").containsMatchIn(pathAndQuery) }
                if (matched.isNotEmpty()) findings.add(1 to "يحتوي مسار الرابط على كلمات شائعة في صفحات تسجيل الدخول أو الإغراء: ${matched.joinToString(", ")}.")
                if (host.split(".").any { it.length > 25 }) findings.add(1 to "جزء من اسم النطاق طويل بشكل غير معتاد.")
            }

            val score = findings.sumOf { it.first }.coerceAtMost(10)
            val level = when {
                score >= 5 -> "مرتفع"
                score >= 2 -> "متوسط"
                score >= 1 -> "منخفض"
                else -> "لم تظهر مؤشرات واضحة"
            }
            val details = if (findings.isEmpty()) {
                "لم نعثر على مؤشرات التحذير التي يبحث عنها هذا الفاحص."
            } else {
                findings.joinToString("\n") { "• ${it.second}" }
            }
            result.text = "مستوى التنبيه: $level ($score/10)\n\n$details\n\nتنبيه: هذا تقييم مبدئي محلي، وليس حكماً قاطعاً. قد تكون الروابط الخطيرة بلا هذه العلامات، وقد تظهر علامات على روابط سليمة. لا تدخل كلمة المرور أو معلوماتك البنكية في رابط غير موثوق."
        }
        actionButton("رجوع") { showHome() }
    }
}
