package za.co.neroland.neromaps.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProjectionAtlasTest {

    @Test
    void projectsInFrontAndRejectsBehind() {
        Projection p = new Projection();
        p.camera(0, 64, 0, 0f, 0f, 70, 400, 300); // facing south (+z)
        assertTrue(p.project(0, 64, 10));
        assertEquals(200, p.x, 1e-6);
        assertEquals(150, p.y, 1e-6);
        assertFalse(p.project(0, 64, -10), "behind the camera");
        assertTrue(p.project(5, 64, 10));
        assertTrue(p.x < 200, "east is on the left when facing south");
        assertTrue(p.project(0, 70, 10));
        assertTrue(p.y < 150, "up is up");
        p.camera(0, 64, 0, -90f, 0f, 70, 400, 300); // facing east (+x)
        assertTrue(p.project(10, 64, 0));
        assertEquals(200, p.x, 1e-6);
        p.camera(0, 64, 0, 0f, 90f, 70, 400, 300); // looking straight down
        assertTrue(p.project(0, 0, 0.001));
    }

    @Test
    void atlasResolvesIdsWithAGenericFallback() {
        IconAtlas a = IconAtlas.builtin();
        assertTrue(a.count() > 20, "shipped atlas loads");
        assertTrue(a.has("neromaps:waypoint"));
        assertArrayEquals(new int[] {16, 0, 16, 16}, a.uv("neromaps:waypoint"));
        assertArrayEquals(a.uv(IconAtlas.FALLBACK), a.uv("somemod:unknown_icon"));
        assertArrayEquals(a.uv(IconAtlas.FALLBACK), a.uv(null));
    }
}
