/*******************************************************************************
 * BAREFOOT NETWORKS CONFIDENTIAL & PROPRIETARY
 *
 * Copyright (c) 2018-2019 Barefoot Networks, Inc.
 * All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains the property of
 * Barefoot Networks, Inc. and its suppliers, if any. The intellectual and
 * technical concepts contained herein are proprietary to Barefoot Networks,
 * Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents, patents in
 * process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material is
 * strictly forbidden unless prior written permission is obtained from
 * Barefoot Networks, Inc.
 *
 * No warranty, explicit or implicit is provided, unless granted under a
 * written agreement with Barefoot Networks, Inc.
 *
 *
 ******************************************************************************/


// Stateful FW only
#include "core.p4"
#if __TARGET_TOFINO__ == 2
#include "t2na.p4"
#else
#include "tna.p4"
#endif

#include "common/util.p4"
#include "common/headers.p4"

typedef bit<16> idx_t;
typedef bit<2> reg_t;
#define MAX_ENTRY 65000


struct metadata_t {
    bit<1> flag;
}


// ---------------------------------------------------------------------------
// Ingress parser
// ---------------------------------------------------------------------------
parser SwitchIngressParser(
        packet_in pkt,
        out header_t hdr,
        out metadata_t ig_md,
        out ingress_intrinsic_metadata_t ig_intr_md) {

    TofinoIngressParser() tofino_parser;

    state start {
        tofino_parser.apply(pkt, ig_md, ig_intr_md);
        transition parse_ethernet;
    }

    state parse_ethernet {
        pkt.extract(hdr.ethernet);
        transition select (hdr.ethernet.ether_type) {
            ETHERTYPE_IPV4 : parse_ipv4;
            default : reject;
        }
    }
    state parse_ipv4 {
        pkt.extract(hdr.ipv4);
        transition accept;
    }
}

// ---------------------------------------------------------------------------
// Egress parser
// ---------------------------------------------------------------------------
parser SwitchEgressParser(
        packet_in pkt,
        out header_t hdr,
        out metadata_t eg_md,
        out egress_intrinsic_metadata_t eg_intr_md) {
    TofinoEgressParser() tofino_parser;

    state start {
        tofino_parser.apply(pkt, eg_intr_md);
        transition accept;
    }
}

// ---------------------------------------------------------------------------
// Ingress Deparser
// ---------------------------------------------------------------------------
control SwitchIngressDeparser(
        packet_out pkt,
        inout header_t hdr,
        in metadata_t ig_md,
        in ingress_intrinsic_metadata_for_deparser_t ig_intr_dprsr_md) {

    apply {
        pkt.emit(hdr);
    }
}

control udp_sfw(
        inout header_t hdr,
        inout metadata_t ig_md,
        in ingress_intrinsic_metadata_t ig_intr_md,
        in ingress_intrinsic_metadata_from_parser_t ig_intr_prsr_md,
        inout ingress_intrinsic_metadata_for_deparser_t ig_intr_dprsr_md,
        inout ingress_intrinsic_metadata_for_tm_t ig_intr_tm_md) {
    Register<reg_t, idx_t>(MAX_ENTRY, 0) state;

    action read_idx(idx_t idx){
        ig_md.idx = idx;
    }

    table state_table{
        actions = {
            read_idx;
        }
        key = {
          hdr.ipv4.src_addr: exact;
          hdr.ipv4.dst_addr: exact;
          hdr.ipv4.protocol: exact;
          hdr.udp.src_port: exact;
          hdr.udp.dst_port: exact;
        }
        size = MAX_ENTRY;
    }

    // if port==1, state transitions
    RegisterAction<reg_t, idx_t, bit<1>>(state) port_1_action = {
        void apply(inout reg_t value, out bit<1> rv){
        	if(value==0){
        		rv = 1;
        	}
        	else{
        		rv = 0; 
        		value = 2;
        	}
        }
    };

    // if port==2, state transitions
    RegisterAction<reg_t, idx_t, bit<1>>(state) port_2_action = {
        void apply(inout reg_t value, out bit<1> rv){
        	if(value == 0){
        		value = 1;
        	}
        }
    };

    action state_action1(){
        ig_md.flag = port_1_action.execute(ig_md.idx);
    }

    action state_action2(){
        ig_md.flag = port_2_action.execute(ig_md.idx);
    }

    table condition_table{
        actions = {
            state_action1;
            state_action2;
        }
        key = {
        	ig_intr_md.ingress_port: exact;
        }
        size = 2;
    }

    action drop(){
        ig_intr_dprsr_md.drop_ctl = 0x1; // Drop packet.
    }

    table action_table{
        actions = {
            drop;
            NoAction;
        }
        key = {
          ig_md.flag: exact;
        }
        size = 2;
        const default_action = NoAction;
    }

    apply{
        state_table.apply();
        condition_table.apply();
        action_table.apply();
    }
}

control SwitchIngress(
        inout header_t hdr,
        inout metadata_t ig_md,
        in ingress_intrinsic_metadata_t ig_intr_md,
        in ingress_intrinsic_metadata_from_parser_t ig_intr_prsr_md,
        inout ingress_intrinsic_metadata_for_deparser_t ig_intr_dprsr_md,
        inout ingress_intrinsic_metadata_for_tm_t ig_intr_tm_md) {

    action drop() {
        ig_intr_dprsr_md.drop_ctl = 0x1; // Drop packet.
    }


    action set_egress_port(PortId_t port) {
        ig_intr_tm_md.ucast_egress_port = port;
    }

    table routing_table {
        key = {
          hdr.ipv4.dst_addr: exact;
        }
        actions = {
          set_egress_port;
        }
        size = 20;
    }

    udp_sfw() nf0;

    apply{
        nf0.apply(hdr, ig_md, ig_intr_md, ig_intr_prsr_md, ig_intr_dprsr_md, ig_intr_tm_md);
        routing_table.apply();
    }
}

Pipeline(SwitchIngressParser(),
         SwitchIngress(),
         SwitchIngressDeparser(),
         SwitchEgressParser(),
         EmptyEgress<header_t, metadata_t>(),
         EmptyEgressDeparser<header_t, metadata_t>()) p1;

Switch(p1) main;
