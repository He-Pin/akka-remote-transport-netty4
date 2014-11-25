package qgame.akka.remote.transport.netty4.tcp

import java.net._
import java.util.concurrent.{CancellationException, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

import akka.actor.{Address, ExtendedActorSystem, Props}
import akka.remote.transport.Transport.AssociationEventListener
import akka.remote.transport.{AssociationHandle, Transport}
import com.typesafe.config.Config
import io.netty.channel.{Channel, ChannelFuture, ChannelFutureListener}
import qgame.akka.remote.transport.netty4.Configuration
import qgame.akka.remote.transport.netty4.RemoteTransportMode.{TCP, UDP, UNKNOWN}

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future, Promise, blocking}


/**
 * Created by kerr.
 */
class Netty4TcpTransport(system: ExtendedActorSystem, netty4Configuration: Netty4Configuration) extends Transport {
  Netty4TcpTransport.init(system.name,netty4Configuration.HostName,schemeIdentifier)
  private val tcpTransportMasterActor = system.actorOf(Props.create(classOf[Netty4TcpTransportMasterActor],schemeIdentifier,netty4Configuration),"netty4Transport")
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
    val shutdownPromise:Promise[Boolean] = Promise[Boolean]()
    tcpTransportMasterActor ! Shutdown(shutdownPromise)
    shutdownPromise.future
  }

  override def maximumPayloadBytes: Int = netty4Configuration.MaximumFrameSize

  override def isResponsibleFor(address: Address): Boolean = true

  override def listen: Future[(Address, Promise[AssociationEventListener])] = {
    val listenPromise:Promise[(Address, Promise[AssociationEventListener])] = Promise[(Address, Promise[AssociationEventListener])]()
    tcpTransportMasterActor ! Listen(listenPromise)
    listenPromise.future
  }

  override def associate(remoteAddress: Address): Future[AssociationHandle] = {
    val associatePromise:Promise[AssociationHandle] = Promise[AssociationHandle]()
    //FIXME here maybe a issue ,maybe need block/future way
    tcpTransportMasterActor ! Associate(remoteAddress,associatePromise)
    associatePromise.future
  }
}

object Netty4TcpTransport{
  private val schemeRef = new AtomicReference[String]()
  private val systemRef = new AtomicReference[String]()
  private val hostnameRef = new AtomicReference[String]()
  private [Netty4TcpTransport] def init(system:String,hostname:String,scheme:String): Unit ={
    systemRef.lazySet(system)
    hostnameRef.lazySet(hostname)
    schemeRef.lazySet(scheme)
  }
  def inetAddressToActorAddress(addr:InetSocketAddress):Address = {
    Address(schemeRef.get(),systemRef.get(),hostnameRef.get(),addr.getPort)
  }
  val FrameLengthFieldLength = 4

  //FIXME when netty 4.1 comes out,using async dns
  def addressToSocketAddress(addr: Address)(implicit ex:ExecutionContext): Future[InetSocketAddress] = addr match {
    case Address(_, _, Some(host), Some(port)) ⇒ Future { blocking { new InetSocketAddress(InetAddress.getByName(host), port) } }
    case _                                     ⇒ Future.failed(new IllegalArgumentException(s"Address [$addr] does not contain host or port information."))
  }

  implicit def netty4ChannelFuture2ScalaFuture(channelFuture:ChannelFuture):Future[Channel] = {
    val promise = Promise[Channel]()
    channelFuture.addListener(new ChannelFutureListener {
      override def operationComplete(f: ChannelFuture): Unit = {
        if (f.isCancelled){
          promise.failure(new CancellationException("netty channel future is canceled"))
        }else{
          if (f.isSuccess){
            promise.success(f.channel())
          }else{
            promise.failure(f.cause())
          }
        }
      }
    })
    promise.future
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

  val HostAddress = try{
    InetAddress.getByName(HostName)
  }catch {
    case _: UnknownHostException =>
      throw new IllegalArgumentException("unknown host address,please check")
  }
  require(HostAddress.isInstanceOf[Inet4Address] || HostAddress.isInstanceOf[Inet6Address],"HostAddress must be ipv4 or ipv6 address")

  val MaximumFrameSize = configuration.getBytes("maximum-frame-size").getOrElse(throw new IllegalArgumentException("must set up maximum-frame-size in akka.remote.netty4.tcp")).toInt
  require(MaximumFrameSize > 32000,s"Setting 'maximum-frame-size' must be at least 32000 bytes,but you provide $MaximumFrameSize")

  val Backlog = configuration.getInt("backlog").getOrElse(throw new IllegalArgumentException("must set up backlog in akka.remote.netty4.tcp"))

  val TcpNoDelay = configuration.getBoolean("tcp-nodelay").getOrElse(throw new IllegalArgumentException("must set up tcp-nodelay in akka.remote.netty4.tcp"))

  val TcpKeepAlive = configuration.getBoolean("tcp-keepalive").getOrElse(throw new IllegalArgumentException("must set up tcp-keepalive in akka.remote.netty4.tcp"))

  val PreferNative = configuration.getBoolean("preferNative").getOrElse(throw new IllegalArgumentException("must set up preferNative in akka.remote.netty4.tcp"))

  val TcpReuseADDR = configuration.getBoolean("tcp-reuse-addr").getOrElse(throw new IllegalArgumentException("must set up tcp-reuse-addr in akka.remote.netty4.tcp"))
}
