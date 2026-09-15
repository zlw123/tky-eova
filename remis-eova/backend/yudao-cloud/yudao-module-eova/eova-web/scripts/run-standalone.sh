#!/bin/bash
# **独立应用一键跑**（r331）—— 拿哥口径：**先不接平台网关、不进 k8s，作为独立应用跑起来**；
# 技术栈迁移完成后再纳入 k8s（那时用 `../k8s/eova-web.yaml` 模板 + `../Dockerfile`）。
#
# 它做什么（**一个进程同时供给 API 与页面**，不依赖 Vite / 不依赖平台）：
#   ① 装配 `target/standalone/`：`eova-web.jar` + `lib/`（JDBC 驱动）+ `webapp/`（旧视图树）
#      + `ui-dist/`（SPA 打包产物）。后两者默认用**符号链接**指回仓库（省时省空间），
#      `--copy-assets` 则物理拷贝（做"可搬走的交付目录"时用）。
#   ② 用可执行 jar 起进程：`java -cp "eova-web.jar:lib/*" …JarLauncher`，
#      并以 `-Deova.webapp.root` / `-Deova.ui.dist` 指向装配好的资产（**配置分支**，见 r329 的修复）。
#   ③ 轮询 `/actuator/health` 到 `UP`（不是"有响应就算就绪"），然后打印可直接点的地址。
#
# 用法：
#   bash scripts/run-standalone.sh                          # 默认 48090 · eova/dev.txt（MySQL 基线档）
#   bash scripts/run-standalone.sh --port 48091 --prop eova/dev-kingbase.txt
#   bash scripts/run-standalone.sh --stage /tmp/eova-app --copy-assets
#   bash scripts/run-standalone.sh --stop
#   bash scripts/run-standalone.sh --drivers-dir /path/to/drivers
#
# 退出码：0 = 已就绪；1 = 启动/健康失败；2 = 环境缺件（jar/资产/驱动造不出来）
set -u

# ---------- 参数 ----------
PORT=48090
PROP=eova/dev.txt
STAGE=""
STOP=0
COPY_ASSETS=0
DRIVERS_DIR=""
while [ $# -gt 0 ]; do
  case "$1" in
    --port) PORT="$2"; shift 2 ;;
    --prop) PROP="$2"; shift 2 ;;
    --stage) STAGE="$2"; shift 2 ;;
    --drivers-dir) DRIVERS_DIR="$2"; shift 2 ;;
    --copy-assets) COPY_ASSETS=1; shift ;;
    --stop) STOP=1; shift ;;
    -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
    *) echo "未知参数：$1（--help 看用法）" >&2; exit 2 ;;
  esac
done

# ---------- 定位仓库根（脚本可能被从任何工作目录调用）----------
SELF=$(cd "$(dirname "$0")" && pwd)
MODULE=$(cd "$SELF/.." && pwd)              # …/eova-web
REPO=""
d="$MODULE"
for _ in $(seq 1 10); do
  if [ -d "$d/remis-eova/backend/yudao-cloud" ]; then REPO="$d"; break; fi
  d=$(dirname "$d")
done
[ -n "$REPO" ] || { echo "✗ 定位不到仓库根（从 $MODULE 向上 10 层）" >&2; exit 2; }

UI="$REPO/remis-eova/front/remis-eova-ui"
LEGACY="$UI/src/legacy"
DIST="$UI/dist"
JAR="$MODULE/target/eova-web.jar"
[ -n "$STAGE" ] || STAGE="$MODULE/target/standalone"
PIDFILE="$STAGE/app.pid"

# ---------- --stop ----------
kill_by_port() {
  local pids
  pids=$(lsof -ti "tcp:$1" 2>/dev/null || true)
  [ -n "$pids" ] && kill $pids 2>/dev/null || true
}
if [ "$STOP" = "1" ]; then
  if [ -f "$PIDFILE" ]; then kill "$(cat "$PIDFILE")" 2>/dev/null || true; rm -f "$PIDFILE"; fi
  kill_by_port "$PORT"
  sleep 2
  echo "已停止（端口 ${PORT}）"
  exit 0
fi

echo "=== ① 装配 $STAGE ==="
# jar：没有就先打包（与 Dockerfile 同一份产物）
if [ ! -f "$JAR" ]; then
  echo "   jar 不存在 ⇒ 先 mvn package"
  ( cd "$REPO/remis-eova/backend/yudao-cloud" \
    && mvn -o -q -pl yudao-module-eova/eova-web -am package -DskipTests ) || {
    echo "✗ package 失败 ⇒ 未执行" >&2; exit 2; }
fi
[ -f "$JAR" ] || { echo "✗ 没有可执行 jar：$JAR" >&2; exit 2; }
mkdir -p "$STAGE/lib"
cp -f "$JAR" "$STAGE/eova-web.jar"

# 驱动（既有口径：驱动是 test 作用域、不进 fat jar ⇒ 由部署提供）
if [ -n "$DRIVERS_DIR" ]; then
  cp -f "$DRIVERS_DIR"/*.jar "$STAGE/lib/" 2>/dev/null || true
fi
if ! ls "$STAGE/lib"/*.jar >/dev/null 2>&1; then
  # 默认从本机 ~/.m2 取（只影响**本机**跑；交付时用 --drivers-dir 给）
  cp -f "$HOME/.m2/repository/mysql/mysql-connector-java/8.0.30/mysql-connector-java-8.0.30.jar" "$STAGE/lib/" 2>/dev/null || true
  cp -f "$HOME/.m2/repository/cn/com/kingbase/kingbase8/8.6.0/kingbase8-8.6.0.jar" "$STAGE/lib/" 2>/dev/null || true
fi
[ "$(ls -1 "$STAGE/lib"/*.jar 2>/dev/null | wc -l | tr -d ' ')" -gt 0 ] || {
  echo "✗ lib/ 里没有 JDBC 驱动（用 --drivers-dir 指定）⇒ 未执行" >&2; exit 2; }
echo "   lib/ 驱动：$(cd "$STAGE/lib" && ls -1 *.jar | tr '\n' ' ')"

# 视图树 + SPA 产物
[ -d "$LEGACY/eova" ] && [ -d "$LEGACY/_eova" ] || { echo "✗ 视图根不全：$LEGACY" >&2; exit 2; }
if [ ! -f "$DIST/index.html" ]; then
  echo "   SPA 产物缺失 ⇒ 先 pnpm build"
  ( cd "$UI" && pnpm build >/dev/null 2>&1 ) || { echo "✗ pnpm build 失败 ⇒ 未执行" >&2; exit 2; }
fi
stage_asset() { # $1=源 $2=目标名
  rm -rf "$STAGE/$2"
  if [ "$COPY_ASSETS" = "1" ]; then cp -R "$1" "$STAGE/$2"; else ln -s "$1" "$STAGE/$2"; fi
}
stage_asset "$LEGACY" webapp
stage_asset "$DIST" ui-dist
echo "   webapp → $(readlink "$STAGE/webapp" 2>/dev/null || echo "（已拷贝）")"
echo "   ui-dist → $(readlink "$STAGE/ui-dist" 2>/dev/null || echo "（已拷贝）")"

echo "=== ② 启动（端口 $PORT · 档 $PROP · profile local）==="
kill_by_port "$PORT"
sleep 1
# ★ `-D` 必须在主类**之前**（`LegacyWebBootstrap` 读的是 System.getProperty）
( cd "$STAGE" && nohup java -Xms512m -Xmx512m \
    -Dspring.profiles.active=local \
    -Deova.prop="$PROP" \
    -Deova.webapp.root="$STAGE/webapp" \
    -Deova.ui.dist="$STAGE/ui-dist" \
    -cp "eova-web.jar:lib/*" org.springframework.boot.loader.launch.JarLauncher \
    --server.port="$PORT" > "$STAGE/app.log" 2>&1 & echo $! > "$PIDFILE" )

echo "=== ③ 等 /actuator/health = UP ==="
up=0
for _ in $(seq 1 40); do
  sleep 2
  h=$(curl -s --max-time 3 "http://127.0.0.1:$PORT/actuator/health" 2>/dev/null || true)
  case "$h" in *'"status":"UP"'*) up=1; break ;; esac
done
if [ "$up" != "1" ]; then
  echo "✗ 未就绪（日志尾）："; tail -5 "$STAGE/app.log" | sed 's/^/   /'; exit 1
fi
echo "   health = $h"

echo "=== ④ 冒烟（页面与接口同一个进程）==="
LOGIN=$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "http://127.0.0.1:$PORT/user/login" || true)
DO=$(curl -s --max-time 8 -X POST -d 'login_id=eova&login_pwd=000000' "http://127.0.0.1:$PORT/user/doLogin" || true)
echo "   /user/login = $LOGIN · /user/doLogin = $(printf '%s' "$DO" | head -c 40)"
case "$DO" in *'"state":"ok"'*) ;; *) echo "✗ 登录接口未 ok ⇒ 判 FAIL"; exit 1 ;; esac

cat <<EOF

独立应用已就绪（**单进程同时供 API 与页面**）：
  地址：http://localhost:$PORT/     （登录 eova / 000000）
  档位：$PROP      profile：local（默认档，**不连 Nacos**）
  日志：$STAGE/app.log
  停止：bash scripts/run-standalone.sh --port $PORT --stop
EOF
exit 0
