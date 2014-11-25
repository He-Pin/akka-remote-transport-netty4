package qgame.akka.remote.transport.netty4.tcp

import java.net._
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{CancellationException, TimeUnit}

import akka.actor.{Address, ExtendedActorSystem, Props}
import akka.remote.transport.Transport.AssociationEventListener
import akka.remote.transport.{AssociationHandle, Transport}
import com.typesafe.config.Config
import io.netty.bootstrap.{Bootstrap, ServerBootstrap}
import io.netty.channel.epoll.EpollChannelOption
import io.netty.channel.{Channel, ChannelFuture, ChannelFutureListener, ChannelOption}
import qgame.akka.remote.transport.netty4.RemoteTransportMode.{TCP, UDP, UNKNOWN}
import qgame.akka.remote.transport.netty4.{Configuration, Platform}

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future, Promise, blocking}
import scala.language.implicitConversions


/**
 * Created by kerr.
 */
class Netty4TcpTransport(system: ExtendedActorSystem, netty4Configuration: Netty4Configuration) extends Transport {
  Netty4TcpTransport.init(system.name, netty4Configuration.HostName, schemeIdentifier)
  private val tcpTransportMasterActor = system.actorOf(Props.create(classOf[Netty4TcpTransportMasterActor], schemeIdentifier, netty4Configuration), "netty4Transport")
  tcpTransportMasterActor ! Init

  def this(system: ExtendedActorSystem, config: Config) = {
    this(system, Netty4Configuration(Configuration(config)))
  }

  override def schemeIdentifier: String = {
    netty4Configuration.TransportMode match {
      case TCP => TCP.toString
      case UDP => UDP.toString
    }
  }

  override def shutdown(): Future[Boolean] = {
    val shutdownPromise: Promise[Boolean] = Promise[Boolean]()
    tcpTransportMasterActor ! Shutdown(shutdownPromise)
    shutdownPromise.future
  }

  override def maximumPayloadBytes: Int = netty4Configuration.MaximumFrameSize

  override def isResponsibleFor(address: Address): Boolean = true

  override def listen: Future[(Address, Promise[AssociationEventListener])] = {
    val listenPromise: Promise[(Address, Promise[AssociationEventListener])] = Promise[(Address, Promise[AssociationEventListener])]()
    tcpTransportMasterActor ! Listen(listenPromise)
    listenPromise.future
  }

  override def associate(remoteAddress: Address): Future[AssociationHandle] = {
    val associatePromise: Promise[AssociationHandle] = Promise[AssociationHandle]()
    //FIXME here maybe a issue ,maybe need block/future way
    tcpTransportMasterActor ! Associate(remoteAddress, associatePromise)
    associatePromise.future
  }
}

object Netty4TcpTransport {
  private val schemeRef = new AtomicReference[String]()
  private val systemRef = new AtomicReference[String]()
  private val hostnameRef = new AtomicReference[String]()

  private[Netty4TcpTransport] def init(system: String, hostname: String, scheme: String): Unit = {
    systemRef.lazySet(system)
    hostnameRef.lazySet(hostname)
    schemeRef.lazySet(scheme)
  }

  def inetAddressToActorAddress(addr: InetSocketAddress): Address = {
    Address(schemeRef.get(), systemRef.get(), hostnameRef.get(), addr.getPort)
  }

  val FrameLengthFieldLength = 4

  //FIXME when netty 4.1 comes out,using async dns
  def addressToSocketAddress(addr: Address)(implicit ex: ExecutionContext): Future[InetSocketAddress] = addr match {
    case Address(_, _, Some(host), Some(port)) ⇒ Future {
      blocking {
        new InetSocketAddress(InetAddress.getByName(host), port)
      }
    }
    case _ ⇒ Future.failed(new IllegalArgumentException(s"Address [$addr] does not contain host or port information."))
  }

  implicit def netty4ChannelFuture2ScalaFuture(channelFuture: ChannelFuture): Future[Channel] = {
    val promise = Promise[Channel]()
    channelFuture.addListener(new ChannelFutureListener {
      override def operationComplete(f: ChannelFuture): Unit = {
        if (f.isCancelled) {
          promise.failure(new CancellationException("netty channel future is canceled"))
        } else {
          if (f.isSuccess) {
            promise.success(f.channel())
          } else {
            promise.failure(f.cause())
          }
        }
      }
    })
    promise.future
  }

  def setupServerBootStrapOption(bootstrap: ServerBootstrap, configuration: Netty4Configuration): Unit = {
    val native = if (Platform.isLinux && configuration.PreferNative) true else false
    bootstrap.option(ChannelOption.SO_BACKLOG, int2Integer(configuration.Backlog))
      .option(ChannelOption.SO_REUSEADDR, boolean2Boolean(configuration.TcpReuseADDR))
      .childOption(ChannelOption.TCP_NODELAY, boolean2Boolean(configuration.TcpNoDelay))
      .childOption(ChannelOption.SO_KEEPALIVE, boolean2Boolean(configuration.TcpKeepAlive))
    if (configuration.SendBufferSize > 0){
      bootstrap.childOption(ChannelOption.SO_SNDBUF,int2Integer(configuration.SendBufferSize))
    }
    if (configuration.ReceiveBufferSize > 0){
      bootstrap.childOption(ChannelOption.SO_RCVBUF,int2Integer(configuration.ReceiveBufferSize))
    }
    if (configuration.WriteBufferHighWaterMark > 0){
      bootstrap.childOption(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK,int2Integer(configuration.WriteBufferHighWaterMark))
    }
    if (configuration.WriteBufferLowWaterMark > 0){
      bootstrap.childOption(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK,int2Integer(configuration.WriteBufferLowWaterMark))
    }
    if (configuration.WriteSpinCount > 0){
      bootstrap.childOption(ChannelOption.WRITE_SPIN_COUNT,int2Integer(configuration.WriteSpinCount))
    }
    if (configuration.MaxMessagesPerRead >0){
      bootstrap.childOption(ChannelOption.MAX_MESSAGES_PER_READ,int2Integer(configuration.MaxMessagesPerRead))
    }
    if (native){
      if (configuration.TcpCork){
        bootstrap.childOption(EpollChannelOption.TCP_CORK,boolean2Boolean(configuration.TcpCork))
      }
      if (configuration.TcpReusePort){
        bootstrap.childOption(EpollChannelOption.SO_REUSEPORT,boolean2Boolean(configuration.TcpReusePort))
      }
      if (configuration.TcpKeepIdle > 0){
        bootstrap.childOption(EpollChannelOption.TCP_KEEPIDLE,int2Integer(configuration.TcpKeepIdle))
      }
      if (configuration.TcpKeepInternal > 0){
        bootstrap.childOption(EpollChannelOption.TCP_KEEPINTVL,int2Integer(configuration.TcpKeepInternal))
      }
      if (configuration.TcpKeepCount > 0){
        bootstrap.childOption(EpollChannelOption.TCP_KEEPCNT,int2Integer(configuration.TcpKeepCount))
      }
    }
  }

  def setupClientBootStrapOption(bootstrap:Bootstrap,configuration:Netty4Configuration): Unit ={
    val native = if (Platform.isLinux && configuration.PreferNative) true else false
      bootstrap.option(ChannelOption.TCP_NODELAY, boolean2Boolean(configuration.TcpNoDelay))
      .option(ChannelOption.SO_KEEPALIVE, boolean2Boolean(configuration.TcpKeepAlive))
    if (configuration.SendBufferSize > 0){
      bootstrap.option(ChannelOption.SO_SNDBUF,int2Integer(configuration.SendBufferSize))
    }
    if (configuration.ReceiveBufferSize > 0){
      bootstrap.option(ChannelOption.SO_RCVBUF,int2Integer(configuration.ReceiveBufferSize))
    }
    if (configuration.WriteBufferHighWaterMark > 0){
      bootstrap.option(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK,int2Integer(configuration.WriteBufferHighWaterMark))
    }
    if (configuration.WriteBufferLowWaterMark > 0){
      bootstrap.option(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK,int2Integer(configuration.WriteBufferLowWaterMark))
    }
    if (configuration.WriteSpinCount > 0){
      bootstrap.option(ChannelOption.WRITE_SPIN_COUNT,int2Integer(configuration.WriteSpinCount))
    }
    if (configuration.MaxMessagesPerRead >0){
      bootstrap.option(ChannelOption.MAX_MESSAGES_PER_READ,int2Integer(configuration.MaxMessagesPerRead))
    }
    if (native){
      if (configuration.TcpCork){
        bootstrap.option(EpollChannelOption.TCP_CORK,boolean2Boolean(configuration.TcpCork))
      }
      if (configuration.TcpReusePort){
        bootstrap.option(EpollChannelOption.SO_REUSEPORT,boolean2Boolean(configuration.TcpReusePort))
      }
      if (configuration.TcpKeepIdle > 0){
        bootstrap.option(EpollChannelOption.TCP_KEEPIDLE,int2Integer(configuration.TcpKeepIdle))
      }
      if (configuration.TcpKeepInternal > 0){
        bootstrap.option(EpollChannelOption.TCP_KEEPINTVL,int2Integer(configuration.TcpKeepInternal))
      }
      if (configuration.TcpKeepCount > 0){
        bootstrap.option(EpollChannelOption.TCP_KEEPCNT,int2Integer(configuration.TcpKeepCount))
      }
    }
  }
}


case class Netty4Configuration(configuration: Configuration) {
  val TransportMode = configuration.getString("transport-protocol").map {
    case "tcp" => TCP
    case "udp" => UNKNOWN //FIXME add udp support latter
    case unknown => UNKNOWN
  }.getOrElse(throw new IllegalArgumentException("must set up transport-protocol in akka.remote.netty4.tcp"))

  require(TransportMode != UNKNOWN, "transport could not be unknown,only tcp support for now")

  val ConnectionTimeOut = Duration(configuration.getDuration("connection-timeout", timeUnit = TimeUnit.SECONDS).getOrElse(5l), TimeUnit.SECONDS)

  val Port = configuration.getInt("port").getOrElse(throw new IllegalArgumentException("must set up port in akka.remote.netty4.tcp"))

  val HostName = configuration.getString("hostname").map {
    case "" => InetAddress.getLocalHost.getHostAddress
    case hostname => hostname
  }.getOrElse(throw new IllegalArgumentException("must set up hostname in akka.remote.netty4.tcp"))

  val HostAddress = try {
    InetAddress.getByName(HostName)
  } catch {
    case _: UnknownHostException =>
      throw new IllegalArgumentException("unknown host address,please check")
  }
  require(HostAddress.isInstanceOf[Inet4Address] || HostAddress.isInstanceOf[Inet6Address], "HostAddress must be ipv4 or ipv6 address")

  val MaximumFrameSize = configuration.getBytes("maximum-frame-size").getOrElse(throw new IllegalArgumentException("must set up maximum-frame-size in akka.remote.netty4.tcp")).toInt
  require(MaximumFrameSize > 32000, s"Setting 'maximum-frame-size' must be at least 32000 bytes,but you provide $MaximumFrameSize")

  val Backlog = configuration.getInt("backlog").getOrElse(throw new IllegalArgumentException("must set up backlog in akka.remote.netty4.tcp"))

  val TcpNoDelay = configuration.getBoolean("tcp-nodelay").getOrElse(throw new IllegalArgumentException("must set up tcp-nodelay in akka.remote.netty4.tcp"))

  val TcpKeepAlive = configuration.getBoolean("tcp-keepalive").getOrElse(throw new IllegalArgumentException("must set up tcp-keepalive in akka.remote.netty4.tcp"))

  val PreferNative = configuration.getBoolean("preferNative").getOrElse(throw new IllegalArgumentException("must set up preferNative in akka.remote.netty4.tcp"))

  val TcpReuseADDR = configuration.getBoolean("tcp-reuse-addr").getOrElse(throw new IllegalArgumentException("must set up tcp-reuse-addr in akka.remote.netty4.tcp"))

  val SendBufferSize = configuration.getBytes("send-buffer-size").getOrElse(throw new IllegalArgumentException("must set up send-buffer-size in akka.remote.netty4.tcp")).toInt
  require(SendBufferSize >= 0, s"Setting 'send-buffer-size' could not < 0 ,but you provide $SendBufferSize")

  val ReceiveBufferSize = configuration.getBytes("receive-buffer-size").getOrElse(throw new IllegalArgumentException("must set up receive-buffer-size in akka.remote.netty4.tcp")).toInt
  require(ReceiveBufferSize >= 0, s"Setting 'receive-buffer-size' could not < 0 ,but you provide $ReceiveBufferSize")

  val WriteBufferHighWaterMark = configuration.getBytes("write-buffer-high-water-mark").getOrElse(throw new IllegalArgumentException("must set up write-buffer-high-water-mark in akka.remote.netty4.tcp")).toInt
  require(WriteBufferHighWaterMark >= 0, s"Setting 'write-buffer-high-water-mark' could not < 0 ,but you provide $WriteBufferHighWaterMark")

  val WriteBufferLowWaterMark = configuration.getBytes("write-buffer-low-water-mark").getOrElse(throw new IllegalArgumentException("must set up write-buffer-low-water-mark in akka.remote.netty4.tcp")).toInt
  require(WriteBufferLowWaterMark >= 0, s"Setting 'write-buffer-low-water-mark' could not < 0 ,but you provide $WriteBufferLowWaterMark")

  val WriteSpinCount = configuration.getInt("write-spin-count").getOrElse(throw new IllegalArgumentException("must set up write-spin-count in akka.remote.netty4.tcp"))
  require(WriteSpinCount >= 0, s"Setting 'write-spin-count' must > 0 ,but you provide $WriteSpinCount")

  val MaxMessagesPerRead = configuration.getInt("max-messages-per-read").getOrElse(throw new IllegalArgumentException("must set up max-messages-per-read in akka.remote.netty4.tcp"))
  require(MaxMessagesPerRead >= 0, s"Setting 'max-messages-per-read' must > 0 ,but you provide $MaxMessagesPerRead")

  val PerformancePreferencesConnectionTime = configuration.getInt("performance-preferences-connection-time").getOrElse(throw new IllegalArgumentException("must set up performance-preferences-connection-time in akka.remote.netty4.tcp"))

  val PerformancePreferencesLatency = configuration.getInt("performance-preferences-connection-time").getOrElse(throw new IllegalArgumentException("must set up performance-preferences-connection-time in akka.remote.netty4.tcp"))

  val PerformancePreferencesBandwidth = configuration.getInt("performance-preferences-connection-time").getOrElse(throw new IllegalArgumentException("must set up performance-preferences-connection-time in akka.remote.netty4.tcp"))

  val TcpCork = configuration.getBoolean("tcp-cork").getOrElse(throw new IllegalArgumentException("must set up tcp-cork in akka.remote.netty4.tcp"))
  require(!(TcpCork&&TcpNoDelay), "tcp-cork && tcp-nodelay should not set at the same time")

  val TcpReusePort = configuration.getBoolean("tcp-reuse-port").getOrElse(throw new IllegalArgumentException("must set up tcp-reuse-port in akka.remote.netty4.tcp"))

  val TcpKeepIdle = configuration.getInt("tcp-keep-idle").getOrElse(throw new IllegalArgumentException("must set up tcp-keep-idle in akka.remote.netty4.tcp"))
  require(TcpKeepIdle >= 0, s"Setting 'tcp-keep-idle' must >= 0 ,but you provide $TcpKeepIdle")

  val TcpKeepInternal = configuration.getInt("tcp-keep-internal").getOrElse(throw new IllegalArgumentException("must set up tcp-keep-internal in akka.remote.netty4.tcp"))
  require(TcpKeepInternal >= 0, s"Setting 'tcp-keep-internal' must >= 0 ,but you provide $TcpKeepInternal")

  val TcpKeepCount = configuration.getInt("tcp-keep-count").getOrElse(throw new IllegalArgumentException("must set up tcp-keep-count in akka.remote.netty4.tcp"))
  require(TcpKeepCount >= 0, s"Setting 'tcp-keep-count' must >= 0 ,but you provide $TcpKeepCount")

  val FlushInternal = Duration(configuration.getDuration("flush-internal", timeUnit = TimeUnit.MILLISECONDS).getOrElse(5l), TimeUnit.MILLISECONDS)
  
}
