package com.juniormods.juniorpatcher

import android.os.SystemClock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class PatchService : IPatchService.Stub() {
    companion object {
        private const val NORMAL_PACKAGE = "com.AnotherAxiom.GorillaTag"
        private const val MODDED_PACKAGE = "com.TvMods.GorillaTag"

        // Deliberately built as a normal Kotlin string so shell variables such as $SRC
        // are passed to the shell instead of being parsed as Kotlin interpolation.
        private val OBB_COMMAND =
            "SRC=$NORMAL_PACKAGE; " +
            "DST=$MODDED_PACKAGE; " +
            "mkdir -p /sdcard/Android/obb/\$DST; " +
            "for f in /sdcard/Android/obb/\$SRC/*.obb; do " +
            "[ -f \"\$f\" ] || continue; " +
            "bn=\$(basename \"\$f\"); " +
            "new=\$(echo \"\$bn\" | sed \"s/\$SRC/\$DST/g\"); " +
            "cp -f \"\$f\" \"/sdcard/Android/obb/\$DST/\$new\"; " +
            "echo \"copied \$bn -> \$new\"; " +
            "done"
    }

    override fun copyObb(): String {
        val result = execShell(OBB_COMMAND, 90)
        return if (result.exitCode == 0 && result.stdout.contains("copied")) {
            "✓ OBB copied with ADB.\n${result.stdout.trim()}"
        } else {
            "OBB copy failed (exit ${result.exitCode}).\n${(result.stderr.ifBlank { result.stdout }).takeLast(2500)}"
        }
    }

    override fun restoreCache(): String {
        val src = "/sdcard/Android/data/$NORMAL_PACKAGE/cache"
        val dst = "/sdcard/Android/data/$MODDED_PACKAGE/cache"
        val command =
            "SRC='$src'; DST='$dst'; " +
            "mkdir -p \"\$DST\"; " +
            "if [ -d \"\$SRC\" ]; then cp -R \"\$SRC/.\" \"\$DST/\"; fi; " +
            "echo cache-copy-finished"
        val result = execShell(command, 90)
        return if (result.exitCode == 0 && result.stdout.contains("cache-copy-finished")) {
            "✓ Cache copied with ADB."
        } else {
            "Cache copy failed (exit ${result.exitCode}).\n${(result.stderr.ifBlank { result.stdout }).takeLast(2000)}"
        }
    }

    override fun checkAuthMarker(marker: String): String {
        val safe = marker.replace("'", "'\\''").take(128)
        val command =
            "if ! pm path $NORMAL_PACKAGE >/dev/null 2>&1; then echo NO_GAME; exit 4; fi; " +
            "logcat -d -t 8000 | grep -F -- '$safe' | tail -n 5"
        val result = execShell(command, 20)
        return when {
            result.stdout.isNotBlank() -> "✓ You Have Authed!\nObserved the redeem marker in ADB log output."
            result.exitCode == 4 -> "Real Gorilla Tag is not installed on this device."
            else -> "The redeem marker was not visible in ADB log output.\nThe game may not log redeem text, so TvPatcher will not falsely claim success. Keep the real game open and try again."
        }
    }

    private data class ExecResult(val exitCode: Int, val stdout: String, val stderr: String)

    private fun execShell(command: String, timeoutSeconds: Long): ExecResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val outThread = Thread {
                BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                    lines.forEach { stdout.append(it).append('\n') }
                }
            }
            val errThread = Thread {
                BufferedReader(InputStreamReader(process.errorStream)).useLines { lines ->
                    lines.forEach { stderr.append(it).append('\n') }
                }
            }
            outThread.start()
            errThread.start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return ExecResult(124, stdout.toString(), "command timed out")
            }
            outThread.join(1000)
            errThread.join(1000)
            ExecResult(process.exitValue(), stdout.toString(), stderr.toString())
        } catch (e: Throwable) {
            ExecResult(1, "", e.message ?: e.javaClass.simpleName)
        }
    }

    override fun destroy() {
        SystemClock.sleep(50)
        System.exit(0)
    }
}
