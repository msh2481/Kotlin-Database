import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlin.properties.Delegates
import kotlin.random.Random

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
fun stringHash(str: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(str.toByteArray(Charsets.UTF_8))


val MAX_STRING_SIZE = 32
val MAX_NODE_SIZE = 128
var file = RandomAccessFile("file.txt", "rw")

object Allocator {
    private var firstFreeOffset = 0
    fun getFreeOffset() : Int {
        return firstFreeOffset.also{ firstFreeOffset += MAX_NODE_SIZE }
    }
}


inline fun <reified NodeType> readNode(offset: Int) : NodeType {
    val buffer = ByteArray(MAX_NODE_SIZE)
    try {
        file.seek(offset.toLong())
        file.readFully(buffer, 0, MAX_NODE_SIZE)
    } catch (e: Exception) {
        println(e)
        println("Failed to read bytes at [$offset; $offset + $MAX_NODE_SIZE) ")
    }

    return Json.decodeFromString(buffer.decodeToString().trimEnd(Char(0)))
}

inline fun <reified NodeType> setValue(offset: Int, node: NodeType) : Unit {
    println("Trying to write bytes at [$offset; $offset + $MAX_NODE_SIZE), data = ${Json.encodeToString(node)}")
    val buffer = Json.encodeToString(node).toByteArray().copyOf(MAX_NODE_SIZE)
    try {
        file.seek(offset.toLong())
        file.write(buffer, 0, MAX_NODE_SIZE)
    } catch (e: Exception) {
        println(e)
        println("Failed to write bytes at [$offset; $offset + $MAX_NODE_SIZE) ")
    }
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
    var data: String by Delegates.observable("") { _, _, _ -> save() }
    init {
        save()
    }
}

fun stringToList(str: String) : ListNode? {
    var root: ListNode? = null
    fun addChunk(chunk: String) {
        assert(chunk.length <= MAX_STRING_SIZE)
        val newNode = ListNode(Allocator.getFreeOffset())
        newNode.next = root
        root = newNode
    }
    str.chunked(MAX_STRING_SIZE).reversed().forEach{ addChunk(it) }
    return root
}

fun listToString(v: ListNode?) : String {
    return buildString {
        while (v != null) {
            append(v.data)
            v = v.next
        }
    }
}

@Serializable
class TreapNode(val offset: Int) {
    fun save() = setValue(offset, this)
    private var leftAddress: Int? = null
    private var rightAddress: Int? = null
    private var keyListAddress: Int? = null
    private var valueListAddress: Int? = null
    var left: TreapNode?
        get() {
            return leftAddress?.let{ readNode(it) }
        }
        set(value) {
            leftAddress = value?.offset
            save()
        }
    var right: TreapNode?
        get() {
            return rightAddress?.let{ readNode(it) }
        }
        set(value) {
            rightAddress = value?.offset
            save()
        }
    var keyList: ListNode?
        get() {
            return keyListAddress?.let{ readNode(it) }
        }
        set(value) {
            keyListAddress = value?.offset
            save()
        }
    var valueList: ListNode?
        get() {
            return valueListAddress?.let{ readNode(it) }
        }
        set(value) {
            valueListAddress = value?.offset
            save()
        }
    var keyHash: String by Delegates.observable("") { _, _, _ -> save() }
    var priority: Int by Delegates.observable(0) { _, _, _ -> save() }
    init {
        save()
    }
}

@ExperimentalUnsignedTypes
fun allocateTreapNode(key: String, value: String) : TreapNode {
    val v = TreapNode(Allocator.getFreeOffset())
    v.keyList = stringToList(key)
    v.valueList = stringToList(value)
    v.keyHash = stringHash(key).toHex()
    v.priority = Random.nextInt()
    return v
}

fun merge(left: TreapNode?, right: TreapNode?) : TreapNode? {
    if (left == null) {
        return right
    }
    if (right == null) {
        return left
    }
    if (left.priority < right.priority) {
        left.right = merge(left.right, right)
        return left
    } else {
        right.left = merge(left, right.left)
        return right
    }
}

/**
 * Splits treap into two parts with keyHashes in (-inf, keyHash] and [keyHash + 1, +inf)
 *
 * @param[tree] root of treap to split
 * @param[keyHash] max keyHash to leave in left resulting tree
 */

fun split(tree: TreapNode?, keyHash: String) : Pair<TreapNode?, TreapNode?> {
    if (tree == null) {
        return Pair(null, null)
    }
    if (keyHash < tree.keyHash) {
        val (leftRes, treeLeft) = split(tree.left, keyHash)
        tree.left = treeLeft
        return Pair(leftRes, tree)
    } else {
        val (treeRight, rightRes) = split(tree.right, keyHash)
        tree.right = treeRight
        return Pair(treeRight, rightRes)
    }
}

fun insert(tree: TreapNode?, newNode: TreapNode) : TreapNode? {
    assert(newNode.left == null)
    assert(newNode.right == null)
    if (tree == null) {
        return newNode
    }
    when {
        newNode.priority < tree.priority -> {
            val (splitLeft, splitRight) = split(tree, newNode.keyHash)
            newNode.left = splitLeft
            newNode.right = splitRight
            return newNode
        }
        newNode.keyHash < tree.keyHash -> {
            tree.left = insert(tree.left, newNode)
            return tree
        }
        newNode.keyHash > tree.keyHash -> {
            tree.right = insert(tree.right, newNode)
            return tree
        }
        else -> assert(false)
    }
}



fun main(args: Array<String>) {
    val l = StringList()
    l.add("abc}}}{{{<><}}}")
    println(l)
}
