package io.practicegroup.arena.tui.mordant

import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.rendering.Line
import com.github.ajalt.mordant.rendering.Lines
import com.github.ajalt.mordant.rendering.Span
import com.github.ajalt.mordant.rendering.TextColors.blue
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.rendering.WidthRange
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.table.grid
import com.github.ajalt.mordant.table.row
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import com.github.ajalt.mordant.terminal.Terminal

class DashboardRenderer(
    private val terminal: Terminal
) {
    fun render(snapshot: ViewSnapshot): String {
        val (matchListWidth, detailWidth) = layoutWidths()
        val base = terminal.render(
            FixedColumnsWidget(
            left = renderMatchList(snapshot, matchListWidth),
            leftWidth = matchListWidth,
            right = renderDetail(snapshot),
            rightWidth = detailWidth
        )
        )
        if (!snapshot.helpVisible) return base
        return buildString {
            append(base)
            append('\n')
            append(helpOverlay())
        }
    }

    fun visibleBoardSize(snapshot: ViewSnapshot): Pair<Int, Int> {
        val detailWidth = layoutWidths().second
        val width = (detailWidth - 8).coerceAtLeast(18) / 2
        val height = (terminal.info.height - 14).coerceAtLeast(5)
        return width to height
    }

    private fun renderMatchList(snapshot: ViewSnapshot, paneWidth: Int): Panel {
        val rows = snapshot.summaries.take((terminal.info.height - 10).coerceAtLeast(5)).ifEmpty {
            listOf(
                MatchSummary(
                    matchId = "(none)",
                    latestEvent = "waiting for matches"
                )
            )
        }
        val latestColumnWidth = (paneWidth - 34).coerceAtLeast(12)
        val latestPreviewWidth = (latestColumnWidth - 2).coerceAtLeast(8)

        val body = grid {
            column(16) {}
            column(8) {}
            column(6) {}
            column(latestColumnWidth) {}
            row {
                cell("match")
                cell("status")
                cell("turn")
                cell("latest")
            }
            rows.forEach { summary ->
                row {
                    val prefix = if (summary.matchId == snapshot.selectedMatchId) "> " else "  "
                    val line = prefix + summary.matchId
                    cell(if (summary.matchId == snapshot.selectedMatchId) green(line) else line)
                    cell(statusLabel(summary.status))
                    cell(summary.currentTurn.toString())
                    cell(summary.latestEvent.take(latestPreviewWidth))
                }
            }
        }

        return Panel(
            body,
            title = Text(title("matches", snapshot.focusPane == FocusPane.MATCH_LIST)),
            borderType = BorderType.SQUARE,
            expand = true
        )
    }

    private fun renderDetail(snapshot: ViewSnapshot): Panel {
        val detail = snapshot.visibleFrame?.detail ?: snapshot.liveRecord?.liveDetail
        val body = if (detail == null) {
            Text("waiting for match selection")
        } else {
            val board = renderBoard(detail, snapshot)
            val stats = renderStats(detail)
            val log = renderLog(detail)
            grid {
                row {
                    cell(Panel(board, title = Text(title("board", snapshot.focusPane == FocusPane.BOARD)), borderType = BorderType.SQUARE))
                }
                row {
                    cell(Panel(stats, title = Text("fighters"), borderType = BorderType.SQUARE))
                }
                row {
                    cell(Panel(log, title = Text(title("events", snapshot.focusPane == FocusPane.EVENT_LOG)), borderType = BorderType.SQUARE))
                }
                row {
                    cell(Text(footer(snapshot, detail)))
                }
            }
        }
        return Panel(
            body,
            title = Text("detail"),
            borderType = BorderType.SQUARE,
            expand = true
        )
    }

    private fun renderBoard(detail: MatchDetailState, snapshot: ViewSnapshot): Text {
        val (visibleWidth, visibleHeight) = visibleBoardSize(snapshot)
        val viewport = snapshot.viewport.clamp(detail.boardWidth, detail.boardHeight, visibleWidth, visibleHeight)
        val lines = buildList {
            for (y in viewport.y until minOf(detail.boardHeight, viewport.y + visibleHeight)) {
                add(buildString {
                    for (x in viewport.x until minOf(detail.boardWidth, viewport.x + visibleWidth)) {
                        append(glyphAt(detail, x, y))
                    }
                })
            }
        }
        return Text(lines.joinToString("\n"))
    }

    private fun glyphAt(detail: MatchDetailState, x: Int, y: Int): String {
        val entity = detail.entities.values.firstOrNull { it.position.x == x && it.position.y == y }
        return when (entity?.type) {
            null -> ". "
            "FIGHTER" -> if (entity.faction == "BLUE") "B " else "R "
            "COVER" -> "# "
            "ITEM" -> "* "
            else -> "? "
        }
    }

    private fun renderStats(detail: MatchDetailState): Text {
        val lines = detail.entities.values
            .filter { it.type == "FIGHTER" }
            .sortedBy { it.id }
            .ifEmpty { listOf(EntityState(id = "waiting", type = "FIGHTER", position = Point(0, 0))) }
            .map { fighter ->
                buildString {
                    append(fighter.id)
                    append(" ")
                    append(fighter.faction ?: "NEUTRAL")
                    append(" HP ")
                    append(fighter.hp ?: "?")
                    append("/")
                    append(fighter.maxHp ?: "?")
                    fighter.attackRange?.let { append(" RNG $it") }
                    if (fighter.statuses.isNotEmpty()) {
                        append(" ")
                        append(fighter.statuses.joinToString(","))
                    }
                }
            }
        return Text(lines.joinToString("\n"))
    }

    private fun renderLog(detail: MatchDetailState): Text {
        return Text(
            detail.log.takeLast(6).joinToString("\n") { entry ->
                "t${entry.turn} ${entry.text}"
            }
        )
    }

    private fun statusLabel(status: MatchStatus): String {
        return when (status) {
            MatchStatus.SCHEDULED -> yellow("sched")
            MatchStatus.RUNNING -> blue("live")
            MatchStatus.ENDED -> red("done")
        }.toString()
    }

    private fun footer(snapshot: ViewSnapshot, detail: MatchDetailState): String {
        val mode = when {
            snapshot.replayControlsActive -> "replay"
            snapshot.paused -> "paused"
            else -> "live"
        }
        val filter = if (snapshot.filterMode) " filter=${snapshot.filterText}" else ""
        val replay = if (snapshot.replayControlsActive && snapshot.visibleFrameIndex != null) {
            val frameNumber = snapshot.visibleFrameIndex + 1
            val startFlag = if (snapshot.atFirstFrame) " start" else ""
            val endFlag = if (snapshot.atLastFrame) " end" else ""
            " frame=$frameNumber/${snapshot.totalFrameCount}$startFlag$endFlag"
        } else {
            ""
        }
        val keys = if (snapshot.replayControlsActive) {
            "keys: j/k select  tab focus  ←/→ or h/l step  f follow  p pause  [] speed  / filter  ? help  q quit"
        } else {
            "keys: j/k select  tab focus  h/j/k/l pan board  f follow  p pause  [] speed  / filter  ? help  q quit"
        }
        return "match=${detail.matchId} turn=${detail.currentTurn} mode=$mode follow=${snapshot.autoFollow} speed=${snapshot.replaySpeed}x$replay$filter  $keys"
    }

    private fun title(name: String, focused: Boolean): String {
        return if (focused) "[${name}]" else name
    }

    private fun helpOverlay(): String {
        return "Help: j/k or arrows move, enter focuses detail, tab cycles panes, h/j/k/l pans board, and finished matches use ←/→ or h/l to step frames outside board focus. f follow, p pause, [ ] replay speed, / filter, q quit"
    }

    private fun layoutWidths(): Pair<Int, Int> {
        val totalWidth = terminal.info.width.coerceAtLeast(80)
        val usableWidth = (totalWidth - 1).coerceAtLeast(79)
        val matchListWidth = (usableWidth * 34 / 100).coerceIn(28, usableWidth - 32)
        val detailWidth = (usableWidth - matchListWidth).coerceAtLeast(32)
        return matchListWidth to detailWidth
    }
}

private class FixedColumnsWidget(
    private val left: Widget,
    private val leftWidth: Int,
    private val right: Widget,
    private val rightWidth: Int
) : Widget {
    override fun measure(terminal: Terminal, width: Int): WidthRange {
        val total = leftWidth + 1 + rightWidth
        return WidthRange(total, total)
    }

    override fun render(terminal: Terminal, width: Int): Lines {
        val leftLines = left.render(terminal, leftWidth).lines
        val rightLines = right.render(terminal, rightWidth).lines
        val height = maxOf(leftLines.size, rightLines.size)

        return Lines(
            buildList(height) {
                repeat(height) { index ->
                    val leftLine = leftLines.getOrElse(index) { Line(emptyList()) }
                    val rightLine = rightLines.getOrElse(index) { Line(emptyList()) }
                    add(
                        Line(
                            buildList {
                                addAll(leftLine.spans)
                                add(Span.space((leftWidth - lineWidth(leftLine)).coerceAtLeast(0) + 1))
                                addAll(rightLine.spans)
                            }
                        )
                    )
                }
            }
        )
    }

    private fun lineWidth(line: Line): Int = line.sumOf { it.text.length }
}
