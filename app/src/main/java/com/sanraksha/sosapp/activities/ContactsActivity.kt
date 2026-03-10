package com.sanraksha.sosapp.activities

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.sanraksha.sosapp.R
import com.sanraksha.sosapp.adapters.ContactsAdapter
import com.sanraksha.sosapp.database.AppDatabase
import com.sanraksha.sosapp.database.Contact
import com.sanraksha.sosapp.utils.PrefManager
import com.sanraksha.sosapp.utils.ThemeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddContact: Button
    private lateinit var tvEmptyState: TextView
    private lateinit var bottomNav: BottomNavigationView

    private lateinit var adapter: ContactsAdapter
    private lateinit var database: AppDatabase
    private lateinit var prefManager: PrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        database = AppDatabase.getDatabase(this)
        prefManager = PrefManager(this)

        initViews()
        setupRecyclerView()
        setupListeners()
        loadContacts()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewContacts)
        btnAddContact = findViewById(R.id.btnAddContact)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        bottomNav = findViewById(R.id.bottomNavigation)

        bottomNav.selectedItemId = R.id.nav_contacts
    }

    private fun setupRecyclerView() {
        adapter = ContactsAdapter(
            onEditClick = { contact -> showEditContactDialog(contact) },
            onDeleteClick = { contact -> deleteContact(contact) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)

        // Swipe to delete
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val contact = adapter.getContactAt(position)
                deleteContact(contact)
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun setupListeners() {
        btnAddContact.setOnClickListener {
            showAddContactDialog()
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_contacts -> true
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            try {
                val userId = prefManager.userId ?: return@launch
                val contacts = withContext(Dispatchers.IO) {
                    database.contactDao().getContactsByUserId(userId)
                }

                adapter.submitList(contacts)

                if (contacts.isEmpty()) {
                    tvEmptyState.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvEmptyState.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Toast.makeText(this@ContactsActivity, "Error loading contacts", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddContactDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null)
        val etName = dialogView.findViewById<EditText>(R.id.etContactName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etContactPhone)

        AlertDialog.Builder(this)
            .setTitle("Add Emergency Contact")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()

                if (name.isEmpty() || phone.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (phone.length != 10) {
                    Toast.makeText(this, "Phone must be 10 digits", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                addContact(name, phone)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditContactDialog(contact: Contact) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null)
        val etName = dialogView.findViewById<EditText>(R.id.etContactName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etContactPhone)

        etName.setText(contact.name)
        etPhone.setText(contact.phone)

        AlertDialog.Builder(this)
            .setTitle("Edit Contact")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()

                if (name.isNotEmpty() && phone.length == 10) {
                    updateContact(contact.copy(name = name, phone = phone))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addContact(name: String, phone: String) {
        lifecycleScope.launch {
            try {
                val userId = prefManager.userId ?: return@launch
                val contact = Contact(
                    userId = userId,
                    name = name,
                    phone = phone
                )

                withContext(Dispatchers.IO) {
                    database.contactDao().insert(contact)
                }

                Toast.makeText(this@ContactsActivity, "Contact added", Toast.LENGTH_SHORT).show()
                loadContacts()
            } catch (e: Exception) {
                Toast.makeText(this@ContactsActivity, "Error adding contact", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateContact(contact: Contact) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.contactDao().update(contact)
                }
                Toast.makeText(this@ContactsActivity, "Contact updated", Toast.LENGTH_SHORT).show()
                loadContacts()
            } catch (e: Exception) {
                Toast.makeText(this@ContactsActivity, "Error updating contact", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteContact(contact: Contact) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.contactDao().delete(contact)
                }
                Toast.makeText(this@ContactsActivity, "Contact deleted", Toast.LENGTH_SHORT).show()
                loadContacts()
            } catch (e: Exception) {
                Toast.makeText(this@ContactsActivity, "Error deleting contact", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
