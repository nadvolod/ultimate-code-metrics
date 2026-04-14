import { NextRequest, NextResponse } from "next/server"
import { readdir, readFile } from "fs/promises"
import { join } from "path"
import { transformReviewResponses } from "@/lib/api/review-transformer"
import type { BackendReviewResponse, FrontendRecommendation, TestReport } from "@/lib/types/review"

const REVIEWS_DIR = join(process.cwd(), "data", "reviews")

interface ApiError {
  error: string
  code: string
  message: string
}

function errorResponse(error: string, code: string, message: string, status: number): NextResponse<ApiError> {
  return NextResponse.json({ error, code, message }, { status })
}

const VALID_SORT_FIELDS = ["date", "prNumber"] as const
const VALID_SORT_DIRS = ["asc", "desc"] as const
type SortField = (typeof VALID_SORT_FIELDS)[number]
type SortDir = (typeof VALID_SORT_DIRS)[number]

interface QueryOptions {
  limit: number | undefined
  offset: number
  sort: SortField
  sortDir: SortDir
  recommendation: string | null
  /** Filters by agent name (stored as `findings.category` in the frontend model) */
  agent: string | null
}

/** Parses and validates all query parameters. Returns a 400 response on invalid input. */
function parseQueryOptions(
  searchParams: URLSearchParams
): { options: QueryOptions } | { error: NextResponse } {
  // limit
  const limitParam = searchParams.get("limit")
  let limit: number | undefined
  if (limitParam !== null) {
    const parsed = parseInt(limitParam, 10)
    if (isNaN(parsed) || parsed < 1) {
      return {
        error: NextResponse.json(
          { error: "Invalid 'limit': must be a positive integer" },
          { status: 400 }
        ),
      }
    }
    limit = parsed
  }

  // offset
  const offsetParam = searchParams.get("offset")
  let offset = 0
  if (offsetParam !== null) {
    const parsed = parseInt(offsetParam, 10)
    if (isNaN(parsed) || parsed < 0) {
      return {
        error: NextResponse.json(
          { error: "Invalid 'offset': must be a non-negative integer" },
          { status: 400 }
        ),
      }
    }
    offset = parsed
  }

  // sort
  const sortParam = searchParams.get("sort") ?? "date"
  if (!(VALID_SORT_FIELDS as readonly string[]).includes(sortParam)) {
    return {
      error: NextResponse.json(
        { error: `Invalid 'sort': must be one of ${VALID_SORT_FIELDS.join(", ")}` },
        { status: 400 }
      ),
    }
  }

  // sortDir
  const sortDirParam = searchParams.get("sortDir") ?? "desc"
  if (!(VALID_SORT_DIRS as readonly string[]).includes(sortDirParam)) {
    return {
      error: NextResponse.json(
        { error: `Invalid 'sortDir': must be one of ${VALID_SORT_DIRS.join(", ")}` },
        { status: 400 }
      ),
    }
  }

  return {
    options: {
      limit,
      offset,
      sort: sortParam as SortField,
      sortDir: sortDirParam as SortDir,
      // Normalise to uppercase so callers can compare against FrontendRecommendation values
      recommendation: searchParams.get("recommendation")?.toUpperCase() ?? null,
      agent: searchParams.get("agent"),
    },
  }
}

/**
 * Extracts the `generatedAt` epoch ms embedded in a report id.
 * Id format: `${filename_without_ext}-${epochMs}` (see generateId in review-transformer.ts).
 */
function extractGeneratedAtMs(id: string): number {
  const lastDash = id.lastIndexOf("-")
  if (lastDash < 0) return 0
  const ms = parseInt(id.slice(lastDash + 1), 10)
  return isNaN(ms) ? 0 : ms
}

/** Comparator for sorting reports. */
function compareReports(a: TestReport, b: TestReport, sort: SortField, sortDir: SortDir): number {
  let comparison = 0
  if (sort === "prNumber") {
    comparison = a.prNumber - b.prNumber
  } else {
    // sort === "date": compare by the generatedAt timestamp embedded in the id
    comparison = extractGeneratedAtMs(a.id) - extractGeneratedAtMs(b.id)
  }
  return sortDir === "asc" ? comparison : -comparison
}

/** Applies recommendation and agent filters to the report list. */
function applyFilters(
  reports: TestReport[],
  recommendation: string | null,
  agent: string | null
): TestReport[] {
  let filtered = reports
  if (recommendation) {
    filtered = filtered.filter(
      (r) => r.recommendation === (recommendation as FrontendRecommendation)
    )
  }
  if (agent) {
    // Each finding's `category` equals the originating agent's name (see review-transformer.ts)
    filtered = filtered.filter((r) =>
      r.findings.some((f) => f.category.toLowerCase() === agent.toLowerCase())
    )
  }
  return filtered
}

export async function GET(request: NextRequest) {

  try {
    const result = parseQueryOptions(request.nextUrl.searchParams)
    if ("error" in result) return result.error
    const { limit, offset, sort, sortDir, recommendation, agent } = result.options

    // Read all JSON files from the reviews directory
    let files: string[]
    try {
      files = await readdir(REVIEWS_DIR)
    } catch (err) {
      const nodeErr = err as NodeJS.ErrnoException
      if (nodeErr.code === "ENOENT") {
        // Directory doesn't exist - return empty list, not an error
        return NextResponse.json({ data: [], total: 0, offset: 0 })
      }
      // Other filesystem errors (permissions, etc.)
      console.error("Failed to read reviews directory:", err)
      return errorResponse(
        "Failed to read reviews directory",
        "DIRECTORY_READ_ERROR",
        "An unexpected error occurred while accessing the reviews directory.",
        500,
      )

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
      } catch (err) {
        const nodeErr = err as NodeJS.ErrnoException
        if (nodeErr.code === "ENOENT") {
          console.warn(`Review file not found (skipping): ${filename}`)
        } else if (err instanceof SyntaxError) {
          console.error(`Failed to parse review file ${filename} (skipping): invalid JSON`)
        } else {
          console.error(`Failed to read/parse ${filename} (skipping):`, err)
        }
        // Skip invalid files and continue processing others
      }
    }

    // Transform, filter, sort, then paginate
    const allReports = transformReviewResponses(reviews)
    const filtered = applyFilters(allReports, recommendation, agent)
    const sorted = [...filtered].sort((a, b) => compareReports(a, b, sort, sortDir))
    const total = sorted.length
    const paginatedReports =
      limit !== undefined ? sorted.slice(offset, offset + limit) : sorted.slice(offset)

    return NextResponse.json({
      data: paginatedReports,
      total,
      offset,
      ...(limit !== undefined ? { limit } : {}),
    })
  } catch (error) {
    console.error("Failed to fetch reviews:", error)
    return errorResponse(
      "Failed to fetch reviews",
      "INTERNAL_SERVER_ERROR",
      "An unexpected error occurred while fetching review data.",
      500,
    )
  }
}

