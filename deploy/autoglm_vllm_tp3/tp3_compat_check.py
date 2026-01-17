"""TP=3 compatibility check for AutoGLM-Phone-9B weights.

Usage (recommended, uses the same environment as serving):
  python tp3_compat_check.py /model

It inspects common config fields that often constrain tensor parallel sharding.
This is a best-effort precheck; passing does not guarantee vLLM will start,
but failing usually means TP=3 will NOT work.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path


def _get_int(obj, name: str):
    v = getattr(obj, name, None)
    if v is None:
        return None
    try:
        return int(v)
    except Exception:
        return None


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: python tp3_compat_check.py /path/to/local/model")
        return 2

    model_dir = Path(sys.argv[1]).expanduser().resolve()
    if not model_dir.exists():
        print(f"Model path not found: {model_dir}")
        return 2

    try:
        from transformers import AutoConfig
    except Exception as e:
        print("ERROR: transformers is not available in this environment.")
        print(str(e))
        return 2

    cfg = AutoConfig.from_pretrained(str(model_dir), trust_remote_code=True)

    fields = {
        "model_type": getattr(cfg, "model_type", None),
        "architectures": getattr(cfg, "architectures", None),
        "hidden_size": _get_int(cfg, "hidden_size"),
        "num_attention_heads": _get_int(cfg, "num_attention_heads"),
        "num_key_value_heads": _get_int(cfg, "num_key_value_heads"),
        "head_dim": _get_int(cfg, "head_dim"),
    }

    print("=== AutoGLM TP=3 compatibility precheck ===")
    print(json.dumps(fields, ensure_ascii=False, indent=2))

    reasons: list[str] = []

    nah = fields["num_attention_heads"]
    if isinstance(nah, int) and nah > 0 and nah % 3 != 0:
        reasons.append(f"num_attention_heads={nah} is not divisible by 3")

    nkvh = fields["num_key_value_heads"]
    if isinstance(nkvh, int) and nkvh > 0 and nkvh % 3 != 0:
        # Some architectures shard kv heads too.
        reasons.append(f"num_key_value_heads={nkvh} is not divisible by 3")

    hs = fields["hidden_size"]
    if isinstance(hs, int) and hs > 0 and hs % 3 != 0:
        # Not always required, but often indicates sharding constraints.
        reasons.append(f"hidden_size={hs} is not divisible by 3 (may block TP=3)")

    if reasons:
        print("\nRESULT: TP=3 is LIKELY NOT SUPPORTED for this checkpoint.")
        print("Reasons:")
        for r in reasons:
            print(f"- {r}")
        print("\nSuggested fallbacks:")
        print("- Use TP=4 (single instance, most stable)")
        print("- Or run 3x single-GPU instances for true 3-way concurrency")
        return 1

    print("\nRESULT: TP=3 looks POSSIBLE based on config fields.")
    print("Next: try starting vLLM with --tensor-parallel-size 3.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
