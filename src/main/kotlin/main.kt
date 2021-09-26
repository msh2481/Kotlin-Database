import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.system.exitProcess

/** TODO
 * Readme
 * Tests
 * Hashes
 *
 * import, saveAll, listBases, listItems
 */

/** Single database and operations not involving other databases
 *
 * Usage:
 * val db = Database()
 * db.store("key", "value")
 * assertEquals("", db.fetch("other key"))
 * assertEquals("value", db.fetch("key"))
 * val json = db.encodeToJson()
 * assertEquals("{"data":{"key":"value"}}", json)
 * val db2 = Database(json)
 * assertEquals("value", db2.fetch("key"))
 * val db3 = Database()
 * db3.store("key", "new value")
 * db2.import(db3)
 * print(db2)
 */
@Serializable
class Database() {
    val data = mutableMapOf<String, String>()

    constructor(json: String) : this() {
        data.putAll(Json.decodeFromString<Database>(json).data)
    }

    fun fetch(key: String) = data[key] ?: println("There is no such key in the database").let{ "" }
    fun store(key: String, value: String) = data.put(key, value)
    override fun toString() = data.map{ (key, value) -> "$key $value"}.joinToString(separator = "\n")
    fun encodeToJson() = Json.encodeToString(this)
}

fun encodeToJson(base: Database) = Json.encodeToString(base)
fun decodeFromJson(arg: String) : Database = Json.decodeFromString(arg)

/** Map from string to database and all operations with them
 *
 * Usage:
 * val bases = DatabaseList()
 * bases.create("a")
 * bases.store("a", "ключ", "значение")
 * bases.print("a"))
 * bases.store("a", "key 1", "value 1")
 * assertEquals("значение", bases.fetch("a", "ключ"))
 * assertEquals("", bases.fetch("a", "b"))
 * assertEquals("", bases.fetch("b", "b"))
 * bases.save("a", "a.txt")
 * bases.close("a")
 * bases.open("b", "a.txt")
 * assertEquals("value 1", bases.fetch("b", "key 1"))
 */
object DatabaseList {
    val bases = mutableMapOf<String, Database>()

    fun open(base: String, file: String) : Unit {
        try {
            bases.put(base, decodeFromJson(File(file).readText()))
        } catch (e: java.io.FileNotFoundException) {
            println("Can't open file $file")
        }
    }
    fun close(name: String) : Unit {
        bases.remove(name)
    }
    fun save(base: String, file: String) : Unit{
        bases[base].let {
            if (it != null) {
                File(file).writeText(encodeToJson(it))
            } else {
                println("There is no such database")
            }
        }
    }
    fun create(base: String) : Unit {
        if (bases.contains(base)) {
            println("Database with this name already exists, close it first")
        } else {
            bases[base] = Database()
        }
    }
    fun fetch(base: String, key: String) : String = bases[base].let {
        return if (it != null) {
            it.fetch(key)
        } else {
            println("There is no such database").let{""}
        }
    }
    fun store(base: String, key: String, value: String) : Unit {
        bases[base].let {
            if (it != null) {
                it.store(key, value)
            } else {
                println("There is no such database")
            }
        }
    }
    fun print(base: String) : String = bases[base].let {
        if (it != null) {
            "Content of $base:\n${it}"
        } else {
            println("There is no such database")
            ""
        }
    }
    fun copy(baseA: String, baseB: String) : Unit {
        val A = bases[baseA]
        val B = bases[baseB]
        if (A == null) {
            println("Invalid first argument")
        } else if (B == null) {
            println("Invalid second argument")
        } else {
            B.data.putAll(A.data)
        }
    }
}

fun greeting() = println("""
    create NAME             to create new empty database NAME
    open NAME FILE          to open database NAME from FILE
    close NAME              to remove database NAME from memory
    store NAME KEY VALUE    to assign NAME[KEY] = VALUE
    fetch NAME KEY          to print NAME[KEY]
    save NAME FILE          to write database NAME to FILE
    print NAME              to print content of database NAME
    copy A B                to insert content of database A into database B
    exit                    to quit program
""".trimIndent())


fun main(args: Array<String>) {
    greeting()
    while (true) {
        val line = readLine()
        if (line == null) {
            break
        }
        if (line.isBlank()) {
            continue
        }
        val tokens = line.split(' ', '\t')
        assert(tokens.isNotEmpty())
        when (tokens.first()) {
            "create" -> DatabaseList.create(tokens[1])
            "open" -> DatabaseList.open(tokens[1], tokens[2])
            "close" -> DatabaseList.close(tokens[1])
            "store" -> DatabaseList.store(tokens[1], tokens[2], tokens[3])
            "fetch" -> println(DatabaseList.fetch(tokens[1], tokens[2]))
            "save" -> DatabaseList.save(tokens[1], tokens[2])
            "print" -> println(DatabaseList.print(tokens[1]))
            "copy" -> DatabaseList.copy(tokens[1], tokens[2])
            "exit" -> break
            else -> println("Failed to parse your command: ${tokens.first()}")
        }
    }
}
