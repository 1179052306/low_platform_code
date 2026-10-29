package com.api.lowcode.w3d;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class W3DRack {
    private String id;
    private String areaId;
    private String warehouseId;
    private String code;
    private Double x;
    private Double z;
    private Double length;
    private Double width;
    private Integer levels;
    private Integer baysPerLevel;
    private String orientation;
    private List<W3DLocation> locations = new ArrayList<>();
}