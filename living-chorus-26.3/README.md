# Living Chorus 0.18.2 — immediate downward hunt (hotfix)

Previous version 0.18.1 removed the large horizontal crane, but retained
separate animation curves: stalk bending ran 0%–83%, while telescoping
began only at 35% and finished at 95% of the attack. The flower therefore
still performed a visibly abrupt final dive after lingering above prey.

0.18.2 makes existing stalk bending and short terminal telescoping follow
the SAME fast-start/ease-out curve immediately from the first attack frame.
Both slow to a halt smoothly near the prey; neither waits until late in the
animation. Mouth opening now begins at 20% of attack time.

Retained: all connected branch joints moving, maximum 1.5-block extra
extension, realistic reach rejection, vanilla chorus texture, sparse 10px
collars, contagious growth, five-second post-feeding particles, and
18-block horizontal / 24-block vertical Endermite detection.

This is a code/compilation hotfix; per the user's request no graphical
gameplay testing is performed before sending the corrected JAR. Manual
side-view footage is needed to verify how it looks in Minecraft 26.3.
