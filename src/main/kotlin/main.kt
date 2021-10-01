import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.system.measureNanoTime

/** TODO
 * Measure number of tries
 * Select best hash
 * Add tests
 * Add docs
 */

/**
 * Returns last bits of SHA-256 hash for a given string as a Long
 * @param[str] string for which we want to get hash
 *
 */
fun stringHash(str: String): Long {
//    val bytes = MessageDigest.getInstance("SHA-256").digest(str.toByteArray(Charsets.UTF_8))
//    return bytes.fold(0) { acc, e -> 256 * acc + e.toLong() }
    return str.toLong()
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
//        println("setLong(pos = $pos, value = $value")
        index.seek(pos)
        index.writeLong(value)
    }
    private fun getLong(pos: Long) : Long {
        if (pos >= index.length()) {
            throw IndexOutOfBoundsException("Indexed index file of length ${index.length()} with $pos")
        }
        try {
            index.seek(pos)
            return index.readLong()
        } catch (e: Exception) {
            println("from getLong(pos = $pos), index file length = ${index.length()}")
            throw e
        }
    }

    val BYTES_IN_LONG = 8
    val FIELDS_COUNT = 3L

    val NO_KEY = 0L
    val DELETED = 1L

    val FIELDS_WIDTH = FIELDS_COUNT * BYTES_IN_LONG
    val KEY_HASH = 0 * BYTES_IN_LONG
    val KEY_POS = 1 * BYTES_IN_LONG
    val VALUE_POS = 2 * BYTES_IN_LONG

    fun tableSize() = index.length() / FIELDS_WIDTH
    var freeCells = 0L

    private fun itemsIndex() : List<Pair<Long, Long>> {
        val buffer = mutableListOf<Pair<Long, Long>>()
        for (i in 0 until tableSize()) {
            when (getLong(i * FIELDS_WIDTH + KEY_HASH)) {
                NO_KEY, DELETED -> continue
                else -> {
                    val keyPos = getLong(i * FIELDS_WIDTH + KEY_POS)
                    val valuePos = getLong(i * FIELDS_WIDTH + VALUE_POS)
                    buffer.add(Pair(keyPos, valuePos))
                }
            }
        }
        return buffer
    }
    fun items() : List<Pair<String, String>> = itemsIndex().map{ (i, j) -> Pair(getString(i), getString(j))}

    private fun findIndex(key: String, acceptDeleted: Boolean) : Long {
        val keyHash = stringHash(key)
        fun checkIndex(i: Long) : Boolean {
            when (getLong(i * FIELDS_WIDTH + KEY_HASH)) {
                keyHash -> return true
                NO_KEY -> return true
                DELETED -> return acceptDeleted
                else -> return false
            }
        }
        val startPos = (keyHash.toULong() % tableSize().toULong()).toLong()
        for (i in startPos until tableSize())
            if (checkIndex(i))
                return i
        for (i in 0 until startPos)
            if (checkIndex(i))
                return i
        throw Exception("Database overflow, nothing found in findIndex(key = $key, acceptDeleted = $acceptDeleted)")
    }
    private fun setIndex(keyPos: Long, valuePos: Long) {
        assert(keyPos >= 0 && valuePos >= 0)
        val key = getString(keyPos)
//        println("setIndex(key = $key, value = ${getString(valuePos)}")
        val i = findIndex(key, true)
        when (getLong(i * FIELDS_WIDTH + KEY_HASH)) {
            DELETED -> {
                throw Exception("Deleted objects aren't expected in setByIndex")
            }
            NO_KEY -> {
                freeCells--
                setLong(i * FIELDS_WIDTH + KEY_HASH, stringHash(key))
                setLong(i * FIELDS_WIDTH + KEY_POS, keyPos)
                setLong(i * FIELDS_WIDTH + VALUE_POS, valuePos)
            }
            else -> {
//                println(getLong(i * FIELDS_WIDTH + KEY_HASH))
//                println(getLong(i * FIELDS_WIDTH + KEY_POS))
//                println(getLong(i * FIELDS_WIDTH + VALUE_POS))
//                println("${stringHash(key)}, $keyPos, $valuePos")
                throw Exception("Overwriting isn't expected in setIndex")
            }
        }
    }
    fun set(key: String, value: String) {
//        println("set(key = $key, value = $value")
        val i = findIndex(key, true)
        when (getLong(i * FIELDS_WIDTH + KEY_HASH)) {
            NO_KEY, DELETED -> {
                freeCells--
                setLong(i * FIELDS_WIDTH + KEY_HASH, stringHash(key))
                setLong(i * FIELDS_WIDTH + KEY_POS, addString(key))
                setLong(i * FIELDS_WIDTH + VALUE_POS, addString(value))
            }
            else -> {
                setLong(i * FIELDS_WIDTH + VALUE_POS, addString(value))
            }
        }
        if (freeCells < tableSize() / 2) {
            resize()
        }
    }
    fun find(key: String) : String {
        val i = findIndex(key, false)
        if (getLong(i * FIELDS_WIDTH + KEY_HASH) == NO_KEY) {
            return ""
        }
        return getString(getLong(i * FIELDS_WIDTH + VALUE_POS))
    }
    fun del(key: String) {
        val i = findIndex(key, false)
        setLong(i * FIELDS_WIDTH + KEY_HASH, DELETED)
    }
    fun clear() {
        for (i in 0 until tableSize()) {
            setLong(i * FIELDS_WIDTH + KEY_HASH, NO_KEY)
            setLong(i * FIELDS_WIDTH + KEY_POS, -1)
            setLong(i * FIELDS_WIDTH + VALUE_POS, -1)
        }
    }
    fun resize() {
        val newSize = max(tableSize(), 1) * 2
        val save = itemsIndex()
        index.setLength(FIELDS_WIDTH * newSize)
        clear()
        freeCells = tableSize()
        for ((key, value) in save) {
            setIndex(key, value)
        }
        assert(freeCells >= tableSize() / 2)
    }
    init {
        if (index.length() % FIELDS_WIDTH > 0) {
            throw Exception("Index file has incorrect length")
        }
        freeCells = tableSize() - itemsIndex().size
        if (tableSize() == 0L || freeCells < tableSize() / 2) {
            resize()
        }
    }
}

fun main(args: Array<String>) {
    val db = Database("tmp")
    val t1 = measureNanoTime {
        for (i in 1L..15000L) {
            db.set(i.toString(), (i * i).toString())
        }
    }
    val t2 = measureNanoTime {
        var s = 0L
        for (i in 1L..15000L) {
            s += db.find(i.toString()).toLong()
        }
        println(s)
    }
    println(t1 / 1e6)
    println(t2 / 1e6)
}
