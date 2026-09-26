package dev.priyanshu.arc.domain.fixtures

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

// FixtureLoader decodes with ignoreUnknownKeys = true so a fixture can carry a
// human-readable `note` (and other top-level scaffolding). The cost is that a
// misspelled expect key — `current_streek` — is silently ignored, asserts
// nothing, and passes. This guards against exactly that: every key inside a
// fixture's `expect` block must be one the harness recognises.
//
// The allowed set is derived from ExpectDto's @SerialName values via its
// serializer descriptor, not a hand-maintained list, so adding a new expect
// field needs no change here. Only `expect` is checked — top-level keys (id,
// note, rules, …) are deliberately allowed (docs/tasks/13).
class FixtureExpectKeysTest {

    private val allowedExpectKeys: Set<String> =
        (0 until ExpectDto.serializer().descriptor.elementsCount)
            .map { ExpectDto.serializer().descriptor.getElementName(it) }
            .toSet()

    @TestFactory
    fun `every expect key is one the harness recognises`(): List<DynamicTest> {
        val resourceUrl = requireNotNull(javaClass.classLoader.getResource("fixtures")) {
            "fixtures/ not found on the test classpath — did :domain:copyFixtures run?"
        }
        val files = File(resourceUrl.toURI())
            .listFiles { file -> file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }

        return files.map { file ->
            DynamicTest.dynamicTest(file.nameWithoutExtension) {
                val root = Json.parseToJsonElement(file.readText()).jsonObject
                val expect = root["expect"]?.jsonObject ?: return@dynamicTest
                val unknown = expect.keys - allowedExpectKeys
                assertTrue(
                    unknown.isEmpty(),
                    "${file.name}: unrecognised expect key(s) $unknown — a typo, or a field " +
                        "ExpectDto does not declare. Allowed: $allowedExpectKeys",
                )
            }
        }
    }
}
