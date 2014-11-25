package qgame.akka.remote.transport.netty4.tcp

import java.net.InetSocketAddress

import akka.OnlyCauseStackTrace
import akka.actor._
import akka.remote.transport.AssociationHandle
import akka.remote.transport.Transport.AssociationEventListener

import scala.concurrent.Promise

/**
 * Created by kerr.
 */
class Netty4TcpTransportMasterActor(scheme: String, configuration: Netty4Configuration) extends Actor with ActorLogging{
  private var serverActor: ActorRef = _
  private var clientActor: ActorRef = _
  //TODO using FSM
  override def receive: Receive = {
    case listen: Listen =>
      serverActor ! listen
    case ListenSuccess(boundAddress) =>
      log.debug("listen success at address :{}",boundAddress)
      log.debug("netty4 tcp transport server actor is listening ,becoming listenSuccess")
      context.become(listenSuccess(boundAddress))
    case Associate(_,associatePromise) =>
      val cause = new Netty4TransportException("netty4 server is not listening now")
      associatePromise.failure(cause)
      log.error(cause,"netty4 tcp transport server is not listening")
    case Shutdown(shutdownPromise) =>

    case Init =>
      serverActor ! Init
      clientActor ! Init
  }

  private def listenSuccess(boundAddress :InetSocketAddress) :Actor.Receive = {
    case associate: Associate =>
      log.debug("associated message received,to remote address :{}",associate.remoteAddress)
      clientActor ! associate
      //here should check the server bound event
      //if the server is not bound ,then failed it
      //update :Checked in the master
  }


  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    serverActor = context.actorOf(Props.create(classOf[Netty4TcpTransportServerActor], configuration), "server")
    clientActor = context.actorOf(Props.create(classOf[Netty4TcpTransportClientActor], configuration), "client")
    super.preStart()
  }
}

class Netty4TransportException(msg: String, cause: Throwable) extends RuntimeException(msg, cause) with OnlyCauseStackTrace {
  def this(msg: String) = this(msg, null)
}

sealed trait Netty4TransportCommand

sealed trait Netty4TransportACK

case object Init extends Netty4TransportCommand

case object InitClientSuccess extends Netty4TransportACK

case class InitClientFailure(exception:Throwable) extends Netty4TransportACK

case object InitServerSuccess extends Netty4TransportACK

case class InitServerFailure(exception:Throwable) extends Netty4TransportACK

case class Shutdown(shutdownPromise: Promise[Boolean]) extends Netty4TransportCommand

case object ShutdownClientSuccess extends Netty4TransportACK

case class ShutdownClientFailure(exception:Throwable) extends Netty4TransportACK

case object ShutdownServerSuccess extends Netty4TransportACK

case class ShutdownServerFailure(exception:Throwable) extends Netty4TransportACK

case class Listen(listenPromise: Promise[(Address, Promise[AssociationEventListener])]) extends Netty4TransportCommand

case class ListenSuccess(bindAddress:InetSocketAddress) extends Netty4TransportACK

case class ListenFailure(exception:Throwable) extends Netty4TransportACK

case class Associate(remoteAddress: Address, associatePromise: Promise[AssociationHandle]) extends Netty4TransportCommand

case class AssociateSuccess(remoteAddress:InetSocketAddress,localAddress:InetSocketAddress) extends Netty4TransportACK

case class AssociateFailure(exception:Throwable) extends Netty4TransportACK
