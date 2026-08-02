package org.enerscope.simulator;

import org.enerscope.node.model.export.LNGCarrier;
import org.enerscope.node.model.export.SeaportTerminal;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.node.model.extraction.TreatmentPlant;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.liquefaction.FLNGUnit;
import org.enerscope.node.model.liquefaction.GroundBasedLiquefactionPlant;
import org.enerscope.node.model.transportation.CompressingPlant;
import org.enerscope.node.model.transportation.Pipeline;
import org.enerscope.node.model.transportation.PipelineConnection;

import java.util.List;

public class Simulator {

    private List<LNGCarrier> lngCarriers;
    private List<SeaportTerminal> seaportTerminals;
    private List<GatheringNetwork> gatheringNetworks;
    private List<TreatmentPlant> treatmentPlants;
    private List<Well> wells;
    private List<FLNGUnit> flngUnits;
    private List<GroundBasedLiquefactionPlant> groundBasedLiquefactionPlants;
    private List<CompressingPlant> compressingPlants;
    private List<Pipeline> pipelines;
    private List<PipelineConnection> pipelineConnections;

    private Result  result = new Result();
    public Result Simulate(Version version){

        this.gatheringNetworks = version.getGatheringNetworks();

        this.gatheringNetworks.forEach(gatheringNetwork -> SimulateGatheringNetwork(gatheringNetwork,version));

    }

    private void SimulateGatheringNetwork(GatheringNetwork gatheringNetwork,Version version){
        this.wells = version.findWellsConnectedToGatheringNetwork();
        Float maxExtraxtion = (float) wells.stream().mapToDouble(well -> well.getMaxCollectionCapacity()).sum();
        Float maxCapacity = gatheringNetwork.getMaxTransportCapacity();
        Float diferenceWells = maxCapacity - maxExtraxtion;

        Float totalTransported = Math.min(maxCapacity, maxExtraxtion);
        TreatmentPlant treatmentPlant = version.findTrearmentPlantConnectedToGatheringNetwork();

        Float diferenceTratment = treatmentPlant.getMaxTreatmentCapacity() - totalTransported;

        if (diferenceWells >= 0 && diferenceTratment >= 0){

            wells.forEach(well -> WellResults(well,(float) 100.0,gatheringNetwork));
            GatheringNetworkResults((float) 100.0, gatheringNetwork, treatmentPlant);

        } else if (diferenceWells < 0 && diferenceTratment >= 0){

            Float diferencePerEach = diferenceWells/ gatheringNetwork.getConnectedWells();
            wells.forEach(well -> WellResults(well,(float) (well.getMaxCollectionCapacity() - diferencePerEach) * 100 / well.getMaxCollectionCapacity(),gatheringNetwork ));
            GatheringNetworkResults((float) 100.0, gatheringNetwork, treatmentPlant);

        } else if (diferenceWells >= 0 && diferenceTratment < 0){
            Float percentDiferenceTratment =  (maxCapacity - diferenceTratment) / maxCapacity;
            wells.forEach(well -> WellResults(well,(float) 100.0 * percentDiferenceTratment,gatheringNetwork));
            GatheringNetworkResults((float) 100.0 * percentDiferenceTratment, gatheringNetwork, treatmentPlant);
        } else {
            Float diferencePerEach = diferenceWells/ gatheringNetwork.getConnectedWells();
            Float percentDiferenceTratment =  (maxCapacity - diferenceTratment) / maxCapacity;
            wells.forEach(well -> WellResults(well,(float) (well.getMaxCollectionCapacity() - diferencePerEach) * 100 * percentDiferenceTratment  / well.getMaxCollectionCapacity(),gatheringNetwork ));
            GatheringNetworkResults((float) 100.0 * percentDiferenceTratment, gatheringNetwork, treatmentPlant);
        }
    }

    private void WellResults(Well well, Float percent, GatheringNetwork gatheringNetwork){
        result.addPercentWell(well, percent, gatheringNetwork);
    }
    private void GatheringNetworkResults(Float percent, GatheringNetwork gatheringNetwork, TreatmentPlant treatmentPlant){
        result.addPercentGatheringNetwork(percent, gatheringNetwork, treatmentPlant);
    }

}
