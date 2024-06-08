#!/bin/bash
date
echo comehere888888888888888
pid_num=`ps -ef |grep bf_switchd|grep -v grep|awk -F " " '{print $2}'`
oldifs="$IFS"
IFS=$'\n'
for pid in $pid_num;do 
    echo pid=$pid
	kill -9 $pid
done
IFS="$oldifs"
date
echo comehere888888888888888
/mnt/onl/data/bf-sde-9.1.0/run_switchd.sh -p route & > /dev/null
while true
do
        #echo comehere111111111111111111111
	server_state=`netstat -apn |grep 0.0.0.0:9999|awk -F " " '{print $6}'`
	server_state2=`netstat -apn |grep 0.0.0.0:9999|awk -F " " '{print $7}'|awk -F "/" '{print $2}'`
	#server_state2=`netstat -apn |grep 0.0.0.0:9999`
	echo server_state2=$server_state2
	if [ $server_state = "LISTEN" ] && [ $server_state2 = "bf_switchd" ];then
		echo comehre_server_state_is_equel_listen
		break
	fi
done
bfshell -f command.txt
./run_p4_tests.sh -t /root/test &
#bfshell -f command.txt 

