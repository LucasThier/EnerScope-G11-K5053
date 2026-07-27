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

        /**
         * Create a Well node.
         */
        @PostMapping(value = "/well", consumes = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Create a Well node", description = "Create a new Well node with all required properties.")
        public ResponseEntity<ApiResponse<Void>> createWell(
                        @RequestBody WellDTO wellDTO) {
                nodeService.createWell(wellDTO);
                return Responses.ok("Well created successfully");
        }

        /**
         * Create a Gathering Network node.
         */
        @PostMapping(value = "/gathering-network", consumes = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Create a Gathering Network node", description = "Create a new Gathering Network node with all required properties.")
        public ResponseEntity<ApiResponse<GatheringNetworkDTO>> createGatheringNetwork(
                        @RequestBody GatheringNetworkDTO gatheringNetworkDTO) {
                GatheringNetworkDTO createdGN = nodeService.createGatheringNetwork(gatheringNetworkDTO);
                return Responses.ok(
                                "Gathering Network created successfully",
                                createdGN);
        }

        /**
         * Create a Treatment Plant node.
         */
        @PostMapping(value = "/treatment-plant", consumes = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Create a Treatment Plant node", description = "Create a new Treatment Plant node with all required properties.")
        public ResponseEntity<ApiResponse<TreatmentPlantDTO>> createTreatmentPlant(
                        @RequestBody TreatmentPlantDTO treatmentPlantDTO) {
                TreatmentPlantDTO createdTP = nodeService.createTreatmentPlant(treatmentPlantDTO);
                return Responses.ok(
                                "Treatment Plant created successfully",
                                createdTP);
        }

        /**
         * Create a Pipeline node.
         */
        @PostMapping(value = "/pipeline", consumes = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Create a Pipeline node", description = "Create a new Pipeline node with all required properties.")
        public ResponseEntity<ApiResponse<PipelineDTO>> createPipeline(
                        @RequestBody PipelineDTO pipelineDTO) {
                PipelineDTO createdPipeline = nodeService.createPipeline(pipelineDTO);
                return Responses.ok(
                                "Pipeline created successfully",
                                createdPipeline);
        }

        /**
         * Create a Pipeline Connection node.
         */
        @PostMapping(value = "/pipeline-connection", consumes = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Create a Pipeline Connection node", description = "Create a new Pipeline Connection node with all required properties.")
        public ResponseEntity<ApiResponse<PipelineConnectionDTO>> createPipelineConnection(
                        @RequestBody PipelineConnectionDTO pipelineConnectionDTO) {
                PipelineConnectionDTO createdPC = nodeService.createPipelineConnection(pipelineConnectionDTO);
                return Responses.ok(
                                "Pipeline Connection created successfully",
                                createdPC);
        }

        /**
         * Create a Compressing Plant node.
         */
        @PostMapping(value = "/compressing-plant", consumes = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Create a Compressing Plant node", description = "Create a new Compressing Plant node with all required properties.")
        public ResponseEntity<ApiResponse<CompressingPlantDTO>> createCompressingPlant(
                        @RequestBody CompressingPlantDTO compressingPlantDTO) {
                CompressingPlantDTO createdCP = nodeService.createCompressingPlant(compressingPlantDTO);
                return Responses.ok(
                                "Compressing Plant created successfully",
                                createdCP);
        }

        /**
         * Create a Ground Based Liquefaction Plant node.
         */
        @PostMapping(value = "/ground-liquefaction-plant", consumes = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Create a Ground Based Liquefaction Plant node", description = "Create a new Ground Based Liquefaction Plant node with all required properties.")
        public ResponseEntity<ApiResponse<GroundBasedLiquefactionPlantDTO>> createGroundBasedLiquefactionPlant(
                        @RequestBody GroundBasedLiquefactionPlantDTO gblpDTO) {
                GroundBasedLiquefactionPlantDTO createdGBLP = nodeService.createGroundBasedLiquefactionPlant(gblpDTO);
                return Responses.ok(
                                "Ground Based Liquefaction Plant created successfully",
                                createdGBLP);
        }

        /**
         * Create an FLNG Unit node.
         */
        @PostMapping(value = "/flng-unit", consumes = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Create an FLNG Unit node", description = "Create a new FLNG Unit node with all required properties.")
        public ResponseEntity<ApiResponse<FLNGUnitDTO>> createFLNGUnit(
                        @RequestBody FLNGUnitDTO flngUnitDTO) {
                FLNGUnitDTO createdFLNG = nodeService.createFLNGUnit(flngUnitDTO);
                return Responses.ok(
                                "FLNG Unit created successfully",
                                createdFLNG);
        }

        /**
         * Create an LNG Carrier node.
         */
        @PostMapping(value = "/lng-carrier", consumes = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Create an LNG Carrier node", description = "Create a new LNG Carrier node with all required properties.")
        public ResponseEntity<ApiResponse<LNGCarrierDTO>> createLNGCarrier(
                        @RequestBody LNGCarrierDTO lngCarrierDTO) {
                LNGCarrierDTO createdLC = nodeService.createLNGCarrier(lngCarrierDTO);
                return Responses.ok(
                                "LNG Carrier created successfully",
                                createdLC);
        }

        /**
         * Create a Seaport Terminal node.
         */
        @PostMapping(value = "/seaport-terminal", consumes = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Create a Seaport Terminal node", description = "Create a new Seaport Terminal node with all required properties.")
        public ResponseEntity<ApiResponse<SeaportTerminalDTO>> createSeaportTerminal(
                        @RequestBody SeaportTerminalDTO seaportTerminalDTO) {
                SeaportTerminalDTO createdST = nodeService.createSeaportTerminal(seaportTerminalDTO);
                return Responses.ok(
                                "Seaport Terminal created successfully",
                                createdST);
        }

        /**
         * Create a connection between two nodes.
         */
        @PostMapping(value = "/connections", consumes = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Create a connection between two nodes", description = "Create a new connection (edge) between two existing nodes.")
        public ResponseEntity<ApiResponse<ConnectionDTO>> createConnection(
                        @RequestBody ConnectionDTO connectionDTO) {
                ConnectionDTO createdConnection = nodeService.createConnection(connectionDTO);
                return Responses.ok(
                                "Connection created successfully",
                                createdConnection);
        }

        // Additional endpoints for nodes (e.g., get, update, delete) can be added here.
}