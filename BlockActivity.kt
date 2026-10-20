package com.shield.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.RadioButton
import android.widget.RadioGroup

class BlockActivity : Activity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        render()
    }

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); setIntent(i); render() }

    @Suppress("DEPRECATION")
    override fun onBackPressed() { goHome() }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun openApp(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
        finish()
    }

    private fun render() {
        val mode = intent.getStringExtra("mode") ?: "block"
        val pkg = intent.getStringExtra("pkg") ?: ""
        if (mode == "block") blockView(pkg, intent.getStringExtra("trigger") ?: "", intent.getBooleanExtra("hard", false))
        else pinView(mode, pkg)
    }

    private fun blockView(pkg: String, trigger: String, hard: Boolean) {
        val (sv, col) = page()
        col.add(label("Blocked", 32f, true), 60)
        col.add(label(
            if (hard) "This site is permanently blocked and can't be unlocked."
            else "Shield detected: $trigger", 16f, color = C.MUTED
        ))
        col.add(button("Go home") { goHome() }, 28)
        if (!hard) col.add(button("Request unblock", false) { form(pkg) })
        setContentView(sv)
    }

    private fun form(pkg: String) {
        val (sv, col) = page()
        col.add(label("Why do you need access?", 24f, true), 40)
        col.add(label("Be specific. Vague or reused reasons are denied.", 14f, color = C.MUTED))
        val cats = arrayOf("Work or school", "Health or medical", "News or research", "Other")
        val rg = RadioGroup(this)
        cats.forEachIndexed { i, c ->
            rg.addView(RadioButton(this).apply { text = c; id = i + 1; setTextColor(C.TEXT) })
        }
        rg.check(1)
        col.add(rg)
        val et = input("What exactly do you need, and why?", multi = true)
        col.add(et)
        val msg = label("", 15f, color = C.MUTED)
        col.add(msg)
        col.add(button("Submit") {
            val (ok, m) = Store.requestUnblock(this, pkg, cats[rg.checkedRadioButtonId - 1], et.text.toString())
            msg.setText(m)
            msg.setTextColor(if (ok) C.OK else C.BAD)
            if (ok) Handler(Looper.getMainLooper()).postDelayed({ openApp(pkg) }, 1200)
        })
        col.add(button("Cancel", false) { goHome() })
        setContentView(sv)
    }

    private fun pinView(mode: String, pkg: String) {
        val (sv, col) = page()
        col.add(label(if (mode == "guard") "Enter PIN to open these settings" else "Enter PIN to open this app", 22f, true), 60)
        val et = input("PIN", pin = true)
        col.add(et, 20)
        val msg = label("", 15f, color = C.BAD)
        col.add(msg)
        col.add(button("Unlock") {
            val r = Store.checkPin(this, et.text.toString())
            if (r == 0L) {
                if (mode == "guard") { Store.grant(this, "guard", 2 * 60_000); finish() }
                else { Store.grant(this, "unl_$pkg", 5 * 60_000); openApp(pkg) }
            } else if (r > 0) msg.setText("Too many tries. Locked for ${r / 60_000 + 1} min.")
            else { msg.setText("Wrong PIN."); et.setText("") }
        })
        col.add(button("Cancel", false) { goHome() })
        setContentView(sv)
    }
}
