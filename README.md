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
```
benckmark
Starting benchmark of 500000 messages with burst size 5000 and payload size 100
It took 597 ms to deliver 15000 messages, throughtput 25125 msg/s, latest round-trip 141 ms, remaining 485000 of 500000
It took 514 ms to deliver 25000 messages, throughtput 48638 msg/s, latest round-trip 65 ms, remaining 460000 of 500000
It took 536 ms to deliver 35000 messages, throughtput 65298 msg/s, latest round-trip 59 ms, remaining 425000 of 500000
It took 550 ms to deliver 40000 messages, throughtput 72727 msg/s, latest round-trip 55 ms, remaining 385000 of 500000
It took 502 ms to deliver 40000 messages, throughtput 79681 msg/s, latest round-trip 52 ms, remaining 345000 of 500000
It took 533 ms to deliver 40000 messages, throughtput 75046 msg/s, latest round-trip 72 ms, remaining 305000 of 500000
It took 554 ms to deliver 45000 messages, throughtput 81227 msg/s, latest round-trip 61 ms, remaining 260000 of 500000
It took 548 ms to deliver 40000 messages, throughtput 72992 msg/s, latest round-trip 72 ms, remaining 220000 of 500000
It took 502 ms to deliver 40000 messages, throughtput 79681 msg/s, latest round-trip 54 ms, remaining 180000 of 500000
It took 537 ms to deliver 45000 messages, throughtput 83798 msg/s, latest round-trip 63 ms, remaining 135000 of 500000
It took 540 ms to deliver 40000 messages, throughtput 74074 msg/s, latest round-trip 68 ms, remaining 95000 of 500000
It took 518 ms to deliver 40000 messages, throughtput 77220 msg/s, latest round-trip 63 ms, remaining 55000 of 500000
It took 558 ms to deliver 45000 messages, throughtput 80645 msg/s, latest round-trip 64 ms, remaining 10000 of 500000
== It took 7147 ms to deliver 500000 messages, throughtput 69959 msg/s, max round-trip 251 ms, burst size 5000, payload size 100
```
