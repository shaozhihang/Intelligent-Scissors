package src;

import java.util.*;

public class Dijkstra {
    public static List<Node> findShortestPath(Map<Node, List<Edge>> graph, Node start, Node end) {
        // 初始化距离
        for (Node node : graph.keySet()) {
            node.distance = Double.POSITIVE_INFINITY;
        }
        start.distance = 0;

        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingDouble(n -> n.distance));
        queue.add(start);

        Map<Node, Node> predecessors = new HashMap<>();

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (current.equals(end)) break;

            for (Edge edge : current.edges) {
                Node neighbor = edge.target;
                double newDist = current.distance + edge.weight;
                if (newDist < neighbor.distance) {
                    neighbor.distance = newDist;
                    predecessors.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        // 回溯路径
        List<Node> path = new ArrayList<>();
        Node step = end;
        while (predecessors.containsKey(step)) {
            path.add(step);
            step = predecessors.get(step);
        }
        Collections.reverse(path);
        return path;
    }
}