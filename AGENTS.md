---
id: compintel-claude
title: Competitive Intelligence Dashboard — Runbook
status: active
doc_type: how-to
topic: compintel-runbook
authoritative: true
last_verified: 2026-05-07
authoritative_for:
  - compintel actor runbook
related:
  - adr-2605072000-langgraph-agent-loop-pattern
---

# Competitive Intelligence Dashboard (`compintel.etzhayyim.com`)

ADR-2605072000 business model ④ of 5.

## Actor

- DID: `did:web:compintel.etzhayyim.com`
- Nanoid: `cpti0001`
- CF Worker: `60-apps/etzhayyim-project-compintel/appview/compintel-cpti0001/`
- Python worker: `40-engine/kotoba/crates/kotoba-kotodama/py/src/kotodama/compintel_worker_main.py`
- BPMN: `etzhayyim-root/00-contracts/bpmn/com/etzhayyim/compintel/`

## Flow

**Weekly refresh** (Monday 08:00 JST):
```
WeeklyTimer → ResearchAllCompetitors (LangGraph batch) → ScoreThreats → AlertGateway:
  hasHighSeverityAlerts=true  → SendDigest (Resend) → End
  hasHighSeverityAlerts=false → End
```

**Track new competitor** (triggered by createSequence XRPC):
```
Start → InitialResearch (LangGraph deep) → Score → End
```

## LangGraph nodes (compintel.run_research_agent)

1. `fetch_signals` — query vertex_news_article for mentions of competitor
2. `analyze_pricing` — LLM extract pricing signals from news
3. `analyze_product` — LLM extract product/feature signals
4. `analyze_hiring` — LLM extract headcount/hiring signals
5. `score_threat` — LLM synthesize → threat score (0-1) + executive summary
6. `store_snapshot` — INSERT to vertex_compintel_snapshot, UPDATE competitor threat_score

## Tables

| Table | Purpose |
|---|---|
| `vertex_compintel_competitor` | Tracked competitor list |
| `vertex_compintel_snapshot` | Weekly intelligence snapshots |
| `vertex_compintel_alert` | High-severity change alerts |
| `edge_compintel_snapshot` | Competitor → snapshot edge |

No PII — all data is public competitive intelligence (sensitivity_ord=0).

## Env

```
AGENTGATEWAY_MCP_URL        gRPC address (default 127.0.0.1:26500)
RW_URL               RisingWave postgres URL
ANTHROPIC_API_KEY
RESEND_API_KEY
RESEND_FROM          sender (default digest@etzhayyim.com)
DIGEST_TO            recipient for weekly digest email
ADS_XRPC_URL         ads.etzhayyim.com base (default https://adsm4d5c.etzhayyim.com)
```

## Start worker

```bash
cd 40-engine/kotoba/crates/kotoba-kotodama/py
python -m kotodama.compintel_worker_main
# or: kotodama-compintel-worker
```
