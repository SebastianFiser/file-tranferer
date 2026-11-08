package org.dpdns.sebastianfiser.filetransferer.network

import net.jpountz.lz4.LZ4Factory
import java.nio.ByteBuffer

class EncodedChunk(val flags: Byte, val buffer: ByteArray, val length: Int)

class Lz4ChunkEncoder {
    companion object {
        const val FLAG_LZ4 = 0x02
        const val FLAG_LAST = 0x01
    }

    private val compressor = LZ4Factory.fastestJavaInstance().fastCompressor()
    private var scratch = ByteArray(0)

    fun encode(src: ByteArray, len: Int): EncodedChunk {
        val needed = 4 + compressor.maxCompressedLength(len)
        if (scratch.size < needed) sctratch = ByteArray(needed)

        ByteBuffer.wrap(scratch, 0, 4).putInt(len)
        val compressedLen = compressor.compress(src, 0, len, scratch, 4, scratch.size - 4)

        return if (4 + compressedLen < len) {
            EncodeChunk(FLAG_LZ4, scratch, 4 + compressedLen)
        } else {
            EncodeChunk(0, src, len)
        }
    }

}