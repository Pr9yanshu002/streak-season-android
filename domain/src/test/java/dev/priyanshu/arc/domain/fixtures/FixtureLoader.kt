package dev.priyanshu.arc.domain.fixtures

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File

// Reads every *.json in test resources/fixtures (populated by the
// :domain:copyFixtures Gradle task from contract/fixtures). README.md lives
// in that folder too and is skipped since it isn't JSON.
//
// A fixture with a top-level "TODO" key is a placeholder Priyanshu hasn't
// written yet (docs/tasks/17). It's skipped, and its file name printed, so
// the rest of the suite still runs.
object FixtureLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadAll(): List<FixtureDto> {
        val resourceUrl = requireNotNull(javaClass.classLoader.getResource("fixtures")) {
            "fixtures/ not found on the test classpath — did :domain:copyFixtures run?"
        }
        val fixturesDir = File(resourceUrl.toURI())
        val files = fixturesDir.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }

        val fixtures = mutableListOf<FixtureDto>()
        for (file in files) {
            val text = file.readText()
            if ("TODO" in Json.parseToJsonElement(text).jsonObject) {
                println("Skipping placeholder fixture (has a TODO key): ${file.name}")
                continue
            }
            fixtures.add(json.decodeFromString(FixtureDto.serializer(), text))
        }
        return fixtures
    }
}
