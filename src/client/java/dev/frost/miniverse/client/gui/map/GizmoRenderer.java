package dev.frost.miniverse.client.gui.map;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

public class GizmoRenderer {

    public static void drawCylinder(MatrixStack matrices, Tessellator tessellator, double tx, double ty, double tz, double length, double radius, int segments, int color, double signX, double signY, double signZ) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;
        if (a == 0.0f) a = 1.0f;

        for (int i = 0; i < segments; i++) {
            double angle1 = (i * 2.0 * Math.PI) / segments;
            double angle2 = ((i + 1) * 2.0 * Math.PI) / segments;

            double c1 = Math.cos(angle1) * radius;
            double s1 = Math.sin(angle1) * radius;
            double c2 = Math.cos(angle2) * radius;
            double s2 = Math.sin(angle2) * radius;

            // Simple directional shading based on angle
            float shade1 = 0.5f + 0.5f * (float)Math.sin(angle1);
            float shade2 = 0.5f + 0.5f * (float)Math.sin(angle2);

            float r1 = r * shade1; float g1 = g * shade1; float b1 = b * shade1;
            float r2 = r * shade2; float g2 = g * shade2; float b2 = b * shade2;

            if (signX != 0) { // X Axis
                float endX = (float) (tx + length * signX);
                buffer.vertex(matrix, (float)tx, (float)(ty + c1), (float)(tz + s1)).color(r1, g1, b1, a);
                buffer.vertex(matrix, endX, (float)(ty + c1), (float)(tz + s1)).color(r1, g1, b1, a);
                buffer.vertex(matrix, endX, (float)(ty + c2), (float)(tz + s2)).color(r2, g2, b2, a);
                buffer.vertex(matrix, (float)tx, (float)(ty + c2), (float)(tz + s2)).color(r2, g2, b2, a);
            } else if (signY != 0) { // Y Axis
                float endY = (float) (ty + length * signY);
                buffer.vertex(matrix, (float)(tx + c1), (float)ty, (float)(tz + s1)).color(r1, g1, b1, a);
                buffer.vertex(matrix, (float)(tx + c1), endY, (float)(tz + s1)).color(r1, g1, b1, a);
                buffer.vertex(matrix, (float)(tx + c2), endY, (float)(tz + s2)).color(r2, g2, b2, a);
                buffer.vertex(matrix, (float)(tx + c2), (float)ty, (float)(tz + s2)).color(r2, g2, b2, a);
            } else if (signZ != 0) { // Z Axis
                float endZ = (float) (tz + length * signZ);
                buffer.vertex(matrix, (float)(tx + c1), (float)(ty + s1), (float)tz).color(r1, g1, b1, a);
                buffer.vertex(matrix, (float)(tx + c1), (float)(ty + s1), endZ).color(r1, g1, b1, a);
                buffer.vertex(matrix, (float)(tx + c2), (float)(ty + s2), endZ).color(r2, g2, b2, a);
                buffer.vertex(matrix, (float)(tx + c2), (float)(ty + s2), (float)tz).color(r2, g2, b2, a);
            }
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    public static void drawCone(MatrixStack matrices, Tessellator tessellator, double tx, double ty, double tz, double length, double radius, int segments, int color, double signX, double signY, double signZ) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;
        if (a == 0.0f) a = 1.0f;

        for (int i = 0; i < segments; i++) {
            double angle1 = (i * 2.0 * Math.PI) / segments;
            double angle2 = ((i + 1) * 2.0 * Math.PI) / segments;

            double c1 = Math.cos(angle1) * radius;
            double s1 = Math.sin(angle1) * radius;
            double c2 = Math.cos(angle2) * radius;
            double s2 = Math.sin(angle2) * radius;

            float shade1 = 0.5f + 0.5f * (float)Math.sin(angle1);
            float shade2 = 0.5f + 0.5f * (float)Math.sin(angle2);

            float r1 = r * shade1; float g1 = g * shade1; float b1 = b * shade1;
            float r2 = r * shade2; float g2 = g * shade2; float b2 = b * shade2;

            if (signX != 0) { // X Axis
                float endX = (float) (tx + length * signX);
                buffer.vertex(matrix, endX, (float)ty, (float)tz).color(r, g, b, a); // Tip
                buffer.vertex(matrix, (float)tx, (float)(ty + c1), (float)(tz + s1)).color(r1, g1, b1, a);
                buffer.vertex(matrix, (float)tx, (float)(ty + c2), (float)(tz + s2)).color(r2, g2, b2, a);
            } else if (signY != 0) { // Y Axis
                float endY = (float) (ty + length * signY);
                buffer.vertex(matrix, (float)tx, endY, (float)tz).color(r, g, b, a); // Tip
                buffer.vertex(matrix, (float)(tx + c1), (float)ty, (float)(tz + s1)).color(r1, g1, b1, a);
                buffer.vertex(matrix, (float)(tx + c2), (float)ty, (float)(tz + s2)).color(r2, g2, b2, a);
            } else if (signZ != 0) { // Z Axis
                float endZ = (float) (tz + length * signZ);
                buffer.vertex(matrix, (float)tx, (float)ty, endZ).color(r, g, b, a); // Tip
                buffer.vertex(matrix, (float)(tx + c1), (float)(ty + s1), (float)tz).color(r1, g1, b1, a);
                buffer.vertex(matrix, (float)(tx + c2), (float)(ty + s2), (float)tz).color(r2, g2, b2, a);
            }
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    public static void drawSolidCube(MatrixStack matrices, Tessellator tessellator, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color) {
        // Simple AABB drawing with proper shading...
        // We can port over our previous drawAABB code here.
        // For brevity, we will implement it fully later, but this serves as the scaffold.
    }
}
