#!/usr/bin/env bash
set -Eeuo pipefail
mkdir -p client-smoke/screenshots run
printf 'guiScale:2\n' > run/options.txt
: > client-smoke/status.txt
export DISPLAY=:99
export LIBGL_ALWAYS_SOFTWARE=1
export SDL_VIDEO_FORCE_EGL=1
export SDL_VIDEO_X11_FORCE_EGL=1
export SDL_VIDEODRIVER=x11
export EGL_PLATFORM=x11
export MESA_LOADER_DRIVER_OVERRIDE=llvmpipe
export ALSOFT_DRIVERS=null
export LIVING_CHORUS_SMOKE=1
export XDG_RUNTIME_DIR=/tmp/living-chorus-minecraft
mkdir -p "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"
X_PID='' WM_PID='' MC_PID=''
cleanup() {
  echo "Cleaning up graphical test" >> client-smoke/status.txt
  if [ -n "$MC_PID" ]; then
    pkill -TERM -P "$MC_PID" 2>/dev/null || true
    kill "$MC_PID" 2>/dev/null || true
  fi
  if [ -n "$WM_PID" ]; then kill "$WM_PID" 2>/dev/null || true; fi
  if [ -n "$X_PID" ]; then kill "$X_PID" 2>/dev/null || true; fi
}
trap cleanup EXIT
Xvfb :99 -screen 0 1280x800x24 +extension GLX +render -noreset > client-smoke/xvfb.log 2>&1 &
X_PID=$!
sleep 4
xdpyinfo -display :99 > client-smoke/x11.txt
openbox > client-smoke/wm.log 2>&1 &
WM_PID=$!
sleep 2
glxinfo -B | tee client-smoke/renderer.txt
grep -iq "OpenGL version string" client-smoke/renderer.txt
echo "Starting REAL NeoForge 26.3 Minecraft client" | tee -a client-smoke/status.txt
gradle --no-daemon --console=plain runClient > client-smoke/client.log 2>&1 &
MC_PID=$!
WIN=''
for i in $(seq 1 125); do
  WIN=$(xdotool search --onlyvisible --name Minecraft 2>/dev/null | tail -n 1 || true)
  if [ -z "$WIN" ] && grep -Fq 'Created window using SDL video driver' client-smoke/client.log; then
    WIN=$(xdotool getactivewindow 2>/dev/null || true)
    echo "SDL_WINDOW_FALLBACK=$WIN" >> client-smoke/status.txt
  fi
  if [ -n "$WIN" ]; then
    echo "CLIENT_REAL_WINDOW=YES after ${i}s" | tee -a client-smoke/status.txt
    break
  fi
  if grep -Eiq 'Mod loading has failed|Failed to create window|GLFW error|Exception in thread "Render thread"' client-smoke/client.log; then
    echo "REAL_CLIENT_CRASH_BEFORE_WINDOW=YES" | tee -a client-smoke/status.txt
    tail -120 client-smoke/client.log
    exit 1
  fi
  if ! kill -0 "$MC_PID" 2>/dev/null; then
    echo "REAL_CLIENT_EXITED_BEFORE_WINDOW=YES" | tee -a client-smoke/status.txt
    tail -120 client-smoke/client.log
    exit 1
  fi
  if [ "$i" -eq 65 ]; then
    xwininfo -root -tree > client-smoke/window-tree.txt 2>&1 || true
    xprop -root _NET_ACTIVE_WINDOW > client-smoke/root-active-window.txt 2>&1 || true
    import -display :99 -window root -silent client-smoke/screenshots/00-display-during-start.png || true
    echo "DISPLAY_PROBE_65_SECONDS=YES" | tee -a client-smoke/status.txt
  fi
  sleep 1
done
xwininfo -root -tree > client-smoke/window-tree.txt 2>&1 || true
xdotool getactivewindow > client-smoke/active-window-id.txt 2>&1 || true
if [ -z "$WIN" ]; then
  echo "SDL_LOADED_BUT_NO_WINDOW_ID=YES" | tee -a client-smoke/status.txt
  import -display :99 -window root -silent client-smoke/screenshots/01-root-no-window-title.png || true
  exit 1
fi
xdotool windowactivate --sync "$WIN" || true
xdotool windowmove "$WIN" 0 0 || true
xdotool windowsize "$WIN" 1024 768 || true
xdotool mousemove 20 20
sleep 32

capture() {
  local label="$1"
  import -display :99 -window root -silent "client-smoke/screenshots/${label}.png"
  test -s "client-smoke/screenshots/${label}.png"
  identify "client-smoke/screenshots/${label}.png"
  echo "REAL_MINECRAFT_SCREEN=${label}" | tee -a client-smoke/status.txt
}
click_window_percent() {
  local px="$1" py="$2" metadata sx sy
  metadata="$(xdotool getwindowgeometry --shell "$WIN")"
  eval "$metadata"
  sx=$((X + WIDTH * px / 100))
  sy=$((Y + HEIGHT * py / 100))
  echo "GUI_CLICK=${px}%,${py}% => ${sx},${sy}" | tee -a client-smoke/status.txt
  xdotool mousemove "$sx" "$sy" click 1
}
capture 01-minecraft-first-launch
if grep -Eiq 'Mod loading has failed|Minecraft has crashed|Exception in thread "Render thread"' client-smoke/client.log; then
  echo "CLIENT_STARTED_BUT_FATAL_LOG=YES" | tee -a client-smoke/status.txt
  exit 1
fi
echo "CLIENT_TITLE_REAL_IMAGE=YES" | tee -a client-smoke/status.txt
# Title screen: singleplayer button (use actual window geometry, NOT Xvfb canvas).
xdotool key --clearmodifiers Escape || true
sleep 2
click_window_percent 50 33
sleep 10
capture 02-singleplayer-selected
# REAL 26.3 Create New World GUI, confirmed by previous screenshot.
# Existing screenshot at 1024x768: Game Mode y=240, Difficulty y=296,
# Allow Commands y=352, Create World x=355 y=756.
# Real 26.3 screenshot proved: Survival -> Creative -> Hardcore;
# ONE click chooses Creative. Two clicks previously selected Hardcore,
# locking difficulty and disabling commands for the entire test.
click_window_percent 50 26
sleep 2
# Default difficulty in the captured GUI was Hard -> Peaceful in one click.
click_window_percent 50 34
sleep 2
# Creative world creation enables commands automatically; do not blindly
# toggle it back OFF. Inspect persisted level.dat after world creation.
sleep 2
capture 03-world-settings-before-create

click_window_percent 34 93
echo "WORLD_CREATION_BUTTON_CLICKED=YES" | tee -a client-smoke/status.txt
world_started=0
for n in $(seq 1 180); do
  if grep -Eq 'Dev joined the game|joined the game' client-smoke/client.log; then
    world_started=1
    break
  fi
  if grep -Eiq 'Failed to start the minecraft server|Exception in server tick loop|Exception in thread "Render thread"|Minecraft has crashed' client-smoke/client.log; then
    echo "WORLD_CREATION_CRASH=YES" | tee -a client-smoke/status.txt
    break
  fi
  sleep 2
done
capture 04-world-load-attempt
if [ "$world_started" != 1 ]; then
  echo "REAL_INTEGRATED_WORLD_NOT_CONFIRMED=YES" | tee -a client-smoke/status.txt
  tail -90 client-smoke/client.log
  exit 1
fi
echo "REAL_INTEGRATED_WORLD_PLAYER_JOIN=YES" | tee -a client-smoke/status.txt
python3 tools/assert_client_world_commands.py run/saves | tee -a client-smoke/status.txt
sleep 6
capture 05-actual-minecraft-world

send_chat() {
  xdotool key --clearmodifiers t
  sleep .4
  xdotool type --clearmodifiers --delay 4 "$1"
  xdotool key --clearmodifiers Return
  sleep 1.3
}
# Server success must appear in the log: mouse clicks alone aren't enough.
send_chat '/say LIVING_CHORUS_REAL_CLIENT_COMMANDS'
sleep 2
if ! grep -E 'Server thread.*LIVING_CHORUS_REAL_CLIENT_COMMANDS' client-smoke/client.log; then
  echo "WORLD_COMMAND_PERMISSION_NOT_CONFIRMED=YES" | tee -a client-smoke/status.txt
  capture 06-game-chat-without-confirmed-permissions
  exit 1
fi
echo "REAL_CLIENT_COMMANDS=YES" | tee -a client-smoke/status.txt
# End island test fixture, not a fabricated rendering.
send_chat '/execute in minecraft:the_end run tp @s 0.5 83 0.5 0 18'
sleep 6
send_chat '/fill -4 78 -4 4 78 8 minecraft:end_stone'
send_chat '/setblock 0 79 4 minecraft:chorus_plant'
send_chat '/setblock 0 80 4 minecraft:chorus_plant'
send_chat '/setblock 0 81 4 minecraft:chorus_plant'
send_chat '/setblock 0 82 4 minecraft:chorus_flower[age=5]'
# Put the viewer's eye at the flower's actual center height, instead
# of blindly targeting the stem below the flower.
send_chat '/setblock 0 80 0 minecraft:end_stone'
send_chat '/tp @s 0.5 81 0.5 0 0'
sleep 2
capture 07-real-vanilla-dead-flower
send_chat '/livingchorus mark'
sleep .4
capture 08-real-mimic-flower-marked
if ! grep -Fq 'MIMIC (sleeping)' client-smoke/client.log; then
  echo 'REAL_MIMIC_MARK_NOT_CONFIRMED=YES' | tee -a client-smoke/status.txt
  tail -55 client-smoke/client.log >&2
  exit 1
fi
echo 'REAL_MIMIC_MARK_CONFIRMED=YES' | tee -a client-smoke/status.txt
send_chat '/difficulty easy'
# Prefer a wide diagonal downward view so the original flower, lower bite
# trajectory AND Endermite ground level are all inside the same screenshot.
send_chat '/tp @s 5.5 84 -4.5 31 24'
sleep 2
capture 09a-side-above-camera-before-prey
# Summon prey shortly before activation; do not rely on a 2-second action
# being visible after the command returns and chat typing finishes.
send_chat '/summon minecraft:endermite 0.5 79 2.5'
capture 09b-prey-side-view
# Capture the natural feeding animation immediately. No /provoke command
# while the first bite is active (that would return "already charging").
sleep .3
capture 10-feeding-start
sleep .45
capture 11-feeding-moving
sleep .5
capture 12-feeding-snap
sleep .55
capture 13-feeding-recovery
sleep 1
capture 14-feeding-idle-return
# Fail the test if no client packet and no extracted + submitted geometry.
# A green screenshot job alone must NOT be equated to successful rendering.
for token in LC_SMOKE_PACKET LC_SMOKE_MASK_STARTED LC_SMOKE_EXTRACT LC_SMOKE_SUBMIT; do
  if ! grep -Fq "$token" client-smoke/client.log; then
    echo "RENDER_DIAGNOSTIC_MISSING=$token" | tee -a client-smoke/status.txt
    exit 1
  fi
  echo "RENDER_DIAGNOSTIC_PRESENT=$token" | tee -a client-smoke/status.txt
done
echo "REAL_MINECRAFT_ANIMATED_MODEL_PIPELINE_CONFIRMED=YES" | tee -a client-smoke/status.txt
