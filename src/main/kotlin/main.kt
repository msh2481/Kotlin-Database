import java.io.File
import java.util.*
import kotlin.system.exitProcess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import javax.xml.crypto.Data


/** TODO
 * OOP?
 * Docs?
 * Exceptions/warnings
 * Files
 * Tests
 * Hashes
 *
 * import, saveAll, listBases, listItems
 */

@Serializable
class Database() {
    val data = mutableMapOf<String, String>()
    fun fetch(key: String) = data[key] ?: println("There is no such key in the database").let{ "" }
    fun store(key: String, value: String) = data.put(key, value)
}

fun encodeToString(base: Database) : String = Json.encodeToString(base)
fun decodeFromString(arg: String) : Database = Json.decodeFromString(arg)

class DatabaseList {
    val bases = mutableMapOf<String, Database>()

    fun open(base: String, file: String) = bases.put(base, decodeFromString(File(file).readText()))
    fun close(name: String) = bases.remove(name)
    fun save(base: String, file: String) = bases[base].let {
        if (it != null) {
            File(file).writeText(encodeToString(it))
        } else {
            println("There is no such database")
        }
    }
    fun create(base: String) = bases.put(base, Database()).also{ println("Succesfully created database '$base'") }
    fun fetch(base: String, key: String) : String = bases[base].let {
        return if (it != null) {
            it.fetch(key)
        } else {
            println("There is no such database").let{""}
        }
    }
    fun store(base: String, key: String, value: String) = bases[base].let {
        if (it != null) {
            println("Succesfully stored ($key, $value) to $base")
            it.store(key, value)
        } else {
            println("There is no such database")
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
    exit                    to quit program
""".trimIndent())

fun main(args: Array<String>) {
    greeting()
    val bases = DatabaseList()
    while (true) {
        val line = readLine()
        if (line == null) {
            break
        }
        if (line.isBlank()) {
            continue
        }
        val tokens = line.split(' ', '\t')
        assert(tokens.size > 0)
        when (tokens.first()) {
            "create" -> bases.create(tokens[1])
            "open" -> bases.open(tokens[1], tokens[2])
            "close" -> bases.close(tokens[1])
            "store" -> bases.store(tokens[1], tokens[2], tokens[3])
            "fetch" -> println(bases.fetch(tokens[1], tokens[2]))
            "save" -> bases.save(tokens[1], tokens[2])
            "exit" -> break
            else -> println("Failed to parse your command")
        }
    }
}
