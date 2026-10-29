package com.api.lowcode.w3d;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class W3DWarehouse {
    private String id;
    private String code;
    private String name;
    private Double length;
    private Double width;
    private Double height;
    private String status;
    private String description;
    private List<W3DArea> areas = new ArrayList<>();
    private List<W3DObstacle> obstacles = new ArrayList<>();
    private List<W3DChannel> channels = new ArrayList<>();
}