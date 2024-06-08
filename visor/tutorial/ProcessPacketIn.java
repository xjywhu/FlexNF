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
import org.onosproject.net.PortNumber;
import org.onosproject.net.topology.PathService;
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
import org.onosproject.net.topology.*;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Link.Type;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.provider.*;
import org.onlab.packet.VlanId;
import java.util.concurrent.atomic.AtomicBoolean;
import org.onosproject.net.statistic.*;

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
    private FileOutputStream fout;
    public static final int HOST_TOPO = 12;
    public static final int LINK_TOPO = 30;
    public static final String reqFile = "/root/onos/apache-karaf-4.2.6/config/requirements.txt";
    public static final String confFile = "/root/onos/apache-karaf-4.2.6/config/config.txt";
    // private static List<String> handled = new ArrayList<>();
    private final DeviceListener deviceListener = new InternalDeviceListener();
    private final TopologyListener topologyListener = new InternalTopologyListener();
    private LinkProvider linkProvider = new MyLinkProvider();
    private HostProvider hostProvider = new InternalHostProvider();
    private LinkProviderService linkProviderService;
    private HostProviderService hostProviderService;
    private static final ProviderId linkProviderID = new ProviderId("links", "my.app");
    private static final ProviderId hostProviderID = new ProviderId("hosts", "test.app");
    private static volatile HashMap<String, PortNumber> requestPort = new HashMap<String, PortNumber>();
    // private static volatile HashMap<DeviceId, ArrayList<String>> nfConfig = new HashMap<DeviceId, ArrayList<String>>();
    private static volatile HashMap<String, DeviceId> nfConfig = new HashMap<String, DeviceId>();
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
    protected StatisticService statisticService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private PathService pathService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PortStatisticsService portStatisticsService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LinkProviderRegistry linkProviderRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostProviderRegistry hostProviderRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LinkService linkService;
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private MainComponent mainComponent;

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
        try{
            linkProviderService = linkProviderRegistry.register(linkProvider);
            hostProviderService = hostProviderRegistry.register(hostProvider);
            log.info("******************Registered well done");
        }catch(Exception e){
            log.info("******************Something went wrong");
        }
        
        log.info("Started");

        try{
            fout=new FileOutputStream("/root/onos/apache-karaf-4.2.6/config/packets.txt");
        }catch(IOException e){
            e.printStackTrace();
        }
        // Schedule set up for all devices.
        // mainComponent.scheduleTask(this::setUpPacketHandle, INITIAL_SETUP_DELAY);
    }

    @Deactivate
    protected void deactivate() {
        deviceService.removeListener(deviceListener);
        topologyService.removeListener(topologyListener);
        linkProviderRegistry.unregister(linkProvider);
        hostProviderRegistry.unregister(hostProvider);

        log.info("Stopped");
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
        log.info("Added three links!");
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
            System.out.println("result.txt written successfully");
        }catch(Exception e){
            e.printStackTrace();
        }
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

    public synchronized void setUpRules(){
        // 读请求文件，传递参数：DeviceId src, DeviceId dst, String[] sfc, IpAddress srcIP, IpAddress dstIP给函数
        log.info("Setting up rules");
        try{
            List<String> config = Files.lines(Paths.get(confFile)).collect(Collectors.toList());
            for(int i = 0; i < config.size(); i += 2){
                String[] sfcs = config.get(i+1).split(";");
                for(String sfc : sfcs)
                    nfConfig.put(sfc, DeviceId.deviceId(config.get(i)));
                // nfConfig.put(config.get(i+1), DeviceId.deviceId(config.get(i)));
            }

            List<String> request = Files.lines(Paths.get(reqFile)).collect(Collectors.toList());
            for(int i = 0; i < request.size(); i += 2){
                // each request
                String sfc = request.get(i+1);
                String srcMac = request.get(i).split(" ")[0];
                String dstMac = request.get(i).split(" ")[1];

                // find function node
                DeviceId func = nfConfig.get(sfc);

                DeviceId src = DeviceId.deviceId("device:s" + ABCToNum(srcMac.charAt(16)));
                DeviceId dst = DeviceId.deviceId("device:s" + ABCToNum(dstMac.charAt(16)));
                IpAddress srcIP = IpAddress.valueOf(new String("2001:" + srcMac.charAt(16) + ":1::b"));
                IpAddress dstIP = IpAddress.valueOf(new String("2001:" + dstMac.charAt(16) + ":1::b"));
                setUpForward(src, dst, func, srcIP, dstIP);
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
                    log.info("Starting to install rules");

                    setUpRules();
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

    private static final class MyLinkProvider implements LinkProvider {
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


    //p4visor环境下获取src到dst的最短路径，注意筛选环路
    public List<Link> getPath(DeviceId src, DeviceId dst, DeviceId func){
        //测试能否获取端口数据
        // List<PortStatistics> tmp = deviceService.getPortStatistics(DeviceId.deviceId("device:s12"));
        // for(PortStatistics p : tmp){
        //     log.info("port s12/{} received {} packets", p.portNumber(), p.packetsReceived());
        // }


        if(src.equals(func) || dst.equals(func)){
            List<DisjointPath> list = new ArrayList<>(pathService.getDisjointPaths(src, dst));
            if(list.isEmpty())
                return null;
            else
                return list.get(0).links();
        }

        List<Path> frontSet = pathService.getKShortestPaths​(src, func).collect(Collectors.toList());
        List<Path> backSet = pathService.getKShortestPaths​(func, dst).collect(Collectors.toList());

        List<Link> path = new ArrayList<Link>();
        //筛选前段路径不包含终点，同时建立路径节点set，用于判断是否存在环路
        for(Path p : frontSet){
            if(p.links().stream().noneMatch(linkFront -> linkFront.dst().deviceId().equals(dst))){
                final Set<DeviceId> nodes = p.links().stream().map(linkNode -> linkNode.src().deviceId()).collect(Collectors.toSet());
                path.addAll(p.links());

                //找到一条没有环路的路
                for(Path pb : backSet){
                    if(pb.links().stream().noneMatch(l -> nodes.contains(l.dst().deviceId()))){
                        path.addAll(pb.links());
                        // log.info("final path {}", path);
                        return path;
                    }
                }
            }
        }
        return path;
    }


    //插入转发流表
    // public synchronized PortNumber setUpForward(DeviceId src, DeviceId dst, DeviceId func, IpAddress srcIP, IpAddress dstIP){
    public synchronized void setUpForward(DeviceId src, DeviceId dst, DeviceId func, IpAddress srcIP, IpAddress dstIP){
        List<Link> links = getPath(src, dst, func);
        if(links != null){
            final String idTable = "IngressPipeImpl.identify_table";
            final String forwardTable = "IngressPipeImpl.forward_table";
            final PiCriterion idMatch = PiCriterion.builder()
                    .matchExact(PiMatchFieldId.of("hdr.ipv6.src_addr"),
                                srcIP.toOctets())
                    .matchExact(PiMatchFieldId.of("hdr.ipv6.dst_addr"),
                                dstIP.toOctets())
                    .build();
            final PiAction idAction = PiAction.builder()
                    .withId(PiActionId.of("IngressPipeImpl.identify"))
                    .build();

            // 下发id和forward流表
            for(int i = 0; i < links.size(); i++){
                // 功能节点下发的id表参数与普通节点不同
                final PiAction forwardAction = PiAction.builder()
                        .withId(PiActionId.of("IngressPipeImpl.ipv6_forward"))
                        .withParameter(new PiActionParam(
                                PiActionParamId.of("port_num"), links.get(i).src().port().toLong()))
                        .build();

                FlowRule idRule = Utils.buildFlowRule(
                        links.get(i).src().deviceId(), appId, idTable, idMatch, idAction);
                FlowRule forwardRule = Utils.buildFlowRule(
                        links.get(i).src().deviceId(), appId, forwardTable, idMatch, forwardAction);
                flowRuleService.applyFlowRules(idRule);
                flowRuleService.applyFlowRules(forwardRule);
            }

            // dst节点转发往host
            final PiAction hostAction = PiAction.builder()
                        .withId(PiActionId.of("IngressPipeImpl.ipv6_forward"))
                        .withParameter(new PiActionParam(
                                PiActionParamId.of("port_num"), 1))
                        .build();
            FlowRule rule1 = Utils.buildFlowRule(
                    dst, appId, idTable, idMatch, idAction);
            FlowRule rule2 = Utils.buildFlowRule(
                dst, appId, forwardTable, idMatch, hostAction);
            flowRuleService.applyFlowRules(rule1);
            flowRuleService.applyFlowRules(rule2);
        }
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

}