#!/bin/bash
# Start running PTF tests associated with a P4 program

function print_help() {
  echo "USAGE: $(basename ""$0"") -p <...> -t <...> [OPTIONS -- PTF_OPTIONS]"
  echo "Options for running PTF tests:"
  echo "  -p <p4_program_name>"
  echo "    Run PTF tests associated with P4 program"
  echo "  -t TEST_DIR"
  echo "    TEST_DIR contains test cases executed by PTF."
  echo "  -f PORTINFO_FILE"
  echo "    Read port to veth mapping information from PORTINFO_FILE"
  echo "  -s TEST_SUITE"
  echo "    Name of the test suite to execute passed to PTF"
  echo "  --arch <ARCHITECTURE>"
  echo "    Architecture (tofino, tofino2, or SimpleSwitch)"
  echo "  --target <TARGET>"
  echo "    Target (asic-model, hw or bmv2)"
  echo "  --thrift-server THRIFT_SERVER"
  echo "    Thrift server IP address, localhost by default"
  echo "  --num-pipes NUM_PIPES"
  echo "    Set num of pipes to use for test"
  echo "  --no-veth"
  echo "    Skip veth setup and special CPU port setup"
  echo "  --setup"
  echo "    Run test setup only"
  echo "  --cleanup"
  echo "    Run test cleanup only"
  echo "  --socket-recv-size <socket bytes size>"
  echo "    socket buffer size for ptf scapy verification "
  echo "  --no-status-srv"
  echo "    Do not query bf_switchd's status server"
  echo "  --status-port <port number>"
  echo "    Specify bf_switchd's status server port number; the default is 7777"
  echo "  --status-host <host name>"
  echo "    Specify bf_switchd's status server address; the default is localhost"
  echo "  --test-params <ptf_test_params>"
  echo "    PTF test params as a string, e.g. arch='Tofino';target='hw';"
  echo "  --drv-test-info <file>"
  echo "    Specify the driver combination test config file"
  echo "  --seed <number>"
  echo "    Specify the driver combination test random seed"
  echo "  -h"
  echo "    Print this message"
  exit 0
}

trap 'exit' ERR

[ -z ${SDE} ] && echo "Environment variable SDE not set" && exit 1
[ -z ${SDE_INSTALL} ] && echo "Environment variable SDE_INSTALL not set" && exit 1

echo "Using SDE ${SDE}"
echo "Using SDE_INSTALL ${SDE_INSTALL}"

opts=`getopt -o p:t:f:s:d:b:h --long thrift-server: --long arch: --long target: --long setup --long cleanup --long no-veth --long num-pipes: --long socket-recv-size: --long no-status-srv --long status-port: --long drv-test-info: --long seed: --long status-host: --long test-params: -- "$@"`
if [ $? != 0 ]; then
  exit 1
fi
eval set -- "$opts"

# P4 program name
P4_NAME=""
# json file specifying model port to veth mapping info
PORTINFO=None
THRIFT_SERVER='localhost'
HELP=false
SETUP=""
CLEANUP=""
NO_VETH=false
CPUPORT=64
CPUVETH=251
ARCH="Tofino"
TARGET="asic-model"
SOCKET_RECV_SIZE="10240"
SKIP_STATUS_SRV=false
NUM_PIPES=4
TEST_PARAMS=""
DRV_TEST_INFO=""
DRV_TEST_SEED=""
while true; do
    case "$1" in
      -p) P4_NAME=$2; shift 2;;
      -t) TEST_DIR="$2"; shift 2;;
      -s) TEST_SUITE="$2"; shift 2;;
      -f) PORTINFO=$2; shift 2;;
      --no-veth) NO_VETH=true; shift 1;;
      --arch) ARCH=$2; shift 2;;
      --target) TARGET=$2; shift 2;;
      --thrift-server) THRIFT_SERVER=$2; shift 2;;
      --socket-recv-size) SOCKET_RECV_SIZE=$2; shift 2;;
      --setup) SETUP="--setup"; shift 1;;
      --cleanup) CLEANUP="--cleanup"; shift 1;;
      --status-port) STS_PORT=$2; shift 2;;
      --status-host) STS_HOST=$2; shift 2;;
      --no-status-srv) SKIP_STATUS_SRV=true; shift 1;;
      --num-pipes) NUM_PIPES=$2; shift 2;;
      --test-params) TEST_PARAMS=$2; shift 2;;
      --drv-test-info) DRV_TEST_INFO="--drivers-test-info $2"; shift 2;;
      --seed) DRV_TEST_SEED="--seed $2"; shift 2;;
      -h) HELP=true; shift 1;;
      --) shift; break;;
    esac
done

if [ $HELP = true ] || ( [ -z $P4_NAME ] && [ -z $TEST_DIR ] ); then
  print_help
fi

if [ -z ${TEST_DIR} ]; then
  TEST_DIR=$SDE/`find -type d -path "*/p4-examples*/ptf-tests/$P4_NAME"`
fi

[ -d "$TEST_DIR" ] || exit "Test directory $TEST_DIR directory does not exist"

ARCH=`echo $ARCH | tr '[:upper:]' '[:lower:]'`

echo "Using TEST_DIR ${TEST_DIR}"

export PATH=$SDE_INSTALL/bin:$PATH
echo "Using PATH ${PATH}"
echo "Arch is $ARCH"
echo "Target is $TARGET"
if [[ "$TARGET" == "bmv2" && "$ARCH" == "tofino" ]]; then
    echo "Running tofinobm tests"
    export PYTHONPATH=$SDE_INSTALL/lib/python2.7/site-packages/p4testutils:$SDE_INSTALL/lib/python2.7/site-packages/tofinobmpd/:$SDE_INSTALL/lib/python2.7/site-packages/tofinobm/pdfixed:$SDE_INSTALL/lib/python2.7/site-packages/:$PYTHONPATH
else
    export PYTHONPATH=$SDE_INSTALL/lib/python2.7/site-packages/p4testutils:$SDE_INSTALL/lib/python2.7/site-packages/${ARCH}pd/:$SDE_INSTALL/lib/python2.7/site-packages/tofino:$SDE_INSTALL/lib/python2.7/site-packages/:$PYTHONPATH
fi


if [ $NO_VETH = true ]; then
  CPUPORT=None
  CPUVETH=None
else
  # Setup veth interfaces
  echo "Setting up veth interfaces"
  sudo env "PATH=$PATH" $SDE_INSTALL/bin/veth_setup.sh
fi

# Check in with bf_switchd's status server to make sure it is ready
STS_PORT_STR="--port 7777"
if [ "$STS_PORT" != "" ]; then
  STS_PORT_STR="--port $STS_PORT"
fi
STS_HOST_STR="--host localhost"
if [ "$STS_HOST" != "" ]; then
  STS_HOST_STR="--host $STS_HOST"
fi
if [ "$TARGET" != "bmv2" ]; then
  if [ $SKIP_STATUS_SRV = false ]; then
    python $SDE_INSTALL/lib/python2.7/site-packages/p4testutils/bf_switchd_dev_status.py \
      $STS_HOST_STR $STS_PORT_STR 
  fi
fi

TEST_PARAMS_STR=""
if [ "$TEST_PARAMS" != "" ]; then
    TEST_PARAMS_STR="--test-params $TEST_PARAMS"
fi

#Run PTF tests
sudo env "PATH=$PATH" "PYTHONPATH=$PYTHONPATH" python \
    $SDE_INSTALL/lib/python2.7/site-packages/p4testutils/run_ptf_tests.py \
    --arch $ARCH \
    --target $TARGET \
    --test-dir $TEST_DIR \
    --port-info $PORTINFO \
    --thrift-server $THRIFT_SERVER \
    --cpu-port $CPUPORT \
    --cpu-veth $CPUVETH \
    --num-pipes $NUM_PIPES \
    --socket-recv-size $SOCKET_RECV_SIZE \
    $DRV_TEST_INFO $DRV_TEST_SEED \
    $TEST_SUITE $SETUP $CLEANUP $TEST_PARAMS_STR
res=$?
if [[ $res == 0 ]]; then
    exit $res
else
    exit 1
fi
