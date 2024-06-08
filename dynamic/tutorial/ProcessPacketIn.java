package org.onosproject.ngsdn.tutorial;

import org.onosproject.ngsdn.tutorial.graph.ksp.Yen;
import org.onosproject.ngsdn.tutorial.graph.util.LogicalPath;
import org.onosproject.ngsdn.tutorial.graph.*;
import org.onosproject.mastership.MastershipService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onosproject.core.ApplicationId;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.onosproject.net.topology.*;
import org.onosproject.net.host.*;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onlab.graph.Weight;
import org.onosproject.net.DeviceId;
import org.onosproject.net.HostId;
import org.onosproject.net.ElementId;
import org.onosproject.net.DisjointPath;
import org.onosproject.net.link.*;
import org.onlab.packet.MacAddress;
import org.onlab.packet.Ip6Address;
import org.onosproject.net.statistic.*;
import org.onosproject.net.PortNumber;
import org.onosproject.net.topology.PathService;
import org.onosproject.net.provider.*;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Link.Type;
import org.onosproject.net.device.*;
import org.onlab.packet.VlanId;
import org.onlab.packet.IpAddress;
import org.onosproject.net.HostLocation;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.Host;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.onosproject.net.statistic.*;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiActionProfileGroupId;
import org.onosproject.net.pi.runtime.PiTableAction;
import org.onosproject.ngsdn.tutorial.common.Utils;
import org.onosproject.net.pi.model.PiRegisterId;
import org.onosproject.net.pi.runtime.PiRegisterCell;
import org.onosproject.net.pi.runtime.PiRegisterCellId;
import org.onosproject.net.pi.runtime.data.PiBitString;
import java.time.*;

import static org.onosproject.ngsdn.tutorial.AppConstants.INITIAL_SETUP_DELAY;

@Component(
        immediate = true,
        enabled = true
)
public class ProcessPacketIn {
    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final int NFALL = 6;
    public static final int LINK_TOPO = 30;
    public static final int HOST_TOPO = 12;
    public static final int switchALL = 6;
    protected double wa = 1;
    protected double wb = 0;
    private ApplicationId appId;

    public static final String reqFile = "/root/onos/apache-karaf-4.2.6/config/requirements.txt";
    public static final String configFile = "/root/onos/apache-karaf-4.2.6/config/config.txt";

    public static List<Request> requestList = new ArrayList<Request>();
    public static HashMap<String, int[]> nfInstall = new HashMap<String, int[]>(); //交换机编号作为index，存储交换机上nf列表
    public static HashMap<Integer, List<String>> switchOf = new HashMap<Integer, List<String>>(); // nf编号作为index，存储安装了相应nf的交换机列表

    // private final HostListener hostListener = new InternalHostListener();
    private final TopologyListener topologyListener = new InternalTopologyListener();
    private final DeviceListener deviceListener = new InternalDeviceListener();
    private LinkProvider linkProvider = new InternalLinkProvider();
    private HostProvider hostProvider = new InternalHostProvider();
    private LinkProviderService linkProviderService;
    private HostProviderService hostProviderService;
    private static final ProviderId linkProviderID = new ProviderId("link", "my.app");
    private static final ProviderId hostProviderID = new ProviderId("host", "test.app");
    private static AtomicBoolean isHappened = new AtomicBoolean(false);
    private static HashMap<ConnectPoint, ArrayList<Double>> portRate = new HashMap<ConnectPoint, ArrayList<Double>>();

    //--------------------------------------------------------------------------
    // ONOS CORE SERVICE BINDING
    //
    // These variables are set by the Karaf runtime environment before calling
    // the activate() method.
    //--------------------------------------------------------------------------
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private PathService pathService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

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


    //--------------------------------------------------------------------------
    // COMPONENT ACTIVATION.
    //
    // When loading/unloading the app the Karaf runtime environment will call
    // activate()/deactivate().
    //--------------------------------------------------------------------------
    @Activate
    protected void activate() {
        appId = mainComponent.getAppId();
        topologyService.addListener(topologyListener);
        deviceService.addListener(deviceListener);
        linkProviderService = linkProviderRegistry.register(linkProvider);
        hostProviderService = hostProviderRegistry.register(hostProvider);
        // hostService.addListener(hostListener);

        // Schedule set up of existing devices. Needed when reloading the app.
        log.info("Started");
        mainComponent.scheduleTask(this::setUpRequest, INITIAL_SETUP_DELAY);

    }

    @Deactivate
    protected void deactivate() {
        // hostService.removeListener(hostListener);
        linkProviderRegistry.unregister(linkProvider);
        hostProviderRegistry.unregister(hostProvider);
        deviceService.removeListener(deviceListener);
        topologyService.removeListener(topologyListener);

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

                configureHosts();
                configureLinks();
                // });
            }
        }
    }

    public int matchRequest(IpAddress src, IpAddress dst){
        int num = hostService.getHostCount();

        for(int i = 0; i < requestList.size(); i++){
            Request r = requestList.get(i);
            Host hs = hostService.getHost(HostId.hostId(r.src));
            Host hd = hostService.getHost(HostId.hostId(r.dst));
            if(!hs.ipAddresses().iterator().hasNext() || !hd.ipAddresses().iterator().hasNext())
                return -1;
            if(src.equals(hs.ipAddresses().iterator().next()) && dst.equals(hd.ipAddresses().iterator().next())){
                return i;
            }
        }
        return -1;
    }

    public synchronized void setUpRules(){
        // 读请求文件，传递参数：List<Link> p, IpAddress dstIP, IpAddress srcIP给函数
        log.info("Setting up rules");
        try{
            FileOutputStream record_file=new FileOutputStream("/root/onos/apache-karaf-4.2.6/config/time_record.txt");
            List<String> config = Files.lines(Paths.get(configFile)).collect(Collectors.toList());
            List<String> request = Files.lines(Paths.get(reqFile)).collect(Collectors.toList());
            for(int i = 0; i < request.size(); i += 2){
                // each request
                String sfc = request.get(i+1);
                String srcMac = request.get(i).split(" ")[0];
                String dstMac = request.get(i).split(" ")[1];
                IpAddress srcIP = IpAddress.valueOf(new String("2001:" + srcMac.charAt(16) + ":1::b"));
                IpAddress dstIP = IpAddress.valueOf(new String("2001:" + dstMac.charAt(16) + ":1::b"));

                Request r = requestList.get(matchRequest(srcIP, dstIP));
                long startTime = Instant.now().getNano();
                r.min_idx = calMetrics(r, record_file);

                long endTime = Instant.now().getNano();
                record_file.write((String.valueOf(endTime-startTime) + ", ").getBytes());
                // log.info("final path {} for {} to {}", r.pathLinks.get(r.min_idx), srcIP, dstIP);
                
                setUpLinkForward(r.pathLinks.get(r.min_idx), srcIP, dstIP);
                log.info("Done for {} times", i/2);
            }
            record_file.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void setUpLinkForward(List<Link> p, IpAddress srcIP, IpAddress dstIP){
        // log.info("Shortest path for {} to {} is {}", srcIP, dstIP, p);
        final String labelTableId = "IngressPipeImpl.add_label_table";
        final String forwardTableId = "IngressPipeImpl.forward_table";
        final PiCriterion matchLabel = PiCriterion.builder()
                .matchExact(PiMatchFieldId.of("hdr.ipv6.src_addr"),
                            srcIP.toOctets())
                .matchExact(PiMatchFieldId.of("hdr.ipv6.dst_addr"),
                            dstIP.toOctets())
                .build();
        final PiAction labelAction = PiAction.builder()
            .withId(PiActionId.of("IngressPipeImpl.get_label"))
            .withParameter(new PiActionParam(
                    PiActionParamId.of("label"), 1))
            .build();

        // 在转发路径的所有节点上安装identify和forward的规则
        for(Link l: p){
            if(l.src().toString().charAt(0) != 'd'){ // link of host, pass
                continue;
            }
            // build forward table rules for device l.src
            // match: srcIP, dstIP; action: port
            FlowRule labelRule = Utils.buildFlowRule(
                    l.src().deviceId(), appId, labelTableId, matchLabel, labelAction);
            flowRuleService.applyFlowRules(labelRule);

            final PiAction forwardAction = PiAction.builder()
                    .withId(PiActionId.of("IngressPipeImpl.set_egress_port"))
                    .withParameter(new PiActionParam(
                            PiActionParamId.of("port"),
                            l.src().port().toLong()))
                    .build();
            FlowRule forwardRule = Utils.buildFlowRule(
                    l.src().deviceId(), appId, forwardTableId, matchLabel, forwardAction);
            flowRuleService.applyFlowRules(forwardRule);
        }
        // adding rules on dst device
        final PiAction dstAction = PiAction.builder()
                .withId(PiActionId.of("IngressPipeImpl.set_egress_port"))
                .withParameter(new PiActionParam(
                        PiActionParamId.of("port"),
                        1))
                .build();
        FlowRule dstRule = Utils.buildFlowRule(p.get(p.size()-1).src().deviceId(), 
            appId, forwardTableId, matchLabel, dstAction);
        flowRuleService.applyFlowRules(dstRule);
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


    // 增加时间记录
    /*Function: calculate metrics for each linkpath of r, and stores in metrics[]*/
    public int calMetrics(Request r, FileOutputStream record_file){
        double min = 65520;
        int min_idx = 0;
        for(int i = 0; i < r.logicalPaths.size(); i++){
            double value = wa * r.distance.get(i).doubleValue() + wb * r.rtimes.get(i).doubleValue();
            if(value < min){
                min = value;
                min_idx = i;
            }
        }
        return min_idx;
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
            log.info("{} links now", event.subject().linkCount());
            //当链路数目等于30时
            if(event.subject().linkCount() == LINK_TOPO){
                //构造转发图，计算最短路径
                 mainComponent.getExecutorService().execute(() -> {
                    log.info("Starting to install rules");
                    if (isHappened.compareAndSet(false, true)) {
                        setUpAlgorithm();
                        setUpRules();
                        
                        log.info("Starting to listen");
                        loadListen();
                    }
                });
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

    private void readRequest(String fileName){
        try {
            //按行读取文件
            List<String> lineLists = Files.lines(Paths.get(fileName)).collect(Collectors.toList());
            //构造类数组
            for(int i = 0; i < lineLists.size(); i=i+2){
                String[] flow = (lineLists.get(i)).split(" ");
                //分离NF需求
                String[] SFC_s = (lineLists.get(i+1)).split(" ");
                int[] SFC = new int[SFC_s.length];
                if(SFC_s[0] == "-1")
                    SFC = null;
                else{
                    for (int k = 0; k < SFC_s.length; k++) {
                        SFC[k] = Integer.parseInt(SFC_s[k]);
                    }
                }
                Request tmp = new Request(flow[0], flow[1], SFC);
                // 构造请求
                requestList.add(tmp);
            }
        } catch (IOException e) {
            System.out.println("File not found!");
            e.printStackTrace();
        }
    }

    private void readConfig(String fileName){
        try {
            //按行读取文件，构造nfInstall和switchOf
            List<String> lineLists = Files.lines(Paths.get(fileName)).collect(Collectors.toList());
            //nfInstall存储每个交换机安装的nf列表
            for(int i = 0; i < lineLists.size(); i=i+2){
                String switchId = lineLists.get(i);
                //分离NF列表
                String[] SFC_s = (lineLists.get(i+1)).split(" ");
                int[] nfList = new int[SFC_s.length];
                for (int k = 0; k < SFC_s.length; k++) {
                    int nfId = Integer.parseInt(SFC_s[k]);
                    nfList[k] = nfId;
                    List<String> oldSwitch = switchOf.get(nfId);
                    oldSwitch.add(switchId);
                    switchOf.put(nfId, oldSwitch);
                }
                nfInstall.put(switchId, nfList);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ElementId getId(String name){
        if(name.startsWith("d")){
            // device
            return DeviceId.deviceId(name);
        }
        else{
            //host
            return HostId.hostId(name);
        }
    }

    private void calDistance(ArrayList<String> prevList, Graph graph, String cur){
        for(String pre: prevList){
            ElementId preId = getId(pre.split("_")[0]);
            ElementId curId = getId(cur.split("_")[0]);
            
            if(preId.equals(curId))
                graph.addEdge(pre, cur, 0.0);
            else{
                Set<Path> paths = pathService.getPaths(preId, curId);

                Iterator<Path> it = paths.iterator();
                if(it.hasNext()){
                    Path p = it.next();

                    graph.addEdge(pre, cur, p.cost());
                    // log.info("********* path cost from {} to {}: {}", preId, curId, p.cost());
                }
            }
        }
    }


/*
* 根据请求，首先选择源点、目的点，确定阶段数，然后生成中间点（设备编号+NF阶段），确定每个点之间的距离
* 调用KSP，返回k条路径的cost和路径编号（转发图中的编号）
* 根据结果存进Request类中pathSet（转发图编号与设备id的转换）
* */
    private String[] constructForwardGraph(Graph graph, Request request){
        String[] srcDst = new String[2];
        srcDst[0] = request.src+ "_0";
        ArrayList<String> prevList = new ArrayList<String>();
        ArrayList<String> newList = new ArrayList<String>();
        prevList.add(srcDst[0]);
        graph.addNode(srcDst[0]);
        // 对于每个阶段，添加转发图中的节点，并且添加边
        for(int i = 0; i < request.nfs.length+1; i++){
            if(i == request.nfs.length){
                // 添加目的节点
                srcDst[1] = request.dst+ "_" + (request.nfs.length+1);
                calDistance(prevList, graph, srcDst[1]);
            }
            else{
                //获取安装了该nf的设备id列表
                List<String> deviceList = switchOf.get(request.nfs[i]);
                for(String device: deviceList){
                    //修改list中每个点的名称存入图中
                    String cur = "" + device + "_" + (i+1);
                    graph.addNode(cur);
                    newList.add(cur);
                    calDistance(prevList, graph, cur);
                }

                prevList = (ArrayList<String>) newList.clone();
                newList.clear();
            }
        }
        return srcDst;
    }

    private void setUpAlgorithm(){
        try{
            // FileOutputStream fout=new FileOutputStream("/root/onos/apache-karaf-4.2.6/config/test.txt");
            FileOutputStream hyper_record=new FileOutputStream("/root/onos/apache-karaf-4.2.6/config/hyper_record.txt");
            
            for(Request request: requestList) {
                long timeStart = Instant.now().getNano();

                if(request.nfs[0] != -1){
                    //构造转发图
                    Graph graph = new Graph();
                    String[] srcDst = constructForwardGraph(graph, request);
                    // System.out.println(srcDst[0] + " " + srcDst[1]);
                    // System.out.println("*******graph:");
                    // System.out.println(graph);

                    // //运行算法
                    List<LogicalPath> ksp;
                    Yen yenAlgorithm = new Yen();
                    // 调用算法，参数graph, source, target, k
                    ksp = yenAlgorithm.ksp(graph, srcDst[0], srcDst[1], 5);
                    long timeEnd = Instant.now().getNano();
                    hyper_record.write((String.valueOf(timeEnd-timeStart) + ", ").getBytes());

                    /* Output the K shortest paths */
                    System.out.println("k) cost: [path]");
                    double max = 0;
                    //为每一条备选路径，添加distance、logicalPaths和PathLink
                    for (LogicalPath p : ksp) {
                        // 计算路径距离
                        if(p.getTotalCost() > max)
                            max = p.getTotalCost();
                        request.distance.add(p.getTotalCost());

                        List<String> logicP = new ArrayList<String>();
                        List<Link> linkP = new ArrayList<Link>();
                        for(int i = 0; i < p.size(); i++){
                            Edge e = p.getEdges().get(i);
                            if(i != 0){
                                logicP.add(e.getFromNode());
                            }

                            ElementId srcId = getId(e.getFromNode().split("_")[0]);
                            ElementId dstId = getId(e.getToNode().split("_")[0]);
                            if(!srcId.equals(dstId)){
                                Iterator<Path> it = pathService.getPaths(srcId, dstId).iterator();
                                if(it.hasNext()){
                                    linkP.addAll(it.next().links());
                                }
                                // else{
                                //     log.info("path empty! srcId {}, dstId {}", srcId, dstId);
                                // }
                            }
                        }
                        request.logicalPaths.add(logicP);
                        request.pathLinks.add(linkP);
                    }
                    //归一化请求的距离
                    if(max!=0){
                        for(int i = 0; i < request.distance.size(); i++){
                            Double tmp = request.distance.get(i);
                            tmp = tmp/max;
                            request.distance.set(i, tmp);
                        }
                    }
                    calResubtimes(request);
                }else{
                    List<Link> linkP = new ArrayList<Link>();
                    Iterator<Path> it = pathService.getPaths(HostId.hostId(request.src), HostId.hostId(request.dst)).iterator();
                    if(it.hasNext()){
                        Path p = it.next();
                        linkP.addAll(p.links());
                        request.distance.add(p.cost());
                    }
                    request.pathLinks.add(linkP);
                }
                // fout.write(request.toString().getBytes());
            }
            hyper_record.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    private void calResubtimes(Request request){
        double max = 0;
        for(List<String> p: request.logicalPaths){
            double count = 0;
            for(int i = 1; i < p.size(); i++){
                String curDeviceId = p.get(i).split("_")[0];
                String preDeviceId = p.get(i-1).split("_")[0];
                if(curDeviceId.equals(preDeviceId)){
                    if(isReversed(curDeviceId, request.nfs[i-1], request.nfs[i]))
                        count += 1;
                }
                
            }
            if(count > max) max = count;
            request.rtimes.add(count);
            log.info("resub times for this path is {} ", count);
        }
        if(max!=0){
            for(int i = 0; i < request.rtimes.size(); i++){
                Double tmp = request.rtimes.get(i);
                tmp = tmp/max;
                request.rtimes.set(i, tmp);
            }
        }
    }

    public Boolean isReversed(String device, int preNfId, int curNfId){
        // int数组转Integer list
        List<Integer> nfList = Arrays.stream(nfInstall.get(device)).boxed().collect(Collectors.toList());
        int preIdx = nfList.indexOf(preNfId);
        int curIdx = nfList.indexOf(curNfId);
        if(preIdx == -1 || curIdx == -1){
            log.info("Error! Can't find nf {} and {} both in switch {}", preNfId, curNfId, device);
            return true;
        }

        if(preIdx >= curIdx)
            return true;
        else
            return false;
    }

    private synchronized void setUpRequest(){
        //初始化switchOf
        for(int i = 1; i < 7; i++){
            switchOf.put(i, new ArrayList<String>());
        }
        // nfInstall.put("device:s1", null);
        // System.out.println("Putting something into nfInstall " + nfInstall.get("device:s1"));

        readRequest(reqFile);
        readConfig(configFile);
        System.out.println("**** Config and request already read!");
    }
}