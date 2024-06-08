import time
import xml.etree.ElementTree as ET
import math
import re
import networkx as nx
import numpy as np
import matplotlib.pyplot as plt
import random
from sko.PSO import PSO
from sko.GA import GA
from collections import Counter


# def init_topology():


node_number = 12
nf_number = 7
node_list = [i for i in range(1, node_number + 1)]
nf_list = [i for i in range(1, nf_number + 1)]
adj = [(1, 2), (1, 3), (2, 3), (2, 4), (3, 5), (4, 6), (5, 6), (5, 7), (6, 8),
       (7, 8), (7, 9), (9, 10), (10, 11), (11, 8), (12, 8)]
G = nx.Graph()
G.add_edges_from(adj)


class TopologyGenerator:
    def __init__(self):
        pass

    def get_topology_info(self, topology_file):
        input_file_name = topology_file
        bandwidth_argument = ''

        # READ FILE AND DO ALL THE ACTUAL PARSING IN THE NEXT PARTS
        xml_tree = ET.parse(input_file_name)
        namespace = "{http://graphml.graphdrawing.org/xmlns}"
        ns = namespace  # just doing shortcutting, namespace is needed often.

        # GET ALL ELEMENTS THAT ARE PARENTS OF ELEMENTS NEEDED LATER ON
        root_element = xml_tree.getroot()
        graph_element = root_element.find(ns + 'graph')

        # GET ALL ELEMENT SETS NEEDED LATER ON
        index_values_set = root_element.findall(ns + 'key')
        node_set = graph_element.findall(ns + 'node')
        edge_set = graph_element.findall(ns + 'edge')

        # SET SOME VARIABLES TO SAVE FOUND DATA FIRST
        # memomorize the values' ids to search for in current topology
        node_label_name_in_graphml = ''
        node_latitude_name_in_graphml = ''
        node_longitude_name_in_graphml = ''
        # for saving the current values
        node_index_value = ''
        node_name_value = ''
        node_longitude_value = ''
        node_latitude_value = ''
        # id:value dictionaries
        id_node_name_dict = {}  # to hold all 'id: node_name_value' pairs
        id_longitude_dict = {}  # to hold all 'id: node_longitude_value' pairs
        id_latitude_dict = {}  # to hold all 'id: node_latitude_value' pairs

        # FIND OUT WHAT KEYS ARE TO BE USED, SINCE THIS DIFFERS IN DIFFERENT GRAPHML TOPOLOGIES
        for i in index_values_set:

            if i.attrib['attr.name'] == 'label' and i.attrib['for'] == 'node':
                node_label_name_in_graphml = i.attrib['id']
            if i.attrib['attr.name'] == 'Longitude':
                node_longitude_name_in_graphml = i.attrib['id']
            if i.attrib['attr.name'] == 'Latitude':
                node_latitude_name_in_graphml = i.attrib['id']

        nodes = set()
        # NOW PARSE ELEMENT SETS TO GET THE DATA FOR THE TOPO
        # GET NODE_NAME DATA
        # GET LONGITUDE DATK
        # GET LATITUDE DATA
        for n in node_set:
            node_index_value = n.attrib['id']
            nodes.add(str(int(node_index_value) + 1))
            # get all data elements residing under all node elements
            data_set = n.findall(ns + 'data')

            # finally get all needed values
            for d in data_set:

                # node name
                if d.attrib['key'] == node_label_name_in_graphml:
                    # strip all whitespace from names so they can be used as id's
                    node_name_value = re.sub(r'\s+', '', d.text)
                # longitude data
                if d.attrib['key'] == node_longitude_name_in_graphml:
                    node_longitude_value = d.text
                # latitude data
                if d.attrib['key'] == node_latitude_name_in_graphml:
                    node_latitude_value = d.text

                # save id:data couple
                id_node_name_dict[node_index_value] = node_name_value
                id_longitude_dict[node_index_value] = node_longitude_value
                id_latitude_dict[node_index_value] = node_latitude_value

        edges = set()
        for e in edge_set:

            # GET IDS FOR EASIER HANDLING
            src_id = e.attrib['source']
            dst_id = e.attrib['target']

            first_product = math.sin(float(id_latitude_dict[dst_id])) * math.sin(float(id_latitude_dict[src_id]))
            second_product_first_part = math.cos(float(id_latitude_dict[dst_id])) * math.cos(
                float(id_latitude_dict[src_id]))
            second_product_second_part = math.cos(
                (float(id_longitude_dict[dst_id])) - (float(id_longitude_dict[src_id])))

            distance = math.radians(
                math.acos(first_product + (second_product_first_part * second_product_second_part))) * 6378.137

            # t (in ms) = ( distance in km * 1000 (for meters) ) / ( speed of light / 1000 (for ms))
            # t         = ( distance       * 1000              ) / ( 1.97 * 10**8   / 1000         )
            latency = (distance * 1000) / (197000)

            # BANDWIDTH LIMITING
            # set bw to 10mbit if nothing was specified otherwise on startup
            if bandwidth_argument == '':
                bandwidth_argument = '10'
            edges.add((str(int(src_id) + 1), str(int(dst_id) + 1), latency))

        return edges, nodes, id_node_name_dict


def init_topology(name):
    tg = TopologyGenerator()
    edges_old, nodes_old, id_node_name_dict = tg.get_topology_info(topology_file=name)
    edges = []
    nodes = []

    for item in edges_old:
        edges.append((int(item[0]), int(item[1])))

    for item in nodes_old:
        nodes.append(int(item))

    node_list = [i for i in range(1, len(nodes) + 1)]
    G = nx.Graph()
    G.add_edges_from(edges)
    return G, node_list


class Request:
    def __init__(self, id, src, dst, service_chain):
        self.id = id
        self.src = src
        self.dst = dst
        self.service_chain = service_chain
        self.short_path = None

    def show(self):
        print("src:{}, dst:{}, service_chain:{}".format(self.src, self.dst, str(self.service_chain)))

    def __eq__(self, other):
        flag = True
        if self.src == other.src and self.dst == other.dst:
            if len(self.service_chain) == len(other.service_chain):
                for i in range(len(self.service_chain)):
                    if self.service_chain[i] != other.service_chain[i]:
                        flag = False
            else:
                flag = False
        else:
            flag = False

        return flag

    def __hash__(self):
        res = ""
        res += str(self.src)
        for nf in self.service_chain:
            res += "0{}".format(nf)
        res += "0{}".format(self.dst)
        return int(res)

    def get_service_path(self, nf_nodes, G):
        req = self
        weights = []
        for i in range(len(req.service_chain) + 1):
            if i == 0:
                last_nodes = [req.src]
            else:
                last_nodes = nf_nodes[req.service_chain[i - 1] - 1]

            if i == len(req.service_chain):
                cur_nodes = [req.dst]
            else:
                nf = req.service_chain[i]
                cur_nodes = nf_nodes[nf - 1]

            for cur in cur_nodes:
                for last in last_nodes:
                    paths = nx.shortest_path(G, source=cur, target=last, weight=None, method="dijkstra")
                    # weights.append((cur, last, len(paths)-1))
                    weights.append(("{}_{}".format(cur, i + 1), ("{}_{}".format(last, i)), len(paths) - 1))

        req_G = nx.Graph()
        req_G.add_weighted_edges_from(weights)
        # self.show()
        try:
            return nx.shortest_path_length(req_G, source="{}_{}".format(req.src, 0), weight="weight",
                                           target="{}_{}".format(req.dst, len(req.service_chain) + 1),
                                           method="dijkstra")
        except:
            print("hello")
            # return 1000000


def generate_request(req_number=10):
    requests = []

    for i in range(req_number):
        pairs = random.sample(node_list, 2)
        src = pairs[0]
        dst = pairs[1]
        # service_length = random.sample(nf_list, 1)[0]
        service_length = random.sample(nf_list[5:7], 1)[0]
        service_chain = random.sample(nf_list, service_length)

        requests.append(Request(id=i, src=src, dst=dst, service_chain=service_chain))

    for re in requests:
        re.show()

    return requests
    # print(node_list)
    # print(nf_list)
    # print(random.sample(nf_list, 3))


def short_path():
    res = 0
    for req in requests:
        res += nx.shortest_path_length(G, source=req.src, target=req.dst, method="dijkstra")
    return res


def max_degree(node_number=5):
    begin = time.time()
    degrees = G.degree
    degrees = sorted(degrees, key=lambda x: x[1], reverse=True)
    selected_nodes = []
    for i in range(node_number):
        selected_nodes.append(degrees[i][0])
    # print(selected_nodes)
    # return None
    # [(8, 4), (2, 3), (3, 3), (5, 3), (6, 3), (7, 3), (1, 2), (4, 2), (9, 2), (10, 2), (11, 2), (12, 1)]
    service_chains = []
    for req in requests:
        service_chains.extend(req.service_chain)

    nf_pop = Counter(service_chains).most_common(nf_number)
    # print(nf_pop[0])
    # print(nf_pop)

    #
    nf_nodes = []
    for i in range(nf_number):
        nf_nodes.append(selected_nodes)
    end = time.time()
    res = 0
    for req in requests:
        res += req.get_service_path(nf_nodes, G)

    return res, end - begin


def obj_func(x):
    res = cons(x)
    for r in res:
        if r[0] != 0:
            return 100000000

    x_new = np.array(x[0:nf_number * node_number]).reshape((nf_number, node_number))
    nf_nodes = []
    for tmp in x_new:
        nf_nodes.append(np.where(tmp == 1)[0] + 1)

    res = 0
    print(x_new)
    for req in requests:
        res += req.get_service_path(nf_nodes=nf_nodes, G=G)
    return res


def cons(x):
    place_number = nf_number * node_number
    place_matrix = np.array(x[0:place_number]).reshape(nf_number, node_number)
    constraint_eq = []

    for i in range(len(place_matrix)):
        constraint_eq.append([sum(place_matrix[i]) - x[place_number + i]])

    place_matrix = place_matrix.T

    for i in range(len(place_matrix)):
        constraint_eq.append([sum(place_matrix[i]) - x[place_number + nf_number + i]])

    return constraint_eq


def pso_test():
    # 迭代次数
    # max_iter = 50
    # max_iter = 2
    max_iter = 200
    place_number = nf_number * node_number
    val_number = place_number + nf_number + node_number
    max_nf = 6

    '''
    每个网络功能最多部署node_number个
    每个交换机上最多部署max_nf个网络功能
    '''
    cons1 = cons
    pso = GA(func=obj_func, size_pop=500,
             n_dim=val_number,
             max_iter=max_iter,
             lb=[0] * place_number + [1] * nf_number + [0] * node_number,
             ub=[1] * place_number + [node_number] * nf_number + [max_nf] * node_number, precision=[1] * val_number,
             constraint_eq=[cons1])

    pso.to("cuda:0")

    pso.record_mode = True
    gbest_x, gbest_y = pso.run()
    print('best_x is ', np.array(gbest_x[0:place_number]).reshape(nf_number, node_number),
          gbest_x[place_number:place_number + nf_number],
          gbest_x[place_number + nf_number:-1],
          'best_y is', gbest_y)
    print("max degree:", max_degree(node_number=5))
    print("optimal:", short_path())


# greedy 策略, 计算出每个节点覆盖最短路的数目，选择最多的节点
def greedy(node_number=5):
    dict_node = {}
    tuple_node = []
    nf_nodes = []
    for i in range(nf_number):
        nf_nodes.append([])
    # print(nf_nodes)

    for req in requests:
        paths = nx.shortest_path(G, source=req.src, target=req.dst)
        req.short_path = paths
        for p in paths:
            if p in dict_node.keys():
                dict_node[p].append(req)
            else:
                dict_node[p] = [req]

    for key in dict_node.keys():
        tuple_node.append((key, len(dict_node[key])))

    tuple_node = sorted(tuple_node, key=lambda x: x[1], reverse=True)
    # print(tuple_node)
    for i in range(node_number):
        node = tuple_node[i][0]
        # print(node)
        dict_nf = {}
        for req in dict_node[node]:
            for nf in req.service_chain:
                if nf in dict_nf.keys():
                    dict_nf[nf] += 1
                else:
                    dict_nf[nf] = 1

        tuple_nf = []
        for key in dict_nf.keys():
            tuple_nf.append((key, dict_nf[key]))

        tuple_nf = sorted(tuple_nf, key=lambda x: x[1], reverse=True)
        # print(tuple_nf)
        for j in range(7):
            nf = tuple_nf[j][0]
            nf_nodes[nf - 1].append(node)

        # print(nf_nodes)

    res = 0
    for req in requests:
        res += req.get_service_path(nf_nodes, G)
    return res


# greedy 策略, 计算出每个节点覆盖最短路的数目，选择最多的节点
def greedy_new(node_number=5):
    dict_node = {}
    tuple_nf = []
    nf_nodes = []
    for i in range(nf_number):
        nf_nodes.append([])
    # print(nf_nodes)

    nf_pop = {}

    for req in requests:
        paths = nx.shortest_path(G, source=req.src, target=req.dst)
        req.short_path = paths
        for p in paths:
            if p in dict_node.keys():
                dict_node[p].append(req)
            else:
                dict_node[p] = [req]
        for nf in req.service_chain:
            if nf in nf_pop:
                nf_pop[nf].append(req)
            else:
                nf_pop[nf] = [req]

    for key in nf_pop.keys():
        tuple_nf.append((key, len(nf_pop[key])))

    tuple_nf = sorted(tuple_nf, key=lambda x: x[1], reverse=True)
    # print(tuple_nf)

    begin = time.time()
    for (i, item) in enumerate(tuple_nf):
        nf = item[0]
        # reqs = nf_pop[nf]
        reqs = set(nf_pop[nf])
        node_dict = {}
        for req in reqs:
            path = nx.shortest_path(G, source=req.src, target=req.dst)
            for p in path:
                if p in node_dict:
                    node_dict[p].add(req)
                else:
                    node_dict[p] = {req}

        selected = []
        # while len(selected) < 2:
        while len(selected) < node_number:
        # while len(reqs) != 0:
            tmp = []
            for key in node_dict:
                # print(node_dict[key].intersection(reqs))
                tmp.append((key, len(node_dict[key].intersection(reqs))))
            tmp = sorted(tmp, key=lambda x: x[1], reverse=True)
            node = tmp[0][0]
            selected.append(node)
            reqs = reqs.difference(node_dict[node])
        # node_pop = Counter(paths).most_common()
        # print(selected)
        nf_nodes[nf-1] = selected
    # print(nf_nodes)
        # break
    end = time.time()

    #
    res = 0
    for (i, req) in enumerate(requests):
        t1 = time.time()
        res += req.get_service_path(nf_nodes, G)
        t2 = time.time()
        # print(i, t2-t1)
    return res, end - begin


if __name__ == "__main__":
    # # nx.draw(G, with_labels=True)
    # # plt.show()
    # print(G.adj)
    # test()

    # main()
    # pso_test()
    # requests = generate_request(req_number=10000)

    # G, node_list = init_topology(name="topo/Chinanet.graphml")
    # G, node_list = init_topology(name="topo/Abilene.graphml")
    # G, node_list = init_topology(name="topo/TopologyZoo/Geant2012.graphml")
    nf_number = 7
    nf_list = [i for i in range(1, nf_number + 1)]
    node_number = len(node_list)

    t1 = time.time()
    requests = generate_request(req_number=10000)

    # print(max_degree(node_number=1))
    # print(greedy_new(node_number=1))

    # for i in range(1, 7):
    print(node_list)
    for i in range(1, len(node_list)+1):
        print(i)
        t1 = time.time()
        print(max_degree(node_number=i))
        t2 = time.time()
        # print(greedy(node_number=i))
        print(greedy_new(node_number=i))
        t3 = time.time()
        # print("max degree:{}, greedy:{}".format(t2-t1, t3-t2))
        print("------------------------")
    print(short_path())

    # print(greedy_new(node_number=10))

    # pso_test()
    # t2 = time.time()
    # print(t2 - t1)
    # max_degree()
    # print("max degree:", max_degree(node_number=3))
    # print("optimal:", short_path())
