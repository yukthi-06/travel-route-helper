package com.vypeensoft.routehelper.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Point {
    private String pointId;
    private String name;
    private double latitude;
    private double longitude;
    private String timestamp;
    private List<String> types;

    private boolean deleted = false;
    private String parentId;
    private String childId;

    public Point(String name, double latitude, double longitude, String timestamp, List<String> types) {
        this(UUID.randomUUID().toString(), name, latitude, longitude, timestamp, types);
    }

    public Point(String pointId, String name, double latitude, double longitude, String timestamp, List<String> types) {
        this.pointId = pointId != null ? pointId : UUID.randomUUID().toString();
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.types = types != null ? types : new ArrayList<>();
    }

    public String getPointId() {
        if (pointId == null) {
            pointId = UUID.randomUUID().toString();
        }
        return pointId;
    }
    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getTimestamp() { return timestamp; }
    public List<String> getTypes() {
        if (types == null) types = new ArrayList<>();
        return types;
    }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getChildId() { return childId; }
    public void setChildId(String childId) { this.childId = childId; }

    @Override
    public String toString() {
        return "Point{" +
                "name='" + name + '\'' +
                ", lat=" + latitude +
                ", lon=" + longitude +
                ", time='" + timestamp + '\'' +
                '}';
    }
}
