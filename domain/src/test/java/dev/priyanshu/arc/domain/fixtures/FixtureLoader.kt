package dev.priyanshu.arc.domain.fixtures

import kotlinx.serialization.json.Json
import java.io.File

// Reads every *.json in test resources/fixtures (populated by the
// :domain:copyFixtures Gradle task from contract/fixtures). README.md lives
// in that folder too and is skipped since it isn't JSON.
object FixtureLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadAll(): List<FixtureDto> {
        val resourceUrl = requireNotNull(javaClass.classLoader.getResource("fixtures")) {
            "fixtures/ not found on the test classpath — did :domain:copyFixtures run?"
        }
        val fixturesDir = File(resourceUrl.toURI())
        return fixturesDir.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file -> json.decodeFromString(FixtureDto.serializer(), file.readText()) }
    }
}
