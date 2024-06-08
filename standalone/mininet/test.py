import time
import subprocess
import os


send = "util/mn-cmd h1 python ../mininet/send.py"
recv = "util/mn-cmd h5 python ../mininet/recv.py"
test = "util/mn-cmd h2 python ../mininet/background.py"
time = 0

# p1 = subprocess.Popen(send,shell=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,encoding="utf-8")
# p2 = subprocess.Popen(recv,shell=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,encoding="utf-8")

for i in range(0, 1):
	p3 = subprocess.Popen(test,shell=True,encoding="utf-8")
	p1 = subprocess.Popen(send,shell=True,encoding="utf-8")
	p2 = subprocess.Popen(recv,shell=True,encoding="utf-8")
	p2.wait(10)
	file = open("mininet/time.txt", "r")
	fct = int(file.readlines()[1])
	# print(fct)
	time += fct
	file.close()

print(time)
print(time/10)



# pkt = Ether(src="00:00:00:00:00:1A", dst="00:00:00:00:00:1E")/IPv6(dst="2001:5:1::b", src="2001:1:1::a")
# hexdump(pkt)

# t = int(time.time() * 1000)
# file = open("../mininet/time.txt", "w+")
# file.write(str(t)+"\n")
# file.close()

# # sendp(pkt, iface='h1-eth0')
# sendp(pkt, count=100, inter=1./100, iface='h1-eth0')