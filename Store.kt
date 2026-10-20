package com.shield.app

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Store {
    val STARTER = listOf(
        "porn", "porno", "pornhub", "xvideos", "xnxx", "xhamster", "redtube", "youporn",
        "onlyfans", "hentai", "xxx", "nsfw", "nude", "nudes", "camgirl", "erotica",
        "blowjob", "milf", "fetish"
    )
    // Permanently blocked, can never be unlocked
    val HARD = listOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com", "youporn.com",
        "spankbang.com", "eporner.com", "onlyfans.com", "chaturbate.com", "stripchat.com",
        "rule34.xxx", "nhentai.net", "hanime.tv"
    )

    private fun p(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("shield", Context.MODE_PRIVATE)

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // ---------- PIN ----------
    fun hasPin(c: Context) = p(c).contains("pin_hash")

    private fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun setPin(c: Context, pin: String) {
        val salt = SecureRandom().nextLong().toString()
        p(c).edit().putString("pin_salt", salt).putString("pin_hash", hash(pin, salt)).apply()
    }

    /** 0 = correct, -1 = wrong, >0 = locked out for that many ms */
    fun checkPin(c: Context, pin: String): Long {
        val sp = p(c)
        val now = System.currentTimeMillis()
        val until = sp.getLong("lock_until", 0)
        if (now < until) return until - now
        val ok = hash(pin, sp.getString("pin_salt", "") ?: "") == sp.getString("pin_hash", "")
        if (ok) { sp.edit().putInt("fails", 0).apply(); return 0 }
        val f = sp.getInt("fails", 0) + 1
        if (f >= 5) {
            sp.edit().putInt("fails", 0).putLong("lock_until", now + 5 * 60_000).apply()
            return 5 * 60_000
        }
        sp.edit().putInt("fails", f).apply()
        return -1
    }

    // ---------- Keywords ----------
    fun keywords(c: Context): Set<String> = p(c).getStringSet("kw", null)?.toSet() ?: STARTER.toSet()
    fun kwVersion(c: Context): Int = p(c).getInt("kwv", 0)
    private fun saveKw(c: Context, s: Set<String>) {
        p(c).edit().putStringSet("kw", HashSet(s)).putInt("kwv", kwVersion(c) + 1).apply()
    }
    fun addKeyword(c: Context, k: String) { saveKw(c, keywords(c) + k.trim().lowercase()) }
    fun removeKeyword(c: Context, k: String) { saveKw(c, keywords(c) - k) }

    fun matchKeyword(text: String, words: Set<String>): String? {
        val t = text.lowercase()
        for (w in words) {
            val k = w.lowercase().trim()
            if (k.isEmpty()) continue
            if (Regex("(?<![a-z0-9])" + Regex.escape(k) + "(?![a-z0-9])").containsMatchIn(t)) return k
        }
        return null
    }

    // ---------- App lock ----------
    fun lockedApps(c: Context): Set<String> = p(c).getStringSet("locked", null)?.toSet() ?: emptySet()
    fun setLockedApps(c: Context, s: Set<String>) { p(c).edit().putStringSet("locked", HashSet(s)).apply() }

    // ---------- Counter (only shown inside the app) ----------
    fun addBlock(c: Context) {
        val sp = p(c)
        val d = today()
        val n = if (sp.getString("blk_day", "") == d) sp.getInt("blk_today", 0) else 0
        sp.edit().putString("blk_day", d).putInt("blk_today", n + 1).putInt("blk_total", sp.getInt("blk_total", 0) + 1).apply()
    }
    fun blocksTotal(c: Context) = p(c).getInt("blk_total", 0)
    fun blocksToday(c: Context): Int = if (p(c).getString("blk_day", "") == today()) p(c).getInt("blk_today", 0) else 0

    // ---------- Sensitivity: 0 strict, 1 normal, 2 lenient ----------
    fun sensitivity(c: Context) = p(c).getInt("sens", 1)
    fun setSensitivity(c: Context, v: Int) { p(c).edit().putInt("sens", v).apply() }

    // ---------- Temporary grants ----------
    fun grant(c: Context, key: String, ms: Long) { p(c).edit().putLong("g_$key", System.currentTimeMillis() + ms).apply() }
    fun granted(c: Context, key: String) = System.currentTimeMillis() < p(c).getLong("g_$key", 0)

    // ---------- Log ----------
    fun log(c: Context): List<String> = (p(c).getString("log", "") ?: "").split("\n").filter { it.isNotBlank() }
    private fun addLog(c: Context, msg: String) {
        val stamp = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date())
        val lines = (log(c) + "$stamp  $msg").takeLast(40)
        p(c).edit().putString("log", lines.joinToString("\n")).apply()
    }

    // ---------- Unblock rules (offline) ----------
    fun requestUnblock(c: Context, pkg: String, category: String, reason: String): Pair<Boolean, String> {
        val sp = p(c)
        val now = System.currentTimeMillis()
        val day = today()
        if (sp.getString("req_day", "") != day) {
            sp.edit().putString("req_day", day).putInt("req_n", 0).putInt("app_n", 0).apply()
        }
        val n = sp.getInt("req_n", 0)
        val approved = sp.getInt("app_n", 0)
        if (n >= 6) return false to "No more requests today."
        if (approved >= 3) return false to "Daily approval limit reached."
        val gap = now - sp.getLong("last_ok", 0)
        if (gap < 20 * 60_000) return false to "Wait ${(20 * 60_000 - gap) / 60_000 + 1} min before the next approval."

        fun deny(m: String): Pair<Boolean, String> {
            sp.edit().putInt("req_n", n + 1).apply()
            addLog(c, "DENIED   $pkg: $m")
            return false to m
        }

        val r = reason.trim()
        if (r.length < 30 || r.split(Regex("\\s+")).size < 6) return deny("Give a longer, specific reason (at least 6 words).")
        if (category == "Other" && r.length < 60) return deny("'Other' needs a more detailed reason.")
        if (matchKeyword(r, keywords(c)) != null) return deny("Your reason contains blocked terms.")
        val norm = r.lowercase().replace(Regex("[^a-z0-9 ]"), "")
        val used = sp.getStringSet("used_reasons", null)?.toSet() ?: emptySet()
        if (norm in used) return deny("You already used that reason. Be specific to this request.")

        sp.edit().putStringSet("used_reasons", HashSet(used + norm))
            .putInt("req_n", n + 1).putInt("app_n", approved + 1).putLong("last_ok", now).apply()
        grant(c, "ok_$pkg", 10 * 60_000)
        addLog(c, "APPROVED $pkg ($category): $r")
        return true to "Approved for 10 minutes."
    }
}
