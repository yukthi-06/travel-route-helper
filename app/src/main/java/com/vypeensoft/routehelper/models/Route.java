package com.vypeensoft.routehelper.models;

import java.util.ArrayList;
import java.util.List;

public class Route {
    private String routeName;
    private String createdAt;
    private List<Point> points;

    public Route(String routeName, String createdAt) {
        this.routeName = routeName;
        this.createdAt = createdAt;
        this.points = new ArrayList<>();
    }

    public String getRouteName() { return routeName; }
    public void setRouteName(String routeName) { this.routeName = routeName; }
    public String getCreatedAt() { return createdAt; }
    public List<Point> getPoints() { return points; }

    public void addPoint(Point point) {
        points.add(point);
    }

    public boolean updateLinkedPointers() {
        boolean changed = false;
        if (points == null) return false;

        List<Point> activePoints = new ArrayList<>();
        for (Point p : points) {
            if (!p.isDeleted()) {
                activePoints.add(p);
            } else {
                if (p.getParentId() != null) {
                    p.setParentId(null);
                    changed = true;
                }
                if (p.getChildId() != null) {
                    p.setChildId(null);
                    changed = true;
                }
            }
        }

        for (int i = 0; i < activePoints.size(); i++) {
            Point current = activePoints.get(i);
            String expectedParentId = (i > 0) ? activePoints.get(i - 1).getPointId() : null;
            String expectedChildId = (i < activePoints.size() - 1) ? activePoints.get(i + 1).getPointId() : null;

            if ((expectedParentId == null && current.getParentId() != null) ||
                (expectedParentId != null && !expectedParentId.equals(current.getParentId()))) {
                current.setParentId(expectedParentId);
                changed = true;
            }

            if ((expectedChildId == null && current.getChildId() != null) ||
                (expectedChildId != null && !expectedChildId.equals(current.getChildId()))) {
                current.setChildId(expectedChildId);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public String toString() {
        return "Route{" +
                "name='" + routeName + '\'' +
                ", created='" + createdAt + '\'' +
                ", points=" + points +
                '}';
    }
}
