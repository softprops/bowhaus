package bowhaus

import com.twitter.bijection._
import org.jboss.netty.buffer.{ ChannelBuffer, ChannelBuffers }

object Bijections extends StringInjections {
  // this is in bijection (0.4.0)
  object ChannelBufferBijection extends Bijection[ChannelBuffer, Array[Byte]] {
    override def apply(cb: ChannelBuffer) = {
      val dup = cb.duplicate
      val result = new Array[Byte](dup.readableBytes)
      dup.readBytes(result)
      result
    }
    override def invert(ary: Array[Byte]) = ChannelBuffers.wrappedBuffer(ary)
  }
  object ByteArrayBijection extends Bijection[Array[Byte], String] {
    override def invert(in: String) = in.getBytes("utf-8")
    override def apply(ary: Array[Byte]) = new String(ary, "utf-8")
  }
  implicit val cbs = ChannelBufferBijection
  implicit val strs = ByteArrayBijection
  implicit val bj = Bijection.connect[ChannelBuffer, Array[Byte], String]
}
