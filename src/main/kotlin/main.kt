import mu.KotlinLogging
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.math.max

/**
 * Global logger
 */
val logger = KotlinLogging.logger{}

/**
 * Returns last bits of SHA-256 hash for a given string as a Long
 * @param[str] string for which we want to get hash
 *
 * Usage:
 * assertEquals(-6659529605712332715, stringHash(""))
 * assertEquals(-5151987661469235269, stringHash("a"))
 * assertEquals(6555996166944976083, stringHash("Hello, world!"))
 */
fun stringHash(str: String): Long {
    val bytes = MessageDigest.getInstance("SHA-256").digest(str.toByteArray(Charsets.UTF_8))
    return bytes.fold(0) { acc, e -> 256 * acc + e.toLong() }
}

/**
 * Database class
 * @constructor
 * @param[databaseName] it determines names of files, from which database will be opened
 * @param[clearOldData] true if you want create new database, erasing possibly existing data
 *
 * @property[content] file for strings, sequence of UTF-8 encoded strings and their lengths
 * @property[index] file for hashtable, merely an array of 3 * tableSize() Longs
 *
 * Usage:
 * val db = Database("database", true)
 * db.store("a", "b")
 * db.store("qwerty", "uiop[]")
 * assertEquals("b", db.fetch("a"))
 * assertEquals("", db.fetch("c"))
 */
class Database(val databaseName: String, clearOldData: Boolean) {
    val content = RandomAccessFile(databaseName + ".content", "rw")
    val index = RandomAccessFile(databaseName + ".index", "rw")

    /**
     * Append a string to the content file
     * @param[str] string to append
     * @return position where the string was written in the file
     */
    private fun addString(str: String) : Long {
        content.seek(content.length())
        return content.length().also{ content.writeUTF(str) }
    }

    /**
     * Return string at the given position in the content file
     * @param[pos] offset in the content file
     * @return string starting at pos
     */
    private fun getString(pos: Long) : String {
        require(pos < content.length()) {"Indexed content file of length ${content.length()} with $pos"}
        content.seek(pos)
        return content.readUTF()
    }

    /**
     * Writes Long at the given position in the index file
     * @param[pos] position to write (in bytes)
     */
    private fun setLong(pos: Long, value: Long) {
        index.seek(pos)
        index.writeLong(value)
    }

    /**
     * Returns Long at the given position in the index file
     * @param[pos] position to read (in bytes)
     * @return Long starting from pos
     */
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

    val BYTES_IN_LONG = 8L
    val FIELDS_COUNT = 3L

    /**
     * Constants used for marking free and deleted nodes (setting their key hash equal to this)
     */
    val NO_KEY = -1L
    val DELETED = -2L

    /**
     * Constants used for reading index file as array of structures
     * So, index[i].field in pseudocode will be written as index[FIELDS_WIDTH * i + <this field's offset>]
     */
    val FIELDS_WIDTH = FIELDS_COUNT * BYTES_IN_LONG
    val KEY_HASH = 0 * BYTES_IN_LONG
    val KEY_POS = 1 * BYTES_IN_LONG
    val VALUE_POS = 2 * BYTES_IN_LONG

    /**
     * Length of the hashtable (including used, free and deleted nodes)
     * @return length of the hashtable
     */
    fun tableSize() = index.length() / FIELDS_WIDTH

    /**
     * freeNodes := |{i : index[i].key_hash == NO_KEY}|
     */
    private var freeNodes = 0L

    /**
     * Returns all key-value pairs from the hashtable in form of pointers to the content file
     * These pairs are sorted (by this pointers, lexicographically)
     * @return key-value pairs of pointers to the content file
     */
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
        return buffer.sortedWith(compareBy({it.first}, {it.second}))
    }

    /**
     * Same as itemsIndex(), but returns pairs of strings
     * @return list of key-value pairs as strings
     */
    fun items() : List<Pair<String, String>> = itemsIndex().map{ (i, j) -> Pair(getString(i), getString(j))}

    /**
     * Two counters used for calculating statistics in findIndex
     */
    var checkIndexCounter = 0
    var lastCounter = 0

    /**
     * Main search function for the hashtable
     * @param[key] string to find as key
     * @param[acceptDeleted] when true, count deleted nodes as free
     * @return index of node nearest to starting (by hashtable algorithm), which if free, or has the same key hash as given string
     */
    private fun findIndex(key: String, acceptDeleted: Boolean) : Long {
        val keyHash = stringHash(key)

        /** Checks if current node is already the answer
         * @param[i] index of hashtable node to check
         * @return true if it's free or it's deleted and deleted nodes are allowed
         */
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

    /**
     * Stores new key-value pair, overwriting value if needed
     * @param[key] key string
     * @param[value] value string
     */
    fun store(key: String, value: String) {
//        logger.info{ "store $key $value" }
        val i = findIndex(key, true)
        when (getLong(i * FIELDS_WIDTH + KEY_HASH)) {
            NO_KEY, DELETED -> {
                freeNodes--
                setLong(i * FIELDS_WIDTH + KEY_HASH, stringHash(key))
                setLong(i * FIELDS_WIDTH + KEY_POS, addString(key))
                setLong(i * FIELDS_WIDTH + VALUE_POS, addString(value))
            }
            else -> {
                setLong(i * FIELDS_WIDTH + VALUE_POS, addString(value))
            }
        }
        if (freeNodes < tableSize() / 2) {
            resize()
        }
    }

    /**
     * Alternative store for strings that are already in the content file
     * @param[keyPos] position of the key string in the content file
     * @param[valuePos] position of the value string in the content file
     */
    private fun storeIndex(keyPos: Long, valuePos: Long) {
        require(keyPos >= 0 && valuePos >= 0)
        val key = getString(keyPos)
        val i = findIndex(key, true)
        when (getLong(i * FIELDS_WIDTH + KEY_HASH)) {
            DELETED -> {
                check(false) {"Deleted objects aren't expected in setByIndex"}
            }
            NO_KEY -> {
                freeNodes--
                setLong(i * FIELDS_WIDTH + KEY_HASH, stringHash(key))
                setLong(i * FIELDS_WIDTH + KEY_POS, keyPos)
                setLong(i * FIELDS_WIDTH + VALUE_POS, valuePos)
            }
            else -> {
                check(false) {"Overwriting isn't expected in setIndex"}
            }
        }
    }

    /**
     * Fetch a string from the hashtable by the given key
     * @param[key] key string
     * @return corresponding value string or an empty string if such key doesn't exist in hashtable
     */
    fun fetch(key: String) : String {
        val i = findIndex(key, false)
        if (getLong(i * FIELDS_WIDTH + KEY_HASH) == NO_KEY) {
            return ""
        }
        return getString(getLong(i * FIELDS_WIDTH + VALUE_POS))
    }

    /**
     * Delete key-value pair from the hashtable by the given key
     * @param[key] key string
     */
    fun del(key: String) {
        val i = findIndex(key, false)
        setLong(i * FIELDS_WIDTH + KEY_HASH, DELETED)
    }

    /**
     * Changes all values in the index file to default (corresponding to the empty hashtable of the same size)
     */
    fun zeroIndexFile() {
        for (i in 0 until tableSize()) {
            setLong(i * FIELDS_WIDTH + KEY_HASH, NO_KEY)
            setLong(i * FIELDS_WIDTH + KEY_POS, -1)
            setLong(i * FIELDS_WIDTH + VALUE_POS, -1)
        }
    }

    /**
     * Copies all key-value pairs into a larger hashtable
     * Sizes start from 1024 (to skip a sequence of reallocations for small databases) and grow as binary powers
     */
    fun resize() {
        val newSize = max(tableSize(), 1024) * 2
        logger.info{ "resize ${tableSize()} -> $newSize" }
        val save = itemsIndex()
        index.setLength(FIELDS_WIDTH * newSize)
        zeroIndexFile()
        freeNodes = tableSize()
        for ((key, value) in save) {
            storeIndex(key, value)
        }
        check(freeNodes >= tableSize() / 2)
    }

    /**
     * Constructor
     * Fills index with default values if clearOldData is true, resizes if there are not enough free nodes
     */
    init {
        if (clearOldData) {
            index.setLength(0)
            content.setLength(0)
        }
        if (index.length() % FIELDS_WIDTH > 0) {
            throw Exception("Index file has incorrect length")
        }
        freeNodes = tableSize() - itemsIndex().size
        if (tableSize() == 0L || freeNodes < tableSize() / 2) {
            resize()
        }
    }
}

val greeting = """
    create NAME             to switch to a new empty database NAME
    open NAME               to switch to database NAME from files NAME.index and NAME.content
    store KEY VALUE         to set value for KEY equal to VALUE (overwriting, if needed)
    fetch KEY               to print value for KEY
    append NAME             to add all key-value pairs from database NAME to the current database (overwriting, if needed)
    print                   to print content of the current database
    exit                    to quit program
""".trimIndent()


fun main(args: Array<String>) {
    println(greeting)
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