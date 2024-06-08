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


#include "core.p4"
#if __TARGET_TOFINO__ == 2
#include "t2na.p4"
#else
#include "tna.p4"
#endif

#include "common/util.p4"
#include "common/headers.p4"
#define MAX_PACKET 10000
#define idx_t bit<14>
#define RESUB_TIMES 1


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
        tofino_parser.apply(pkt,ig_md, ig_intr_md);
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
    action dummy(){
        ig_intr_tm_md.ucast_egress_port = 128;
    }

    table routing_table {
      key = {
          hdr.ipv4.dst_addr: exact;
      }
      actions = {
          set_egress_port;
          dummy;
      }
      const default_action = dummy;
      size = 20;
    }

    action get_label(bit<8> label){
        ig_md.resubmit_data.times = label;
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
        if(ig_intr_md.resubmit_flag == 0)
            add_label_table.apply();
        // resubmit
        if(ig_md.resubmit_data.times <= 0){
            ig_md.resubmit_data.flag = 0;
        }
        else{
            ig_md.resubmit_data.flag = 1;
            ig_md.resubmit_data.times = ig_md.resubmit_data.times - 1;
        }
        routing_table.apply();
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
    Resubmit() resubmit;

    apply {
        // resubmit
        if(ig_md.resubmit_data.flag == 1){
            resubmit.emit(ig_md.resubmit_data);
        }
        pkt.emit(hdr);
    }
}


Pipeline(SwitchIngressParser(),
         SwitchIngress(),
         SwitchIngressDeparser(),
         SwitchEgressParser(),
         EmptyEgress<header_t, metadata_t>(),
         EmptyEgressDeparser<header_t, metadata_t>()) pipe;

Switch(pipe) main;
