package cz.android.logtoolkit.reader;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import kotlin.text.Charsets;

/**
 * A buffered file channel reader.
 * Used for read primitive data from file channel more easily.
 *
 * @see #readBoolean()
 * @see #readByte()
 * @see #readByteUnsigned()
 * @see #readShort()
 * @see #readShortLe()
 * @see #readInt()
 * @see #readInt(int)
 * @see #readIntUnsigned(int)
 * @see #readFloat()
 * @see #readLong()
 * @see #readLong(int)
 * @see #readLongUnsigned(int)
 * @see #readDouble()
 * @see #readString(int)
 * @see #readString(int, Charset)
 */
public class BufferedChannelReader implements Reader, Closeable {
    private static int defaultCharBufferSize = 8 * 1024;
    private static final byte[] TEMP_ARRAY = new byte[4 * 1024];
    private ByteBuffer byteBuffer;
    private FileChannel fileChannel;

    public BufferedChannelReader(FileChannel fileChannel) {
        this(fileChannel, defaultCharBufferSize);
    }

    public BufferedChannelReader(FileChannel fileChannel, int bufferSize) {
        this.fileChannel = fileChannel;
        this.byteBuffer = ByteBuffer.allocate(bufferSize);
        this.byteBuffer.position(byteBuffer.limit());
    }

    /**
     * Checks to make sure that the stream has not been closed
     **/
    private void ensureChannel() throws IOException {
        if (fileChannel == null)
            throw new IOException("Stream closed");
        readBufferIfNeed();
    }

    /**
     * @return 32 bit signed integer value
     */
    @Override
    public short readShortLe() throws IOException {
        return (short) (readByte() & 0xFF | ((readByte() & 0xFF) << 8));
    }

    /**
     * @return 32 bit signed integer value
     */
    @Override
    public short readShort() throws IOException {
        return (short) (((readByte() & 0xFF) << 8) | (readByte() & 0xFF));
    }

    /**
     * @return 32 bit signed integer value
     */
    @Override
    public int readInt() throws IOException {
        return ((readByte() & 0xFF) << 24) | ((readByte() & 0xFF) << 16) | ((readByte() & 0xFF) << 8) | (readByte()
                & 0xFF);
    }

    /**
     * @param bits Length of integer
     * @return Signed integer value of given bit width
     */
    @Override
    public int readInt(int bits) throws IOException {
        if (bits == 0)
            return 0;
        boolean sign = readBoolean();
        int inBits = --bits;

        int res = 0;
        do {
            if (bits > 7) {
                res = (res << 8) | (readByte() & 0xFF);
                bits -= 8;
            } else {
                res = (res << bits) + (readByte());
                bits -= bits;
            }
        } while (bits > 0);

        return sign ? (0xFFFFFFFF << inBits) | res : res;
    }

    @Override
    public boolean readBoolean() throws IOException {
        byte b = peekByte();
        return 0 < (b & 0x80);
    }

    /**
     * @param bits Length of integer
     * @return Unsigned Integer value of given bit width
     */
    @Override
    public int readIntUnsigned(int bits) throws IOException {
        if (bits == 0)
            return 0;
        int res = 0;
        do {
            if (bits > 7) {
                res = (res << 8) | (readByte() & 0xFF);
                bits -= 8;
            } else {
                res = (res << bits) + (readByteUnsigned());
                bits -= bits;
            }
        } while (bits > 0);
        return res;
    }

    /**
     * @return 64 bit signed long value
     */
    @Override
    public long readLong() throws IOException {
        return ((readByte() & 0xFFL) << 56L) | ((readByte() & 0xFFL) << 48L) | ((readByte() & 0xFFL) << 40L) | (
                (readByte() & 0xFFL) << 32L)
                | ((readByte() & 0xFFL) << 24L) | ((readByte() & 0xFFL) << 16L) | ((readByte() & 0xFFL) << 8L) | (
                readByte() & 0xFFL);
    }

    /**
     * @param bits Length of long integer
     * @return Signed long value of given bit width
     */
    @Override
    public long readLong(int bits) throws IOException {
        if (bits == 0)
            return 0;
        boolean sign = readBoolean();
        int inBits = --bits;

        long res = 0;
        do {
            if (bits > 31) {
                res = (res << 32L) | (readInt() & 0xFFFFFFFFL);
                bits -= 32;
            } else {
                res = (res << bits) | (readIntUnsigned(bits) & 0xFFFFFFFFL);
                bits -= bits;
            }
        } while (bits > 0);
        return (sign ? (0xFFFFFFFFFFFFFFFFL << (long) inBits) | res : res);
    }

    /**
     * @param bits Length of long integer
     * @return Unsigned long value of given bit width
     */
    @Override
    public long readLongUnsigned(int bits) throws IOException {
        if (bits == 0)
            return 0;
        long res = 0;
        do {
            if (bits > 31) {
                res = (res << 32L) | (readInt() & 0xFFFFFFFFL);
                bits -= 32;
            } else {
                res = (res << bits) | (readIntUnsigned(bits) & 0xFFFFFFFFL);
                bits -= bits;
            }
        } while (bits > 0);
        return res;
    }

    /**
     * @return 32 bit floating point value
     */
    @Override
    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    /**
     * @return 64 bit floating point value
     */
    @Override
    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * @param length Length of the string
     * @return String of given length, using ASCII encoding
     */
    @Override
    public String readString(int length) throws IOException {
        byte[] tmp = TEMP_ARRAY;
        readBufferIfNeed();
        return readStringInternal(tmp, 0, length, Charsets.US_ASCII);
    }

    /**
     * @param length  Length of the string
     * @param charset {@link Charset} to use for decoding
     * @return String of given length, using ASCII encoding
     */
    @Override
    public String readString(int length, Charset charset) throws IOException {
        byte[] tmp = TEMP_ARRAY;
        readBufferIfNeed();
        return readStringInternal(tmp, 0, length, charset);
    }

    private String readStringInternal(byte[] buffer, int start, int length, Charset charset) throws IOException {
        if (position() == size()) {
            return new String(buffer, 0, start, charset);
        }
        if (length <= byteBuffer.remaining()) {
            int position = byteBuffer.position();
            byteBuffer.position(position + length);
            final byte[] array = byteBuffer.array();
            System.arraycopy(array, position, buffer, start, length);
            return new String(buffer, 0, start + length, charset);
        } else {
            int position = byteBuffer.position();
            int remaining = byteBuffer.remaining();
            final byte[] array = byteBuffer.array();
            System.arraycopy(array, position, buffer, start, remaining);
            readBuffer();
            return readStringInternal(buffer, start + remaining, length - remaining, charset);
        }
    }

    private int readBuffer() throws IOException {
        byteBuffer.clear();
        int read = fileChannel.read(byteBuffer);
        byteBuffer.flip();
        return read;
    }

    private void readBufferIfNeed() throws IOException {
        if (!byteBuffer.hasRemaining()) {
            readBuffer();
        }
    }

    @Override
    public int readByteUnsigned() throws IOException {
        ensureChannel();
        if (byteBuffer.position() >= byteBuffer.limit()) {
            return -1;
        } else {
            return byteBuffer.get() & 0xFF;
        }
    }

    @Override
    public byte peekByte() throws IOException {
        ensureChannel();
        int position = byteBuffer.position();
        byte data = byteBuffer.get();
        byteBuffer.position(position);
        return data;
    }

    @Override
    public byte readByte() throws IOException {
        ensureChannel();
        return byteBuffer.get();
    }

    public boolean hasRemaining() throws IOException {
        ensureChannel();
        return byteBuffer.position() >= byteBuffer.limit();
    }

    @Override
    public long position() throws IOException {
        long position = fileChannel.position();
        return position - byteBuffer.remaining();
    }

    public void position(long newPosition) throws IOException {
        fileChannel.position(newPosition);
        readBuffer();
    }

    @Override
    public long size() throws IOException {
        return fileChannel.size();
    }

    public boolean isOpen() {
        return fileChannel.isOpen();
    }

    /**
     * Read file channel by a internal buffer
     *
     * @return
     * @throws IOException
     */
    @Override
    public String readLine() throws IOException {
        ensureChannel();
        readBufferIfNeed();
        //First time when we readByte the line.
        int offset = 0;
        byte[] buffer = TEMP_ARRAY;
        while (byteBuffer.hasRemaining()) {
            byte b = byteBuffer.get();
            buffer[offset++] = b;
            if (buffer.length == offset) {
                buffer = Arrays.copyOf(buffer, (int) (buffer.length * 1.2f));
            }
            if (b == '\n') {
                return new String(buffer, 0, offset);
            }
            readBufferIfNeed();
        }
        return null;
    }

    @Override
    public void skip(int num) throws IOException {
        if (position() == size()) {
            return;
        }
        if (num < byteBuffer.remaining()) {
            int position = byteBuffer.position();
            byteBuffer.position(position + num);
        } else {
            num -= byteBuffer.remaining();
            readBuffer();
            skip(num);
        }
    }

    public long skipLine() throws IOException {
        ensureChannel();
        readBufferIfNeed();
        //First time when we readByte the line.
        while (byteBuffer.hasRemaining()) {
            byte b = byteBuffer.get();
            if (b == '\n') {
                return position();
            }
            readBufferIfNeed();
        }
        return -1;
    }

    @Override
    public void close() throws IOException {
        fileChannel.close();
    }
}
