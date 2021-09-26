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
        val json = db.encodeToJson()
        assertEquals("{\"data\":{\"key\":\"value\"}}", json)
        val db2 = Database(json)
        assertEquals("value", db2.fetch("key"))
        val db3 = Database()
        db3.store("key", "new value")
        db2.import(db3)
        print(db2)
        assertEquals("There is no such key in the database\n" +
                "key new value".trim(), streamOut.toString().trim().filter{ it != '\r'})
    }

    @Test
    fun testDatabaseList() {
        DatabaseList.create("a")
        DatabaseList.store("a", "ключ", "значение")
        assertEquals("Content of a:\n" +
                "ключ значение", DatabaseList.print("a"))
        DatabaseList.store("a", "key 1", "value 1")
        DatabaseList.store("a", "key 2", "value 2")
        assertEquals("значение", DatabaseList.fetch("a", "ключ"))
        assertEquals("", DatabaseList.fetch("a", "b"))
        assertEquals("", DatabaseList.fetch("b", "b"))
        DatabaseList.save("a", "a.txt")
        DatabaseList.close("a")
        DatabaseList.open("b", "a.txt")
        DatabaseList.open("c", "no.file")
        assertEquals("value 1", DatabaseList.fetch("b", "key 1"))
        DatabaseList.create("c")
        assertEquals("There is no such key in the database\n" +
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
