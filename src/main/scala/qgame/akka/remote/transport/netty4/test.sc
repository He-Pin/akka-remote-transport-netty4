import java.net.{Inet4Address, Inet6Address, InetAddress, UnknownHostException}

import akka.actor.Address

val hostname = "127.0.0.1"
val HostAddress = try{
  InetAddress.getByName(hostname)
}catch {
  case _: UnknownHostException =>
    throw new IllegalArgumentException("unknown host address,please check")
}

InetAddress.getLocalHost.getHostAddress

require(HostAddress.isInstanceOf[Inet4Address] || HostAddress.isInstanceOf[Inet6Address],"HostAddress must be ip4 or ip6 address")

Address("tcp","engine",hostname,8888)