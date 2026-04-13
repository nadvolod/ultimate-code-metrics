import { NextRequest, NextResponse } from "next/server"
import { readdir, readFile } from "fs/promises"
import { join } from "path"
import { transformReviewResponses } from "@/lib/api/review-transformer"
import type { BackendReviewResponse, TestReport } from "@/lib/types/review"

const REVIEWS_DIR = join(process.cwd(), "data", "reviews")

export async function GET(request: NextRequest) {
  try {
    const { searchParams } = request.nextUrl

    // Parse pagination parameters
    const limitParam = searchParams.get("limit")
    const offsetParam = searchParams.get("offset")

    let limit: number | undefined
    if (limitParam !== null) {
      const parsed = parseInt(limitParam, 10)
      if (isNaN(parsed) || parsed < 1) {
        return NextResponse.json({ error: "Invalid 'limit': must be a positive integer" }, { status: 400 })
      }
      limit = parsed
    }

    let offset = 0
    if (offsetParam !== null) {
      const parsed = parseInt(offsetParam, 10)
      if (isNaN(parsed) || parsed < 0) {
        return NextResponse.json({ error: "Invalid 'offset': must be a non-negative integer" }, { status: 400 })
      }
      offset = parsed
    }

    // Parse and validate sort parameters
    const sortParam = searchParams.get("sort") ?? "date"
    const sortDirParam = searchParams.get("sortDir") ?? "desc"

    const VALID_SORT_FIELDS = ["date", "prNumber"] as const
    const VALID_SORT_DIRS = ["asc", "desc"] as const
    type SortField = (typeof VALID_SORT_FIELDS)[number]
    type SortDir = (typeof VALID_SORT_DIRS)[number]

    if (!(VALID_SORT_FIELDS as readonly string[]).includes(sortParam)) {
      return NextResponse.json(
        { error: `Invalid 'sort': must be one of ${VALID_SORT_FIELDS.join(", ")}` },
        { status: 400 }
      )
    }
    if (!(VALID_SORT_DIRS as readonly string[]).includes(sortDirParam)) {
      return NextResponse.json(
        { error: `Invalid 'sortDir': must be one of ${VALID_SORT_DIRS.join(", ")}` },
        { status: 400 }
      )
    }

    const sort = sortParam as SortField
    const sortDir = sortDirParam as SortDir

    // Parse filter parameters
    const recommendationFilter = searchParams.get("recommendation")?.toUpperCase() ?? null
    const agentFilter = searchParams.get("agent")

    // Read all JSON files from the reviews directory
    let files: string[]
    try {
      files = await readdir(REVIEWS_DIR)
    } catch {
      // Directory doesn't exist or is empty
      return NextResponse.json({ data: [], total: 0, offset: 0 })
    }

    const jsonFiles = files.filter((f) => f.endsWith(".json"))

    if (jsonFiles.length === 0) {
      return NextResponse.json({ data: [], total: 0, offset: 0 })
    }

    // Read and parse each review file
    const reviews: Array<{ response: BackendReviewResponse; filename: string }> = []

    for (const filename of jsonFiles) {
      try {
        const filePath = join(REVIEWS_DIR, filename)
        const content = await readFile(filePath, "utf-8")
        const response = JSON.parse(content) as BackendReviewResponse
        reviews.push({ response, filename })
      } catch (error) {
        console.error(`Failed to read/parse ${filename}:`, error)
        // Skip invalid files
      }
    }

    // Transform to frontend format
    let testReports: TestReport[] = transformReviewResponses(reviews)

    // Apply filtering
    if (recommendationFilter) {
      testReports = testReports.filter((r) => r.recommendation === recommendationFilter)
    }

    if (agentFilter) {
      testReports = testReports.filter((r) =>
        r.findings.some((f) => f.category.toLowerCase() === agentFilter.toLowerCase())
      )
    }

    // Apply sorting (overrides the default sort from transformReviewResponses)
    testReports.sort((a, b) => {
      let comparison = 0
      if (sort === "prNumber") {
        comparison = a.prNumber - b.prNumber
      } else {
        // Default: sort by date using id (which contains the generatedAt timestamp)
        comparison = a.id.localeCompare(b.id)
      }
      return sortDir === "asc" ? comparison : -comparison
    })

    const total = testReports.length

    // Apply pagination
    const paginatedReports =
      limit !== undefined ? testReports.slice(offset, offset + limit) : testReports.slice(offset)

    return NextResponse.json({
      data: paginatedReports,
      total,
      offset,
      ...(limit !== undefined ? { limit } : {}),
    })
  } catch (error) {
    console.error("Failed to fetch reviews:", error)
    return NextResponse.json({ error: "Failed to fetch reviews" }, { status: 500 })
  }
}
