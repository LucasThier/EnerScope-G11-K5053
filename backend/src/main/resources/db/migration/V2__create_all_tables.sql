-- V2: Create all necessary tables for the Nodes
-- This migration creates tables for all @Entity classes in the correct dependency order




-- CompressingPlant table (our target)
CREATE TABLE  node_change (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Specific fields for CompressingPlant
    changeType                VARCHAR(320)             NOT NULL,
    -- Foreign keys (NOT NULL as per entity mappings)
    changed_node_identity     UUID                     NOT NULL,
    changed_node              UUID                     NOT NULL,
    result_node               UUID                     NOT NULL,
    PRIMARY KEY (id),
    -- Foreign key constraints
    CONSTRAINT fk_nc_changed_node FOREIGN KEY (changed_node) REFERENCES base_node(id),  ???
    CONSTRAINT fk_nc_result_node FOREIGN KEY (result_node) REFERENCES node_graph_data(id), ???
    CONSTRAINT fk_nc_identity FOREIGN KEY (changed_node_identity) REFERENCES node_identity(id)
);


-- NodeTypeData table
CREATE TABLE  node_type_data (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Specific fields
    vertical                  VARCHAR(20)                NOT NULL,
    role                      VARCHAR(15)                NOT NULL,
    type                      VARCHAR(25)                NOT NULL,
    PRIMARY KEY (id)
);

-- BaseEntity based tables (no specific fields beyond BaseEntity)
-- These would typically be inherited, but we need to check if they have specific mappings

-- NodeGraphData table
CREATE TABLE  node_graph_data (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Specific fields
    x_position                DOUBLE PRECISION,
    y_position                DOUBLE PRECISION,
    coordinates               DOUBLE PRECISION,
    PRIMARY KEY (id)
);

-- NodeIdentity table
CREATE TABLE  node_identity (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id)
);

-- InvestmentCost table
CREATE TABLE  investment_cost (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- InvestmentCost has no additional fields beyond BaseEntity
    PRIMARY KEY (id)
);

-- InvestmentCostComponent table
CREATE TABLE  investment_cost_component (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Specific fields
    name                      VARCHAR(320)               NOT NULL,
    amount                    NUMERIC(19,2)              NOT NULL,
    cost_basis                VARCHAR(20),
    components_id             UUID                     NOT NULL,
    -- InvestmentCost component belongs to an InvestmentCost (components_id is the foreign key from InvestmentCost.components)
    PRIMARY KEY (id),
    -- Foreign key
    CONSTRAINT fk_icc_investment_cost FOREIGN KEY (components_id) REFERENCES investment_cost(id)
);

-- CompressingPlant table (our target)
CREATE TABLE  compressing_plant (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Inherited from BaseNode
    name                      VARCHAR(320)               NOT NULL,
    node_state                VARCHAR(30)                NOT NULL,
    startup_date              TIMESTAMP WITH TIME ZONE,
    lifespan_in_months        INTEGER,
    up_keep_costs             NUMERIC(19,2)              NOT NULL,
    maintenance_interval_in_days INTEGER,
    operating_costs          NUMERIC(19,2)              NOT NULL,
    waste_percentage          REAL,
    -- Specific fields for CompressingPlant
    max_compression_capacity  REAL,
    process_waste             REAL,
    gas_consumption           REAL,
    -- Foreign keys (NOT NULL as per entity mappings)
    type_id                   UUID                     NOT NULL,
    investment_cost_id        UUID                     NOT NULL,
    graph_data_id             UUID                     NOT NULL,
    identity_id               UUID                     NOT NULL,
    PRIMARY KEY (id),
    -- Foreign key constraints
    CONSTRAINT fk_cp_type FOREIGN KEY (type_id) REFERENCES node_type_data(id),
    CONSTRAINT fk_cp_investment_cost FOREIGN KEY (investment_cost_id) REFERENCES investment_cost(id),
    CONSTRAINT fk_cp_graph_data FOREIGN KEY (graph_data_id) REFERENCES node_graph_data(id),
    CONSTRAINT fk_cp_identity FOREIGN KEY (identity_id) REFERENCES node_identity(id)
);

-- ExportNode based tables
-- SeaportTerminal table
CREATE TABLE  seaport_terminal (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Inherited from BaseNode via ExportNode
    name                      VARCHAR(320)               NOT NULL,
    node_state                VARCHAR(30)                NOT NULL,
    startup_date              TIMESTAMP WITH TIME ZONE,
    lifespan_in_months        INTEGER,
    up_keep_costs             NUMERIC(19,2)              NOT NULL,
    maintenance_interval_in_days INTEGER,
    operating_costs          NUMERIC(19,2)              NOT NULL,
    waste_percentage          REAL,
    -- Specific fields for SeaportTerminal
    intermediate_storage      REAL,
    port_depth                REAL,
    ship_capacity             INTEGER,
    -- Foreign keys (inherited from BaseNode)
    type_id                   UUID                     NOT NULL,
    investment_cost_id        UUID                     NOT NULL,
    graph_data_id             UUID                     NOT NULL,
    identity_id               UUID                     NOT NULL,
    PRIMARY KEY (id),
    -- Foreign keys (inherited from BaseNode)
    CONSTRAINT fk_st_type FOREIGN KEY (type_id) REFERENCES node_type_data(id),
    CONSTRAINT fk_st_investment_cost FOREIGN KEY (investment_cost_id) REFERENCES investment_cost(id),
    CONSTRAINT fk_st_graph_data FOREIGN KEY (graph_data_id) REFERENCES node_graph_data(id),
    CONSTRAINT fk_st_identity FOREIGN KEY (identity_id) REFERENCES node_identity(id)
);

-- LNGCarrier table
CREATE TABLE  lng_carrier (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Inherited from BaseNode via ExportNode
    name                      VARCHAR(320)               NOT NULL,
    node_state                VARCHAR(30)                NOT NULL,
    startup_date              TIMESTAMP WITH TIME ZONE,
    lifespan_in_months        INTEGER,
    up_keep_costs             NUMERIC(19,2)              NOT NULL,
    maintenance_interval_in_days INTEGER,
    operating_costs          NUMERIC(19,2)              NOT NULL,
    waste_percentage          REAL,
    -- Specific fields
    export_frequency          INTEGER,
    ship_capacity             REAL,
    full_load_time            REAL,
    hiring_cost        NUMERIC(19,2)              NOT NULL,
    time_to_destination       INTEGER,
    -- Foreign keys (inherited from BaseNode)
    type_id                   UUID                     NOT NULL,
    investment_cost_id        UUID                     NOT NULL,
    graph_data_id             UUID                     NOT NULL,
    identity_id               UUID                     NOT NULL,
    PRIMARY KEY (id),
    -- Foreign key constraints
    CONSTRAINT fk_lc_type FOREIGN KEY (type_id) REFERENCES node_type_data(id),
    CONSTRAINT fk_lc_investment_cost FOREIGN KEY (investment_cost_id) REFERENCES investment_cost(id),
    CONSTRAINT fk_lc_graph_data FOREIGN KEY (graph_data_id) REFERENCES node_graph_data(id),
    CONSTRAINT fk_lc_identity FOREIGN KEY (identity_id) REFERENCES node_identity(id)
);

-- LiquefactionNode based tables
-- FLNGUnit table
CREATE TABLE  flng_unit (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Inherited from BaseNode via LiquefactionNode
    name                      VARCHAR(320)               NOT NULL,
    node_state                VARCHAR(30)                NOT NULL,
    startup_date              TIMESTAMP WITH TIME ZONE,
    lifespan_in_months        INTEGER,
    up_keep_costs             NUMERIC(19,2)              NOT NULL,
    maintenance_interval_in_days INTEGER,
    operating_costs          NUMERIC(19,2)              NOT NULL,
    waste_percentage          REAL,
    -- Specific fields
    max_processing_capacity   REAL,
    mtpa_ratio                REAL,
    intermediate_storage      REAL,
    vessel_depth              REAL,
    hiring_cost        NUMERIC(19,2)              NOT NULL,
    -- Foreign keys (inherited from BaseNode)
    type_id                   UUID                     NOT NULL,
    investment_cost_id        UUID                     NOT NULL,
    graph_data_id             UUID                     NOT NULL,
    identity_id               UUID                     NOT NULL,
    PRIMARY KEY (id),
    -- Foreign key constraints
    CONSTRAINT fk_fu_type FOREIGN KEY (type_id) REFERENCES node_type_data(id),
    CONSTRAINT fk_fu_investment_cost FOREIGN KEY (investment_cost_id) REFERENCES investment_cost(id),
    CONSTRAINT fk_fu_graph_data FOREIGN KEY (graph_data_id) REFERENCES node_graph_data(id),
    CONSTRAINT fk_fu_identity FOREIGN KEY (identity_id) REFERENCES node_identity(id)
);

-- GroundBasedLiquefactionPlant table
CREATE TABLE  ground_based_liquefaction_plant (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Inherited from BaseNode via LiquefactionNode
    name                      VARCHAR(320)               NOT NULL,
    node_state                VARCHAR(30)                NOT NULL,
    startup_date              TIMESTAMP WITH TIME ZONE,
    lifespan_in_months        INTEGER,
    up_keep_costs             NUMERIC(19,2)              NOT NULL,
    maintenance_interval_in_days INTEGER,
    operating_costs          NUMERIC(19,2)              NOT NULL,
    waste_percentage          REAL,
    -- Specific fields
    max_processing_capacity   REAL,
    mtpa_ratio                REAL,
    intermediate_storage      REAL,
    gas_consumption           REAL,
    -- Foreign keys (inherited from BaseNode)
    type_id                   UUID                     NOT NULL,
    investment_cost_id        UUID                     NOT NULL,
    graph_data_id             UUID                     NOT NULL,
    identity_id               UUID                     NOT NULL,
    PRIMARY KEY (id),
    -- Foreign key constraints
    CONSTRAINT fk_gblp_type FOREIGN KEY (type_id) REFERENCES node_type_data(id),
    CONSTRAINT fk_gblp_investment_cost FOREIGN KEY (investment_cost_id) REFERENCES investment_cost(id),
    CONSTRAINT fk_gblp_graph_data FOREIGN KEY (graph_data_id) REFERENCES node_graph_data(id),
    CONSTRAINT fk_gblp_identity FOREIGN KEY (identity_id) REFERENCES node_identity(id)
);

-- ExtractionNode based tables
-- Well table
CREATE TABLE  well (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Inherited from BaseNode via ExtractionNode
    name                      VARCHAR(320)               NOT NULL,
    node_state                VARCHAR(30)                NOT NULL,
    startup_date              TIMESTAMP WITH TIME ZONE,
    lifespan_in_months        INTEGER,
    up_keep_costs             NUMERIC(19,2)              NOT NULL,
    maintenance_interval_in_days INTEGER,
    operating_costs          NUMERIC(19,2)              NOT NULL,
    waste_percentage          REAL,
    -- Specific fields
    max_collection_capacity   REAL,
    decline_curve             REAL,
    gas_richness               REAL,
    dtm_time                  INTEGER,
    DTMCost                   NUMERIC(19,2)              NOT NULL,
    -- Foreign keys (inherited from BaseNode)
    type_id                   UUID                     NOT NULL,
    investment_cost_id        UUID                     NOT NULL,
    graph_data_id             UUID                     NOT NULL,
    identity_id               UUID                     NOT NULL,
    PRIMARY KEY (id),
    -- Foreign key constraints
    CONSTRAINT fk_w_type FOREIGN KEY (type_id) REFERENCES node_type_data(id),
    CONSTRAINT fk_w_investment_cost FOREIGN KEY (investment_cost_id) REFERENCES investment_cost(id),
    CONSTRAINT fk_w_graph_data FOREIGN KEY (graph_data_id) REFERENCES node_graph_data(id),
    CONSTRAINT fk_w_identity FOREIGN KEY (identity_id) REFERENCES node_identity(id)
);

-- TreatmentPlant table
CREATE TABLE  treatment_plant (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Inherited from BaseNode via ExtractionNode
    name                      VARCHAR(320)               NOT NULL,
    node_state                VARCHAR(30)                NOT NULL,
    startup_date              TIMESTAMP WITH TIME ZONE,
    lifespan_in_months        INTEGER,
    up_keep_costs             NUMERIC(19,2)              NOT NULL,
    maintenance_interval_in_days INTEGER,
    operating_costs          NUMERIC(19,2)              NOT NULL,
    waste_percentage          REAL,
    -- Specific fields
    max_treatment_capacity    REAL,
    contaminant_waste         REAL,
    intermediate_storage      REAL,
    treatment_cost             NUMERIC(19,2)              NOT NULL,
    -- Foreign keys (inherited from BaseNode)
    type_id                   UUID                     NOT NULL,
    investment_cost_id        UUID                     NOT NULL,
    graph_data_id             UUID                     NOT NULL,
    identity_id               UUID                     NOT NULL,
    PRIMARY KEY (id),
    -- Foreign key constraints
    CONSTRAINT fk_tp_type FOREIGN KEY (type_id) REFERENCES node_type_data(id),
    CONSTRAINT fk_tp_investment_cost FOREIGN KEY (investment_cost_id) REFERENCES investment_cost(id),
    CONSTRAINT fk_tp_graph_data FOREIGN KEY (graph_data_id) REFERENCES node_graph_data(id),
    CONSTRAINT fk_tp_identity FOREIGN KEY (identity_id) REFERENCES node_identity(id)
);

-- GatheringNetwork table
CREATE TABLE  gathering_network (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Inherited from BaseNode via ExtractionNode
    name                      VARCHAR(320)               NOT NULL,
    node_state                VARCHAR(30)                NOT NULL,
    startup_date              TIMESTAMP WITH TIME ZONE,
    lifespan_in_months        INTEGER,
    up_keep_costs             NUMERIC(19,2)              NOT NULL,
    maintenance_interval_in_days INTEGER,
    operating_costs          NUMERIC(19,2)              NOT NULL,
    waste_percentage          REAL,
    -- Specific fields (GatheringNetwork)
    max_transport_capacity        REAL,                 
    length                        REAL,
    loss_per_meter                REAL,
    connected_wells               INTEGER,
    -- Foreign keys (inherited from BaseNode)
    type_id                   UUID                     NOT NULL,
    investment_cost_id        UUID                     NOT NULL,
    graph_data_id             UUID                     NOT NULL,
    identity_id               UUID                     NOT NULL,
    PRIMARY KEY (id),
    -- Foreign key constraints
    CONSTRAINT fk_gn_type FOREIGN KEY (type_id) REFERENCES node_type_data(id),
    CONSTRAINT fk_gn_investment_cost FOREIGN KEY (investment_cost_id) REFERENCES investment_cost(id),
    CONSTRAINT fk_gn_graph_data FOREIGN KEY (graph_data_id) REFERENCES node_graph_data(id),
    CONSTRAINT fk_gn_identity FOREIGN KEY (identity_id) REFERENCES node_identity(id)
);

-- TransportationNode based tables
-- PipelineConnection table
CREATE TABLE  pipeline_connection (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Inherited from BaseNode via TransportNode
    name                      VARCHAR(320)               NOT NULL,
    node_state                VARCHAR(30)                NOT NULL,
    startup_date              TIMESTAMP WITH TIME ZONE,
    lifespan_in_months        INTEGER,
    up_keep_costs             NUMERIC(19,2)              NOT NULL,
    maintenance_interval_in_days INTEGER,
    operating_costs          NUMERIC(19,2)              NOT NULL,
    waste_percentage          REAL,
    -- Specific fields (PipelineConnection seems to have no specific fields beyond TransportNode)
    transfer_capacity         REAL,
    output_priority           REAL,
    -- Foreign keys (inherited from BaseNode)
    type_id                   UUID                     NOT NULL,
    investment_cost_id        UUID                     NOT NULL,
    graph_data_id             UUID                     NOT NULL,
    identity_id               UUID                     NOT NULL,
    PRIMARY KEY (id),
    -- Foreign key constraints
    CONSTRAINT fk_pc_type FOREIGN KEY (type_id) REFERENCES node_type_data(id),
    CONSTRAINT fk_pc_investment_cost FOREIGN KEY (investment_cost_id) REFERENCES investment_cost(id),
    CONSTRAINT fk_pc_graph_data FOREIGN KEY (graph_data_id) REFERENCES node_graph_data(id),
    CONSTRAINT fk_pc_identity FOREIGN KEY (identity_id) REFERENCES node_identity(id)
);

-- Pipeline table
CREATE TABLE  pipeline (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Inherited from BaseNode via TransportNode
    name                      VARCHAR(320)               NOT NULL,
    node_state                VARCHAR(30)                NOT NULL,
    startup_date              TIMESTAMP WITH TIME ZONE,
    lifespan_in_months        INTEGER,
    up_keep_costs             NUMERIC(19,2)              NOT NULL,
    maintenance_interval_in_days INTEGER,
    operating_costs          NUMERIC(19,2)              NOT NULL,
    waste_percentage          REAL,
    -- Specific fields
    max_flow_capacity         REAL,
    length                    REAL,
    loss_per_km               REAL,
    
    -- Foreign keys (inherited from BaseNode)
    type_id                   UUID                     NOT NULL,
    investment_cost_id        UUID                     NOT NULL,
    graph_data_id             UUID                     NOT NULL,
    identity_id               UUID                     NOT NULL,
    PRIMARY KEY (id),
    -- Foreign key constraints
    CONSTRAINT fk_p_type FOREIGN KEY (type_id) REFERENCES node_type_data(id),
    CONSTRAINT fk_p_investment_cost FOREIGN KEY (investment_cost_id) REFERENCES investment_cost(id),
    CONSTRAINT fk_p_graph_data FOREIGN KEY (graph_data_id) REFERENCES node_graph_data(id),
    CONSTRAINT fk_p_identity FOREIGN KEY (identity_id) REFERENCES node_identity(id)
);

-- ConnectionIdentity table
CREATE TABLE  connection_identity (
    id           UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- No additional fields in ConnectionIdentity beyond BaseEntity
    PRIMARY KEY (id)
);

-- NodeConnection table
CREATE TABLE  node_connection (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- FK
    from_node_id              UUID                     NOT NULL,
    to_node_id                UUID                     NOT NULL,
    identity_id               UUID                     NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_nc_1_identity FOREIGN KEY (from_node_id) REFERENCES node_identity(id),
    CONSTRAINT fk_nc_2_identity FOREIGN KEY (to_node_id) REFERENCES node_identity(id),
    CONSTRAINT fk_nc_3_identity FOREIGN KEY (identity_id) REFERENCES connection_identity(id)
);