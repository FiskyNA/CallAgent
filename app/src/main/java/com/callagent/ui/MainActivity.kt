package com.callagent.ui

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.callagent.R
import com.callagent.data.AgentConfig
import com.callagent.data.ConfigRepository

// ─────────────────────────────────────────────────────────────
//  MainActivity — setup screen
//
//  Screens:
//    1. Permissions & default phone app setup
//    2. Agent config (name, greeting, tone, timeout)
//    3. Enable/disable toggle
//    4. Recent call log
//
//  Note: In a production app, use Jetpack Compose for the UI.
//  This uses simple Views to keep the code readable.
// ─────────────────────────────────────────────────────────────

class MainActivity : AppCompatActivity() {

    private lateinit var repo: ConfigRepository

    // Views
    private lateinit var btnSetDefaultPhone: Button
    private lateinit var switchAgentEnabled: Switch
    private lateinit var etAgentName: EditText
    private lateinit var etCompany: EditText
    private lateinit var etRole: EditText
    private lateinit var etGreeting: EditText
    private lateinit var etApiKey: EditText
    private lateinit var etTransferNumber: EditText
    private lateinit var seekTimeout: SeekBar
    private lateinit var tvTimeout: TextView
    private lateinit var btnSave: Button
    private lateinit var tvStatus: TextView
    private lateinit var llCallLogs: LinearLayout

    // Permission launcher
    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted) {
            showToast("Some permissions were denied. The agent may not work correctly.")
        }
    }

    // Default phone app request launcher
    private val requestDefaultPhone = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateDefaultPhoneStatus()
    }

    // ── Lifecycle ────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = ConfigRepository(this)

        bindViews()
        loadConfig()
        setupListeners()
        requestNeededPermissions()
        updateDefaultPhoneStatus()
        loadCallLogs()
    }

    override fun onResume() {
        super.onResume()
        updateDefaultPhoneStatus()
        loadCallLogs()
    }

    // ── View binding ─────────────────────────────────────────

    private fun bindViews() {
        btnSetDefaultPhone = findViewById(R.id.btnSetDefaultPhone)
        switchAgentEnabled  = findViewById(R.id.switchAgentEnabled)
        etAgentName         = findViewById(R.id.etAgentName)
        etCompany           = findViewById(R.id.etCompany)
        etRole              = findViewById(R.id.etRole)
        etGreeting          = findViewById(R.id.etGreeting)
        etApiKey            = findViewById(R.id.etApiKey)
        etTransferNumber    = findViewById(R.id.etTransferNumber)
        seekTimeout         = findViewById(R.id.seekTimeout)
        tvTimeout           = findViewById(R.id.tvTimeout)
        btnSave             = findViewById(R.id.btnSave)
        tvStatus            = findViewById(R.id.tvStatus)
        llCallLogs          = findViewById(R.id.llCallLogs)
    }

    // ── Load saved config into fields ────────────────────────

    private fun loadConfig() {
        val config = repo.getConfig()
        etAgentName.setText(config.name)
        etCompany.setText(config.company)
        etRole.setText(config.role)
        etGreeting.setText(config.greeting)
        etApiKey.setText(repo.getApiKey())
        etTransferNumber.setText(config.transferNumber)
        switchAgentEnabled.isChecked = config.isEnabled

        // Timeout slider: 5–60 seconds
        seekTimeout.max = 55
        seekTimeout.progress = config.ringTimeoutSeconds - 5
        tvTimeout.text = "${config.ringTimeoutSeconds}s"
    }

    // ── Wire up interactions ─────────────────────────────────

    private fun setupListeners() {
        btnSetDefaultPhone.setOnClickListener { requestDefaultPhoneApp() }

        seekTimeout.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                tvTimeout.text = "${progress + 5}s"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        btnSave.setOnClickListener { saveConfig() }

        switchAgentEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isDefaultPhoneApp()) {
                showToast("Please set CallAgent as your default phone app first")
                switchAgentEnabled.isChecked = false
                return@setOnCheckedChangeListener
            }
            val config = repo.getConfig().copy(isEnabled = isChecked)
            repo.saveConfig(config)
            updateStatusLabel(isChecked)
        }
    }

    // ── Save config ──────────────────────────────────────────

    private fun saveConfig() {
        val timeout = seekTimeout.progress + 5
        val config = AgentConfig(
            name = etAgentName.text.toString().trim().ifBlank { "Aria" },
            company = etCompany.text.toString().trim(),
            role = etRole.text.toString().trim().ifBlank { "customer support assistant" },
            greeting = etGreeting.text.toString().trim().ifBlank { "Hi! How can I help?" },
            tone = "professional and friendly",
            language = "en-IN",
            ringTimeoutSeconds = timeout,
            isEnabled = switchAgentEnabled.isChecked,
            transferNumber = etTransferNumber.text.toString().trim(),
        )
        repo.saveConfig(config)
        repo.saveApiKey(etApiKey.text.toString().trim())
        showToast("Settings saved ✓")
    }

    // ── Default phone app ────────────────────────────────────

    private fun requestDefaultPhoneApp() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        ) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            requestDefaultPhone.launch(intent)
        } else {
            showToast("Already set as default phone app!")
        }
    }

    private fun isDefaultPhoneApp(): Boolean {
        val telecom = getSystemService(TelecomManager::class.java)
        return telecom.defaultDialerPackage == packageName
    }

    private fun updateDefaultPhoneStatus() {
        val isDefault = isDefaultPhoneApp()
        btnSetDefaultPhone.text = if (isDefault) "✓ Default phone app set" else "Set as Default Phone App"
        btnSetDefaultPhone.isEnabled = !isDefault
        updateStatusLabel(repo.getConfig().isEnabled)
    }

    private fun updateStatusLabel(enabled: Boolean) {
        tvStatus.text = when {
            !isDefaultPhoneApp() -> "⚠ Not set as default phone app"
            enabled              -> "● Agent is active — calls will be handled automatically"
            else                 -> "○ Agent is off"
        }
    }

    // ── Call logs ────────────────────────────────────────────

    private fun loadCallLogs() {
        llCallLogs.removeAllViews()
        val logs = repo.getCallLogs()

        if (logs.isEmpty()) {
            val tv = TextView(this).apply {
                text = "No calls yet"
                setPadding(0, 16, 0, 16)
                setTextColor(resources.getColor(android.R.color.darker_gray, theme))
            }
            llCallLogs.addView(tv)
            return
        }

        logs.take(20).forEach { log ->
            val row = layoutInflater.inflate(R.layout.item_call_log, llCallLogs, false)
            row.findViewById<TextView>(R.id.tvCallerName).text  = log.callerName
            row.findViewById<TextView>(R.id.tvCallerNumber).text = log.callerNumber
            row.findViewById<TextView>(R.id.tvSummary).text     = log.summary
            row.findViewById<TextView>(R.id.tvDuration).text    = "${log.durationSeconds}s"
            llCallLogs.addView(row)
        }
    }

    // ── Permissions ──────────────────────────────────────────

    private fun requestNeededPermissions() {
        requestPermissions.launch(arrayOf(
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.ANSWER_PHONE_CALLS,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_CALL_LOG,
        ))
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
