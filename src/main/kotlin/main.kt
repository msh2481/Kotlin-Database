import java.io.IOError
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.reflect.KProperty
import kotlin.text.Charsets.UTF_8
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * Converts array of bytes to a hex string
 * @receiver array of bytes
 * @return string where each byte is encoded by 2 chars
 *
 * Usage:
 * assertEquals("010c13ac", byteArrayOf(1, 12, 19, -84).toHex())
 */
@ExperimentalUnsignedTypes
fun ByteArray.toHex(): String = asUByteArray().joinToString("") { it.toString(radix = 16).padStart(2, '0') }

/**
 * Returns SHA-256 hash for a given string as an array of 32 bytes
 * @param[str] string for which we want to get hash
 *
 * Usage:
 * assertEquals("315f5bdb76d078c43b8ac0064e4a0164612b1fce77c869345bfc94c75894edd3", stringHash("Hello, world!").toHex())
 */
fun stringHash(str: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(str.toByteArray(UTF_8))

class Database(filename: String) {
    val TEXT_BLOCK_SIZE = 32
    val MAX_NODE_SIZE = 64
    val file = RandomAccessFile(filename, "rw")

    inner class NodePointer(val offset: Int) {
        inline fun <reified NodeType> getValue(node: NodeType, property: KProperty<*>) : NodeType {
            val buffer = ByteArray(MAX_NODE_SIZE)
            try {
                file.readFully(buffer, offset, MAX_NODE_SIZE)
            } catch (e: IOError) {
                println(e)
                println("Failed to read bytes at [$offset; $offset + $MAX_NODE_SIZE) ")
            }
            return Json.decodeFromString(buffer.toString())

        }

        fun setValue(node: Any, property: KProperty<*>) : Unit {
            val buffer = Json.encodeToString(node).toByteArray().copyOf(MAX_NODE_SIZE)
            file.write(buffer, offset, MAX_NODE_SIZE)
        }
    }

    inner class ListNode(val offset: Int) {
        var nextPtr: NodePointer? = null
        fun next
    }
}

fun main(args: Array<String>) {
}
