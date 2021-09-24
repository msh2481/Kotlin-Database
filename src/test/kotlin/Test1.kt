import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.File
import java.io.FileInputStream
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
    fun testDatabase() {
        val db = Database()
        db.store("key", "value")
        assertEquals("", db.fetch("other key"))
        assertEquals("value", db.fetch("key"))
        val json = encodeToJson(db)
        assertEquals("{\"data\":{\"key\":\"value\"}}", json)
        val db2 = decodeFromJson(json)
        assertEquals("value", db.fetch("key"))
        print(db2)
        assertEquals("There is no such key in the database\n" +
                "key value".trim(), streamOut.toString().trim().filter{ it != '\r'})
    }

    @Test
    fun testDatabaseList() {
        val bases = DatabaseList()
        bases.create("a")
        bases.store("a", "ключ", "значение")
        assertEquals("Content of a:\n" +
                "ключ значение", bases.print("a"))
        bases.store("a", "key 1", "value 1")
        bases.store("a", "key 2", "value 2")
        assertEquals("значение", bases.fetch("a", "ключ"))
        assertEquals("", bases.fetch("a", "b"))
        assertEquals("", bases.fetch("b", "b"))
        bases.save("a", "a.txt")
        bases.close("a")
        bases.open("b", "a.txt")
        bases.open("c", "no.file")
        assertEquals("value 1", bases.fetch("b", "key 1"))
        assertEquals("Created database 'a'\n" +
                "There is no such key in the database\n" +
                "There is no such database\n" +
                "Can't open file no.file".trim(), streamOut.toString().trim().filter{ it != '\r'})
    }

    fun singleTestFromFiles(inputFilename: String, answerFilename: String) {
        val prefix = "test/"
        System.setIn(FileInputStream(prefix + inputFilename))
        main(arrayOf())
        assertEquals(File(prefix + answerFilename).readText().filter{ it != '\r'}, streamOut.toString().trim().filter{ it != '\r'})
    }

    @Test
    fun testFullFromFiles() {
        singleTestFromFiles("01.in", "01.ans")
    }
}
