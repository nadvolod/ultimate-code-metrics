import { NextRequest, NextResponse } from "next/server"
import { readdir, readFile, stat } from "fs/promises"
import { join } from "path"
import { createHash } from "crypto"
import { transformReviewResponses } from "@/lib/api/review-transformer"
import type { BackendReviewResponse } from "@/lib/types/review"

const REVIEWS_DIR = join(process.cwd(), "data", "reviews")

// Cache max-age in seconds (60 seconds for review data)
const CACHE_MAX_AGE = 60
const CACHE_CONTROL = `private, max-age=${CACHE_MAX_AGE}, stale-while-revalidate=30`

interface CacheEntry {
  etag: string
  data: ReturnType<typeof transformReviewResponses>
  expiresAt: number
}

// In-memory cache for review data
let cache: CacheEntry | null = null

async function buildETag(files: string[]): Promise<string> {
  // Compute an ETag based on file names and their last-modified timestamps
  const stats = await Promise.all(
    files.map((f) =>
      stat(join(REVIEWS_DIR, f))
        .then((s) => `${f}:${s.mtimeMs}`)
        .catch(() => f)
    )
  )
  return createHash("sha1").update(stats.join("|")).digest("hex")
}

/**
 * Parse an If-None-Match header value and check if any of the ETags match.
 * Handles weak ETags (W/"...") and comma-separated lists per RFC 7232.
 */
function etagMatches(ifNoneMatch: string | null, etag: string): boolean {
  if (!ifNoneMatch) return false

  // Strip W/ prefix for weak comparison
  const normalise = (val: string) => val.trim().replace(/^W\//, "")
  const target = normalise(etag)

  return ifNoneMatch
    .split(",")
    .some((candidate) => normalise(candidate) === target)
}

function notModified(etag: string): NextResponse {
  return new NextResponse(null, {
    status: 304,
    headers: { ETag: etag, "Cache-Control": CACHE_CONTROL },
  })
}

export async function GET(request: NextRequest) {
  try {
    // Read all JSON files from the reviews directory
    let files: string[]
    try {
      files = await readdir(REVIEWS_DIR)
    } catch {
      // Directory doesn't exist or is empty
      return NextResponse.json([], { headers: { "Cache-Control": CACHE_CONTROL } })
    }

    const jsonFiles = files.filter((f) => f.endsWith(".json")).sort()

    if (jsonFiles.length === 0) {
      return NextResponse.json([], { headers: { "Cache-Control": CACHE_CONTROL } })
    }

    const now = Date.now()

    // Fast path: return cached data while the TTL is still valid (no stat calls needed)
    if (cache && cache.expiresAt > now) {
      const ifNoneMatch = request.headers.get("if-none-match")
      if (etagMatches(ifNoneMatch, cache.etag)) {
        return notModified(cache.etag)
      }
      return NextResponse.json(cache.data, {
        headers: { ETag: cache.etag, "Cache-Control": CACHE_CONTROL },
      })
    }

    // Cache is stale or absent – compute a fresh ETag from file metadata
    const etag = `"${await buildETag(jsonFiles)}"`

    // Honour conditional request before reading file contents
    const ifNoneMatch = request.headers.get("if-none-match")
    if (etagMatches(ifNoneMatch, etag)) {
      return notModified(etag)
    }

    // If the ETag hasn't changed since last cache, reuse cached data (skip file I/O)
    if (cache && cache.etag === etag) {
      cache.expiresAt = now + CACHE_MAX_AGE * 1000
      return NextResponse.json(cache.data, {
        headers: { ETag: etag, "Cache-Control": CACHE_CONTROL },
      })
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

    // Store in in-memory cache
    cache = {
      etag,
      data: testReports,
      expiresAt: now + CACHE_MAX_AGE * 1000,
    }

    return NextResponse.json(testReports, {
      headers: { ETag: etag, "Cache-Control": CACHE_CONTROL },
    })
  } catch (error) {
    console.error("Failed to fetch reviews:", error)
    return NextResponse.json(
      { error: "Failed to fetch reviews" },
      { status: 500, headers: { "Cache-Control": "no-store" } },
    )
  }
}
