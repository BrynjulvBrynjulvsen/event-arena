package io.practicegroup.arena.tui.mordant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.Flushable
import java.lang.ProcessBuilder.Redirect

class RawTerminalController(
    private val shell: SttyShell = SttyShell(),
    private val output: Appendable = System.out
) : AutoCloseable {
    private var originalMode: String? = null

    fun enable() {
        val saved = shell.capture() ?: return
        originalMode = saved
        shell.applyRawMode()
        writeEscape("\u001B[?1049h\u001B[H\u001B[J")
    }

    override fun close() {
        writeEscape("\u001B[?1049l")
        originalMode?.let(shell::restore)
    }

    private fun writeEscape(sequence: String) {
        output.append(sequence)
        if (output is Flushable) {
            output.flush()
        }
    }
}

open class SttyShell {
    open fun capture(): String? = runCommand("stty", "-g")

    open fun applyRawMode() {
        runCommand("stty", "-icanon", "-echo", "min", "1", "time", "0")
    }

    open fun restore(mode: String) {
        runCommand("stty", mode)
    }

    private fun runCommand(vararg command: String): String? {
        return runCatching {
            val process = ProcessBuilder(*command)
                .redirectInput(Redirect.INHERIT)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0) output else null
        }.getOrNull()
    }
}

class KeyboardReader(
    private val input: InputStream = System.`in`
) {
    suspend fun pump(out: SendChannel<UiInput>) = withContext(Dispatchers.IO) {
        while (!out.isClosedForSend) {
            val code = input.read()
            if (code < 0) break
            mapInput(code)?.let { out.send(it) }
        }
    }

    internal fun mapInput(code: Int, next: (() -> Int)? = null): UiInput? {
        return when (code) {
            3, 113 -> UiInput.Quit
            10, 13 -> UiInput.Enter
            9 -> UiInput.Tab
            127 -> UiInput.Backspace
            47 -> UiInput.Filter
            63 -> UiInput.Help
            91 -> UiInput.SpeedDown
            93 -> UiInput.SpeedUp
            102 -> UiInput.ToggleFollow
            112 -> UiInput.TogglePause
            27 -> readEscape(next)
            in 32..126 -> UiInput.Character(code.toChar())
            else -> null
        }
    }

    private fun readEscape(next: (() -> Int)?): UiInput? {
        val reader = next ?: return null
        return when (reader()) {
            91 -> when (reader()) {
                65 -> UiInput.Up
                66 -> UiInput.Down
                67 -> UiInput.NextFrame
                68 -> UiInput.PreviousFrame
                90 -> UiInput.ShiftTab
                else -> null
            }

            else -> null
        }
    }
}
