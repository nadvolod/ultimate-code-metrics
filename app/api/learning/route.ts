import { NextResponse } from "next/server"
import { readFile } from "fs/promises"
import { join } from "path"

const LEARNING_STATUS_PATH = join(process.cwd(), "data", "learning", "status.json")

export interface AgentAccuracyEntry {
  agentName: string
  totalFindings: number
  acceptedFindings: number
  dismissedFindings: number
  deferredFindings: number
  precisionRate: number
}

export interface ScheduleEntry {
  cron: string
  description: string
  lastRunAt: string | null
  status: "OK" | "ERROR" | "PENDING"
}

export interface LearnedHeuristicEntry {
  id: number
  agentName: string
  heuristicType: "PATH_OVERRIDE" | "SKIP_CATEGORY" | "SEVERITY_ADJUST" | "CUSTOM"
  description: string
  evidence: string
  status: "PROPOSED" | "APPROVED" | "REJECTED" | "RETIRED"
  learningVersion: number
}

export interface PromptPatchEntry {
  id: number
  agentName: string
  patchType: "ADD_INSTRUCTION" | "REMOVE_INSTRUCTION" | "MODIFY_CRITERIA"
  description: string
  evidence: string
  status: "PROPOSED" | "APPROVED" | "REJECTED"
  learningVersion: number
}

export interface SeverityCalibrationEntry {
  id: number
  agentName: string
  category: string
  originalLevel: string
  calibratedLevel: string
  confidence: number
  sampleSize: number
  status: "PROPOSED" | "APPROVED" | "REJECTED"
  learningVersion: number
}

export interface LearningStatus {
  repository: string
  learningVersion: number
  lastRunAt: string | null
  nextRunAt: string | null
  totalReviewsAnalyzed: number
  schedules: {
    outcomeCollection: ScheduleEntry
    learningAnalysis: ScheduleEntry
    evaluation: ScheduleEntry
  }
  agentAccuracy: Record<string, AgentAccuracyEntry>
  activeHeuristics: LearnedHeuristicEntry[]
  activePromptPatches: PromptPatchEntry[]
  activeCalibrations: SeverityCalibrationEntry[]
}

const MOCK_STATUS: LearningStatus = {
  repository: "—",
  learningVersion: 0,
  lastRunAt: null,
  nextRunAt: null,
  totalReviewsAnalyzed: 0,
  schedules: {
    outcomeCollection: { cron: "0 * * * *", description: "Collect PR outcomes hourly", lastRunAt: null, status: "PENDING" },
    learningAnalysis: { cron: "0 3 * * *", description: "Analyze outcomes daily", lastRunAt: null, status: "PENDING" },
    evaluation: { cron: "0 6 * * MON", description: "Weekly evaluation snapshot", lastRunAt: null, status: "PENDING" },
  },
  agentAccuracy: {},
  activeHeuristics: [],
  activePromptPatches: [],
  activeCalibrations: [],
}

export async function GET() {
  try {
    const content = await readFile(LEARNING_STATUS_PATH, "utf-8")
    const status = JSON.parse(content) as LearningStatus
    return NextResponse.json(status)
  } catch {
    // No status file yet — return empty/mock state so the UI still renders
    return NextResponse.json(MOCK_STATUS)
  }
}
