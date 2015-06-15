package qgame.akka.remote.transport.netty4

/**
 * Created by kerr.
 */
object RemoteTransportMode extends Enumeration {
  type RemoteTransportMode = Value
  val TCP = Value("tcp")
  val UDP = Value("udp")
  val UNKNOWN = Value("unknown")
}
