
version := "1.0"

name := "netty4-akka-remote-transport"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" % "akka-remote_2.11" % "2.3.11",
  "io.netty" % "netty-all" % "4.1.0.Beta5",
  "io.netty" % "netty-transport-native-epoll" % "4.1.0.Beta5" classifier "linux-x86_64"
)