import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlin.properties.Delegates
import kotlin.random.Random

/**
 * Returns last bits of SHA-256 hash for a given string as a Long
 * @param[str] string for which we want to get hash
 *
 */
fun stringHash(str: String): Long {
    val bytes = MessageDigest.getInstance("SHA-256").digest(str.toByteArray(Charsets.UTF_8))
    return bytes.fold(0) { acc, e -> 256 * acc + e.toLong() }
}

class Database(val databaseName: String) {
    val content = RandomAccessFile(databaseName + ".content", "rw")
    val index = RandomAccessFile(databaseName + ".index", "rw")

    private fun addString(str: String) : Long {
        content.seek(content.length())
        return content.length().also{ content.writeUTF(str) }
    }
    private fun getString(pos: Long) : String {
        if (pos >= content.length()) {
            throw IndexOutOfBoundsException("Indexed content file of length ${content.length()} with $pos")
        }
        content.seek(pos)
        return content.readUTF()
    }

    private fun setLong(pos: Long, value: Long) {
        index.seek(pos)
        index.writeLong(value.toLong())
    }
    private fun getLong(pos: Long) : Long {
        if (pos >= index.length()) {
            throw IndexOutOfBoundsException("Indexed index file of length ${index.length()} with $pos")
        }
        index.seek(pos)
        return content.readLong().toLong()
    }

    val BYTES_IN_LONG = 8
    val NO_KEY = 0L
    val DELETED = 1L

    val FIELDS_COUNT = 3L
    val KEY_HASH = 0L
    val KEY_POS = 1L
    val VALUE_POS = 2L

    fun tableSize() = index.length() / (FIELDS_COUNT * BYTES_IN_LONG)
    var freeCells = 0L

    private fun itemsIndex() : List<Pair<Long, Long>> {
        val buffer = mutableListOf<Pair<Long, Long>>()
        for (i in 0 until tableSize()) {
            val keyPos = getLong(i * FIELDS_COUNT + KEY_POS)
            val valuePos = getLong(i * FIELDS_COUNT + VALUE_POS)
            buffer.add(Pair(keyPos, valuePos))
        }
        return buffer
    }
    fun items() : List<Pair<String, String>> = itemsIndex().map{ (i, j) -> Pair(getString(i), getString(j))}

    private fun findIndex(key: String, acceptDeleted: Boolean) : Long {
        val keyHash = stringHash(key)
        fun checkIndex(i: Long) : Boolean {
            when (getLong(i * FIELDS_COUNT + KEY_HASH)) {
                keyHash -> return true
                NO_KEY -> return true
                DELETED -> return acceptDeleted
                else -> return false
            }
        }
        val startPos = keyHash % tableSize()
        for (i in startPos until tableSize())
            if (checkIndex(i))
                return i
        for (i in 0 until startPos)
            if (checkIndex(i))
                return i
        throw Exception("Database overflow, nothing found in findIndex(key = $key, acceptDeleted = $acceptDeleted)")
    }
    private fun setIndex(keyPos: Long, valuePos: Long) {
        val key = getString(keyPos)
        val i = findIndex(key, true)
        when (getLong(i * FIELDS_COUNT + KEY_HASH)) {
            NO_KEY -> {
                freeCells--
                setLong(i * FIELDS_COUNT + KEY_HASH, stringHash(key))
                setLong(i * FIELDS_COUNT + KEY_POS, keyPos)
                setLong(i * FIELDS_COUNT + VALUE_POS, valuePos)
            }
            DELETED -> {
                throw Exception("Deleted objects aren't expected in setByIndex")
            }
            else -> {
                throw Exception("Overwriting isn't expected in setByIndex")
            }
        }
        if (freeCells < tableSize() / 2) {
            resize()
        }
    }
    fun set(key: String, value: String) {
        val i = findIndex(key, true)
        when (getLong(i * FIELDS_COUNT + KEY_HASH)) {
            NO_KEY, DELETED -> {
                freeCells--
                setLong(i * FIELDS_COUNT + KEY_HASH, stringHash(key))
                setLong(i * FIELDS_COUNT + KEY_POS, addString(key))
                setLong(i * FIELDS_COUNT + VALUE_POS, addString(value))
            }
            else -> {
                setLong(i * FIELDS_COUNT + VALUE_POS, addString(value))
            }
        }
    }
    fun find(key: String) : String {
        val i = findIndex(key, false)
        if (getLong(i * FIELDS_COUNT + KEY_HASH) == NO_KEY) {
            return ""
        }
        return getString(getLong(i * FIELDS_COUNT + VALUE_POS))
    }
    fun del(key: String) {
        val i = findIndex(key, false)
        setLong(i * FIELDS_COUNT + KEY_HASH, DELETED)
    }
    fun resize() {
        val newSize = tableSize() * 2
        val save = itemsIndex()
        index.setLength(FIELDS_COUNT * BYTES_IN_LONG * newSize)
        for (i in 0 until tableSize()) {
            setLong(i * FIELDS_COUNT + KEY_HASH, NO_KEY)
        }
        freeCells = tableSize()
        for ((key, value) in save) {
            setIndex(key, value)
        }
    }
}

fun main(args: Array<String>) {

}
