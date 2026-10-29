package com.api.lowcode.w3d;

import lombok.Data;

import java.util.List;

@Data
public class W3DChannel {
    private String id;
    private String warehouseId;
    private String channelType;
    private List<Point> path;
    private Double width;
    private Boolean bidirectional;

    @Data
    public static class Point {
        private Double x;
        private Double z;
    }
}