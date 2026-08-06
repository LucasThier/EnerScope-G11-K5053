package org.enerscope.node.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.enerscope.node.dto.*;
import org.enerscope.node.service.NodeService;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Node-management endpoints. These are not under {@code /auth/**}, so they
 * require a valid Bearer token (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/nodes")
@Tag(name = "Nodes", description = "Node management operations")
public class NodeController {

        private final NodeService nodeService;

        public NodeController(NodeService nodeService) {
                this.nodeService = nodeService;
        }

        /*
         * Create a Well node.
         */
        @PostMapping(value = "/well", consumes = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Create a Well node", description = "Create a new Well node with all required properties.")
        public ResponseEntity<ApiResponse<Void>> createWell(
                        @RequestBody WellDTO wellDTO) {
                nodeService.saveWell(wellDTO);
                return Responses.ok("Well created successfully");
        }

        @PostMapping(value = "/gathering-network", consumes = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Create a Gathering Network node", description = "Create a new Gathering Network node with all required properties.")
        public ResponseEntity<ApiResponse<Void>> createGatheringNetwork(
                        @RequestBody GatheringNetworkDTO gatheringNetworkDTO) {
                nodeService.saveGatheringNetwork(gatheringNetworkDTO);
                return Responses.ok("Gathering Network created successfully");
        }

        @PostMapping(value = "/treatment-plant", consumes = MediaType.APPLICATION_JSON_VALUE)

        @Operation(summary = "Create a Treatment Plant node", description = "Create a new Treatment Plant node with all required properties.")
        public ResponseEntity<ApiResponse<Void>> createTreatmentPlant(

                        @RequestBody TreatmentPlantDTO treatmentPlantDTO) {
                nodeService.saveTreatmentPlant(treatmentPlantDTO);
                return Responses.ok("Treatment Plant created successfully");
        }

        @PostMapping(value = "/pipeline", consumes = MediaType.APPLICATION_JSON_VALUE)

        @Operation(summary = "Create a Pipeline node", description = "Create a new Pipeline node with all required properties.")
        public ResponseEntity<ApiResponse<Void>> createPipeline(

                        @RequestBody PipelineDTO pipelineDTO) {
                nodeService.savePipeline(pipelineDTO);
                return Responses.ok("Pipeline created successfully");
        }

        @PostMapping(value = "/pipeline-connection", consumes = MediaType.APPLICATION_JSON_VALUE)

        @Operation(summary = "Create a Pipeline Connection node", description = "Create a new Pipeline Connection node with all required properties.")
        public ResponseEntity<ApiResponse<Void>> createPipelineConnection(

                        @RequestBody PipelineConnectionDTO pipelineConnectionDTO) {
                nodeService.savePipelineConnection(pipelineConnectionDTO);
                return Responses.ok("Pipeline Connection created successfully");
        }

        @PostMapping(value = "/compressing-plant", consumes = MediaType.APPLICATION_JSON_VALUE)

        @Operation(summary = "Create a Compressing Plant node", description = "Create a new Compressing Plant node with all required properties.")
        public ResponseEntity<ApiResponse<Void>> createCompressingPlant(

                        @RequestBody CompressingPlantDTO compressingPlantDTO) {
                nodeService.saveCompressingPlant(compressingPlantDTO);
                return Responses.ok("Compressing Plant created successfully");
        }

        @PostMapping(value = "/ground-liquefaction-plant", consumes = MediaType.APPLICATION_JSON_VALUE)

        @Operation(summary = "Create a Ground Based Liquefaction Plant node", description = "Create a new Ground Based Liquefaction Plant node with all required properties.")
        public ResponseEntity<ApiResponse<Void>> createGroundBasedLiquefactionPlant(

                        @RequestBody GroundBasedLiquefactionPlantDTO gblpDTO) {
                nodeService.saveGroundBasedLiquefactionPlant(gblpDTO);
                return Responses.ok("Ground Based Liquefaction Plant created successfully");
        }

        @PostMapping(value = "/flng-unit", consumes = MediaType.APPLICATION_JSON_VALUE)

        @Operation(summary = "Create an FLNG Unit node", description = "Create a new FLNG Unit node with all required properties.")
        public ResponseEntity<ApiResponse<Void>> createFLNGUnit(

                        @RequestBody FLNGUnitDTO flngUnitDTO) {
                nodeService.saveFLNGUnit(flngUnitDTO);
                return Responses.ok("FLNG Unit created successfully");
        }

        @PostMapping(value = "/lng-carrier", consumes = MediaType.APPLICATION_JSON_VALUE)

        @Operation(summary = "Create an LNG Carrier node", description = "Create a new LNG Carrier node with all required properties.")
        public ResponseEntity<ApiResponse<Void>> createLNGCarrier(

                        @RequestBody LNGCarrierDTO lngCarrierDTO) {
                nodeService.saveLNGCarrier(lngCarrierDTO);
                return Responses.ok("LNG Carrier created successfully");
        }

        @PostMapping(value = "/seaport-terminal", consumes = MediaType.APPLICATION_JSON_VALUE)

        @Operation(summary = "Create a Seaport Terminal node", description = "Create a new Seaport Terminal node with all required properties.")
        public ResponseEntity<ApiResponse<Void>> createSeaportTerminal(

                        @RequestBody SeaportTerminalDTO seaportTerminalDTO) {
                nodeService.saveSeaportTerminal(seaportTerminalDTO);
                return Responses.ok("Seaport Terminal created successfully");
        }

        @PostMapping(value = "/connections", consumes = MediaType.APPLICATION_JSON_VALUE)

        @Operation(summary = "Create a connection between two nodes", description = "Create a new connection (edge) between two existing nodes.")
        public ResponseEntity<ApiResponse<Void>> createConnection(

                        @RequestBody ConnectionDTO connectionDTO) {
                nodeService.saveConnection(connectionDTO);
                return Responses.ok("Connection created successfully");
        }

        // Additional endpoints for nodes (e.g., get, update, delete) can be added
        // here.
}