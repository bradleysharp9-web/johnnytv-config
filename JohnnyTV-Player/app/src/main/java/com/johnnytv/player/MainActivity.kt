package com.johnnytv.player

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var client: XtreamClient

    private lateinit var categoryList: RecyclerView
    private lateinit var streamList: RecyclerView
    private lateinit var searchInput: EditText
    private lateinit var tabLive: TextView
    private lateinit var tabMovies: TextView
    private lateinit var signOutButton: TextView
    private lateinit var emptyLabel: TextView
    private lateinit var progress: ProgressBar

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var streamAdapter: StreamAdapter

    private var showingLive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        if (!prefs.isLoggedIn) {
            signOut()
            return
        }

        setContentView(R.layout.activity_main)
        client = XtreamClient(prefs.server, prefs.username, prefs.password)

        categoryList = findViewById(R.id.categoryList)
        streamList = findViewById(R.id.streamList)
        searchInput = findViewById(R.id.searchInput)
        tabLive = findViewById(R.id.tabLive)
        tabMovies = findViewById(R.id.tabMovies)
        signOutButton = findViewById(R.id.signOutButton)
        emptyLabel = findViewById(R.id.emptyLabel)
        progress = findViewById(R.id.mainProgress)

        categoryAdapter = CategoryAdapter { category -> loadStreams(category) }
        streamAdapter = StreamAdapter { item -> play(item) }

        categoryList.layoutManager = LinearLayoutManager(this)
        categoryList.adapter = categoryAdapter
        streamList.layoutManager = LinearLayoutManager(this)
        streamList.adapter = streamAdapter
        streamList.setHasFixedSize(true)

        tabLive.setOnClickListener { setMode(live = true) }
        tabMovies.setOnClickListener { setMode(live = false) }
        signOutButton.setOnClickListener { signOut() }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                streamAdapter.filter(s?.toString() ?: "")
                updateEmptyState(loading = false)
            }
        })

        setMode(live = true)
    }

    private fun setMode(live: Boolean) {
        showingLive = live
        tabLive.isActivated = live
        tabMovies.isActivated = !live
        searchInput.setText("")
        streamAdapter.submit(emptyList())
        loadCategories()
    }

    private fun loadCategories() {
        setLoading(true)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (showingLive) client.liveCategories() else client.vodCategories()
                }
            }
            result.onSuccess { categories ->
                categoryAdapter.submit(categories)
                if (categories.isNotEmpty()) {
                    categoryAdapter.selectAt(0)
                    loadStreams(categories[0])
                } else {
                    setLoading(false)
                }
            }.onFailure { error ->
                setLoading(false)
                toast(error.message ?: "Could not load categories.")
            }
        }
    }

    private fun loadStreams(category: Category) {
        setLoading(true)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (showingLive) client.liveStreams(category.id)
                    else client.vodStreams(category.id)
                }
            }
            setLoading(false)
            result.onSuccess { items ->
                streamAdapter.submit(items)
                streamAdapter.filter(searchInput.text.toString())
                streamList.scrollToPosition(0)
                updateEmptyState(loading = false)
            }.onFailure { error ->
                toast(error.message ?: "Could not load that category.")
            }
        }
    }

    private fun play(item: StreamItem) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(client.playbackUrls(item)))
            putExtra(PlayerActivity.EXTRA_TITLE, item.name)
            putExtra(PlayerActivity.EXTRA_LIVE, item.isLive)
        }
        startActivity(intent)
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        updateEmptyState(loading)
    }

    private fun updateEmptyState(loading: Boolean) {
        val showEmpty = !loading && streamAdapter.isEmpty()
        emptyLabel.visibility = if (showEmpty) View.VISIBLE else View.GONE
    }

    private fun signOut() {
        prefs.clearCredentials()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
