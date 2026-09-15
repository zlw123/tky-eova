# ★ r329（阶段三 B 级，`docs/DES-011-R1-phase3-stack-alignment.md`）：可执行 jar 的**容器镜像**配方。
#
# 为什么长这样（逐条对照平台，只读取证）：
#   · `FROM eclipse-temurin:17-jre` —— 与平台 `yudao-module-infra/.../Dockerfile:3` **逐字同基础镜像**
#     （平台根 `Dockerfile:3` 用的是 21-jre，与它自己 `java.version=17` 不一致 ⇒ **不照抄那一份**）。
#   · `COPY target/eova-web.jar app.jar` —— 对应 `spring-boot-maven-plugin:repackage` + `finalName`
#     （见 `eova-web/pom.xml`）；构建前须先 `mvn -o package`。
#   · `ENV TZ=Asia/Shanghai JAVA_OPTS=...` / `EXPOSE` / `CMD java ... -jar` —— 与平台同形。
#
# ## 端口
# 与 `application.yaml` 的 `server.port=48090` 一致（平台"一服务一端口"约定；部署时可用
# `-Dserver.port=` 覆盖，平台 k8s 就是这么做的）。
#
# ## ★ DB 驱动（既有口径：**驱动由部署提供**，不进 fat jar）
# maven 里驱动是 **test 作用域**（`eova-web/pom.xml` 注释 + `eova-db-adapter/pom.xml`）⇒ 可执行 jar
# **不含**任何 JDBC 驱动 ⇒ 镜像里必须把驱动放进 `/app/lib/`（构建上下文里的 `lib/` 目录，
# 由部署流水线提供；本目录里的 `lib/.gitkeep` 只是占位，保证空目录也能构建出镜像）。
# 启动用 `-cp "app.jar:/app/lib/*" ...JarLauncher`（与仓库里实跑过的配方同形，见
# `docs/.local/spikes/verify-phase3-alignment.sh` 的 ②）。
# ★ 待裁（DES-011 §4.4 第 5 条）：若平台 k8s 交付希望"一个 jar 自带驱动"，则把驱动提为
#   `eova-web` 的 runtime 依赖即可（属**打包边界**决定，另行裁定），届时本文件可简化成 `-jar`。
FROM eclipse-temurin:17-jre

RUN mkdir -p /app
WORKDIR /app

# 可执行 jar（先在本模块 `mvn -o package -DskipTests` ⇒ target/eova-web.jar）
#   · 构建：`docker build -t eova-web:dev .`（**上下文 = 本模块目录**，与平台 Dockerfile 同姿势）
COPY target/eova-web.jar app.jar
# DB 驱动等"运行时提供物"（构建上下文里必须存在 `lib/`；空目录也合法）
COPY docker/lib/ /app/lib/

# ## ★ 页面资产（**部署输入**，不是本镜像自带）
# 后端要自己供给页面：① 旧视图树（`eova/` + `_eova/` 两层，`eova.webapp.root`）
# ② SPA 打包产物（含 index.html，`eova.ui.dist`）。二者体积与发布节奏都与后端不同 ⇒
# 本 Dockerfile **不烤进镜像**，由部署时挂载或由流水线在构建上下文里补齐：
#   docker run -v <repo>/remis-eova/front/remis-eova-ui/src/legacy:/app/webapp:ro \
#              -v <repo>/remis-eova/front/remis-eova-ui/dist:/app/ui-dist:ro \
#              -e JAVA_OPTS="-Deova.webapp.root=/app/webapp -Deova.ui.dist=/app/ui-dist ..."
# ★ 实跑验证（r329）：按上面配方起容器后 `/user/login`=200（SPA 壳）、`/user.doLogin`={"state":"ok"}、
#   `/actuator/health`=UP（数据源用金仓档 + `--add-host=base.platform:<ip>`）。
# ★ 待裁（DES-011 §4.4）：是否把这两个目录烤进镜像（需要把构建上下文扩到仓库根）。
ENV TZ=Asia/Shanghai JAVA_OPTS="-Xms512m -Xmx512m"

EXPOSE 48090

CMD java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom \
    -cp "app.jar:/app/lib/*" org.springframework.boot.loader.launch.JarLauncher
