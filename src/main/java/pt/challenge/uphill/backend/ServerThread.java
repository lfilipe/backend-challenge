package pt.challenge.uphill.backend;

import org.apache.log4j.Logger;
import pt.challenge.uphill.backend.model.Edge;
import pt.challenge.uphill.backend.model.ClientMessage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ServerThread extends Thread implements Runnable  {
    private Socket clientSocket;
    private static final int TIMEOUT = 30000; // 30 seconds
    private HashMap<UUID, ClientMessage> commandMap = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final Logger logger = Logger.getLogger(ServerThread.class);
    public static final String ERROR_NODE_NOT_FOUND = "ERROR: NODE NOT FOUND";
    public ServerThread(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    public void run() {
        //synchronized (this) {
            try {
                clientSocket.setSoTimeout(TIMEOUT); // Set the timeout period

                UUID sessionID = UUID.randomUUID();

                try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                     PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                    ClientMessage clientMessage = new ClientMessage(sessionID, out, Instant.now() );

                    commandMap.put(sessionID, clientMessage);

                    out.println("HI, I AM " + sessionID);
                    logger.trace("Returned: HI, I AM " + sessionID);

                    String receivedMessage;
                    do {
                        receivedMessage = in.readLine();
                        logger.trace("Received: " + receivedMessage);

                        Pattern patternHi = Pattern.compile("HI, I AM (.*)");
                        Matcher matchHi = patternHi.matcher(receivedMessage);

                        Pattern patternAddNodeX = Pattern.compile("ADD NODE ([\\w-]+)");//ADD NODE Phase2-Node-0 until ADD NODE Phase3-Node-999
                        Matcher matchAddNodeX = patternAddNodeX.matcher(receivedMessage);

                        Pattern patternAddEdgeXYW = Pattern.compile("ADD EDGE ([\\w-]+) ([\\w-]+) (\\d+)");//ADD EDGE Phase3-Node-264 Phase3-Node-470 1
                        Matcher matchAddEdgeXYW = patternAddEdgeXYW.matcher(receivedMessage);

                        Pattern patternRemoveNodeX = Pattern.compile("REMOVE NODE ([\\w-]+)");//REMOVE NODE Phase2-Node-75
                        Matcher matchRemoveNodeX = patternRemoveNodeX.matcher(receivedMessage);

                        Pattern patternRemoveEdgeXYW = Pattern.compile("REMOVE EDGE ([\\w-]+) ([\\w-]+)");//REMOVE EDGE Phase2-Node-0 Phase2-Node-68
                        Matcher matchRemoveEdgeXYW = patternRemoveEdgeXYW.matcher(receivedMessage);

                        Pattern patternShortestPath = Pattern.compile("SHORTEST PATH ([\\w-]+) ([\\w-]+)");//SHORTEST PATH Phase3-Node-971 Phase3-Node-6
                        Matcher matchShortestPath = patternShortestPath.matcher(receivedMessage);

                        Pattern patternCloserThan = Pattern.compile("CLOSER THAN (\\d+) ([\\w-]+)");//CLOSER THAN 17 Phase4-Node-256
                        Matcher matchCloserThan = patternCloserThan.matcher(receivedMessage);

                        Set<String> nodesCliente = clientMessage.getNodesList();

                        if (matchHi.find()) {
                            String name_client = matchHi.group(1);
                            clientMessage.setName(name_client);
                            clientMessage.responseToClient("HI " + name_client);
                            logger.trace("Returned: HI " + name_client);
                        } else if (receivedMessage.startsWith("BYE")) {
                            clientMessage.responseToClient("BYE " + clientMessage.getName() + ", WE SPOKE FOR " + clientMessage.getDurationConnection() + " MS");
                            logger.trace("Returned: BYE " + clientMessage.getName() + ", WE SPOKE FOR " + clientMessage.getDurationConnection() + " MS");
                            commandMap.remove(clientMessage.getSessionId());
                        } else if (matchAddNodeX.matches()) {
                            String nodeName = matchAddNodeX.group(1);
                            this.lock.writeLock().lock();

                            try {
                                if (nodesCliente.contains(nodeName)) {
                                    clientMessage.responseToClient("ERROR: NODE ALREADY EXISTS");
                                    logger.trace("Returned: ERROR: NODE ALREADY EXISTS");
                                } else {
                                    nodesCliente.add(nodeName);
                                    clientMessage.responseToClient("NODE ADDED");
                                    logger.trace("Returned: NODE ADDED");
                                }
                            } finally {
                                this.lock.writeLock().unlock();
                            }
                        } else if (matchRemoveNodeX.matches()) {
                            String nodeName = matchRemoveNodeX.group(1);
                            this.lock.writeLock().lock();

                            try {
                                if (nodesCliente.contains(nodeName)) {
                                    boolean removed = nodesCliente.remove(nodeName);
                                    if (removed) {
                                        Map<String, Set<Edge>> edges = clientMessage.getEdgesListFromNode();
                                        edges.remove(nodeName);
                                        clientMessage.responseToClient("NODE REMOVED");
                                        logger.trace("Returned: NODE REMOVED");
                                    }
                                } else {
                                    clientMessage.responseToClient(this.ERROR_NODE_NOT_FOUND);
                                    logger.trace("Returned: "+this.ERROR_NODE_NOT_FOUND);
                                }
                            } finally {
                                this.lock.writeLock().unlock();
                            }
                        } else if (matchAddEdgeXYW.matches()) {
                            String sourceNodeName = matchAddEdgeXYW.group(1);
                            String destinationNodeName = matchAddEdgeXYW.group(2);
                            int weight = Integer.parseInt(matchAddEdgeXYW.group(3));

                            this.lock.writeLock().lock();

                            try {

                                if (!nodesCliente.contains(sourceNodeName) || !nodesCliente.contains(destinationNodeName)) {
                                    clientMessage.responseToClient(this.ERROR_NODE_NOT_FOUND);
                                    logger.trace("Returned: "+this.ERROR_NODE_NOT_FOUND);
                                } else {
                                    Edge edge = new Edge(sourceNodeName, destinationNodeName, weight);

                                    Map<String, Set<Edge>> edges = clientMessage.getEdgesListFromNode();

                                    Set<Edge> toEdges = edges.computeIfAbsent(sourceNodeName, k -> new HashSet());
                                    toEdges.add(edge);
                                    clientMessage.responseToClient("EDGE ADDED");
                                    logger.trace("Returned: EDGE ADDED");
                                }
                            } finally {
                                this.lock.writeLock().unlock();
                            }
                        } else if (matchRemoveEdgeXYW.matches()) {
                            String from = matchRemoveEdgeXYW.group(1);
                            String to = matchRemoveEdgeXYW.group(2);

                            this.lock.writeLock().lock();

                            try {
                                if (!nodesCliente.contains(from) || !nodesCliente.contains(to)) {
                                    clientMessage.responseToClient(this.ERROR_NODE_NOT_FOUND);
                                    logger.trace("Returned: "+this.ERROR_NODE_NOT_FOUND);
                                } else {
                                    Set<Edge> fromEdges = clientMessage.getEdgesListFromNode().get(from);
                                    if (fromEdges != null) {
                                        Set<Edge> toRemove = new HashSet<>();

                                        for (Edge edge : fromEdges) {
                                            if (edge.getTo().equals(to)) {
                                                toRemove.add(edge);
                                            }
                                        }
                                        fromEdges.removeAll(toRemove);
                                        clientMessage.responseToClient("EDGE REMOVED");
                                        logger.trace("Returned: EDGE REMOVED");
                                    }
                                }
                            } finally {
                                lock.writeLock().unlock();
                            }
                        } else if (matchShortestPath.matches()) {

                            String from = matchShortestPath.group(1);
                            String to = matchShortestPath.group(2);

                            this.lock.writeLock().lock();
                            try {

                                int shortestpath = clientMessage.getShortestPathNoLock(from, to);

                                if (shortestpath < 0) {
                                    clientMessage.responseToClient(this.ERROR_NODE_NOT_FOUND);
                                    logger.trace("Returned: "+this.ERROR_NODE_NOT_FOUND+" (" + String.valueOf(shortestpath) + ")");
                                } else {
                                    clientMessage.responseToClient(String.valueOf(shortestpath));
                                    logger.trace("Returned: " + String.valueOf(shortestpath));
                                }
                            } finally {
                                lock.writeLock().unlock();
                            }
                        } else if (matchCloserThan.matches()) {

                            int weight = Integer.parseInt(matchCloserThan.group(1));
                            String from = matchCloserThan.group(2);

                            this.lock.writeLock().lock();
                            try {
                                if (!nodesCliente.contains(from)) {
                                    clientMessage.responseToClient(this.ERROR_NODE_NOT_FOUND);
                                    logger.trace("Returned: "+this.ERROR_NODE_NOT_FOUND);
                                } else {
                                    List<String> nodesCloserThan = clientMessage.findNodesCloserThan(weight, from);

                                    String result = nodesCloserThan.stream().collect(Collectors.joining(","));

                                    clientMessage.responseToClient(result);
                                    logger.trace("Returned: " + result);
                                }
                            } finally {
                                lock.writeLock().unlock();
                            }
                        } else {
                            clientMessage.responseToClient("SORRY, I DID NOT UNDERSTAND THAT");
                            logger.trace("Returned: SORRY, I DID NOT UNDERSTAND THAT");
                        }

                    }while (!receivedMessage.equals("BYE MATE!"));

                } catch (SocketTimeoutException e) {
                    logger.error("Connection timed out due to inactivity.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Generic error: "+e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                    logger.warn("Client socket is closed");
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error("Error closing socket: "+e.getMessage());
                }
            }
        //}
    }

}

