package com.api.lowcode.w3d;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class W3DArea {
    private String id;
    private String warehouseId;
    private String code;
    private String name;
    private String areaType;
    private Double x;
    private Double z;
    private Double length;
    private Double width;
    private Integer sortOrder;
    private List<W3DRack> racks = new ArrayList<>();
}