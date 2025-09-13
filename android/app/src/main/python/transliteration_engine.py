"""Wrapper around ai4bharat-transliteration for Gujarati.

Exposes `transliterate(sentence: str) -> str` for Kotlin via Chaquopy. If the
official model cannot be initialized (e.g., model pruned or torch removed),
the function now returns the original input unchanged (heuristic fallback removed).
"""

from __future__ import annotations

import traceback
from typing import List

try:
    # ai4bharat transliteration library
    from ai4bharat.transliteration import XlitEngine  # type: ignore
except Exception:  # pragma: no cover - environment dependent
    XlitEngine = None  # type: ignore

_engine = None  # Lazy singleton

SUPPORTED_LANGUAGE = "gu"


def _init_engine():
    global _engine
    if _engine is not None:
        return _engine
    if XlitEngine is None:
        return None
    try:
        # Initialize only Gujarati to reduce memory usage.
        _engine = XlitEngine(lang2use=[SUPPORTED_LANGUAGE], beam_width=4, rescore=True)
        return _engine
    except Exception:
        # Likely missing model weights or torch issues.
        traceback.print_exc()
        _engine = None
        return None


def transliterate(sentence: str) -> str:
    """Return Gujarati transliteration for a Roman sentence.
    Tries model; returns original text if model unavailable.
    """
    sent = sentence.strip()
    if not sent:
        return sent

    engine = _init_engine()
    if engine is not None:
        try:
            # engine.translit_sentence returns list of suggestions; pick top or join tokens.
            # API: engine.translit_sentence(sentence, lang_code)
            out: List[str] = engine.translit_sentence(sent, SUPPORTED_LANGUAGE)  # type: ignore
            if isinstance(out, list) and out:
                # If list of strings, join; if list of tokens, join with space.
                return " ".join(out) if len(out) > 1 else out[0]
        except Exception:
            traceback.print_exc()

    # No heuristic fallback: return original if engine not available or failed.
    return sent


def batch_transliterate(text: str) -> str:
    # Exposed in case we want a more complex API later.
    return transliterate(text)


if __name__ == "__main__":  # Simple manual test
    print(transliterate("namaste tame kem cho"))
