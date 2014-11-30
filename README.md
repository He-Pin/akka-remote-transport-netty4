akka-remote-transport-netty4(developing)
============================
##Only TCP support now.on UDP no SSL no Docker support,will add soon and the document will udpate when I am not that busy.


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
benckmark
before
```
Starting benchmark of 500000 messages with burst size 5000 and payload size 100
It took 576 ms to deliver 15000 messages, throughtput 26041 msg/s, latest round-trip 142 ms, remaining 485000 of 500000
It took 643 ms to deliver 30000 messages, throughtput 46656 msg/s, latest round-trip 160 ms, remaining 455000 of 500000
It took 569 ms to deliver 30000 messages, throughtput 52724 msg/s, latest round-trip 141 ms, remaining 425000 of 500000
It took 526 ms to deliver 20000 messages, throughtput 38022 msg/s, latest round-trip 242 ms, remaining 405000 of 500000
It took 681 ms to deliver 30000 messages, throughtput 44052 msg/s, latest round-trip 252 ms, remaining 375000 of 500000
It took 545 ms to deliver 30000 messages, throughtput 55045 msg/s, latest round-trip 70 ms, remaining 345000 of 500000
It took 579 ms to deliver 30000 messages, throughtput 51813 msg/s, latest round-trip 89 ms, remaining 315000 of 500000
It took 574 ms to deliver 40000 messages, throughtput 69686 msg/s, latest round-trip 191 ms, remaining 275000 of 500000
It took 556 ms to deliver 15000 messages, throughtput 26978 msg/s, latest round-trip 118 ms, remaining 260000 of 500000
It took 529 ms to deliver 20000 messages, throughtput 37807 msg/s, latest round-trip 263 ms, remaining 240000 of 500000
It took 505 ms to deliver 30000 messages, throughtput 59405 msg/s, latest round-trip 157 ms, remaining 210000 of 500000
It took 505 ms to deliver 20000 messages, throughtput 39603 msg/s, latest round-trip 251 ms, remaining 190000 of 500000
It took 624 ms to deliver 25000 messages, throughtput 40064 msg/s, latest round-trip 216 ms, remaining 165000 of 500000
It took 610 ms to deliver 25000 messages, throughtput 40983 msg/s, latest round-trip 219 ms, remaining 140000 of 500000
It took 615 ms to deliver 25000 messages, throughtput 40650 msg/s, latest round-trip 189 ms, remaining 115000 of 500000
It took 620 ms to deliver 25000 messages, throughtput 40322 msg/s, latest round-trip 200 ms, remaining 90000 of 500000
It took 633 ms to deliver 20000 messages, throughtput 31595 msg/s, latest round-trip 214 ms, remaining 70000 of 500000
It took 732 ms to deliver 30000 messages, throughtput 40983 msg/s, latest round-trip 244 ms, remaining 40000 of 500000
It took 531 ms to deliver 25000 messages, throughtput 47080 msg/s, latest round-trip 50 ms, remaining 15000 of 500000
== It took 11515 ms to deliver 500000 messages, throughtput 43421 msg/s, max round-trip 263 ms, burst size 5000, payload size 100
```
after
```
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
