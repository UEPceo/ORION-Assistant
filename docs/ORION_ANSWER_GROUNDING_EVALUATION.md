# ORION answer-grounding evaluation

## Claim-level contract

Every factual answer must be decomposed into atomic claims before semantic verification. Each claim carries its exact cited evidence IDs. Opinions, preferences and explicitly marked creative text are excluded from factual truth scoring.

The semantic judge returns SUPPORTED, CONTRADICTED, INSUFFICIENT or UNVERIFIABLE, plus confidence and evidence IDs. The deterministic auditor constrains those IDs to the evidence actually supplied. A supported verdict requires confidence >= 0.82 and at least one valid supporting source. Contradictory evidence takes precedence. Missing evidence, judge errors, low confidence and invented source IDs cannot be promoted to verified.

## Abstention policy

- VERIFIED: every factual claim is supported and every reference resolves to supplied evidence.
- PARTIALLY_SUPPORTED: at least one claim is supported but one or more claims remain uncertain or contradictory, or invalid citation IDs occur.
- ABSTAIN: no factual claim can be verified, or the claim audit is unavailable.

A caller must not present PARTIALLY_SUPPORTED or ABSTAIN as a fully verified answer. It should qualify supported fragments and explicitly withhold uncertain claims.

## Current implementation boundary

AnswerGroundingAuditor is a deterministic policy boundary around a pluggable semantic entailment judge. This commit adds the contract and regression tests; it does not yet provide a production semantic judge, atomic claim extractor, nor wire the audit into the final response path. Those are mandatory follow-up tasks before the semantic-truth demonstration. The judge must compare claim meaning with source passages, not rely on token overlap or the generator's own confidence.

## Evaluation battery and release gates

The integrated battery must contain positive entailment, partial entailment, contradiction, unsupported claim, wrong citation, source disagreement, temporal staleness, jurisdiction mismatch, ambiguous question, out-of-domain question, and insufficient-evidence/abstention cases. Each case must have a human-reviewed expected label and evidence span.

Initial release criteria proposed:
- 100% rejection of fabricated/unknown citation IDs.
- 100% abstention on empty evidence, judge failure and explicit contradiction.
- >= 95% precision on SUPPORTED labels.
- >= 90% recall for explicit contradictions.
- >= 90% correct abstention on insufficient-evidence cases.
- 0 critical false-positive legal/safety claims.
- Report sample size and per-domain confidence intervals; do not treat a small test set as proof of universal truth.

These are target gates, not measured results. A benchmark is not passed until executed against a pinned model, pinned corpus and reproducible test fixture.
