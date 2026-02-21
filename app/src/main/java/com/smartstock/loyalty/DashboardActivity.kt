// DashboardActivity.kt
package com.smartstock.loyalty

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.smartstock.loyalty.databinding.ActivityDashboardBinding
import com.smartstock.loyalty.databinding.RowSpecItemBinding
import java.text.DecimalFormat
import java.time.LocalDate

class DashboardActivity : AppCompatActivity() {

    private lateinit var b: ActivityDashboardBinding
    private val dfMoney = DecimalFormat("#,##0")
    private val dfPoints = DecimalFormat("#,##0.00")
    private val dfPercent = DecimalFormat("0.##")
    private fun fmtPercent(p: Double): String = "${dfPercent.format(p)}%"

    private lateinit var payoutsAdapter: PayoutsAdapter

    // ✅ sačuvaj default boje (tema/dark-mode friendly)
    private var defaultValueColor: Int = 0
    private var defaultLabelColor: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        b = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(b.root)

        // ✅ uhvati default boje pre nego što ih menjamo
        defaultValueColor = b.tvCurrentBonusValue.currentTextColor
        defaultLabelColor = b.tvCurrentBonusLabel.currentTextColor

        applySystemInsets()

        bindHeader()
        setupTabs()
        setupQuarterToggle()
        setupPayoutsList()

        showTab(0)
        bindQuarter(isCurrent = true)
        bindPayouts()

        b.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            b.root.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }
    }

    private fun bindHeader() {
        val u = LoyaltyStore.user
        b.tvName.text = u.displayName.ifBlank { "Član" }
        b.tvMemberNoBig.text = u.memberNoText?.takeIf { it.isNotBlank() } ?: "—"
    }

    private fun setupTabs() {
        b.tabMain.removeAllTabs()
        b.tabMain.addTab(b.tabMain.newTab().setText("Kvartali"))
        b.tabMain.addTab(b.tabMain.newTab().setText("Isplate"))

        b.tabMain.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showTab(index: Int) {
        b.viewQuarters.visibility = if (index == 0) android.view.View.VISIBLE else android.view.View.GONE
        b.viewPayouts.visibility = if (index == 1) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun setupQuarterToggle() {
        b.btnCurrent.setOnClickListener {
            setQuarterToggle(isCurrent = true)
            bindQuarter(isCurrent = true)
        }
        b.btnPrevious.setOnClickListener {
            setQuarterToggle(isCurrent = false)
            bindQuarter(isCurrent = false)
        }
        setQuarterToggle(isCurrent = true)
    }

    private fun setQuarterToggle(isCurrent: Boolean) {
        b.btnCurrent.isSelected = isCurrent
        b.btnPrevious.isSelected = !isCurrent
        b.btnCurrent.alpha = if (isCurrent) 1f else 0.6f
        b.btnPrevious.alpha = if (!isCurrent) 1f else 0.6f
    }

    // ---------------- Payout math (PAID sum by quarter) ----------------

    private fun quarterOf(d: LocalDate): Int = ((d.monthValue - 1) / 3) + 1

    private fun tryParseDate(s: String?): LocalDate? {
        if (s.isNullOrBlank()) return null
        return try { LocalDate.parse(s.trim()) } catch (_: Exception) { null }
    }

    private fun isFinal(q: LoyaltyStore.QuarterSnapshot): Boolean =
        q.finalized || !q.finalizedAt.isNullOrBlank()

    private fun formatFinalDate(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        return try {
            java.time.OffsetDateTime.parse(iso)
                .toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        } catch (_: Exception) {
            iso.take(10)
        }
    }

    /** SUM(PAID) samo za dati year/quarter (na osnovu payout.date) */
    private fun sumPaidForQuarter(year: Int, quarter: Int): Double {
        if (year <= 0 || quarter !in 1..4) return 0.0

        return LoyaltyStore.payouts
            .asSequence()
            .filter { (it.status ?: "").trim().equals("PAID", ignoreCase = true) }
            .mapNotNull { p ->
                val d = tryParseDate(p.date)
                if (d == null) null else Pair(d, p.amount)
            }
            .filter { (d, _) -> d.year == year && quarterOf(d) == quarter }
            .sumOf { it.second }
    }

    private fun bindQuarter(isCurrent: Boolean) {
        val q = if (isCurrent) LoyaltyStore.currentQuarter else LoyaltyStore.previousQuarter

        val y = q.year
        val qu = q.quarter

        // ✅ zbir PAID isplata za taj kvartal
        val paidSum = sumPaidForQuarter(y, qu)

        val final = isFinal(q)

        val shownPercent =
            if (q.percent > 0.0) q.percent
            else LoyaltyStore.currentPercent

        b.tvCurrentPercent.text =
            if (isCurrent) "Trenutni procenat: ${fmtPercent(shownPercent)}"
            else "Procenat: ${fmtPercent(shownPercent)}"

        // ✅ history zapis za isti kvartal (u "quarters" listi)
        val hist = LoyaltyStore.quarters.firstOrNull {
            it.year == q.year && it.quarter == q.quarter
        }

        // ✅ baza za isplatu:
        // - POTENCIJAL: snapshot pointsAfterFee (već umanjeno za članarinu)
        // - FINAL: finalPoints - članarina (jer finalPoints je "sirovo", članarina se skida ovde)
        val baseForPayout =
            if (final) {
                val fp = hist?.finalPoints ?: 0.0
                (fp - q.membershipFeeRsd).coerceAtLeast(0.0)
            } else {
                q.pointsAfterFee
            }

        // ✅ konačno za isplatu = baza - isplaćeno (PAID)
        val payoutAmount = (baseForPayout - paidSum).coerceAtLeast(0.0)

        b.tvCurrentBonusValue.text = "${dfPoints.format(payoutAmount)} ${LoyaltyStore.currency}"

        val finAt = formatFinalDate(q.finalizedAt)

        b.tvCurrentBonusLabel.text =
            if (isCurrent) {
                if (final) "KONAČNO ZA ISPLATU" else "ZA ISPLATU"
            } else {
                "UKUPNO (prethodni)"
            }

        b.tvCurrentBonusHint.text =
            if (final) {
                "FINAL (finalizovano: ${finAt ?: "—"}) • Iznos je umanjen za članarinu i sve isplate za ovaj kvartal."
            } else {
                "Iznos je umanjen za članarinu i sve isplate za ovaj kvartal."
            }

        // ✅ BOJA kada je FINAL (čuva default iz teme)
        b.tvCurrentBonusValue.setTextColor(
            if (final) getColor(android.R.color.holo_green_dark) else defaultValueColor
        )
        b.tvCurrentBonusLabel.setTextColor(
            if (final) getColor(android.R.color.holo_green_light) else defaultLabelColor
        )

        bindSpecification(q, payoutAmount, paidSum, final, hist)
    }

    private fun bindSpecification(
        q: LoyaltyStore.QuarterSnapshot,
        payoutAmount: Double,
        paidSum: Double,
        isFinal: Boolean,
        hist: LoyaltyStore.QuarterHistory?
    ) {

        fun setRow(row: RowSpecItemBinding, label: String, value: Double) {
            row.tvLabel.text = label
            row.tvValue.text = "${dfMoney.format(value)} ${LoyaltyStore.currency}"
            row.root.visibility = android.view.View.VISIBLE
        }

        fun setRowText(row: RowSpecItemBinding, label: String, value: String) {
            row.tvLabel.text = label
            row.tvValue.text = value
            row.root.visibility = android.view.View.VISIBLE
        }

        val pd = q.purchaseDetails   // ✅ KUPovine
        val rd = q.returnsDetails    // ✅ POVRATI

        // ✅ NETO povrati (da ne duplira):
        // - kasa: bruto - pairedKasaRsd
        // - otpremnica: bruto (da se vidi deleted OT trag)
        val vraceniKasa = (rd.kasaRsd - rd.pairedKasaRsd).coerceAtLeast(0.0)
        val vraceniOtpremnica = rd.otRsd

        // 1) Ukupno kupljeno
        val ukupnoKupljeno = if (pd.allRsd > 0.0) pd.allRsd else q.purchasedRsd

        // 2-3) Ne podleže loyalty
        val nePodlezeA = pd.markerARsd
        val nePodlezeBlacklist = pd.blacklistRsd

        // 7) Osnovica (kontrolno iz snapshot-a)
        val osnovicaZaLoyalty =
            if (pd.eligibleNetRsd > 0.0) pd.eligibleNetRsd
            else if (pd.eligibleRsd > 0.0) pd.eligibleRsd
            else 0.0

        // članarina
        val clanarina = q.membershipFeeRsd

        // ✅ (opc.) kada je FINAL, možeš prikazati "Konačno posle članarine" iz history finalPoints
        // Ako ne želiš, slobodno ignoriši - ovo ne menja izračun "Za isplatu".
        val finalAfterFeeShown =
            if (isFinal) {
                val fp = hist?.finalPoints ?: 0.0
                (fp - clanarina).coerceAtLeast(0.0)
            } else {
                0.0
            }

        // Za isplatu (krajnje)
        val zaIsplatu = payoutAmount

        val percentValue =
            if (q.percent > 0.0) q.percent
            else LoyaltyStore.currentPercent

        setRowText(b.specRow0, "Procenat", fmtPercent(percentValue))

        setRow(b.specRow1, "Ukupno kupljeno", ukupnoKupljeno)
        setRow(b.specRow2, "Ne podleže loyalty programu (A)", nePodlezeA)
        setRow(b.specRow3, "Ne podleže loyalty programu (black list)", nePodlezeBlacklist)
        setRow(b.specRow4, "Vraćeni artikli (kasa)", vraceniKasa)
        setRow(b.specRow5, "Vraćeni artikli (otpremnica)", vraceniOtpremnica)

        setRow(b.specRow6, "Isplaćeno ukupno (PAID)", paidSum)

        setRow(b.specRow7, "Ukupna suma na koju se primenjuje loyalty", osnovicaZaLoyalty)
        setRow(b.specRow8, "Članarina", clanarina)
        setRow(b.specRow9, "Za isplatu", zaIsplatu)
    }

    private fun setupPayoutsList() {
        payoutsAdapter = PayoutsAdapter(DecimalFormat("#,##0.00"))
        b.rvPayouts.layoutManager = LinearLayoutManager(this)
        b.rvPayouts.adapter = payoutsAdapter
    }

    private fun bindPayouts() {
        val all = LoyaltyStore.payouts
        if (all.isEmpty()) {
            b.tvNoPayouts.visibility = android.view.View.VISIBLE
            b.rvPayouts.visibility = android.view.View.GONE
        } else {
            b.tvNoPayouts.visibility = android.view.View.GONE
            b.rvPayouts.visibility = android.view.View.VISIBLE
            payoutsAdapter.submit(all)
        }
    }
}
