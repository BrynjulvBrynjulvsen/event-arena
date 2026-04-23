package io.practicegroup.arena.tui.mordant

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

class KafkaIngestionServiceTest {

    @Test
    fun `historical import selects latest global records across partitions and resets to live end`() = runBlocking {
        val lifecycle0 = TopicPartition("arena.match-lifecycle.v1", 0)
        val events1 = TopicPartition("arena.match-events.v1", 1)
        val consumer = MockConsumer<String, Any>(OffsetResetStrategy.EARLIEST)
        consumer.subscribe(listOf(lifecycle0.topic(), events1.topic()))
        consumer.rebalance(listOf(lifecycle0, events1))
        consumer.updateBeginningOffsets(mapOf(lifecycle0 to 0L, events1 to 0L))
        consumer.updateEndOffsets(mapOf(lifecycle0 to 3L, events1 to 2L))
        consumer.addRecord(matchStartedRecord(lifecycle0, 0L, timestamp = 1000L, matchId = "match-1"))
        consumer.addRecord(matchStartedRecord(lifecycle0, 1L, timestamp = 3000L, matchId = "match-2"))
        consumer.addRecord(matchStartedRecord(lifecycle0, 2L, timestamp = 5000L, matchId = "match-3"))
        consumer.addRecord(matchEndedRecord(events1, 0L, timestamp = 2000L, matchId = "match-1"))
        consumer.addRecord(matchEndedRecord(events1, 1L, timestamp = 4000L, matchId = "match-2"))

        val service = KafkaIngestionService(
            config = MordantTuiConfig(historicalImportRecords = 3),
            parser = ArenaEventParser(
                JsonMapper.builder()
                    .addModule(kotlinModule())
                    .build()
            )
        )
        val out = Channel<AppEvent>(Channel.UNLIMITED)

        service.importHistoricalRecords(consumer, out)

        val events = drain(out).filterIsInstance<AppEvent.Consumed>().map { it.event }
        assertEquals(listOf("match-2", "match-2", "match-3"), events.map { it.matchId })
        assertEquals(listOf(3000L, 4000L, 5000L), events.map { it.occurredAt.toEpochMilli() })
        assertEquals(3L, consumer.position(lifecycle0))
        assertEquals(2L, consumer.position(events1))
    }

    @Test
    fun `historical record selection keeps the latest timestamps`() {
        val partition = TopicPartition("arena.match-events.v1", 0)
        val records = listOf(
            matchStartedRecord(partition, 0L, timestamp = 1000L, matchId = "match-1"),
            matchStartedRecord(partition, 1L, timestamp = 2000L, matchId = "match-2"),
            matchStartedRecord(partition, 2L, timestamp = 3000L, matchId = "match-3")
        )

        val selected = KafkaIngestionService.selectHistoricalRecords(records, 2)

        assertEquals(listOf("match-2", "match-3"), selected.map { it.key() })
    }

    @Test
    fun `historical start offset is clamped at partition beginning`() {
        assertEquals(25L, KafkaIngestionService.historicalStartOffset(beginningOffset = 25L, endOffset = 27L, maxRecords = 5000))
        assertEquals(90L, KafkaIngestionService.historicalStartOffset(beginningOffset = 0L, endOffset = 100L, maxRecords = 10))
    }

    private fun matchStartedRecord(
        partition: TopicPartition,
        offset: Long,
        timestamp: Long,
        matchId: String
    ): ConsumerRecord<String, Any> {
        return record(
            topicPartition = partition,
            offset = offset,
            timestamp = timestamp,
            matchId = matchId,
            value = mapOf(
                "eventType" to "MatchStarted",
                "turn" to 0,
                "occurredAt" to iso(timestamp),
                "payload" to mapOf(
                    "fighterAId" to "blue",
                    "fighterBId" to "red"
                )
            )
        )
    }

    private fun matchEndedRecord(
        partition: TopicPartition,
        offset: Long,
        timestamp: Long,
        matchId: String
    ): ConsumerRecord<String, Any> {
        return record(
            topicPartition = partition,
            offset = offset,
            timestamp = timestamp,
            matchId = matchId,
            value = mapOf(
                "eventType" to "MatchEnded",
                "turn" to 1,
                "occurredAt" to iso(timestamp),
                "payload" to mapOf(
                    "winnerFighterId" to "blue",
                    "loserFighterId" to "red"
                )
            )
        )
    }

    private fun record(
        topicPartition: TopicPartition,
        offset: Long,
        timestamp: Long,
        matchId: String,
        value: Any
    ): ConsumerRecord<String, Any> {
        return ConsumerRecord(
            topicPartition.topic(),
            topicPartition.partition(),
            offset,
            timestamp,
            TimestampType.CREATE_TIME,
            matchId.length,
            0,
            matchId,
            value,
            RecordHeaders(),
            Optional.empty()
        )
    }

    private fun iso(timestamp: Long): String {
        return java.time.Instant.ofEpochMilli(timestamp).toString()
    }

    private fun drain(channel: Channel<AppEvent>): List<AppEvent> {
        val events = mutableListOf<AppEvent>()
        while (true) {
            val result = channel.tryReceive().getOrNull() ?: break
            events += result
        }
        assertTrue(events.isNotEmpty())
        return events
    }
}
