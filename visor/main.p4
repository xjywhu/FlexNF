/*
 * Copyright 2019-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <core.p4>
#include <v1model.p4>

// CPU_PORT specifies the P4 port number associated to packet-in and packet-out.
// All packets forwarded via this port will be delivered to the controller as
// PacketIn messages. Similarly, PacketOut messages from the controller will be
// seen by the P4 pipeline as coming from the CPU_PORT.
#define CPU_PORT 255

// CPU_CLONE_SESSION_ID specifies the mirroring session for packets to be cloned
// to the CPU port. Packets associated with this session ID will be cloned to
// the CPU_PORT as well as being transmitted via their egress port as set by the
// bridging/routing/acl table. For cloning to work, the P4Runtime controller
// needs first to insert a CloneSessionEntry that maps this session ID to the
// CPU_PORT.
#define CPU_CLONE_SESSION_ID 99


//------------------------------------------------------------------------------
// TYPEDEF DECLARATIONS
// To favor readability.
//------------------------------------------------------------------------------
typedef bit<9>   port_num_t;
typedef bit<48>  mac_addr_t;
typedef bit<16>  mcast_group_id_t;
typedef bit<32>  ipv4_addr_t;
typedef bit<128> ipv6_addr_t;
typedef bit<16>  l4_port_t;


//------------------------------------------------------------------------------
// CONSTANT VALUES
//------------------------------------------------------------------------------
const bit<16> ETHERTYPE_IPV6 = 0x86dd;
const bit<16> ETHERTYPE_TUNNEL = 0x1212;
const bit<6> TUNNEL_IPV6 = 0x12;


const bit<8> IP_PROTO_TCP = 6;
const bit<8> IP_PROTO_UDP = 17;
const bit<8> IP_PROTO_ICMPV6 = 58;

const mac_addr_t IPV6_MCAST_01 = 0x33_33_00_00_00_01;

const bit<8> ICMP6_TYPE_NS = 135;
const bit<8> ICMP6_TYPE_NA = 136;
const bit<8> NDP_OPT_TARGET_LL_ADDR = 2;
const bit<32> NDP_FLAG_ROUTER = 0x80000000;
const bit<32> NDP_FLAG_SOLICITED = 0x40000000;
const bit<32> NDP_FLAG_OVERRIDE = 0x20000000;
const bit<32> PKT_INSTANCE_TYPE_RESUBMIT = 6;


//------------------------------------------------------------------------------
// HEADER DEFINITIONS
//------------------------------------------------------------------------------
header ethernet_t {
    mac_addr_t  dst_addr;
    mac_addr_t  src_addr;
    bit<16>     ether_type;
}

header ipv6_t {
    bit<4>   version;
    bit<8>   traffic_class;
    bit<20>  flow_label;
    bit<16>  payload_len;
    bit<8>   next_hdr;
    bit<8>   hop_limit;
    bit<128> src_addr;
    bit<128> dst_addr;
}

header tcp_t {
    bit<16>  src_port;
    bit<16>  dst_port;
    bit<32>  seq_no;
    bit<32>  ack_no;
    bit<4>   data_offset;
    bit<3>   res;
    bit<3>   ecn;
    bit<6>   ctrl;
    bit<16>  window;
    bit<16>  checksum;
    bit<16>  urgent_ptr;
}

header udp_t {
    bit<16> src_port;
    bit<16> dst_port;
    bit<16> len;
    bit<16> checksum;
}

header icmp_t {
    bit<8>   type;
    bit<8>   icmp_code;
    bit<16>  checksum;
    bit<16>  identifier;
    bit<16>  sequence_number;
    bit<64>  timestamp;
}

header icmpv6_t {
    bit<8>   type;
    bit<8>   code;
    bit<16>  checksum;
}

header ndp_t {
    bit<32>      flags;
    ipv6_addr_t  target_ipv6_addr;
    // NDP option.
    bit<8>       type;
    bit<8>       length;
    bit<48>      target_mac_addr;
}

// Packet-in header. Prepended to packets sent to the CPU_PORT and used by the
// P4Runtime server (Stratum) to populate the PacketIn message metadata fields.
// Here we use it to carry the original ingress port where the packet was
// received.
@controller_header("packet_in")
header packet_in_t {
    port_num_t  ingress_port;
    bit<7>      _pad;
}

// Packet-out header. Prepended to packets received from the CPU_PORT. Fields of
// this header are populated by the P4Runtime server based on the P4Runtime
// PacketOut metadata fields. Here we use it to inform the P4 pipeline on which
// port this packet-out should be transmitted.
@controller_header("packet_out")
header packet_out_t {
    port_num_t  egress_port;
    bit<7>      _pad;
}

header tunnel_t {
    bit<10>      id;
    bit<6>       proto_id;
}

struct parsed_headers_t {
    packet_out_t  packet_out;
    packet_in_t   packet_in;
    tunnel_t      tunnel;
    ethernet_t    ethernet;
    ipv6_t        ipv6;
    tcp_t         tcp;
    udp_t         udp;
    icmpv6_t      icmpv6;
    ndp_t         ndp;
}



//------------------------------------------------------------------------------
// USER-DEFINED METADATA
// User-defined data structures associated with each packet.
//------------------------------------------------------------------------------
struct local_metadata_t {
    l4_port_t  l4_src_port;
    l4_port_t  l4_dst_port;
    bool       is_multicast;
    bit<1>     state;
    bit<1>     condition;
    bit<8>     count;
    bit<32>    idx;
    bit<1>     ecmp_select;
    bit<1>     ecmp_pool;
    // bit<3>     fw_state;
    // bit<2>     fw_condition;
    // bit<32>    fw_hash_val;
}

// *** INTRINSIC METADATA
/*
struct standard_metadata_t {
    bit<9>  ingress_port;
    bit<9>  egress_spec; // Set by the ingress pipeline
    bit<9>  egress_port; // Read-only, available in the egress pipeline
    bit<32> instance_type;
    bit<32> packet_length;
    bit<48> ingress_global_timestamp;
    bit<48> egress_global_timestamp;
    bit<16> mcast_grp; // ID for the mcast replication table
    bit<1>  checksum_error; // 1 indicates that verify_checksum() method failed
}
*/


parser ParserImpl (packet_in packet,
                   out parsed_headers_t hdr,
                   inout local_metadata_t local_metadata,
                   inout standard_metadata_t standard_metadata)
{
    // We assume the first header will always be the Ethernet one, unless the
    // the packet is a packet-out coming from the CPU_PORT.
    state start {
        transition select(standard_metadata.ingress_port) {
            CPU_PORT: parse_packet_out;
            default: parse_ethernet;
        }
    }

    state parse_packet_out {
        packet.extract(hdr.packet_out);
        transition parse_ethernet;
    }

    state parse_ethernet {
        packet.extract(hdr.ethernet);
        transition select(hdr.ethernet.ether_type){
            ETHERTYPE_IPV6:   parse_ipv6;
            ETHERTYPE_TUNNEL: parse_tunnel;
            default: accept;
        }
    }

    state parse_tunnel {
        packet.extract(hdr.tunnel);
        transition parse_ipv6;
    }

    state parse_ipv6 {
        packet.extract(hdr.ipv6);
        transition select(hdr.ipv6.next_hdr) {
            IP_PROTO_TCP:    parse_tcp;
            IP_PROTO_UDP:    parse_udp;
            IP_PROTO_ICMPV6: parse_icmpv6;
            default: accept;
        }
    }

    state parse_tcp {
        packet.extract(hdr.tcp);
        local_metadata.l4_src_port = hdr.tcp.src_port;
        local_metadata.l4_dst_port = hdr.tcp.dst_port;
        transition accept;
    }

    state parse_udp {
        packet.extract(hdr.udp);
        local_metadata.l4_src_port = hdr.udp.src_port;
        local_metadata.l4_dst_port = hdr.udp.dst_port;
        transition accept;
    }

    state parse_icmpv6 {
        packet.extract(hdr.icmpv6);
        transition select(hdr.icmpv6.type) {
            ICMP6_TYPE_NS: parse_ndp;
            ICMP6_TYPE_NA: parse_ndp;
            default: accept;
        }
    }

    state parse_ndp {
        packet.extract(hdr.ndp);
        transition accept;
    }
}



control VerifyChecksumImpl(inout parsed_headers_t hdr,
                           inout local_metadata_t meta)
{
    // Not used here. We assume all packets have valid checksum, if not, we let
    // the end hosts detect errors.
    apply { /* EMPTY */ }
}

control big_flow_detector(inout parsed_headers_t hdr, inout local_metadata_t local_metadata, inout standard_metadata_t standard_metadata){
    register<bit<1>>(16) state;
    register<bit<8>>(16) count;
    action read_state(){
        hash(local_metadata.idx,
        HashAlgorithm.crc16,
        32w0,
        { hdr.ipv6.src_addr,
          hdr.ipv6.dst_addr},
        32w16);
        state.read(local_metadata.state, local_metadata.idx);
        count.read(local_metadata.count, local_metadata.idx);
    }

    table state_table{
        actions = {
            read_state;
        }
        size = 1;
        default_action = read_state;
    }

    action compare(bit<8> thresh){
        local_metadata.condition = (local_metadata.count<thresh) ? 1w0 : 1w1;
    }

    action goto_small(){
        //update count number
        count.write(local_metadata.idx, local_metadata.count+8w1);
    }

    action goto_big(){
        //update the state to big
        state.write(local_metadata.idx, 1w1);
    }

    table condition_table{
        actions = {
            compare;
            NoAction;
        }
        key = {
            local_metadata.state: exact;
        }
        size = 4;
        default_action = NoAction;
    }

    action drop() {
            mark_to_drop(standard_metadata);
        }

    table action_table{
        actions = {
            goto_small;
            goto_big;
            drop;
        }
        key = {
            local_metadata.state: exact;
            local_metadata.condition: exact;
        }
        size = 4;
        default_action = drop;
    }
    apply{
        state_table.apply();
        condition_table.apply();
        action_table.apply();

    }
}


control load_balance(inout parsed_headers_t hdr, inout local_metadata_t local_metadata, inout standard_metadata_t standard_metadata) {
    action drop() {
        mark_to_drop(standard_metadata);
    }
    action set_ecmp_pool(bit<1> pool) {
        local_metadata.ecmp_pool = pool;
        hash(local_metadata.ecmp_select,
        HashAlgorithm.crc16,
        1w0,
        { hdr.ipv6.src_addr,
          hdr.ipv6.dst_addr},
        1w1);
    }
    action set_dip(bit<48> nhop_dmac, bit<128> nhop_ipv6, bit<9> port) {
        hdr.ethernet.dst_addr = nhop_dmac;
        hdr.ipv6.dst_addr = nhop_ipv6;
        standard_metadata.egress_spec = port;
    }
    table ecmp_pool {
        key = {
            hdr.ipv6.dst_addr: exact;
        }
        actions = {
            set_ecmp_pool;
        }
        size = 1024;
    }
    table ecmp_nhop {
        key = {
            local_metadata.ecmp_pool: exact;
            local_metadata.ecmp_select: exact;
        }
        actions = {
            drop;
            set_dip;
        }
        size = 10;
    }
    apply {
        if (hdr.ipv6.isValid()) {
            ecmp_pool.apply();
            ecmp_nhop.apply();
        }
    }
}


control nat(inout parsed_headers_t hdr, inout local_metadata_t local_metadata, inout standard_metadata_t standard_metadata) {
    
    action map(bit<48> nhop_dmac, bit<128> nhop_ipv6, bit<9> port) {
        hdr.ethernet.dst_addr = nhop_dmac;
        hdr.ipv6.dst_addr = nhop_ipv6;
        standard_metadata.egress_spec = port;
    }
    table nat_table {
        key = {
            hdr.ipv6.src_addr: exact;
        }
        actions = {
            map;
        }
        size = 1024;
    }
    apply {
        nat_table.apply();
    }
}


control firewall(inout parsed_headers_t hdr, inout local_metadata_t local_metadata, inout standard_metadata_t standard_metadata) {
    
    action drop() {
        mark_to_drop(standard_metadata);
    }
    table filter_table {
        key = {
            hdr.ipv6.src_addr: exact;
            hdr.ipv6.dst_addr: exact;
        }
        actions = {
            drop;
        }
        size = 1024;
    }
    apply {
        filter_table.apply();
    }
}


control IngressPipeImpl (inout parsed_headers_t    hdr,
                         inout local_metadata_t    local_metadata,
                         inout standard_metadata_t standard_metadata) {
    action drop() {
        mark_to_drop(standard_metadata);
    }

    // --- l2_exact_table (for unicast entries) --------------------------------
    action set_egress_port(port_num_t port_num) {
        standard_metadata.egress_spec = port_num;
    }

    table l2_exact_table {
        key = {
            hdr.ethernet.dst_addr: exact;
        }
        actions = {
            set_egress_port;
            @defaultonly drop;
        }
        const default_action = drop;
        @name("l2_exact_table_counter")
        counters = direct_counter(CounterType.packets_and_bytes);
    }

    // --- l2_ternary_table (for broadcast/multicast entries) ------------------
    action set_multicast_group(mcast_group_id_t gid) {
        standard_metadata.mcast_grp = gid;
        local_metadata.is_multicast = true;
    }

    table l2_ternary_table {
        key = {
            hdr.ethernet.dst_addr: ternary;
        }
        actions = {
            set_multicast_group;
            @defaultonly drop;
        }
        const default_action = drop;
        @name("l2_ternary_table_counter")
        counters = direct_counter(CounterType.packets_and_bytes);
    }

    // --- my_station_table ----------------------------------------------------
    table my_station_table {
        key = {
            hdr.ethernet.dst_addr: exact;
        }
        actions = { NoAction; }
        @name("my_station_table_counter")
        counters = direct_counter(CounterType.packets_and_bytes);
    }

    // --- routing_v6_table ----------------------------------------------------
    action set_next_hop(mac_addr_t dmac) {
        hdr.ethernet.src_addr = hdr.ethernet.dst_addr;
        hdr.ethernet.dst_addr = dmac;
        // Decrement TTL
        hdr.ipv6.hop_limit = hdr.ipv6.hop_limit - 1;
    }

    table routing_v6_table {
      key = {
          hdr.ipv6.dst_addr: lpm;
      }
      actions = {
          set_next_hop;
      }
      @name("routing_v6_table_counter")
      counters = direct_counter(CounterType.packets_and_bytes);
    }

    // --- acl_table -----------------------------------------------------------

    action send_to_cpu() {
        standard_metadata.egress_spec = CPU_PORT;
    }

    action clone_to_cpu() {
        clone3(CloneType.I2E, CPU_CLONE_SESSION_ID, { standard_metadata.ingress_port });
    }

    table acl_table {
        key = {
            standard_metadata.ingress_port: ternary;
            hdr.ethernet.dst_addr:          ternary;
            hdr.ethernet.src_addr:          ternary;
            hdr.ethernet.ether_type:        ternary;
            hdr.ipv6.next_hdr:              ternary;
            hdr.icmpv6.type:                ternary;
            local_metadata.l4_src_port:     ternary;
            local_metadata.l4_dst_port:     ternary;
        }
        actions = {
            send_to_cpu;
            clone_to_cpu;
            drop;
        }
        @name("acl_table_counter")
        counters = direct_counter(CounterType.packets_and_bytes);
    }

    // --- tunnel_forward table ----------------------------------------------------
   action ipv6_forward(port_num_t port_num) {
        standard_metadata.egress_spec = port_num;
    }

    table forward_table {
        actions = {
            ipv6_forward;
        }
        key = {
            hdr.ipv6.src_addr: exact;
            hdr.ipv6.dst_addr: exact;
        }
    }

    // identify table
    action identify() {
		//nothing
    }

    table identify_table {
        actions = {
            identify;
        }
        key = {
            hdr.ipv6.src_addr: exact;
            hdr.ipv6.dst_addr: exact;
        }
    }

    firewall() nf0;
    load_balance() nf1;
    big_flow_detector() nf2;
    nat() nf3;

    apply {
        // If this is a packet-out from the controller...
        if (hdr.packet_out.isValid()) {
            standard_metadata.egress_spec = hdr.packet_out.egress_port;
            hdr.packet_out.setInvalid();
            exit;
        }
        // If this is the first packet
        if(!identify_table.apply().hit){
            send_to_cpu();
            exit;
        }
        if(!forward_table.apply().hit){
            l2_ternary_table.apply();
        }
        acl_table.apply();  
    }
}


control EgressPipeImpl (inout parsed_headers_t hdr,
                        inout local_metadata_t local_metadata,
                        inout standard_metadata_t standard_metadata) {
    apply {
        if (standard_metadata.egress_port == CPU_PORT) {
            hdr.packet_in.setValid();
            hdr.packet_in.ingress_port = standard_metadata.ingress_port;
            exit;
        }

        if (local_metadata.is_multicast == true &&
              standard_metadata.ingress_port == standard_metadata.egress_port) {
            mark_to_drop(standard_metadata);
        }
    }
}


control ComputeChecksumImpl(inout parsed_headers_t hdr,
                            inout local_metadata_t local_metadata)
{
    apply {
        update_checksum(hdr.ndp.isValid(),
            {
                hdr.ipv6.src_addr,
                hdr.ipv6.dst_addr,
                hdr.ipv6.payload_len,
                8w0,
                hdr.ipv6.next_hdr,
                hdr.icmpv6.type,
                hdr.icmpv6.code,
                hdr.ndp.flags,
                hdr.ndp.target_ipv6_addr,
                hdr.ndp.type,
                hdr.ndp.length,
                hdr.ndp.target_mac_addr
            },
            hdr.icmpv6.checksum,
            HashAlgorithm.csum16
        );
    }
}


control DeparserImpl(packet_out packet, in parsed_headers_t hdr) {
    apply {
        packet.emit(hdr.packet_in);
        packet.emit(hdr.ethernet);
        packet.emit(hdr.tunnel);
        packet.emit(hdr.ipv6);
        packet.emit(hdr.tcp);
        packet.emit(hdr.udp);
        packet.emit(hdr.icmpv6);
        packet.emit(hdr.ndp);
    }
}


V1Switch(
    ParserImpl(),
    VerifyChecksumImpl(),
    IngressPipeImpl(),
    EgressPipeImpl(),
    ComputeChecksumImpl(),
    DeparserImpl()
) main;
