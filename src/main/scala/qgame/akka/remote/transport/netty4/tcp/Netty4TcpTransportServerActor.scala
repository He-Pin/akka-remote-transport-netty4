package qgame.akka.remote.transport.netty4.tcp

import java.net.InetSocketAddress

import akka.actor._
import akka.remote.transport.AssociationHandle
import akka.remote.transport.Transport.{InboundAssociation, AssociationEventListener}
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.channel.epoll.{EpollEventLoopGroup, EpollServerSocketChannel, EpollSocketChannel}
import io.netty.channel.group.{ChannelGroup, DefaultChannelGroup}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.{NioServerSocketChannel, NioSocketChannel}
import io.netty.handler.codec.{LengthFieldBasedFrameDecoder, LengthFieldPrepender}
import qgame.akka.remote.transport.netty4.Platform

import scala.concurrent.Promise
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
 * Created by kerr.
 */
class Netty4TcpTransportServerActor(configuration:Netty4Configuration) extends Actor with ActorLogging{
  private var serverBootstrap: ServerBootstrap = _
  private var serverParentEventLoopGroup: EventLoopGroup = _
  private var serverChildEventLoopGroup: EventLoopGroup = _
  private var allChannelGroup: ChannelGroup = _
  private val address: Address = Netty4TcpTransport.inetAddressToActorAddress(
    new InetSocketAddress(configuration.HostAddress,configuration.Port))

  override def receive: Receive = {
    case Init =>
      try{
        val (initAddress,native) = initNettySever()
        log.debug("netty4 tcp transport server init success at: {},with native: {}",initAddress,native)
        sender() ! InitServerSuccess
        log.debug("netty4 tcp transport initialized.waiting Listen command")
        context.become(initialized())
      } catch {
        case NonFatal(e) =>
          log.error(e,"netty4 tcp transport server init error")
          sender() ! InitServerFailure(e)
          context.stop(self)
      }
  }

  private def initialized():Actor.Receive = {
    case Listen(listenPromise)=>
      log.debug("Listen command,completing it,switch to waiting")
      listenPromise.complete(listen(sender()))
      log.debug("becoming waiting channel bound")
      context.become(waitingChannelBound())
  }

  private def waitingChannelBound() :Actor.Receive = {
    case ChannelBound(serverChannel) =>
      log.debug("channel bound at :{}",serverChannel)
      log.debug("becoming waitAssociationEventListenerRegister for :{}",serverChannel)
      context.become(waitAssociationEventListenerRegister(serverChannel))
  }


  private def listen(replyTo:ActorRef):Try[(Address, Promise[AssociationEventListener])] = {
    Try{
      val associationEventListenerPromise = Promise[AssociationEventListener]()
      val associationEventListenerFuture = associationEventListenerPromise.future
      //do the register to future,the akka remote will complete the associationEventListenerPromise
      import scala.concurrent.ExecutionContext.Implicits.global
      associationEventListenerFuture.onComplete{
        case Success(associationEventListener)=>
          //here we need update the associate listener
          self ! AssociationEventListenerRegisterSuccess(associationEventListener)
        case Failure(e)=>
          //the associate listener register failed
          self ! AssociationEventListenerRegisterFailure(e)
      }
      //do bind
      val serverChannelFuture = serverBootstrap.bind().addListener(new ChannelFutureListener {
        override def operationComplete(future: ChannelFuture): Unit = {
          if (future.isSuccess){
            val underlyingChannel = future.channel()
            allChannelGroup.add(underlyingChannel)
            replyTo ! ListenSuccess(underlyingChannel.localAddress().asInstanceOf[InetSocketAddress])
            log.debug(s"netty4 tcp transport bound: $underlyingChannel")
          }else{
            val cause = future.cause()
            replyTo ! ListenFailure(cause)
            log.error(cause,"netty4 tcp transport bind failed")
          }
        }
      })
      serverChannelFuture.channel().config().setAutoRead(false)
      val serverChannel  = serverChannelFuture.sync().channel()
      self ! ChannelBound(serverChannel)
      (address,associationEventListenerPromise)
    }
  }

  private def waitAssociationEventListenerRegister(serverChannel:Channel) :Actor.Receive = {
    case AssociationEventListenerRegisterSuccess(listener)=>
      log.debug("association event listener register success for :{} at :{}",serverChannel,self)
      log.debug("becoming associationEventListenerRegistered for :{} at :{}",serverChannel,self)
      context.become(associationEventListenerRegistered(serverChannel,listener))
      log.debug("set serverChannel auto read to true for :{} at :{}",serverChannel,self)
      serverChannel.config().setAutoRead(true)
    case AssociationEventListenerRegisterFailure(exception)=>
      log.error(exception,"akka remote association event listener register failed,shutdown now")
      shutdown()
  }

  private def associationEventListenerRegistered(serverChannel:Channel,listener:AssociationEventListener) : Actor.Receive = {
    case ChannelActive(channel)=>
      allChannelGroup.add(channel)
      val op = (handler:AssociationHandle) => {
        log.debug(
          s"""
            |~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            |operation in Netty4TcpTransportServerActor
            |client connected in need associate
            |server channel : $serverChannel
            |client channel : $channel
            |~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          """.stripMargin)
        log.debug("notify server listener InboundAssociation at :{}",self)
        listener.notify(InboundAssociation(handler))
      }
      val remoteAddress = Netty4TcpTransport.inetAddressToActorAddress(channel.remoteAddress().asInstanceOf[InetSocketAddress])
      log.debug("remote client connected in,remote address :{}",remoteAddress)
      val associator = context.actorOf(Props.create(classOf[Netty4TcpTransportAssociator],
        remoteAddress,
        configuration.FlushInternal,
        channel,
        boolean2Boolean(configuration.AutoFlush),
        op))
      associator ! AssociateChannelInBound
    case ChannelInActive(channel) =>
      //add to channel group
      allChannelGroup.remove(channel)
    case Shutdown(shutdownPromise)=>
      shutdownPromise.complete{
        Try{
          shutdown()
          true
        }
      }
  }

  private def shutdown(): Unit ={
    //FIXME handle the shutdown right
    try {
      allChannelGroup.close().sync()
      serverParentEventLoopGroup.shutdownGracefully().sync()
      serverChildEventLoopGroup.shutdownGracefully().sync()
      log.debug("shutdown success")
      context.stop(self)
    } catch {
      case NonFatal(e)=>
        log.error(e,"error occurred when shutdown")
    }
  }

  private def initNettySever():(InetSocketAddress,Boolean) = {
    val bindAddress = new InetSocketAddress(configuration.HostAddress,configuration.Port)
    val native = if (Platform.isLinux && configuration.PreferNative) true else false
    serverBootstrap = new ServerBootstrap()
    serverParentEventLoopGroup = if (native){
      new EpollEventLoopGroup()
    }else{
      new NioEventLoopGroup()
    }
    serverChildEventLoopGroup = if (native){
      new EpollEventLoopGroup()
    }else{
      new NioEventLoopGroup()
    }
    serverBootstrap.group(serverParentEventLoopGroup, serverChildEventLoopGroup)
    allChannelGroup = new DefaultChannelGroup(new DefaultEventLoop())

    val nettyServerMasterActor = self
    Netty4TcpTransport.setupServerBootStrapOption(serverBootstrap,configuration)
    serverBootstrap.channel{
      if (native){
        classOf[EpollServerSocketChannel]
      }else{
        classOf[NioServerSocketChannel]
      }
    }.localAddress(bindAddress)
      .childHandler{
      if (native){
        new ChannelInitializer[EpollSocketChannel] {
          override def initChannel(socketChannel: EpollSocketChannel): Unit = {
            val pipeline = socketChannel.pipeline()
            pipeline.addLast("frameEncoder", new LengthFieldPrepender(4, false))
            pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(configuration.MaximumFrameSize,
              0,
              Netty4TcpTransport.FrameLengthFieldLength,
              0,
              Netty4TcpTransport.FrameLengthFieldLength,
              true))
            pipeline.addLast("serverHandler",new Netty4TcpTransportHandler(nettyServerMasterActor))
          }
        }
      }else{
        new ChannelInitializer[NioSocketChannel] {
          override def initChannel(socketChannel: NioSocketChannel): Unit = {
            val pipeline = socketChannel.pipeline()
            pipeline.addLast("frameEncoder", new LengthFieldPrepender(4, false))
            pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(configuration.MaximumFrameSize,
              0,
              Netty4TcpTransport.FrameLengthFieldLength,
              0,
              Netty4TcpTransport.FrameLengthFieldLength,
              true))
            pipeline.addLast("serverHandler",new Netty4TcpTransportHandler(nettyServerMasterActor))
          }
        }
      }
    }
    (bindAddress,native)
  }
}
case class ChannelBound(channel:Channel)

case class AssociationEventListenerRegisterSuccess(listener:AssociationEventListener)
case class AssociationEventListenerRegisterFailure(exception:Throwable)
