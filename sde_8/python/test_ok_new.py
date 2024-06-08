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
        p4_name = "general_nf" 
        BfRuntimeTest.setUp(self, client_id, p4_name)
    
    def set_register_value(self):
        register_idx = 0
        register_value = 0
        while True:
            try:
                register_value=register_value+1
                register_value=register_value%MODVALUE_NUM
                print time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())
                register_table = self.bfrt_info.table_get("Ingress.flow_index_register")
                register_table.entry_mod(
                self.target,
                    [register_table.make_key([gc.KeyTuple('$REGISTER_INDEX', register_idx)])],
                    [register_table.make_data(
                        [gc.DataTuple('Ingress.flow_index_register.f1', register_value)])])
                #register_table.operations_execute(self.target, 'Sync')
  
                general_table = self.bfrt_info.table_get("Ingress.general_table")
                #general_table.entry_del(self.target)
                #meta.key1:exact;
                #meta.key2:exact;
                #meta.key3:exact;
                #meta.key4:exact;
                #meta.key5:exact;
                if register_value == 0:
                    general_table.entry_del(
                            self.target,
                           [general_table.make_key(
                                [gc.KeyTuple('meta.key1', 0x01010101),
                                gc.KeyTuple('meta.key2', 0x02020202),
                                gc.KeyTuple('meta.key3', 0x0),
                                gc.KeyTuple('meta.key4', 0x0),
                                gc.KeyTuple('meta.key5', 0x0)]
                            )]
                        )
                    general_table.entry_add(
                            self.target,
                            [general_table.make_key(
                                [gc.KeyTuple('meta.key1', 0x00000011),
                                gc.KeyTuple('meta.key2', 0x0),
                                gc.KeyTuple('meta.key3', 0x0),
                                gc.KeyTuple('meta.key4', 0x0),
                                gc.KeyTuple('meta.key5', 0x0)]
                            )],
                            [general_table.make_data(
                                [gc.DataTuple('port', 184)],
                                'Ingress.switch_action'
                            )]
                        )
                elif register_value == 1:
                    general_table.entry_del(
                            self.target,
                           [general_table.make_key(
                                [gc.KeyTuple('meta.key1', 0x00000011),
                                gc.KeyTuple('meta.key2', 0x0),
                                gc.KeyTuple('meta.key3', 0x0),
                                gc.KeyTuple('meta.key4', 0x0),
                                gc.KeyTuple('meta.key5', 0x0)]
                            )]
                        )
                    general_table.entry_add(
                            self.target,
                            [general_table.make_key(
                                [gc.KeyTuple('meta.key1', 0x02020202),
                                gc.KeyTuple('meta.key2', 0x0),
                                gc.KeyTuple('meta.key3', 0x0),
                                gc.KeyTuple('meta.key4', 0x0),
                                gc.KeyTuple('meta.key5', 0x0)]
                            )],
                            [general_table.make_data(
                                [gc.DataTuple('srcMacAddr', 0x0b0b0b0b0b0b),
                                gc.DataTuple('dstMacAddr', 0x0c0c0c0c0c),
                                gc.DataTuple('port', 184)],
                                'Ingress.router_action'
                            )]
                        )
                elif register_value == 2:
                    general_table.entry_del(
                            self.target,
                           [general_table.make_key(
                                [gc.KeyTuple('meta.key1', 0x02020202),
                                gc.KeyTuple('meta.key2', 0x0),
                                gc.KeyTuple('meta.key3', 0x0),
                                gc.KeyTuple('meta.key4', 0x0),
                                gc.KeyTuple('meta.key5', 0x0)]
                            )]
                        )
                    general_table.entry_add(
                            self.target,
                            [general_table.make_key(
                                [gc.KeyTuple('meta.key1', 0x01010101),
                                gc.KeyTuple('meta.key2', 0x02020202),
                                gc.KeyTuple('meta.key3', 0x0),
                                gc.KeyTuple('meta.key4', 0x0),
                                gc.KeyTuple('meta.key5', 0x0)]
                            )],
                            [general_table.make_data(
                                [],
                                'Ingress.firewall_action'
                            )]
                        )

                elif register_value == 3:	
                    general_table.entry_add(
                        self.target,
                        [general_table.make_key(
                            [gc.KeyTuple('meta.key1', 0x01010101),
                            gc.KeyTuple('meta.key2', 0x02020202),
                            gc.KeyTuple('meta.key3', 0x0),
                            gc.KeyTuple('meta.key4', 0x0),
                            gc.KeyTuple('meta.key5', 0x0)]
                        )],
                        [general_table.make_data(
                            [gc.DataTuple('mac_addr', 0x0b0b0b0b0b0b)],
                            'Ingress.arp_action'
                        )]
                    )
       #del table 
        #general_table.entry_del(self.target)
        #del entry
        #       general_table.entry_del(
        #               self.target,
        #               [general_table.make_key(
        #                   [gc.KeyTuple('meta.key1', 0x02020202),
        #                   gc.KeyTuple('meta.key2', 0x1),
        #                   gc.KeyTuple('meta.key3', 0x0),
        #                   gc.KeyTuple('meta.key4', 0x0),
        #                   gc.KeyTuple('meta.key5', 0x0)]
        #               )])
                time.sleep(TIMEINTERVAL)
                #register_value=register_value+1
                #register_value=register_value%MODVALUE_NUM
            except Exception as e:
                print(e)

    def runTest(self):
        try:
        # Get bfrt_info and set it as part of the test
            self.bfrt_info = self.interface.bfrt_info_get("general_nf")
            self.target = gc.Target(device_id=0, pipe_id=0xffff)
            set_register_thread = threading.Thread(target=self.set_register_value)
            #set_flow_table_thread = threading.Thread(target=self.set_flow_table)
            set_register_thread.start()
            #set_flow_table_thread.start()
            set_register_thread.join()
            #set_flow_table_thread.join()
        except Exception, exc:
            print(exc)    
