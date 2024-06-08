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


package org.onosproject.ngsdn.tutorial;

import com.google.common.collect.Lists;
import org.onlab.packet.Ip6Address;
import org.onlab.packet.Ip6Prefix;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onlab.util.ItemNotFoundException;
import org.onosproject.core.ApplicationId;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.Link;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.device.*;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.group.GroupDescription;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.onosproject.net.host.InterfaceIpAddress;
import org.onosproject.net.intf.Interface;
import org.onosproject.net.intf.InterfaceService;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.link.LinkListener;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiActionProfileGroupId;
import org.onosproject.net.pi.runtime.PiTableAction;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.onosproject.ngsdn.tutorial.common.FabricDeviceConfig;
import org.onosproject.ngsdn.tutorial.common.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onlab.packet.IpAddress;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onlab.packet.Ethernet;
import org.onosproject.net.HostId;
import org.onosproject.net.pi.model.PiRegisterId;
import org.onosproject.net.pi.runtime.PiRegisterCell;
import org.onosproject.net.pi.runtime.PiRegisterCellId;
import org.onosproject.net.pi.runtime.data.PiBitString;
import org.onlab.util.ImmutableByteSequence;
import javax.xml.bind.DatatypeConverter;
import org.onlab.packet.Ip6Address;
import org.onosproject.net.statistic.*;
import org.onosproject.net.PortNumber;
import org.onosproject.net.topology.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.onosproject.net.Path;
import org.onosproject.net.DisjointPath;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.onosproject.net.HostLocation;
import org.onosproject.net.link.*;
import org.onosproject.net.host.*;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Link.Type;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.provider.*;
import org.onlab.packet.VlanId;
import java.util.concurrent.atomic.AtomicBoolean;

import java.io.*;

import static com.google.common.collect.Streams.stream;
import static org.onosproject.ngsdn.tutorial.AppConstants.INITIAL_SETUP_DELAY;

/**
 * App component that recept and process packe_in packets received from edge switches
 * across the whole fabric.
 */
@Component(
        immediate = true,
        enabled = true
)
public class ProcessPacketIn {
    private static final Logger log = LoggerFactory.getLogger(ProcessPacketIn.class);

    private ApplicationId appId;
    public static final int HOST_TOPO = 12;
    public static final int LINK_TOPO = 30;
    public static final String reqFile = "/root/onos/apache-karaf-4.2.6/config/requirements.txt";
    public static final String confFile = "/root/onos/apache-karaf-4.2.6/config/config.txt";
    // private static List<String> handled = new ArrayList<>();
    private final DeviceListener deviceListener = new InternalDeviceListener();
    private final TopologyListener topologyListener = new InternalTopologyListener();
    private LinkProvider linkProvider = new InternalLinkProvider();
    private HostProvider hostProvider = new InternalHostProvider();
    private LinkProviderService linkProviderService;
    private HostProviderService hostProviderService;
    private static final ProviderId linkProviderID = new ProviderId("link", "test.app");
    private static final ProviderId hostProviderID = new ProviderId("host", "test.app");
    private static volatile HashMap<String, PortNumber> requestPort = new HashMap<String, PortNumber>();
    private static volatile HashMap<Integer, DeviceId> nfConfig = new HashMap<Integer, DeviceId>();
    private static AtomicBoolean isHappened = new AtomicBoolean(false);
    private static HashMap<ConnectPoint, ArrayList<Double>> portRate = new HashMap<ConnectPoint, ArrayList<Double>>();

    //--------------------------------------------------------------------------
    // ONOS CORE SERVICE BINDING
    //
    // These variables are set by the Karaf runtime environment before calling
    // the activate() method.
    //--------------------------------------------------------------------------

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected GroupService groupService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigService networkConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected InterfaceService interfaceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StatisticService statisticService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private PathService pathService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LinkProviderRegistry linkProviderRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostProviderRegistry hostProviderRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LinkService linkService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PortStatisticsService portStatisticsService;
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private MainComponent mainComponent;

    private ReactivePacketProcessor processor = new ReactivePacketProcessor();
    //--------------------------------------------------------------------------
    // COMPONENT ACTIVATION.
    //
    // When loading/unloading the app the Karaf runtime environment will call
    // activate()/deactivate().
    //--------------------------------------------------------------------------

    @Activate
    protected void activate() {
        appId = mainComponent.getAppId();
        deviceService.addListener(deviceListener);
        topologyService.addListener(topologyListener);
        linkProviderService = linkProviderRegistry.register(linkProvider);
        hostProviderService = hostProviderRegistry.register(hostProvider);
        log.info("Started");

        // Schedule set up for all devices.
        mainComponent.scheduleTask(this::setUpPacketHandle, INITIAL_SETUP_DELAY);
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
        deviceService.removeListener(deviceListener);
        topologyService.removeListener(topologyListener);
        packetService.removeProcessor(processor);
        linkProviderRegistry.unregister(linkProvider);
        hostProviderRegistry.unregister(hostProvider);
    }

    private synchronized void setUpPacketHandle(){
        log.info("Activate thread");

        packetService.addProcessor(processor, PacketProcessor.director(2));
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        packetService.requestPackets(selector.build(), PacketPriority.CONTROL, appId);
    }

    private synchronized void writeRate(){
        try{
            FileOutputStream fout=new FileOutputStream("/root/onos/apache-karaf-4.2.6/config/result.txt");

            for (Map.Entry<ConnectPoint, ArrayList<Double>> entry : portRate.entrySet()){
                fout.write(entry.getKey().toString().getBytes());
                fout.write("\n".getBytes());
                for(Double value : entry.getValue()){
                    fout.write(String.valueOf​(value).getBytes());
                    fout.write("\n".getBytes());
                }
            }
            // //Create blank workbook
            // XSSFWorkbook workbook = new XSSFWorkbook();
            // //Create a blank sheet
            // XSSFSheet spreadsheet = workbook.createSheet( "Port Rate");
            // //Create row object
            // XSSFRow row;
            // //Iterate over data and write to sheet
            // Set<ConnectPoint> keyid = portRate.keySet();
            // int rowid = 0;
            // for (ConnectPoint key : keyid) {
            //     row = spreadsheet.createRow(rowid++);
            //     ArrayList<Double> objectArr = portRate.get(key);
            //     int cellid = 0;
            //     for (Double obj : objectArr){
            //     Cell cell = row.createCell(cellid++);
            //     cell.setCellValue(obj);
            //     }
            // }
            // workbook.write(fout);
            // fout.close();
            System.out.println("result.txt written successfully");
        }catch(Exception e){
            e.printStackTrace();
        }

    }

    public String ABCToNum(char c){
        if(c>='0' && c<='9')
            return String.valueOf(c);
        if(c=='A' || c=='a')
            return "10";
        if(c=='B' || c=='b')
            return "11";
        if(c=='C' || c=='c')
            return "12";
        return null;
    }


    // 添加s8-s11, s8-s12, s10-s11的链路
    private synchronized void configureLinks(){
        log.info("------------------------------------");
        log.info("Starting to add links to topology");
        DefaultLinkDescription l1 = new DefaultLinkDescription​(ConnectPoint.deviceConnectPoint​("device:s8/4"), ConnectPoint.deviceConnectPoint​("device:s11/3"), 
             Link.Type.DIRECT, DefaultLinkDescription.NOT_EXPECTED, DefaultAnnotations.EMPTY);
        DefaultLinkDescription l2 = new DefaultLinkDescription​(ConnectPoint.deviceConnectPoint​("device:s8/5"), ConnectPoint.deviceConnectPoint​("device:s12/2"), 
             Link.Type.DIRECT, DefaultLinkDescription.NOT_EXPECTED, DefaultAnnotations.EMPTY);
        DefaultLinkDescription l3 = new DefaultLinkDescription​(ConnectPoint.deviceConnectPoint​("device:s10/3"), ConnectPoint.deviceConnectPoint​("device:s11/2"), 
             Link.Type.DIRECT, DefaultLinkDescription.NOT_EXPECTED, DefaultAnnotations.EMPTY);
        // 问题出在这
        // deviceService.getDeviceCount();
        try{
            linkProviderService.linkDetected​(l1);
            linkProviderService.linkDetected​(l2);
            linkProviderService.linkDetected​(l3);
        }catch(Exception e){
            log.info("******************Something went wrong");
        }
        log.info("Added three link!");
    }

    // 手动添加所有host
    private synchronized void configureHosts(){
        for(int i = 1; i < 10; i++){
            MacAddress mac = MacAddress.valueOf​("00:00:00:00:00:0" + i);
            DefaultHostDescription​ host = new DefaultHostDescription​(mac, VlanId.NONE, new HostLocation​(ConnectPoint.deviceConnectPoint​("device:s" + i + "/1"), System.currentTimeMillis()), 
            IpAddress.valueOf​("2001:" + i + ":1::b"));
            hostProviderService.hostDetected​(HostId.hostId(mac, VlanId.NONE), host, false);
        }

        MacAddress macH10 = MacAddress.valueOf​("00:00:00:00:00:0A");
        MacAddress macH11 = MacAddress.valueOf​("00:00:00:00:00:0B");
        MacAddress macH12 = MacAddress.valueOf​("00:00:00:00:00:0C");
        DefaultHostDescription​ h10 = new DefaultHostDescription​(macH10, VlanId.NONE, new HostLocation​(ConnectPoint.deviceConnectPoint​("device:s10/1"), System.currentTimeMillis()), 
            IpAddress.valueOf​("2001:a:1::b"));
        DefaultHostDescription​ h11 = new DefaultHostDescription​(macH11, VlanId.NONE, new HostLocation​(ConnectPoint.deviceConnectPoint​("device:s11/1"), System.currentTimeMillis()), 
            IpAddress.valueOf​("2001:b:1::b"));
        DefaultHostDescription​ h12 = new DefaultHostDescription​(macH12, VlanId.NONE, new HostLocation​(ConnectPoint.deviceConnectPoint​("device:s12/1"), System.currentTimeMillis()), 
            IpAddress.valueOf​("2001:c:1::b"));
        hostProviderService.hostDetected​(HostId.hostId(macH10, VlanId.NONE), h10, false);
        hostProviderService.hostDetected​(HostId.hostId(macH11, VlanId.NONE), h11, false);
        hostProviderService.hostDetected​(HostId.hostId(macH12, VlanId.NONE), h12, false);
        log.info("Added 12 hosts!");
    } 

    private synchronized void loadListen(){
        // 只执行一次
        for(int i = 0; i < 3000; i++){
            Iterable<Link> links = linkService.getActiveLinks();
            for(Link l : links){
                // Load load = statisticService.load(l.src());
                Load load = portStatisticsService.load(l.src());
                if(load != null){
                    ArrayList<Double> rateList = portRate.getOrDefault(l.src(), new ArrayList<Double>());
                    rateList.add((double) load.rate());
                    portRate.put(l.src(), rateList);
                    // String str = "Load for port is " + load.rate() + " " + load.latest() + " at " + load.time();
                    // str += " for port " + l.src() + "\n";
                    // fout.write(str.getBytes());
                }
            }
            try{
                Thread.sleep(100);
            }catch(InterruptedException ex){
                Thread.currentThread().interrupt();
            }
        }
        writeRate();
    }

    private synchronized void setUpNFForward(){
        log.info("Setting up tunnel forward at initial");

        try{
            List<String> config = Files.lines(Paths.get(confFile)).collect(Collectors.toList());
            for(String line : config)
                nfConfig.put(Integer.valueOf(line.split(" ")[1]), DeviceId.deviceId(line.split(" ")[0]));
            // log.info("Testing validity of nfConfig {}", nfConfig.get(0), nfConfig.get(1), nfConfig.get(2), nfConfig.get(3), nfConfig.get(4), nfConfig.get(5));


            //在SFC节点上下发shift流表
            final String shiftTable = "IngressPipeImpl.shift_tag_table";
            for(int i = 1; i < 7; i++){
                final PiCriterion shiftMatch = PiCriterion.builder()
                        .matchTernary​(PiMatchFieldId.of("hdr.tunnel.id"),
                                    i, 7)
                        .build();
                final PiAction shiftAction = PiAction.builder()
                    .withId(PiActionId.of("IngressPipeImpl.shift"))
                    .build();
                FlowRule shiftRule = Utils.buildFlowRule(
                        nfConfig.get(i), appId, shiftTable, shiftMatch, shiftAction);
                flowRuleService.applyFlowRules(shiftRule);  
            }
            
            // 在每个交换机上安装tunnelforward程序
            for(Device d : deviceService.getAvailableDevices()){
                // 安装到每个NF上的流表
                // NF编号从1到6
                // 匹配2进制最后3位
                final String forwardTable = "IngressPipeImpl.tunnel_forward_table";
                for(int i = 1; i < 7; i++){
                    final PiCriterion forwardMatch = PiCriterion.builder()
                        .matchTernary​(PiMatchFieldId.of("hdr.tunnel.id"),
                                    i, 7)
                        .build();

                    // 出端口是当前设备到NF设备的最短路径的端口
                    if(!d.id().equals(nfConfig.get(i))){
                        Set<DisjointPath> path = pathService.getDisjointPaths(d.id(), nfConfig.get(i));
                        if(path.iterator().hasNext()){
                            final PiAction forwardAction = PiAction.builder()
                                .withId(PiActionId.of("IngressPipeImpl.tunnel_forward"))
                                .withParameter(new PiActionParam(
                                        PiActionParamId.of("port_num"), path.iterator().next().src().port().toLong()))
                                .build();
                            FlowRule forwardRule = Utils.buildFlowRule(
                                    d.id(), appId, forwardTable, forwardMatch, forwardAction);
                            flowRuleService.applyFlowRules(forwardRule);
                            // System.out.println("Installed NF forward rules!");
                        }else{
                            log.info("Notice: {} to NF {} is unreachable!", d.id(), i);
                        }                     
                    }                      
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }


    private String macToIP(String macAdress){
        return new String("2001:" + macAdress.charAt(16) + ":1::b");
    }

    private synchronized void setUpSfc(){
        log.info("Setting up SFC");
        try{
            final String sfcTable = "IngressPipeImpl.sfc_table";
            final String forwardTable = "IngressPipeImpl.ipv6_forward_table";
            // Setting up sfc label on the ingress switch
            // Calculate tags based on request
            List<String> request = Files.lines(Paths.get(reqFile)).collect(Collectors.toList());
            for(int i = 0; i < request.size(); i += 2){
                // each request
                final PiCriterion sfcMatch = PiCriterion.builder()
                        .matchExact(PiMatchFieldId.of("hdr.ipv6.src_addr"),
                                    IpAddress.valueOf(macToIP(request.get(i).split(" ")[0])).toOctets())
                        .matchExact(PiMatchFieldId.of("hdr.ipv6.dst_addr"),
                                    IpAddress.valueOf(macToIP(request.get(i).split(" ")[1])).toOctets())
                        .matchExact(PiMatchFieldId.of("hdr.ipv6.flow_label"),
                                    0)
                        .build();
                final PiCriterion forwardMatch = PiCriterion.builder()
                        .matchExact(PiMatchFieldId.of("hdr.ipv6.src_addr"),
                                    IpAddress.valueOf(macToIP(request.get(i).split(" ")[0])).toOctets())
                        .matchExact(PiMatchFieldId.of("hdr.ipv6.dst_addr"),
                                    IpAddress.valueOf(macToIP(request.get(i).split(" ")[1])).toOctets())
                        .build();

                String[] sfc = request.get(i+1).split(" ");
                int tag = 0;
                for(int j = 0; j < sfc.length; j++){
                    tag += Integer.valueOf(sfc[j])<<(3*j);
                }
                // 在ingress switch处下发sfc流表
                // System.out.println("The tag is :" + tag);
                final PiAction sfcAction = PiAction.builder()
                        .withId(PiActionId.of("IngressPipeImpl.embed_tag"))
                                .withParameter(new PiActionParam(
                                    PiActionParamId.of("id"), tag))
                        .build();
                FlowRule sfcRule = Utils.buildFlowRule(
                        DeviceId.deviceId("device:s" + ABCToNum(request.get(i).split(" ")[0].charAt(16))), appId, sfcTable, sfcMatch, sfcAction);
                flowRuleService.applyFlowRules(sfcRule);
                log.info("Installing tag rules of {}", tag);

                //Setting up ipv6_forward rules
                DeviceId func = nfConfig.get(Integer.valueOf(sfc[sfc.length-1]));
                DeviceId dst = DeviceId.deviceId("device:s" + ABCToNum(request.get(i).split(" ")[1].charAt(16)));
                // 若dst为最后一个NF节点，则不需要安装路由规则
                if(!dst.equals(func)){
                    List<DisjointPath> path = new ArrayList<>(pathService.getDisjointPaths(func, dst));
                    if(!path.isEmpty()){
                        List<Link> links = path.get(0).links();
                        // log.info("Path from func {} to dst {} is {}", func, dst, path.get(0));
                        for(Link l : links){
                            final PiAction forwardAction = PiAction.builder()
                                    .withId(PiActionId.of("IngressPipeImpl.ipv6_forward"))
                                    .withParameter(new PiActionParam(
                                        PiActionParamId.of("port_num"), l.src().port().toLong()))
                                    .build();
                            FlowRule forwardRule = Utils.buildFlowRule(
                                    l.src().deviceId(), appId, forwardTable, forwardMatch, forwardAction);
                            flowRuleService.applyFlowRules(forwardRule);
                        }
                    }else{
                        System.out.println("Can't find path between " + func + " and " + dst);
                    }
                    
                }
                // dst节点转发往host
                final PiAction hostAction = PiAction.builder()
                            .withId(PiActionId.of("IngressPipeImpl.ipv6_forward"))
                            .withParameter(new PiActionParam(
                                    PiActionParamId.of("port_num"), 1))
                            .build();
                FlowRule rule1 = Utils.buildFlowRule(
                    dst, appId, forwardTable, forwardMatch, hostAction);
                flowRuleService.applyFlowRules(rule1);

                log.info("Done for {} times", i/2);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }


    public class InternalTopologyListener implements TopologyListener {
        @Override
        public boolean isRelevant(TopologyEvent event) {
            switch (event.type()) {
                case TOPOLOGY_CHANGED:
                    break;
                default:
                    // Ignore other events.
                    return false;
            }
            return true;
        }

        @Override
        public void event(TopologyEvent event) {
            //当链路数目等于30时
            if(event.subject().linkCount() == LINK_TOPO){
                // log.info("DeviceService finds 12 devices, while topologyService finds {}", topologyService.currentTopology().deviceCount());
                //构造转发图，计算最短路径
                 mainComponent.getExecutorService().execute(() -> {
                    log.info("Starting to configure links, hosts and install rules");

                    setUpNFForward();
                    setUpSfc();
                    if (isHappened.compareAndSet(false, true)) {
                        log.info("Starting to listen");
                        loadListen();
                    }
                });
            }
        }
    }


    public class InternalDeviceListener implements DeviceListener {
        @Override
        public boolean isRelevant(DeviceEvent event) {
            switch (event.type()) {
                case DEVICE_ADDED:
                    break;
                default:
                    // Ignore other events.
                    return false;
            }
            // Process only if this controller instance is the master.
            final DeviceId deviceId = event.subject().id();
            return mastershipService.isLocalMaster(deviceId);
        }

        @Override
        public void event(DeviceEvent event) {
            log.info("DeviceListener: {}", deviceService.getDeviceCount());
            //当交换机数目等于12时
            if(deviceService.getDeviceCount() == HOST_TOPO){
                //构造转发图，计算最短路径
                 // mainComponent.getExecutorService().execute(() -> {
                log.info("DeviceListener: 12 devices");

                configureLinks();
                configureHosts();
                // });
            }
        }

    }

    private static final class InternalLinkProvider implements LinkProvider {
        @Override
        public ProviderId id() {
            return linkProviderID;
        }
    }

    private static final class InternalHostProvider implements HostProvider {
        @Override
        public ProviderId id() {
            return hostProviderID;
        }

        @Override
        public void triggerProbe​(Host host){
            //doing nothing
        }
    }


    private class ReactivePacketProcessor implements PacketProcessor {
        @Override
        public synchronized void process(PacketContext context) {
            long timeStart = System.currentTimeMillis(); 

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            if (isControlPacket(ethPkt) || ethPkt == null) {
                return;
            }

            // Stop processing if the packet has been handled
            // if (context.isHandled()) {
            //     context.treatmentBuilder()
            //             .setOutput(pkt.receivedFrom().port())
            //             .build();
            //     context.send();
            //     log.info("already handled for {}", ethPkt.getSourceMAC());
            //     return;
            // }

            // 提取ip地址
            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();
            byte[] packetArray = pkt.unparsed().array();
            IpAddress srcIP = IpAddress.valueOf(IpAddress.Version.INET6, packetArray, 22); // src IP starts from 22 in packet
            IpAddress dstIP = IpAddress.valueOf(IpAddress.Version.INET6, packetArray, 38); // dst IP starts from 30 in packet

            String combinedStr = srcIP.toString() + " "+ dstIP.toString();
            if(requestPort.containsKey(combinedStr)){
                context.treatmentBuilder()
                        // .setOutput(requestPort.get(combinedStr))
                        .setOutput(pkt.receivedFrom().port())
                        .build();
                context.send();
                log.info("Already handled for {}, sending to port {}", combinedStr, pkt.receivedFrom());
                return;
            }

            /*Try to find the request in requirements file*/
            log.info("Captured a packet from " + srcMac + " to " + dstMac);
            String sfc = matchRequest(srcMac, dstMac);
            if(sfc == null){
                log.info("Can't find request in file for {} to {}", srcIP,dstIP);
                return;
            }

            /*Setup tunnel add on src and sfc nodes*/
            String dstId = "device:s" + ABCToNum(dstMac.toString().charAt(16));
            /*Setup identify and tunnel forward along path*/
            PortNumber port = setUpForward(pkt.receivedFrom().deviceId(), DeviceId.deviceId(dstId), sfc.split(" "), srcIP, dstIP);
            // log.info("Sending to port {}", port);

            /* Adding to handled list 
            *  for concurrent programming
            */
            // handled.add(combinedStr);
            requestPort.put(combinedStr, pkt.receivedFrom().port());

            context.treatmentBuilder()
                        .setOutput(pkt.receivedFrom().port())
                        .build();
            log.info("Send packets back to {}", pkt.receivedFrom());
            context.send();

            long timeFinish = System.currentTimeMillis();
            System.out.println("Operation took " + (timeFinish - timeStart) / 1000.0 + " seconds.");

            // int operationTime = (int) (System.currentTimeMillis()-timeStart);
            
        }


        //打印数据包byte字符串
        public String bytesToHex(byte[] bytes) {  
            StringBuffer sb = new StringBuffer();  
            for(int i = 0; i < bytes.length; i++) {  
                String hex = Integer.toHexString(bytes[i] & 0xFF);  
                if(hex.length() < 2){
                    sb.append(0);  
                }  
                sb.append(hex);  
            }  
            return sb.toString();  
        } 


        //插入转发流表
        public synchronized PortNumber setUpForward(DeviceId src, DeviceId dst, String[] sfc, IpAddress srcIP, IpAddress dstIP){
            final String idTable = "IngressPipeImpl.identify_table";
            final String sfcTable = "IngressPipeImpl.sfc_table";
            final String forwardTable = "IngressPipeImpl.ipv6_forward_table";

            final PiCriterion idMatch = PiCriterion.builder()
                    .matchExact(PiMatchFieldId.of("hdr.ipv6.src_addr"),
                                srcIP.toOctets())
                    .matchExact(PiMatchFieldId.of("hdr.ipv6.dst_addr"),
                                dstIP.toOctets())
                    .build();
            final PiCriterion sfcMatch = PiCriterion.builder()
                    .matchExact(PiMatchFieldId.of("hdr.ipv6.src_addr"),
                                srcIP.toOctets())
                    .matchExact(PiMatchFieldId.of("hdr.ipv6.dst_addr"),
                                dstIP.toOctets())
                    .matchExact(PiMatchFieldId.of("hdr.ipv6.flow_label"),
                                0)
                    .build();
            final PiAction idAction = PiAction.builder()
                    .withId(PiActionId.of("IngressPipeImpl.identify"))
                    .build();
            // 根据sfc请求计算应该下发的tag
            int tag = 0;
            for(int i = 0; i < sfc.length; i++){
                tag += Integer.valueOf(sfc[i])<<(3*i);
            }
            System.out.println("The tag is :" + tag);
            // int tag = Integer.valueOf(sfc[0]) + Integer.valueOf(sfc[1])<<3 + Integer.valueOf(sfc[2])<<3;
            final PiAction sfcAction = PiAction.builder()
                    .withId(PiActionId.of("IngressPipeImpl.embed_tag"))
                            .withParameter(new PiActionParam(
                                PiActionParamId.of("id"), tag))
                    .build();
            // identify rule on edge switch
            FlowRule idRule = Utils.buildFlowRule(
                    src, appId, idTable, idMatch, idAction);
            flowRuleService.applyFlowRules(idRule);
            FlowRule sfcRule = Utils.buildFlowRule(
                    src, appId, sfcTable, sfcMatch, sfcAction);
            flowRuleService.applyFlowRules(sfcRule);


            // 下发ipv6_forward流表到最后一个NF到dst的节点上
            DeviceId func = nfConfig.get(Integer.valueOf(sfc[sfc.length-1]));

            // 若dst为最后一个NF节点，则不需要安装路由规则
            if(!dst.equals(func)){
                List<DisjointPath> path = new ArrayList<>(pathService.getDisjointPaths(func, dst));
                List<Link> links = path.get(0).links();
                // log.info("Path from func {} to dst {} is {}", func, dst, path.get(0));
                for(Link l : links){
                    final PiAction forwardAction = PiAction.builder()
                            .withId(PiActionId.of("IngressPipeImpl.ipv6_forward"))
                            .withParameter(new PiActionParam(
                                PiActionParamId.of("port_num"), l.src().port().toLong()))
                            .build();
                    FlowRule forwardRule = Utils.buildFlowRule(
                            l.src().deviceId(), appId, forwardTable, idMatch, forwardAction);
                    flowRuleService.applyFlowRules(forwardRule);
                }
            }
            // dst节点转发往host
            final PiAction hostAction = PiAction.builder()
                        .withId(PiActionId.of("IngressPipeImpl.ipv6_forward"))
                        .withParameter(new PiActionParam(
                                PiActionParamId.of("port_num"), 1))
                        .build();
            FlowRule rule1 = Utils.buildFlowRule(
                dst, appId, forwardTable, idMatch, hostAction);
            flowRuleService.applyFlowRules(rule1);

            //返回该路径的起点port
            return pathService.getDisjointPaths(src, nfConfig.get(Integer.valueOf(sfc[0]))).iterator().next().src().port();
        }

        /*
        *  Return: the sfc list, null if there no match
        */
        public String matchRequest(MacAddress src, MacAddress dst){
            try{
                List<String> lineLists = Files.lines(Paths.get(reqFile)).collect(Collectors.toList());
                for(int i = 0; i < lineLists.size(); i=i+2){
                    String[] nodes = lineLists.get(i).split(" ");
                    if(nodes[0].contains(src.toString()) && nodes[1].contains(dst.toString()))
                        return lineLists.get(i+1);
                }
            } catch (IOException e) {
                System.out.println("File not found!");
                e.printStackTrace();
            }
            return null;
        }

        /**
         * Returns the MAC address configured in the "myStationMac" property of the
         * given device config.
         *
         * @param deviceId the device ID
         * @return MyStation MAC address
         */
        private MacAddress getMyStationMac(DeviceId deviceId) {
            return getDeviceConfig(deviceId)
                    .map(FabricDeviceConfig::myStationMac)
                    .orElseThrow(() -> new ItemNotFoundException(
                            "Missing myStationMac config for " + deviceId));
        }
        /**
         * Returns the fabric config object for the given device.
         *
         * @param deviceId the device ID
         * @return fabric device config
         */
        private Optional<FabricDeviceConfig> getDeviceConfig(DeviceId deviceId) {
            FabricDeviceConfig config = networkConfigService.getConfig(
                    deviceId, FabricDeviceConfig.class);
            return Optional.ofNullable(config);
        }

        // Indicates whether this is a control packet, e.g. LLDP, BDDP
        private boolean isControlPacket(Ethernet eth) {
            short type = eth.getEtherType();
            return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
        }
    }

}