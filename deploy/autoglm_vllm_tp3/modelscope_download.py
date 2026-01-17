"""Download AutoGLM-Phone weights from ModelScope to a local directory.

You need a working Python env with `modelscope` installed.

Example:
  python modelscope_download.py --model-id ZhipuAI/AutoGLM-Phone-9B --local-dir /data/models/AutoGLM-Phone-9B

Tip:
- If you want the multilingual checkpoint, search ModelScope for the exact ID and pass it via --model-id.
"""

from __future__ import annotations

import argparse


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--model-id", required=True, help="ModelScope model id (copy from ModelScope page)")
    p.add_argument("--local-dir", required=True, help="Where to place the downloaded snapshot")
    p.add_argument(
        "--cache-dir",
        default=None,
        help="Optional cache dir. If unset, modelscope default cache is used.",
    )
    args = p.parse_args()

    try:
        from modelscope.hub.snapshot_download import snapshot_download
    except Exception as e:
        print("ERROR: modelscope is not installed. Install it in your conda env first.")
        print(str(e))
        return 2

    snapshot_download(
        model_id=args.model_id,
        cache_dir=args.cache_dir,
        local_dir=args.local_dir,
        local_dir_use_symlinks=False,
    )

    print("OK")
    print(f"Downloaded to: {args.local_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
