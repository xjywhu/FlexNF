# Copyright 2019-present Open Networking Foundation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# ------------------------------------------------------------------------------
# NDP GENERATION TESTS
#
# To run all tests:
#     make ndp
#
# To run a specific test case:
#     make ndp.<TEST CLASS NAME>
#
# For example:
#     make ndp.NdpReplyGenTest
# ------------------------------------------------------------------------------

from ptf.testutils import group
from base_test import *


@group("forward")
class SimpleForwardTest(P4RuntimeTest):
    """Tests automatic generation of Simple Forward cases with label 2 
    """
    @autocleanup
    def runTest(self):
        # Insert add_label_table_entry
        self.insert(self.helper.build_table_entry(
            table_name="IngressPipeImpl.add_label_table",
            match_fields={
                # Exact match.
                "hdr.ipv6.src_addr": '2001:1:1::a'
            },
            action_name="IngressPipeImpl.get_label",
            action_params={
                "label": 2
            }
        ))


        pkt = Ether(src="00:00:00:00:00:1A", dst="00:00:00:00:00:1E")/IPv6(dst="2001:5:1::b",src="2001:1:1::a")

        # Expected packet
        exp_pkt = Ether(src="00:00:00:00:00:1A", dst="00:00:00:00:00:1E")/IPv6(dst="2001:5:1::b",src="2001:1:1::a")

        # Start test
        testutils.send_packet(self, self.port1, str(pkt))
        testutils.verify_packet(self, exp_pkt, self.port1)
