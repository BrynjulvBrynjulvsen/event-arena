package io.practicegroup.arena.tui.mordant

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RawTerminalControllerTest {

    @Test
    fun `raw mode close restores terminal state`() {
        val fake = RecordingSttyShell()
        val output = RecordingAppendable()
        val controller = RawTerminalController(fake, output)

        controller.enable()
        controller.close()

        assertEquals(listOf("stty -g", "stty -icanon -echo min 1 time 0", "stty sane-mode"), fake.commands)
        assertEquals("\u001B[?1049h\u001B[H\u001B[J\u001B[?1049l", output.content.toString())
    }

    private class RecordingSttyShell : SttyShell() {
        val commands = mutableListOf<String>()

        override fun capture(): String {
            commands += "stty -g"
            return "sane-mode"
        }

        override fun applyRawMode() {
            commands += "stty -icanon -echo min 1 time 0"
        }

        override fun restore(mode: String) {
            commands += "stty $mode"
        }
    }

    private class RecordingAppendable : Appendable, java.io.Flushable {
        val content = StringBuilder()

        override fun append(csq: CharSequence?): Appendable {
            content.append(csq)
            return this
        }

        override fun append(csq: CharSequence?, start: Int, end: Int): Appendable {
            content.append(csq, start, end)
            return this
        }

        override fun append(c: Char): Appendable {
            content.append(c)
            return this
        }

        override fun flush() = Unit
    }
}
