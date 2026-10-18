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

    /**
     * Move the ENTIRE infected terminal branch, anchored at its first shared
     * root or junction. No dynamically changing partial-tail pivot.
     */
    public static int neededSegments(List<Vec3> rest, Vec3 target) {
        return Math.max(0, rest.size() - 1);
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
        // Hunt WITHOUT hovering: the vertical drop starts almost at once,
        // together with rotation and horizontal approach. Angular X->Z->Y
        // construction remains, but the head heads DOWN throughout the attack.
        // Full opening occurs before contact, and the bite lands near p=1.
        double bend = recovering
            ? 1.0 - smooth((p - .32) / .67) : smooth(p / .40);
        double extendX = recovering
            ? 1.0 - smooth((p - .42) / .48) : smooth((p - .04) / .60);
        double extendZ = recovering
            ? 1.0 - smooth((p - .32) / .54) : smooth((p - .09) / .63);
        double descend = recovering
            ? 1.0 - smooth((p - .06) / .73) : smooth((p - .05) / .79);
        double opening = recovering
            ? 1.0 - smooth((p - .02) / .32) : smooth((p - .28) / .46);

        int last = resting.size() - 1;
        // Use ALL real attached plant joints; never just the last few blocks.
        int pivotIndex = 0;
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

        // The shared root stays fixed while all branch segments flex.
        // Sharp X/Z/Y extension legs handle direct approach without a hover.
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

        // Strong, clean axis-aligned elbows X -> Z -> Y. The phases
        // OVERLAP, so the flower moves toward the prey immediately, instead
        // of waiting above it until the final third of the animation.
        Vec3 tip = points.get(points.size()-1);
        double dx = target.x - tip.x;
        double dz = target.z - tip.z;
        double dy = target.y - tip.y;

        // Make some elbows STRONGER than a plain 90-degree corner (~112°):
        // extend 0.26 blocks beyond an axis, then turn with a short backward
        // diagonal into the next straight leg. The next leg returns exactly
        // to the prey's X/Z coordinates. This keeps the chorus-like square
        // silhouette without introducing long bent/diagonal tubes.
        double xHook = Math.abs(dx) > .75 && Math.abs(dz) > .75
            ? Math.copySign(.26, dx) : 0.0;
        double zHook = Math.abs(dz) > .75 && Math.abs(dy) > .75
            ? Math.copySign(.26, dz) : 0.0;

        tip = appendAxis(points, tip, target.x + xHook, 0, extendX);
        if (xHook != 0 && Math.abs(target.z - tip.z) > .01)
            tip = elbow(points, tip, 0, -xHook, 2, target.z - tip.z, extendZ);
        tip = appendAxis(points, tip, target.z + zHook, 2, extendZ);
        if (zHook != 0 && Math.abs(target.y - tip.y) > .01)
            tip = elbow(points, tip, 2, -zHook, 1, target.y - tip.y, descend);
        tip = appendAxis(points, tip, target.y, 1, descend);

        return new Pose(List.copyOf(points), tip, clamp(opening), bend);
    }

    /**
     * A compact backward-leaning elbow. The 0.26px-block retrace against
     * a 0.65-block forward leg produces an external angle around 112°.
     * The rest of the branch still follows cardinal-axis straight segments.
     */
    private static Vec3 elbow(ArrayList<Vec3> joints, Vec3 from,
                              int retreatAxis, double retreat, int forwardAxis,
                              double forwardDistance, double phase) {
        if (joints.size() >= MAX_EXTENSIONS + 24 || phase <= 1e-7) return from;
        double t = smooth(phase / .45);
        double next = Math.copySign(Math.min(.65, Math.abs(forwardDistance)), forwardDistance) * t;
        double x = from.x, y = from.y, z = from.z;
        if (retreatAxis == 0) x += retreat * t;
        if (retreatAxis == 2) z += retreat * t;
        if (forwardAxis == 1) y += next;
        if (forwardAxis == 2) z += next;
        Vec3 bend = new Vec3(x, y, z);
        if (bend.distanceToSqr(from) >= 1e-8) joints.add(bend);
        return bend;
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
