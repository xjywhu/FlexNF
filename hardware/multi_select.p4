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

// NAT, FW, Load Balancer

#include "core.p4"
#if __TARGET_TOFINO__ == 2
#include "t2na.p4"
#else
#include "tna.p4"
#endif

#include "common/util.p4"
#include "common/headers.p4"
#define MAX_PACKET 10000

struct metadata_t {
    bit<32> state;
    bit<1> idx;
    bit<32> count;
    bit<30> label;
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
        tofino_parser.apply(pkt, ig_intr_md);
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

    // --- l2_exact_table (for unicast entries) --------------------------------
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
    }

    action dummy(){
        bit<3> tmp = 0;
        tmp = tmp + 1;
        tmp = tmp + 1;
        tmp = tmp + 1;
        tmp = tmp + 1;
        tmp = tmp + 1;
        tmp = tmp + 1;
        tmp = tmp + 1;
    }
    table table_1 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_2 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_3 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_4 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_5 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_6 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_7 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_8 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_9 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_10 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_11 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_12 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_13 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_14 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_15 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_16 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_17 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_18 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_19 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_20 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_21 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_22 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_23 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_24 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_25 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_26 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_27 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_28 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_29 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }
    table table_30 {
        actions = {
            dummy;
        }
        const default_action = dummy;
    }

    action get_label(bit<30> label){
        ig_md.label = label;
    }
    table add_label_table {
        actions = {
            get_label;
        }
        key = {
            hdr.ipv4.src_addr: exact;
        }
    }

    apply {
        add_label_table.apply();
        if(ig_md.label&1!= 0){
            if((ig_md.label&30w1) != 0){
                table_1.apply();
            }
            if((ig_md.label&30w2) != 0){
                table_2.apply();
            }
            if((ig_md.label&30w4) != 0){
                table_3.apply();
            }
            if((ig_md.label&30w8) != 0){
                table_4.apply();
            }
            if((ig_md.label&30w16) != 0){
                table_5.apply();
            }
            if((ig_md.label&30w32) != 0){
                table_6.apply();
            }
            if((ig_md.label&30w64) != 0){
                table_7.apply();
            }
            if((ig_md.label&30w128) != 0){
                table_8.apply();
            }
            if((ig_md.label&30w256) != 0){
                table_9.apply();
            }
            if((ig_md.label&30w512) != 0){
                table_10.apply();
            }
            if((ig_md.label&30w1024) != 0){
                table_11.apply();
            }
            if((ig_md.label&30w2048) != 0){
                table_12.apply();
            }
            if((ig_md.label&30w4096) != 0){
                table_13.apply();
            }
            if((ig_md.label&30w8192) != 0){
                table_14.apply();
            }
            if((ig_md.label&30w16384) != 0){
                table_15.apply();
            }
            if((ig_md.label&30w32768) != 0){
                table_16.apply();
            }
            if((ig_md.label&30w65536) != 0){
                table_17.apply();
            }
            if((ig_md.label&30w131072) != 0){
                table_18.apply();
            }
            if((ig_md.label&30w262144) != 0){
                table_19.apply();
            }
            if((ig_md.label&30w524288) != 0){
                table_20.apply();
            }
            if((ig_md.label&30w1048576) != 0){
                table_21.apply();
            }
            if((ig_md.label&30w2097152) != 0){
                table_22.apply();
            }
            if((ig_md.label&30w4194304) != 0){
                table_23.apply();
            }
            if((ig_md.label&30w8388608) != 0){
                table_24.apply();
            }
            if((ig_md.label&30w16777216) != 0){
                table_25.apply();
            }
            if((ig_md.label&30w33554432) != 0){
                table_26.apply();
            }
            if((ig_md.label&30w67108864) != 0){
                table_27.apply();
            }
            if((ig_md.label&30w134217728) != 0){
                table_28.apply();
            }
            if((ig_md.label&30w268435456) != 0){
                table_29.apply();
            }
            if((ig_md.label&30w536870912) != 0){
                table_30.apply();
            }
        }
        routing_table.apply();
    }
}

Pipeline(SwitchIngressParser(),
         SwitchIngress(),
         SwitchIngressDeparser(),
         SwitchEgressParser(),
         EmptyEgress<header_t, metadata_t>(),
         EmptyEgressDeparser<header_t, metadata_t>()) pipe;

Switch(pipe) main;
