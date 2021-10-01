import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.File
import java.io.FileInputStream
import kotlin.test.*
import kotlin.text.Charsets.UTF_8

internal class Test1 {
    private val standardOut = System.out
    private val standardIn = System.`in`
    private var streamOut = ByteArrayOutputStream()

    @BeforeTest
    fun setUp() {
        streamOut = ByteArrayOutputStream()
        System.setOut(PrintStream(streamOut))
    }

    @AfterTest
    fun tearDown() {
        System.setOut(standardOut)
        System.setIn(standardIn)
    }

    @Test
    fun testDatabase() {
        val db = Database("database", true)
        for (i in 1..100) {
            db.store(i.toString(), (i * i).toString())
        }
        for (i in 1..1000) {
            db.store(i.toString(), (2 * i).toString())
        }
        for (i in 1..1000) {
            assertEquals((2 * i).toString(), db.fetch(i.toString()))
        }
        for (i in -1000..-1) {
            assertEquals("", db.fetch(i.toString()))
        }
        assertEquals(2090390714, setOf(db.items()).hashCode())
    }

    fun singleTestFromFiles(testName: String) {
        System.setIn(FileInputStream("test/$testName.in"))
        main(arrayOf())
        assertEquals(File("test/$testName.ans").readText(Charsets.UTF_8), streamOut.toString().trim())
    }

    @Test
    fun testFullFromFiles() {
        for (i in 1..1) {
            singleTestFromFiles(i.toString())
        }
    }
}