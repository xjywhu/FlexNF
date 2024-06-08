# import os
from scapy.all import *

pkt = Ether(src="00:00:00:00:00:1B", dst="00:00:00:00:00:1C")/IPv6(dst="2001:3:1::b",src="2001:2:1::b")/Raw(RandString(size=46))
hexdump(pkt)

sendp(pkt, iface='h2-eth0', inter=1./500, count=800)
# sendp(pkt, count=100, inter=1./100, iface='h1-eth0')