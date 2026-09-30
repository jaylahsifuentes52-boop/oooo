package com.Juniormods.JuniorPatcher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 1401
        private const val BYTEZUKU_PACKAGE = "com.byteus.bytezuku"
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val OTP_API_BASE = "https://e-dy27.onrender.com/"
    }

    private lateinit var grantButton: Button
    private lateinit var copyObbButton: Button
    private lateinit var restoreCacheButton: Button
    private lateinit var requestOtpButton: Button
    private lateinit var verifyOtpButton: Button
    private lateinit var headsetInput: EditText
    private lateinit var otpInput: EditText
    private lateinit var requestCodeText: TextView
    private lateinit var statusText: TextView

    private var patchAccessGranted = false
    private var serviceBound = false
    private var patchService: IPatchService? = null
    private var requestCode: String? = null
    private var otpVerified = false

    private fun createServiceArgs(): Shizuku.UserServiceArgs {
        return Shizuku.UserServiceArgs(ComponentName(this, PatchService::class.java))
            .daemon(false)
            .tag("JuniorPatcher")
            .version(1)
            .processNameSuffix("patch")
    }

    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            patchService = IPatchService.Stub.asInterface(service)
            serviceBound = patchService != null
            runOnUiThread {
                updateUi()
                setStatus(if (serviceBound) "✓ Patch service ready" else "Patch service failed.")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            patchService = null
            serviceBound = false
            runOnUiThread {
                updateUi()
                setStatus("Patch service disconnected.")
            }
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCodeValue, grantResult ->
        if (requestCodeValue != SHIZUKU_REQUEST_CODE) return@OnRequestPermissionResultListener
        runOnUiThread {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                patchAccessGranted = true
                updateUi()
                setStatus("✓ Patch access granted")
                bindPatchService()
            } else {
                patchAccessGranted = false
                updateUi()
                setStatus("Patch access denied.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        grantButton = findViewById(R.id.grantPatchAccess)
        copyObbButton = findViewById(R.id.copyObb)
        restoreCacheButton = findViewById(R.id.restoreCache)
        requestOtpButton = findViewById(R.id.requestOtp)
        verifyOtpButton = findViewById(R.id.verifyOtp)
        headsetInput = findViewById(R.id.headsetInput)
        otpInput = findViewById(R.id.otpInput)
        requestCodeText = findViewById(R.id.requestCodeText)
        statusText = findViewById(R.id.statusText)

        requestOtpButton.setOnClickListener { requestOtp() }
        verifyOtpButton.setOnClickListener { verifyOtp() }
        grantButton.setOnClickListener { requestPatchAccess() }
        copyObbButton.setOnClickListener { runCopyObb() }
        restoreCacheButton.setOnClickListener { runRestoreCache() }

        Shizuku.addRequestPermissionResultListener(permissionListener)
        updatePermissionState()
        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionState()
    }

    private fun requestOtp() {
        val headset = headsetInput.text.toString().trim()
        if (headset.isEmpty()) {
            setStatus("Enter your headset identifier first.")
            return
        }
        requestOtpButton.isEnabled = false
        setStatus("Requesting OTP…")
        Thread {
            val result = try {
                postJson("/api/otp/request", "{\"headset\":\"${jsonEscape(headset)}\"}")
            } catch (e: Throwable) {
                ApiResult(false, "", "Network error: ${e.message ?: e.javaClass.simpleName}")
            }
            runOnUiThread {
                requestOtpButton.isEnabled = true
                if (result.ok) {
                    requestCode = jsonString(result.body, "requestCode")
                    val code = requestCode
                    if (code != null) {
                        requestCodeText.text = "Request code: $code"
                        setStatus("✓ Request created. Use /verify in Discord with this code and the same headset identifier.")
                    } else {
                        setStatus("Server response did not contain a request code.")
                    }
                } else {
                    setStatus("OTP request failed: ${result.error}")
                }
            }
        }.start()
    }

    private fun verifyOtp() {
        val otp = otpInput.text.toString().trim()
        val headset = headsetInput.text.toString().trim()
        if (requestCode.isNullOrEmpty()) {
            setStatus("Request an OTP first.")
            return
        }
        if (otp.length != 6 || !otp.all { it.isDigit() }) {
            setStatus("Enter the 6-digit OTP from Discord.")
            return
        }
        if (headset.isEmpty()) {
            setStatus("Enter the same headset identifier used for the request.")
            return
        }
        verifyOtpButton.isEnabled = false
        setStatus("Checking OTP…")
        Thread {
            val result = try {
                postJson("/api/otp/check", "{\"otp\":\"${jsonEscape(otp)}\",\"headset\":\"${jsonEscape(headset)}\"}")
            } catch (e: Throwable) {
                ApiResult(false, "", "Network error: ${e.message ?: e.javaClass.simpleName}")
            }
            runOnUiThread {
                verifyOtpButton.isEnabled = true
                if (result.ok) {
                    otpVerified = true
                    setStatus("✓ OTP verified. You can now use JuniorPatcher.")
                    updateUi()
                } else {
                    otpVerified = false
                    setStatus("OTP verification failed: ${result.error}")
                    updateUi()
                }
            }
        }.start()
    }

    private fun requestPatchAccess() {
        if (Shizuku.pingBinder()) {
            requestShizukuPermission()
            return
        }
        if (isPackageInstalled(BYTEZUKU_PACKAGE)) {
            setStatus("Bytezuku is installed but its service is not running.\nOpening Bytezuku...")
            openPackage(BYTEZUKU_PACKAGE)
            return
        }
        if (isPackageInstalled(SHIZUKU_PACKAGE)) {
            setStatus("Shizuku is installed but its service is not running.\nOpening Shizuku...")
            openPackage(SHIZUKU_PACKAGE)
            return
        }
        setStatus("No compatible privilege manager was found.")
    }

    private fun requestShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            setStatus("Compatible privilege service is not running.")
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            patchAccessGranted = true
            updateUi()
            setStatus("✓ Patch access already granted")
            bindPatchService()
            return
        }
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            setStatus("Allow JuniorPatcher in the privilege manager.")
            return
        }
        setStatus("Requesting Patch Access...")
        Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
    }

    private fun updatePermissionState() {
        if (!Shizuku.pingBinder()) {
            patchAccessGranted = false
            serviceBound = false
            patchService = null
            updateUi()
            setStatus("Tap Grant Patch Access.")
            return
        }
        patchAccessGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        updateUi()
        if (patchAccessGranted) bindPatchService()
    }

    private fun bindPatchService() {
        if (!Shizuku.pingBinder() || !patchAccessGranted || serviceBound) return
        try {
            Shizuku.bindUserService(createServiceArgs(), serviceConnection)
            setStatus("Starting patch service...")
        } catch (e: Throwable) {
            setStatus("Service start failed:\n${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun runCopyObb() {
        if (!checkReady()) return
        val service = patchService ?: return
        copyObbButton.isEnabled = false
        restoreCacheButton.isEnabled = false
        setStatus("Copying OBB...")
        Thread {
            val result = try { service.copyObb() } catch (e: Throwable) { "OBB copy failed:\n${e.message ?: e.javaClass.simpleName}" }
            runOnUiThread { updateUi(); setStatus(result) }
        }.start()
    }

    private fun runRestoreCache() {
        if (!checkReady()) return
        val service = patchService ?: return
        copyObbButton.isEnabled = false
        restoreCacheButton.isEnabled = false
        setStatus("Restoring cache...")
        Thread {
            val result = try { service.restoreCache() } catch (e: Throwable) { "Cache restore failed:\n${e.message ?: e.javaClass.simpleName}" }
            runOnUiThread { updateUi(); setStatus(result) }
        }.start()
    }

    private fun checkReady(): Boolean {
        if (!otpVerified) {
            setStatus("Verify the OTP first.")
            return false
        }
        if (!Shizuku.pingBinder()) {
            setStatus("Privilege service is not running.")
            return false
        }
        if (!patchAccessGranted) {
            setStatus("Grant Patch Access first.")
            return false
        }
        if (!serviceBound || patchService == null) {
            setStatus("Patch service is starting...")
            bindPatchService()
            return false
        }
        return true
    }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun openPackage(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                setStatus("Could not open $packageName.")
                return
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Throwable) {
            setStatus("Could not open manager:\n${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun updateUi() {
        grantButton.isEnabled = true
        val ready = otpVerified && patchAccessGranted && serviceBound && patchService != null
        copyObbButton.isEnabled = ready
        restoreCacheButton.isEnabled = ready
        verifyOtpButton.isEnabled = true
    }

    private fun setStatus(message: String) { statusText.text = message }

    private data class ApiResult(val ok: Boolean, val body: String, val error: String)

    private fun postJson(path: String, json: String): ApiResult {
        val connection = (URL(OTP_API_BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(json.toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.let { BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { r -> r.readText() } } ?: ""
            if (code in 200..299) ApiResult(true, body, "")
            else ApiResult(false, body, jsonString(body, "error") ?: "HTTP $code")
        } finally {
            connection.disconnect()
        }
    }

    private fun jsonString(json: String, key: String): String? {
        val regex = Regex("\\\"" + Regex.escape(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
        return regex.find(json)?.groupValues?.getOrNull(1)
    }

    private fun jsonEscape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        if (serviceBound) {
            try { patchService?.destroy() } catch (_: Throwable) {}
            try { Shizuku.unbindUserService(createServiceArgs(), serviceConnection, true) } catch (_: Throwable) {}
        }
        patchService = null
        serviceBound = false
        super.onDestroy()
    }
}
