package dev.frost.miniverse.client.gui.map;

import net.minecraft.client.MinecraftClient;
import org.joml.Intersectiond;
import org.joml.Matrix4f;
import org.joml.Vector3d;
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

    public static org.joml.Vector2d project3DTo2D(MinecraftClient client, double x, double y, double z) {
        if (MapEditorRenderIntegration.lastProjMatrix == null || MapEditorRenderIntegration.lastModelViewMatrix == null) return null;

        net.minecraft.client.render.Camera camera = client.gameRenderer.getCamera();
        net.minecraft.util.math.Vec3d camPos = camera.getPos();

        Vector4f pos = new Vector4f((float)(x - camPos.x), (float)(y - camPos.y), (float)(z - camPos.z), 1.0f);
        MapEditorRenderIntegration.lastModelViewMatrix.transform(pos);
        MapEditorRenderIntegration.lastProjMatrix.transform(pos);

        if (pos.w <= 0.0f) {
            return null; // Behind camera
        }
        
        pos.x /= pos.w;
        pos.y /= pos.w;

        int w = client.getWindow().getFramebufferWidth();
        int h = client.getWindow().getFramebufferHeight();
        double scale = client.getWindow().getScaleFactor();

        double mouseX = ((pos.x + 1.0) * 0.5 * w) / scale;
        double mouseY = ((1.0 - pos.y) * 0.5 * h) / scale;

        return new org.joml.Vector2d(mouseX, mouseY);
    }
}
