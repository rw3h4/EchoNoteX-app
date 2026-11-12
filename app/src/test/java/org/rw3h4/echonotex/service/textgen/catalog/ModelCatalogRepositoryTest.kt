package org.rw3h4.echonotex.service.textgen.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ModelCatalogRepositoryTest {

    @Test
    fun validCatalogParses() {
        val catalog = ModelCatalogRepository.parseAndValidate(VALID_JSON)
        assertEquals(1, catalog.version)
        assertEquals(1, catalog.models.size)
        val entry = catalog.models[0]
        assertEquals("qwen2.5-1.5b-instruct-q4km", entry.id)
        assertEquals("qwen2.5-1.5b-instruct-q4_k_m.gguf", entry.filename)
        assertEquals(1117320736L, entry.sizeBytes)
        assertEquals(2048, entry.contextDefault)
        assertEquals("qwen2", entry.chatTemplateId)
    }

    @Test
    fun missingFieldThrowsMalformed() {
        val json = """
            {
              "version": 1,
              "models": [
                {
                  "id": "x",
                  "name": "X",
                  "description": "d",
                  "url": "https://example.com/x.gguf",
                  "filename": "x.gguf",
                  "size_bytes": 100,
                  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "quant": "Q4_K_M",
                  "min_ram_mb": 1024,
                  "context_default": 2048
                }
              ]
            }
        """.trimIndent()
        // chat_template_id missing → SerializationException → Malformed
        assertThrows(ModelCatalogException.Malformed::class.java) {
            ModelCatalogRepository.parseAndValidate(json)
        }
    }

    @Test
    fun duplicateIdThrows() {
        val json = """
            {
              "version": 1,
              "models": [
                ${entryJson(id = "dup")},
                ${entryJson(id = "dup")}
              ]
            }
        """.trimIndent()
        assertThrows(ModelCatalogException.DuplicateId::class.java) {
            ModelCatalogRepository.parseAndValidate(json)
        }
    }

    @Test
    fun malformedJsonThrowsMalformed() {
        assertThrows(ModelCatalogException.Malformed::class.java) {
            ModelCatalogRepository.parseAndValidate("{ this is not json")
        }
    }

    @Test
    fun unsupportedVersionThrows() {
        val json = """
            {
              "version": 2,
              "models": [ ${entryJson(id = "x")} ]
            }
        """.trimIndent()
        assertThrows(ModelCatalogException.UnsupportedVersion::class.java) {
            ModelCatalogRepository.parseAndValidate(json)
        }
    }

    @Test
    fun emptyModelsThrows() {
        val json = """{"version": 1, "models": []}"""
        assertThrows(ModelCatalogException.Empty::class.java) {
            ModelCatalogRepository.parseAndValidate(json)
        }
    }

    @Test
    fun nonHttpsUrlThrowsInvalidField() {
        val json = """
            {
              "version": 1,
              "models": [ ${entryJson(id = "x", url = "http://insecure.example.com/x.gguf")} ]
            }
        """.trimIndent()
        assertThrows(ModelCatalogException.InvalidField::class.java) {
            ModelCatalogRepository.parseAndValidate(json)
        }
    }

    @Test
    fun badSha256ThrowsInvalidField() {
        val json = """
            {
              "version": 1,
              "models": [ ${entryJson(id = "x", sha256 = "not-a-real-hash")} ]
            }
        """.trimIndent()
        assertThrows(ModelCatalogException.InvalidField::class.java) {
            ModelCatalogRepository.parseAndValidate(json)
        }
    }

    private fun entryJson(
        id: String,
        url: String = "https://example.com/x.gguf",
        sha256: String = "a".repeat(64),
    ): String = """
        {
          "id": "$id",
          "name": "X",
          "description": "d",
          "url": "$url",
          "filename": "x.gguf",
          "size_bytes": 100,
          "sha256": "$sha256",
          "quant": "Q4_K_M",
          "min_ram_mb": 1024,
          "context_default": 2048,
          "chat_template_id": "qwen2"
        }
    """.trimIndent()

    companion object {
        private val VALID_JSON = """
            {
              "version": 1,
              "models": [
                {
                  "id": "qwen2.5-1.5b-instruct-q4km",
                  "name": "Qwen2.5 1.5B",
                  "description": "Compact instruction-tuned model.",
                  "url": "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                  "filename": "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                  "size_bytes": 1117320736,
                  "sha256": "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
                  "quant": "Q4_K_M",
                  "min_ram_mb": 3072,
                  "context_default": 2048,
                  "chat_template_id": "qwen2"
                }
              ]
            }
        """.trimIndent()
    }
}
