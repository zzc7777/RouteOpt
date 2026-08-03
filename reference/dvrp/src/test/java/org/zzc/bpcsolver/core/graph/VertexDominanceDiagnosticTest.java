package org.zzc.bpcsolver.core.graph;

import org.junit.jupiter.api.Test;
import org.zzc.bpcsolver.algorithm.pricing.Label;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertexDominanceDiagnosticTest {

    @Test
    void shouldReturnDominatingLabelCopyForDiagnostics() {
        Vertex vertex = new Vertex();
        Label dominating = buildLabel(2, 10.0, 3.0, 20.0, 0, 1, 2);
        Label dominated = buildLabel(2, 12.0, 4.0, 25.0, 0, 1, 2, 3);

        assertTrue(vertex.addNonDominatedLabel(dominating));

        Label found = vertex.findDominatingLabel(dominated);

        assertNotNull(found);
        assertEquals(dominating.getLastNodeId(), found.getLastNodeId());
        assertEquals(dominating.getStartServiceTime(), found.getStartServiceTime(), 1e-6);
        assertEquals(dominating.getReducedCost(), found.getReducedCost(), 1e-6);
        assertEquals(1, vertex.getLabelSet().size());
    }

    private Label buildLabel(int lastNodeId,
                             double reducedCost,
                             double load,
                             double startServiceTime,
                             int... ngNodes) {
        Label label = new Label(0, 0, 16);
        label.setLastNodeId(lastNodeId);
        label.setReducedCost(reducedCost);
        label.setLoad(load);
        label.setCapacityUsed(load);
        label.setStartServiceTime(startServiceTime);
        for (int ngNode : ngNodes) {
            label.addNgNode(ngNode);
        }
        return label;
    }
}
