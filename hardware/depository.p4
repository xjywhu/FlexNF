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


// Created on 2021.5.17
// Including
#include "core.p4"
#if __TARGET_TOFINO__ == 2
#include "t2na.p4"
#else
#include "tna.p4"
#endif

#include "common/util.p4"
#include "common/headers.p4"

typedef bit<16> idx_t;
typedef bit<8> reg_t;
#define MAX_ENTRY 65000


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

control stateful_fw(
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
          hdr.tcp.src_port: exact;
          hdr.tcp.dst_port: exact;
        }
        size = MAX_ENTRY;
    }

    RegisterAction<reg_t, idx_t, bit<1>>(state) write_state_flag2 = {
        void apply(inout reg_t value, out bit<1> rv){
            bit<1> tmp = (bit<1>)value;
            if(tmp == 0){
                value = value + 1;
                rv = 0;
            }
            else{
                value = 9;
                rv = 1;
            }
        }
    };

    RegisterAction<reg_t, idx_t, bit<1>>(state) write_state_flag0 = {
        void apply(inout reg_t value, out bit<1> rv){
            if(value != 3){
                value = 9;
                rv = 1;
            }
            else
                rv = 0;
        }
    };

    RegisterAction<reg_t, idx_t, bit<1>>(state) write_state_flag1 = {
        void apply(inout reg_t value, out bit<1> rv){
            if(value == 3 || value == 5){
                value = value + 1;
                rv = 0;
            }
            else{
                value = 9;
                rv = 1;
            }
        }
    };

    RegisterAction<reg_t, idx_t, bit<1>>(state) write_state_flag18 = {
        void apply(inout reg_t value, out bit<1> rv){
            if(value == 1){
                value = 2;
                rv = 0;
            }
            else{
                value = 9;
                rv = 1;
            }
        }
    };

    action state_action0(){
        ig_md.state = write_state_flag0.execute(ig_md.idx);
    }

    action state_action1(){
        ig_md.state = write_state_flag1.execute(ig_md.idx);
    }

    action state_action2(){
        ig_md.state = write_state_flag2.execute(ig_md.idx);
    }

    action state_action18(){
        ig_md.state = write_state_flag18.execute(ig_md.idx);
    }


    table condition_table{
        actions = {
            state_action0;
            state_action1;
            state_action2;
            state_action18;
        }
        key = {
          hdr.tcp.flags: exact;
        }
        size = 10;
    }
    

    action valid_action(){
        bit<4> tmp = 0;
        tmp = tmp + 1;
        tmp = tmp + 1;
        tmp = tmp + 1;
    }

    action drop(){
        // ig_intr_dprsr_md.drop_ctl = 0x1; // Drop packet.
        bit<4> tmp = 0;
        tmp = tmp + 1;
        tmp = tmp + 1;
        tmp = tmp + 1;
    }

    table action_table{
        actions = {
            valid_action;
            drop;
        }
        key = {
          ig_md.state: exact;
        }
        size = 2;
        const default_action = drop;
    }

    apply{
        state_table.apply();
        condition_table.apply();
        action_table.apply();
    }
}



control syn_flood_detection(
        inout header_t hdr,
        inout metadata_t ig_md,
        in ingress_intrinsic_metadata_t ig_intr_md,
        in ingress_intrinsic_metadata_from_parser_t ig_intr_prsr_md,
        inout ingress_intrinsic_metadata_for_deparser_t ig_intr_dprsr_md,
        inout ingress_intrinsic_metadata_for_tm_t ig_intr_tm_md) {
    Register<bit<32>, idx_t>(32w60000, 0) state;

    RegisterAction<bit<32>, idx_t, bit<1>>(state) write_state_action = {
        void apply(inout bit<32> value, out bit<1> rv){
            value = value + 32w1;
            if(value > MAX_PACKET){
                rv = 1;
            }
            else{
                rv = 0;
            }
        }
    };

    action increment(){
        // ig_md.idx = idx;
        ig_md.bfd_state = write_state_action.execute(ig_md.idx);
    }

    table condition_table{
        actions = {
            increment;
        }
        // key = {
        //   hdr.ipv4.src_addr: exact;
        //   hdr.ipv4.dst_addr: exact;
        // }
        const default_action = increment;
        size = MAX_ENTRY;
    }

    action goto_big(){
        bit<4> tmp = 0;
        tmp = tmp + 1;
        tmp = tmp + 1;
        tmp = tmp + 1;
        tmp = tmp + 1;
        tmp = tmp + 1;
        tmp = tmp + 1;
        tmp = tmp + 1;
        tmp = tmp + 1;
        tmp = tmp + 1;
        tmp = tmp + 1;
        // ig_intr_dprsr_md.drop_ctl = 0x1; // Drop packet.
    }

    table action_table{
        actions = {
            goto_big;
        }
        // key = {
        //     ig_md.state: exact;
        // }
        size = 2;
        const default_action = goto_big;
    }

    apply{
        condition_table.apply();
        if(ig_md.bfd_state == 1){ // if state is larger than 0
            action_table.apply();
        }
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

    stateful_fw() nf0;
    stateful_fw() nf1;
    stateful_fw() nf2;
    stateful_fw() nf3;
    stateful_fw() nf4;
    stateful_fw() nf5;
    stateful_fw() nf6;
    stateful_fw() nf7;
    stateful_fw() nf8;
    stateful_fw() nf9;
    // stateful_fw() nf10;

    apply{
        nf0.apply(hdr, ig_md, ig_intr_md, ig_intr_prsr_md, ig_intr_dprsr_md, ig_intr_tm_md);
        nf1.apply(hdr, ig_md, ig_intr_md, ig_intr_prsr_md, ig_intr_dprsr_md, ig_intr_tm_md);
        nf2.apply(hdr, ig_md, ig_intr_md, ig_intr_prsr_md, ig_intr_dprsr_md, ig_intr_tm_md);
        nf3.apply(hdr, ig_md, ig_intr_md, ig_intr_prsr_md, ig_intr_dprsr_md, ig_intr_tm_md);
        nf4.apply(hdr, ig_md, ig_intr_md, ig_intr_prsr_md, ig_intr_dprsr_md, ig_intr_tm_md);
        nf5.apply(hdr, ig_md, ig_intr_md, ig_intr_prsr_md, ig_intr_dprsr_md, ig_intr_tm_md);
        nf6.apply(hdr, ig_md, ig_intr_md, ig_intr_prsr_md, ig_intr_dprsr_md, ig_intr_tm_md);
        nf7.apply(hdr, ig_md, ig_intr_md, ig_intr_prsr_md, ig_intr_dprsr_md, ig_intr_tm_md);
        nf8.apply(hdr, ig_md, ig_intr_md, ig_intr_prsr_md, ig_intr_dprsr_md, ig_intr_tm_md);
        nf9.apply(hdr, ig_md, ig_intr_md, ig_intr_prsr_md, ig_intr_dprsr_md, ig_intr_tm_md);
        // nf10.apply(hdr, ig_md, ig_intr_md, ig_intr_prsr_md, ig_intr_dprsr_md, ig_intr_tm_md);
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
