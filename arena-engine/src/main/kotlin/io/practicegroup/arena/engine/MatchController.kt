package io.practicegroup.arena.engine

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/matches")
class MatchController(
    private val matchOrchestrator: MatchOrchestrator
) {
    @PostMapping
    fun startMatch(@RequestBody(required = false) request: StartMatchRequest?): StartMatchResponse {
        return matchOrchestrator.startMatch(request ?: StartMatchRequest())
    }
}
