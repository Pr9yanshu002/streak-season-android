package dev.priyanshu.arc.domain.fixtures

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun allowedKeys(serializer: kotlinx.serialization.KSerializer<*>): Set<String> =
        (0 until serializer.descriptor.elementsCount)
            .map { serializer.descriptor.getElementName(it) }
            .toSet()

    private val allowedExpectKeys = allowedKeys(ExpectDto.serializer())
    private val allowedRecapKeys = allowedKeys(RecapExpectDto.serializer())
    private val allowedHardestWeekKeys = allowedKeys(HardestWeekExpectDto.serializer())
    private val allowedCommitmentTotalKeys = allowedKeys(CommitmentTotalExpectDto.serializer())

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
                assertNoUnknownKeys(file.name, "expect", expect, allowedExpectKeys)

                // recap is nested, so the top-level check above cannot see inside
                // it — a typo like `recap.best_streek` would pass. Recurse into the
                // recap block and its nested objects.
                val recap = expect["recap"]?.jsonObject ?: return@dynamicTest
                assertNoUnknownKeys(file.name, "expect.recap", recap, allowedRecapKeys)

                (recap["hardest_week"] as? JsonObject)?.let {
                    assertNoUnknownKeys(file.name, "expect.recap.hardest_week", it, allowedHardestWeekKeys)
                }
                (recap["commitment_totals"] as? JsonObject)?.forEach { (id, total) ->
                    (total as? JsonObject)?.let {
                        assertNoUnknownKeys(file.name, "expect.recap.commitment_totals[$id]", it, allowedCommitmentTotalKeys)
                    }
                }
            }
        }
    }

    private fun assertNoUnknownKeys(fileName: String, path: String, obj: JsonObject, allowed: Set<String>) {
        val unknown = obj.keys - allowed
        assertTrue(
            unknown.isEmpty(),
            "$fileName: unrecognised $path key(s) $unknown — a typo, or a field the DTO does " +
                "not declare. Allowed: $allowed",
        )
    }
}
