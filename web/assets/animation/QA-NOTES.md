# Podong v3 motion QA
All working files on D. Codex provider explicitly retained. Existing accepted base identity retained; calm raw reused from v2, no extra motion references.
Geometry/extraction: all 72 components extracted; no errors or chroma warnings. Every active row has 16 sequential drawings. Inspect full motion-presence warning 0.0095<0.01 retained; not hidden by threshold changes.
calm: pass, small blink and breathing, long resting interval in production.
recommend: best-effort, continuous anticipation / broad right-arm indication / settling. Naturalness remains subjective; 10fps, not rigged vector motion.
small: best-effort, continuous broad left-arm greeting, no turn discontinuity.
full: best-effort, one crouch-stretch cheer; inspect low adjacent-difference warning above. Clear start/peak/return; avoids old 8-frame front/back snap.
recovery: best-effort, slump / broad overhead stretch / relief, readable start-middle-end.
Source resolution is lower for 16-pose rows; fixed clip viewport enlarges ink consistently, with some softness. No per-frame rescaling or fabricated optical-flow interpolation.
Runtime: browser observed all 0..15 frames in every active state; production keeps once-only event playback. Preview page repeats only when explicitly labelled repeat-preview checkbox is checked.
