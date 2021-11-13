package cz.android.logtoolkit

import cz.android.logtoolkit.reader.BufferedChannelReader
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Buffered file change.
 * Because FileChannel actually do not have a buffer. And it sometimes make things difficult to deal with.
 * You have to take care of the ByteBuffer and take data from the buffer at the same time.
 *
 * So here we have the BufferedChannelReader. it helps you to take care of the buffer.
 * I've tested when the buffer size is only one. It works pretty good.
 */
class BufferedChannelReaderTest {
    /**
     * Load a file and check if our buffer reader works fine.
     * @throws IOException
     */
    @Test
    @Throws(IOException::class)
    fun testReadFile() {
        val file = File("../conf/test.txt")
        val reader = BufferedChannelReader(file.inputStream().channel)
        var line = reader.readLine()
        val regex = "[^/]+/([\\w\\.]+)\\s(\\w)/([^:]+)".toRegex()
        var counter1 = 0
        var counter2 = 0
        while (null != line) {
            if (line.startsWith("2021-11-10")) {
                counter1++
            }
            val matcher = regex.find(line)
            if (null != matcher) {
                counter2++
            }
            line = reader.readLine()
        }
        Assert.assertEquals(counter1, counter2)
    }

    @Test
    @Throws(IOException::class)
    fun testReadLine() {
        val file = File("../conf/test.txt")
        val count = Files.lines(file.toPath()).count().toInt()
        val reader = BufferedChannelReader(file.inputStream().channel)
        var line = reader.readLine()
        var counter = 0
        while (null != line) {
            counter++
            line = reader.readLine()
        }
        Assert.assertEquals(count, counter)
    }

    @Test
    fun testLines() {
        val file = File("../conf/test.txt")
        val available = file.inputStream().use {
            it.available()
        }
        val reader = BufferedChannelReader(file.inputStream().channel)
        var index = reader.skipLine()
        while (-1L != index) {
            index = reader.skipLine()
        }
        Assert.assertEquals(reader.position(), available.toLong())
    }

    @Test
    fun testReadString() {
        val file = File("../conf/test.txt")
        val reader1 = BufferedChannelReader(file.inputStream().channel, 2)
        val reader2 = BufferedChannelReader(file.inputStream().channel, 2)
        var pre = 0L
        var index = reader1.skipLine()
        while (-1L != index) {
            val length = (index - pre).toInt()
            var str = reader2.readString(length)
            Assert.assertEquals(str.length, length)
            pre = index
            index = reader1.skipLine()
        }
    }
}