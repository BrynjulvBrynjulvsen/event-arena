package io.practicegroup.arena.tui.mordant

import kotlin.math.roundToInt

sealed interface UiInput {
    data object Up : UiInput
    data object Down : UiInput
    data object Left : UiInput
    data object Right : UiInput
    data object PreviousFrame : UiInput
    data object NextFrame : UiInput
    data object Enter : UiInput
    data object Tab : UiInput
    data object ShiftTab : UiInput
    data object ToggleFollow : UiInput
    data object TogglePause : UiInput
    data object SpeedDown : UiInput
    data object SpeedUp : UiInput
    data object Filter : UiInput
    data object Help : UiInput
    data object Quit : UiInput
    data object Backspace : UiInput
    data class Character(val value: Char) : UiInput
}

class DashboardController(
    private val configuredMatchId: String?,
    initialAutoFollow: Boolean,
    initialReplaySpeed: Double
) {
    private val replaySpeeds = listOf(0.5, 1.0, 2.0, 4.0)
    private var selectedMatchId: String? = configuredMatchId
    private var focusPane: FocusPane = FocusPane.MATCH_LIST
    private var autoFollow: Boolean = initialAutoFollow && configuredMatchId == null
    private var paused: Boolean = false
    private var replaySpeed: Double = replaySpeeds.minByOrNull { kotlin.math.abs(it - initialReplaySpeed) } ?: 1.0
    private var filterText: String = ""
    private var filterMode: Boolean = false
    private var helpVisible: Boolean = false
    private var viewport: BoardViewport = BoardViewport()
    private var visibleSequence: Long = Long.MAX_VALUE
    private var lastFilteredIds: List<String> = emptyList()
    private var initializedSelection: Pair<String?, MatchStatus?>? = null

    fun onStoreUpdated(summaries: List<MatchSummary>) {
        lastFilteredIds = summaries.map { it.matchId }
        val newest = summaries.firstOrNull()?.matchId
        val preferredFollowMatchId = preferredFollowMatchId(summaries)
        when {
            selectedMatchId == null -> {
                updateSelectedMatch(configuredMatchId ?: if (autoFollow) preferredFollowMatchId else newest)
            }

            autoFollow && configuredMatchId == null && newest != null -> {
                updateSelectedMatch(preferredFollowMatchId ?: newest)
            }

            selectedMatchId !in lastFilteredIds -> {
                updateSelectedMatch(configuredMatchId ?: if (autoFollow) preferredFollowMatchId else newest)
            }
        }
    }

    fun onInput(input: UiInput, summaries: List<MatchSummary>, liveRecord: MatchRecord?, visibleBoardWidth: Int, visibleBoardHeight: Int): Boolean {
        if (filterMode) {
            when (input) {
                UiInput.Enter -> filterMode = false
                UiInput.Backspace -> filterText = filterText.dropLast(1)
                is UiInput.Character -> filterText += input.value
                UiInput.Quit -> return true
                else -> return false
            }
            return false
        }

        when (input) {
            UiInput.Up -> moveSelection(-1, summaries)
            UiInput.Down -> moveSelection(1, summaries)
            UiInput.Enter -> focusPane = FocusPane.BOARD
            UiInput.Tab -> focusPane = focusPane.next()
            UiInput.ShiftTab -> focusPane = focusPane.previous()
            UiInput.ToggleFollow -> {
                autoFollow = !autoFollow
                if (autoFollow) {
                    updateSelectedMatch(preferredFollowMatchId(summaries))
                }
            }

            UiInput.TogglePause -> paused = !paused
            UiInput.SpeedDown -> replaySpeed = shiftSpeed(-1)
            UiInput.SpeedUp -> replaySpeed = shiftSpeed(1)
            UiInput.Filter -> {
                filterMode = true
                filterText = ""
            }

            UiInput.Help -> helpVisible = !helpVisible
            UiInput.Left,
            UiInput.PreviousFrame -> handlePreviousFrameInput(liveRecord, visibleBoardWidth, visibleBoardHeight)
            UiInput.Right,
            UiInput.NextFrame -> handleNextFrameInput(liveRecord, visibleBoardWidth, visibleBoardHeight)
            UiInput.Backspace -> {}
            UiInput.Quit -> return true
            is UiInput.Character -> {
                when (input.value) {
                    'h' -> handlePreviousFrameInput(liveRecord, visibleBoardWidth, visibleBoardHeight)
                    'j' -> if (focusPane == FocusPane.MATCH_LIST) moveSelection(1, summaries) else moveViewport(0, 1, liveRecord, visibleBoardWidth, visibleBoardHeight)
                    'k' -> if (focusPane == FocusPane.MATCH_LIST) moveSelection(-1, summaries) else moveViewport(0, -1, liveRecord, visibleBoardWidth, visibleBoardHeight)
                    'l' -> handleNextFrameInput(liveRecord, visibleBoardWidth, visibleBoardHeight)
                }
            }
        }
        return false
    }

    fun snapshot(store: MatchProjectionStore): ViewSnapshot {
        val summaries = store.summaries(filterText)
        onStoreUpdated(summaries)
        val liveRecord = store.snapshot(selectedMatchId)
        initializeSelectionState(liveRecord)
        val visibleFrame = currentVisibleFrame(liveRecord)
        val visibleFrameIndex = liveRecord?.frames
            ?.indexOfFirst { it.sequence == visibleFrame?.sequence }
            ?.takeIf { it >= 0 }
        val totalFrameCount = liveRecord?.frames?.size ?: 0
        return ViewSnapshot(
            summaries = summaries,
            selectedMatchId = selectedMatchId,
            liveRecord = liveRecord,
            visibleFrame = visibleFrame,
            replayControlsActive = isFinishedMatchReplay(liveRecord),
            visibleFrameIndex = visibleFrameIndex,
            totalFrameCount = totalFrameCount,
            atFirstFrame = visibleFrameIndex == 0,
            atLastFrame = visibleFrameIndex != null && visibleFrameIndex == totalFrameCount - 1,
            focusPane = focusPane,
            autoFollow = autoFollow,
            paused = paused,
            replaySpeed = replaySpeed,
            filterText = filterText,
            filterMode = filterMode,
            helpVisible = helpVisible,
            viewport = viewport
        )
    }

    fun onTick(store: MatchProjectionStore) {
        if (paused) return
        val record = store.snapshot(selectedMatchId) ?: return
        val current = record.frames.lastOrNull()?.sequence ?: return
        if (visibleSequence == Long.MAX_VALUE || visibleSequence >= current) {
            visibleSequence = current
            return
        }
        val gap = current - visibleSequence
        val step = replaySpeed.roundToInt().coerceAtLeast(1).toLong()
        visibleSequence += minOf(gap, step)
    }

    private fun shiftSpeed(direction: Int): Double {
        val index = replaySpeeds.indexOf(replaySpeed).coerceAtLeast(0)
        val next = (index + direction).coerceIn(0, replaySpeeds.lastIndex)
        return replaySpeeds[next]
    }

    private fun moveSelection(delta: Int, summaries: List<MatchSummary>) {
        if (summaries.isEmpty()) return
        autoFollow = false
        val ids = summaries.map { it.matchId }
        val currentIndex = ids.indexOf(selectedMatchId).takeIf { it >= 0 } ?: 0
        updateSelectedMatch(ids[(currentIndex + delta).coerceIn(0, ids.lastIndex)])
    }

    private fun moveViewport(dx: Int, dy: Int, liveRecord: MatchRecord?, visibleBoardWidth: Int, visibleBoardHeight: Int) {
        if (focusPane != FocusPane.BOARD) return
        val detail = currentVisibleFrame(liveRecord)?.detail ?: liveRecord?.liveDetail ?: return
        viewport = BoardViewport(viewport.x + dx, viewport.y + dy).clamp(
            boardWidth = detail.boardWidth,
            boardHeight = detail.boardHeight,
            visibleWidth = visibleBoardWidth,
            visibleHeight = visibleBoardHeight
        )
    }

    private fun updateSelectedMatch(matchId: String?) {
        if (selectedMatchId == matchId) return
        selectedMatchId = matchId
        initializedSelection = null
        resetReplayPositionToEnd()
    }

    private fun initializeSelectionState(liveRecord: MatchRecord?) {
        val selection = selectedMatchId to liveRecord?.summary?.status
        if (selection == initializedSelection) return
        initializedSelection = selection
        if (isFinishedMatchReplay(liveRecord)) {
            paused = true
            autoFollow = false
            visibleSequence = liveRecord?.frames?.lastOrNull()?.sequence ?: Long.MAX_VALUE
            return
        }
        resetReplayPositionToEnd()
    }

    private fun currentVisibleFrame(liveRecord: MatchRecord?): MatchFrame? {
        liveRecord ?: return null
        return if (visibleSequence == Long.MAX_VALUE) {
            liveRecord.frames.lastOrNull()
        } else {
            liveRecord.frames.firstOrNull { it.sequence >= visibleSequence } ?: liveRecord.frames.lastOrNull()
        }
    }

    private fun currentVisibleFrameIndex(liveRecord: MatchRecord): Int {
        val visibleFrame = currentVisibleFrame(liveRecord)
        return liveRecord.frames.indexOfFirst { it.sequence == visibleFrame?.sequence }
            .takeIf { it >= 0 }
            ?: liveRecord.frames.lastIndex
    }

    private fun isFinishedMatchReplay(liveRecord: MatchRecord?): Boolean {
        return liveRecord?.summary?.status == MatchStatus.ENDED
    }

    private fun isFinishedMatchReplayContext(liveRecord: MatchRecord?): Boolean {
        return isFinishedMatchReplay(liveRecord) && focusPane != FocusPane.BOARD
    }

    private fun handlePreviousFrameInput(liveRecord: MatchRecord?, visibleBoardWidth: Int, visibleBoardHeight: Int) {
        if (isFinishedMatchReplayContext(liveRecord)) {
            stepReplayFrame(liveRecord, -1)
            return
        }
        moveViewport(-1, 0, liveRecord, visibleBoardWidth, visibleBoardHeight)
    }

    private fun handleNextFrameInput(liveRecord: MatchRecord?, visibleBoardWidth: Int, visibleBoardHeight: Int) {
        if (isFinishedMatchReplayContext(liveRecord)) {
            stepReplayFrame(liveRecord, 1)
            return
        }
        moveViewport(1, 0, liveRecord, visibleBoardWidth, visibleBoardHeight)
    }

    private fun stepReplayFrame(liveRecord: MatchRecord?, delta: Int) {
        liveRecord ?: return
        if (!isFinishedMatchReplay(liveRecord) || liveRecord.frames.isEmpty()) return
        paused = true
        autoFollow = false
        val currentIndex = currentVisibleFrameIndex(liveRecord)
        val targetIndex = (currentIndex + delta).coerceIn(0, liveRecord.frames.lastIndex)
        visibleSequence = liveRecord.frames[targetIndex].sequence
    }

    private fun resetReplayPositionToEnd() {
        visibleSequence = Long.MAX_VALUE
    }

    private fun preferredFollowMatchId(summaries: List<MatchSummary>): String? {
        return summaries.firstOrNull { it.status == MatchStatus.RUNNING }?.matchId
            ?: summaries.firstOrNull()?.matchId
    }
}
