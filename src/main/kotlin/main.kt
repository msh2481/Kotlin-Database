import java.io.File
import java.util.*
import kotlin.system.exitProcess

/** TODO
 * OOP?
 * Docs?
 * Exceptions/warnings
 * Files
 * Tests
 * Hashes
 */

class Database(val file: String? = null) {
    val data = mutableMapOf<String, String>()
    init {
        file?.let{
            val read = Scanner(File(it))
            if (!read.hasNextInt()) {
                throw RuntimeException("Bad file format: there should be entries count in the beginning")
            }
            val entriesCnt = read.nextInt()
            val lenList = mutableListOf<Int>()
            repeat(2 * entriesCnt) {
                if (!read.hasNextInt()) {
                    throw RuntimeException("Bad file format: not enough length definitions")
                }
                lenList.add(read.nextInt())
            }
            val text = read.next()
            var pos = 0
            lenList.chunked(2).forEach{ (keyLen, valLen) ->
                data[text.substring(pos, pos + keyLen)] = text.substring(pos + keyLen, pos + keyLen + valLen)
                pos += keyLen + valLen
            }
            println(data)
        }
    }
    fun save() {
        file?.let{
            File(file).printWriter().use{ out ->
                out.println(data.size)
                data.forEach{ key, value ->
                    out.println("${key.length} ${value.length}")
                }
                data.forEach{ key, value ->
                    out.print(key + value)
                }
            }
        }
    }
}

class DatabaseList {
    val bases = mutableMapOf<String, Database>()

    fun take(name: String) = bases.getOrDefault(name, Database())
    fun open(file: String, name: String) = bases.put(name, Database(file))
    fun close(name: String) = bases.remove(name)
    fun save(name: String) = bases.getValue(name).save()
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
    dbList.take("a")
    dbList.open("f.txt", "f")
}
