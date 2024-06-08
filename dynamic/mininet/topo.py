#!/usr/bin/python

#  Copyright 2019-present Open Networking Foundation
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import argparse

from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.net import Mininet
from mininet.node import Host
from mininet.topo import Topo
from stratum import StratumBmv2Switch
from scapy.all import *
import time
import random
import datetime
import threading
import subprocess

CPU_PORT = 255
# load_set = [100, 500, 800 ,1000]
load_set = [1000]
# whether to send differetiated flows
DIFF = 1


class IPv6Host(Host):

    def config(self, ipv6, ipv6_gw=None, **params):
        super(IPv6Host, self).config(**params)
        self.cmd('ip -4 addr flush dev %s' % self.defaultIntf())
        self.cmd('ip -6 addr flush dev %s' % self.defaultIntf())
        self.cmd('ip -6 addr add %s dev %s' % (ipv6, self.defaultIntf()))
        if ipv6_gw:
            self.cmd('ip -6 route add default via %s' % ipv6_gw)
        # Disable offload
        for attr in ["rx", "tx", "sg"]:
            cmd = "/sbin/ethtool --offload %s %s off" % (self.defaultIntf(), attr)
            self.cmd(cmd)

        def updateIP():
            return ipv6.split('/')[0]

        self.defaultIntf().updateIP = updateIP

    def terminate(self):
        super(IPv6Host, self).terminate()


class TutorialTopo(Topo):
    """cumstom fabric topology"""

    def __init__(self, *args, **kwargs):
        Topo.__init__(self, *args, **kwargs)

        # Leaves
        # gRPC port 50001
        s1 = self.addSwitch('s1', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        # gRPC port 50002
        s2 = self.addSwitch('s2', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        s3 = self.addSwitch('s3', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        s4 = self.addSwitch('s4', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        s5 = self.addSwitch('s5', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        s6 = self.addSwitch('s6', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        s7 = self.addSwitch('s7', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        s8 = self.addSwitch('s8', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        s9 = self.addSwitch('s9', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        s10 = self.addSwitch('s10', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        s11 = self.addSwitch('s11', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        s12 = self.addSwitch('s12', cls=StratumBmv2Switch, cpuport=CPU_PORT)


        # IPv6 hosts attached to leaf 1
        h1 = self.addHost('h1', cls=IPv6Host, mac="00:00:00:00:00:01",
                           ipv6='2001:1:1::b/64', ipv6_gw='2001:1:1::ff')
        h2 = self.addHost('h2', cls=IPv6Host, mac="00:00:00:00:00:02",
                           ipv6='2001:2:1::b/64', ipv6_gw='2001:2:1::ff')
        h3 = self.addHost('h3', cls=IPv6Host, mac="00:00:00:00:00:03",
                           ipv6='2001:3:1::b/64', ipv6_gw='2001:3:1::ff')
        h4 = self.addHost('h4', cls=IPv6Host, mac="00:00:00:00:00:04",
                           ipv6='2001:4:1::b/64', ipv6_gw='2001:4:1::ff')
        h5 = self.addHost('h5', cls=IPv6Host, mac="00:00:00:00:00:05",
                           ipv6='2001:5:1::b/64', ipv6_gw='2001:5:1::ff')
        h6 = self.addHost('h6', cls=IPv6Host, mac="00:00:00:00:00:06",
                           ipv6='2001:6:1::b/64', ipv6_gw='2001:6:1::ff')
        h7 = self.addHost('h7', cls=IPv6Host, mac="00:00:00:00:00:07",
                           ipv6='2001:7:1::b/64', ipv6_gw='2001:7:1::ff')
        h8 = self.addHost('h8', cls=IPv6Host, mac="00:00:00:00:00:08",
                           ipv6='2001:8:1::b/64', ipv6_gw='2001:8:1::ff')
        h9 = self.addHost('h9', cls=IPv6Host, mac="00:00:00:00:00:09",
                           ipv6='2001:9:1::b/64', ipv6_gw='2001:9:1::ff')
        h10 = self.addHost('h10', cls=IPv6Host, mac="00:00:00:00:00:0A",
                           ipv6='2001:0a:1::b/64', ipv6_gw='2001:a:1::ff')
        h11 = self.addHost('h11', cls=IPv6Host, mac="00:00:00:00:00:0B",
                           ipv6='2001:0b:1::b/64', ipv6_gw='2001:b:1::ff')
        h12 = self.addHost('h12', cls=IPv6Host, mac="00:00:00:00:00:0C",
                           ipv6='2001:0c:1::b/64', ipv6_gw='2001:c:1::ff')

        self.addLink(h1, s1, bw=1000)
        self.addLink(h2, s2, bw=1000)
        self.addLink(h3, s3, bw=1000)
        self.addLink(h4, s4, bw=1000)
        self.addLink(h5, s5, bw=1000)
        self.addLink(h6, s6, bw=1000)
        self.addLink(h7, s7, bw=1000)
        self.addLink(h8, s8, bw=1000)
        self.addLink(h9, s9, bw=1000)
        self.addLink(h10, s10, bw=1000)
        self.addLink(h11, s11, bw=1000)
        self.addLink(h12, s12, bw=1000)
        # Switch Links
        self.addLink(s1, s2, bw=1000)
        self.addLink(s1, s3, bw=1000)
        self.addLink(s2, s3, bw=1000)
        self.addLink(s2, s4, bw=1000)
        self.addLink(s3, s5, bw=1000)
        self.addLink(s4, s6, bw=1000)
        self.addLink(s5, s6, bw=1000)
        self.addLink(s5, s7, bw=1000)
        self.addLink(s6, s8, bw=1000)
        self.addLink(s7, s8, bw=1000)
        self.addLink(s7, s9, bw=1000)
        self.addLink(s9, s10, bw=1000)
        self.addLink(s10, s11, bw=1000)
        self.addLink(s11, s8, bw=1000)
        self.addLink(s12, s8, bw=1000)

def ABCToNum(char):
    if char in "0123456789":
        return char
    if char in "Aa":
        return '10'
    if char in "Bb":
        return '11'
    if char in "Cc":
        return '12'


def test(self, arg):
    "testing for 5 times each load with randomly sending packets"
    # Test Times
    SUM = 3000002

    for times in range(0, 1):
        n = 132
        num = random.sample(range(0, n), n)

        file = open("../mininet/requirements.txt", "r");
        lines = file.readlines()  # requests info 
        f = open("../mininet/flowsize.txt", "r");
        size_values = f.readlines() # size info
        
        for factor in load_set:
            print("Testing for load " + str(factor))
            count = 0
            # randomly send one line of request file
            for i in num:
                line = lines[i*2]
                # src and dst host
                s = line.split(' ')[0][-6]
                d = line.split(' ')[1][-7]
                if DIFF != 0 :
                    real_load = int(float(size_values[i]) * factor * 132 / SUM)
                if s not in "bBcC" and d not in "bBcC":
                    # command for dst host to receive packets
                    command = '../mininet/host-cmd h' + ABCToNum(d) + ' python ../mininet/recv.py h' + ABCToNum(d) + " " + str(real_load) + " " + str(factor)
                    # open new process
                    subp = subprocess.Popen(command, shell=True)
                    time.sleep(2)
                    # send packets
                    pkt = Ether(src="00:00:00:00:00:0" + s, dst="00:00:00:00:00:0" + d) / IPv6(
                        src="2001:" + s + ":1::b", dst="2001:" + d + ":1::b") / Raw(int(time.time()*1000))
                    hexdump(pkt)
                    sendp(pkt, count=real_load, inter=1./1000000, iface="s" + ABCToNum(s) + "-eth1")
                    print('Send packets from h' + s + ' to h' + d)
                    # subp.wait()
                count+=1
                print(count)
            time.sleep(40)


def once(self, load):
    "test for one time with certain load"
    n = 132
    num = random.sample(range(0, n), n)
    file = open("../mininet/requirements.txt", "r");

    l = file.readlines()
    count = 0
    # randomly send one line of request file
    for i in num:
        line = l[i*2]
        # src and dst host
        s = line.split(' ')[0][-6]
        d = line.split(' ')[1][-7]
        if s not in "bBcC" and d not in "bBcC":
            # command for dst host to receive packets
            command = '../mininet/host-cmd h' + ABCToNum(d) + ' python ../mininet/recv.py h' + ABCToNum(d) + " " + load
            # open new process
            subp = subprocess.Popen(command, shell=True)
            time.sleep(2)
            # send packets
            pkt = Ether(src="00:00:00:00:00:0" + s, dst="00:00:00:00:00:0" + d) / IPv6(
                src="2001:" + s + ":1::b", dst="2001:" + d + ":1::b") / Raw(int(time.time()*1000))
            hexdump(pkt)
            sendp(pkt, count=int(load), inter=1./100000, iface="s" + ABCToNum(s) + "-eth1")
            print('Send packets from h' + s + ' to h' + d)
            # waiting for subprocess to terminate
            
        count+=1
        print(count)


def recv(host, net):
    h = net.get(host)
    h.cmd('python ../mininet/recv.py', host)
    # port = host + '-eth0'
    # pkt = sniff(iface=host, count=10)
    # file = open("../mininet/time.txt", "a+")
    # t = int(time.time() * 1000)
    # file.write(str(t) + "\n")
    # file.close()
    # print("received 100 packets")

def send():
    pkt = Ether(src="00:00:00:00:00:09", dst="00:00:00:00:00:03")/IPv6(
        src="2001:9:1::b", dst="2001:3:1::b")/Raw(int(time.time()*1000))
    hexdump(pkt)
    sendp(pkt, count=10, inter=1./10000, iface='s9-eth1')
    print('done')
    

def main():
    file_100 = open("../mininet/100.txt", "w+")
    file_800 = open("../mininet/500.txt", "w+")
    file_500 = open("../mininet/800.txt", "w+")
    file_1000 = open("../mininet/1000.txt", "w+")
    file_fct = open("../mininet/fct.txt", "w+")
    file_100.close()
    file_500.close()
    file_800.close()
    file_1000.close()
    file_fct.close()

    net = Mininet(topo=TutorialTopo(), controller=None, autoStaticArp=True)
    net.start()
    CLI.do_test = test
    CLI.do_once = once
    CLI(net)
    net.stop()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description='Mininet topology script for single switch fabric with stratum_bmv2 and IPv6 hosts')
    args = parser.parse_args()
    setLogLevel('info')


    main()
