#!/usr/bin/env bash
# Dev helper: run a scripted headless session and summarize the log.
#   tools/devrun.sh server|client|gametest <compat:none|create|all> [script] [extra gradle args...]
# Client runs use Xvfb + Mesa software GL. Logs go to build/devrun/<side>-<compat>.log
set -u
side=$1; compat=$2; script=${3:-}; shift 3 2>/dev/null || shift $#
cd "$(dirname "$0")/.."
mkdir -p build/devrun
log=build/devrun/${side}-${compat}.log
args=(-Preverie.compat=$compat)
[ -n "$script" ] && args+=(-Preverie.dev.script=$script)
[ "$side" = server ] && args+=(-Preverie.dev.world=${WORLD:-world-$compat})
case $side in
  server) task=runServer ;;
  client) task=runClient ;;
  gametest) task=runGameTestServer ;;
  *) echo "bad side"; exit 2 ;;
esac
if [ "$side" = client ]; then
  export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 MESA_GLSL_VERSION_OVERRIDE=450
  timeout 1500 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew $task "${args[@]}" "$@" > "$log" 2>&1
else
  timeout 1500 ./gradlew $task "${args[@]}" "$@" > "$log" 2>&1
fi
code=$?
echo "== $side/$compat exit=$code log=$log"
grep -E "\[devscript\]|Done \(|GameTest|tests? (passed|failed)|All .* required tests passed|BUILD" "$log" | grep -v JAVA_TOOL | tail -40
echo "-- errors/exceptions (filtered):"
grep -n -E "ERROR|Exception|Caused by|FATAL" "$log" | grep -v -E "JAVA_TOOL|ApiStatus\\\$ScheduledForRemoval|Error loading class: org/jetbrains" | head -40
exit $code
