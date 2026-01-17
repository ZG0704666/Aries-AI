#!/usr/bin/env bash
set -euo pipefail

# ---- User config ----
PORT="${PORT:-8000}"
SERVED_MODEL_NAME="${SERVED_MODEL_NAME:-autoglm-phone-9b-multilingual}"

# Local path on HOST where the ModelScope-downloaded weights live
MODEL_DIR_HOST="${MODEL_DIR_HOST:-/data/zhangyongqi/models/AutoGLM-Phone-9B}"

# GPU selection on the HOST (you requested 0,1,2)
GPUS_HOST="${GPUS_HOST:-0,1,2}"

# Optional: HF cache on host (can be empty)
HF_CACHE_HOST="${HF_CACHE_HOST:-/data/zhangyongqi/hf_cache}"

# Image tag
IMAGE="${IMAGE:-autoglm-vllm:0.12.0-tp3}"

echo "Using GPUs: ${GPUS_HOST}"
echo "Model dir (host): ${MODEL_DIR_HOST}"

# Build derived image once (adds transformers --pre as required by Zhipu docs)
docker build -t "${IMAGE}" -f "$(dirname "$0")/docker/Dockerfile" "$(dirname "$0")/docker"

# NOTE:
# - --ipc=host is important for shm (mm_processor_cache_type shm)
# - We mount model to /model and point vLLM --model /model
# - We also mount HF cache (optional)

docker run --rm -it \
  --gpus "device=${GPUS_HOST}" \
  --ipc=host \
  -p "${PORT}:8000" \
  -v "${MODEL_DIR_HOST}:/model:ro" \
  -v "${HF_CACHE_HOST}:/root/.cache/huggingface" \
  -e "HF_HOME=/root/.cache/huggingface" \
  "${IMAGE}" \
  python3 -m vllm.entrypoints.openai.api_server \
    --tensor-parallel-size 3 \
    --served-model-name "${SERVED_MODEL_NAME}" \
    --allowed-local-media-path / \
    --mm-encoder-tp-mode data \
    --mm_processor_cache_type shm \
    --mm_processor_kwargs '{"max_pixels":5000000}' \
    --max-model-len 25480 \
    --chat-template-content-format string \
    --limit-mm-per-prompt '{"image":10}' \
    --model /model \
    --port 8000
