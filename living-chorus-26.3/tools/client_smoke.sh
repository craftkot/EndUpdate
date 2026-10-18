#!/usr/bin/env bash
set -Eeuo pipefail
mkdir -p client-smoke/screenshots run
printf 'guiScale:2\n' > run/options.txt
: > client-smoke/status.txt
export DISPLAY=:99
export LIBGL_ALWAYS_SOFTWARE=1
export MESA_LOADER_DRIVER_OVERRIDE=llvmpipe
export ALSOFT_DRIVERS=null
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
for i in $(seq 1 330); do
  WIN=$(xdotool search --onlyvisible --name Minecraft 2>/dev/null | tail -n 1 || true)
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
  sleep 1
done
if [ -z "$WIN" ]; then
  echo "CLIENT_REAL_WINDOW=NO" | tee -a client-smoke/status.txt
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
# Depending on installed version, the new-world GUI can be entered directly
# when there are no saves. A second click captures the alternative list case.
click_window_percent 50 92
sleep 7
capture 03-world-creation
# We do not announce an integrated world without its log confirmation.
# 26.3 UI layout must be visually audited before automating world creation.
echo "CLIENT_REAL_START_CAPTURED=YES" | tee -a client-smoke/status.txt
echo "WORLD_LOADED=$(grep -Ec 'joined the game|Starting integrated minecraft server' client-smoke/client.log || true)" | tee -a client-smoke/status.txt
echo "REAL_CLIENT_PHASE1_COMPLETE=YES" | tee -a client-smoke/status.txt
