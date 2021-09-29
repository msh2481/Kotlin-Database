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

val MAX_STRING_SIZE = 32
val MAX_NODE_SIZE = 64
val file = RandomAccessFile("file.txt", "rw")
var firstFreeOffset = 0

inline fun <reified NodeType> readNode(offset: Int) : NodeType {
    val buffer = ByteArray(MAX_NODE_SIZE)
    try {
        file.seek(offset.toLong())
        file.read(buffer)
//        file.readFully(buffer, offset, MAX_NODE_SIZE)
    } catch (e: IOError) {
        println(e)
        println("Failed to read bytes at [$offset; $offset + $MAX_NODE_SIZE) ")
    }
    return Json.decodeFromString(buffer.toString())
}

inline fun <reified NodeType> setValue(offset: Int, node: NodeType) : Unit {
    val buffer = Json.encodeToString(node).toByteArray().copyOf(MAX_NODE_SIZE)
    file.seek(offset.toLong())
    file.writeBytes(buffer.toString())
//    file.write(buffer, offset, MAX_NODE_SIZE)
}

@Serializable
class ListNode(val offset: Int) {
    fun save() = setValue(offset, this)
    private var nextAddress: Int? = null
    var next: ListNode?
        get() {
            return nextAddress?.let{ readNode(it) }
        }
        set(value) {
            nextAddress = value?.offset
            save()
        }
    var data: String = ""
        set(value: String) {
            field = value
            save()
        }
}

class StringList {
    var root: ListNode? = null
    var head: ListNode? = null

    fun addChunk(chunk: String) {
        assert(chunk.length <= MAX_STRING_SIZE)
        val offset = firstFreeOffset++
        val newNode = ListNode(offset)
        newNode.data = chunk
        if (head != null) {
            head?.next = newNode
            head = newNode
        } else {
            root = newNode
            head = newNode
        }
    }
    fun add(str: String) = str.chunked(MAX_STRING_SIZE).forEach{ addChunk(it) }
    override fun toString() : String{
        return buildString {
            var v : ListNode? = root
            while (v != null) {
                append(v.data)
                v = v.next
            }
        }
    }
}

fun main(args: Array<String>) {
    println("Hello")
    val l = StringList()
    l.add("kekdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd")
    println(l.root)
    println(l)
    println("Bye")
}
