import { NextResponse } from "next/server"
import { readdir, readFile } from "fs/promises"
import { join } from "path"
import type { BackendReviewResponse } from "@/lib/types/review"

const REVIEWS_DIR = join(process.cwd(), "data", "reviews")

export interface DashboardMetrics {
  prsAnalyzed: number
  avgAnalysisTimeMinutes: string
  autoApprovedPct: string
  engineeringHoursSaved: string
  trend?: {
    prsAnalyzed: string
    direction: "up" | "down"
  }
}

export async function GET() {
  try {
    // Read all JSON files from the reviews directory
    let files: string[]
    try {
      files = await readdir(REVIEWS_DIR)
    } catch {
      // Directory doesn't exist or is empty
      return NextResponse.json(null)
    }

    const jsonFiles = files.filter((f) => f.endsWith(".json"))

    if (jsonFiles.length === 0) {
      return NextResponse.json(null)
    }

    // Read and parse each review file
    const reviews: BackendReviewResponse[] = []

    for (const filename of jsonFiles) {
      try {
        const filePath = join(REVIEWS_DIR, filename)
        const content = await readFile(filePath, "utf-8")
        const response = JSON.parse(content) as BackendReviewResponse
        reviews.push(response)
      } catch (error) {
        console.error(`Failed to read/parse ${filename}:`, error)
        // Skip invalid files
      }
    }

    if (reviews.length === 0) {
      return NextResponse.json(null)
    }

    // Compute metrics
    const prsAnalyzed = reviews.length

    // Average analysis time (in minutes)
    const totalMs = reviews.reduce((sum, r) => sum + (r.metadata?.tookMs || 0), 0)
    const avgAnalysisTimeMinutes =
      reviews.length > 0 ? (totalMs / reviews.length / 1000 / 60).toFixed(1) : "0"

    // Auto-approved percentage
    const approvedCount = reviews.filter((r) => r.overallRecommendation === "APPROVE").length
    const autoApprovedPct =
      reviews.length > 0 ? ((approvedCount / reviews.length) * 100).toFixed(0) : "0"

    // Engineering hours saved
    // Assumption: Manual review = 30 min, AI analysis = 3 min, saved = 27 min = 0.45 hours
    const engineeringHoursSaved = (prsAnalyzed * 0.45).toFixed(0)

    const metrics: DashboardMetrics = {
      prsAnalyzed,
      avgAnalysisTimeMinutes,
      autoApprovedPct,
      engineeringHoursSaved,
    }

    return NextResponse.json(metrics)
  } catch (error) {
    console.error("Failed to fetch metrics:", error)
    return NextResponse.json({ error: "Failed to fetch metrics" }, { status: 500 })
  }
}
