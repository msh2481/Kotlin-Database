import mu.KotlinLogging
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.math.max

val logger = KotlinLogging.logger{}

/** TODO
 * Own exceptions
 * Find logging library
 * Logging with levels
 * Different files
 * Select best hash
 * Add tests
 * Add docs
 *
 * ---
 * Optimize:
 *      Measure number of tries
 *      Exceptions
 */

/**
 * Returns last bits of SHA-256 hash for a given string as a Long
 * @param[str] string for which we want to get hash
 *
 */
fun stringHash(str: String): Long {
    val bytes = MessageDigest.getInstance("SHA-256").digest(str.toByteArray(Charsets.UTF_8))
    return bytes.fold(0) { acc, e -> 256 * acc + e.toLong() }
}

class Database(val databaseName: String, clearOldData: Boolean) {
    val content = RandomAccessFile(databaseName + ".content", "rw")
    val index = RandomAccessFile(databaseName + ".index", "rw")

    private fun addString(str: String) : Long {
        content.seek(content.length())
        return content.length().also{ content.writeUTF(str) }
    }
    private fun getString(pos: Long) : String {
        require(pos < content.length()) {"Indexed content file of length ${content.length()} with $pos"}
        content.seek(pos)
        return content.readUTF()
    }

    private fun setLong(pos: Long, value: Long) {
        index.seek(pos)
        index.writeLong(value)
    }
    private fun getLong(pos: Long) : Long {
        require(pos < index.length()) {"Indexed index file of length ${index.length()} with $pos"}
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

    val NO_KEY = -1L
    val DELETED = -2L

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
    var checkIndexCounter = 0
    var lastCounter = 0

    private fun findIndex(key: String, acceptDeleted: Boolean) : Long {
        val keyHash = stringHash(key)
        fun checkIndex(i: Long) : Boolean {
            checkIndexCounter++
            when (getLong(i * FIELDS_WIDTH + KEY_HASH)) {
                keyHash -> return true
                NO_KEY -> return true
                DELETED -> return acceptDeleted
                else -> return false
            }
        }
        lastCounter = checkIndexCounter

        val startPos = (keyHash.toULong() % tableSize().toULong()).toLong()
        for (i in startPos until tableSize())
            if (checkIndex(i))
                return i
        for (i in 0 until startPos)
            if (checkIndex(i))
                return i
        check(false) {"Database overflow, nothing found in findIndex(key = $key, acceptDeleted = $acceptDeleted)"}
        return 0
    }
    private fun storeIndex(keyPos: Long, valuePos: Long) {
        require(keyPos >= 0 && valuePos >= 0)
        val key = getString(keyPos)
        val i = findIndex(key, true)
        when (getLong(i * FIELDS_WIDTH + KEY_HASH)) {
            DELETED -> {
                check(false) {"Deleted objects aren't expected in setByIndex"}
            }
            NO_KEY -> {
                freeCells--
                setLong(i * FIELDS_WIDTH + KEY_HASH, stringHash(key))
                setLong(i * FIELDS_WIDTH + KEY_POS, keyPos)
                setLong(i * FIELDS_WIDTH + VALUE_POS, valuePos)
            }
            else -> {
                check(false) {"Overwriting isn't expected in setIndex"}
            }
        }
    }
    fun store(key: String, value: String) {
        logger.info{ "store $key $value" }
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
    fun fetch(key: String) : String {
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
        val newSize = max(tableSize(), 1024) * 2
        logger.info{ "resize ${tableSize()} -> $newSize" }
        val save = itemsIndex()
        index.setLength(FIELDS_WIDTH * newSize)
        clear()
        freeCells = tableSize()
        for ((key, value) in save) {
            storeIndex(key, value)
        }
        check(freeCells >= tableSize() / 2)
    }
    init {
        if (clearOldData) {
            index.setLength(0)
            content.setLength(0)
        }
        if (index.length() % FIELDS_WIDTH > 0) {
            throw Exception("Index file has incorrect length")
        }
        freeCells = tableSize() - itemsIndex().size
        if (tableSize() == 0L || freeCells < tableSize() / 2) {
            resize()
        }
    }
}

fun greeting() = println("""
    create NAME             to switch to a new empty database NAME
    open NAME               to switch to database NAME from files NAME.index and NAME.content
    store KEY VALUE         to set value for KEY equal to VALUE (overwriting, if needed)
    fetch KEY               to print value for KEY
    append NAME             to add all key-value pairs from database NAME to the current database (overwriting, if needed)
    print                   to print content of the current database
    exit                    to quit program
""".trimIndent())


fun main(args: Array<String>) {
    greeting()
    var db : Database? = null
    while (true) {
        val line = readLine()
        val tokens = line?.split(' ', '\t') ?: break
        val argsByCommand = mapOf("create" to 1, "open" to 1, "store" to 2, "fetch" to 1, "append" to 1, "print" to 0, "exit" to 0)
        if (tokens.isEmpty() || tokens.first() !in argsByCommand || tokens.size < 1 + checkNotNull(argsByCommand[tokens.first()])) {
            println("Failed to parse your input or not enough arguments for this command:\n$line")
            continue
        }
        if (tokens.first() in listOf("create", "open")) {
            db = Database(tokens[1], tokens.first() == "create")
            continue
        }
        if (db == null) {
            println("There is no open database, nothing will be done")
            continue
        }
        when (tokens.first()) {
            "store" ->  db.store(tokens[1], tokens[2])
            "fetch" -> println(db.fetch(tokens[1]))
            "append" -> Database(tokens[1], false).items().forEach{ (key, value) -> db.store(key, value) }
            "print" -> db.items().forEach{ (key, value) -> println("$key $value") }
            "exit" -> break
            else -> check(false) {"Unknown command exists in argsByCommand: ${tokens.first()}"}
        }
    }
}