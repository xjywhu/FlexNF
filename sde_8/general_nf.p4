 /* -*- P4_16 -*- */

/*
 * Copyright (c) pcl, Inc.
 *
 *
 *zuozhe：pcl:lll,vk
 */


#include <core.p4>
#if __TARGET_TOFINO__ == 2
#include <t2na.p4>
#else
#include <tna.p4>
#endif
#include "common/headers.p4"
#include "common/util.p4"
/* MACROS */

/*************************************************************************
*********************** H E A D E R S  ***********************************
*************************************************************************/

typedef bit<48> macAddr_t;
typedef bit<32> ip4Addr_t;

header ethernet_t {
    macAddr_t dstAddr;
    macAddr_t srcAddr;
    bit<16>   etherType;
}

header ipv4_t {
    bit<4>    version;
    bit<4>    ihl;
    bit<8>    diffserv;
    bit<16>   totalLen;
    bit<16>   identification;
    bit<3>    flags;
    bit<13>   fragOffset;
    bit<8>    ttl;
    bit<8>    protocol;
    bit<16>   hdrChecksum;
    ip4Addr_t srcAddr;
    ip4Addr_t dstAddr;
}

header tcp_t {
    bit<16> srcPort;
    bit<16> dstPort;
    bit<32> seqNo;
    bit<32> ackNo;
    bit<4>  dataOffset;
    bit<4>  res;
    bit<8>  flags;
    bit<16> window;
    bit<16> checksum;
    bit<16> urgentPtr;
}

header udp_t {
    bit<16> srcPort;
    bit<16> dstPort;
    bit<16> length_;
    bit<16> checksum;
}

struct my_ingress_metadata_t {
	ip4Addr_t key1;
    ip4Addr_t key2;
	bit<16> key3;
	bit<16> key4;
	bit<8>  key5;
	bit<8>	flag;
	bool crc_update;
}

struct my_ingress_headers_t {
    // my change
    ethernet_t  ethernet;
	arp_h       arp;
    ipv4_t      ipv4;
    tcp_t       tcp;
    udp_t       udp; 
}

    /***********************  H E A D E R S  ************************/

struct my_egress_headers_t {
}

    /********  G L O B A L   E G R E S S   M E T A D A T A  *********/

struct my_egress_metadata_t {
}


const bit<16> TYPE_IPV4 = 0x800;
const bit<8> PROTO_TCP = 6;
const bit<8> PROTO_UDP = 17;
const bit<16> TYPE_ARP = 0x0806;

/*************************************************************************
*********************** P A R S E R  ***********************************
*************************************************************************/
parser IngressParser(packet_in        pkt,
    out my_ingress_headers_t          hdr,
    out my_ingress_metadata_t         meta,
    out ingress_intrinsic_metadata_t  ig_intr_md)
{
	//TofinoIngressParser() tofino_parser;
    state start {
		pkt.extract(ig_intr_md);
		transition select(ig_intr_md.resubmit_flag){
            1: parse_resubmit;
            0: parse_port_metadata;
        }
    }
	
    state parse_resubmit {
       // pkt.extract(meta.resubmit_data);
        transition parse_ethernet;
    }
   state parse_port_metadata {
	   pkt.advance(PORT_METADATA_SIZE);
       transition parse_ethernet;
   }
    //
    state parse_ethernet {
        pkt.extract(hdr.ethernet);
        transition select(hdr.ethernet.etherType) {
            TYPE_IPV4   : parse_ipv4;
			TYPE_ARP    : parse_arp; 
        }
    }
   
   state parse_ipv4 {
		
        pkt.extract(hdr.ipv4);
        transition select(hdr.ipv4.protocol) {
            PROTO_TCP   : parse_tcp;
            PROTO_UDP   : parse_udp;
		 	//default: accept;
        }
   }
     
    state parse_tcp {
        pkt.extract(hdr.tcp);
		// meta.srcPort=hdr.tcp.srcPort;
		// meta.dstPort=hdr.tcp.dstPort;
        transition accept;
    }
    
    state parse_udp {
        pkt.extract(hdr.udp);
		// meta.srcPort=hdr.udp.srcPort;
		// meta.dstPort=hdr.udp.dstPort;
        transition accept;
    }
	
	state parse_arp{
		pkt.extract(hdr.arp);
        transition accept;
	}
}

   
control Ingress(
    /* User */
    inout my_ingress_headers_t                       hdr,
    inout my_ingress_metadata_t                      meta,
    /* Intrinsic */
    in    ingress_intrinsic_metadata_t               ig_intr_md,
    in    ingress_intrinsic_metadata_from_parser_t   ig_prsr_md,
    inout ingress_intrinsic_metadata_for_deparser_t  ig_dprsr_md,
    inout ingress_intrinsic_metadata_for_tm_t        ig_tm_md
     )
{	
	Register<bit<8>, bit<8>>(1) flow_index_register;
	RegisterAction<bit<8>,bit<8>,bit<8>>(flow_index_register) read_flow_value = {
        void apply(inout bit<8> register_data, out bit<8> read_value){
            read_value=register_data;
        }
    };
	
	
	action switch_action_pre()
	{
		meta.key3=(bit<16>)(hdr.ethernet.dstAddr>>32);  //高16位
		meta.key1=(bit<32>)hdr.ethernet.dstAddr;        //低32位
		
		meta.key2=0x0;
		meta.key4=0x0;
		meta.key5=0x0;
	}
	
	
	action router_action_pre()
	{
		meta.key1=hdr.ipv4.dstAddr;
		
		meta.key2=0x0;
		meta.key3=0x0;
		meta.key4=0x0;
		meta.key5=0x0;
	}
	
	action firewall_action_pre()
	{
		meta.key1=hdr.ipv4.srcAddr;
		meta.key2=hdr.ipv4.dstAddr;
		
		meta.key3=0x0;
		meta.key4=0x0;
		meta.key5=0x0;
	}
	
	action arp_action_pre()
	{
		meta.key1=hdr.arp.recv_ip;
		meta.key2=(bit<32>)hdr.arp.opcode; 
		
		meta.key3=0x0;
		meta.key4=0x0;
		meta.key5=0x0;
	}

	@pragma stage 2
	table decide_function_table{
		 key={
			meta.flag:exact;
		}
		actions={
			//flag=0;
			switch_action_pre;
			//flag=1;
			router_action_pre;
			//flag=2;
			firewall_action_pre;
            //flag=3;
			arp_action_pre;
			NoAction;
		}	
		default_action=NoAction;
		size=256;
	}
	
	action switch_action(PortId_t port){
		ig_tm_md.ucast_egress_port = port;
	}
	
	action router_action(macAddr_t srcMacAddr,macAddr_t dstMacAddr,PortId_t port){
		ig_tm_md.ucast_egress_port = port;
        hdr.ethernet.srcAddr = srcMacAddr;
        hdr.ethernet.dstAddr = dstMacAddr;
        hdr.ipv4.ttl = hdr.ipv4.ttl - 1;
		meta.crc_update=true;
	}

    action firewall_action(){
		ig_tm_md.ucast_egress_port = 0;
    }

    action arp_action(macAddr_t mac_addr){
		ig_tm_md.ucast_egress_port = ig_intr_md.ingress_port;
		hdr.ethernet.dstAddr=hdr.arp.send_mac;
		hdr.ethernet.srcAddr=mac_addr;

		ip4Addr_t tmp_ip_addr;
		tmp_ip_addr = hdr.arp.recv_ip;
		hdr.arp.recv_ip=hdr.arp.send_ip;
		hdr.arp.recv_mac=hdr.arp.send_mac;
		hdr.arp.send_mac=mac_addr;
		hdr.arp.send_ip=tmp_ip_addr;
		hdr.arp.opcode=2;
    }
	
	action defaut_egress_port()
	{
		ig_tm_md.ucast_egress_port = 184;
		hdr.ethernet.dstAddr=(bit<48>)meta.key5;
		hdr.ethernet.srcAddr=(bit<48>)meta.key4;
		hdr.ipv4.srcAddr= meta.key1;
		hdr.ipv4.dstAddr= meta.key2;
	}
	@pragma stage 3
	table general_table{
	    key={
			meta.key1:exact;
			meta.key2:exact;
			meta.key3:exact;
			meta.key4:exact;
			meta.key5:exact;
		}
		actions={
			// key1=dstMAC[0:31], key3=dstMAC[32:47], 其他为0
			switch_action;
			// key1=dstIP, 其他为0
			router_action;
			// key1=srcIP, key2=dstIP, 其他为0
			firewall_action;
			// key1=arp.recv_ip, key2=arp.opcode, 其他为0
            arp_action;
			//defaut_egress_port;
			NoAction;
		}	
		default_action=NoAction;
		size=256;
	}

	apply {
			meta.flag=read_flow_value.execute(0);
			decide_function_table.apply();
			general_table.apply();
			ig_tm_md.bypass_egress = true;
	}
  
}


control IngressDeparser(packet_out pkt,
    /* User */
    inout my_ingress_headers_t                       hdr,
    in    my_ingress_metadata_t                      meta,
    /* Intrinsic */
    in    ingress_intrinsic_metadata_for_deparser_t  ig_dprsr_md)
{
	//Checksum() ipv4_checksum;
	Checksum<bit<16>>(HashAlgorithm_t.CSUM16) ipv4_checksum;
	
    apply {
		if(meta.crc_update)
		{
			hdr.ipv4.hdrChecksum = ipv4_checksum.update(
			{hdr.ipv4.version,
			hdr.ipv4.ihl,
			hdr.ipv4.diffserv,
			hdr.ipv4.totalLen,
			hdr.ipv4.identification,
			hdr.ipv4.flags,
			hdr.ipv4.fragOffset,
			hdr.ipv4.ttl,
			hdr.ipv4.protocol,
			hdr.ipv4.srcAddr,
			hdr.ipv4.dstAddr});
		}	 
		pkt.emit(hdr);
    }
}



/*************************************************************************
***********************  S W I T C H  *******************************
*************************************************************************/

/************ F I N A L   P A C K A G E ******************************/
Pipeline(
    IngressParser(),
    Ingress(),
    IngressDeparser(),
    EmptyEgressParser<header_t, my_ingress_metadata_t>(),
    EmptyEgress<header_t, my_ingress_metadata_t>(),
    EmptyEgressDeparser<header_t, my_ingress_metadata_t>()
) pipe;

Switch(pipe) main;


