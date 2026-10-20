package com.shield.app

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout

class MainActivity : Activity() {
    private var unlocked = false
    private var stopAt = 0L

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    override fun onStart() { super.onStart(); if (SystemClock.elapsedRealtime() - stopAt > 60_000) unlocked = false }
    override fun onResume() { super.onResume(); render() }
    override fun onStop() { super.onStop(); stopAt = SystemClock.elapsedRealtime() }

    private fun render() {
        if (!Store.hasPin(this)) pinSetup(false)
        else if (!unlocked) lockScreen()
        else home()
    }

    private fun pinSetup(change: Boolean) {
        val (sv, col) = page()
        col.add(label(if (change) "Change PIN" else "Create a PIN", 26f, true), 40)
        col.add(label("Needed to open Shield, change settings, and unlock protected apps. Use 4 to 8 digits.", 15f, color = C.MUTED))
        val old = input("Current PIN", pin = true)
        if (change) col.add(old)
        val a = input("New PIN", pin = true)
        val b = input("Repeat new PIN", pin = true)
        col.add(a); col.add(b)
        val msg = label("", 15f, color = C.BAD)
        col.add(msg)
        col.add(button("Save") {
            val p = a.text.toString()
            if (change && Store.checkPin(this, old.text.toString()) != 0L) msg.setText("Current PIN is wrong.")
            else if (p.length !in 4..8) msg.setText("Use 4 to 8 digits.")
            else if (p != b.text.toString()) msg.setText("PINs don't match.")
            else { Store.setPin(this, p); unlocked = true; render() }
        })
        if (change) col.add(button("Cancel", false) { render() })
        setContentView(sv)
    }

    private fun lockScreen() {
        val (sv, col) = page()
        col.add(label("🛡️ Shield", 30f, true), 60)
        col.add(label("Enter your PIN", 16f, color = C.MUTED))
        val et = input("PIN", pin = true)
        col.add(et, 20)
        val msg = label("", 15f, color = C.BAD)
        col.add(msg)
        col.add(button("Unlock") {
            val r = Store.checkPin(this, et.text.toString())
            if (r == 0L) { unlocked = true; render() }
            else if (r > 0) msg.setText("Too many tries. Try again in ${r / 60_000 + 1} min.")
            else msg.setText("Wrong PIN.")
        })
        setContentView(sv)
    }

    private fun serviceOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.contains("$packageName/")
    }

    private fun adminOn(): Boolean =
        (getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager)
            .isAdminActive(ComponentName(this, AdminReceiver::class.java))

    private fun home() {
        val (sv, col) = page()
        col.add(label("🛡️ Shield", 28f, true), 0)

        // Protection status
        val st = card()
        st.add(label("Protection", 18f, true), 0)
        val svcOn = serviceOn()
        val admOn = adminOn()
        val img = Classifier.available(this)
        st.add(label(if (svcOn) "✓ Screen monitoring on" else "✗ Screen monitoring off", 15f, color = if (svcOn) C.OK else C.BAD), 8)
        if (!svcOn) st.add(button("Turn on in Accessibility") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, 4)
        st.add(label(if (admOn) "✓ Uninstall protection on" else "✗ Uninstall protection off", 15f, color = if (admOn) C.OK else C.BAD), 8)
        if (!admOn) st.add(button("Enable uninstall protection") {
            startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this, AdminReceiver::class.java))
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Makes Shield harder to uninstall.")
            )
        }, 4)
        st.add(label(if (img) "✓ Image scanning on" else "• Image scanning off (no model file, see README)", 15f, color = if (img) C.OK else C.MUTED), 8)
        col.add(st)

        // Counter
        val cc = card()
        cc.add(label("Blocks", 18f, true), 0)
        cc.add(label("Today: ${Store.blocksToday(this)}     Total: ${Store.blocksTotal(this)}", 20f), 6)
        col.add(cc)

        // Sensitivity
        val sc = card()
        sc.add(label("Image sensitivity", 18f, true), 0)
        val names = arrayOf("Strict", "Normal", "Lenient")
        val cur = Store.sensitivity(this)
        sc.add(row(*Array(3) { i ->
            button(names[i] + if (i == cur) " ✓" else "", i == cur) { Store.setSensitivity(this, i); render() }
        }), 6)
        col.add(sc)

        // Keyword bank
        val kc = card()
        kc.add(label("Keyword bank", 18f, true), 0)
        kc.add(label("Blocks any screen showing these whole words.", 14f, color = C.MUTED), 4)
        val ke = input("Add a keyword")
        kc.add(ke)
        kc.add(button("Add") {
            val k = ke.text.toString().trim()
            if (k.length >= 2) { Store.addKeyword(this, k); render() }
        }, 4)
        for (k in Store.keywords(this).sorted()) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r.addView(label(k), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(button("Remove", false) { Store.removeKeyword(this, k); render() })
            kc.add(r, 0)
        }
        col.add(kc)

        // App lock
        val ac = card()
        ac.add(label("App lock", 18f, true), 0)
        ac.add(label("${Store.lockedApps(this).size} apps need your PIN to open.", 14f, color = C.MUTED), 4)
        ac.add(button("Choose apps") { chooseApps() }, 4)
        col.add(ac)

        // Unblock log
        val lc = card()
        lc.add(label("Unblock requests", 18f, true), 0)
        val lines = Store.log(this).takeLast(10).reversed()
        if (lines.isEmpty()) lc.add(label("None yet.", 14f, color = C.MUTED), 4)
        for (l in lines) lc.add(label(l, 13f, color = C.MUTED), 4)
        col.add(lc)

        // Security
        col.add(button("Change PIN", false) { pinSetup(true) }, 16)
        setContentView(sv)
    }

    private fun chooseApps() {
        val pmgr = packageManager
        val apps = pmgr.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName to it.loadLabel(pmgr).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val locked = Store.lockedApps(this).toMutableSet()
        val checked = BooleanArray(apps.size) { apps[it].first in locked }
        AlertDialog.Builder(this)
            .setTitle("Apps behind PIN")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, on ->
                if (on) locked.add(apps[i].first) else locked.remove(apps[i].first)
            }
            .setPositiveButton("Save") { _, _ -> Store.setLockedApps(this, locked); render() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
