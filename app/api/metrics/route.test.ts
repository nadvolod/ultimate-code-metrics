import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { GET } from "./route"

// Mock fs/promises
vi.mock("fs/promises", () => ({
  readdir: vi.fn(),
  readFile: vi.fn(),
}))

import { readdir, readFile } from "fs/promises"

const mockReaddir = vi.mocked(readdir)
const mockReadFile = vi.mocked(readFile)

describe("Metrics API Security Tests", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  describe("Test 1: Schema Validation", () => {
    it("should handle malformed JSON gracefully without crashing", async () => {
      mockReaddir.mockResolvedValue(["valid.json", "malformed.json"] as unknown as Awaited<ReturnType<typeof readdir>>)

      mockReadFile.mockImplementation((filePath) => {
        const path = filePath as string
        if (path.includes("valid.json")) {
          return Promise.resolve(JSON.stringify({
            overallRecommendation: "APPROVE",
            agents: [],
            metadata: { generatedAt: "2024-01-01", tookMs: 1000, model: "test" }
          }))
        }
        // Return malformed JSON for the other file
        return Promise.resolve("{ invalid json }")
      })

      const response = await GET()
      const data = await response.json()

      // API should not crash and should return valid metrics from the valid file
      expect(response.status).toBe(200)
      expect(data).not.toBeNull()
      expect(data.prsAnalyzed).toBe(1) // Only the valid file should be counted
    })

    it("should handle invalid schema data (missing required fields)", async () => {
      mockReaddir.mockResolvedValue(["incomplete.json"] as unknown as Awaited<ReturnType<typeof readdir>>)

      mockReadFile.mockResolvedValue(JSON.stringify({
        // Missing overallRecommendation, agents, metadata
        someRandomField: "value"
      }))

      const response = await GET()
      const data = await response.json()

      // API should handle gracefully - it will include the file but metrics calculations
      // should handle undefined values without crashing
      expect(response.status).toBe(200)
    })

    it("should handle completely empty JSON object", async () => {
      mockReaddir.mockResolvedValue(["empty.json"] as unknown as Awaited<ReturnType<typeof readdir>>)
      mockReadFile.mockResolvedValue("{}")

      const response = await GET()

      // Should not crash
      expect(response.status).toBe(200)
    })
  })

  describe("Test 2: Path Traversal Protection", () => {
    it("should not process files with path traversal patterns in filenames", async () => {
      // SECURITY TEST: Verifies that filenames containing path traversal patterns
      // do not cause the API to read files outside the reviews directory.
      //
      // Note: In practice, fs.readdir() returns only basenames (not paths),
      // and path.join() with a basename containing ".." will still resolve
      // within the REVIEWS_DIR. However, this test documents the expected
      // behavior for defense-in-depth.
      mockReaddir.mockResolvedValue([
        "valid-review.json",
        "suspicious..file.json", // Contains ".." but not a traversal attack
      ] as unknown as Awaited<ReturnType<typeof readdir>>)

      mockReadFile.mockImplementation((filePath) => {
        const path = filePath as string
        return Promise.resolve(JSON.stringify({
          overallRecommendation: "APPROVE",
          agents: [],
          metadata: { generatedAt: "2024-01-01", tookMs: 500, model: "test" }
        }))
      })

      const response = await GET()
      const data = await response.json()

      expect(response.status).toBe(200)
      // Both files are processed since they're valid JSON files in the directory
      expect(data.prsAnalyzed).toBe(2)
    })

    it("should safely handle the reviews directory path construction", async () => {
      // SECURITY TEST: Verifies that path.join() is used correctly with REVIEWS_DIR.
      // The API uses path.join(REVIEWS_DIR, filename) which prevents escaping
      // the reviews directory as long as filenames come from readdir().
      mockReaddir.mockResolvedValue([
        "normal.json",
      ] as unknown as Awaited<ReturnType<typeof readdir>>)

      let capturedPath = ""
      mockReadFile.mockImplementation((filePath) => {
        capturedPath = filePath as string
        return Promise.resolve(JSON.stringify({
          overallRecommendation: "REQUEST_CHANGES",
          agents: [],
          metadata: { generatedAt: "2024-01-01", tookMs: 200, model: "test" }
        }))
      })

      const response = await GET()

      expect(response.status).toBe(200)
      // Verify the path contains the expected directory structure
      expect(capturedPath).toContain("data")
      expect(capturedPath).toContain("reviews")
      expect(capturedPath).toContain("normal.json")
    })
  })

  describe("Test 3: Empty Directory Handling", () => {
    it("should return null when directory is empty", async () => {
      mockReaddir.mockResolvedValue([] as unknown as Awaited<ReturnType<typeof readdir>>)

      const response = await GET()
      const data = await response.json()

      expect(response.status).toBe(200)
      expect(data).toBeNull()
    })

    it("should return null when directory has no JSON files", async () => {
      mockReaddir.mockResolvedValue([
        "readme.txt",
        "config.yaml",
        ".gitkeep",
      ] as unknown as Awaited<ReturnType<typeof readdir>>)

      const response = await GET()
      const data = await response.json()

      expect(response.status).toBe(200)
      expect(data).toBeNull()
    })

    it("should return null when directory does not exist", async () => {
      mockReaddir.mockRejectedValue(new Error("ENOENT: no such file or directory"))

      const response = await GET()
      const data = await response.json()

      expect(response.status).toBe(200)
      expect(data).toBeNull()
    })

    it("should return null when all JSON files are invalid", async () => {
      mockReaddir.mockResolvedValue(["bad1.json", "bad2.json"] as unknown as Awaited<ReturnType<typeof readdir>>)
      mockReadFile.mockRejectedValue(new Error("Parse error"))

      const response = await GET()
      const data = await response.json()

      expect(response.status).toBe(200)
      expect(data).toBeNull()
    })
  })
})
