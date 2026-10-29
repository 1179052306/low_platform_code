package com.api.lowcode.w3d;

import lombok.Data;

@Data
public class W3DLocation {
    private String id;
    private String rackId;
    private String warehouseId;
    private String code;
    private Integer level;
    private Integer bay;
    private Double x;
    private Double y;
    private Double z;
    private Double sizeX;
    private Double sizeY;
    private Double sizeZ;
    private String status;
    private Double utilization;
    private String productCode;
    private String updateTime;
}