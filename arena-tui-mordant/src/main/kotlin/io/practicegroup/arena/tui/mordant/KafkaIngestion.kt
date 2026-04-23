package io.practicegroup.arena.tui.mordant

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializerConfig
import kotlinx.coroutines.channels.SendChannel
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.TopicPartition
import java.time.Duration
import java.util.Properties
import java.util.UUID

sealed interface AppEvent {
    data class Consumed(val event: ParsedArenaEvent) : AppEvent
    data class Fatal(val message: String, val cause: Throwable? = null) : AppEvent
}

class KafkaIngestionService(
    private val config: MordantTuiConfig,
    private val parser: ArenaEventParser,
    private val adminFactory: () -> Admin = { Admin.create(adminProperties(config)) },
    private val consumerFactory: () -> Consumer<String, Any> = { KafkaConsumer(consumerProperties(config)) }
) {
    suspend fun run(out: SendChannel<AppEvent>) {
        runCatching {
            adminFactory().use { admin ->
                admin.describeTopics(listOf(config.lifecycleTopic, config.matchEventsTopic)).allTopicNames().get()
            }
        }.onFailure { out.send(AppEvent.Fatal("Kafka topic check failed", it)) }

        runCatching {
            consumerFactory().use { consumer ->
                consumer.subscribe(listOf(config.lifecycleTopic, config.matchEventsTopic))
                awaitAssignment(consumer)
                importHistoricalRecords(consumer, out)
                while (!out.isClosedForSend) {
                    val records = consumer.poll(Duration.ofMillis(250))
                    emitParsedRecords(records, out)
                }
            }
        }.onFailure { out.send(AppEvent.Fatal("Kafka consumption failed", it)) }
    }

    internal suspend fun importHistoricalRecords(consumer: Consumer<String, Any>, out: SendChannel<AppEvent>) {
        if (config.historicalImportRecords <= 0) return
        val assignments = consumer.assignment()
        if (assignments.isEmpty()) return

        val beginningOffsets = consumer.beginningOffsets(assignments)
        val endOffsets = consumer.endOffsets(assignments)
        assignments.forEach { partition ->
            consumer.seek(
                partition,
                historicalStartOffset(
                    beginningOffset = beginningOffsets[partition] ?: 0L,
                    endOffset = endOffsets[partition] ?: 0L,
                    maxRecords = config.historicalImportRecords
                )
            )
        }

        val imported = mutableListOf<ConsumerRecord<String, Any>>()
        while (assignments.any { partition -> consumer.position(partition) < (endOffsets[partition] ?: 0L) }) {
            val records = consumer.poll(Duration.ofMillis(250))
            records.forEach { record ->
                val partition = TopicPartition(record.topic(), record.partition())
                val endOffset = endOffsets[partition] ?: return@forEach
                if (record.offset() < endOffset) {
                    imported += record
                }
            }
        }

        selectHistoricalRecords(imported, config.historicalImportRecords).forEach { record ->
            emitParsedRecord(record, out)
        }

        assignments.forEach { partition ->
            consumer.seek(partition, endOffsets[partition] ?: 0L)
        }
    }

    private suspend fun emitParsedRecords(records: Iterable<ConsumerRecord<String, Any>>, out: SendChannel<AppEvent>) {
        records.forEach { record -> emitParsedRecord(record, out) }
    }

    private suspend fun emitParsedRecord(record: ConsumerRecord<String, Any>, out: SendChannel<AppEvent>) {
        val parsed = runCatching { parser.parse(record.key(), record.value()) }.getOrNull() ?: return
        out.send(AppEvent.Consumed(parsed))
    }

    private fun awaitAssignment(consumer: Consumer<String, Any>) {
        repeat(20) {
            consumer.poll(Duration.ofMillis(250))
            if (consumer.assignment().isNotEmpty()) return
        }
        error("Kafka consumer did not receive any partition assignment")
    }

    companion object {
        internal fun historicalStartOffset(beginningOffset: Long, endOffset: Long, maxRecords: Int): Long {
            return (endOffset - maxRecords).coerceAtLeast(beginningOffset)
        }

        internal fun selectHistoricalRecords(
            records: List<ConsumerRecord<String, Any>>,
            maxRecords: Int
        ): List<ConsumerRecord<String, Any>> {
            if (maxRecords <= 0) return emptyList()
            return records
                .sortedWith(
                    compareBy<ConsumerRecord<String, Any>> { it.timestamp() }
                        .thenBy { it.topic() }
                        .thenBy { it.partition() }
                        .thenBy { it.offset() }
                )
                .takeLast(maxRecords)
        }

        private fun adminProperties(config: MordantTuiConfig): Properties {
            return Properties().apply {
                put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers)
                put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, config.schemaRegistryUrl)
            }
        }

        private fun consumerProperties(config: MordantTuiConfig): Properties {
            return Properties().apply {
                put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers)
                put(ConsumerConfig.GROUP_ID_CONFIG, "${config.consumerGroupId}-${UUID.randomUUID()}")
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
                put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
                put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonSchemaDeserializer::class.java.name)
                put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, config.schemaRegistryUrl)
                put(KafkaJsonSchemaDeserializerConfig.JSON_VALUE_TYPE, Any::class.java.name)
            }
        }
    }
}
