package com.vypeensoft.routehelper.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.vypeensoft.routehelper.R;
import com.vypeensoft.routehelper.models.Point;
import com.vypeensoft.routehelper.models.PointWithDistance;
import com.vypeensoft.routehelper.utils.*;
import java.util.List;
import java.util.Iterator;
import java.util.Collections;
import java.util.Comparator;
import android.text.TextUtils;
import android.location.Location;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.graphics.Color;

public class PointAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_POINT = 0;
    private static final int TYPE_CURRENT_LOCATION = 1;

    private static final double JITTER_THRESHOLD_METERS = 5.0;
    private static final double MIN_MOVEMENT_METERS = 2.0;

    private List<Point> pointsOnRoute; // This is the displayed list
    private List<PointWithDistance> currentDistances = new java.util.ArrayList<>();
    private List<PointWithDistance> previousDistances = new java.util.ArrayList<>();
    
    private OnPointClickListener listener;
    private Location currentLocation;
    private int userRowPosition = 0;

    public interface OnPointClickListener {
        void onPointClick(int position);
    }

    public PointAdapter(List<Point> pointsOnRoute, OnPointClickListener listener) {
        this.pointsOnRoute = pointsOnRoute;
        this.listener = listener;
        initializeLinksIfMissing();
    }

    public void updateCurrentLocation(Location currLocation) {
        if (currentLocation != null && currLocation != null) {
            float distanceMoved = currentLocation.distanceTo(currLocation);
            if (distanceMoved < MIN_MOVEMENT_METERS) {
                //System.out.println("-------------- small distance covered  --------------");
                return;
            }
        }

        this.currentLocation = currLocation;
        if (pointsOnRoute == null || pointsOnRoute.isEmpty()) {
            notifyDataSetChanged();
            System.out.println("return another reason");
            return;
        }
        System.out.println("---------------------------- PointAdapter.updateCurrentLocation(Location currLocation) ----------------------------");

        // 1. Snapshot previous distances
        previousDistances = new java.util.ArrayList<>(currentDistances);
        
        //LogUtil.printList(previousDistances, "PointAdapter.previousDistances");
        // 2. Calculate new current distances
        currentDistances = new java.util.ArrayList<>();
        for (Point onePointOnRoute : pointsOnRoute) {
            if (onePointOnRoute.getName().equals("----Current User----")) continue;
            float[] results = new float[1];
            Location.distanceBetween(currLocation.getLatitude(), currLocation.getLongitude(), onePointOnRoute.getLatitude(), onePointOnRoute.getLongitude(), results);
            currentDistances.add(new PointWithDistance(onePointOnRoute, results[0]));
        }
        //LogUtil.printList(currentDistances, "PointAdapter.currentDistances");

        // 3. Categorize pointsOnRoute based on movement/doubly linked list
        java.util.List<Point> receding    = new java.util.ArrayList<>();
        java.util.List<Point> approaching = new java.util.ArrayList<>();
        java.util.List<Point> neutral     = new java.util.ArrayList<>();

        Point currUserPoint = createCurrentUserPoint(currLocation.getLatitude(), currLocation.getLongitude());
        addCurrentUserPoint(currUserPoint);

        List<Point> orderedActive = getOriginalRouteOrder(pointsOnRoute);
        int M = orderedActive.size();

        if (M == 1) {
            receding.add(orderedActive.get(0));
        } else if (M >= 2) {
            int minIndex = 0;
            double minAvgDist = Double.MAX_VALUE;
            for (int i = 0; i < M - 1; i++) {
                Point pA = orderedActive.get(i);
                Point pB = orderedActive.get(i + 1);
                double distA = getDistanceForPoint(pA, currentDistances);
                double distB = getDistanceForPoint(pB, currentDistances);
                if (distA != -1 && distB != -1) {
                    double avg = (distA + distB) / 2.0;
                    if (avg < minAvgDist) {
                        minAvgDist = avg;
                        minIndex = i;
                    }
                }
            }

            // Receding: from 0 up to minIndex
            for (int i = 0; i <= minIndex; i++) {
                receding.add(orderedActive.get(i));
            }
            // Approaching: from minIndex + 1 to M - 1
            for (int i = minIndex + 1; i < M; i++) {
                approaching.add(orderedActive.get(i));
            }
        }

        // 4. Re-order the display list: Receding -> Marker -> Approaching
        // Sort receding by distance descending (furthest first)
        Collections.sort(receding, (p1, p2) -> Double.compare(getDistanceForPoint(p2, currentDistances), getDistanceForPoint(p1, currentDistances)));
        // Sort approaching by distance ascending (closest first)
        Collections.sort(approaching, (p1, p2) -> Double.compare(getDistanceForPoint(p1, currentDistances), getDistanceForPoint(p2, currentDistances)));

        neutral.add(currUserPoint);

        java.util.List<Point> newOrder = new java.util.ArrayList<>();
        newOrder.addAll(receding);
        newOrder.addAll(neutral);
        
        this.userRowPosition = newOrder.size();
        newOrder.addAll(approaching);
        LogUtil.printList(newOrder, "PointAdapter.newOrder:"+newOrder.size());

        this.pointsOnRoute = newOrder;
        notifyDataSetChanged();
    }

    private double getDistanceForPoint(Point p, List<PointWithDistance> list) {
        for (PointWithDistance pwd : list) {
            if (pwd.getPointId().equals(p.getPointId())) {
                return pwd.getDistance();
            }
        }
        return -1;
    }

    public void setUserRowPosition(int position) {
        if (pointsOnRoute != null && position >= 0 && position <= pointsOnRoute.size()) {
            this.userRowPosition = position;
            notifyDataSetChanged();
        }
    }

    @Override
    public int getItemViewType(int position) {
        return (position == userRowPosition) ? TYPE_CURRENT_LOCATION : TYPE_POINT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_CURRENT_LOCATION) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_current_location, parent, false);
            return new CurrentLocationViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_point, parent, false);
            return new PointViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof CurrentLocationViewHolder) {
            bindCurrentLocation((CurrentLocationViewHolder) holder);
        } else if (holder instanceof PointViewHolder) {
            int pointIndex = (position > userRowPosition) ? position - 1 : position;
            bindPoint((PointViewHolder) holder, pointIndex);
        }
    }

    private void bindCurrentLocation(CurrentLocationViewHolder holder) {
        if (currentLocation != null) {
            holder.textViewStatus.setText("Tracking - " + pointsOnRoute.size() + " points on Route");
        } else {
            holder.textViewStatus.setText("Waiting for GPS...");
        }
    }

    private void bindPoint(PointViewHolder holder, int position) {
        if (pointsOnRoute == null || position < 0 || position >= pointsOnRoute.size()) return;
        Point point = pointsOnRoute.get(position);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        
        int startName = builder.length();
        builder.append(point.getName());
        builder.setSpan(new StyleSpan(Typeface.BOLD), startName, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        double currentDist = getDistanceForPoint(point, currentDistances);
        double previousDist = getDistanceForPoint(point, previousDistances);

        if (currentDist != -1) {
            String distanceStr;
            if (currentDist < 1000) {
                distanceStr = String.format(" (%.0f m)", currentDist);
            } else {
                distanceStr = String.format(" (%.1f km)", currentDist / 1000f);
            }
            
            int startDist = builder.length();
            builder.append(distanceStr);
            builder.setSpan(new StyleSpan(Typeface.BOLD), startDist, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            int color = Color.parseColor("#4EC3D0"); // Turquoise
            if (previousDist != -1) {
                double diff = currentDist - previousDist;
                if (diff > JITTER_THRESHOLD_METERS) {
                    color = Color.parseColor("#E57373"); // Red
                } else if (diff < -JITTER_THRESHOLD_METERS) {
                    color = Color.parseColor("#81C784"); // Green
                }
            }
            builder.setSpan(new ForegroundColorSpan(color), startDist, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        holder.textViewPointName.setText(builder);

        List<String> types = point.getTypes();
        if (types != null && !types.isEmpty()) {
            holder.textViewPointTypes.setVisibility(View.VISIBLE);
            holder.textViewPointTypes.setText(TextUtils.join(", ", types));
        } else {
            holder.textViewPointTypes.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPointClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return (pointsOnRoute != null) ? pointsOnRoute.size() + 1 : 1;
    }

    public void updateData(List<Point> pointsOnRoute) {
        this.pointsOnRoute = pointsOnRoute;
        currentDistances.clear();
        previousDistances.clear();
        initializeLinksIfMissing();
        notifyDataSetChanged();
    }

    private void initializeLinksIfMissing() {
        if (pointsOnRoute == null) return;
        List<Point> active = new java.util.ArrayList<>();
        for (Point p : pointsOnRoute) {
            if (p != null && !p.getName().equals("----Current User----")) {
                active.add(p);
            }
        }
        
        // If pointers are already set, we don't overwrite them
        for (Point p : active) {
            if (p.getParentId() != null || p.getChildId() != null) {
                return;
            }
        }
        
        // Populate parentId and childId using original order
        for (int i = 0; i < active.size(); i++) {
            Point current = active.get(i);
            if (i > 0) {
                current.setParentId(active.get(i - 1).getPointId());
            } else {
                current.setParentId(null);
            }
            if (i < active.size() - 1) {
                current.setChildId(active.get(i + 1).getPointId());
            } else {
                current.setChildId(null);
            }
        }
    }

    private List<Point> getOriginalRouteOrder(List<Point> points) {
        List<Point> active = new java.util.ArrayList<>();
        for (Point p : points) {
            if (p != null && !p.getName().equals("----Current User----")) {
                active.add(p);
            }
        }
        if (active.isEmpty()) return active;

        // Find the head (no parentId)
        Point head = null;
        for (Point p : active) {
            if (p.getParentId() == null || p.getParentId().trim().isEmpty()) {
                head = p;
                break;
            }
        }
        
        if (head == null) {
            head = active.get(0);
        }

        List<Point> ordered = new java.util.ArrayList<>();
        Point current = head;
        while (current != null) {
            ordered.add(current);
            String nextId = current.getChildId();
            if (nextId == null || nextId.trim().isEmpty()) {
                break;
            }
            Point next = null;
            for (Point p : active) {
                if (nextId.equals(p.getPointId())) {
                    next = p;
                    break;
                }
            }
            if (next == null || ordered.contains(next)) {
                break;
            }
            current = next;
        }

        if (ordered.size() < active.size()) {
            for (Point p : active) {
                if (!ordered.contains(p)) {
                    ordered.add(p);
                }
            }
        }
        return ordered;
    }

    static class PointViewHolder extends RecyclerView.ViewHolder {
        TextView textViewPointName, textViewPointLocation, textViewPointTimestamp, textViewPointTypes;

        PointViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewPointName = itemView.findViewById(R.id.textViewPointName);
            textViewPointLocation = itemView.findViewById(R.id.textViewPointLocation);
            textViewPointTimestamp = itemView.findViewById(R.id.textViewPointTimestamp);
            textViewPointTypes = itemView.findViewById(R.id.textViewPointTypes);
        }
    }

    static class CurrentLocationViewHolder extends RecyclerView.ViewHolder {
        TextView textViewStatus;

        CurrentLocationViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewStatus = itemView.findViewById(R.id.textViewCurrentStatus);
        }
    }
    private Point createCurrentUserPoint(double lat, double lon) {
    	Point p = new Point("----Current User----", lat, lon, "tu", null);
        return p;
    }
    private void addCurrentUserPoint(Point p) {
        Iterator<Point> iterator = this.pointsOnRoute.iterator();
        while (iterator.hasNext()) {
            Point p1= iterator.next();
            if(p1.getName().equals("----Current User----")) {
    			iterator.remove();
    		}
    	}
		this.pointsOnRoute.add(p);

    }
}
