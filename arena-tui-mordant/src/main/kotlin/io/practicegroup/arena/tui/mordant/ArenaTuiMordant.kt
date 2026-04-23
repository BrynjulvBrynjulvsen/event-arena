package io.practicegroup.arena.tui.mordant

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

fun main(args: Array<String>) = runBlocking {
    val config = MordantTuiConfig.fromArgs(args)
    val terminal = Terminal()
    val renderer = DashboardRenderer(terminal)
    val parser = ArenaEventParser(
        JsonMapper.builder()
            .addModule(kotlinModule())
            .build()
    )
    val store = MatchProjectionStore(
        maxTrackedMatches = config.maxTrackedMatches,
        maxBufferedEventsPerMatch = config.maxBufferedEventsPerMatch
    )
    val controller = DashboardController(
        configuredMatchId = config.pinnedMatchId,
        initialAutoFollow = config.initialAutoFollow,
        initialReplaySpeed = config.initialReplaySpeed
    )
    val appEvents = Channel<AppEvent>(capacity = 256)
    val uiInputs = Channel<UiInput>(capacity = 64)
    val keyboard = KeyboardReader()
    val rawMode = RawTerminalController()
    val ingestion = KafkaIngestionService(config, parser)
    val renderLock = Any()

    rawMode.enable()
    terminal.cursor.hide()

    val scope = CoroutineScope(Dispatchers.Default + Job())
    val ingestionJob = scope.launch { ingestion.run(appEvents) }
    val keyboardJob = scope.launch { keyboard.pump(uiInputs) }
    val tickerJob = scope.launch {
        while (isActive) {
            delay(120)
            controller.onTick(store)
            draw(terminal, renderer, controller, store, renderLock)
        }
    }

    try {
        draw(terminal, renderer, controller, store, renderLock)
        while (scope.isActive) {
            var shouldQuit = false
            while (true) {
                val event = appEvents.tryReceive().getOrNull() ?: break
                when (event) {
                    is AppEvent.Consumed -> {
                        store.ingest(event.event, controller.snapshot(store).selectedMatchId)
                    }

                    is AppEvent.Fatal -> {
                        terminal.warning("${event.message}: ${event.cause?.message ?: "unknown"}")
                    }
                }
            }

            while (true) {
                val input = uiInputs.tryReceive().getOrNull() ?: break
                val live = store.snapshot(controller.snapshot(store).selectedMatchId)
                val summaries = store.summaries(controller.snapshot(store).filterText)
                val (boardWidth, boardHeight) = renderer.visibleBoardSize(controller.snapshot(store))
                shouldQuit = controller.onInput(input, summaries, live, boardWidth, boardHeight)
                if (shouldQuit) break
            }
            draw(terminal, renderer, controller, store, renderLock)
            if (shouldQuit) break
            delay(20)
        }
    } finally {
        tickerJob.cancel()
        keyboardJob.cancel()
        ingestionJob.cancel()
        rawMode.close()
        terminal.cursor.show()
        terminal.println()
    }
}

private fun draw(
    terminal: Terminal,
    renderer: DashboardRenderer,
    controller: DashboardController,
    store: MatchProjectionStore,
    renderLock: Any
) {
    val snapshot = controller.snapshot(store)
    synchronized(renderLock) {
        terminal.rawPrint("\u001B[H", false)
        terminal.print(renderer.render(snapshot))
        terminal.rawPrint("\u001B[J", false)
    }
}
