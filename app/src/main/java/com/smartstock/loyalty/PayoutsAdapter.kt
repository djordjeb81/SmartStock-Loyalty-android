package com.smartstock.loyalty

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.smartstock.loyalty.databinding.ItemPayoutBinding
import java.text.DecimalFormat

class PayoutsAdapter(private val df: DecimalFormat) : RecyclerView.Adapter<PayoutsAdapter.VH>() {

    private val items = mutableListOf<LoyaltyStore.Payout>()

    fun submit(newItems: List<LoyaltyStore.Payout>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemPayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b, df)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class VH(private val b: ItemPayoutBinding, private val df: DecimalFormat) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(p: LoyaltyStore.Payout) {
            b.tvDate.text = p.date
            b.tvAmount.text = "${df.format(p.amount)} ${LoyaltyStore.currency}"
            b.tvStatus.text = statusToSr(p.status)

            val note = p.note?.trim().orEmpty()
            if (note.isBlank()) {
                b.tvNote.visibility = View.GONE
            } else {
                b.tvNote.visibility = View.VISIBLE
                b.tvNote.text = note
            }
        }

        private fun statusToSr(raw: String): String {
            return when (raw.trim().lowercase()) {
                "paid", "isplaceno", "isplaćeno", "completed", "done" -> "Isplaćeno"
                "pending", "u obradi", "processing" -> "U obradi"
                "rejected", "odbijeno", "failed" -> "Odbijeno"
                else -> raw
            }
        }
    }
}
