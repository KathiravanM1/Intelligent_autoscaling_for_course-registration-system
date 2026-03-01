# Autoscaling Dashboard

## Overview
Real-time web dashboard displaying all intermediate steps of the intelligent autoscaling system including:
- **MFDS Algorithm** (Multi-Feature Decision System)
- **FPR Algorithm** (Future Prediction & Reaction)
- **MSO Strategy** (Multi-Step Optimization)
- Service metrics (latency, arrival rate, utilization)
- Scaling actions (scale in/out)

## Access Dashboard

Once the autoscaler-service is running, access the dashboard at:

```
http://localhost:8083/dashboard
```

## Features

### Service Metrics Panel
- Current replica count
- Latency (response time)
- Arrival rate (λ)
- Delta (Δλ) - rate of change
- Utilization (ρ)
- Current action

### Events Timeline
- Real-time autoscaling events
- Algorithm decisions (MFDS, FPR, MSO)
- Scaling actions with detailed metrics
- Color-coded events:
  - 🔴 Red: Scale Out
  - 🟢 Green: Scale In
  - ⚪ Gray: No Action

## Build & Run

```bash
cd autoscaler-service
mvn clean package
java -jar target/autoscaler-service-0.0.1-SNAPSHOT.jar
```

## Dashboard Updates
The dashboard auto-refreshes every 2 seconds to display the latest metrics and events.
