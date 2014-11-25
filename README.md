akka-remote-transport-netty4
============================

using it like this
```
  akka {
    remote {
      startup-timeout = 10 s
      enabled-transports = ["akka.remote.netty4.tcp"]
      netty4 {
        tcp {
          port = 2554
        }
      }
    }
  }
}
```
