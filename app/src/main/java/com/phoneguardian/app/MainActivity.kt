package com.phoneguardian.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface

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
        content.addView(paragraph("حماية هاتفك تبدأ بمعرفة التطبيقات والإعدادات المهمة. هذه نسخة أولية بأدوات آمنة ولا تحتاج إلى Root أو Shizuku."))
        actionButton("📱 معلومات الهاتف") { showDeviceInfo() }
        actionButton("📦 التطبيقات الظاهرة في القائمة") { showApps() }
        actionButton("🔐 إعدادات أمان Android") {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }
        actionButton("🔎 فحص رابط مبدئي") { showLinkCheck() }
        content.addView(paragraph("تنبيه: هذا الإصدار لا يدّعي اكتشاف جميع الفيروسات. فحص الروابط مبدئي ومحلي فقط."))
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
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

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
        content.addView(title("فحص رابط مبدئي"))
        content.addView(paragraph("أدخل الرابط. سنبحث محلياً عن علامات تحذير واضحة فقط، ولن نفتح الرابط أو نتصل به."))
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
            val lower = raw.lowercase()
            val warnings = mutableListOf<String>()
            if (!lower.startsWith("https://")) warnings.add("الرابط لا يستخدم HTTPS.")
            if (raw.contains("@")) warnings.add("يحتوي الرابط على الرمز @ وقد يخفي الوجهة.")
            if (raw.length > 120) warnings.add("الرابط طويل بشكل غير معتاد.")
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                val host = lower.removePrefix("https://").removePrefix("http://").substringBefore("/")
                if (host.count { it == '.' } >= 4) warnings.add("اسم النطاق يحتوي على نقاط كثيرة.")
            } else {
                warnings.add("صيغة الرابط غير واضحة. تأكد من كتابته كاملاً.")
            }
            result.text = if (warnings.isEmpty()) {
                "لم نعثر على علامة تحذير بسيطة. هذا لا يعني أن الرابط آمن."
            } else {
                "تحذيرات:\n• " + warnings.joinToString("\n• ") +
                    "\n\nلا تفتح الرابط إذا لم تكن تثق بمصدره."
            }
        }
        actionButton("رجوع") { showHome() }
    }
}
