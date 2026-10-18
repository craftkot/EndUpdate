package com.craftkot.livingchorus;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Geometry-independent rig shared by procedural renderer and server attack
 * logic. Every piece is a rigid segment: no geometry/UV texture scaling.
 *
 * The V17 Blockbench file is a *reference* only; final stems derive from a
 * deterministic per-flower MimicProfile and real target position.
 */
public final class MimicRig {
    public record Pose(List<Vec3> joints, Vec3 mouth, double opening, double attackProgress) { }

    private MimicRig() { }

    public static Pose evaluate(MimicProfile profile, Vec3 root, Vec3 sleepingHead,
                                Vec3 target, double fraction, boolean recovering) {
        fraction = clamp(fraction);
        double move = recovering ? 1.0 - smooth(fraction) : smooth(clamp(fraction / 0.84));
        // Slow approach with a fast final forward snap.
        double snap = recovering ? 0.0 : smooth(clamp((fraction - 0.76) / 0.24));
        double reach = clamp(move * (0.82 + 0.18 * snap));
        Vec3 end = sleepingHead.lerp(target, reach);

        double sign = (profile.direction() % 2 == 0) ? 1.0 : -1.0;
        double curveX = sign * (0.08 + profile.firstBend() / 75.0);
        double curveZ = -sign * (0.08 + profile.secondBend() / 95.0);

        // Two cubic Bezier controls. At target height 0.2 the head bends
        // below player height; at player height the same base follows a higher
        // arc. A renderer instantiates rigid segments at the returned joints.
        double rise = Math.max(.24, sleepingHead.y - root.y);
        Vec3 c1 = root.add(curveX, Math.max(.34, rise * .67), curveZ);
        Vec3 toward = end.subtract(root);
        Vec3 c2 = end.subtract(toward.x * .27, 0, toward.z * .27)
            .add(-curveZ, Math.max(.12, Math.abs(toward.y) * .13), curveX);

        // Up to 12 uniform-length displayable segments; NEVER scale segment
        // meshes to bridge longer reaches or their UV pixels would stretch.
        int count = Math.min(12, Math.max(profile.sectionCount(),
            (int) Math.ceil(end.distanceTo(root) / .64)));
        ArrayList<Vec3> joints = new ArrayList<>(count + 1);
        for (int i = 0; i <= count; ++i) {
            joints.add(cubic(root, c1, c2, end, i / (double) count));
        }

        double opening = recovering ? 1.0 - smooth(clamp(fraction * 1.7))
            : smooth(clamp((fraction - .56) / .26));
        return new Pose(List.copyOf(joints), end, opening, reach);
    }

    private static Vec3 cubic(Vec3 a, Vec3 b, Vec3 c, Vec3 d, double t) {
        double s = 1.0 - t;
        return a.scale(s*s*s).add(b.scale(3*s*s*t))
            .add(c.scale(3*s*t*t)).add(d.scale(t*t*t));
    }

    private static double smooth(double t) {
        t = clamp(t);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
