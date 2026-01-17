# Deploy AutoGLM-Phone-9B on Ubuntu (Docker, vLLM, TP=3, GPUs 0/1/2)

本目录提供你要的“从 0 到跑起来”的可执行脚本：

- `modelscope_download.py`：用 ModelScope 把模型下载到宿主机目录
- `tp3_compat_check.py`：启动前检查 TP=3 是否大概率可行
- `start_vllm_tp3.sh`：用 Docker 在 GPU 0/1/2 上启动 vLLM OpenAI 服务

> 重要：TP=3 不是所有 checkpoint 都支持；请务必先跑 `tp3_compat_check.py`。

---

## 1) Miniconda（宿主机上，用于下载模型 & 运行检查）

你只需要一个“工具环境”即可，推理服务本身用 Docker。

建议创建环境：`autoglm-tools`（Python 3.10）
- 安装 `modelscope`

> 如果你已经有 Python 环境，也可以不用 conda。

---

## 2) 用 ModelScope 下载模型到宿主机

1. 在 ModelScope 网站搜索 AutoGLM-Phone-9B，复制模型 ID（例如常见的是 `ZhipuAI/AutoGLM-Phone-9B`）。
2. 选择落盘目录（本方案默认：`/data/zhangyongqi/models/...`）。
3. 用 `modelscope_download.py` 下载。

下载完成后，你会得到一个包含 `config.json`、权重文件等的目录。

---

## 3) 在 Docker 环境里做 TP=3 兼容性预检（推荐）

推理镜像会安装 `transformers --pre`，所以用它来跑检查最贴近真实启动环境。

你可以在启动服务前先执行：
- `python tp3_compat_check.py /model`

（把宿主机模型目录挂载到容器的 `/model`）

如果输出提示“不支持 TP=3”，不要硬上，直接回退：TP=4 或多实例并发。

---

## 4) 启动 vLLM（TP=3，GPU 0/1/2）

直接运行：
- `./start_vllm_tp3.sh`

可通过环境变量覆盖：
- `MODEL_DIR_HOST`：宿主机模型目录（默认 `/data/zhangyongqi/models/AutoGLM-Phone-9B`）
- `GPUS_HOST`：默认 `0,1,2`
- `PORT`：默认 `8000`
- `SERVED_MODEL_NAME`：默认 `autoglm-phone-9b-multilingual`

服务启动后：
- OpenAI 兼容地址：`http://<server-ip>:8000/v1`

---

## 5) 用官方脚本验收

在仓库 `temp/Open-AutoGLM-main` 目录下：
- `python scripts/check_deployment_cn.py --base-url http://<server-ip>:8000/v1 --model autoglm-phone-9b-multilingual`

通过后，再用 `python main.py ...` 跑一个短任务验收（手机端 ADB 权限别漏）。
