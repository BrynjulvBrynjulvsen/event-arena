package io.practicegroup.arena.engine

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class ArenaEngineApplication

fun main(args: Array<String>) {
    runApplication<ArenaEngineApplication>(*args)
}
