package com.smartbill.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smartbill.app.data.Transaction
import com.smartbill.app.databinding.ItemTransactionBinding
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(
    private val onClick: (Transaction) -> Unit,
    private val onLongClick: (Transaction) -> Unit
) : ListAdapter<Transaction, TransactionAdapter.TransactionViewHolder>(TransactionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TransactionViewHolder(binding, onClick, onLongClick)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TransactionViewHolder(
        private val binding: ItemTransactionBinding,
        private val onClick: (Transaction) -> Unit,
        private val onLongClick: (Transaction) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

        fun bind(transaction: Transaction) {
            binding.apply {
                tvAppName.text = transaction.appName
                tvAmount.text = String.format("¥%.2f", transaction.amount)
                tvPaymentMethod.text = transaction.paymentMethod
                tvMerchant.text = if (transaction.merchantName.isNotEmpty()) {
                    transaction.merchantName
                } else {
                    ""
                }
                tvTime.text = dateFormat.format(Date(transaction.timestamp))
                tvCategory.text = transaction.category

                root.setOnClickListener { onClick(transaction) }
                root.setOnLongClickListener {
                    onLongClick(transaction)
                    true
                }
            }
        }
    }

    class TransactionDiffCallback : DiffUtil.ItemCallback<Transaction>() {
        override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
            return oldItem == newItem
        }
    }
}
