# import os
from scapy.all import *
import time
import datetime

pkt = Ether(src="00:00:00:00:00:01", dst="00:00:00:00:00:02")/IPv6(
        src="2001:1:1::b", dst="2001:2:1::b", fl=0)/Raw(int(time.time()*1000))

# pkt = Ether(src="00:00:00:00:00:01", dst="00:00:00:00:00:0")/IPv6(
#         src="2001:1:1::b", dst="2001:02:1::b")/Raw(int(time.time()*1000))


hexdump(pkt)
sendp(pkt, count=100, inter=1./1000000, iface='h1-eth0')