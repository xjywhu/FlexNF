package org.onosproject.ngsdn.tutorial;

import java.util.*;
import org.onosproject.net.Link;

public class Request {
    public String src;
    public String dst;
//    private HashMap<Integer, List<Integer>> NF;
    public int[] nfs;
    public List<List<String>> logicalPaths; //包含安装NF的交换机的id
    public List<List<Link>> pathLinks; //包含源到目的路径的链路上所有交换机id
    public List<Double> rtimes; // 备选路径集构建好后，计算每条路径的重提交次数
    public List<Double> distance;
    public int min_idx;

    public Request(String src, String dst, int[] NF){
        this.src = src;
        this.dst = dst;
        this.nfs = NF;
        this.rtimes = new ArrayList<Double>();
        this.distance = new ArrayList<Double>();
        this.logicalPaths = new ArrayList<List<String>>();
        this.pathLinks = new ArrayList<List<Link>>();
        this.min_idx = -1;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("src: " + src + " dst: " + dst + "\n");
        sb.append("sfc requirements: " + nfs.toString() + "\n");
        sb.append("logic switches:\n");
        for(List<String> p: logicalPaths){
            for(String tmp: p){
                sb.append(tmp + " ");
            }
            sb.append("\n");
        }

        sb.append("resub times: \n");
        for(Double i: rtimes){
            sb.append(i + "\n");
        }

        sb.append("link paths:\n");
        for(int i = 0; i < pathLinks.size(); i++){
            List<Link> p = pathLinks.get(i);
            sb.append(distance.get(i).toString() + ": ");
            for(Link tmp: p){
                sb.append(tmp.src().toString() + " ");
            }
            sb.append(p.get(p.size()-1).dst().toString() + "\n");
        }
        sb.append("\n");

        sb.append("optimal path at " + min_idx + "\n");
        return sb.toString();
    }
}