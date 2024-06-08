################################################################################
# BAREFOOT NETWORKS CONFIDENTIAL & PROPRIETARY
#
# Copyright (c) 2019-present Barefoot Networks, Inc.
#
# All Rights Reserved.
#
# NOTICE: All information contained herein is, and remains the property of
# Barefoot Networks, Inc. and its suppliers, if any. The intellectual and
# technical concepts contained herein are proprietary to Barefoot Networks, Inc.
# and its suppliers and may be covered by U.S. and Foreign Patents, patents in
# process, and are protected by trade secret or copyright law.  Dissemination of
# this information or reproduction of this material is strictly forbidden unless
# prior written permission is obtained from Barefoot Networks, Inc.
#
# No warranty, explicit or implicit is provided, unless granted under a written
# agreement with Barefoot Networks, Inc.
#
################################################################################

import logging

from ptf import config
import ptf.testutils as testutils
from bfruntime_client_base_tests import BfRuntimeTest
import bfrt_grpc.bfruntime_pb2 as bfruntime_pb2
import bfrt_grpc.client as gc
import random
import threading
import time

TIMEINTERVAL=60
MODVALUE_NUM=3
class Genrnal_nfTest(BfRuntimeTest):
    """@brief This test adds a value in an indirect register table and then
        reads the value back using the single-entry sync-from-hw functionality.
    """

    def setUp(self):
        client_id = 0
        p4_name = "switch" 
        BfRuntimeTest.setUp(self, client_id, p4_name)
    
    def set_register_value(self):
        try:
            print time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())
  
            general_table = self.bfrt_info.table_get("Ingress.switch_table")
            general_table.entry_del(self.target)
 
            general_table.entry_add(
                    self.target,
                    [general_table.make_key(
                        [gc.KeyTuple('hdr.ethernet.dstAddr', 0x000000000011)]
                    )],
                    [general_table.make_data(
                        [gc.DataTuple('port', 0)],
                        'Ingress.switch_action'
                    )]
                )
        except Exception as e:
            print(e)

    def runTest(self):
        try:
        # Get bfrt_info and set it as part of the test
            self.bfrt_info = self.interface.bfrt_info_get("switch")
            self.target = gc.Target(device_id=0, pipe_id=0xffff)
            set_register_thread = threading.Thread(target=self.set_register_value)
            #set_flow_table_thread = threading.Thread(target=self.set_flow_table)
            set_register_thread.start()
            #set_flow_table_thread.start()
            set_register_thread.join()
            #set_flow_table_thread.join()
        except Exception, exc:
            print(exc)    
