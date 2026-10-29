-- 数字孪生可视化仓库 DDL（PostgreSQL）
-- 第一阶段：平库

CREATE TABLE IF NOT EXISTS w3d_warehouse (
  id              varchar(64) PRIMARY KEY,
  code            varchar(50) NOT NULL UNIQUE,
  name            varchar(100) NOT NULL,
  length          numeric(10,2) NOT NULL,
  width           numeric(10,2) NOT NULL,
  height          numeric(10,2) NOT NULL,
  status          varchar(20) DEFAULT 'active',
  description     varchar(500),
  create_time     timestamp DEFAULT CURRENT_TIMESTAMP,
  update_time     timestamp DEFAULT CURRENT_TIMESTAMP,
  create_by       varchar(50),
  update_by       varchar(50)
);
COMMENT ON TABLE w3d_warehouse IS '数字孪生-仓库';

CREATE TABLE IF NOT EXISTS w3d_area (
  id              varchar(64) PRIMARY KEY,
  warehouse_id    varchar(64) NOT NULL,
  code            varchar(50) NOT NULL,
  name            varchar(100) NOT NULL,
  area_type       varchar(20) NOT NULL,
  pos_x           numeric(10,2) NOT NULL,
  pos_z           numeric(10,2) NOT NULL,
  length          numeric(10,2) NOT NULL,
  width           numeric(10,2) NOT NULL,
  sort_order      int DEFAULT 0,
  create_time     timestamp DEFAULT CURRENT_TIMESTAMP,
  update_time     timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_w3d_area_wh_code UNIQUE (warehouse_id, code)
);
CREATE INDEX IF NOT EXISTS idx_w3d_area_wh ON w3d_area (warehouse_id);
COMMENT ON TABLE w3d_area IS '数字孪生-库区';

CREATE TABLE IF NOT EXISTS w3d_rack (
  id              varchar(64) PRIMARY KEY,
  area_id         varchar(64) NOT NULL,
  warehouse_id    varchar(64) NOT NULL,
  code            varchar(50) NOT NULL,
  pos_x           numeric(10,2) NOT NULL,
  pos_z           numeric(10,2) NOT NULL,
  length          numeric(10,2) NOT NULL,
  width           numeric(10,2) NOT NULL,
  levels          int NOT NULL,
  bays_per_level  int NOT NULL,
  orientation     varchar(2) NOT NULL,
  create_time     timestamp DEFAULT CURRENT_TIMESTAMP,
  update_time     timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_w3d_rack_area_code UNIQUE (area_id, code)
);
CREATE INDEX IF NOT EXISTS idx_w3d_rack_area ON w3d_rack (area_id);
CREATE INDEX IF NOT EXISTS idx_w3d_rack_wh ON w3d_rack (warehouse_id);
COMMENT ON TABLE w3d_rack IS '数字孪生-货架';

CREATE TABLE IF NOT EXISTS w3d_location (
  id              varchar(64) PRIMARY KEY,
  rack_id         varchar(64) NOT NULL,
  warehouse_id    varchar(64) NOT NULL,
  code            varchar(50) NOT NULL,
  level           int NOT NULL,
  bay             int NOT NULL,
  pos_x           numeric(10,2) NOT NULL,
  pos_y           numeric(10,2) NOT NULL,
  pos_z           numeric(10,2) NOT NULL,
  size_x          numeric(10,2) NOT NULL,
  size_y          numeric(10,2) NOT NULL,
  size_z          numeric(10,2) NOT NULL,
  status          varchar(20) DEFAULT 'empty',
  utilization     numeric(5,2) DEFAULT 0,
  product_code    varchar(50),
  update_time     timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_w3d_loc_rack_code UNIQUE (rack_id, code)
);
CREATE INDEX IF NOT EXISTS idx_w3d_loc_rack ON w3d_location (rack_id);
CREATE INDEX IF NOT EXISTS idx_w3d_loc_wh ON w3d_location (warehouse_id);
CREATE INDEX IF NOT EXISTS idx_w3d_loc_status ON w3d_location (warehouse_id, status);
CREATE INDEX IF NOT EXISTS idx_w3d_loc_product ON w3d_location (product_code);
COMMENT ON TABLE w3d_location IS '数字孪生-库位';

CREATE TABLE IF NOT EXISTS w3d_obstacle (
  id              varchar(64) PRIMARY KEY,
  warehouse_id    varchar(64) NOT NULL,
  obstacle_type   varchar(20) NOT NULL,
  pos_x           numeric(10,2) NOT NULL,
  pos_z           numeric(10,2) NOT NULL,
  length          numeric(10,2) NOT NULL,
  width           numeric(10,2) NOT NULL,
  height          numeric(10,2) NOT NULL,
  rotation        numeric(10,4) DEFAULT 0,
  create_time     timestamp DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_w3d_obs_wh ON w3d_obstacle (warehouse_id);
COMMENT ON TABLE w3d_obstacle IS '数字孪生-障碍物';

CREATE TABLE IF NOT EXISTS w3d_channel (
  id              varchar(64) PRIMARY KEY,
  warehouse_id    varchar(64) NOT NULL,
  channel_type    varchar(20) NOT NULL,
  path_json       text NOT NULL,
  width           numeric(10,2) NOT NULL,
  bidirectional   boolean DEFAULT true,
  create_time     timestamp DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_w3d_ch_wh ON w3d_channel (warehouse_id);
COMMENT ON TABLE w3d_channel IS '数字孪生-通道';

CREATE TABLE IF NOT EXISTS w3d_layout_snapshot (
  id              varchar(64) PRIMARY KEY,
  warehouse_id    varchar(64) NOT NULL,
  version         int NOT NULL,
  snapshot_time   timestamp DEFAULT CURRENT_TIMESTAMP,
  operator        varchar(50) NOT NULL,
  operator_name   varchar(100),
  description     varchar(500),
  snapshot_data   text NOT NULL,
  change_log      varchar(1000),
  CONSTRAINT uk_w3d_snap_version UNIQUE (warehouse_id, version)
);
CREATE INDEX IF NOT EXISTS idx_w3d_snap_wh ON w3d_layout_snapshot (warehouse_id);
COMMENT ON TABLE w3d_layout_snapshot IS '数字孪生-布局快照';