# ORION answer-grounding evaluation

## Claim-level contract

Every factual answer must be decomposed into atomic claims before semantic verification. Each claim carries its exact cited evidence IDs. Opinions and explicitly marked creative text are excluded from factual truth scoring.

The semantic judge returns SUPPORTED, CONTRADICTED, INSUFFICIENT or UNVERIFIABLE, plus confidence and evidence IDs. The deterministic auditor constrains those IDs to supplied evidence. A supported verdict requires confidence >= 0.82 and at least one valid supporting source. Contradictory evidence takes precedence. Missing evidence, judge errors, low confidence and invented source IDs cannot be promoted to verified.

## Abstention policy

- VERIFIED: every factual claim is supported and every reference resolves to supplied evidence.
- PARTIALLY_SUPPORTED: at least one claim is supported but one or more claims remain uncertain or contradictory, or invalid citation IDs occur.
- ABSTAIN: no factual claim can be verified, or the claim audit is unavailable.

A caller must not present PARTIALLY_SUPPORTED or ABSTAIN as a fully verified answer.

## Benchmark battery and acceptance gates

AnswerGroundingBenchmarkRunner computes supported precision, contradiction recall, abstention accuracy, unknown-citation rejection, and critical-domain false positives. It refuses to pass a benchmark below minimum sample sizes.

Default target criteria:
- At least 100 labelled factual claims.
- At least 20 contradiction cases and 20 abstention cases.
- >= 95% precision on SUPPORTED labels.
- >= 90% recall for explicit contradictions.
- >= 90% correct abstention on insufficient-evidence cases.
- 100% rejection of fabricated/unknown citation IDs.
- Zero critical-domain false positives.

These are target gates, not measured results. Every benchmark label and evidence span must be human-reviewed. Results must include pinned model/corpus versions and per-domain breakdowns before any semantic-truth claim.

## Implementation boundary

The auditor and benchmark runner are deterministic safeguards around a pluggable semantic entailment judge. This is not yet a production semantic judge, atomic claim extractor, or final-response integration. The judge must compare claim meaning with source passages, not token overlap or generator self-confidence. No semantic-truth demonstration is authorized until those components are integrated and the full benchmark passes.
