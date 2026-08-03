package org.zzc.bpcsolver.core.graph;

import lombok.Getter;
import org.zzc.bpcsolver.core.template.GraphTemplate;
import org.zzc.bpcsolver.problem.entity.Customer;
import org.zzc.bpcsolver.problem.entity.Depot;
import org.zzc.bpcsolver.problem.entity.DynamicInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class Graph extends GraphTemplate<Vertex, Arc> {

    private int index;
    private int startDepotId;
    private DynamicInstance instance;
    private List<Arc>[] incomingArcs;
    private Map<Integer, Vertex> verticesById;

    public Graph() {
        super();
        this.incomingArcs = new List[0];
        this.verticesById = new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    public Graph(Graph other) {
        super(other);
        this.index = other.index;
        this.startDepotId = other.startDepotId;
        this.instance = other.instance == null ? null : other.instance.copy();
        this.verticesById = new HashMap<>();
        for (Vertex vertex : this.vertices) {
            if (vertex != null) {
                this.verticesById.put(vertex.getId(), vertex);
            }
        }
        this.incomingArcs = new List[other.incomingArcs.length];
        for (int i = 0; i < other.incomingArcs.length; i++) {
            this.incomingArcs[i] = new ArrayList<>();
            for (Arc arc : other.incomingArcs[i]) {
                this.incomingArcs[i].add(arc == null ? null : arc.copy());
            }
        }
    }

    @Override
    public Graph copy() {
        return new Graph(this);
    }

    @SuppressWarnings("unchecked")
    public void buildFromInstance(DynamicInstance instance, int startDepotId) {
        if (instance == null) {
            throw new IllegalArgumentException("instance must not be null.");
        }
        Depot startDepot = instance.getStartDepotById(startDepotId);
        if (startDepot == null) {
            throw new IllegalArgumentException("Unknown startDepotId: " + startDepotId);
        }

        this.id = startDepotId;
        this.type = "DVRP";
        this.index = 0;
        this.startDepotId = startDepotId;
        this.instance = instance;
        this.verticesById = new HashMap<>();
        this.numVertices = instance.getRemainingCustomerIds().size() + 2;
        this.vertices = new ArrayList<>(Collections.nCopies(numVertices, null));
        this.outgoingArcs = new List[numVertices];
        this.incomingArcs = new List[numVertices];
        for (int i = 0; i < numVertices; i++) {
            this.outgoingArcs[i] = new ArrayList<>();
            this.incomingArcs[i] = new ArrayList<>();
        }

        registerVertex(createDepotVertex(startDepot, 0));
        int vertexIndex = 1;
        for (int customerId : instance.getRemainingCustomerIds()) {
            Customer customer = instance.getProblemData().getCustomers().get(customerId - 1);
            registerVertex(createCustomerVertex(customer, vertexIndex));
            vertexIndex++;
        }
        registerVertex(createDepotVertex(instance.getReturnDepot(), numVertices - 1));

        buildCompleteFeasibleArcs();
    }

    public List<Arc> getIncomingArcs(int index) {
        return Collections.unmodifiableList(incomingArcs[index]);
    }

    public List<Arc> getArcs() {
        List<Arc> arcs = new ArrayList<>();
        for (List<Arc> outgoingArcList : outgoingArcs) {
            if (outgoingArcList != null) {
                arcs.addAll(outgoingArcList);
            }
        }
        return Collections.unmodifiableList(arcs);
    }

    public Vertex findVertexById(int nodeId) {
        return verticesById.get(nodeId);
    }

    public void updateNgSets(int size) {
        for (Vertex vertex : vertices) {
            if (vertex != null && vertex.isCustomer()) {
                findSimilarCustomers(vertex, size);
            }
        }
    }

    public void findSimilarCustomers(Vertex currentVertex, int size) {
        if (currentVertex == null || !currentVertex.isCustomer()) {
            return;
        }
        currentVertex.getNgSet().clear();
        if (size <= 0) {
            return;
        }

        currentVertex.getNgSet().add(currentVertex.getId());
        if (size == 1) {
            return;
        }

        List<Vertex> candidates = new ArrayList<>();
        for (Vertex vertex : vertices) {
            if (vertex != null && vertex.isCustomer() && vertex.getId() != currentVertex.getId()) {
                candidates.add(vertex);
            }
        }
        candidates.sort(Comparator
                .comparingDouble((Vertex vertex) -> instance.getDistance(currentVertex.getId(), vertex.getId()))
                .thenComparingInt(Vertex::getId));

        int limit = Math.min(size - 1, candidates.size());
        for (int i = 0; i < limit; i++) {
            currentVertex.getNgSet().add(candidates.get(i).getId());
        }
    }

    @Override
    public void removeVertex(int vertexIndex) {
        Vertex removedVertex = vertices.get(vertexIndex);
        super.removeVertex(vertexIndex);
        if (removedVertex != null) {
            verticesById.remove(removedVertex.getId());
        }
        incomingArcs[vertexIndex].clear();
        for (int i = 0; i < numVertices; i++) {
            for (int a = 0; a < incomingArcs[i].size(); a++) {
                Arc arc = incomingArcs[i].get(a);
                if (arc.getTailIndex() == vertexIndex) {
                    incomingArcs[i].remove(a);
                    a--;
                }
            }
        }
    }

    @Override
    public void clearLabels() {
        for (Vertex vertex : vertices) {
            if (vertex != null) {
                vertex.clearLabels();
            }
        }
    }

    private void registerArc(Arc arc) {
        super.addArc(arc);
        incomingArcs[arc.getHeadIndex()].add(arc);
    }

    private void registerVertex(Vertex vertex) {
        super.addVertex(vertex);
        if (vertex != null) {
            verticesById.put(vertex.getId(), vertex);
        }
    }

    private Vertex createDepotVertex(Depot depot, int index) {
        return new Vertex(
                depot.getId(),
                index,
                depot.getX(),
                depot.getY(),
                depot.getEarliestTime(),
                depot.getLatestTime(),
                0.0,
                0,
                depot.getType()
        );
    }

    private Vertex createCustomerVertex(Customer customer, int index) {
        return new Vertex(
                customer.getId(),
                index,
                customer.getX(),
                customer.getY(),
                customer.getEarliestTime(),
                customer.getLatestTime(),
                customer.getServiceTime(),
                customer.getDemand(),
                null
        );
    }

    private void buildCompleteFeasibleArcs() {
        int sinkIndex = numVertices - 1;
        for (int tailIndex = 0; tailIndex < numVertices; tailIndex++) {
            if (tailIndex == sinkIndex) {
                continue;
            }
            for (int headIndex = 1; headIndex < numVertices; headIndex++) {
                if (tailIndex == headIndex) {
                    continue;
                }
                if (!isFeasibleArc(tailIndex, headIndex)) {
                    continue;
                }
                Vertex tail = vertices.get(tailIndex);
                Vertex head = vertices.get(headIndex);
                registerArc(new Arc(
                        tailIndex,
                        headIndex,
                        instance.getDistance(tail.getId(), head.getId()),
                        instance.getTravelTime(tail.getId(), head.getId()),
                        false
                ));
            }
        }
    }

    @Override
    public boolean isFeasibleArc(int tailIndex, int headIndex) {
        Vertex tail = vertices.get(tailIndex);
        Vertex head = vertices.get(headIndex);

        if (headIndex == 0) {
            return false;
        }
        if (tail.isReturnDepot()) {
            return false;
        }
        if (head.isOriginDepot() || head.isExternalDepot()) {
            return false;
        }
        if ((tail.isOriginDepot() || tail.isExternalDepot()) && (head.isOriginDepot() || head.isExternalDepot())) {
            return false;
        }
        return true;
    }
}
