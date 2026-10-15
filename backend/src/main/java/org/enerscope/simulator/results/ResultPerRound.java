package org.enerscope.simulator.results;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.export.IndustrialConsumption;
import org.enerscope.node.model.export.InternalConsumption;
import org.enerscope.node.model.export.LNGCarrier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The per-node results of one Monte Carlo round. Not persisted: only the rounds
 * picked as percentiles survive, copied into {@link FinalResult}.
 */
@Getter
@Setter
public class ResultPerRound {

    /**
     * Node classes at the end of the value chain, whose output is what the chain
     * actually delivers: LNG loaded onto carriers for export, plus the gas taken by
     * domestic consumption.
     */
    public static final Set<String> SINK_CLASSES = Set.of(
            LNGCarrier.class.getSimpleName(),
            InternalConsumption.class.getSimpleName(),
            IndustrialConsumption.class.getSimpleName());

    private int year;

    private List<ResultPerNode> resultPerNodes;


    public void addAllResultPerNodes(List<ResultPerNode> resultPerNodes){
        this.resultPerNodes.addAll(resultPerNodes);
    }

    public ResultPerRound(){
        this.resultPerNodes = new ArrayList<>();
    }

    /** LNG exported in this round: what the carriers loaded. */
    public float amountProduced(){
        return sumOf(Set.of(LNGCarrier.class.getSimpleName()));
    }

    /**
     * Everything the chain delivered in this round — exports plus domestic
     * consumption. This is what rounds are ranked by: a model that only feeds the
     * domestic market exports nothing, and ranking it by exports alone would put
     * its rounds in an arbitrary order.
     */
    public float amountDelivered(){
        return sumOf(SINK_CLASSES);
    }

    // Compared with equals, not ==: the class names come from getSimpleName(),
    // whose cached String is not guaranteed to be the same instance every time.
    private float sumOf(Set<String> nodeClasses) {
        double total = 0;
        for (ResultPerNode resultPerNode : resultPerNodes) {
            if (nodeClasses.contains(resultPerNode.getNodeClass())) {
                total += resultPerNode.getTotalProduced();
            }
        }
        return (float) total;
    }
}
