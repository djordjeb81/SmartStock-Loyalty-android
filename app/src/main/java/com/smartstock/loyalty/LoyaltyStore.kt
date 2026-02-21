// LoyaltyStore.kt
package com.smartstock.loyalty

/**
 * Globalni runtime store (u RAM-u).
 * Login učitava JSON i puni ovo, a ekrani samo čitaju.
 */
object LoyaltyStore {

    const val CURRENT_SCHEMA = 4

    data class UserInfo(
        val id: String = "",
        val appId: String = "",
        val email: String = "",
        val displayName: String = "",
        val status: String = "inactive",
        val memberNo: Int? = null,
        val memberNoText: String? = null,
        val fileName: String = "",
        val memberValidFrom: String? = null,
        val memberValidTo: String? = null,
        val loyaltyPercent: Double = 0.0
    )

    data class ReturnsDetails(
        val kasaRsd: Double = 0.0,
        val otRsd: Double = 0.0,
        val deletedOtRsd: Double = 0.0,     // ✅ novo
        val pairedKasaRsd: Double = 0.0,    // ✅ novo
        val eligibleRsd: Double = 0.0,
        val blacklistRsd: Double = 0.0,
        val markerARsd: Double = 0.0,
        val otherIneligibleRsd: Double = 0.0,
        val allRsd: Double = 0.0,
        val eligibleNetRsd: Double = 0.0
    )

    data class PurchaseDetails(
        val kasaRsd: Double = 0.0,
        val otRsd: Double = 0.0,
        val deletedOtRsd: Double = 0.0,     // ✅ novo
        val pairedKasaRsd: Double = 0.0,    // ✅ novo
        val eligibleRsd: Double = 0.0,
        val blacklistRsd: Double = 0.0,
        val markerARsd: Double = 0.0,
        val otherIneligibleRsd: Double = 0.0,
        val allRsd: Double = 0.0,
        val eligibleNetRsd: Double = 0.0
    )


    data class QuarterSnapshot(
        val year: Int = 0,
        val quarter: Int = 0,
        val from: String = "",
        val to: String = "",
        val returnsTo: String = "",
        val percent: Double = 0.0,

        val purchasedRsd: Double = 0.0,
        val returnedRsd: Double = 0.0,
        val pointsPurchased: Double = 0.0,
        val pointsReturned: Double = 0.0,

        val membershipFeeRsd: Double = 0.0,
        val membershipFeeTier: String = "",
        val pointsNet: Double = 0.0,
        val pointsAfterFee: Double = 0.0,

        val purchaseDetails: PurchaseDetails = PurchaseDetails(),
        val returnsDetails: ReturnsDetails = ReturnsDetails(),

// ✅ schema v4
        val finalized: Boolean = false,
        val finalizedAt: String? = null

    )

    data class Totals(
        val paid: Double = 0.0,
        val pending: Double = 0.0,
        val rejected: Double = 0.0,
        val all: Double = 0.0,
        val earnedFinal: Double = 0.0,
        val earnedPotential: Double = 0.0,
        val balance: Double = 0.0
    )

    data class Payout(
        val id: String? = null,
        val date: String,     // YYYY-MM-DD
        val amount: Double,
        val status: String = "PAID",
        val note: String? = null,
        val createdAt: String? = null,
        val createdBy: String? = null
    )

    data class QuarterHistory(
        val year: Int,
        val quarter: Int,
        val memberNoText: String = "",
        val percent: Double = 0.0,
        val potentialRsd: Double = 0.0,
        val potentialPoints: Double = 0.0,
        val finalRsd: Double? = null,
        val finalPoints: Double? = null,
        val finalizedAt: String? = null
    )

    // --- runtime data ---
    var schemaVersion: Int = 0
    var lastRefresh: String = "—"
    var currency: String = "RSD"
    var currentPercent: Double = 0.0

    var user: UserInfo = UserInfo()
    var currentQuarter: QuarterSnapshot = QuarterSnapshot()
    var previousQuarter: QuarterSnapshot = QuarterSnapshot()

    var totals: Totals = Totals()

    var payouts: List<Payout> = emptyList()
    var quarters: List<QuarterHistory> = emptyList()

    fun clear() {
        schemaVersion = 0
        lastRefresh = "—"
        currency = "RSD"
        currentPercent = 0.0

        user = UserInfo()
        currentQuarter = QuarterSnapshot()
        previousQuarter = QuarterSnapshot()
        totals = Totals()

        payouts = emptyList()
        quarters = emptyList()
    }
}
