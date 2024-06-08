# import os
from scapy.all import *
import datetime
import sys

path = "../mininet/"
# filename used for 100,500,800,1000 loads
file = open(path + sys.argv[3] + ".txt", "a+")
# filename used for different flow size
# file = open(path + "fct.txt", "a+")
port = sys.argv[1] + '-eth0'
num = int(sys.argv[2])
print ("Starting sniffing on " + port)

pkts = sniff(iface=port, count=num, timeout=30)
current_time = int(time.time() * 1000)
print "Received " + str(len(pkts)) + " packets for " + str(num) + " input"

for p in pkts:
	time = p['Raw'].load
	if "scovery" not in time:
		print("send time: " + time[-13:])
		#time[-13:0]
		print("recv time: " + str(current_time))
		res = current_time - int(time[-13:])
		file.write(str(res) + "\n")
		break;

file.close()
