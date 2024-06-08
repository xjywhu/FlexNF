import xml.etree.ElementTree as ET
import math
import re


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
            nodes.add(str(int(node_index_value)+1))
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
            edges.add((str(int(src_id)+1), str(int(dst_id)+1), latency))

        return edges, nodes, id_node_name_dict



if __name__ == "__main__":
    tg = TopologyGenerator()
    edges_old, nodes_old, id_node_name_dict = tg.get_topology_info(topology_file="Chinanet.graphml")
    edges = []
    nodes = []
    for item in edges_old:
        edges.append((int(item[0]), int(item[1])))

    for item in nodes_old:
        nodes.append(int(item))
    print(edges)
    print(nodes)

    # print(res)
    # tg.generate_topology("Tinet.graphml", "Tinet.py")
    # tg.generate_topology("Topo/Amres.graphml", "Topo/Amres.py")
    # tg.generate_topology("Topo/Chinanet.graphml", "Topo/Chinanet.py")
    # tg.generate_topology("Topo/UsCarrier.graphml", "Topo/UsCarrier.py")
    # STP=False
    # tg.generate_topology("Topo/Sanren.graphml", "Topo/Sanren.py")
