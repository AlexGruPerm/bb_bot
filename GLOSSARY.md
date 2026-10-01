# bb_bot

Trading system that gathers Bybit futures data into Postgres and periodically analyzes price and open-interest dynamics, exposing results through a Telegram bot.

## Language

**Metric**:
A definition of an analytical calculation over futures data: its name and the aggregate table it writes its results into. A metric is inert until it has a metric run.
_Avoid_: metric type, metric template

**Metric run**:
A configured, executable instance of a metric: how often it runs and which analysis parameters (filtering window, comparison window, open-interest threshold, price and OI change thresholds) feed into the metric's queries. One metric may have several runs.
_Avoid_: metric configuration, metric job, metric meta

**Metric group**:
A price/volume state bucket computed for each future by the metric's queries (e.g. price up + OI up, price down + OI down, flat).
_Avoid_: cluster, class, category

**Aggregate metric result**:
The summary rows a metric run writes, aggregated across all analyzed futures by metric group.
_Avoid_: total metrics

**Per-symbol metric result**:
The rows a metric run writes, broken down per future symbol, each assigned a metric group.
_Avoid_: symbol metrics

## Metrics Analysis

**Open interest**:
The total value of open derivative positions for a futures symbol, used here as a filter (only large- OI futures are analyzed) and as a signal alongside price change.
_Avoid_: OI, positions volume