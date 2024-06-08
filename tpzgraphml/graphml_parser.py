# -*- coding: utf-8 -*-

from __future__ import unicode_literals
from __future__ import division
from __future__ import absolute_import
from __future__ import print_function


from xml.dom import minidom

# from . import Graph
# from . import Node
# from . import Edge
from collections import defaultdict

class Graph:
    """
    Main class which represent a Graph

    :param name: name of the graph
    """

    def __init__(self, name=""):
        """
        """

        self.name = name

        self._nodes = []
        self._edges = []
        self._root = None
        self.directed = True

        self.i = 0

    def DFS_prefix(self, root=None):
        """
        Depth-first search.

        .. seealso::
           `Wikipedia DFS descritpion <http://en.wikipedia.org/wiki/Depth-first_search>`_

        :param root: first to start the search
        :return: list of nodes
        """

        if not root:
            root = self._root

        return self._DFS_prefix(root)

    def _DFS_prefix(self, n, parent=None):
        """
        """

        nodes = [n]
        n['depth'] = self.i

        for c in n.children():
            nodes += self._DFS_prefix(c, n)
            self.i += 1

        return nodes

    def BFS(self, root=None):
        """
        Breadth-first search.

        .. seealso::
           `Wikipedia BFS descritpion <http://en.wikipedia.org/wiki/Breadth-first_search>`_

        :param root: first to start the search
        :return: list of nodes


        """

        if not root:
            root = self.root()

        queue = deque()
        queue.append(root)

        nodes = []

        while len(queue) > 0:
            x = queue.popleft()
            nodes.append(x)

            for child in x.children():
                queue.append(child)

        return nodes

    def get_depth(self, node):
        """
        """

        depth = 0
        while node.parent() and node != self.root():
            node = node.parent()[0]
            depth += 1

        return depth

    def nodes(self, ):
        """
        """

        return self._nodes

    def edges(self, ):
        """
        """

        return self._edges

    def children(self, node):
        """
        """

        return node.children()

    def add_node(self, label=""):
        """
        """

        n = Node()
        n['label'] = label
        self._nodes.append(n)

        return n

    def add_edge(self, n1, n2, directed=False):
        """
        """

        if n1 not in self._nodes:
            raise Test("fff")
        if n2 not in self._nodes:
            raise Test("fff")

        e = Edge(n1, n2, directed)
        self._edges.append(e)

        return e

    def add_edge_by_label(self, label1, label2):
        """
        """

        n1 = None
        n2 = None

        for n in self._nodes:
            if n['label'] == label1:
                n1 = n
            if n['label'] == label2:
                n2 = n

        if n1 and n2:
            return self.add_edge(n1, n2)
        else:
            return

    def set_root(self, node):
        """
        """

        self._root = node

    def root(self):
        """
        """

        return self._root

    def set_root_by_attribute(self, value, attribute='label'):
        """
        """

        for n in self.nodes():
            if n[attribute] in value:
                self.set_root(n)
                return n

    def get_attributs(self):
        """
        """

        attr = []
        attr_obj = []
        for n in self.nodes():
            for a in n.attr:
                if a not in attr:
                    attr.append(a)
                    attr_obj.append(n.attr[a])

        for e in self.edges():
            for a in e.attr:
                if a not in attr:
                    attr.append(a)
                    attr_obj.append(e.attr[a])

        return attr_obj


    def show(self, show_label=False):
        """
        """

        import matplotlib

        import matplotlib.pyplot as plt
        import networkx as nx

        G = nx.Graph()

        for n in self._nodes:
            if show_label:
                n_label = n['label']
            else:
                n_label = n.id
            G.add_node(n_label)

        for e in self._edges:
            if show_label:
                n1_label = e.node1['label']
                n2_label = e.node2['label']
            else:
                n1_label = e.node1.id
                n2_label = e.node2.id
            G.add_edge(n1_label, n2_label)

        nx.draw(G)

        if show_label:
            nx.draw_networkx_labels(G, pos=nx.spring_layout(G))

        plt.show()


class GraphMLParser:
    """
    """

    def __init__(self):
        """
        """

    def write(self, graph, fname):
        """
        """

        doc = minidom.Document()

        root = doc.createElement('graphml')
        doc.appendChild(root)

        # Add attributs
        for a in graph.get_attributs():
            attr_node = doc.createElement('key')
            attr_node.setAttribute('id', a.name)
            attr_node.setAttribute('attr.name', a.name)
            attr_node.setAttribute('attr.type', a.type)
            root.appendChild(attr_node)

        graph_node = doc.createElement('graph')
        graph_node.setAttribute('id', graph.name)
        if graph.directed:
            graph_node.setAttribute('edgedefault', 'directed')
        else:
            graph_node.setAttribute('edgedefault', 'undirected')
        root.appendChild(graph_node)

        # Add nodes
        for n in graph.nodes():

            node = doc.createElement('node')
            node.setAttribute('id', n['label'])
            for a in n.attributes():
                if a != 'label':
                    data = doc.createElement('data')
                    data.setAttribute('key', a)
                    data.appendChild(doc.createTextNode(str(n[a])))
                    node.appendChild(data)
            graph_node.appendChild(node)

        # Add edges
        for e in graph.edges():

            edge = doc.createElement('edge')
            edge.setAttribute('source', e.node1['label'])
            edge.setAttribute('target', e.node2['label'])
            if e.directed() != graph.directed:
                edge.setAttribute('directed', 'true' if e.directed() else 'false')
            for a in e.attributes():
                if e != 'label':
                    data = doc.createElement('data')
                    data.setAttribute('key', a)
                    data.appendChild(doc.createTextNode(e[a]))
                    edge.appendChild(data)
            graph_node.appendChild(edge)

        f = open(fname, 'w')
        f.write(doc.toprettyxml(indent = '    '))

    def parse(self, fname):
        """
        """

        dom = minidom.parse(open(fname, 'r'))
        root = dom.getElementsByTagName("graphml")[0]
        graph = root.getElementsByTagName("graph")[0]
        name = graph.getAttribute('id')

        g = Graph(name)

        # Get latitude and longitude
        for r in root.getElementsByTagName("key"):
            if r.getAttribute("attr.name") == 'Longitude':
                Longitude = r.getAttribute("id")
                
            elif r.getAttribute("attr.name") == 'Latitude':
                Latitude = r.getAttribute("id")
        
        # Get nodes and their coordinates
        nodel = defaultdict(list)
        for node in graph.getElementsByTagName("node"):
            n = g.add_node(node.getAttribute('id'))
            node_ = node.getAttribute('id')

            for attr in node.getElementsByTagName("data"):
                if attr.firstChild:
                    n[attr.getAttribute("key")] = attr.firstChild.data
                else:
                    n[attr.getAttribute("key")] = ""
                
            Lat = n[Latitude]
            Lon = n[Longitude]
            if Lat != None or Lon != None:
                nodel[int(node_)] = (float(Lat), float(Lon))
            else:
                del n

        # Get edges
        for edge in graph.getElementsByTagName("edge"):
            source = edge.getAttribute('source')
            dest = edge.getAttribute('target')
            if int(source) not in nodel or int(dest) not in nodel:
                continue
            
            e = g.add_edge_by_label(source, dest)

            for attr in edge.getElementsByTagName("data"):
                if attr.firstChild:
                    e[attr.getAttribute("key")] = attr.firstChild.data
                else:
                    e[attr.getAttribute("key")] = ""
        
        return g, nodel


if __name__ == '__main__':

    parser = GraphMLParser()
    # g = parser.parse('test.graphml')
    g = parser.parse('Chinanet.graphml')
    g.show(True)
    
