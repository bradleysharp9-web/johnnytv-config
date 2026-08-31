package com.johnnytv.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    private lateinit var logo: ImageView
    private lateinit var noticeLabel: TextView
    private lateinit var serverInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var signInButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var statusLabel: TextView

    /** Resolved at launch: config.json, then the manual override, then the cached value. */
    private var resolvedServer: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_login)

        logo = findViewById(R.id.logo)
        noticeLabel = findViewById(R.id.noticeLabel)
        serverInput = findViewById(R.id.serverInput)
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        signInButton = findViewById(R.id.signInButton)
        progress = findViewById(R.id.loginProgress)
        statusLabel = findViewById(R.id.loginStatus)

        signInButton.setOnClickListener { attemptSignIn() }

        // Hidden support escape hatch: long-press the logo to type a server by hand.
        logo.setOnLongClickListener {
            serverInput.visibility = View.VISIBLE
            serverInput.setText(resolvedServer)
            statusLabel.text = getString(R.string.support_mode)
            true
        }

        loadConfigThenContinue()
    }

    private fun loadConfigThenContinue() {
        setBusy(true)
        statusLabel.text = getString(R.string.connecting)

        lifecycleScope.launch {
            val remote = withContext(Dispatchers.IO) {
                RemoteConfigLoader.fetch(Config.CONFIG_URL)
            }

            // A manually entered server always wins, so support can fix a broken config.
            resolvedServer = when {
                prefs.manualServer.isNotBlank() -> prefs.manualServer
                remote != null && remote.server.isNotBlank() -> remote.server
                prefs.server.isNotBlank() -> prefs.server
                Config.DEFAULT_SERVER.isNotBlank() -> Config.DEFAULT_SERVER
                else -> ""
            }

            if (remote != null && remote.notice.isNotBlank()) {
                noticeLabel.text = remote.notice
                noticeLabel.visibility = View.VISIBLE
            }

            if (remote != null && isUpdateAvailable(remote)) {
                setBusy(false)
                showUpdateDialog(remote)
                if (remote.forceUpdate) return@launch
            }

            setBusy(false)
            statusLabel.text = ""

            if (resolvedServer.isBlank()) {
                // Nothing to connect to - let the user type one rather than dead-ending.
                serverInput.visibility = View.VISIBLE
                statusLabel.text = getString(R.string.no_server)
                return@launch
            }

            // Remember it so the app still works if config.json is ever unreachable.
            prefs.server = resolvedServer

            when {
                Config.PRESET_USERNAME.isNotBlank() && Config.PRESET_PASSWORD.isNotBlank() -> {
                    usernameInput.setText(Config.PRESET_USERNAME)
                    passwordInput.setText(Config.PRESET_PASSWORD)
                    attemptSignIn()
                }
                prefs.isLoggedIn -> goToMain()
            }
        }
    }

    private fun attemptSignIn() {
        val typedServer = serverInput.text.toString().trim()
        val server = if (serverInput.visibility == View.VISIBLE && typedServer.isNotBlank()) {
            typedServer
        } else {
            resolvedServer
        }
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        if (server.isBlank()) {
            statusLabel.text = getString(R.string.no_server)
            return
        }
        if (username.isBlank() || password.isBlank()) {
            statusLabel.text = getString(R.string.fill_all_fields)
            return
        }

        setBusy(true)
        statusLabel.text = getString(R.string.signing_in)

        lifecycleScope.launch {
            val client = XtreamClient(server, username, password)
            val result = runCatching {
                withContext(Dispatchers.IO) { client.login() }
            }
            setBusy(false)
            result.onSuccess { accountStatus ->
                if (serverInput.visibility == View.VISIBLE && typedServer.isNotBlank()) {
                    prefs.manualServer = client.server
                }
                prefs.saveCredentials(client.server, username, password)
                statusLabel.text = accountStatus
                goToMain()
            }.onFailure { error ->
                statusLabel.text = error.message ?: "Could not sign in."
            }
        }
    }

    private fun isUpdateAvailable(remote: RemoteConfig): Boolean {
        if (remote.latestVersionCode <= 0L || remote.downloadUrl.isBlank()) return false
        return remote.latestVersionCode > currentVersionCode()
    }

    private fun currentVersionCode(): Long = try {
        PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))
    } catch (e: Exception) {
        0L
    }

    private fun showUpdateDialog(remote: RemoteConfig) {
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.update_title)
            .setMessage(R.string.update_message)
            .setCancelable(!remote.forceUpdate)
            .setPositiveButton(R.string.update_now) { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(remote.downloadUrl)))
                } catch (e: Exception) {
                    statusLabel.text = "Could not open the download link."
                }
            }
        if (!remote.forceUpdate) {
            builder.setNegativeButton(R.string.update_later, null)
        }
        builder.show()
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        signInButton.isEnabled = !busy
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
