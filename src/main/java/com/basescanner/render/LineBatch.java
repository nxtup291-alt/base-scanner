package com.basescanner.render;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Emits line-list vertices (position, color, normal) relative to the camera.
 * Coordinates are subtracted from the camera position in double precision before
 * being converted to float, so outlines stay precise far from the world origin.
 */
final class LineBatch {
    private final BufferBuilder buffer;
    private final MatrixStack.Entry entry;
    private final Matrix4f matrix;
    private final Vec3d cam;
    private int lines;

    LineBatch(BufferBuilder buffer, MatrixStack.Entry entry, Vec3d cam) {
        this.buffer = buffer;
        this.entry = entry;
        this.matrix = entry.getPositionMatrix();
        this.cam = cam;
    }

    int lineCount() {
        return lines;
    }

    void line(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        float ax = (float) (x1 - cam.x), ay = (float) (y1 - cam.y), az = (float) (z1 - cam.z);
        float bx = (float) (x2 - cam.x), by = (float) (y2 - cam.y), bz = (float) (z2 - cam.z);
        float nx = bx - ax, ny = by - ay, nz = bz - az;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0e-5f) return;
        nx /= len;
        ny /= len;
        nz /= len;
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        buffer.vertex(matrix, ax, ay, az).color(r, g, b, a).normal(entry, nx, ny, nz);
        buffer.vertex(matrix, bx, by, bz).color(r, g, b, a).normal(entry, nx, ny, nz);
        lines++;
    }

    void box(Box b, int argb) {
        box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, argb);
    }

    void box(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        // bottom
        line(x1, y1, z1, x2, y1, z1, argb);
        line(x2, y1, z1, x2, y1, z2, argb);
        line(x2, y1, z2, x1, y1, z2, argb);
        line(x1, y1, z2, x1, y1, z1, argb);
        // top
        line(x1, y2, z1, x2, y2, z1, argb);
        line(x2, y2, z1, x2, y2, z2, argb);
        line(x2, y2, z2, x1, y2, z2, argb);
        line(x1, y2, z2, x1, y2, z1, argb);
        // verticals
        line(x1, y1, z1, x1, y2, z1, argb);
        line(x2, y1, z1, x2, y2, z1, argb);
        line(x2, y1, z2, x2, y2, z2, argb);
        line(x1, y1, z2, x1, y2, z2, argb);
    }

    /** Horizontal rectangle at height y. */
    void rect(double x1, double z1, double x2, double z2, double y, int argb) {
        line(x1, y, z1, x2, y, z1, argb);
        line(x2, y, z1, x2, y, z2, argb);
        line(x2, y, z2, x1, y, z2, argb);
        line(x1, y, z2, x1, y, z1, argb);
    }
}
