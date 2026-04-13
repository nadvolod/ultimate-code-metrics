import { NextResponse } from "next/server"
import { readdir, readFile } from "fs/promises"
import { join } from "path"
import { transformReviewResponses } from "@/lib/api/review-transformer"
import type { BackendReviewResponse } from "@/lib/types/review"

const REVIEWS_DIR = join(process.cwd(), "data", "reviews")

interface ApiError {
  error: string
  code: string
  message: string
}

function errorResponse(error: string, code: string, message: string, status: number): NextResponse<ApiError> {
  return NextResponse.json({ error, code, message }, { status })
}

export async function GET() {
  try {
    // Read all JSON files from the reviews directory
    let files: string[]
    try {
      files = await readdir(REVIEWS_DIR)
    } catch (err) {
      const nodeErr = err as NodeJS.ErrnoException
      if (nodeErr.code === "ENOENT") {
        // Directory doesn't exist - return empty list, not an error
        return NextResponse.json([])
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
      return NextResponse.json([])
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

    // Transform to frontend format
    const testReports = transformReviewResponses(reviews)

    return NextResponse.json(testReports)
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
