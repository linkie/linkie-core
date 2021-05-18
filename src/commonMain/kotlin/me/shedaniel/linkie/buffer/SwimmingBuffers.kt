package me.shedaniel.linkie.buffer

import okio.Buffer
import kotlin.jvm.JvmName

fun swimmingPoolWriter(): SwimmingPoolByteBuffer = SwimmingPoolByteBuffer.writer()

fun swimmingPoolReader(byteArray: ByteArray): SwimmingPoolByteBuffer = SwimmingPoolByteBuffer.reader(byteArray)

@JvmName("swimmingPoolReader_")
fun ByteArray.swimmingPoolReader(): SwimmingPoolByteBuffer = swimmingPoolReader(this)

class SwimmingPoolByteBuffer(buffer: Buffer) : ByteBuffer(buffer) {
    val swimmingPool = StringSwimmingPool()

    companion object {
        fun writer(): SwimmingPoolByteBuffer = SwimmingPoolByteBuffer(Buffer())
        fun reader(byteArray: ByteArray): SwimmingPoolByteBuffer = SwimmingPoolByteBuffer(Buffer()).apply {
            writeByteArray(byteArray)
            swimmingPool.read(this)
        }
    }

    override fun writeTo(): ByteArray = swimmingPool.write(ByteBuffer.writer()).writeTo() + super.writeTo()

    override fun writeNotNullString(string: String) = writeStringOrNull(string)
    override fun writeStringOrNull(string: String?) = swimmingPool.writeString(this, string)
    override fun readStringOrNull(): String? = swimmingPool.readString(this)
}
