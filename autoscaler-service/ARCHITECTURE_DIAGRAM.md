# MFDS-FPR System Architecture

## Complete System Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    AUTOSCALER SERVICE                           │
│                   (Every 60 seconds)                            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
        ┌─────────────────────────────────────────┐
        │   1. COLLECT METRICS (PrometheusClient) │
        │      - Arrival Rate (λ)                 │
        │      - Latency                          │
        │      - Service Rate (μ = 1/latency)     │
        └─────────────────────────────────────────┘
                              │
                              ▼
        ┌─────────────────────────────────────────┐
        │   2. MFDS DECISION ENGINE               │
        │      ρ = λ / (N × μ)                    │
        │      p0 = [Σ(λ/μ)^n/n! + ...]^-1       │
        │      Lq = p0(λ/μ)^N ρ / (N!(1-ρ)^2)    │
        │      W = (Lq/λ) + (1/μ)                 │
        │                                         │
        │      if W > Tmax → SCALE_OUT            │
        │      if W < Tmin → SCALE_IN             │
        │      else → NO_ACTION                   │
        └─────────────────────────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    │                   │
                    ▼                   ▼
        ┌───────────────────┐  ┌───────────────────┐
        │  3a. MSO ENGINE   │  │ 3b. HYSTERESIS    │
        │  (SCALE_OUT)      │  │ (SCALE_IN)        │
        │                   │  │                   │
        │  Rm = ((λ2-λ1)/λ1)│  │ counter++         │
        │       × N         │  │ if counter >= 3:  │
        │  Nnew = N + Rm    │  │   Nnew = N - 1    │
        └───────────────────┘  └───────────────────┘
                    │                   │
                    └─────────┬─────────┘
                              ▼
        ┌─────────────────────────────────────────┐
        │   4. DOCKER SCALING SERVICE             │
        │      docker service scale               │
        │      autoscale_<service>=<Nnew>         │
        └─────────────────────────────────────────┘
                              │
                              ▼
        ┌─────────────────────────────────────────┐
        │   5. FPR DECISION ENGINE                │
        │      weight_i = replicas_i × λ_i        │
        │      P(i) = weight_i / Σweights         │
        │      Update NGINX routing config        │
        └─────────────────────────────────────────┘
```

## Data Flow

```
┌──────────────┐
│   NGINX      │ ← Users
│   Gateway    │
└──────┬───────┘
       │
       ▼
┌──────────────────────────────────────┐
│  Microservices (Docker Swarm)        │
│  ┌────────┐  ┌────────┐  ┌────────┐ │
│  │ Auth   │  │ Course │  │ Seat   │ │
│  │ N=2    │  │ N=3    │  │ N=5    │ │
│  └────────┘  └────────┘  └────────┘ │
└──────────────────────────────────────┘
       │
       │ /actuator/prometheus
       ▼
┌──────────────┐
│  Prometheus  │ ← Scrapes metrics every 15s
│  :9090       │
└──────┬───────┘
       │
       │ PromQL queries
       ▼
┌──────────────┐
│  Autoscaler  │ ← Evaluates every 60s
│  :8083       │
└──────┬───────┘
       │
       │ docker service scale
       ▼
┌──────────────┐
│ Docker Swarm │ ← Scales containers
└──────────────┘
```

## Decision Tree

```
START
  │
  ▼
Collect Metrics (λ, latency, N)
  │
  ▼
Calculate μ = 1/latency
  │
  ▼
Calculate ρ = λ/(N×μ)
  │
  ▼
Is ρ >= 1.0? ──YES──> SCALE_OUT (MSO)
  │                         │
  NO                        ▼
  │                   Calculate Rm
  ▼                         │
Calculate p0, Lq, W         ▼
  │                   Nnew = N + Rm
  ▼                         │
Is W > Tmax? ──YES──> SCALE_OUT (MSO)
  │                         │
  NO                        ▼
  │                   Scale Docker
  ▼                         │
Is W < Tmin? ──YES──> Increment Counter
  │                         │
  NO                        ▼
  │                   Counter >= 3? ──YES──> SCALE_IN
  │                         │                    │
  ▼                         NO                   ▼
NO_ACTION                   │              Nnew = N - 1
  │                         │                    │
  │                         │                    ▼
  │                         │              Scale Docker
  │                         │                    │
  └─────────────────────────┴────────────────────┘
                            │
                            ▼
                  Update FPR Routing
                            │
                            ▼
                    Wait 60 seconds
                            │
                            ▼
                         REPEAT
```

## Component Interaction

```
┌─────────────────────────────────────────────────────────┐
│                  AutoscalingService                     │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐          │
│  │   MFDS    │  │    MSO    │  │    FPR    │          │
│  │  Engine   │  │  Engine   │  │  Engine   │          │
│  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘          │
│        │              │              │                  │
│        │ Decision     │ Replicas     │ Probabilities    │
│        └──────────────┴──────────────┘                  │
└─────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ Prometheus   │    │ Docker Swarm │    │ NGINX Config │
│ Client       │    │ Scaling      │    │ (Future)     │
└──────────────┘    └──────────────┘    └──────────────┘
```

## Scaling Example Timeline

```
Time    Event                   λ      N    W      Action
────────────────────────────────────────────────────────
00:00   Initial state          50     2    0.03   NO_ACTION
01:00   Traffic increases      100    2    0.09   SCALE_OUT (W>Tmax)
01:00   MSO calculates Rm=2    100    4    0.04   Scaled
02:00   Traffic stable         100    4    0.04   NO_ACTION
03:00   Traffic decreases      20     4    0.015  Counter=1
04:00   Traffic still low      20     4    0.015  Counter=2
05:00   Traffic still low      20     4    0.015  Counter=3, SCALE_IN
05:00   Scaled down            20     3    0.018  Scaled
06:00   Traffic stable         20     3    0.018  NO_ACTION
```

## Queue Model Visualization

```
Arrival Rate (λ)
      │
      ▼
┌─────────────┐
│   Queue     │ ← Lq (Queue Length)
│   ░░░░░     │
└─────────────┘
      │
      ▼
┌─────────────────────────────┐
│   Servers (N replicas)      │
│   [S1] [S2] [S3] ... [SN]   │
│    μ    μ    μ  ...   μ     │
└─────────────────────────────┘
      │
      ▼
Response Time (W) = Queue Wait + Service Time
                  = (Lq/λ) + (1/μ)
```

## SLA Bounds

```
Response Time (W)
      │
      │                    ┌─────────────┐
      │                    │  SCALE OUT  │
Tmax ─┼────────────────────┤             │
0.08s │                    │  (W > Tmax) │
      │                    └─────────────┘
      │
      │    ┌─────────────────────────┐
      │    │      NO ACTION          │
      │    │  (Tmin ≤ W ≤ Tmax)      │
      │    └─────────────────────────┘
      │
Tmin ─┼────────────────────┬─────────────┐
0.02s │                    │  SCALE IN   │
      │                    │  (W < Tmin) │
      │                    └─────────────┘
      │
      └────────────────────────────────────> Time
```

## System States

```
┌──────────────┐
│   IDLE       │ ← λ = 0
│   N = 1      │
└──────┬───────┘
       │ Traffic arrives
       ▼
┌──────────────┐
│   NORMAL     │ ← Tmin ≤ W ≤ Tmax
│   N = 2-4    │
└──────┬───────┘
       │ Traffic spike
       ▼
┌──────────────┐
│   OVERLOAD   │ ← W > Tmax
│   Scaling... │
└──────┬───────┘
       │ MSO scales
       ▼
┌──────────────┐
│   SCALED     │ ← W normalized
│   N = 6-10   │
└──────┬───────┘
       │ Traffic drops
       ▼
┌──────────────┐
│   UNDERLOAD  │ ← W < Tmin
│   Counter++  │
└──────┬───────┘
       │ After 3 cycles
       ▼
┌──────────────┐
│   SCALE IN   │ ← N decreases
│   N = N - 1  │
└──────────────┘
```
