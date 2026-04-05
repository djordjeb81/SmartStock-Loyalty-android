package com.smartstock.loyalty

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.smartstock.loyalty.databinding.ActivityLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.OffsetDateTime

class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding

    // --- Live countdown ---
    private val uiHandler = Handler(Looper.getMainLooper())
    private var lockTicker: Runnable? = null

    // da ne iskače više puta u istom prikazu
    private var phoneUnlockPromptShown = false
    private var firstSetupRedirectShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge + safe area (sat/signal + donja traka)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            b.root.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        b.btnLogin.setOnClickListener { attemptLogin() }
        b.btnPickCompany.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        b.etPin.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else false
        }

        updateLockUi()
        refreshSelectedCompanyUi()
        updateLockUi()
    }

    override fun onStart() {
        super.onStart()

        refreshSelectedCompanyUi()
        refreshCompanyPickUi()

        if (!ensureUsersSourceSelected()) {
            b.btnLogin.isEnabled = false
            b.tvLoginStatus.text = "Prvo izaberite firmu."
            return
        }

        updateLockUi()
        maybeStartPhoneUnlock()
    }

    override fun onStop() {
        super.onStop()
        stopLockTicker()
        phoneUnlockPromptShown = false
    }

    override fun onResume() {
        super.onResume()

        refreshSelectedCompanyUi()
        refreshCompanyPickUi()

        if (!ensureUsersSourceSelected()) {
            b.btnLogin.isEnabled = false
            b.tvLoginStatus.text = "Prvo izaberite firmu."
            return
        }

        updateLockUi()
        maybeStartPhoneUnlock()
    }

    // ---------------- Phone Unlock flow ----------------

    private fun maybeStartPhoneUnlock() {
        if (phoneUnlockPromptShown) return
        if (LoginLockoutPrefs.isLocked(this)) return
        if (!SettingsPrefs.isQuickUnlockEnabled(this)) return

        val email = SettingsPrefs.getLastEmail(this)
        if (email.isBlank()) return

        if (!PhoneUnlockAuth.isAvailable(this)) {
            SettingsPrefs.setQuickUnlockEnabled(this, false)
            SettingsPrefs.clearLastEmail(this)
            return
        }

        b.btnLogin.isEnabled = true
        b.tvLoginStatus.text = ""

        phoneUnlockPromptShown = true

        PhoneUnlockAuth.prompt(
            activity = this,
            title = "Brzo otključavanje",
            subtitle = email,
            onSuccess = { loginWithPhoneUnlock(email) },
            onCancelOrFail = {
                b.btnLogin.isEnabled = true
                b.tvLoginStatus.text = ""
                b.etPin.requestFocus()
            },
            onHardError = { msg: String ->
                b.tvLoginStatus.text = "Ne mogu da pokrenem otključavanje telefonom. ($msg)"
                b.btnLogin.isEnabled = true
            }
        )
    }

    private fun loginWithPhoneUnlock(email: String) {
        LoyaltyStore.clear()
        b.btnLogin.isEnabled = false
        b.tvLoginStatus.text = "Učitavanje..."

        lifecycleScope.launch {
            try {
                val selectedFolder = SettingsPrefs.getSelectedUsersFolder(this@LoginActivity)
                val path = if (selectedFolder.isNotBlank()) {
                    DropboxJsonClient.pathForEmail(email, selectedFolder)
                } else {
                    DropboxJsonClient.pathForEmail(email) // fallback na staru logiku
                }

                val jsonText = withContext(Dispatchers.IO) {
                    DropboxJsonClient.downloadJsonByPath(path)
                }

                saveCache(email, jsonText)

                val result = withContext(Dispatchers.Default) {
                    verifyStatusAndBuildStore(jsonText)
                }

                when (result) {
                    is VerifyStatusResult.Success -> {
                        applyStore(result.store)
                        SettingsPrefs.commitPendingUsersSource(this@LoginActivity)
                        goDashboard()
                    }
                    is VerifyStatusResult.FailMessage -> {
                        b.tvLoginStatus.text = result.message
                        b.btnLogin.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                if (e is UserNotFoundException) {
                    SettingsPrefs.clearPendingUsersSource(this@LoginActivity)

                    AlertDialog.Builder(this@LoginActivity)
                        .setTitle("Pogrešna firma")
                        .setMessage("U izabranoj firmi ne postoji nalog za ovaj email. Izaberite drugu firmu.")
                        .setCancelable(false)
                        .setPositiveButton("Izaberi firmu") { _, _ ->
                            startActivity(Intent(this@LoginActivity, SettingsActivity::class.java))
                        }
                        .show()

                    b.tvLoginStatus.text = e.message ?: "Ne postoji nalog za ovaj email."
                } else {
                    b.tvLoginStatus.text = "Ne mogu da učitam nalog. (${e.message ?: "greška"})"
                }

                b.btnLogin.isEnabled = true
            }
        }
    }

    // ---------------- Manual login (email + PIN) ----------------

    private fun attemptLogin() {
        if (!ensureUsersSourceSelected()) return
        if (LoginLockoutPrefs.isLocked(this)) {
            updateLockUi()
            return
        }

        val email = b.etEmail.text?.toString()?.trim().orEmpty()
        val pin = b.etPin.text?.toString()?.trim().orEmpty()

        b.tvLoginStatus.text = ""

        if (email.isBlank()) {
            b.tvLoginStatus.text = "Unesite email."
            b.etEmail.requestFocus()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            b.tvLoginStatus.text = "Email nije ispravan."
            b.etEmail.requestFocus()
            return
        }
        if (pin.length != 4 || pin.any { !it.isDigit() }) {
            b.tvLoginStatus.text = "PIN mora imati tačno 4 cifre."
            b.etPin.requestFocus()
            return
        }

        LoyaltyStore.clear()

        b.btnLogin.isEnabled = false
        b.tvLoginStatus.text = "Učitavanje..."

        lifecycleScope.launch {
            try {
                val activeFolder = SettingsPrefs.getPendingUsersFolder(this@LoginActivity)
                    .ifBlank { SettingsPrefs.getSelectedUsersFolder(this@LoginActivity) }

                val path = if (activeFolder.isNotBlank()) {
                    DropboxJsonClient.pathForEmail(email, activeFolder)
                } else {
                    DropboxJsonClient.pathForEmail(email)
                }

                val jsonText = withContext(Dispatchers.IO) {
                    DropboxJsonClient.downloadJsonByPath(path)
                }

                saveCache(email, jsonText)

                val result = withContext(Dispatchers.Default) {
                    verifyAndBuildStore(jsonText, pin)
                }

                when (result) {
                    is VerifyResult.Success -> {
                        LoginLockoutPrefs.reset(this@LoginActivity)
                        stopLockTicker()

                        applyStore(result.store)
                        SettingsPrefs.commitPendingUsersSource(this@LoginActivity)
                        maybeOfferEnablePhoneUnlock(email)
                    }
                    is VerifyResult.FailWrongPin -> {
                        LoginLockoutPrefs.recordFailedAttempt(this@LoginActivity)

                        if (LoginLockoutPrefs.isLocked(this@LoginActivity)) {
                            updateLockUi()
                        } else {
                            val remainingTries =
                                (LoginLockoutPrefs.MAX_FAILS - LoginLockoutPrefs.getFailCount(this@LoginActivity))
                                    .coerceAtLeast(0)
                            b.tvLoginStatus.text = "Pogrešan PIN. Preostalo pokušaja: $remainingTries"
                            b.btnLogin.isEnabled = true
                        }
                        b.etPin.requestFocus()
                    }
                    is VerifyResult.FailMessage -> {
                        b.tvLoginStatus.text = result.message
                        b.btnLogin.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                if (e is UserNotFoundException) {
                    SettingsPrefs.clearPendingUsersSource(this@LoginActivity)

                    AlertDialog.Builder(this@LoginActivity)
                        .setTitle("Pogrešna firma")
                        .setMessage("U izabranoj firmi ne postoji nalog za ovaj email. Izaberite drugu firmu.")
                        .setCancelable(false)
                        .setPositiveButton("Izaberi firmu") { _, _ ->
                            startActivity(Intent(this@LoginActivity, SettingsActivity::class.java))
                        }
                        .show()

                    b.tvLoginStatus.text = e.message ?: "Ne postoji nalog za ovaj email."
                } else {
                    b.tvLoginStatus.text = "Ne mogu da učitam nalog za ovaj email. (${e.message ?: "greška"})"
                }

                b.btnLogin.isEnabled = true
            }
        }
    }

    private fun maybeOfferEnablePhoneUnlock(email: String) {
        if (SettingsPrefs.isQuickUnlockEnabled(this)) {
            SettingsPrefs.setLastEmail(this, email)
            goDashboard()
            return
        }

        if (!PhoneUnlockAuth.isAvailable(this)) {
            goDashboard()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Brzo otključavanje")
            .setMessage("Želite li da uključite brzo otključavanje telefonom za sledeći ulazak?")
            .setPositiveButton("Da") { _, _ ->
                SettingsPrefs.setQuickUnlockEnabled(this, true)
                SettingsPrefs.setLastEmail(this, email)
                goDashboard()
            }
            .setNegativeButton("Ne") { _, _ ->
                SettingsPrefs.setQuickUnlockEnabled(this, false)
                SettingsPrefs.clearLastEmail(this)
                goDashboard()
            }
            .setCancelable(false)
            .show()
    }

    private fun goDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }

    // ---------------- Lockout UI ----------------

    private fun startLockTickerIfNeeded() {
        if (!LoginLockoutPrefs.isLocked(this)) return
        if (lockTicker != null) return

        lockTicker = object : Runnable {
            override fun run() {
                updateLockUi()
                if (LoginLockoutPrefs.isLocked(this@LoginActivity)) {
                    uiHandler.postDelayed(this, 1000)
                } else {
                    stopLockTicker()
                }
            }
        }

        uiHandler.post(lockTicker!!)
    }

    private fun stopLockTicker() {
        lockTicker?.let { uiHandler.removeCallbacks(it) }
        lockTicker = null
    }

    private fun updateLockUi() {
        if (LoginLockoutPrefs.isLocked(this)) {
            val remainingMs = LoginLockoutPrefs.remainingMs(this)
            val totalSeconds = (remainingMs / 1000).toInt().coerceAtLeast(0)
            val mm = totalSeconds / 60
            val ss = totalSeconds % 60

            b.tvLoginStatus.text =
                "Previše pokušaja. Pokušajte ponovo za ${mm}:${ss.toString().padStart(2, '0')}"

            b.btnLogin.isEnabled = false
            startLockTickerIfNeeded()
        } else {
            b.btnLogin.isEnabled = true
            stopLockTicker()
        }
    }

    // ---------------- Store + verify ----------------

    private data class StoreSnapshot(
        val schemaVersion: Int,
        val lastRefresh: String,
        val currency: String,
        val currentPercent: Double,
        val user: LoyaltyStore.UserInfo,
        val currentQuarter: LoyaltyStore.QuarterSnapshot,
        val previousQuarter: LoyaltyStore.QuarterSnapshot,
        val totals: LoyaltyStore.Totals,
        val payouts: List<LoyaltyStore.Payout>,
        val quarters: List<LoyaltyStore.QuarterHistory>
    )

    private sealed class VerifyResult {
        data class Success(val store: StoreSnapshot) : VerifyResult()
        data object FailWrongPin : VerifyResult()
        data class FailMessage(val message: String) : VerifyResult()
    }

    private sealed class VerifyStatusResult {
        data class Success(val store: StoreSnapshot) : VerifyStatusResult()
        data class FailMessage(val message: String) : VerifyStatusResult()
    }

    private fun verifyAndBuildStore(jsonText: String, pin: String): VerifyResult {
        val root = JSONObject(jsonText)

        val schemaVersion = root.optInt("schemaVersion", 0)
        if (schemaVersion < 3) {
            return VerifyResult.FailMessage("Aplikacija očekuje schemaVersion 3+. (dobijeno: $schemaVersion)")
        }

        val userObj = root.optJSONObject("user")
            ?: return VerifyResult.FailMessage("Greška: user podaci ne postoje u JSON-u.")

        val userStatus = userObj.optString("status", "inactive").trim().lowercase()
        if (userStatus == "blocked") return VerifyResult.FailMessage("Nalog je blokiran, kontaktirajte podršku.")
        if (userStatus != "active") return VerifyResult.FailMessage("Nalog nije aktivan.")

        val auth = root.optJSONObject("auth")
            ?: return VerifyResult.FailMessage("Greška: auth podaci ne postoje u JSON-u.")

        val salt = auth.optString("pinSalt", "").trim()
        val expectedHash = auth.optString("pinHash", "").trim()
        if (salt.isBlank() || expectedHash.isBlank()) {
            return VerifyResult.FailMessage("Greška: pinSalt/pinHash nedostaju u JSON-u.")
        }

        val actualBytes = HashUtil.sha256Bytes(pin + salt)
        val match = HashUtil.matchesExpected(expectedHash, actualBytes)
        if (!match) return VerifyResult.FailWrongPin

        return VerifyResult.Success(buildStoreSnapshot(root))
    }

    private fun verifyStatusAndBuildStore(jsonText: String): VerifyStatusResult {
        val root = JSONObject(jsonText)

        val schemaVersion = root.optInt("schemaVersion", 0)
        if (schemaVersion < 3) {
            return VerifyStatusResult.FailMessage("Aplikacija očekuje schemaVersion 3+. (dobijeno: $schemaVersion)")
        }

        val userObj = root.optJSONObject("user")
            ?: return VerifyStatusResult.FailMessage("Greška: user podaci ne postoje u JSON-u.")

        val userStatus = userObj.optString("status", "inactive").trim().lowercase()
        if (userStatus == "blocked") return VerifyStatusResult.FailMessage("Nalog je blokiran, kontaktirajte podršku.")
        if (userStatus != "active") return VerifyStatusResult.FailMessage("Nalog nije aktivan.")

        return VerifyStatusResult.Success(buildStoreSnapshot(root))
    }

    private fun buildStoreSnapshot(root: JSONObject): StoreSnapshot {
        val schemaVersion = root.optInt("schemaVersion", 0)

        val userObj = root.optJSONObject("user") ?: JSONObject()
        val user = LoyaltyStore.UserInfo(
            id = userObj.optString("id", ""),
            appId = userObj.optString("appId", ""),
            email = userObj.optString("email", ""),
            displayName = userObj.optString("displayName", ""),
            status = userObj.optString("status", "inactive"),
            memberNo = userObj.optInt("memberNo").let { if (it == 0) null else it },
            memberNoText = userObj.optString("memberNoText", null),
            fileName = userObj.optString("fileName", ""),
            memberValidFrom = userObj.optString("memberValidFrom", null),
            memberValidTo = userObj.optString("memberValidTo", null),
            loyaltyPercent = userObj.optDouble("loyaltyPercent", 0.0)
        )

        val loyalty = root.optJSONObject("loyalty") ?: JSONObject()
        val lastRefresh = loyalty.optString("generatedAt", "—")
        val currency = loyalty.optString("currency", "RSD")
        val currentPercent = loyalty.optDouble("currentPercent", 0.0)

        var currentQuarter = parseQuarterSnapshot(loyalty.optJSONObject("currentQuarter"))
        var previousQuarter = parseQuarterSnapshot(loyalty.optJSONObject("previousQuarter"))

// ✅ 1) pogledaj finalQuarter blok (ako postoji)
        val finalObj = loyalty.optJSONObject("finalQuarter")
        if (finalObj != null) {
            val fy = finalObj.optInt("year", 0)
            val fq = finalObj.optInt("quarter", 0)
            val fAt = finalObj.optString("finalizedAt", null)

            if (fy > 0 && fq in 1..4 && !fAt.isNullOrBlank()) {
                // ✅ 2) odluči da li ide u current ili previous
                if (currentQuarter.year == fy && currentQuarter.quarter == fq) {
                    currentQuarter = currentQuarter.copy(finalized = true, finalizedAt = fAt)
                } else if (previousQuarter.year == fy && previousQuarter.quarter == fq) {
                    previousQuarter = previousQuarter.copy(finalized = true, finalizedAt = fAt)
                }
                // ✅ 3) ako ne matchuje ni jedan -> “izlazi iz okvira”
                // ne diramo toggle snapshot-e; to ostaje za istoriju/potencijal kvartale
            }
        }


        val totalsObj = loyalty.optJSONObject("totals") ?: JSONObject()
        val totals = LoyaltyStore.Totals(
            paid = totalsObj.optDouble("paid", 0.0),
            pending = totalsObj.optDouble("pending", 0.0),
            rejected = totalsObj.optDouble("rejected", 0.0),
            all = totalsObj.optDouble("all", 0.0),
            earnedFinal = totalsObj.optDouble("earnedFinal", 0.0),
            earnedPotential = totalsObj.optDouble("earnedPotential", 0.0),
            balance = totalsObj.optDouble("balance", 0.0)
        )

        val payoutsArr = loyalty.optJSONArray("payouts")
        val payouts = mutableListOf<LoyaltyStore.Payout>()
        if (payoutsArr != null) {
            for (i in 0 until payoutsArr.length()) {
                val o = payoutsArr.optJSONObject(i) ?: continue
                val date = o.optString("date", "")
                if (date.isBlank()) continue

                payouts.add(
                    LoyaltyStore.Payout(
                        id = o.optString("id", null),
                        date = date,
                        amount = o.optDouble("amount", 0.0),
                        status = o.optString("status", "PAID"),
                        note = o.optString("note", null),
                        createdAt = o.optString("createdAt", null),
                        createdBy = o.optString("createdBy", null)
                    )
                )
            }
        }

        val quartersArr = loyalty.optJSONArray("quarters")
        val quarters = mutableListOf<LoyaltyStore.QuarterHistory>()
        if (quartersArr != null) {
            for (i in 0 until quartersArr.length()) {
                val o = quartersArr.optJSONObject(i) ?: continue
                val year = o.optInt("year", 0)
                val q = o.optInt("quarter", 0)
                if (year <= 0 || q <= 0) continue

                quarters.add(
                    LoyaltyStore.QuarterHistory(
                        year = year,
                        quarter = q,
                        memberNoText = o.optString("memberNoText", ""),
                        percent = o.optDouble("percent", 0.0),
                        potentialRsd = o.optDouble("potentialRsd", 0.0),
                        potentialPoints = o.optDouble("potentialPoints", 0.0),
                        finalRsd = o.optDouble("finalRsd").let { if (it == 0.0) null else it },
                        finalPoints = o.optDouble("finalPoints").let { if (it == 0.0) null else it },
                        finalizedAt = o.optString("finalizedAt", null)
                    )
                )
            }
        }

        return StoreSnapshot(
            schemaVersion = schemaVersion,
            lastRefresh = lastRefresh,
            currency = currency,
            currentPercent = currentPercent,
            user = user,
            currentQuarter = currentQuarter,
            previousQuarter = previousQuarter,
            totals = totals,
            payouts = payouts.sortedByDescending { it.date },
            quarters = quarters.sortedWith(
                compareByDescending<LoyaltyStore.QuarterHistory> { it.year }
                    .thenByDescending { it.quarter }
            )
        )
    }

    /**
     * ✅ FIX: parsiramo i purchaseDetails (dobar blok) + returnsDetails (povrati)
     */
    private fun parseQuarterSnapshot(o: JSONObject?): LoyaltyStore.QuarterSnapshot {
        if (o == null) return LoyaltyStore.QuarterSnapshot()

        // returnsDetails (povrati)
        val rdObj = o.optJSONObject("returnsDetails") ?: JSONObject()
        val rd = LoyaltyStore.ReturnsDetails(
            kasaRsd = rdObj.optDouble("kasaRsd", 0.0),
            otRsd = rdObj.optDouble("otRsd", 0.0),
            deletedOtRsd = rdObj.optDouble("deletedOtRsd", 0.0),   // ✅
            pairedKasaRsd = rdObj.optDouble("pairedKasaRsd", 0.0), // ✅
            eligibleRsd = rdObj.optDouble("eligibleRsd", 0.0),
            blacklistRsd = rdObj.optDouble("blacklistRsd", 0.0),
            markerARsd = rdObj.optDouble("markerARsd", 0.0),
            otherIneligibleRsd = rdObj.optDouble("otherIneligibleRsd", 0.0),
            allRsd = rdObj.optDouble("allRsd", 0.0),
            eligibleNetRsd = rdObj.optDouble("eligibleNetRsd", 0.0)
        )

        // ✅ purchaseDetails (kupovine - tvoj blok 431k / 54k / 458k / 11375 / 16890 ...)
        val pdObj = o.optJSONObject("purchaseDetails") ?: JSONObject()
        val pd = LoyaltyStore.PurchaseDetails(
            kasaRsd = pdObj.optDouble("kasaRsd", 0.0),
            otRsd = pdObj.optDouble("otRsd", 0.0),
            deletedOtRsd = pdObj.optDouble("deletedOtRsd", 0.0),   // ✅
            pairedKasaRsd = pdObj.optDouble("pairedKasaRsd", 0.0),            eligibleRsd = pdObj.optDouble("eligibleRsd", 0.0),
            blacklistRsd = pdObj.optDouble("blacklistRsd", 0.0),
            markerARsd = pdObj.optDouble("markerARsd", 0.0),
            otherIneligibleRsd = pdObj.optDouble("otherIneligibleRsd", 0.0),
            allRsd = pdObj.optDouble("allRsd", 0.0),
            eligibleNetRsd = pdObj.optDouble("eligibleNetRsd", 0.0)
        )

        return LoyaltyStore.QuarterSnapshot(
            year = o.optInt("year", 0),
            quarter = o.optInt("quarter", 0),
            from = o.optString("from", ""),
            to = o.optString("to", ""),
            returnsTo = o.optString("returnsTo", ""),
            percent = o.optDouble("percent", 0.0),

            purchasedRsd = o.optDouble("purchasedRsd", 0.0),
            returnedRsd = o.optDouble("returnedRsd", 0.0),
            pointsPurchased = o.optDouble("pointsPurchased", 0.0),
            pointsReturned = o.optDouble("pointsReturned", 0.0),

            membershipFeeRsd = o.optDouble("membershipFeeRsd", 0.0),
            membershipFeeTier = o.optString("membershipFeeTier", ""),
            pointsNet = o.optDouble("pointsNet", 0.0),
            pointsAfterFee = o.optDouble("pointsAfterFee", 0.0),

            // ✅ upisujemo oba:
            purchaseDetails = pd,
                returnsDetails = rd,

            finalized = o.optBoolean("finalized", false),
            finalizedAt = o.optString("finalizedAt", null)
        )
    }

    private fun ensureUsersSourceSelected(): Boolean {
        val effectiveFolder = SettingsPrefs.getPendingUsersFolder(this)
            .ifBlank { SettingsPrefs.getSelectedUsersFolder(this) }

        return effectiveFolder.isNotBlank()
    }
    private fun applyStore(store: StoreSnapshot) {
        LoyaltyStore.schemaVersion = store.schemaVersion
        LoyaltyStore.lastRefresh = store.lastRefresh
        LoyaltyStore.currency = store.currency
        LoyaltyStore.currentPercent = store.currentPercent
        LoyaltyStore.user = store.user
        LoyaltyStore.currentQuarter = store.currentQuarter
        LoyaltyStore.previousQuarter = store.previousQuarter
        LoyaltyStore.totals = store.totals
        LoyaltyStore.payouts = store.payouts
        LoyaltyStore.quarters = store.quarters
    }

    private fun saveCache(email: String, jsonText: String) {
        val cachedAt = try {
            JSONObject(jsonText).optJSONObject("loyalty")
                ?.optString("generatedAt", "")
                ?.ifBlank { "" }
                ?: ""
        } catch (_: Exception) { "" }

        val at = if (cachedAt.isNotBlank()) cachedAt else OffsetDateTime.now().toString()
        UserJsonCache.save(this, email, jsonText, at)
    }
    private fun refreshSelectedCompanyUi() {
        val pendingLabel = SettingsPrefs.getPendingUsersLabel(this)
        val selectedLabel = SettingsPrefs.getSelectedUsersLabel(this)

        b.tvSelectedCompanyValue.text = when {
            pendingLabel.isNotBlank() -> "Firma: $pendingLabel"
            selectedLabel.isNotBlank() -> "Firma: $selectedLabel"
            else -> "Firma: nije izabrana"
        }
    }
    private fun refreshCompanyPickUi() {
        val effectiveFolder = SettingsPrefs.getPendingUsersFolder(this)
            .ifBlank { SettingsPrefs.getSelectedUsersFolder(this) }

        b.btnPickCompany.visibility = if (effectiveFolder.isBlank()) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }
}
