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

class DashboardActivity : AppCompatActivity() {

    private lateinit var b: ActivityDashboardBinding
    private val dfMoney = DecimalFormat("#,##0")
    private val dfPoints = DecimalFormat("#,##0.00")

    private lateinit var payoutsAdapter: PayoutsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        b = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(b.root)

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

    private fun bindQuarter(isCurrent: Boolean) {
        val q = if (isCurrent) LoyaltyStore.currentQuarter else LoyaltyStore.previousQuarter

        // ✅ Current: globalni balans (za isplatu "do sada")
        // ✅ Previous: vrednost iz prethodnog kvartala (npr. pointsAfterFee)
        val payoutAmount = if (isCurrent) {
            LoyaltyStore.totals.balance
        } else {
            q.pointsAfterFee
        }

        b.tvCurrentBonusValue.text = "${dfPoints.format(payoutAmount)} ${LoyaltyStore.currency}"
        b.tvCurrentBonusLabel.text = if (isCurrent) "ZA ISPLATU" else "UKUPNO (prethodni)"
        b.tvCurrentBonusHint.text =
            if (isCurrent) {
                "Procena za trenutni kvartal. Iznos se može promeniti do završetka obračuna."
            } else {
                "Pregled prethodnog kvartala."
            }

        bindSpecification(q, payoutAmount)
    }

    private fun bindSpecification(q: LoyaltyStore.QuarterSnapshot, payoutAmount: Double) {

        fun setRow(row: RowSpecItemBinding, label: String, value: Double) {
            row.tvLabel.text = label
            row.tvValue.text = "${dfMoney.format(value)} ${LoyaltyStore.currency}"
            row.root.visibility = android.view.View.VISIBLE
        }

        val pd = q.purchaseDetails   // ✅ KUPovine
        val rd = q.returnsDetails    // ✅ POVRATI

        // ✅ NETO povrati (da ne duplira):
        // - kasa: bruto - pairedKasaRsd
        // - otpremnica: bruto - deletedOtRsd
        val vraceniKasa = (rd.kasaRsd - rd.pairedKasaRsd).coerceAtLeast(0.0)
        val vraceniOtpremnica = rd.otRsd // ✅ prikaži bruto kao ranije

        // 1) Ukupno kupljeno (tačno iz purchaseDetails)
        val ukupnoKupljeno = if (pd.allRsd > 0.0) pd.allRsd else q.purchasedRsd

        // 2-3) Ne podleže loyalty (A / black list) -> iz purchaseDetails
        val nePodlezeA = pd.markerARsd
        val nePodlezeBlacklist = pd.blacklistRsd

        // 6) Ukupna vrednost vraćene robe
        val ukupnoVraceno = if (rd.allRsd > 0.0) rd.allRsd else q.returnedRsd

        // 7) Osnovica (tačno iz purchaseDetails, ne iz pointsNet/percent)
        val osnovicaZaLoyalty =
            if (pd.eligibleNetRsd > 0.0) pd.eligibleNetRsd
            else if (pd.eligibleRsd > 0.0) pd.eligibleRsd
            else 0.0

        // 8) Članarina
        val clanarina = q.membershipFeeRsd

        // 9) Za isplatu
        val zaIsplatu = payoutAmount

        setRow(b.specRow0, "Ukupno kupljeno", ukupnoKupljeno)
        setRow(b.specRow1, "Ne podleže loyalty programu (A)", nePodlezeA)
        setRow(b.specRow2, "Ne podleže loyalty programu (black list)", nePodlezeBlacklist)
        setRow(b.specRow3, "Vraćeni artikli (kasa)", vraceniKasa)
        setRow(b.specRow4, "Vraćeni artikli (otpremnica)", vraceniOtpremnica)
        setRow(b.specRow5, "Ukupna vrednost vraćene robe", ukupnoVraceno)
        setRow(b.specRow6, "Ukupna suma na koju se primenjuje loyalty", osnovicaZaLoyalty)
        setRow(b.specRow7, "Članarina", clanarina)
        setRow(b.specRow8, "Za isplatu", zaIsplatu)
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
