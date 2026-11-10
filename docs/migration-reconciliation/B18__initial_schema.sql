-- Consolidated schema through V18 for empty databases only.
-- Flyway baseline migrations are ignored when versioned migration history already exists.
-- Historical V scripts remain unchanged; subsequent changes start at V19.


-- Schema section V1__init.sql
-- V1: users table
CREATE TABLE app_user (
    id            UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    active        BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
    mail          VARCHAR(320)             NOT NULL    UNIQUE,
    first_name    VARCHAR(60)              NOT NULL,
    last_name     VARCHAR(60)              NOT NULL,
    password_hash VARCHAR(255)             NOT NULL
);

CREATE INDEX idx_app_user_mail ON app_user (LOWER(mail));


-- Schema section V2__create_nodes.sql
-- V2: Create all necessary tables for the Nodes
-- This migration creates tables for all @Entity classes in the correct dependency order

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

-- BaseNode table (our target)
CREATE TABLE  base_node (
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
    operating_costs           NUMERIC(19,2)              NOT NULL,
    waste_percentage          REAL,
    identity_id                  UUID                     NOT NULL,
    -- Foreign keys (NOT NULL as per entity mappings)
    type_id                   UUID                     NOT NULL,
    investment_cost_id        UUID                     NOT NULL,
    graph_data_id             UUID                     NOT NULL,
    PRIMARY KEY (id),
    -- Foreign key constraints
    CONSTRAINT fk_cp_type FOREIGN KEY (type_id) REFERENCES node_type_data(id),
    CONSTRAINT fk_cp_investment_cost FOREIGN KEY (investment_cost_id) REFERENCES investment_cost(id),
    CONSTRAINT fk_cp_graph_data FOREIGN KEY (graph_data_id) REFERENCES node_graph_data(id)
);

-- CompressingPlant table (our target)
CREATE TABLE  compressing_plant (
    id                        UUID                     NOT NULL ,
    -- Specific fields for CompressingPlant
    max_compression_capacity  REAL,
    process_waste             REAL,
    gas_consumption           REAL,
    PRIMARY KEY (id),
    CONSTRAINT fk_cp_base_node FOREIGN KEY (id) REFERENCES base_node(id)
);

-- ExportNode based tables
-- SeaportTerminal table
CREATE TABLE  seaport_terminal (
    id                        UUID                     NOT NULL ,
    -- Specific fields for SeaportTerminal
    intermediate_storage      REAL,
    port_depth                REAL,
    ship_capacity             INTEGER,
    PRIMARY KEY (id),
    CONSTRAINT fk_cp_base_node FOREIGN KEY (id) REFERENCES base_node(id)
);

-- LNGCarrier table
CREATE TABLE  lng_carrier (
    id                        UUID                     NOT NULL ,
    -- Specific fields
    export_frequency          INTEGER,
    ship_capacity             REAL,
    full_load_time            REAL,
    hiring_cost        NUMERIC(19,2)              NOT NULL,
    time_to_destination       INTEGER,
    -- Foreign keys (inherited from BaseNode)

    PRIMARY KEY (id),
    CONSTRAINT fk_cp_base_node FOREIGN KEY (id) REFERENCES base_node(id)
);

-- LiquefactionNode based tables
-- FLNGUnit table
CREATE TABLE  flng_unit (
    id                        UUID                     NOT NULL ,
    -- Specific fields
    max_processing_capacity   REAL,
    mtpa_ratio                REAL,
    intermediate_storage      REAL,
    vessel_depth              REAL,
    hiring_cost        NUMERIC(19,2)              NOT NULL,
    ship_capacity          INTEGER,

    -- Foreign keys (inherited from BaseNode)

    PRIMARY KEY (id),
    CONSTRAINT fk_cp_base_node FOREIGN KEY (id) REFERENCES base_node(id)
);

-- GroundBasedLiquefactionPlant table
CREATE TABLE  ground_based_liquefaction_plant (
    id                        UUID                     NOT NULL ,
    -- Specific fields
    max_processing_capacity   REAL,
    mtpa_ratio                REAL,
    intermediate_storage      REAL,
    gas_consumption           REAL,

    PRIMARY KEY (id),
    CONSTRAINT fk_cp_base_node FOREIGN KEY (id) REFERENCES base_node(id)
);

-- ExtractionNode based tables
-- Well table
CREATE TABLE  well (
    id                        UUID                     NOT NULL ,
    -- Specific fields
    max_collection_capacity   REAL,
    decline_curve             REAL,
    gas_richness               REAL,
    dtm_time                  INTEGER,
    DTMCost                   NUMERIC(19,2)              NOT NULL,
    surface                     REAL,

    PRIMARY KEY (id),
    CONSTRAINT fk_cp_base_node FOREIGN KEY (id) REFERENCES base_node(id)
);

-- TreatmentPlant table
CREATE TABLE  treatment_plant (
    id                        UUID                     NOT NULL ,
    -- Specific fields
    max_treatment_capacity    REAL,
    intermediate_storage      REAL,
    treatment_cost             NUMERIC(19,2)              NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_cp_base_node FOREIGN KEY (id) REFERENCES base_node(id)
);

-- GatheringNetwork table
CREATE TABLE  gathering_network (
    id                        UUID                     NOT NULL ,
    -- Specific fields (GatheringNetwork)
    max_transport_capacity        REAL,                 
    length                        REAL,
    loss_per_meter                REAL,
    connected_wells               INTEGER,
    -- Foreign keys (inherited from BaseNode)

    PRIMARY KEY (id),
    CONSTRAINT fk_cp_base_node FOREIGN KEY (id) REFERENCES base_node(id)
);

-- TransportationNode based tables
-- PipelineConnection table
CREATE TABLE  pipeline_connection (
    id                        UUID                     NOT NULL ,
    -- Specific fields
    transfer_capacity         REAL,
    output_priority           REAL,
    -- Foreign keys (inherited from BaseNode)

    PRIMARY KEY (id),
    CONSTRAINT fk_cp_base_node FOREIGN KEY (id) REFERENCES base_node(id)
);

-- Pipeline table
CREATE TABLE  pipeline (
    id                        UUID                     NOT NULL ,
    -- Specific fields
    max_flow_capacity         REAL,
    length                    REAL,
    loss_per_km               REAL,
    
    -- Foreign keys (inherited from BaseNode)

    PRIMARY KEY (id),
    CONSTRAINT fk_cp_base_node FOREIGN KEY (id) REFERENCES base_node(id)
);

-- NodeConnection table
CREATE TABLE  node_connection (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- identities
    from_node_id              UUID                     NOT NULL,
    to_node_id                UUID                     NOT NULL,
    identity_id               UUID                     NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_cp_from_node FOREIGN KEY (from_node_id) REFERENCES base_node(id),
    CONSTRAINT fk_cp_to_node FOREIGN KEY (to_node_id) REFERENCES base_node(id)
);

CREATE TABLE internal_consumption (
                                      id                  UUID NOT NULL,
    -- Specific fields for InternalConsumption
                                      consumption_summer  REAL,
                                      consumption_autumn  REAL,
                                      consumption_winter  REAL,
                                      consumption_spring  REAL,

                                      PRIMARY KEY (id),
                                      CONSTRAINT fk_ic_base_node FOREIGN KEY (id) REFERENCES base_node(id)
);

CREATE TABLE industrial_consumption (
                                      id          UUID NOT NULL,
    -- Specific fields for InternalConsumption
                                      consumption REAL,

                                      PRIMARY KEY (id),
                                      CONSTRAINT fk_ic_base_node FOREIGN KEY (id) REFERENCES base_node(id)
);




-- Schema section V3__create_organization_tables.sql
-- V3: organizations, organization membership/roles and projects
CREATE TABLE organization (
    id            UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    active        BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
    name          VARCHAR(120)             NOT NULL
);

CREATE TABLE organization_member (
    id              UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    active          BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified   TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id         UUID                     NOT NULL    REFERENCES app_user (id),
    organization_id UUID                     NOT NULL    REFERENCES organization (id),
    CONSTRAINT uq_org_member_org_user UNIQUE (organization_id, user_id)
);

CREATE INDEX idx_org_member_organization ON organization_member (organization_id);
CREATE INDEX idx_org_member_user ON organization_member (user_id);

CREATE TABLE organization_member_role (
    id                      UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    active                  BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified           TIMESTAMP WITH TIME ZONE NOT NULL,
    name                    VARCHAR(60)              NOT NULL,
    member_type             VARCHAR(30)              NOT NULL,
    organization_member_id  UUID                     NOT NULL REFERENCES organization_member (id)
);

CREATE INDEX idx_org_member_role_member ON organization_member_role (organization_member_id);

CREATE TABLE organization_member_role_permission (
    organization_member_role_id UUID        NOT NULL REFERENCES organization_member_role (id),
    permission                  VARCHAR(40) NOT NULL,
    PRIMARY KEY (organization_member_role_id, permission)
);

CREATE TABLE project (
    id              UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    active          BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified   TIMESTAMP WITH TIME ZONE NOT NULL,
    name            VARCHAR(120)             NOT NULL,
    description     VARCHAR(500)             NOT NULL,
    organization_id UUID                     NOT NULL    REFERENCES organization (id)
);

CREATE INDEX idx_project_organization ON project (organization_id);


-- Schema section V4__create_project_member_tables.sql
-- V4: project membership and roles
CREATE TABLE project_member (
    id            UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    active        BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id       UUID                     NOT NULL    REFERENCES app_user (id),
    project_id    UUID                     NOT NULL    REFERENCES project (id),
    CONSTRAINT uq_project_member_project_user UNIQUE (project_id, user_id)
);

CREATE INDEX idx_project_member_project ON project_member (project_id);
CREATE INDEX idx_project_member_user ON project_member (user_id);

CREATE TABLE project_member_role (
    id                 UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    active             BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified      TIMESTAMP WITH TIME ZONE NOT NULL,
    name               VARCHAR(60)              NOT NULL,
    member_type        VARCHAR(30)              NOT NULL,
    project_member_id  UUID                     NOT NULL REFERENCES project_member (id)
);

CREATE INDEX idx_project_member_role_member ON project_member_role (project_member_id);

CREATE TABLE project_member_role_permission (
    project_member_role_id UUID        NOT NULL REFERENCES project_member_role (id),
    permission              VARCHAR(40) NOT NULL,
    PRIMARY KEY (project_member_role_id, permission)
);


-- Schema section V5__create_version_tables.sql

-- Version table
CREATE TABLE  version (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Specific fields
    name                      VARCHAR(320)               NOT NULL,
    parent_version_id         UUID,
    versions_id        UUID,
    PRIMARY KEY (id),
    -- Foreign key constraints
    CONSTRAINT fk_version_parent FOREIGN KEY (parent_version_id) REFERENCES version(id),
    CONSTRAINT fk_project FOREIGN KEY (versions_id) REFERENCES project (id)
);


CREATE TABLE  node_change (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Specific fields for CompressingPlant
    change_type                VARCHAR(320)             NOT NULL,
    -- Foreign keys (NOT NULL as per entity mappings)
    changed_node_id              UUID,
    result_node_id               UUID,
    -- Version relationship
    node_changes_id              UUID,
    PRIMARY KEY (id),
    -- Foreign key constraints
    CONSTRAINT fk_nc_changed_node FOREIGN KEY (changed_node_id) REFERENCES base_node(id),
    CONSTRAINT fk_nc_result_node FOREIGN KEY (result_node_id) REFERENCES base_node(id),
    CONSTRAINT fk_nc_node_changes FOREIGN KEY (node_changes_id) REFERENCES version(id)
    );

CREATE TABLE  connection_change (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Specific fields for CompressingPlant
    change_type                VARCHAR(320)             NOT NULL,
    -- Foreign keys (NOT NULL as per entity mappings)
    changed_connection_id              UUID,
    result_connection_id               UUID,
    -- Version relationship
    connection_changes_id              UUID,
    PRIMARY KEY (id),
    -- Foreign key constraints
    CONSTRAINT fk_cc_changed_connection FOREIGN KEY (changed_connection_id) REFERENCES node_connection(id),
    CONSTRAINT fk_cc_result_connection FOREIGN KEY (result_connection_id) REFERENCES node_connection(id),
    CONSTRAINT fk_cc_connection_changes FOREIGN KEY (connection_changes_id) REFERENCES version(id)
    );


-- Join table for version and node snapshot (many-to-many)
CREATE TABLE  versionXNode (
    version_id                UUID                     NOT NULL,
    node_id                   UUID                     NOT NULL,
    PRIMARY KEY (version_id, node_id),
    CONSTRAINT fk_versionxnode_version FOREIGN KEY (version_id) REFERENCES version(id),
    CONSTRAINT fk_versionxnode_node FOREIGN KEY (node_id) REFERENCES base_node(id)
);

-- Join table for version and connection snapshot (many-to-many)
CREATE TABLE  versionXConnection (
    version_id                UUID                     NOT NULL,
    connection_id             UUID                     NOT NULL,
    PRIMARY KEY (version_id, connection_id),
    CONSTRAINT fk_versionxconnection_version FOREIGN KEY (version_id) REFERENCES version(id),
    CONSTRAINT fk_versionxconnection_connection FOREIGN KEY (connection_id) REFERENCES node_connection(id)
);


-- Schema section V6__add_platform_role.sql
-- V7: platform-level role for every user (ADMIN or USER).
-- Existing rows default to USER; the seeded default administrator is promoted.
ALTER TABLE app_user
    ADD COLUMN platform_role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- Promote the default seeded administrator. Deployments that override
-- app.admin.mail rely on the AdminSeeder to create the admin with the ADMIN
-- role instead (see docs/considerations.md).
UPDATE app_user
    SET platform_role = 'ADMIN'
    WHERE LOWER(mail) = LOWER('admin@enerscope.org');


-- Schema section V7__add_job_title.sql
-- V7: optional job title for every user (e.g. "Senior Investment Analyst").
-- Nullable and without a default: accounts created before this migration have
-- no title recorded, and the field stays optional afterwards.
ALTER TABLE app_user
    ADD COLUMN job_title VARCHAR(120);


-- Schema section V8.5__create_results.sql
--V7: Result tables
CREATE TABLE result (
                        id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Specific fields
                        year                      INT                      NOT NULL,
                        version_id                UUID,
                        PRIMARY KEY (id),
    -- Foreign key constraints
                        CONSTRAINT fk_result_version FOREIGN KEY (version_id) REFERENCES version(id) ON DELETE CASCADE
);

CREATE TABLE result_per_node (
                                 id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Specific fields
                                 node_id                   UUID                     NOT NULL,
                                 node_class                 VARCHAR(100)             NOT NULL,
                                 total_produced            REAL                     NOT NULL DEFAULT 0.0,
                                 total_deferred            REAL                     NOT NULL DEFAULT 0.0,
                                 max_possible_produced     REAL                     NOT NULL DEFAULT 0.0,
                                 extra                     REAL                     NOT NULL DEFAULT 0.0,
    -- Relationship field
                                 result_id                 UUID                     NOT NULL,
                                 PRIMARY KEY (id),
    -- Foreign key constraints
                                 CONSTRAINT fk_rpn_result FOREIGN KEY (result_id) REFERENCES result(id) ON DELETE CASCADE,
                                 CONSTRAINT fk_rpn_node FOREIGN KEY (node_id) REFERENCES base_node(id)
);

-- Indexes for optimal lookup performance
CREATE INDEX idx_result_version_id ON result(version_id);
CREATE INDEX idx_rpn_result_id ON result_per_node(result_id);
CREATE INDEX idx_rpn_node_id ON result_per_node(node_id);


-- Schema section V9__create_results.sql
-- V9: final (aggregated) result tables.
-- `result` and `result_per_node` (with their indexes) are already created by
-- V8__create_results.sql; this migration only adds what V8 does not have.
CREATE TABLE final_result (
                              id           UUID NOT NULL DEFAULT gen_random_uuid(),
                              time         INT ,
                              media_output REAL ,
                              version_id   UUID,
                              PRIMARY KEY (id),
                              CONSTRAINT fk_final_result_version FOREIGN KEY (version_id) REFERENCES version(id) ON DELETE CASCADE
);

CREATE TABLE final_result_p90 (
                                  final_result_id    UUID NOT NULL,
                                  result_per_node_id UUID NOT NULL,
                                  PRIMARY KEY (final_result_id, result_per_node_id),
                                  CONSTRAINT fk_p90_fr FOREIGN KEY (final_result_id) REFERENCES final_result(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_p90_rpn FOREIGN KEY (result_per_node_id) REFERENCES result_per_node(id) ON DELETE CASCADE
);

CREATE TABLE final_result_p50 (
                                  final_result_id    UUID NOT NULL,
                                  result_per_node_id UUID NOT NULL,
                                  PRIMARY KEY (final_result_id, result_per_node_id),
                                  CONSTRAINT fk_p50_fr FOREIGN KEY (final_result_id) REFERENCES final_result(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_p50_rpn FOREIGN KEY (result_per_node_id) REFERENCES result_per_node(id) ON DELETE CASCADE
);

CREATE TABLE final_result_p10 (
                                  final_result_id    UUID NOT NULL,
                                  result_per_node_id UUID NOT NULL,
                                  PRIMARY KEY (final_result_id, result_per_node_id),
                                  CONSTRAINT fk_p10_fr FOREIGN KEY (final_result_id) REFERENCES final_result(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_p10_rpn FOREIGN KEY (result_per_node_id) REFERENCES result_per_node(id) ON DELETE CASCADE
);

CREATE INDEX idx_final_result_version_id ON final_result(version_id);


-- Schema section V10__split_node_graph_position.sql
-- V10: Split NodeGraphData's single position into two independent positions:
--   * graph position (graph_x / graph_y)  -> abstract diagram-canvas coordinates
--   * geographical position (longitude / latitude) -> real-world coordinates (map/globe view)
-- Replaces the previous x_position / y_position / coordinates columns.

ALTER TABLE node_graph_data ADD COLUMN graph_x   DOUBLE PRECISION;
ALTER TABLE node_graph_data ADD COLUMN graph_y   DOUBLE PRECISION;
ALTER TABLE node_graph_data ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE node_graph_data ADD COLUMN latitude  DOUBLE PRECISION;

-- Preserve any existing diagram positions; the old single `coordinates` value
-- has no meaningful mapping to a (longitude, latitude) pair and is dropped.
UPDATE node_graph_data SET graph_x = x_position, graph_y = y_position;

ALTER TABLE node_graph_data DROP COLUMN x_position;
ALTER TABLE node_graph_data DROP COLUMN y_position;
ALTER TABLE node_graph_data DROP COLUMN coordinates;


-- Schema section V11__economic_module.sql
-- Align existing result mappings without rewriting applied migrations.
ALTER TABLE result RENAME COLUMN year TO simulation_year;

CREATE TABLE economic_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
    version_id UUID NOT NULL UNIQUE REFERENCES version(id) ON DELETE CASCADE,
    configuration_json TEXT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE economic_evaluation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
    version_id UUID NOT NULL REFERENCES version(id) ON DELETE CASCADE,
    snapshot_json TEXT NOT NULL
);
CREATE INDEX idx_economic_evaluation_version ON economic_evaluation(version_id, created_at);


-- Schema section V12__align_operational_schema.sql
-- Fields used by the simulator existed in Java but were missing from V2.
ALTER TABLE base_node ADD COLUMN maintenance_duration INTEGER NOT NULL DEFAULT 0;
ALTER TABLE flng_unit ADD COLUMN gas_consumption REAL NOT NULL DEFAULT 0;


-- Schema section V13__economic_drafts.sql
CREATE TABLE economic_draft (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
    version_id UUID NOT NULL UNIQUE REFERENCES version(id) ON DELETE CASCADE,
    draft_json TEXT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0
);


-- Schema section V14__create_version_conflict_tables.sql
-- V14: conflicts detected automatically when a subversion is merged into its parent.
-- One row per (sibling, entity) whose node/connection identityId was also changed by the merged subversion.
CREATE TABLE version_conflict (
    id                     UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                 BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified          TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Specific fields
    merged_version_id      UUID                     NOT NULL,
    source_version_id      UUID                     NOT NULL,
    conflicting_version_id UUID                     NOT NULL,
    entity_type            VARCHAR(20)              NOT NULL,
    identity_id            UUID                     NOT NULL,
    resolved               BOOLEAN                  NOT NULL DEFAULT FALSE,
    resolved_at            TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT fk_vc_merged_version FOREIGN KEY (merged_version_id) REFERENCES version (id) ON DELETE CASCADE,
    CONSTRAINT fk_vc_source_version FOREIGN KEY (source_version_id) REFERENCES version (id) ON DELETE CASCADE,
    CONSTRAINT fk_vc_conflicting_version FOREIGN KEY (conflicting_version_id) REFERENCES version (id) ON DELETE CASCADE
);

CREATE INDEX idx_version_conflict_conflicting ON version_conflict (conflicting_version_id);
CREATE INDEX idx_version_conflict_merged ON version_conflict (merged_version_id);
CREATE INDEX idx_version_conflict_identity ON version_conflict (identity_id);


-- Schema section V15__normalize_export_vertical.sql
-- The original export constant used Cyrillic lookalikes (U+0415 U+0425 U+0420 U+041E R U+0422).
-- Normalize only that exact legacy value to the current ASCII enum constant.
UPDATE node_type_data
SET vertical = 'EXPORT'
WHERE vertical = 'ЕХРОRТ';


-- Schema section V16__probabilistic_economic_jobs.sql
CREATE TABLE economic_job (
 id UUID PRIMARY KEY, active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL,
 last_modified TIMESTAMPTZ NOT NULL, version_id UUID NOT NULL REFERENCES version(id),
 input_json TEXT NOT NULL, status VARCHAR(24) NOT NULL, rounds INT NOT NULL CHECK (rounds BETWEEN 1 AND 100),
 completed_rounds INT NOT NULL DEFAULT 0 CHECK (completed_rounds >= 0 AND completed_rounds <= rounds),
 seed VARCHAR(20) NOT NULL, started_at TIMESTAMPTZ, finished_at TIMESTAMPTZ, error_message TEXT,
 evaluation_id UUID UNIQUE REFERENCES economic_evaluation(id),
 CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED','INTERRUPTED'))
);
CREATE INDEX idx_economic_job_queue ON economic_job(status,created_at);
CREATE INDEX idx_economic_job_version ON economic_job(version_id,created_at);
CREATE TABLE economic_round (
 id UUID PRIMARY KEY, active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL,
 last_modified TIMESTAMPTZ NOT NULL, job_id UUID NOT NULL REFERENCES economic_job(id),
 round_number INT NOT NULL CHECK (round_number BETWEEN 1 AND 100), snapshot_json TEXT NOT NULL,
 summary_json TEXT NOT NULL, UNIQUE(job_id,round_number)
);
-- New final-result percentile collections no longer require a legacy per-round Result owner.
ALTER TABLE result_per_node ALTER COLUMN result_id DROP NOT NULL;


-- Schema section V17__upgrade_legacy_well_distributions.sql
CREATE TABLE probabilistic_distribution (
                                            id                  UUID NOT NULL DEFAULT gen_random_uuid(),
                                            distribution_type   VARCHAR(30) NOT NULL,
    -- Parámetros (todos deben permitir NULL porque varían según el tipo)
                                            val_a        REAL,
                                            val_b               REAL,
                                            PRIMARY KEY (id)
);
-- Preserve legacy deterministic decline as a constant distribution per well.
-- Retain the scalar column for audit and rollback of the previous application.
ALTER TABLE well ADD COLUMN decline_curve_id UUID REFERENCES probabilistic_distribution(id);
INSERT INTO probabilistic_distribution (id, distribution_type, val_a)
SELECT id, 'CONSTANT', decline_curve FROM well WHERE decline_curve IS NOT NULL;
UPDATE well SET decline_curve_id = id WHERE decline_curve IS NOT NULL;


-- Schema section V18__add_flng_ship_capacity.sql
-- ship_capacity is already present in the consolidated FLNG table.
ALTER TABLE flng_unit ALTER COLUMN ship_capacity SET DEFAULT 0;
UPDATE flng_unit SET ship_capacity = 0 WHERE ship_capacity IS NULL;
ALTER TABLE flng_unit ALTER COLUMN ship_capacity SET NOT NULL;
