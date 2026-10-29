package com.api.lowcode.w3d;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class W3DLayoutService {

    @Resource
    private SqlEngineJson helper;

    private String genId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 获取完整仓库布局（仓库→库区→货架→库位 + 障碍物 + 通道）
     */
    public W3DWarehouse getLayout(String warehouseId) throws Exception {
        JSONArray whRows = helper.queryAndMap("w3d_warehouse",
                new String[]{"id", "code", "name", "length", "width", "height", "status", "description"},
                helper.eq("id", warehouseId), null, false,
                new String[][]{
                        {"id", "id"}, {"code", "code"}, {"name", "name"},
                        {"length", "length"}, {"width", "width"}, {"height", "height"},
                        {"status", "status"}, {"description", "description"}
                });
        if (whRows == null || whRows.isEmpty()) {
            throw new IllegalArgumentException("仓库不存在: " + warehouseId);
        }
        JSONObject whJson = whRows.getJSONObject(0);
        W3DWarehouse wh = whJson.toJavaObject(W3DWarehouse.class);

        JSONArray areaRows = helper.queryAndMap("w3d_area",
                new String[]{"id", "warehouse_id", "code", "name", "area_type", "pos_x", "pos_z", "length", "width", "sort_order"},
                helper.eq("warehouse_id", warehouseId), "sort_order", true,
                new String[][]{
                        {"id", "id"}, {"warehouse_id", "warehouseId"}, {"code", "code"}, {"name", "name"},
                        {"area_type", "areaType"}, {"pos_x", "x"}, {"pos_z", "z"},
                        {"length", "length"}, {"width", "width"}, {"sort_order", "sortOrder"}
                });
        for (int i = 0; i < areaRows.size(); i++) {
            JSONObject areaJson = areaRows.getJSONObject(i);
            W3DArea area = areaJson.toJavaObject(W3DArea.class);
            area.setRacks(loadRacks(area.getId(), warehouseId));
            wh.getAreas().add(area);
        }

        JSONArray obsRows = helper.queryAndMap("w3d_obstacle",
                new String[]{"id", "warehouse_id", "obstacle_type", "pos_x", "pos_z", "length", "width", "height", "rotation"},
                helper.eq("warehouse_id", warehouseId), null, false,
                new String[][]{
                        {"id", "id"}, {"warehouse_id", "warehouseId"}, {"obstacle_type", "obstacleType"},
                        {"pos_x", "x"}, {"pos_z", "z"}, {"length", "length"}, {"width", "width"},
                        {"height", "height"}, {"rotation", "rotation"}
                });
        for (int i = 0; i < obsRows.size(); i++) {
            wh.getObstacles().add(obsRows.getJSONObject(i).toJavaObject(W3DObstacle.class));
        }

        JSONArray chRows = helper.queryAndMap("w3d_channel",
                new String[]{"id", "warehouse_id", "channel_type", "path_json", "width", "bidirectional"},
                helper.eq("warehouse_id", warehouseId), null, false,
                new String[][]{
                        {"id", "id"}, {"warehouse_id", "warehouseId"}, {"channel_type", "channelType"},
                        {"path_json", "pathJson"}, {"width", "width"}, {"bidirectional", "bidirectional"}
                });
        for (int i = 0; i < chRows.size(); i++) {
            JSONObject chJson = chRows.getJSONObject(i);
            W3DChannel ch = new W3DChannel();
            ch.setId(chJson.getString("id"));
            ch.setWarehouseId(chJson.getString("warehouseId"));
            ch.setChannelType(chJson.getString("channelType"));
            ch.setWidth(chJson.getDouble("width"));
            ch.setBidirectional(chJson.getBoolean("bidirectional"));
            String pathJson = chJson.getString("pathJson");
            if (pathJson != null && !pathJson.isEmpty()) {
                ch.setPath(JSON.parseArray(pathJson).toJavaList(W3DChannel.Point.class));
            }
            wh.getChannels().add(ch);
        }

        return wh;
    }

    private List<W3DRack> loadRacks(String areaId, String warehouseId) throws Exception {
        List<W3DRack> racks = new ArrayList<>();
        JSONArray rackRows = helper.queryAndMap("w3d_rack",
                new String[]{"id", "area_id", "warehouse_id", "code", "pos_x", "pos_z", "length", "width", "levels", "bays_per_level", "orientation"},
                helper.eq("area_id", areaId), null, false,
                new String[][]{
                        {"id", "id"}, {"area_id", "areaId"}, {"warehouse_id", "warehouseId"},
                        {"code", "code"}, {"pos_x", "x"}, {"pos_z", "z"},
                        {"length", "length"}, {"width", "width"}, {"levels", "levels"},
                        {"bays_per_level", "baysPerLevel"}, {"orientation", "orientation"}
                });
        for (int i = 0; i < rackRows.size(); i++) {
            JSONObject rackJson = rackRows.getJSONObject(i);
            W3DRack rack = rackJson.toJavaObject(W3DRack.class);
            rack.setLocations(loadLocations(rack.getId(), warehouseId));
            racks.add(rack);
        }
        return racks;
    }

    private List<W3DLocation> loadLocations(String rackId, String warehouseId) throws Exception {
        List<W3DLocation> locations = new ArrayList<>();
        JSONArray locRows = helper.queryAndMap("w3d_location",
                new String[]{"id", "rack_id", "warehouse_id", "code", "level", "bay",
                        "pos_x", "pos_y", "pos_z", "size_x", "size_y", "size_z",
                        "status", "utilization", "product_code", "update_time"},
                helper.eq("rack_id", rackId), "level,bay", true,
                new String[][]{
                        {"id", "id"}, {"rack_id", "rackId"}, {"warehouse_id", "warehouseId"},
                        {"code", "code"}, {"level", "level"}, {"bay", "bay"},
                        {"pos_x", "x"}, {"pos_y", "y"}, {"pos_z", "z"},
                        {"size_x", "sizeX"}, {"size_y", "sizeY"}, {"size_z", "sizeZ"},
                        {"status", "status"}, {"utilization", "utilization"},
                        {"product_code", "productCode"}, {"update_time", "updateTime"}
                });
        for (int i = 0; i < locRows.size(); i++) {
            locations.add(locRows.getJSONObject(i).toJavaObject(W3DLocation.class));
        }
        return locations;
    }

    /**
     * 保存布局（全量覆盖：先删后插，事务内）
     */
    @Transactional
    public void saveLayout(String warehouseId, JSONObject data, String changeLog, String operator) throws Exception {
            helper.delete("w3d_location", helper.eq("warehouse_id", warehouseId));
            helper.delete("w3d_rack", helper.eq("warehouse_id", warehouseId));
            helper.delete("w3d_area", helper.eq("warehouse_id", warehouseId));
            helper.delete("w3d_obstacle", helper.eq("warehouse_id", warehouseId));
            helper.delete("w3d_channel", helper.eq("warehouse_id", warehouseId));

            JSONObject whJson = data.getJSONObject("data");
            if (whJson == null) whJson = data;

            helper.insert("w3d_warehouse", whJson);

            JSONArray areas = whJson.getJSONArray("areas");
            if (areas != null) {
                for (int i = 0; i < areas.size(); i++) {
                    JSONObject area = areas.getJSONObject(i);
                    if (area.getString("id") == null) area.put("id", genId());
                    area.put("warehouse_id", warehouseId);
                    helper.insert("w3d_area", area);

                    JSONArray racks = area.getJSONArray("racks");
                    if (racks != null) {
                        for (int j = 0; j < racks.size(); j++) {
                            JSONObject rack = racks.getJSONObject(j);
                            if (rack.getString("id") == null) rack.put("id", genId());
                            rack.put("area_id", area.getString("id"));
                            rack.put("warehouse_id", warehouseId);
                            helper.insert("w3d_rack", rack);

                            JSONArray locs = rack.getJSONArray("locations");
                            if (locs != null) {
                                for (int k = 0; k < locs.size(); k++) {
                                    JSONObject loc = locs.getJSONObject(k);
                                    if (loc.getString("id") == null) loc.put("id", genId());
                                    loc.put("rack_id", rack.getString("id"));
                                    loc.put("warehouse_id", warehouseId);
                                    helper.insert("w3d_location", loc);
                                }
                            }
                        }
                    }
                }
            }

            JSONArray obstacles = whJson.getJSONArray("obstacles");
            if (obstacles != null) {
                for (int i = 0; i < obstacles.size(); i++) {
                    JSONObject obs = obstacles.getJSONObject(i);
                    if (obs.getString("id") == null) obs.put("id", genId());
                    obs.put("warehouse_id", warehouseId);
                    helper.insert("w3d_obstacle", obs);
                }
            }

            JSONArray channels = whJson.getJSONArray("channels");
            if (channels != null) {
                for (int i = 0; i < channels.size(); i++) {
                    JSONObject ch = channels.getJSONObject(i);
                    if (ch.getString("id") == null) ch.put("id", genId());
                    ch.put("warehouse_id", warehouseId);
                    JSONArray path = ch.getJSONArray("path");
                    if (path != null) ch.put("path_json", path.toJSONString());
                    ch.remove("path");
                    helper.insert("w3d_channel", ch);
                }
            }

            createSnapshot(warehouseId, changeLog, operator, whJson);

    }

    /**
     * 创建版本快照
     */
    public String createSnapshot(String warehouseId, String description, String operator, JSONObject layoutData) throws Exception {
        String snapId = genId();
        JSONArray verRows = helper.queryAndMap("w3d_layout_snapshot",
                new String[]{"version"},
                helper.eq("warehouse_id", warehouseId), "version", false,
                new String[][]{{"version", "version"}});
        int maxVer = 0;
        if (verRows != null && !verRows.isEmpty()) {
            for (int i = 0; i < verRows.size(); i++) {
                int v = verRows.getJSONObject(i).getIntValue("version");
                if (v > maxVer) maxVer = v;
            }
        }

        JSONObject snap = new JSONObject();
        snap.put("id", snapId);
        snap.put("warehouse_id", warehouseId);
        snap.put("version", maxVer + 1);
        snap.put("snapshot_time", Instant.now().toString());
        snap.put("operator", operator);
        snap.put("description", description == null ? "" : description);
        snap.put("snapshot_data", layoutData.toJSONString());
        snap.put("change_log", description == null ? "" : description);
        helper.insert("w3d_layout_snapshot", snap);
        return snapId;
    }

    /**
     * 快照列表
     */
    public JSONArray listSnapshots(String warehouseId) throws Exception {
        return helper.queryAndMap("w3d_layout_snapshot",
                new String[]{"id", "warehouse_id", "version", "snapshot_time", "operator", "operator_name", "description", "change_log"},
                helper.eq("warehouse_id", warehouseId), "version", false,
                new String[][]{
                        {"id", "id"}, {"warehouse_id", "warehouseId"}, {"version", "version"},
                        {"snapshot_time", "snapshotTime"}, {"operator", "operator"},
                        {"operator_name", "operatorName"}, {"description", "description"},
                        {"change_log", "changeLog"}
                });
    }

    /**
     * 回滚到指定快照
     */
    public W3DWarehouse rollback(String warehouseId, String snapshotId, String operator) throws Exception {
        JSONArray snapRows = helper.queryAndMap("w3d_layout_snapshot",
                new String[]{"snapshot_data"},
                helper.eq("id", snapshotId), null, false,
                new String[][]{{"snapshot_data", "snapshotData"}});
        if (snapRows == null || snapRows.isEmpty()) {
            throw new IllegalArgumentException("快照不存在: " + snapshotId);
        }
        String snapData = snapRows.getJSONObject(0).getString("snapshotData");
        JSONObject layoutData = JSON.parseObject(snapData);

        saveLayout(warehouseId, layoutData, "回滚到快照: " + snapshotId, operator);
        return getLayout(warehouseId);
    }
}