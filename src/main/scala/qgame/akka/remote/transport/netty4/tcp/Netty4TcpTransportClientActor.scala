package qgame.akka.remote.transport.netty4.tcp

import java.net.{ConnectException, UnknownHostException}
import java.util.concurrent.CancellationException

import akka.actor._
import akka.remote.transport.AssociationHandle
import akka.remote.transport.Transport.InvalidAssociationException
import io.netty.bootstrap
import io.netty.bootstrap.Bootstrap
import io.netty.channel._
import io.netty.channel.epoll.{EpollEventLoopGroup, EpollSocketChannel}
import io.netty.channel.group.{ChannelGroup, DefaultChannelGroup}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.{LengthFieldBasedFrameDecoder, LengthFieldPrepender}
import qgame.akka.remote.transport.netty4.Platform

import scala.util.control.{NoStackTrace, NonFatal}
import scala.util.{Failure, Success}

/**
 * Created by kerr.
 */
class Netty4TcpTransportClientActor(configuration:Netty4Configuration) extends Actor with ActorLogging{
  private var clientBootstrap :Bootstrap = _
  private var clientEventLoopGroup :EventLoopGroup = _
  private var allChannelGroup: ChannelGroup = _
  private var associatedChannel:Map[Address,ActorRef] = Map.empty

  override def receive: Receive = {
    case Init =>
      try{
        val native = initNettyClient()
        log.debug("netty4 tcp transport client init success with native: {}",native)
        sender() ! InitClientSuccess
        log.debug("netty4 tcp transport initialized.waiting Associate command")
        context.become(initialized())
      } catch {
        case NonFatal(e) =>
          log.error(e,"netty4 tcp transport client init error")
          sender() ! InitClientFailure(e)
          context.stop(self)
      }
  }

  private def initialized():Actor.Receive ={
    case Associate(remoteAddress,associatePromise)=>
      log.debug("associate command received ,for remote address :{},at :{}",remoteAddress,self)
      //Note the socket is connect to remote ,so I think this socket need close when a new socket is connected to the target remote
      associatedChannel.get(remoteAddress).foreach{
        previousAssociator =>
          previousAssociator ! RequestShutdown(duplicated = true)
      }
      import scala.concurrent.ExecutionContext.Implicits.global
      Netty4TcpTransport.addressToSocketAddress(remoteAddress).onComplete{
        case Success(remoteSocketAddress) =>
            import qgame.akka.remote.transport.netty4.tcp.Netty4TcpTransport._
            clientBootstrap.connect(remoteSocketAddress).onComplete{
              case Success(channel) =>
                //when the channel is connected to the remote,
                //we need to associate it
                val op = (handler:AssociationHandle) => {
                  log.debug(
                    s"""
                      |~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                      |operation in Netty4TcpTransportClientActor
                      |connected to remote socket,need associate
                      |remoteAddress :$remoteAddress
                      |remoteSocketAddress :$remoteSocketAddress
                      |client channel :$channel
                      |~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    """.stripMargin)
                  log.debug("success client associatePromise with handler at :{}",self)
                  associatePromise.success(handler)
                }
                val associator = context.actorOf(Props.create(classOf[Netty4TcpTransportAssociator],
                  remoteAddress,
                  configuration.FlushInternal,
                  channel,
                  boolean2Boolean(configuration.AutoFlush),
                  op))
                associator ! AssociateChannelOutBound
              case Failure(exception)=>
                log.error(exception,"could not connect to remote socket address :{},for actor address :{}",remoteSocketAddress,remoteAddress)
                val associateException = exception match {
                  case c: CancellationException =>
                    new Netty4TransportException("Connection was cancelled") with NoStackTrace
                  case NonFatal(t) =>
                    new Netty4TransportException(t.getMessage, t.getCause) with NoStackTrace
                  case e => throw e
                }
                log.debug("fail client associatePromise with exception:{} at :{}",exception,self)
                associatePromise.failure(new Netty4TransportException("could not connected to remote socket address",associateException))
            }
        case Failure(exception) =>
          log.error(exception,"get address to socket address failed ,address :{}",remoteAddress)
          val associateException = exception match {
            case u @ (_: UnknownHostException | _: SecurityException | _: ConnectException)=>
              throw new InvalidAssociationException(u.getMessage, u.getCause)
            case NonFatal(t) =>
              throw new Netty4TransportException(t.getMessage, t.getCause) with NoStackTrace
            case e => throw  e
          }
          associatePromise.failure(associateException)
      }
    case Associated(associatedAddress)=>
      associatedChannel += (associatedAddress -> sender())
    case DeAssociated(deAssociatedAddress)=>
      associatedChannel -= deAssociatedAddress
  }



  private def initNettyClient(): Boolean ={
    val native = if (Platform.isLinux && configuration.PreferNative) true else false
    clientBootstrap = new bootstrap.Bootstrap()
    clientEventLoopGroup = if (native) new EpollEventLoopGroup() else new NioEventLoopGroup()
    //this may need change
    allChannelGroup = new DefaultChannelGroup(new DefaultEventLoop())
    val nettyClientMasterActor = self
    Netty4TcpTransport.setupClientBootStrapOption(clientBootstrap,configuration)
    clientBootstrap.group(clientEventLoopGroup).channel{
      if (native){
        classOf[EpollSocketChannel]
      }else{
        classOf[NioSocketChannel]
      }
    }.handler{
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
            pipeline.addLast("clientHandler",new Netty4TcpTransportHandler(nettyClientMasterActor))          }
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
            pipeline.addLast("clientHandler",new Netty4TcpTransportHandler(nettyClientMasterActor))
          }
        }
      }
    }
    native
  }
}
