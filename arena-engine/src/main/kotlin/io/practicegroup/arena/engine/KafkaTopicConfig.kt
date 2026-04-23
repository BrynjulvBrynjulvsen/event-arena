package io.practicegroup.arena.engine

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaTopicConfig {
    @Bean
    fun matchEventsTopic(
        @Value($$"${arena.kafka.topic}") topicName: String
    ): NewTopic {
        return TopicBuilder.name(topicName)
            .partitions(6)
            .replicas(1)
            .build()
    }

    @Bean
    fun lifecycleTopic(
        @Value($$"${arena.kafka.lifecycle-topic}") topicName: String
    ): NewTopic {
        return TopicBuilder.name(topicName)
            .partitions(6)
            .replicas(1)
            .build()
    }
}
