package io.practicegroup.arena.tui.mordant

data class MordantTuiConfig(
    val bootstrapServers: String = "localhost:9092",
    val schemaRegistryUrl: String = "http://localhost:8081",
    val lifecycleTopic: String = "arena.match-lifecycle.v1",
    val matchEventsTopic: String = "arena.match-events.v1",
    val pinnedMatchId: String? = null,
    val initialAutoFollow: Boolean = true,
    val maxTrackedMatches: Int = 50,
    val maxBufferedEventsPerMatch: Int = 200,
    val historicalImportRecords: Int = 5000,
    val initialReplaySpeed: Double = 1.0,
    val consumerGroupId: String = "arena-tui-mordant"
) {
    companion object {
        fun fromArgs(args: Array<String>): MordantTuiConfig {
            val values = args
                .filter { it.startsWith("--") }
                .map { it.removePrefix("--") }
                .mapNotNull {
                    val idx = it.indexOf('=')
                    if (idx < 0) null else it.substring(0, idx) to it.substring(idx + 1)
                }
                .toMap()

            fun string(name: String, default: String): String {
                return values[name]
                    ?: System.getProperty(name)
                    ?: System.getenv(name.replace('.', '_').uppercase())
                    ?: default
            }

            fun optional(name: String): String? {
                val value = values[name]
                    ?: System.getProperty(name)
                    ?: System.getenv(name.replace('.', '_').uppercase())
                return value?.ifBlank { null }
            }

            fun int(name: String, default: Int): Int = string(name, default.toString()).toIntOrNull() ?: default

            fun boolean(name: String, default: Boolean): Boolean = string(name, default.toString()).toBooleanStrictOrNull() ?: default

            fun double(name: String, default: Double): Double = string(name, default.toString()).toDoubleOrNull() ?: default

            return MordantTuiConfig(
                bootstrapServers = string("arena.kafka.bootstrap-servers", "localhost:9092"),
                schemaRegistryUrl = string("arena.kafka.schema-registry-url", "http://localhost:8081"),
                lifecycleTopic = string("arena.kafka.lifecycle-topic", "arena.match-lifecycle.v1"),
                matchEventsTopic = string("arena.kafka.match-events-topic", "arena.match-events.v1"),
                pinnedMatchId = optional("arena.tui.match-id"),
                initialAutoFollow = boolean("arena.tui.auto-follow", true),
                maxTrackedMatches = int("arena.tui.max-tracked-matches", 50),
                maxBufferedEventsPerMatch = int("arena.tui.max-buffered-events-per-match", 200),
                historicalImportRecords = int("arena.tui.historical-import-records", 5000).coerceAtLeast(0),
                initialReplaySpeed = double("arena.tui.initial-replay-speed", 1.0),
                consumerGroupId = string("arena.tui.consumer-group-id", "arena-tui-mordant")
            )
        }
    }
}
