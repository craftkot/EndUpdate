package com.craftkot.livingchorus;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Compact, joint-driven infected chorus branch. Never creates the long
 * horizontal X->Z->Y "crane" from v0.18: the actual vanilla stalk bends
 * toward prey, with no more than 1.5 blocks of terminal telescoping.
 *
 * All original joints move; the first shared/root junction is fixed.
 * Rendered cuboids are rigid, without scaling or texture stretching.
 */
public final class MimicRig {
    public record Pose(List<Vec3> joints, Vec3 mouth, double opening, double attackProgress) {}

    private static final double EXTENSION_LIMIT = 1.5;
    private static final double EXTENSION_STEP = .75;

    private MimicRig() {}

    public static int neededSegments(List<Vec3> rest, Vec3 target) {
        return Math.max(0, rest.size() - 1);
    }

    public static double maximumReach(List<Vec3> rest) {
        double length = EXTENSION_LIMIT;
        for (int i = 1; i < rest.size(); i++)
            length += rest.get(i-1).distanceTo(rest.get(i));
        return length;
    }

    public static boolean canReach(List<Vec3> rest, Vec3 target) {
        return rest.size() >= 2
            && rest.get(0).distanceTo(target) <= maximumReach(rest) - .05;
    }

    public static Pose evaluateBranch(MimicProfile style, List<Vec3> resting,
                                      Vec3 target, double fraction, boolean recovering) {
        return evaluateBranch(style, resting, target, fraction, recovering,
            neededSegments(resting, target));
    }

    public static Pose evaluateBranch(MimicProfile style, List<Vec3> resting,
                                      Vec3 target, double fraction, boolean recovering,
                                      int affectedSegments) {
        if (resting.size() < 2) {
            Vec3 tip = resting.isEmpty() ? target : resting.get(resting.size() - 1);
            return new Pose(List.copyOf(resting), tip, 0, 0);
        }

        double p = clamp(fraction);
        // Immediately bend DOWN AND toward the prey, rather than building a
        // horizontal beam first and hanging over the target.
        double bend = recovering ? 1.0 - smooth((p - .19) / .77)
                                 : smooth(p / .83);
        double telescope = recovering ? 1.0 - smooth((p - .06) / .73)
                                      : smooth((p - .35) / .60);
        double open = recovering ? 1.0 - smooth((p - .03) / .36)
                                 : smooth((p - .39) / .47);

        ArrayList<Vec3> points = new ArrayList<>(resting);
        Vec3 root = resting.get(0);
        int last = resting.size() - 1;
        double[] lengths = new double[last];
        double total = 0;
        for (int i = 0; i < last; i++) {
            lengths[i] = resting.get(i).distanceTo(resting.get(i+1));
            total += lengths[i];
        }

        Vec3 toTarget = target.subtract(root);
        double distance = toTarget.length();
        Vec3 reachable = distance > total * .985 && distance > 1e-8
            ? root.add(toTarget.scale(total * .985 / distance)) : target;
        Vec3 desiredHead = resting.get(last).lerp(reachable, bend);

        // FABRIK moves the full infected part but preserves every real
        // segment length. No huge new X/Z crane sections are generated.
        for (int iteration = 0; iteration < 18; iteration++) {
            points.set(last, desiredHead);
            for (int i = last - 1; i >= 0; i--) {
                Vec3 diff = points.get(i).subtract(points.get(i+1));
                double d = diff.length();
                if (d > 1e-8)
                    points.set(i, points.get(i+1).add(diff.scale(lengths[i] / d)));
            }
            points.set(0, root);
            for (int i = 1; i <= last; i++) {
                Vec3 diff = points.get(i).subtract(points.get(i-1));
                double d = diff.length();
                if (d > 1e-8)
                    points.set(i, points.get(i-1).add(diff.scale(lengths[i-1] / d)));
            }
        }

        Vec3 tip = points.get(last);
        Vec3 needed = target.subtract(tip);
        double missing = needed.length();
        if (missing > 1e-5 && telescope > 1e-5) {
            Vec3 direction = needed.scale(1.0 / missing);
            double extension = Math.min(EXTENSION_LIMIT, missing) * telescope;
            // At most TWO short 0.75-block pieces instead of dozens of crane
            // segments. Texture cubes keep their exact unscaled dimensions.
            for (int i = 1; i <= 2; i++) {
                double step = Math.min(extension, i * EXTENSION_STEP);
                if (step > 1e-5) points.add(tip.add(direction.scale(step)));
            }
            tip = points.get(points.size() - 1);
        }
        return new Pose(List.copyOf(points), tip, clamp(open), bend);
    }

    public static Vec3 endermiteGroundAim(double x, double feetY, double z) {
        return new Vec3(x, feetY + 7.0 / 16.0 + .015, z);
    }

    public static Pose evaluate(MimicProfile profile, Vec3 root, Vec3 head,
                                Vec3 target, double progress, boolean recovering) {
        return evaluateBranch(profile, List.of(root, head), target, progress, recovering);
    }

    private static double smooth(double value) {
        double t = clamp(value);
        return t*t*(3 - 2*t);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
