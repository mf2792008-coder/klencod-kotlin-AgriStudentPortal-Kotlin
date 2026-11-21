package com.mustafa.agriportal

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class MainActivity : FragmentActivity() {
    private val prefs by lazy { getSharedPreferences("student_portal_local", Context.MODE_PRIVATE) }
    private lateinit var usernameField: EditText
    private lateinit var passwordField: EditText

    companion object {
        private const val KEY_USERNAME = "local_username"
        private const val KEY_SALT = "password_salt"
        private const val KEY_HASH = "password_hash"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val REQUEST_ENROLL_BIOMETRIC = 701
        private const val PBKDF2_ITERATIONS = 160_000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(11, 89, 66)
        window.navigationBarColor = Color.rgb(11, 89, 66)
        showLoginScreen()
    }

    private fun showLoginScreen() {
        val firstRun = prefs.getString(KEY_HASH, null) == null
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.rgb(244, 247, 245))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }

        val logo = TextView(this).apply {
            text = "🌱"
            textSize = 38f
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE, 26f)
            elevation = dp(3).toFloat()
            layoutParams = LinearLayout.LayoutParams(dp(86), dp(86)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(18)
            }
        }
        page.addView(logo)

        page.addView(label("بوابة طالب كلية الزراعة", 25f, Color.rgb(20, 65, 49), true).apply {
            gravity = Gravity.CENTER
        })
        page.addView(label("جامعة المنوفية", 15f, Color.rgb(105, 125, 115), false).apply {
            gravity = Gravity.CENTER
        })
        addSpace(page, 28)

        val info = if (firstRun) {
            "أول استخدام؟ أنشئ اسم مستخدم وكلمة مرور خاصة بفتح التطبيق على هذا الجهاز."
        } else {
            "أدخل بيانات الدخول التي أنشأتها للتطبيق، أو استخدم بصمة الهاتف."
        }
        page.addView(infoCard(info, Color.rgb(231, 244, 237)))
        addSpace(page, 22)

        page.addView(label("اسم المستخدم", 14f, Color.rgb(44, 74, 60), true))
        usernameField = EditText(this).apply {
            textSize = 16f
            singleLine = true
            hint = "اكتب اسم المستخدم"
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(Color.WHITE, 14f, Color.rgb(220, 231, 224))
            layoutParams = fieldParams()
            setText(prefs.getString(KEY_USERNAME, "") ?: "")
            setTextColor(Color.rgb(31, 55, 43))
        }
        page.addView(usernameField)
        addSpace(page, 14)

        page.addView(label("كلمة المرور", 14f, Color.rgb(44, 74, 60), true))
        passwordField = EditText(this).apply {
            textSize = 16f
            singleLine = true
            hint = "اكتب كلمة المرور"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(Color.WHITE, 14f, Color.rgb(220, 231, 224))
            layoutParams = fieldParams()
            setTextColor(Color.rgb(31, 55, 43))
        }
        page.addView(passwordField)
        addSpace(page, 20)

        page.addView(actionButton("دخول", true) {
            handlePasswordLogin(firstRun)
        })
        addSpace(page, 12)
        page.addView(actionButton("◉  الدخول بالبصمة", false) {
            if (prefs.getString(KEY_HASH, null) == null) {
                toast("أنشئ بيانات الدخول أولاً")
            } else if (prefs.getBoolean(KEY_BIOMETRIC, false)) {
                authenticateWithBiometric()
            } else {
                setupBiometric()
            }
        })
        addSpace(page, 18)
        page.addView(label("ملاحظة: دخول التطبيق المحلي منفصل عن حسابك الجامعي.", 12f,
            Color.rgb(112, 126, 117), false).apply {
            gravity = Gravity.CENTER
        })

        setContentView(scroll)
    }

    private fun handlePasswordLogin(firstRun: Boolean) {
        val username = usernameField.text.toString().trim()
        val password = passwordField.text.toString()
        if (username.isBlank() || password.length < 4) {
            toast("اكتب اسم المستخدم وكلمة مرور لا تقل عن 4 أحرف")
            return
        }

        if (firstRun) {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val hash = hashPassword(password, salt)
            prefs.edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                .apply()
            showPortal()
            showBiometricSetupMessage()
            return
        }

        val savedUsername = prefs.getString(KEY_USERNAME, "") ?: ""
        val saltText = prefs.getString(KEY_SALT, null)
        val hashText = prefs.getString(KEY_HASH, null)
        if (!username.equals(savedUsername, ignoreCase = true) || saltText == null || hashText == null) {
            toast("اسم المستخدم أو كلمة المرور غير صحيحة")
            return
        }

        try {
            val salt = Base64.decode(saltText, Base64.NO_WRAP)
            val expected = Base64.decode(hashText, Base64.NO_WRAP)
            val actual = hashPassword(password, salt)
            if (MessageDigest.isEqual(expected, actual)) {
                showPortal()
            } else {
                toast("اسم المستخدم أو كلمة المرور غير صحيحة")
            }
        } catch (_: Exception) {
            toast("تعذر التحقق من بيانات الدخول. أعد تثبيت التطبيق أو تواصل مع المطوّر")
        }
    }

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun showBiometricSetupMessage() {
        android.app.AlertDialog.Builder(this)
            .setTitle("تفعيل الدخول بالبصمة")
            .setMessage("تقدر تفعّل الدخول بالبصمة من بصمة مسجلة بالفعل على هاتفك. لو مفيش بصمة مسجلة، أندرويد هيفتح إعدادات الهاتف لتسجيلها. التطبيق لا يقرأ أو يخزّن صورة البصمة.")
            .setPositiveButton("إعداد البصمة") { _, _ -> setupBiometric() }
            .setNegativeButton("لاحقًا", null)
            .show()
    }

    private fun setupBiometric() {
        val manager = BiometricManager.from(this)
        when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                prefs.edit().putBoolean(KEY_BIOMETRIC, true).apply()
                toast("تم تفعيل الدخول بالبصمة")
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> openBiometricEnrollment()
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> showBiometricUnavailable("الهاتف لا يحتوي على مستشعر بصمة متوافق.")
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> showBiometricUnavailable("مستشعر البصمة غير متاح حاليًا. جرّب مرة أخرى لاحقًا.")
            else -> showBiometricUnavailable("تعذر تفعيل البصمة على هذا الجهاز. تأكد من إعداد قفل الشاشة والبصمة.")
        }
    }

    private fun openBiometricEnrollment() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                )
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            }
            startActivityForResult(intent, REQUEST_ENROLL_BIOMETRIC)
        } catch (_: ActivityNotFoundException) {
            showBiometricUnavailable("افتح إعدادات الهاتف ثم الأمان والخصوصية وسجّل بصمتك، وبعدها ارجع للتطبيق.")
        }
    }

    @Deprecated("Handled for compatibility with Android biometric enrollment settings")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENROLL_BIOMETRIC) {
            val available = BiometricManager.from(this)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
            if (available) {
                prefs.edit().putBoolean(KEY_BIOMETRIC, true).apply()
                toast("تم تفعيل الدخول بالبصمة")
            } else {
                toast("لم يتم تسجيل بصمة متوافقة. تقدر تستخدم كلمة المرور عادي")
            }
        }
    }

    private fun showBiometricUnavailable(message: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("البصمة غير متاحة")
            .setMessage(message)
            .setPositiveButton("حسنًا", null)
            .show()
    }

    private fun authenticateWithBiometric() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                showPortal()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                toast("تعذر إكمال التحقق بالبصمة. استخدم كلمة المرور")
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                toast("البصمة غير متطابقة، حاول مرة أخرى")
            }
        })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("تأكيد الهوية")
            .setSubtitle("استخدم بصمة الهاتف لفتح بوابة الطالب")
            .setNegativeButtonText("إلغاء")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(promptInfo)
    }

    private fun showPortal() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(18), dp(18), dp(30))
            setBackgroundColor(Color.rgb(244, 247, 245))
        }
        val scroll = ScrollView(this).apply { addView(page) }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(22), dp(20), dp(22))
            background = GradientDrawable(
                GradientDrawable.Orientation.RIGHT_LEFT,
                intArrayOf(Color.rgb(11, 89, 66), Color.rgb(27, 132, 91))
            ).apply { cornerRadius = dp(22).toFloat() }
        }
        header.addView(label("أهلًا بيك في بوابة الطالب 🌱", 22f, Color.WHITE, true))
        addSpace(header, 6)
        header.addView(label("كلية الزراعة — جامعة المنوفية", 14f, Color.rgb(223, 244, 232), false))
        addSpace(header, 16)
        val logout = TextView(this).apply {
            text = "تسجيل الخروج من البوابة"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = rounded(0x33FFFFFF, 18f)
            setOnClickListener { showLoginScreen() }
        }
        header.addView(logout, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        page.addView(header)
        addSpace(page, 20)

        page.addView(label("روابط ومنصات تهمك", 21f, Color.rgb(24, 61, 44), true))
        page.addView(label("افتح أي منصة من الكارت الخاص بيها — هتظهر داخل التطبيق.", 13f,
            Color.rgb(106, 123, 113), false))
        addSpace(page, 12)

        addLinkCard(page, "🎓", "منصة الطالب الأساسية (ابن الهيثم)",
            "الخدمات والبيانات الأكاديمية للطالب", "https://stdch.menofia.education/static/index.html")
        addLinkCard(page, "📚", "منصة الامتحانات والكتب", "الدخول إلى منصة الامتحانات والمحتوى الدراسي",
            "https://menofia.education/login/index.php")
        addLinkCard(page, "📧", "الإيميل الجامعي (Outlook)", "افتح بريدك الجامعي",
            "https://outlook.office.com")
        addLinkCard(page, "🧭", "وحدة الإرشاد الأكاديمي", "صفحة الوحدة على Facebook",
            "https://www.facebook.com/share/1NyDAZE8Ns/")
        addLinkCard(page, "🩵", "التواصل مع IT (Messenger)", "للتبليغ عن مشكلات المنصات والدعم الفني",
            "https://www.facebook.com/share/1SqXZzdCKY")
        addLinkCard(page, "✈️", "جروب Telegram للإعلانات", "لمتابعة التنبيهات والأشياء المهمة فقط",
            "https://t.me/+z1fV_hCJrqwxZDNk")

        addSpace(page, 8)
        page.addView(infoCard(
            "‼️ مهم عند الإبلاغ عن مشكلة\n\nلازم تحدد اسم المنصة اللي فيها المشكلة بالضبط: منصة ابن الهيثم أم منصة الطالب الرئيسية/منصة الامتحانات والكتب. وصف المشكلة مع تحديد المنصة بيساعد فريق الدعم يفهمها بشكل صحيح.",
            Color.rgb(255, 246, 224)
        ))
        addSpace(page, 12)
        page.addView(infoCard(
            "📣 بخصوص جروب Telegram\n\nالجروب مخصص لنشر الإعلانات والمعلومات المهمة فقط، ومش مخصص للتواصل أو استقبال الشكاوى. الدكتور عبداللطيف مسؤول عن الصفحة على Telegram، فمتبعتلوش مشكلة على الجروب. لو الموضوع ضروري، تقدر تراجع الدكتور في مكتبه.",
            Color.rgb(232, 241, 250)
        ))
        addSpace(page, 12)
        page.addView(infoCard(
            "⏰ مواعيد الرد في وحدة IT\n\nالوحدة متاحة للرد يوميًا من 9:00 صباحًا إلى 1:30 ظهرًا، ما عدا يوم الجمعة والعطلات الرسمية.",
            Color.rgb(231, 244, 237)
        ))
        addSpace(page, 18)
        page.addView(label("بوابة مساعدة طلاب كلية الزراعة", 12f, Color.rgb(130, 145, 135), false).apply {
            gravity = Gravity.CENTER
        })
        setContentView(scroll)
    }

    private fun addLinkCard(parent: LinearLayout, icon: String, title: String, description: String, url: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = rounded(Color.WHITE, 17f, Color.rgb(227, 235, 229))
            elevation = dp(2).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { openPage(title, url) }
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val iconView = TextView(this).apply {
            text = icon
            textSize = 22f
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(232, 244, 237), 13f)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        top.addView(iconView)
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(12), 0, 0, 0)
        }
        titleBlock.addView(label(title, 16f, Color.rgb(31, 64, 47), true))
        addSpace(titleBlock, 4)
        titleBlock.addView(label(description, 12f, Color.rgb(112, 128, 117), false))
        top.addView(titleBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(top)
        addSpace(card, 12)
        val open = TextView(this).apply {
            text = "فتح داخل التطبيق  ←"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.rgb(22, 132, 95), 12f)
        }
        card.addView(open)
        parent.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(11)
        })
    }

    private fun openPage(title: String, url: String) {
        startActivity(Intent(this, WebViewActivity::class.java).apply {
            putExtra(WebViewActivity.EXTRA_TITLE, title)
            putExtra(WebViewActivity.EXTRA_URL, url)
        })
    }

    private fun infoCard(message: String, color: Int): TextView = TextView(this).apply {
        text = message
        textSize = 14f
        setTextColor(Color.rgb(52, 68, 57))
        gravity = Gravity.RIGHT
        setLineSpacing(dp(4).toFloat(), 1.0f)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(color, 16f)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun actionButton(text: String, primary: Boolean, action: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(if (primary) Color.WHITE else Color.rgb(19, 104, 73))
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = rounded(if (primary) Color.rgb(20, 127, 88) else Color.WHITE, 15f,
            if (primary) null else Color.rgb(195, 220, 204))
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.RIGHT
        includeFontPadding = true
    }

    private fun fieldParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54))

    private fun addSpace(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun rounded(color: Int, radiusDp: Float, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}