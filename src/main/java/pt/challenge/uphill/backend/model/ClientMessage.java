package pt.challenge.uphill.backend.model;
import java.util.*;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

public class ClientMessage {
    private UUID sessionId;
    private String name;
    private PrintWriter out;
    private Instant createdAt;
    private Set<String> nodesList = new HashSet<>();
    private Map<String, Set<Edge>> edgesListFromNode = new HashMap<>();

    public ClientMessage(UUID sessionId, PrintWriter out, Instant createdAt) {
        this.sessionId = sessionId;
        this.out = out;
        this.createdAt = createdAt;
    }

    public UUID getSessionId() {
        return sessionId;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public long getDurationConnection() {
        return Duration.between(this.getCreatedAt(), Instant.now()).toMillis();
    }
    public void responseToClient(String line) {
        out.println(line);
    }
    public Set<String> getNodesList() { return this.nodesList; }
    public Map<String, Set<Edge>> getEdgesListFromNode() {
        return edgesListFromNode;
    }

    public int getShortestPathNoLock(String from, String to) {
        if (!this.getNodesList().contains(from) || !this.getNodesList().contains(to))
            return -1;
        if (from.equals(to))
            return 0;
        return ((Integer)this.calculateWeightsInGraph(from, to, (node, weightedNodes) -> !node.equals(to)).get(to)).intValue();
    }

    private Map<String, Integer> calculateWeightsInGraph(String from, String to, CalculationStopCheck check) {
        Map<String, Integer> weightedNodes = new HashMap<>();
        Set<String> unvisited = new HashSet<>(this.getNodesList());
        for (String node : unvisited)
            weightedNodes.put(node, Integer.valueOf(2147483647));
        weightedNodes.put(from, Integer.valueOf(0));
        String nodeToVisit = from;
        this.visitNode(nodeToVisit, unvisited, weightedNodes);
        do {
            nodeToVisit = this.findNextNodeToVisit(unvisited, weightedNodes);
            if (nodeToVisit == null)
                break;
            this.visitNode(nodeToVisit, unvisited, weightedNodes);
        } while (check.shouldContinue(nodeToVisit, weightedNodes));
        return weightedNodes;
    }

    private void visitNode(String currentNode, Set<String> unvisited, Map<String, Integer> weightedNodes) {
        Set<String> neighbours = new HashSet<>();
        int currentNodeWeight = ((Integer)weightedNodes.get(currentNode)).intValue();
        Set<Edge> outgoingEdges = this.getEdgesListFromNode().get(currentNode);
        if (outgoingEdges != null)
            for (Edge e : outgoingEdges) {
                String targetNode = e.getTo();
                if (unvisited.contains(targetNode)) {
                    neighbours.add(targetNode);
                    int potentialNewWeight = currentNodeWeight + e.getWeight();
                    if (potentialNewWeight < ((Integer)weightedNodes.get(targetNode)).intValue())
                        weightedNodes.put(targetNode, Integer.valueOf(potentialNewWeight));
                }
            }
        unvisited.remove(currentNode);
    }
    private String findNextNodeToVisit(Set<String> unvisited, Map<String, Integer> weightedNodes) {
        String smallest = null;
        int smallestWeight = Integer.MAX_VALUE;
        for (String toCheck : unvisited) {
            if (((Integer)weightedNodes.get(toCheck)).intValue() < smallestWeight) {
                smallestWeight = ((Integer)weightedNodes.get(toCheck)).intValue();
                smallest = toCheck;
            }
        }
        return smallest;
    }
    public List<String> findNodesCloserThan(int weight, String from) {
        if (this.getNodesList().contains(from)) {
            Map<String, Integer> weightedGraph = calculateWeightsInGraph(from, UUID.randomUUID().toString(), (node, weightedNodes) ->
                    weightedNodes.get(node) < weight);
            return weightedGraph.entrySet().stream().filter(entry ->
                            entry.getValue() < weight).filter(entry ->
                            !(entry.getKey()).equals(from))
                    .map(Map.Entry::getKey)
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
        }else{
            return new ArrayList<String>();
        }
    }

    static interface CalculationStopCheck {
        boolean shouldContinue(String param1String, Map<String, Integer> param1Map);
    }




}
