package qgame.akka.remote.transport.netty4.tcp

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, Address, Cancellable}
import akka.remote.transport.AssociationHandle
import akka.remote.transport.AssociationHandle.HandleEventListener
import akka.util.ByteString
import io.netty.buffer.Unpooled
import io.netty.channel.Channel

import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

/**
 * Created by kerr.
 */
class Netty4TcpTransportAssociator(flushDuration:FiniteDuration,channel: Channel, op: AssociationHandle => Any) extends Actor with ActorLogging{
  private var flushTickTask :Cancellable = _
  override def receive: Receive = {
    case AssociateChannelInBound =>
      //first register the associator
      log.debug("associate channel inbound/connected in ,fireUserEventTriggered RegisterAssociator at :{}",self)
      channel.pipeline().fireUserEventTriggered(RegisterAssociator(self))
      log.debug("register associator command fired,waiting ack at:{}",self)
      context.become(waitRegisterAssociatorACK())
    case AssociateChannelOutBound =>
      //first register the associator
      log.debug("associate channel outbound/connected out ,fireUserEventTriggered RegisterAssociator at :{}",self)
      channel.pipeline().fireUserEventTriggered(RegisterAssociator(self))
      log.debug("register associator command fired,waiting ack at:{}",self)
      context.become(waitRegisterAssociatorACK())
  }

  private def waitRegisterAssociatorACK():Actor.Receive = {
    case RegisterAssociatorACK =>
      //going to notify inbound association ,and waiting the
      //read register
      log.debug("register associator success at:{}",self)
      val associationHandler = Netty4TcpTransportAssociationHandle(channel)
      log.debug(
        s"""
          |~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          |           associationHandler information
          |~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          |channel:${associationHandler.channel}
          |localAddress:${associationHandler.localAddress}
          |remoteAddress:${associationHandler.remoteAddress}
          |-------------------------------------------------------
          """.stripMargin)

      val readHandlerRegisterFuture = associationHandler.readHandlerPromise.future
      import scala.concurrent.ExecutionContext.Implicits.global
      log.debug("register call back on readHandlerRegisterFuture at :{}",self)
      readHandlerRegisterFuture.onComplete {
        case Success(handleEventListener) =>
          log.debug("readHandlerRegisterFuture success,tell self at :{}",self)
          self ! HandleEventListenerRegisterSuccess(handleEventListener)
        case Failure(exception) =>
          log.error("readHandlerRegisterFuture failed,tell self at :{}",self)
          self ! HandleEventListenerRegisterFailure(exception)
      }
      log.debug("associationHandler created,doing op at :{}",self)
      op(associationHandler)
      log.debug("associationHandler handled,waiting HandleEventListenerRegisterACK at:{}",self)
      context.become(waitHandleEventListenerRegisterACK())
  }

  private def waitHandleEventListenerRegisterACK(): Actor.Receive = {
    case HandleEventListenerRegisterSuccess(handleEventListener) =>
      log.debug("handle event lister register success,firing RegisterHandlerEventListener at :{}",self)
      //now should register the handleEventListener to the channel
      //via trigger user defined event
      channel.pipeline().fireUserEventTriggered(RegisterHandlerEventListener(handleEventListener))
      log.debug("RegisterHandlerEventListener fired,waiting ack at :{}",self)
      context.become(waitingRegisterHandlerEventListenerACK())
    case HandleEventListenerRegisterFailure(exception) =>
      log.error(exception,"handle event listener register error for channel :{} at :{}",channel,self)
      import qgame.akka.remote.transport.netty4.tcp.Netty4TcpTransport._

import scala.concurrent.ExecutionContext.Implicits.global
      channel.disconnect().onComplete{
        case Success(underlyingChannel)=>
          underlyingChannel.close()
        case Failure(e)=>
          log.error(e,"error occur when disconnect channel at :{}",self)
          channel.close()
      }
  }

  private def waitingRegisterHandlerEventListenerACK() :Actor.Receive = {
    case RegisterHandlerEventListenerACK =>
      log.debug("register handle event listener success :{}",self)
      channel.config().setAutoRead(true)
      log.debug("becoming associated at :{}",self)
      log.debug("going to schedule the flush tick ,duration :{} at :{}",flushDuration,self)
      import scala.concurrent.ExecutionContext.Implicits.global
      flushTickTask = context.system.scheduler.schedule(flushDuration/3,flushDuration,self,FlushTick)
      context.become(associated())
  }

  private def associated():Actor.Receive = {
    case FlushTick =>
      if (channel.isActive){
        channel.flush()
      }
    case ChannelInActive(underlyingChannel)=>
      //channel is broken
      log.debug("channel inactive ,current associated, channel:{} at:{}",underlyingChannel,self)
      context.stop(self)
    case ChannelExceptionCaught(underlyingChannel,exception)=>
      //channel exception
      log.error(exception,"channel inactive ,current associated, channel:{} at:{}",underlyingChannel,self)
    case RequestShutdown =>
      log.debug("request shutdown ,shutdown channel :{},at :{}",channel,self)
      channel.flush()
      sender() ! AssociatorShutdownACK
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    if (flushTickTask ne null){
      flushTickTask.cancel()
    }
    super.postStop()
  }
}

sealed trait AssociatorCommand

case object AssociateChannelInBound extends AssociatorCommand

case object AssociateChannelOutBound extends AssociatorCommand

case object RequestShutdown extends AssociatorCommand

case object FlushTick extends AssociatorCommand

case object AssociatorShutdownACK

sealed trait HandleEventListenerRegisterACK

case class HandleEventListenerRegisterSuccess(handleEventListener: HandleEventListener) extends HandleEventListenerRegisterACK

case class HandleEventListenerRegisterFailure(exception: Throwable) extends HandleEventListenerRegisterACK


case class Netty4TcpTransportAssociationHandle(channel: Channel,localAddress:Address,remoteAddress:Address) extends AssociationHandle {
  private val innerReadHandlerPromise = Promise[HandleEventListener]()
  override def disassociate(): Unit = {
    if (channel.isActive){
      channel.flush()
      import Netty4TcpTransport._
      channel.disconnect().onComplete{
        case Success(underlyingChannel)=>
          underlyingChannel.close()
        case Failure(e)=>
          channel.close()
      }
    }else{
      channel.close()
    }
  }

  override def write(payload: ByteString): Boolean = {
    if (channel.isActive){
      if (channel.isWritable){
        channel.write(Unpooled.wrappedBuffer(payload.asByteBuffer))
        true
      }else{
        channel.flush()
        false
      }
    }else{
      false
    }
  }
//FIXME BUG here,using inner filed instead,this is my first fuck bug!!!
  override def readHandlerPromise: Promise[HandleEventListener] = innerReadHandlerPromise
}

object Netty4TcpTransportAssociationHandle{
  def apply(channel:Channel):Netty4TcpTransportAssociationHandle = {
    this(channel,
      Netty4TcpTransport.inetAddressToActorAddress(channel.localAddress().asInstanceOf[InetSocketAddress]),
      Netty4TcpTransport.inetAddressToActorAddress(channel.remoteAddress().asInstanceOf[InetSocketAddress]))
  }
}
