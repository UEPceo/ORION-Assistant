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


## Runtime integration

The call response path now buffers generated text and does not send draft sentences to TTS. Once generation completes, ORION retrieves the source context again, runs claim decomposition and semantic entailment assessment with the loaded local language model, applies the deterministic auditor, and only then speaks/commits the final response. A malformed judge response or verification exception fails closed with a clear abstention message. The judge sees all retrieved passages for contradiction detection; a claim is only marked supported when the judge identifies at least one source explicitly cited in the generated answer. Contradictions can be identified in any retrieved passage, not only cited passages.

The local judge is a semantic model assessment, not a mathematical proof. Its labels still require benchmark calibration and human-reviewed gold cases. The deterministic layer prevents fabricated source identifiers, missing citations, low confidence, and unsupported verdicts from being promoted to VERIFIED.
