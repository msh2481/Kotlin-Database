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
 */

@Serializable
class Database() {
    val data = mutableMapOf<String, String>()
    fun fetch(key: String) =
}

fun encodeToString(base: Database) : String = Json.encodeToString(base)
fun decodeFromString(arg: String) : Database = Json.decodeFromString(arg)

class DatabaseList {
    val bases = mutableMapOf<String, Database>()

    fun fetchDatabase(name: String) = bases.getValue(name)
    fun open(file: String, base: String) {
        val str : String = File(file).readText()
        bases.put(base, decodeFromString(str))
    }
    fun close(name: String) = bases.remove(name)
    fun save(file: String, base: String) = File(file).writeText(encodeToString(fetchDatabase(base))).also{}
}

fun greeting() = println("""
    open FILE NAME          to open database NAME from FILE
    import FILE NAME        to open database from FILE and insert it into database NAME
    close NAME              to remove database NAME from memory
    store NAME KEY VALUE    to assign NAME[KEY] = VALUE
    fetch NAME KEY          to print NAME[KEY]
    save NAME               to write database NAME to the corresponding file
    save                    to save all open databases
""".trimIndent())

fun main(args: Array<String>) {
    greeting()
    val dbList = DatabaseList()
    dbList.open("g.txt", "g")
    dbList.save("f.txt", "g")
}
