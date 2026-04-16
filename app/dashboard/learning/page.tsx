"use client"

import { useEffect, useState } from "react"
import {
  Brain,
  CheckCircle2,
  Clock,
  AlertCircle,
  TrendingUp,
  Loader2,
  RefreshCw,
  GitBranch,
  Zap,
  FileText,
  BarChart2,
} from "lucide-react"
import type {
  LearningStatus,
  AgentAccuracyEntry,
  LearnedHeuristicEntry,
  PromptPatchEntry,
  SeverityCalibrationEntry,
} from "@/app/api/learning/route"

// ─── helpers ───────────────────────────────────────────────────────────────

function formatDate(iso: string | null) {
  if (!iso) return "Never"
  return new Date(iso).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  })
}

function precisionColor(rate: number) {
  if (rate >= 0.85) return "text-green-400"
  if (rate >= 0.70) return "text-yellow-400"
  return "text-red-400"
}

function precisionBar(rate: number) {
  const pct = Math.round(rate * 100)
  const color = rate >= 0.85 ? "bg-green-500" : rate >= 0.70 ? "bg-yellow-500" : "bg-red-500"
  return { pct, color }
}

function heuristicTypeBadge(type: LearnedHeuristicEntry["heuristicType"]) {
  const map: Record<string, string> = {
    PATH_OVERRIDE: "bg-blue-500/20 text-blue-300",
    SKIP_CATEGORY: "bg-orange-500/20 text-orange-300",
    SEVERITY_ADJUST: "bg-purple-500/20 text-purple-300",
    CUSTOM: "bg-gray-500/20 text-gray-300",
  }
  return map[type] ?? "bg-gray-500/20 text-gray-300"
}

function patchTypeBadge(type: PromptPatchEntry["patchType"]) {
  const map: Record<string, string> = {
    ADD_INSTRUCTION: "bg-green-500/20 text-green-300",
    REMOVE_INSTRUCTION: "bg-red-500/20 text-red-300",
    MODIFY_CRITERIA: "bg-yellow-500/20 text-yellow-300",
  }
  return map[type] ?? "bg-gray-500/20 text-gray-300"
}

// ─── sub-components ────────────────────────────────────────────────────────

function ScheduleRow({ label, entry }: { label: string; entry: LearningStatus["schedules"]["outcomeCollection"] }) {
  const isOk = entry.status === "OK"
  return (
    <div className="flex items-center justify-between py-3 border-b border-border last:border-0">
      <div className="flex items-center gap-3">
        {isOk ? (
          <CheckCircle2 size={16} className="text-green-400 shrink-0" />
        ) : entry.status === "ERROR" ? (
          <AlertCircle size={16} className="text-red-400 shrink-0" />
        ) : (
          <Clock size={16} className="text-muted-foreground shrink-0" />
        )}
        <div>
          <p className="text-sm font-medium text-foreground">{label}</p>
          <p className="text-xs text-muted-foreground">{entry.description}</p>
        </div>
      </div>
      <div className="text-right shrink-0 ml-4">
        <p className="text-xs font-mono text-muted-foreground">{entry.cron}</p>
        <p className="text-xs text-muted-foreground">Last: {formatDate(entry.lastRunAt)}</p>
      </div>
    </div>
  )
}

function AccuracyRow({ a }: { a: AgentAccuracyEntry }) {
  const { pct, color } = precisionBar(a.precisionRate)
  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between text-sm">
        <span className="font-medium text-foreground">{a.agentName}</span>
        <span className={`font-bold ${precisionColor(a.precisionRate)}`}>{pct}%</span>
      </div>
      <div className="h-2 rounded-full bg-muted overflow-hidden">
        <div className={`h-full rounded-full ${color} transition-all duration-500`} style={{ width: `${pct}%` }} />
      </div>
      <div className="flex gap-3 text-xs text-muted-foreground">
        <span>✓ {a.acceptedFindings} accepted</span>
        <span>✗ {a.dismissedFindings} dismissed</span>
        <span>~ {a.deferredFindings} deferred</span>
      </div>
    </div>
  )
}

function HeuristicCard({ h }: { h: LearnedHeuristicEntry }) {
  return (
    <div className="bg-muted/30 rounded-lg p-4 border border-border/50 space-y-2">
      <div className="flex items-center gap-2 flex-wrap">
        <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${heuristicTypeBadge(h.heuristicType)}`}>
          {h.heuristicType.replace("_", " ")}
        </span>
        <span className="text-xs text-muted-foreground">{h.agentName}</span>
        <span className="text-xs text-muted-foreground ml-auto">v{h.learningVersion}</span>
      </div>
      <p className="text-sm text-foreground">{h.description}</p>
      <p className="text-xs text-muted-foreground italic">{h.evidence}</p>
    </div>
  )
}

function PatchCard({ p }: { p: PromptPatchEntry }) {
  return (
    <div className="bg-muted/30 rounded-lg p-4 border border-border/50 space-y-2">
      <div className="flex items-center gap-2 flex-wrap">
        <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${patchTypeBadge(p.patchType)}`}>
          {p.patchType.replace(/_/g, " ")}
        </span>
        <span className="text-xs text-muted-foreground">{p.agentName}</span>
        <span className="text-xs text-muted-foreground ml-auto">v{p.learningVersion}</span>
      </div>
      <p className="text-sm text-foreground">{p.description}</p>
      <p className="text-xs text-muted-foreground italic">{p.evidence}</p>
    </div>
  )
}

function CalibrationCard({ c }: { c: SeverityCalibrationEntry }) {
  return (
    <div className="bg-muted/30 rounded-lg p-4 border border-border/50 space-y-2">
      <div className="flex items-center gap-2 flex-wrap">
        <span className="text-xs px-2 py-0.5 rounded-full font-medium bg-purple-500/20 text-purple-300">
          SEVERITY CALIBRATION
        </span>
        <span className="text-xs text-muted-foreground">{c.agentName}</span>
        <span className="text-xs text-muted-foreground ml-auto">v{c.learningVersion}</span>
      </div>
      <div className="flex items-center gap-2 text-sm">
        <span className="text-red-400 font-medium">{c.originalLevel}</span>
        <span className="text-muted-foreground">→</span>
        <span className="text-green-400 font-medium">{c.calibratedLevel}</span>
        <span className="text-muted-foreground text-xs">for {c.category}</span>
      </div>
      <p className="text-xs text-muted-foreground">
        Confidence: {Math.round(c.confidence * 100)}% · Sample size: {c.sampleSize}
      </p>
    </div>
  )
}

// ─── main page ─────────────────────────────────────────────────────────────

export default function LearningPage() {
  const [status, setStatus] = useState<LearningStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [lastRefreshed, setLastRefreshed] = useState<Date>(new Date())

  async function fetchStatus() {
    setLoading(true)
    try {
      const res = await fetch("/api/learning")
      if (!res.ok) throw new Error("Failed to fetch")
      const data = await res.json()
      setStatus(data)
      setLastRefreshed(new Date())
    } catch {
      // keep previous data if any
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchStatus()
    // Auto-refresh every 60 seconds
    const interval = setInterval(fetchStatus, 60_000)
    return () => clearInterval(interval)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const agentList = status ? Object.values(status.agentAccuracy) : []
  const totalFindings = agentList.reduce((s, a) => s + a.totalFindings, 0)
  const totalAccepted = agentList.reduce((s, a) => s + a.acceptedFindings, 0)
  const overallPrecision = totalFindings > 0 ? totalAccepted / totalFindings : 0

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-foreground flex items-center gap-2">
            <Brain size={24} className="text-purple-400" />
            Learning Progress
          </h2>
          <p className="text-muted-foreground mt-1">
            Real-time visibility into what the self-improving agent is learning
          </p>
        </div>
        <button
          onClick={fetchStatus}
          className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors"
          disabled={loading}
        >
          <RefreshCw size={16} className={loading ? "animate-spin" : ""} />
          {loading ? "Refreshing…" : `Updated ${lastRefreshed.toLocaleTimeString()}`}
        </button>
      </div>

      {loading && !status ? (
        <div className="flex items-center justify-center py-24">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
      ) : (
        <>
          {/* Summary Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="bg-card border border-border rounded-lg p-5">
              <div className="flex items-center gap-2 text-muted-foreground mb-2">
                <GitBranch size={16} />
                <span className="text-sm">Repository</span>
              </div>
              <p className="text-lg font-semibold text-foreground truncate">{status?.repository ?? "—"}</p>
              <p className="text-xs text-muted-foreground mt-1">Version v{status?.learningVersion ?? 0}</p>
            </div>

            <div className="bg-card border border-border rounded-lg p-5">
              <div className="flex items-center gap-2 text-muted-foreground mb-2">
                <BarChart2 size={16} />
                <span className="text-sm">PRs Analyzed</span>
              </div>
              <p className="text-lg font-semibold text-foreground">{status?.totalReviewsAnalyzed ?? 0}</p>
              <p className="text-xs text-muted-foreground mt-1">
                Last run: {formatDate(status?.lastRunAt ?? null)}
              </p>
            </div>

            <div className="bg-card border border-border rounded-lg p-5">
              <div className="flex items-center gap-2 text-muted-foreground mb-2">
                <TrendingUp size={16} />
                <span className="text-sm">Overall Precision</span>
              </div>
              <p className={`text-lg font-semibold ${precisionColor(overallPrecision)}`}>
                {totalFindings > 0 ? `${Math.round(overallPrecision * 100)}%` : "—"}
              </p>
              <p className="text-xs text-muted-foreground mt-1">
                {totalAccepted} of {totalFindings} findings accepted
              </p>
            </div>

            <div className="bg-card border border-border rounded-lg p-5">
              <div className="flex items-center gap-2 text-muted-foreground mb-2">
                <Zap size={16} />
                <span className="text-sm">Active Rules</span>
              </div>
              <p className="text-lg font-semibold text-foreground">
                {(status?.activeHeuristics.length ?? 0) +
                  (status?.activePromptPatches.length ?? 0) +
                  (status?.activeCalibrations.length ?? 0)}
              </p>
              <p className="text-xs text-muted-foreground mt-1">
                {status?.activeHeuristics.length ?? 0}h · {status?.activePromptPatches.length ?? 0}p ·{" "}
                {status?.activeCalibrations.length ?? 0}c
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
            {/* Schedules */}
            <div className="bg-card border border-border rounded-lg p-6">
              <h3 className="text-base font-semibold text-foreground flex items-center gap-2 mb-4">
                <Clock size={18} className="text-muted-foreground" />
                Scheduled Jobs
              </h3>
              {status?.schedules ? (
                <div>
                  <ScheduleRow label="Outcome Collection" entry={status.schedules.outcomeCollection} />
                  <ScheduleRow label="Learning Analysis" entry={status.schedules.learningAnalysis} />
                  <ScheduleRow label="Weekly Evaluation" entry={status.schedules.evaluation} />
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">No schedule data available</p>
              )}
            </div>

            {/* Agent Accuracy */}
            <div className="bg-card border border-border rounded-lg p-6">
              <h3 className="text-base font-semibold text-foreground flex items-center gap-2 mb-4">
                <TrendingUp size={18} className="text-muted-foreground" />
                Agent Precision Rates
              </h3>
              {agentList.length > 0 ? (
                <div className="space-y-4">
                  {agentList.map((a) => (
                    <AccuracyRow key={a.agentName} a={a} />
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">
                  No accuracy data yet — run at least 5 reviews to start learning
                </p>
              )}
            </div>
          </div>

          {/* Active Heuristics */}
          <div className="bg-card border border-border rounded-lg p-6">
            <h3 className="text-base font-semibold text-foreground flex items-center gap-2 mb-4">
              <Zap size={18} className="text-muted-foreground" />
              Active Heuristics
              <span className="ml-auto text-sm font-normal text-muted-foreground">
                {status?.activeHeuristics.length ?? 0} rules
              </span>
            </h3>
            {status?.activeHeuristics && status.activeHeuristics.length > 0 ? (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {status.activeHeuristics.map((h) => (
                  <HeuristicCard key={h.id} h={h} />
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">
                No heuristics approved yet — the agent needs more data to propose changes
              </p>
            )}
          </div>

          {/* Prompt Patches */}
          <div className="bg-card border border-border rounded-lg p-6">
            <h3 className="text-base font-semibold text-foreground flex items-center gap-2 mb-4">
              <FileText size={18} className="text-muted-foreground" />
              Prompt Patches
              <span className="ml-auto text-sm font-normal text-muted-foreground">
                {status?.activePromptPatches.length ?? 0} patches
              </span>
            </h3>
            {status?.activePromptPatches && status.activePromptPatches.length > 0 ? (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {status.activePromptPatches.map((p) => (
                  <PatchCard key={p.id} p={p} />
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">No prompt patches active yet</p>
            )}
          </div>

          {/* Severity Calibrations */}
          {status?.activeCalibrations && status.activeCalibrations.length > 0 && (
            <div className="bg-card border border-border rounded-lg p-6">
              <h3 className="text-base font-semibold text-foreground flex items-center gap-2 mb-4">
                <BarChart2 size={18} className="text-muted-foreground" />
                Severity Calibrations
                <span className="ml-auto text-sm font-normal text-muted-foreground">
                  {status.activeCalibrations.length} calibrations
                </span>
              </h3>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {status.activeCalibrations.map((c) => (
                  <CalibrationCard key={c.id} c={c} />
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
