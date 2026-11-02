package com.example.hclibrary

import android.content.Intent
import android.os.Bundle
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import android.view.ViewGroup

class MainActivity : AppCompatActivity() {
    data class Book(
        val title: String,
        val author: String,
        var available: Boolean,
        val rfidTag: String
    )

    companion object {
        // Temporary catalog data until a database is connected.
        val books = mutableListOf(
            Book("The Great Gatsby", "F. Scott Fitzgerald", true, "E0:04:01:00:1A:2B"),
            Book("To Kill a Mockingbird", "Harper Lee", true, "E0:04:01:00:3C:4D"),
            Book("The Hobbit", "J.R.R. Tolkien", false, "E0:04:01:00:5E:6F"),
            Book("Pride and Prejudice", "Jane Austen", true, "E0:04:01:00:7A:8B"),
            Book("1984", "George Orwell", true, "E0:04:01:00:9C:0D"),
            Book("The Alchemist", "Paulo Coelho", false, "E0:04:01:00:1E:2F")
        )
    }

    private val rfidScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val searchInput = findViewById<EditText>(R.id.search_input)
        if (result.resultCode == RESULT_OK) {
            val queryFromScanner = result.data?.getStringExtra(RfidScannerActivity.EXTRA_SEARCH_QUERY)
            if (!queryFromScanner.isNullOrEmpty()) {
                searchInput.setText(queryFromScanner)
                searchInput.setSelection(queryFromScanner.length)
                return@registerForActivityResult
            }
        }
        renderBooks(searchInput.text?.toString().orEmpty())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Open the RFID Scanner menu when the button is clicked.
        findViewById<Button>(R.id.open_rfid_scanner_button).setOnClickListener {
            val intent = Intent(this, RfidScannerActivity::class.java)
            rfidScannerLauncher.launch(intent)
        }

        // Set up the catalog and search input.
        val searchInput = findViewById<EditText>(R.id.search_input)
        renderBooks("")
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderBooks(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        val searchInput = findViewById<EditText>(R.id.search_input)
        renderBooks(searchInput?.text?.toString().orEmpty())
    }

    private fun renderBooks(query: String) {
        // Display books matching the search query.
        val list = findViewById<LinearLayout>(R.id.book_list)
        val emptyState = findViewById<TextView>(R.id.empty_state)
        list.removeAllViews()
        val normalized = query.trim().lowercase()
        val filtered = books.filter {
            "${it.title} ${it.author} ${it.rfidTag}".lowercase().contains(normalized)
        }

        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        filtered.forEach { book ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 18, 20, 18)
                background = GradientDrawable().apply {
                    setColor(ContextCompat.getColor(this@MainActivity, R.color.card_background))
                    cornerRadius = 20f
                    setStroke(1, ContextCompat.getColor(this@MainActivity, R.color.card_border))
                }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 14) }
            card.layoutParams = params

            val title = TextView(this).apply {
                text = book.title
                textSize = 18f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.card_title))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val author = TextView(this).apply {
                text = "${book.author} • RFID: ${book.rfidTag}"
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.muted_text))
                setPadding(0, 6, 0, 14)
            }
            val bottomRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val status = TextView(this).apply {
                text = if (book.available) "● Available" else "● Checked out"
                textSize = 13f
                setTextColor(ContextCompat.getColor(
                    this@MainActivity,
                    if (book.available) R.color.available_green else R.color.checked_out_orange
                ))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val button = Button(this).apply {
                text = if (book.available) "Check Out" else "Check In"
                setOnClickListener {
                    // Toggle the selected book between checked out and checked in.
                    book.available = !book.available
                    renderBooks(query)
                }
            }
            bottomRow.addView(status)
            bottomRow.addView(button)
            card.addView(title)
            card.addView(author)
            card.addView(bottomRow)
            list.addView(card)
        }
    }
}
