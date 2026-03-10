package com.sanraksha.sosapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sanraksha.sosapp.R
import com.sanraksha.sosapp.database.Contact

class ContactsAdapter(
    private val onEditClick: (Contact) -> Unit,
    private val onDeleteClick: (Contact) -> Unit
) : ListAdapter<Contact, ContactsAdapter.ContactViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = getItem(position)
        holder.bind(contact)
    }

    fun getContactAt(position: Int): Contact = getItem(position)

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAvatar: TextView = itemView.findViewById(R.id.tvContactAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvContactName)
        private val tvPhone: TextView = itemView.findViewById(R.id.tvContactPhone)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditContact)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteContact)

        fun bind(contact: Contact) {
            tvAvatar.text = contact.name.firstOrNull()?.uppercase() ?: "?"
            tvName.text = contact.name
            tvPhone.text = contact.phone
            btnEdit.setOnClickListener { onEditClick(contact) }
            btnDelete.setOnClickListener { onDeleteClick(contact) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Contact>() {
            override fun areItemsTheSame(oldItem: Contact, newItem: Contact): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Contact, newItem: Contact): Boolean {
                return oldItem == newItem
            }
        }
    }
}
