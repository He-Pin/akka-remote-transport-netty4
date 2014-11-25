
version := "1.0"

name := "netty4-akka-remote-transport"

scalaVersion := "2.11.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" % "akka-remote_2.11" % "2.3.7",
  "io.netty" % "netty-all" % "4.1.0.Beta3",
  "io.netty" % "netty-transport-native-epoll" % "4.1.0.Beta3" classifier "linux-x86_64"
)