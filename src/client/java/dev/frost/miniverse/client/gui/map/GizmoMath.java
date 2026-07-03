package dev.frost.miniverse.client.gui.map;

import net.minecraft.client.MinecraftClient;
import org.joml.Intersectiond;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector2d;
import org.joml.Vector4f;

public class GizmoMath {

    public static Vector3d unprojectMouseToRay(MinecraftClient client, double mouseX, double mouseY) {
        if (MapEditorRenderIntegration.lastProjMatrix == null || MapEditorRenderIntegration.lastModelViewMatrix == null) return null;

        int w = client.getWindow().getFramebufferWidth();
        int h = client.getWindow().getFramebufferHeight();
        double scale = client.getWindow().getScaleFactor();

        float ndcX = (float) ((mouseX * scale / w) * 2.0 - 1.0);
        float ndcY = (float) (1.0 - (mouseY * scale / h) * 2.0);

        Matrix4f mv = new Matrix4f(MapEditorRenderIntegration.lastModelViewMatrix);
        Matrix4f p = new Matrix4f(MapEditorRenderIntegration.lastProjMatrix);
        Matrix4f inv = p.mul(mv).invert();

        Vector4f near = new Vector4f(ndcX, ndcY, -1.0f, 1.0f);
        inv.transform(near);
        Vector4f far = new Vector4f(ndcX, ndcY, 1.0f, 1.0f);
        inv.transform(far);

        if (near.w != 0.0f) {
            near.x /= near.w; near.y /= near.w; near.z /= near.w;
        }
        if (far.w != 0.0f) {
            far.x /= far.w; far.y /= far.w; far.z /= far.w;
        }

        Vector3d dir = new Vector3d(far.x - near.x, far.y - near.y, far.z - near.z);
        return dir.normalize();
    }

    public static double distanceToSegment(Vector3d rayOrigin, Vector3d rayDir, Vector3d segStart, Vector3d segEnd) {
        Vector3d ptRay = new Vector3d();
        Vector3d ptSeg = new Vector3d();
        double distance = Intersectiond.findClosestPointsLineSegments(
                rayOrigin.x, rayOrigin.y, rayOrigin.z,
                rayOrigin.x + rayDir.x * 1000.0, rayOrigin.y + rayDir.y * 1000.0, rayOrigin.z + rayDir.z * 1000.0,
                segStart.x, segStart.y, segStart.z,
                segEnd.x, segEnd.y, segEnd.z,
                ptRay, ptSeg
        );

        return ptRay.distance(ptSeg);
    }

    public static Vector3d getClosestPointOnAxis(Vector3d rayOrigin, Vector3d rayDir, Vector3d axisOrigin, Vector3d axisDir) {
        Vector3d ptRay = new Vector3d();
        Vector3d ptSeg = new Vector3d();
        double distance = Intersectiond.findClosestPointsLineSegments(
                rayOrigin.x, rayOrigin.y, rayOrigin.z,
                rayOrigin.x + rayDir.x * 1000.0, rayOrigin.y + rayDir.y * 1000.0, rayOrigin.z + rayDir.z * 1000.0,
                axisOrigin.x - axisDir.x * 1000.0, axisOrigin.y - axisDir.y * 1000.0, axisOrigin.z - axisDir.z * 1000.0,
                axisOrigin.x + axisDir.x * 1000.0, axisOrigin.y + axisDir.y * 1000.0, axisOrigin.z + axisDir.z * 1000.0,
                ptRay, ptSeg
        );

        return ptSeg;
    }

    public static Vector3d intersectRayPlane(Vector3d rayOrigin, Vector3d rayDir, Vector3d planePoint, Vector3d planeNormal) {
        Vector3d intersection = new Vector3d();
        double d = -(planeNormal.x * planePoint.x + planeNormal.y * planePoint.y + planeNormal.z * planePoint.z);
        if (Intersectiond.intersectLineSegmentPlane(
                rayOrigin.x, rayOrigin.y, rayOrigin.z,
                rayOrigin.x + rayDir.x * 2000.0, rayOrigin.y + rayDir.y * 2000.0, rayOrigin.z + rayDir.z * 2000.0,
                planeNormal.x, planeNormal.y, planeNormal.z, d, intersection
        )) {
            return intersection;
        }
        return null;
    }

    public record ProjectedPoint(Vector2d screen, Vector4f clip) {
        public boolean inFront() {
            return clip.w > 1.0E-5f;
        }

        public boolean insideFrustum() {
            return inFront()
                    && clip.x >= -clip.w && clip.x <= clip.w
                    && clip.y >= -clip.w && clip.y <= clip.w
                    && clip.z >= -clip.w && clip.z <= clip.w;
        }
    }

    public static ProjectedPoint project3D(MinecraftClient client, double x, double y, double z) {
        if (MapEditorRenderIntegration.lastProjMatrix == null || MapEditorRenderIntegration.lastModelViewMatrix == null) return null;

        net.minecraft.client.render.Camera camera = client.gameRenderer.getCamera();
        net.minecraft.util.math.Vec3d camPos = camera.getPos();

        Vector4f clip = new Vector4f((float)(x - camPos.x), (float)(y - camPos.y), (float)(z - camPos.z), 1.0f);
        new Matrix4f(MapEditorRenderIntegration.lastModelViewMatrix).transform(clip);
        new Matrix4f(MapEditorRenderIntegration.lastProjMatrix).transform(clip);

        if (clip.w <= 1.0E-5f) {
            return null;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;

        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();

        double mouseX = (ndcX + 1.0) * 0.5 * w;
        double mouseY = (1.0 - ndcY) * 0.5 * h;

        return new ProjectedPoint(new Vector2d(mouseX, mouseY), clip);
    }

    public static org.joml.Vector2d project3DTo2D(MinecraftClient client, double x, double y, double z) {
        ProjectedPoint projected = project3D(client, x, y, z);
        return projected != null && projected.insideFrustum() ? projected.screen() : null;
    }
}
