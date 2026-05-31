package com.vypeensoft.routehelper;

import static org.junit.Assert.*;

import com.vypeensoft.routehelper.models.Point;
import com.vypeensoft.routehelper.models.Route;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
public class RouteLinkedListTest {

    @Test
    public void testUpdateLinkedPointers() {
        Route route = new Route("Test Route", "2026-05-31T12:00:00Z");

        Point p1 = new Point("Point 1", 12.0, 77.0, "t1", new ArrayList<>());
        Point p2 = new Point("Point 2", 12.1, 77.1, "t2", new ArrayList<>());
        Point p3 = new Point("Point 3", 12.2, 77.2, "t3", new ArrayList<>());

        route.addPoint(p1);
        route.addPoint(p2);
        route.addPoint(p3);

        // Initially parentId and childId should be null
        assertNull(p1.getParentId());
        assertNull(p1.getChildId());
        assertNull(p2.getParentId());
        assertNull(p2.getChildId());
        assertNull(p3.getParentId());
        assertNull(p3.getChildId());

        // Update pointers
        boolean changed = route.updateLinkedPointers();
        assertTrue(changed);

        // Verify pointers
        assertNull(p1.getParentId());
        assertEquals(p2.getPointId(), p1.getChildId());

        assertEquals(p1.getPointId(), p2.getParentId());
        assertEquals(p3.getPointId(), p2.getChildId());

        assertEquals(p2.getPointId(), p3.getParentId());
        assertNull(p3.getChildId());

        // Run update again, should not change
        assertFalse(route.updateLinkedPointers());

        // Test with deleted point in middle
        p2.setDeleted(true);
        assertTrue(route.updateLinkedPointers());

        // Now p1 should link directly to p3, and p2 pointers should be null
        assertNull(p1.getParentId());
        assertEquals(p3.getPointId(), p1.getChildId());

        assertNull(p2.getParentId());
        assertNull(p2.getChildId());

        assertEquals(p1.getPointId(), p3.getParentId());
        assertNull(p3.getChildId());
    }
}
