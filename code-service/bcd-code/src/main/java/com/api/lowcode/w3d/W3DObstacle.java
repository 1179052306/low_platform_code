package com.api.lowcode.w3d;

import lombok.Data;

@Data
public class W3DObstacle {
    private String id;
    private String warehouseId;
    private String obstacleType;
    private Double x;
    private Double z;
    private Double length;
    private Double width;
    private Double height;
    private Double rotation;
}