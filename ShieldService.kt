package com.shield.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors

class ShieldService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var power: PowerManager
    private var rx: List<Pair<String, Regex>> = emptyList()
    private var rxVer = -1
    private var home: String? = null
    private var classifier: Classifier? = null
    private var lastScan = 0L
    private var lastShot = 0L
    private var lastBlock = 0L
    private var lastLock = 0L
    private var lastHash = 0
    private val settingsPkgs = setOf(
        "com.android.settings", "com.google.android.packageinstaller",
        "com.android.packageinstaller", "com.samsung.android.packageinstaller"
    )
    private val hostRx = Regex("([a-z0-9-]+\\.)+[a-z]{2,}")

    override fun onServiceConnected() {
        power = getSystemService(POWER_SERVICE) as PowerManager
        classifier = Classifier.load(this)
        home = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName
    }

    override fun onInterrupt() {}
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui" || pkg == home) return
        if (!power.isInteractive) return  // screen off: do nothing
        val now = SystemClock.elapsedRealtime()
        val stateChange = e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // App lock
        val locked = Store.lockedApps(this).contains(pkg)
        if (locked && !Store.granted(this, "unl_$pkg")) {
            if (now - lastLock > 1500) { lastLock = now; showScreen("applock", pkg, "", false) }
            return
        }

        if (!stateChange && now - lastScan < 2000) return  // throttle
        lastScan = now
        if (locked) Store.grant(this, "unl_$pkg", 5 * 60_000)
        scan(pkg, now)
    }

    private fun scan(pkg: String, now: Long) {
        val root = rootInActiveWindow ?: return
        val sb = StringBuilder()
        collect(root, sb, 0, intArrayOf(0))
        root.recycle()
        val low = sb.toString().lowercase()

        // Protect Shield's own settings / uninstall screens with the PIN
        if (pkg in settingsPkgs && low.contains("shield")) {
            if (!Store.granted(this, "guard") && now - lastLock > 1500) { lastLock = now; showScreen("guard", pkg, "", false) }
            return
        }

        val hard = hardHit(low)
        if (hard != null) { block(pkg, hard, true); return }
        if (Store.granted(this, "ok_$pkg")) return
        val kw = keywordHit(low)
        if (kw != null) { block(pkg, kw, false); return }
        shoot(pkg, now)
    }

    private fun collect(n: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int, cnt: IntArray) {
        if (n == null || cnt[0] > 400 || depth > 25 || !n.isVisibleToUser) return
        cnt[0]++
        n.text?.let { if (it.length < 400) sb.append(it).append(' ') }
        n.contentDescription?.let { if (it.length < 200) sb.append(it).append(' ') }
        for (i in 0 until n.childCount) {
            val ch = n.getChild(i) ?: continue
            collect(ch, sb, depth + 1, cnt)
            ch.recycle()
        }
    }

    private fun hardHit(low: String): String? {
        for (m in hostRx.findAll(low)) {
            val h = m.value
            for (d in Store.HARD) if (h == d || h.endsWith(".$d")) return d
        }
        return null
    }

    private fun keywordHit(low: String): String? {
        val v = Store.kwVersion(this)
        if (v != rxVer) {
            rx = Store.keywords(this).filter { it.isNotBlank() }.map {
                it to Regex("(?<![a-z0-9])" + Regex.escape(it.lowercase().trim()) + "(?![a-z0-9])")
            }
            rxVer = v
        }
        for ((k, r) in rx) if (r.containsMatchIn(low)) return k
        return null
    }

    private fun shoot(pkg: String, now: Long) {
        val cl = classifier ?: return
        if (now - lastShot < 3000) return
        lastShot = now
        takeScreenshot(Display.DEFAULT_DISPLAY, worker, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                val hb = r.hardwareBuffer
                try {
                    val hw = Bitmap.wrapHardwareBuffer(hb, r.colorSpace) ?: return
                    val soft = hw.copy(Bitmap.Config.ARGB_8888, false) ?: return
                    hw.recycle()
                    // Skip if the screen looks unchanged since last check (saves battery)
                    val tiny = Bitmap.createScaledBitmap(soft, 8, 8, true)
                    val px = IntArray(64)
                    tiny.getPixels(px, 0, 8, 0, 0, 8, 8)
                    var h = 17
                    for (p in px) h = h * 31 + (((p shr 16) and 255) shr 5)
                    if (h == lastHash) return
                    lastHash = h
                    val th = floatArrayOf(0.5f, 0.7f, 0.85f)[Store.sensitivity(this@ShieldService)]
                    if (cl.score(soft) >= th) handler.post { block(pkg, "explicit image", false) }
                } finally { hb.close() }
            }
            override fun onFailure(errorCode: Int) {}
        })
    }

    private fun block(pkg: String, trigger: String, hard: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlock < 2500) return
        lastBlock = now
        Store.addBlock(this)
        performGlobalAction(GLOBAL_ACTION_HOME)
        showScreen("block", pkg, trigger, hard)
    }

    private fun showScreen(mode: String, pkg: String, trigger: String, hard: Boolean) {
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("mode", mode).putExtra("pkg", pkg)
                .putExtra("trigger", trigger).putExtra("hard", hard)
        )
    }
}
