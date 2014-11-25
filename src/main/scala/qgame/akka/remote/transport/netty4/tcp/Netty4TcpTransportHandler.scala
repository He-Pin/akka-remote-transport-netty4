package qgame.akka.remote.transport.netty4.tcp

import akka.actor.ActorRef
import akka.remote.transport.AssociationHandle.{Unknown, Disassociated, InboundPayload, HandleEventListener}
import akka.util.ByteString
import io.netty.buffer.ByteBuf
import io.netty.channel.{Channel, ChannelHandlerContext, SimpleChannelInboundHandler}

import scala.util.control.NonFatal

/**
 * Created by kerr.
 */
class Netty4TcpTransportHandler(master:ActorRef) extends SimpleChannelInboundHandler[ByteBuf]{
  private var listener :HandleEventListener = _
  private var associator:ActorRef = _
  override def channelRead0(channelHandlerContext: ChannelHandlerContext, msg: ByteBuf): Unit = {
    val payload = {
      val length = msg.readableBytes()
      val bytesArray = new Array[Byte](length)
      msg.readBytes(bytesArray)
      ByteString(bytesArray)
    }
    listener.notify(InboundPayload(payload))
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    println("channelActive at :"+ctx.channel())
    ctx.channel().config().setAutoRead(false)
    master ! ChannelActive(ctx.channel())
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    println("channelInactive at :"+ctx.channel())
    master ! ChannelInActive(ctx.channel())
    if (associator ne null){
      associator ! ChannelInActive(ctx.channel())
      ctx.close()
    }else{
      ctx.close()
    }
    if (listener ne null){
      listener.notify(Disassociated(Unknown))
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    println("exceptionCaught at :"+ctx.channel())
    cause match {
      case NonFatal(e)=>
        if (associator ne null){
          associator ! ChannelExceptionCaught(ctx.channel(),e)
          //FIXME should here notify the listener too? akka remote netty3 notified that.
          ctx.close()
        }else{
          ctx.close()
        }
      case _=>
        ctx.close()
    }
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, event:AnyRef): Unit = {
    println(
      s"""-------------------------------------------
        |           userEventTriggered
        |--------------------------------------------
        |event :${event.getClass}
        |--------------------------------------------
      """.stripMargin)
    println("userEventTriggered at :"+ctx.channel())
    event match {
      case RegisterAssociator(handlerAssociator)=>
        associator = handlerAssociator
        associator ! RegisterAssociatorACK
      case RegisterHandlerEventListener(handleEventListener) =>
        //update local var ,and then set the channel to auto read true
        listener = handleEventListener
        associator ! RegisterHandlerEventListenerACK
      case _ => ctx.fireUserEventTriggered(event)
    }
  }
}

sealed trait Netty4TcpTransportHandlerCommand

case class RegisterHandlerEventListener(handleEventListener:HandleEventListener) extends Netty4TcpTransportHandlerCommand

case object RegisterHandlerEventListenerACK

case class RegisterAssociator(associator:ActorRef) extends Netty4TcpTransportHandlerCommand

//FIXME ,the channel could be closed before an user event triggered
case object RegisterAssociatorACK

sealed trait ChannelEvent

case class ChannelActive(channel:Channel) extends ChannelEvent

case class ChannelInActive(channel:Channel) extends ChannelEvent

case class ChannelExceptionCaught(channel:Channel,cause:Throwable) extends ChannelEvent
