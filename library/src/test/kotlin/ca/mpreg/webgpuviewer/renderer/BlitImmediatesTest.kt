package ca.mpreg.webgpuviewer.renderer

import org.junit.Assert.assertEquals
import org.junit.Test

/** The clip a parted spread rewrites alone lands where the blit shader reads FrameParams.clip. */
class BlitImmediatesTest {

    /** Byte offset of [field] in [struct]'s WGSL declaration, by WGSL's alignment of f32 and vectors of it. */
    private fun offsetOf(shader: String, struct: String, field: String): Int {
        val body = shader.substringAfter("struct $struct {").substringBefore("}")
        var offset = 0
        for (line in body.lines().map { it.substringBefore("//").trim() }.filter { ':' in it }) {
            val name = line.substringBefore(':').trim()
            val (size, align) = when (line.substringAfter(':').trim().removeSuffix(",")) {
                "f32" -> 4 to 4
                "vec2<f32>" -> 8 to 8
                "vec4<f32>" -> 16 to 16
                else -> error("unhandled type in $line")
            }
            offset = (offset + align - 1) / align * align
            if (name == field) return offset
            offset += size
        }
        error("no $field in $struct")
    }

    @Test
    fun theClipOffsetIsWhereTheShaderDeclaresIt() {
        assertEquals(offsetOf(BLIT_SHADER, "FrameParams", "clip"), CLIP_IMMEDIATE_OFFSET)
    }
}
