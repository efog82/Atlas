package pt.atlas.personal

import org.junit.Assert.*
import org.junit.Test
import pt.atlas.personal.ai.ModelStore
import java.io.File

class LiteRtModelFormatTest {
    @Test fun rejectsTinyOrRenamedGguf() {
        val file = File.createTempFile("atlas-litert", ".litertlm")
        try {
            file.writeBytes(ByteArray(2048))
            assertThrows(IllegalArgumentException::class.java) {
                ModelStore.validateLiteRtLm(file)
            }
            file.outputStream().use { stream ->
                stream.write(byteArrayOf(0x47, 0x47, 0x55, 0x46))
                stream.write(ByteArray(1024 * 1024 + 64))
            }
            assertThrows(IllegalArgumentException::class.java) {
                ModelStore.validateLiteRtLm(file)
            }
        } finally {
            file.delete()
        }
    }

    @Test fun acceptsCandidateForNativeValidation() {
        val file = File.createTempFile("atlas-litert", ".litertlm")
        try {
            file.writeBytes(ByteArray(1024 * 1024 + 1))
            // Structural screening is not proof the runtime can load this candidate.
            ModelStore.validateLiteRtLm(file)
        } finally {
            file.delete()
        }
    }
}
