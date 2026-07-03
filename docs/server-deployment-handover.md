# 智能问答助手系统服务器部署交接文档

> 适用对象：校内服务器运维/信息化工作人员  
> 更新时间：2026-07-02  
> 推荐部署方式：Docker Compose 一键部署  
> 默认代码分支：`main`  
> 推荐拉取远端：`gitee`

本文档用于把“智能问答助手系统”部署到校内服务器，并配置专用 SSH Deploy Key。后续代码更新时，校内工作人员可在服务器上直接执行 `git pull` 或 `deploy/update.sh` 拉取最新代码并重启服务。

## 1. 系统组成

当前 Docker Compose 会启动以下服务：

| 服务 | 说明 | 默认宿主机端口 |
| --- | --- | --- |
| `frontend` | Vue 前端，Nginx 静态站点，同时反向代理 `/api`、`/ws`、`/media` | `80` |
| `backend` | Spring Boot 后端 API 与 WebSocket 服务 | `8080` |
| `mysql` | MySQL 8.0，首次启动会自动执行 `backend/llm_qa_sy.sql` | `3306` |
| `redis` | Redis 7.2 | `6379` |
| `milvus` | Milvus standalone 向量库 | `19530`、`9091` |
| `model-service` | FastAPI 本地模型服务 | `18080` |
| `attu` | Milvus 可视化管理工具 | `8000` |

前端 Nginx 已配置反向代理：

- `/api/*` 转发到 `backend:8080`
- `/ws/*` 转发到 `backend:8080`
- `/media/*` 转发到 `backend:8080`

## 2. 服务器准备

### 2.1 推荐配置

最低可运行配置取决于本地模型是否启用 CPU 或 GPU。建议：

- CPU：8 核及以上
- 内存：16 GB 起步，建议 32 GB
- 磁盘：100 GB 起步，模型、数据库、上传文件较多时建议 200 GB 以上
- 操作系统：Ubuntu Server 22.04 LTS / Debian 12 / Rocky Linux 9 均可
- 网络：服务器可访问代码仓库、Docker 镜像源；校内用户可访问服务器 `80` 端口

如使用 GPU 推理，还需要额外安装 NVIDIA 驱动和 NVIDIA Container Toolkit。当前默认配置使用 CPU，保证无 GPU 服务器也能启动。

### 2.2 安装基础软件

Ubuntu/Debian 示例：

```bash
sudo apt update
sudo apt install -y git curl ca-certificates vim openssh-client
```

安装 Docker 与 Compose Plugin。校内服务器建议使用清华 TUNA Docker CE 软件源：

```bash
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

cat <<EOF | sudo tee /etc/apt/sources.list.d/docker.sources
Types: deb
URIs: https://mirrors.tuna.tsinghua.edu.cn/docker-ce/linux/ubuntu
Suites: $(. /etc/os-release && echo "$VERSION_CODENAME")
Components: stable
Signed-By: /etc/apt/keyrings/docker.gpg
EOF

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"
newgrp docker
docker version
docker compose version
```

如果服务器是 Debian，把上面两处 `ubuntu` 改为 `debian`。

注意：清华 TUNA 的 `docker-ce` 是 Docker 安装包镜像源，不是 Docker Hub 镜像缓存。它可以加速安装 Docker 本身，但不能直接解决 `mysql:8.0`、`redis:7.2-alpine` 等容器镜像从 Docker Hub 拉取超时的问题。若校内服务器无法直接访问 Docker Hub，可使用离线镜像包或校内已有镜像代理，不要求开通付费云厂商服务。

### 2.3 处理 Docker Hub 镜像拉取

如果启动时报 `failed to resolve reference "docker.io/library/mysql:8.0"` 或 `registry-1.docker.io ... i/o timeout`，说明 Docker Hub 镜像拉取失败。这不是项目代码问题。

#### 方案 A：离线镜像包，推荐且无云服务依赖

在一台可以正常访问 Docker Hub、Quay、MinIO 等镜像仓库的电脑或服务器上执行。若校内服务器是常见 x86_64/amd64 架构，制作镜像包时要显式指定 `--platform linux/amd64`；如果服务器是 ARM 架构，再改成 `--platform linux/arm64`。

```bash
export PLATFORM=linux/amd64

docker pull --platform "$PLATFORM" mysql:8.0
docker pull --platform "$PLATFORM" redis:7.2-alpine
docker pull --platform "$PLATFORM" quay.io/coreos/etcd:v3.5.5
docker pull --platform "$PLATFORM" minio/minio:RELEASE.2023-03-20T20-16-18Z
docker pull --platform "$PLATFORM" milvusdb/milvus:v2.4.0
docker pull --platform "$PLATFORM" zilliz/attu:latest
docker pull --platform "$PLATFORM" eclipse-temurin:17-jdk-jammy
docker pull --platform "$PLATFORM" eclipse-temurin:17-jre-jammy
docker pull --platform "$PLATFORM" python:3.11-slim
docker pull --platform "$PLATFORM" node:22-alpine
docker pull --platform "$PLATFORM" nginx:1.27-alpine

docker save \
  mysql:8.0 \
  redis:7.2-alpine \
  quay.io/coreos/etcd:v3.5.5 \
  minio/minio:RELEASE.2023-03-20T20-16-18Z \
  milvusdb/milvus:v2.4.0 \
  zilliz/attu:latest \
  eclipse-temurin:17-jdk-jammy \
  eclipse-temurin:17-jre-jammy \
  python:3.11-slim \
  node:22-alpine \
  nginx:1.27-alpine \
  -o intelligent-qa-images.tar
```

把 `intelligent-qa-images.tar` 传到校内服务器后执行：

```bash
docker load -i intelligent-qa-images.tar
docker images | grep -E 'mysql|redis|milvus|etcd|minio|attu|temurin|node|nginx'
docker compose build --pull=false
docker compose up -d --no-build
```

说明：

- 这种方式不需要阿里云 ACR、腾讯云 TCR 或任何付费镜像服务。
- 如果在 Apple Silicon Mac 上制作镜像包，而服务器是 x86_64/amd64，必须使用 `--platform linux/amd64`，否则可能导出的是 ARM 镜像，服务器构建时仍会去 Docker Hub 拉取 amd64 镜像。
- 离线服务器上不要直接执行 `docker compose up -d --build`，建议使用 `docker compose build --pull=false` 后再 `docker compose up -d --no-build`。
- 后续如果 `docker-compose.yml` 或 Dockerfile 里的基础镜像版本变了，需要重新制作一次离线镜像包。
- 项目前端、后端和模型服务镜像是本地构建的，但构建时仍需要 `node:22-alpine`、`nginx:1.27-alpine`、`eclipse-temurin:*`、`python:3.11-slim` 这些基础镜像，所以也要放进离线包。

#### 方案 B：校内 Docker Hub 镜像代理

如果学校信息化部门已有 Docker Hub 缓存/代理地址，可写入 Docker daemon 配置：

```bash
sudo mkdir -p /etc/docker

cat <<'EOF' | sudo tee /etc/docker/daemon.json
{
  "registry-mirrors": [
    "https://学校提供的镜像代理地址"
  ]
}
EOF

sudo systemctl daemon-reload
sudo systemctl restart docker
docker info | sed -n '/Registry Mirrors/,+5p'
docker pull mysql:8.0
```

#### 方案 C：云厂商镜像加速，可选

如果学校已有阿里云账号且控制台提供免费的 Docker 镜像加速地址，可使用阿里云 ACR “镜像工具 -> 镜像加速器”里的专属地址，格式通常类似：

```text
https://xxxxxxxx.mirror.aliyuncs.com
```

腾讯云文档中的 `https://mirror.ccs.tencentyun.com` 只支持腾讯云内网访问；如果校内服务器不在腾讯云 CVM 内网，该地址大概率不可用。

配置示例：

```bash
sudo mkdir -p /etc/docker

cat <<'EOF' | sudo tee /etc/docker/daemon.json
{
  "registry-mirrors": [
    "https://xxxxxxxx.mirror.aliyuncs.com"
  ]
}
EOF

sudo systemctl daemon-reload
sudo systemctl restart docker
docker info | sed -n '/Registry Mirrors/,+5p'
docker pull mysql:8.0
```

### 2.4 放行端口

至少需要对校内用户开放：

```bash
sudo ufw allow 80/tcp
```

如需运维人员从服务器外部访问后端、Milvus 或 Attu，再按需开放：

```bash
sudo ufw allow 8080/tcp
sudo ufw allow 8000/tcp
```

生产环境不建议对公网开放 `3306`、`6379`、`19530`。如需调试，优先使用 SSH 隧道。

## 3. SSH Deploy Key 配置

### 3.1 本次交接生成的公钥

已生成一对专用 Ed25519 SSH key，用于服务器只读拉取仓库代码。

公钥如下，需要添加到代码仓库平台的 Deploy Key / 部署公钥中：

```text
  ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIIUyVEb6t7kr81SdEVcxYRMLKIh8NF/3VwSqM2EvH8gF intelligent-qa-deploy-2026-07-02
```

指纹：

```text
SHA256:ugWYI72OxorLtk4GwBGnk69vaxT9Idva5LSwG1+c/hQ
```

私钥文件在开发机本地：

```text
deploy/ssh/intelligent_qa_deploy_ed25519
```

注意：

- 私钥不能提交到 Git 仓库。
- 私钥需要作为交接附件，通过 U 盘、加密压缩包或校内安全文件传输方式交给运维人员。
- 仓库平台中只添加上面的公钥。
- 如果人员变更或私钥疑似泄露，应立即删除仓库平台中的该 Deploy Key，并重新生成。

### 3.2 在仓库平台添加公钥

Gitee 推荐路径：

1. 打开项目仓库。
2. 进入“管理”或“设置”。
3. 找到“部署公钥”或“Deploy Keys”。
4. 新增公钥，标题建议填：`school-server-intelligent-qa-deploy`。
5. 粘贴上方 `ssh-ed25519 ...` 公钥。
6. 权限选择“只读”即可，服务器只需要 `pull`。

GitHub 路径：

1. 打开项目仓库。
2. 进入 `Settings`。
3. 进入 `Deploy keys`。
4. 点击 `Add deploy key`。
5. 粘贴上方公钥。
6. 不勾选 `Allow write access`。

### 3.3 在服务器安装私钥

假设私钥文件已安全传到服务器当前用户目录，执行：

```bash
mkdir -p ~/.ssh
chmod 700 ~/.ssh

cp intelligent_qa_deploy_ed25519 ~/.ssh/intelligent_qa_deploy_ed25519
chmod 600 ~/.ssh/intelligent_qa_deploy_ed25519
```

配置 SSH 使用该私钥。Gitee 示例：

```bash
cat >> ~/.ssh/config <<'EOF'
Host gitee.com
  HostName gitee.com
  User git
  IdentityFile ~/.ssh/intelligent_qa_deploy_ed25519
  IdentitiesOnly yes
EOF

chmod 600 ~/.ssh/config
ssh-keyscan gitee.com >> ~/.ssh/known_hosts
ssh -T git@gitee.com
```

GitHub 示例：

```bash
cat >> ~/.ssh/config <<'EOF'
Host github.com
  HostName github.com
  User git
  IdentityFile ~/.ssh/intelligent_qa_deploy_ed25519
  IdentitiesOnly yes
EOF

chmod 600 ~/.ssh/config
ssh-keyscan github.com >> ~/.ssh/known_hosts
ssh -T git@github.com
```

测试命令可能提示没有 shell 权限，这是正常现象；只要提示认证成功或能识别用户即可。

## 4. 首次部署

### 4.1 创建部署目录

建议统一放在 `/opt`：

```bash
sudo mkdir -p /opt/intelligent-qa
sudo chown -R "$USER":"$USER" /opt/intelligent-qa
cd /opt/intelligent-qa
```

### 4.2 克隆代码

推荐使用 Gitee：

```bash
git clone git@gitee.com:Rainhouse-Xingyu/Intelligen_QA_Assistant_System.git .
git remote rename origin gitee
git remote -v
git branch
```

如果使用 GitHub：

```bash
git clone git@github.com:Rainhouse-Xingyu/Intelligen_QA_Assistant_System.git .
git remote -v
git branch
```

如需同时保留两个远端：

```bash
git remote add gitee git@gitee.com:Rainhouse-Xingyu/Intelligen_QA_Assistant_System.git || true
git remote add origin git@github.com:Rainhouse-Xingyu/Intelligen_QA_Assistant_System.git || true
```

### 4.3 配置环境变量

复制示例配置：

```bash
cp .env.example .env
vim .env
```

至少修改以下项：

```properties
MYSQL_ROOT_PASSWORD=请改成强密码
REDIS_PASSWORD=请改成强密码
AIGE_MODEL_BASE_HOST=/opt/aige-models
AIGE_MODEL_DEVICE=cpu
```

如已申请第三方能力，再填写：

```properties
COZE_API_TOKEN=
VOLCENGINE_SPEECH_APP_ID=
VOLCENGINE_SPEECH_ACCESS_TOKEN=
VOLCENGINE_SPEECH_SECRET_KEY=
```

说明：

- `.env` 包含数据库密码和第三方密钥，不能提交到仓库。
- 如果服务器 `80` 端口已被其他 Nginx/Apache 占用，可把 `FRONTEND_PORT=80` 改为其他端口，例如 `FRONTEND_PORT=8088`。
- 如不希望数据库、Redis、Milvus 暴露到宿主机，可在 `docker-compose.yml` 中移除对应 `ports`，只保留容器内访问。

### 4.4 准备模型目录

默认模型目录是宿主机：

```text
/opt/aige-models
```

目录下需要包含：

```text
Qwen3-0.6B
Qwen2.5-1.5B-Instruct
bge-base-zh-v1.5
bge-reranker-large
chinese-macbert-base
```

创建目录：

```bash
sudo mkdir -p /opt/aige-models
sudo chown -R "$USER":"$USER" /opt/aige-models
```

把模型文件放入上述目录后检查：

```bash
ls -lh /opt/aige-models
```

如果暂时没有模型文件，`model-service` 可能无法正常启动，后端涉及本地模型的能力也会受影响。

### 4.5 启动服务

首次启动：

```bash
docker compose up -d --build
```

查看状态：

```bash
docker compose ps
```

查看日志：

```bash
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f model-service
```

首次启动 MySQL 时会自动执行：

```text
backend/llm_qa_sy.sql
```

该初始化脚本只会在 MySQL 数据卷第一次创建时执行。后续如果 SQL 文件变化，已有数据不会自动重放，需要人工执行迁移 SQL。

## 5. 部署验证

在服务器本机验证：

```bash
set -a
. ./.env
set +a

curl -I http://127.0.0.1:${FRONTEND_PORT:-80}
curl http://127.0.0.1:${MODEL_SERVICE_PORT:-18080}/health
docker compose ps
```

浏览器访问：

```text
http://服务器IP/
```

如修改了前端端口，例如 `FRONTEND_PORT=8088`，访问：

```text
http://服务器IP:8088/
```

重点检查：

- 登录页是否正常加载。
- `/api` 请求是否能通过前端 Nginx 反向代理到后端。
- 聊天 WebSocket 是否可用。
- 知识库上传后文件是否保存在 Docker volume `app-uploads`。
- 模型服务 `/health` 是否返回正常。

## 6. 后续代码更新

### 6.1 推荐方式：使用更新脚本

进入项目目录：

```bash
cd /opt/intelligent-qa
chmod +x deploy/update.sh
REMOTE=gitee BRANCH=main ./deploy/update.sh
```

脚本会执行：

1. 检查本地是否有未提交修改。
2. `git fetch gitee main`
3. `git pull --ff-only gitee main`
4. `docker compose up -d --build`
5. 输出服务状态。

如果使用 GitHub：

```bash
REMOTE=origin BRANCH=main ./deploy/update.sh
```

### 6.2 手动更新方式

```bash
cd /opt/intelligent-qa
git status
git pull --ff-only gitee main
docker compose up -d --build
docker compose ps
```

更新后观察日志：

```bash
docker compose logs -f backend
docker compose logs -f frontend
```

### 6.3 更新前备份

建议每次较大版本更新前备份数据库：

```bash
mkdir -p /opt/intelligent-qa-backup
docker compose exec mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  > /opt/intelligent-qa-backup/llm_qa_sy_$(date +%F_%H%M%S).sql
```

备份上传文件 volume：

```bash
docker compose exec backend tar czf - -C /app/uploads . \
  > /opt/intelligent-qa-backup/app-uploads_$(date +%F_%H%M%S).tar.gz
```

Docker volume 的实际名称可用以下命令确认：

```bash
docker volume ls | grep uploads
docker volume ls | grep mysql
```

## 7. 停止、重启与清理

停止服务，不删除数据：

```bash
docker compose down
```

重启服务：

```bash
docker compose restart
```

重新构建并启动：

```bash
docker compose up -d --build
```

删除容器和数据卷，慎用：

```bash
docker compose down -v
```

`down -v` 会删除 MySQL、Redis、Milvus、上传文件等数据卷。生产环境执行前必须确认已有备份。

## 8. 常见问题

### 8.1 `Permission denied (publickey)`

原因通常是 Deploy Key 未添加到仓库，或服务器 SSH 没有使用正确私钥。

排查：

```bash
ssh -vT git@gitee.com
ls -l ~/.ssh/intelligent_qa_deploy_ed25519
cat ~/.ssh/config
```

确认：

- 私钥权限是 `600`。
- `~/.ssh/config` 中 `IdentityFile` 路径正确。
- 仓库平台已添加公钥。
- 公钥添加到了正确仓库，而不是错误账号或错误项目。

### 8.2 前端能打开，接口报错

查看后端日志：

```bash
docker compose logs -f backend
```

重点检查：

- MySQL 是否健康：`docker compose ps mysql`
- Redis 是否健康：`docker compose ps redis`
- `.env` 中密码是否与容器启动时一致
- 后端是否报数据库连接失败、Redis 认证失败、Milvus 连接失败

### 8.3 修改 SQL 后没有生效

MySQL 初始化 SQL 只在数据卷首次创建时执行。已有数据卷不会自动重新执行 `backend/llm_qa_sy.sql`。

生产环境应通过人工迁移 SQL 处理。测试环境可以清空数据后重建：

```bash
docker compose down -v
docker compose up -d --build
```

### 8.4 模型服务启动失败

查看日志：

```bash
docker compose logs -f model-service
```

确认：

- `/opt/aige-models` 存在。
- 五个模型目录名称与文档完全一致。
- `.env` 中 `AIGE_MODEL_BASE_HOST=/opt/aige-models`。
- 服务器内存足够。

### 8.5 端口被占用

查看占用：

```bash
sudo ss -lntp | grep ':80'
```

修改 `.env`：

```properties
FRONTEND_PORT=8088
```

然后重启：

```bash
docker compose up -d
```

### 8.6 拉取 Docker 镜像超时

如果启动时报错类似：

```text
failed to resolve reference "docker.io/library/mysql:8.0"
dial tcp ...:443: i/o timeout
```

说明 Docker 正在从 Docker Hub 拉取镜像，但服务器访问 `registry-1.docker.io` 超时。这不是项目代码问题。

先确认网络：

```bash
curl -I https://registry-1.docker.io/v2/
docker pull mysql:8.0
```

清华 TUNA 可用于安装 Docker CE，但不是 Docker Hub 镜像缓存，因此不能把 `registry-mirrors` 简单配置为清华地址来解决该问题。建议处理方式：

- 不使用付费云服务时，优先制作离线镜像包并在服务器上 `docker load`。
- 让校内运维提供可用的 Docker Hub 缓存/代理地址，并写入 `/etc/docker/daemon.json` 的 `registry-mirrors`。
- 如果学校已有阿里云账号且可免费获取专属镜像加速地址，也可以使用阿里云 ACR 镜像加速器。
- 如果服务器在腾讯云内网，可使用 `https://mirror.ccs.tencentyun.com`。

离线导入示例：

```bash
# 在可访问 Docker Hub 的机器上执行
export PLATFORM=linux/amd64

docker pull --platform "$PLATFORM" mysql:8.0
docker pull --platform "$PLATFORM" redis:7.2-alpine
docker pull --platform "$PLATFORM" quay.io/coreos/etcd:v3.5.5
docker pull --platform "$PLATFORM" minio/minio:RELEASE.2023-03-20T20-16-18Z
docker pull --platform "$PLATFORM" milvusdb/milvus:v2.4.0
docker pull --platform "$PLATFORM" zilliz/attu:latest
docker pull --platform "$PLATFORM" eclipse-temurin:17-jdk-jammy
docker pull --platform "$PLATFORM" eclipse-temurin:17-jre-jammy
docker pull --platform "$PLATFORM" python:3.11-slim
docker pull --platform "$PLATFORM" node:22-alpine
docker pull --platform "$PLATFORM" nginx:1.27-alpine

docker save \
  mysql:8.0 \
  redis:7.2-alpine \
  quay.io/coreos/etcd:v3.5.5 \
  minio/minio:RELEASE.2023-03-20T20-16-18Z \
  milvusdb/milvus:v2.4.0 \
  zilliz/attu:latest \
  eclipse-temurin:17-jdk-jammy \
  eclipse-temurin:17-jre-jammy \
  python:3.11-slim \
  node:22-alpine \
  nginx:1.27-alpine \
  -o intelligent-qa-images.tar

# 传到服务器后执行
docker load -i intelligent-qa-images.tar
docker compose build --pull=false
docker compose up -d --no-build
```

## 9. 安全注意事项

- `.env`、私钥、数据库备份、第三方 API Token 不进入 Git 仓库。
- Deploy Key 建议只读，不给写权限。
- MySQL、Redis、Milvus 端口不要对公网开放。
- 服务器离职交接或权限变更时，应删除旧 Deploy Key 并重新生成。
- 定期备份 MySQL 和上传文件 volume。
- 生产环境修改 `.env` 后，应保存到校内密码管理系统或运维交接系统，不建议只存在单台服务器上。

## 10. 日常运维命令速查

```bash
# 进入项目
cd /opt/intelligent-qa

# 查看服务
docker compose ps

# 查看全部日志
docker compose logs -f

# 查看后端日志
docker compose logs -f backend

# 更新代码并重启
REMOTE=gitee BRANCH=main ./deploy/update.sh

# 离线镜像环境更新代码并重启
OFFLINE_DOCKER=1 REMOTE=gitee BRANCH=main ./deploy/update.sh

# 停止服务
docker compose down

# 启动服务
docker compose up -d

# 重新构建
docker compose up -d --build

# 查看 Git 远端
git remote -v

# 查看当前版本
git log -1 --oneline
```
