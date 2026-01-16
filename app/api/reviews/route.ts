import { NextResponse } from "next/server"
import { readdir, readFile } from "fs/promises"
import { join } from "path"
import { transformReviewResponses } from "@/lib/api/review-transformer"
import type { BackendReviewResponse } from "@/lib/types/review"

const REVIEWS_DIR = join(process.cwd(), "data", "reviews")

export async function GET() {
  try {
    // Read all JSON files from the reviews directory
    let files: string[]
    try {
      files = await readdir(REVIEWS_DIR)
    } catch {
      // Directory doesn't exist or is empty
      return NextResponse.json([])
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
      } catch (error) {
        console.error(`Failed to read/parse ${filename}:`, error)
        // Skip invalid files
      }
    }

    // Transform to frontend format
    const testReports = transformReviewResponses(reviews)

    return NextResponse.json(testReports)
  } catch (error) {
    console.error("Failed to fetch reviews:", error)
    return NextResponse.json({ error: "Failed to fetch reviews" }, { status: 500 })
  }
}
