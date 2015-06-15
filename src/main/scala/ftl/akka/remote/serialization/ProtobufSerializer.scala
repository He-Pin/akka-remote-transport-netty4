package ftl.akka.remote.serialization

import java.util.concurrent.ConcurrentHashMap

import akka.serialization.Serializer
import com.google.protobuf.{ Message, Parser }

/**
 * Created by kerr.
 */
class ProtobufSerializer extends Serializer {
  private val serializerBinding = new ConcurrentHashMap[Class[_], Parser[Message]]()
  override def identifier: Int = 17

  override def includeManifest: Boolean = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    manifest match {
      case Some(clazz) =>
        val cachedParser = serializerBinding.get(clazz)
        if (cachedParser ne null) {
          cachedParser.parseFrom(bytes)
        } else {
          val parser = clazz.getField("PARSER").get(null).asInstanceOf[Parser[Message]]
          val previousParser = serializerBinding.putIfAbsent(clazz, parser)
          if (previousParser ne null) {
            previousParser.parseFrom(bytes)
          } else {
            parser.parseFrom(bytes)
          }
        }
      case None => throw new IllegalArgumentException("Need a protobuf message class to be able to serialize bytes using protobuf")
    }
  }

  override def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case message: Message => message.toByteArray
    case _ => throw new IllegalArgumentException(s"Can't serialize a non-protobuf message using protobuf [$obj]")
  }
}
