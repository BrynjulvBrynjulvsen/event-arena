package io.practicegroup.arena.tui

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ArenaTuiCliApplication

fun main(args: Array<String>) {
    runApplication<ArenaTuiCliApplication>(*args)
}
