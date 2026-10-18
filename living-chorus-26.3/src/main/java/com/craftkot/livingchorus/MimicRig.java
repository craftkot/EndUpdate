package com.craftkot.livingchorus;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Chorus-shaped articulated plant: a fixed vanilla stalk bends toward a
 * waypoint at its own height, then extends in deliberate orthogonal legs.
 *
 * Left/right (X), forward/back (Z), finally up/down (Y).
 * Full-size rigid 16px cubes are translated, never stretched or re-UV-mapped.
 */
public final class MimicRig {
    public record Pose(List<Vec3> joints, Vec3 mouth, double opening, double attackProgress) { }

    private static final double STEP = 0.88;
    private static final int MAX_EXTENSIONS = 96;

    private MimicRig() { }

    /** Stable joint count chosen once at the start; never recalculated mid-bite. */
    public static int neededSegments(List<Vec3> rest, Vec3 target) {
        if (rest.size() < 2) return 0;
        Vec3 tip = rest.get(rest.size() - 1);
        return Math.max(1, Math.min(rest.size() - 1,
            (int)Math.ceil(tip.distanceTo(target) / 0.90) + 1));
    }

    public static Pose evaluateBranch(MimicProfile style, List<Vec3> resting,
                                      Vec3 target, double progress, boolean recovering) {
        return evaluateBranch(style, resting, target, progress, recovering,
            neededSegments(resting, target));
    }

    public static Pose evaluateBranch(MimicProfile style, List<Vec3> resting,
                                      Vec3 target, double progress, boolean recovering,
                                      int affectedSegments) {
        if (resting.size() < 2) {
            Vec3 head = resting.isEmpty() ? target : resting.get(resting.size()-1);
            return new Pose(List.copyOf(resting), head, 0, 0);
        }

        double p = clamp(progress);
        // Important: these phases proceed one after the other.
        // The flower first leans outward at its current height, grows to the
        // target's X, then Z, and ONLY THEN lowers the mouth vertically.
        double bend = recovering
            ? 1.0 - smooth((p - .56) / .44) : smooth(p / .32);
        double extendX = recovering
            ? 1.0 - smooth((p - .43) / .51) : smooth((p - .25) / .29);
        double extendZ = recovering
            ? 1.0 - smooth((p - .22) / .45) : smooth((p - .43) / .27);
        double descend = recovering
            ? 1.0 - smooth(p / .38) : smooth((p - .66) / .30);
        double opening = recovering
            ? 1.0 - smooth((p - .02) / .32) : smooth((p - .81) / .15);

        int last = resting.size() - 1;
        int pivotIndex = last - Math.max(1, Math.min(last, affectedSegments));
        Vec3 pivot = resting.get(pivotIndex);
        Vec3 restingHead = resting.get(last);

        ArrayList<Vec3> points = new ArrayList<>(resting);
        int movable = last - pivotIndex;
        double[] lengths = new double[movable];
        double available = 0;
        for (int i = 0; i < movable; i++) {
            double length = resting.get(pivotIndex+i).distanceTo(resting.get(pivotIndex+i+1));
            lengths[i] = length;
            available += length;
        }

        // The existing branch rotates toward the target's horizontal bearing,
        // *not* diagonally down toward the prey. This creates the overhang.
        Vec3 horizontal = new Vec3(target.x - pivot.x, 0, target.z - pivot.z);
        Vec3 direction = horizontal.lengthSqr() > 1e-8
            ? horizontal.normalize() : new Vec3(0, 1, 0);
        Vec3 turnPoint = pivot.add(direction.scale(
            Math.min(horizontal.length(), available * .98)))
            .add(0, restingHead.y - pivot.y, 0);
        // Stay within the IK chain's available reach, avoiding a snap at
        // the exact moment a target crosses a reach boundary.
        Vec3 fromPivot = turnPoint.subtract(pivot);
        if (fromPivot.length() > available * .995)
            turnPoint = pivot.add(fromPivot.normalize().scale(available * .995));
        Vec3 desired = restingHead.lerp(turnPoint, bend);

        // Fixed-length IK for ORIGINAL chorus blocks, anchored at first branch
        // junction. This is only the initial turning motion; the extension
        // below is expressly axis-aligned.
        ArrayList<Vec3> solved = new ArrayList<>(resting.subList(pivotIndex, last + 1));
        for (int loop = 0; loop < 14; loop++) {
            solved.set(movable, desired);
            for (int i = movable - 1; i >= 0; i--) {
                Vec3 delta = solved.get(i).subtract(solved.get(i+1));
                double d = delta.length();
                if (d > 1e-7) solved.set(i, solved.get(i+1).add(
                    delta.scale(lengths[i] / d)));
            }
            solved.set(0, pivot);
            for (int i = 1; i <= movable; i++) {
                Vec3 delta = solved.get(i).subtract(solved.get(i-1));
                double d = delta.length();
                if (d > 1e-7) solved.set(i, solved.get(i-1).add(
                    delta.scale(lengths[i-1] / d)));
            }
        }
        for (int i = 1; i <= movable; i++)
            points.set(pivotIndex + i, solved.get(i));

        // Three independent, cardinal direction legs make clear right angles:
        // X (left/right) -> Z (forward/back) -> Y (ground/height).
        // Each axis has its own eased timing; never interpolate diagonally.
        Vec3 tip = points.get(points.size()-1);
        tip = appendAxis(points, tip, target.x, 0, extendX);
        tip = appendAxis(points, tip, target.z, 2, extendZ);
        tip = appendAxis(points, tip, target.y, 1, descend);

        return new Pose(List.copyOf(points), tip, clamp(opening), bend);
    }

    private static Vec3 appendAxis(ArrayList<Vec3> points, Vec3 from,
                                   double target, int axis, double phase) {
        if (points.size() >= MAX_EXTENSIONS + 24 || phase <= 1e-7) return from;
        double current = axis == 0 ? from.x : axis == 1 ? from.y : from.z;
        double desired = current + (target - current) * phase;
        double delta = desired - current;
        if (Math.abs(delta) < 1e-5) return from;

        int count = Math.min((int)Math.ceil(Math.abs(delta) / STEP),
            Math.max(0, MAX_EXTENSIONS + 24 - points.size()));
        if (count <= 0) return from;

        Vec3 last = from;
        for (int i = 1; i <= count; i++) {
            double value = current + Math.signum(delta) *
                Math.min(Math.abs(delta), i * STEP);
            last = switch (axis) {
                case 0 -> new Vec3(value, from.y, from.z);
                case 1 -> new Vec3(from.x, value, from.z);
                default -> new Vec3(from.x, from.y, value);
            };
            points.add(last);
        }
        return last;
    }

    /** Mouth 14px tall: bottom face rests on the Endermite's ground plane. */
    public static Vec3 endermiteGroundAim(double x, double feetY, double z) {
        return new Vec3(x, feetY + 7.0 / 16.0 + .015, z);
    }

    public static Pose evaluate(MimicProfile profile, Vec3 root, Vec3 head,
                                Vec3 target, double p, boolean recovering) {
        return evaluateBranch(profile, List.of(root, head), target, p, recovering);
    }

    private static double smooth(double v) {
        double t = clamp(v);
        return t*t*(3.0 - 2.0*t);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
