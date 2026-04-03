package com.smartstock.loyalty

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.smartstock.loyalty.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import android.widget.Toast
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding
    private var suppressSwitchCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge + safe area (sat/signal + donja traka)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        val base = (16 * resources.displayMetrics.density).toInt()
        val bottomExtra = (24 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            b.root.setPadding(
                base + sys.left,
                base + sys.top,
                base + sys.right,
                base + sys.bottom + bottomExtra
            )
            insets
        }

        b.btnBack.setOnClickListener { finish() }

        b.btnManualPdf.setOnClickListener {
            openManualPdf()
        }

        b.btnClearSavedEmail.setOnClickListener {
            SettingsPrefs.clearLastEmail(this)
            refreshUi()
            AlertDialog.Builder(this)
                .setTitle("Obrisano")
                .setMessage("Sačuvani email je obrisan.")
                .setPositiveButton("OK", null)
                .show()
        }

        b.swQuickUnlock.setOnCheckedChangeListener { _, isChecked ->
            if (suppressSwitchCallback) return@setOnCheckedChangeListener

            if (isChecked) {
                if (!PhoneUnlockAuth.isAvailable(this)) {
                    setSwitchSilently(false)
                    AlertDialog.Builder(this)
                        .setTitle("Nije dostupno")
                        .setMessage("Na ovom telefonu nije dostupno otključavanje (biometrija ili PIN/šablon telefona).")
                        .setPositiveButton("OK", null)
                        .show()
                    return@setOnCheckedChangeListener
                }

                val existingEmail = SettingsPrefs.getLastEmail(this)
                if (existingEmail.isBlank()) {
                    promptForEmailThenEnable()
                } else {
                    confirmBiometricThenEnable(existingEmail)
                }
            } else {
                SettingsPrefs.setQuickUnlockEnabled(this, false)
                SettingsPrefs.clearLastEmail(this)
                refreshUi()
            }
        }

        // Help buttons (3 fajla sa Dropboxa)
        b.btnHelpOpste.setOnClickListener { loadHelp("opste") }
        b.btnHelpClanarina.setOnClickListener { loadHelp("clanarina") }
        b.btnHelpIsplate.setOnClickListener { loadHelp("isplate") }

        // Default: opšte
        loadHelp("opste")

        // Odjava
        b.btnLogout.setOnClickListener { confirmLogout() }

        refreshUi()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Odjava")
            .setMessage("Da li želite da se odjavite i zatvorite aplikaciju?")
            .setPositiveButton("Da") { _, _ ->
                LoyaltyStore.clear()
                SettingsPrefs.setQuickUnlockEnabled(this, false)
                SettingsPrefs.clearLastEmail(this)
                UserJsonCache.clearAll(this)

                finishAffinity()
                android.os.Process.killProcess(android.os.Process.myPid())
            }
            .setNegativeButton("Ne", null)
            .show()
    }

    private fun loadHelp(docKey: String) {
        b.helpProgress.visibility = View.VISIBLE
        b.tvHelpBody.text = ""

        lifecycleScope.launch {
            val path = DropboxJsonClient.pathForHelpDoc(docKey)

            val text = try {
                withContext(Dispatchers.IO) { DropboxJsonClient.downloadTextByPath(path) }
            } catch (_: Exception) {
                null
            }

            b.helpProgress.visibility = View.GONE

            b.tvHelpBody.text = if (text == null) {
                "Uputstvo nije dostupno (proveri internet ili da li fajl postoji).\n\nPath: $path"
            } else {
                text.trim()
            }
        }
    }

    private fun promptForEmailThenEnable() {
        val et = EditText(this).apply {
            hint = "npr. vili84@gmail.com"
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle("Email za brzo otključavanje")
            .setMessage("Unesite email naloga koji želite da otključavate telefonom.")
            .setView(et)
            .setPositiveButton("Nastavi") { _, _ ->
                val email = et.text?.toString()?.trim().orEmpty().lowercase()
                if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    setSwitchSilently(false)
                    AlertDialog.Builder(this)
                        .setTitle("Neispravan email")
                        .setMessage("Unesite ispravan email.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@setPositiveButton
                }

                confirmBiometricThenEnable(email)
            }
            .setNegativeButton("Otkaži") { _, _ ->
                setSwitchSilently(false)
            }
            .show()
    }
    private fun openManualPdf() {
        b.manualProgress.visibility = View.VISIBLE
        b.btnManualPdf.isEnabled = false

        lifecycleScope.launch {
            try {
                val manifest = withContext(Dispatchers.IO) {
                    DropboxJsonClient.downloadManualManifest()
                }

                val localFile = File(filesDir, "manuals/${manifest.fileName}")
                val localVersion = ManualPrefs.getVersion(this@SettingsActivity)

                val shouldDownload = !localFile.exists() || manifest.version > localVersion

                if (shouldDownload) {
                    withContext(Dispatchers.IO) {
                        DropboxJsonClient.downloadFileByPath(manifest.pdfPath, localFile)
                    }

                    ManualPrefs.setVersion(this@SettingsActivity, manifest.version)
                    ManualPrefs.setFileName(this@SettingsActivity, manifest.fileName)
                }

                val intent = Intent(this@SettingsActivity, PdfViewerActivity::class.java).apply {
                    putExtra(PdfViewerActivity.EXTRA_FILE_PATH, localFile.absolutePath)
                    putExtra(PdfViewerActivity.EXTRA_TITLE, manifest.title)
                }
                startActivity(intent)

            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    "Ne mogu da otvorim uputstvo: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                b.manualProgress.visibility = View.GONE
                b.btnManualPdf.isEnabled = true
            }
        }
    }

    private fun confirmBiometricThenEnable(email: String) {
        PhoneUnlockAuth.prompt(
            activity = this,
            title = "Potvrdi otključavanje telefonom",
            subtitle = "Potvrdi biometrijom/PIN-om telefona",
            onSuccess = {
                SettingsPrefs.setQuickUnlockEnabled(this, true)
                SettingsPrefs.setLastEmail(this, email)
                refreshUi()
            },
            onCancelOrFail = {
                setSwitchSilently(false)
                SettingsPrefs.setQuickUnlockEnabled(this, false)
                refreshUi()
            },
            onHardError = { msg ->
                setSwitchSilently(false)
                SettingsPrefs.setQuickUnlockEnabled(this, false)

                AlertDialog.Builder(this)
                    .setTitle("Greška")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show()
            }
        )
    }

    private fun refreshUi() {
        val enabled = SettingsPrefs.isQuickUnlockEnabled(this)
        val email = SettingsPrefs.getLastEmail(this)

        setSwitchSilently(enabled)

        b.tvQuickUnlockState.text = if (enabled) {
            "Status: uključeno (${email.ifBlank { "—" }})"
        } else {
            "Status: isključeno"
        }
    }

    private fun setSwitchSilently(value: Boolean) {
        suppressSwitchCallback = true
        b.swQuickUnlock.isChecked = value
        suppressSwitchCallback = false
    }
}
