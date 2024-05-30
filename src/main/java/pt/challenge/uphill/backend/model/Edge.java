package pt.challenge.uphill.backend.model;

public class Edge {
    private final String from;
    private final String to;
    private final int weight;

    public Edge(String from, String to, int weight) {
        this.from = from;
        this.to = to;
        this.weight = weight;
    }

    public String getFrom() {
        return this.from;
    }

    public String getTo() {
        return this.to;
    }

    public int getWeight() {
        return this.weight;
    }
}
