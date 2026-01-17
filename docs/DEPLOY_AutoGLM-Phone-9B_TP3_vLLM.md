# AutoGLM-Phone-9B（vLLM，TP=3）四卡3090服务器部署指南（一步一步）

> 目标：在 4×RTX 3090 服务器上，只用 3 张卡做 **张量并行（Tensor Parallel, TP=3）** 启动一个 AutoGLM-Phone-9B 的 **OpenAI 兼容**服务；第 4 张卡留作其它任务（例如后续精调/另一个服务实例）。
>
> 重要前置：TP=3 **不一定**被模型结构支持（常见限制：注意力头数/某些分片维度必须能被 3 整除）。本指南包含“启动前校验”和“失败回退策略”，确保你不会卡死。

---

## 0. 你将得到什么

- 一个模型服务：`http://<server-ip>:8000/v1`（OpenAI 兼容）
- 能被 `temp/Open-AutoGLM-main/main.py` 直接调用：
  - `--base-url http://<server-ip>:8000/v1`
  - `--model autoglm-phone-9b-multilingual`（或你自定义的 served-model-name）

---

## 1. 基础检查（只需要做一次）

在服务器上确认：

1) NVIDIA 驱动正常
- 目标现象：`nvidia-smi` 能看到 4 张 3090

2) 磁盘空间充足
- 模型权重下载大约 ~20GB（官方提示），再加上缓存/日志，建议预留 **>= 80GB**

3) 网络可访问模型仓库
- 需要能从 Hugging Face 或其它镜像（ModelScope 等）拉取 `zai-org/AutoGLM-Phone-9B-Multilingual`。

> 注：这一步我不在仓库里执行命令；你在服务器执行即可。

---

## 1.5 你的工作目录约定（你这台机器是全新的）

你要求“进入服务器后所有东西都在我的账户下运行，统一放 `/data/zhangyongqi/`”。

本指南后续将默认使用这些目录（你可以照抄创建）：

- 仓库目录：`/data/zhangyongqi/aries`
- 模型目录：`/data/zhangyongqi/models/AutoGLM-Phone-9B`
- HuggingFace/Transformers 缓存：`/data/zhangyongqi/hf_cache`
- 日志目录：`/data/zhangyongqi/logs`

在服务器上执行：

```bash
mkdir -p /data/zhangyongqi/{aries,models,hf_cache,logs}
```

---

## 2. 宿主机准备（Ubuntu + Docker + NVIDIA 容器运行时）

你选择的是 **Ubuntu + Docker 部署**，并且只用 GPU **0/1/2** 做 TP=3。

### 2.1 检查 NVIDIA 驱动

在宿主机上运行：

```bash
nvidia-smi
```

确认能看到 4 张 3090。

### 2.2 安装/检查 Docker 与 NVIDIA Container Toolkit

你需要满足：Docker 能调用 GPU（容器里能看到 GPU）。

快速自检（成功时会打印 GPU 信息）：

```bash
docker run --rm --gpus all nvidia/cuda:12.4.1-base-ubuntu22.04 nvidia-smi
```

如果这里失败，再去补装 `nvidia-container-toolkit`（你系统如何装取决于发行版与现有 Docker 版本）。

> 注意：安装/配置 Docker、NVIDIA Container Toolkit 通常需要 `sudo` 权限。
> 如果你没有 sudo，需要找管理员先把“Docker 可用 + 容器可用 GPU”这一步做好；否则后续无法继续。

---

## 3. 安装 Miniconda（用于 ModelScope 下载与工具脚本）

> 推理服务用 Docker 跑；Miniconda 只负责 **ModelScope 下载模型**、以及（可选）在宿主机做一些检查脚本。

### 3.1 安装 Miniconda（Ubuntu）

```bash
mkdir -p /data/zhangyongqi/miniconda3
curl -fsSL -o /tmp/miniconda.sh https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh
bash /tmp/miniconda.sh -b -p /data/zhangyongqi/miniconda3
/data/zhangyongqi/miniconda3/bin/conda init bash
exec bash
```

### 3.2 创建工具环境（Python 3.10）

```bash
conda create -n autoglm-tools python=3.10 -y
conda activate autoglm-tools
python -m pip install -U pip
pip install modelscope
```

> 如果你所在网络访问 PyPI 有困难，你需要配置 pip 镜像源；这与模型本身无关。

---

## 4. 用 ModelScope 下载模型到宿主机目录

你说你要走 **ModelScope**，建议把权重落盘到：`/data/models/...`。

1) 在 ModelScope 网站搜索 AutoGLM-Phone-9B，复制 **模型 ID**（常见例如 `ZhipuAI/AutoGLM-Phone-9B`；以你页面实际为准）

2) 在本仓库的 `deploy/autoglm_vllm_tp3` 目录执行下载：

```bash
cd /data/zhangyongqi/aries/deploy/autoglm_vllm_tp3
conda activate autoglm-tools

python modelscope_download.py \
  --model-id <把ModelScope的模型ID粘贴到这里> \
  --local-dir /data/zhangyongqi/models/AutoGLM-Phone-9B
```

下载完成后，你会得到一个本地目录（里面有 `config.json`、tokenizer、权重等）。

---

## 5. 启动前：先验证“TP=3 是否可行”（强烈建议）

TP=3 是否可行，常见取决于：
- `num_attention_heads` 是否能被 3 整除
- 某些模型维度分片是否支持 3

### 5.1 使用仓库脚本做预检（推荐）

本仓库已提供：`deploy/autoglm_vllm_tp3/tp3_compat_check.py`。

我们推荐在 **同一个推理镜像环境**里跑检查（确保 transformers 版本一致）：

```bash
cd /data/zhangyongqi/aries/deploy/autoglm_vllm_tp3

docker build -t autoglm-vllm:0.12.0-tp3 -f docker/Dockerfile docker

docker run --rm -it \
  -v "$(pwd):/work" \
  -v /data/zhangyongqi/models/AutoGLM-Phone-9B:/model:ro \
  autoglm-vllm:0.12.0-tp3 \
  python /work/tp3_compat_check.py /model
```

> 如果头数不能被 3 整除：**不要硬上 TP=3**，直接回退到 TP=4（单实例吃满4卡）或改“多实例并发”。

---

## 6. 用 3 张 GPU 启动 vLLM（严格遵循官方多模态参数 + 增加 TP=3）

### 6.1 选择使用哪 3 张卡

你要“3卡张量并行”，最稳的方法是**屏蔽第4张卡**，让进程只看见 3 张 GPU。

- 例：使用 GPU 0/1/2，保留 GPU3
- 典型做法：设置 `CUDA_VISIBLE_DEVICES=0,1,2`

### 6.2 一键启动脚本（推荐）

本仓库已提供：`deploy/autoglm_vllm_tp3/start_vllm_tp3.sh`。

在宿主机执行：

```bash
cd /data/zhangyongqi/aries/deploy/autoglm_vllm_tp3
chmod +x start_vllm_tp3.sh

export MODEL_DIR_HOST=/data/zhangyongqi/models/AutoGLM-Phone-9B
export GPUS_HOST=0,1,2
export PORT=8000
export SERVED_MODEL_NAME=autoglm-phone-9b-multilingual

./start_vllm_tp3.sh
```

成功启动后：
- OpenAI 兼容端点：`http://<server-ip>:8000/v1`

智谱官方 vLLM 参数（来自 `temp/Open-AutoGLM-main/README.md` / `README_en.md`）包含：
- `--allowed-local-media-path /`
- `--mm-encoder-tp-mode data`
- `--mm_processor_cache_type shm`
- `--mm_processor_kwargs '{"max_pixels":5000000}'`
- `--max-model-len 25480`
- `--chat-template-content-format string`
- `--limit-mm-per-prompt '{"image":10}'`

你需要在此基础上增加：
- `--tensor-parallel-size 3`

建议 served-model-name（客户端将用它作为 `--model` 参数）：
- `autoglm-phone-9b-multilingual`

模型权重名：
- `zai-org/AutoGLM-Phone-9B-Multilingual`

端口：
- `8000`

> 如果你在容器里跑：也可以用 `vllm/vllm-openai:v0.12.0`，进入容器后 `pip install -U transformers --pre` 再启动。

---

## 7. 验收（官方最短路径）

### 7.1 先验收模型服务

在 `temp/Open-AutoGLM-main` 目录下，使用官方脚本：
- `scripts/check_deployment_cn.py` 或 `scripts/check_deployment_en.py`
- base-url 指向：`http://localhost:8000/v1`
- model 指向：`autoglm-phone-9b-multilingual`（要与你 served-model-name 一致）

### 7.2 再验收 Agent 端

用官方示例跑一次任务（随便一个短任务就行），例如：
- `python main.py --base-url http://<server-ip>:8000/v1 --model "autoglm-phone-9b-multilingual" "打开Chrome浏览器"`

> 提醒：Agent 成功与否还取决于手机端 ADB 权限、ADB Keyboard 等配置。

---

## 8. 常见失败与回退策略（非常重要）

### 8.1 TP=3 启动失败（最常见）

典型表现：
- 报错提示某些维度无法按 3 分片

回退方案：
1) **TP=4**：单实例吃满 4 卡（最稳）
2) **多实例并发**：开 3 个单卡实例（0/1/2），第四卡留给精调（最符合“3路并发”）
3) **TP=2**：两卡一实例，可做 2 路并发（2+2）

### 8.2 显存 OOM / KV cache 顶爆

触发因素：
- `--max-model-len 25480` 很长
- 并发请求多、图片多（`image` up to 10）

缓解思路：
- 先降低并发（例如 max-num-seqs）
- 必要时降低 max-model-len（会影响长任务能力）

### 8.3 Transformers 版本不对导致输出异常

官方判断：
- 思维链很短、乱码、或 check_deployment 输出异常，常是部署失败

处理：
- 确保 `transformers --pre` 已升级
- 依赖冲突提示可先忽略（官方明确说可忽略）

---

## 9. 建议的生产化形态（你后续会用到）

- 推理：vLLM（单独 conda env 或 docker）
- 精调：单独 conda env（LoRA/QLoRA）
- 第4张 GPU：留作训练/离线批处理/第二套服务

---

## 下一步（你现在就能做）

你已经确认：Ubuntu + Docker + ModelScope + GPU 0/1/2。

按顺序执行即可：

1) 第 2 节：确认 Docker 能用 GPU
2) 第 3~4 节：用 ModelScope 下载模型到 `/data/models/AutoGLM-Phone-9B`
3) 第 5 节：先跑一次 TP=3 预检
4) 第 6~7 节：启动 vLLM，然后用 `temp/Open-AutoGLM-main/scripts/check_deployment_cn.py` 验收
