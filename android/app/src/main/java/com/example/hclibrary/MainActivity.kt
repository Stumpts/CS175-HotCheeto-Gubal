package com.example.hclibrary

import android.content.Intent
import android.os.Bundle
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import android.view.ViewGroup
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {
    data class Book(
        val title: String,
        val author: String,
        var available: Boolean,
        val rfidTag: String,
        val isbn: String = "",
        val copyCount: Int = 1,
        val availableCount: Int = if (available) 1 else 0
    )

    companion object {
        // Catalog data populated from backend (with initial fallback items).
        val books = mutableListOf(
            Book("The Great Gatsby", "F. Scott Fitzgerald", true, "E0:04:01:00:1A:2B", "9780743273565"),
            Book("To Kill a Mockingbird", "Harper Lee", true, "E0:04:01:00:3C:4D", "9780061120084"),
            Book("The Hobbit", "J.R.R. Tolkien", false, "E0:04:01:00:5E:6F", "9780547928227"),
            Book("Pride and Prejudice", "Jane Austen", true, "E0:04:01:00:7A:8B", "9780141439518"),
            Book("1984", "George Orwell", true, "E0:04:01:00:9C:0D", "9780451524935"),
            Book("The Alchemist", "Paulo Coelho", false, "E0:04:01:00:1E:2F", "9780062315007")
        )
    }

    private var loggedInUser: String? = null
    private var loggedInRole: String? = null
    private val isEmployee: Boolean
        get() = loggedInRole.equals("EMPLOYEE", ignoreCase = true)

    private val searchCategories = listOf("Title", "Author", "ISBN")

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
        fetchBooksFromBackend(searchInput.text?.toString().orEmpty())
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

        // Set up the right-side navigation drawer and menu button.
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val navView = findViewById<NavigationView>(R.id.nav_view)
        updateNavigationMenuVisibility(navView)
        val menuButton = findViewById<ImageButton>(R.id.menu_button)

        menuButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_search -> {
                    showCatalogPage()
                    setActiveDrawerItem(navView, R.id.nav_search)
                }
                R.id.nav_login -> {
                    if (loggedInUser != null) {
                        onLogout()
                    } else {
                        findViewById<View>(R.id.catalog_container).visibility = View.GONE
                        findViewById<View>(R.id.fragment_container).visibility = View.VISIBLE
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, LoginFragment())
                            .commit()
                        setActiveDrawerItem(navView, R.id.nav_login)
                    }
                }
                R.id.nav_account -> {
                    if (loggedInUser == null) {
                        Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
                    } else {
                        findViewById<View>(R.id.catalog_container).visibility = View.GONE
                        findViewById<View>(R.id.fragment_container).visibility = View.VISIBLE
                        supportFragmentManager.beginTransaction()
                            .replace(
                                R.id.fragment_container,
                                AccountFragment.newInstance(loggedInUser!!, loggedInRole ?: "BORROWER")
                            )
                            .commit()
                        setActiveDrawerItem(navView, R.id.nav_account)
                    }
                }
                R.id.nav_employee_circulation -> {
                    if (!isEmployee || loggedInUser == null) {
                        Toast.makeText(this, "Employee privileges are required", Toast.LENGTH_SHORT).show()
                    } else {
                        openEmployeeCirculation(null)
                    }
                }
            }
            drawerLayout.closeDrawer(GravityCompat.END)
            true
        }

        // Open the RFID Scanner menu when the button is clicked.
        findViewById<Button>(R.id.open_rfid_scanner_button).setOnClickListener {
            val intent = Intent(this, RfidScannerActivity::class.java).apply {
                putExtra(RfidScannerActivity.EXTRA_IS_EMPLOYEE, isEmployee)
                putExtra(RfidScannerActivity.EXTRA_EMPLOYEE_USERNAME, loggedInUser)
            }
            rfidScannerLauncher.launch(intent)
        }

        // Set up the category dropdown spinner and search input.
        val searchInput = findViewById<EditText>(R.id.search_input)
        val categorySpinner = findViewById<Spinner>(R.id.search_category_spinner)

        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            searchCategories
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        categorySpinner.adapter = spinnerAdapter

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = searchCategories[position]
                searchInput.hint = "Search by ${selected.lowercase()}..."
                fetchBooksFromBackend(searchInput.text?.toString().orEmpty())
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                fetchBooksFromBackend(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        refreshCatalog()
    }

    fun refreshCatalog() {
        val searchInput = findViewById<EditText>(R.id.search_input)
        fetchBooksFromBackend(searchInput?.text?.toString().orEmpty())
    }

    @JvmOverloads
    fun onLoginSuccess(username: String, role: String = "BORROWER") {
        loggedInUser = username
        loggedInRole = role
        findViewById<TextView>(R.id.user_status_text).text =
            if (isEmployee) "$username (Employee)" else username
        val navView = findViewById<NavigationView>(R.id.nav_view)
        updateNavigationMenuVisibility(navView)
        navView.menu.findItem(R.id.nav_login)?.title = getString(R.string.menu_logout)
        setActiveDrawerItem(navView, R.id.nav_search)
        showCatalogPage()
    }

    private fun onLogout() {
        loggedInUser = null
        loggedInRole = null
        findViewById<TextView>(R.id.user_status_text).text = "Guest"
        val navView = findViewById<NavigationView>(R.id.nav_view)
        updateNavigationMenuVisibility(navView)
        navView.menu.findItem(R.id.nav_login)?.title = getString(R.string.menu_login)
        setActiveDrawerItem(navView, R.id.nav_search)
        showCatalogPage()
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
    }

    private fun updateNavigationMenuVisibility(navView: NavigationView) {
        navView.menu.findItem(R.id.nav_account)?.isVisible = loggedInUser != null
        navView.menu.findItem(R.id.nav_employee_section)?.isVisible = isEmployee
    }

    private fun setActiveDrawerItem(navView: NavigationView, activeItemId: Int) {
        val allIds = listOf(
            R.id.nav_account,
            R.id.nav_search,
            R.id.nav_employee_circulation,
            R.id.nav_login
        )
        allIds.forEach { id ->
            navView.menu.findItem(id)?.isChecked = (id == activeItemId)
        }
    }

    private fun openEmployeeCirculation(initialBarcode: String?) {
        val employee = loggedInUser ?: return
        findViewById<View>(R.id.catalog_container).visibility = View.GONE
        findViewById<View>(R.id.fragment_container).visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_container,
                EmployeeCirculationFragment.newInstance(employee, initialBarcode)
            )
            .commit()
        val navView = findViewById<NavigationView>(R.id.nav_view)
        setActiveDrawerItem(navView, R.id.nav_employee_circulation)
    }

    private fun showCatalogPage() {
        findViewById<View>(R.id.fragment_container).visibility = View.GONE
        findViewById<View>(R.id.catalog_container).visibility = View.VISIBLE
        supportFragmentManager.findFragmentById(R.id.fragment_container)?.let {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }
        refreshCatalog()
    }

    private fun getSelectedSearchType(): String {
        val spinner = findViewById<Spinner>(R.id.search_category_spinner)
        return (spinner?.selectedItem as? String)?.lowercase() ?: "title"
    }

    private fun fetchBooksFromBackend(query: String) {
        val type = getSelectedSearchType()
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
                val url = URL("${ApiConfig.getBaseUrl()}/api/books/search?type=$type&query=$encodedQuery")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                if (conn.responseCode in 200..299) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(response)
                    val fetchedBooks = mutableListOf<Book>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val copyCount = obj.optInt("copyCount", 1)
                        val availableCount = obj.optInt("availableCount", copyCount)
                        val isbn = obj.optString("isbn", "")
                        val barcodes = obj.optJSONArray("barcodes")
                        val primaryTag = if (barcodes != null && barcodes.length() > 0) {
                            barcodes.optString(0, isbn)
                        } else {
                            isbn
                        }
                        fetchedBooks.add(
                            Book(
                                title = obj.optString("title"),
                                author = obj.optString("author"),
                                available = availableCount > 0,
                                rfidTag = primaryTag,
                                isbn = isbn,
                                copyCount = copyCount,
                                availableCount = availableCount
                            )
                        )
                    }
                    runOnUiThread {
                        books.clear()
                        books.addAll(fetchedBooks)
                        renderBooks(fetchedBooks)
                    }
                } else {
                    runOnUiThread { renderLocalFallback(type, query) }
                }
            } catch (_: Exception) {
                runOnUiThread { renderLocalFallback(type, query) }
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    private fun renderLocalFallback(type: String, query: String) {
        val normalized = query.trim().lowercase()
        val filtered = if (normalized.isEmpty()) {
            books
        } else {
            books.filter { book ->
                when (type) {
                    "author" -> book.author.lowercase().contains(normalized)
                    "isbn" -> book.isbn.lowercase().contains(normalized) ||
                            book.rfidTag.lowercase().contains(normalized)
                    else -> book.title.lowercase().contains(normalized)
                }
            }
        }
        renderBooks(filtered)
    }

    private fun renderBooks(bookItems: List<Book>) {
        // Display books matching the search query.
        val list = findViewById<LinearLayout>(R.id.book_list)
        val emptyState = findViewById<TextView>(R.id.empty_state)
        list.removeAllViews()

        emptyState.visibility = if (bookItems.isEmpty()) View.VISIBLE else View.GONE
        bookItems.forEach { book ->
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
            val statusLabel = when {
                book.available && book.copyCount > 1 ->
                    "● Available (${book.availableCount}/${book.copyCount})"
                book.available -> "● Available"
                else -> "● Checked out"
            }
            val status = TextView(this).apply {
                text = statusLabel
                textSize = 13f
                setTextColor(ContextCompat.getColor(
                    this@MainActivity,
                    if (book.available) R.color.available_green else R.color.checked_out_orange
                ))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            bottomRow.addView(status)

            if (isEmployee) {
                val button = Button(this).apply {
                    text = if (book.available) "Check Out" else "Check In"
                    isAllCaps = false
                    setOnClickListener {
                        openEmployeeCirculation(book.rfidTag)
                    }
                }
                bottomRow.addView(button)
            }

            card.addView(title)
            card.addView(author)
            card.addView(bottomRow)
            list.addView(card)
        }
    }
}
