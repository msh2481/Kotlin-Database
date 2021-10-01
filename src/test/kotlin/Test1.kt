import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.File
import java.io.FileInputStream
import kotlin.math.sin
import kotlin.system.measureNanoTime
import kotlin.test.*

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
    fun hashTest() {
        assertEquals(-6659529605712332715, stringHash(""))
        assertEquals(-5151987661469235269, stringHash("a"))
        assertEquals(6555996166944976083, stringHash("Hello, world!"))
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
        assertEquals(-1481962552, db.items().hashCode())
    }

    fun singleTestFromFiles(testName: String) {
        setUp()
        System.setIn(FileInputStream("test/$testName.in"))
        main(arrayOf())
        assertEquals(File("test/$testName.ans").readText().filter{ it != '\r'}, streamOut.toString().trim().filter{ it != '\r'})
        tearDown()
    }

    @Test
    fun testFullFromFiles() {
        for (i in 1..3) {
            val t = measureNanoTime { singleTestFromFiles(i.toString()) }
            println("Test $i in ${t / 1e6}ms")
        }
    }
}