"use client"

import { useEffect, useState } from "react"
import { FileCode, CheckCircle2, Brain } from "lucide-react"

export function AgentHandoffAnimation() {
  const [activeAgent, setActiveAgent] = useState(0)
  const [showConnections, setShowConnections] = useState(false)

  useEffect(() => {
    setShowConnections(true)
    const interval = setInterval(() => {
      setActiveAgent((prev) => (prev + 1) % 3)
    }, 2500)
    return () => clearInterval(interval)
  }, [])

  const agents = [
    { icon: FileCode, label: "Code Agent", color: "from-blue-500 to-cyan-500" },
    { icon: Brain, label: "Analysis Agent", color: "from-purple-500 to-pink-500" },
    { icon: CheckCircle2, label: "Review Agent", color: "from-green-500 to-emerald-500" },
  ]

  return (
    <div className="relative w-full max-w-2xl mx-auto h-64 flex items-center justify-center">
      {/* Agents */}
      <div className="relative w-full flex items-center justify-between px-12">
        {agents.map((agent, idx) => (
          <div key={idx} className="flex flex-col items-center gap-3 z-10">
            <div
              className={`
                relative h-20 w-20 rounded-full bg-gradient-to-br ${agent.color} 
                flex items-center justify-center shadow-lg
                transition-all duration-500
                ${activeAgent === idx ? "scale-110 shadow-2xl ring-4 ring-primary/30" : "scale-100 opacity-60"}
              `}
            >
              <agent.icon size={32} className="text-white" />

              {/* Pulse ring when active */}
              {activeAgent === idx && (
                <div className="absolute inset-0 rounded-full bg-gradient-to-br from-primary/40 to-transparent animate-ping" />
              )}
            </div>
            <span
              className={`text-xs font-medium transition-opacity ${activeAgent === idx ? "opacity-100" : "opacity-50"}`}
            >
              {agent.label}
            </span>
          </div>
        ))}

        {/* Connection Lines */}
        <svg className="absolute inset-0 w-full h-full pointer-events-none" style={{ top: "40px" }}>
          {/* Line 1: Agent 0 to Agent 1 */}
          <line
            x1="25%"
            y1="50%"
            x2="50%"
            y2="50%"
            stroke="currentColor"
            strokeWidth="2"
            className="text-border transition-colors duration-500"
            strokeDasharray="4 4"
          />

          {/* Line 2: Agent 1 to Agent 2 */}
          <line
            x1="50%"
            y1="50%"
            x2="75%"
            y2="50%"
            stroke="currentColor"
            strokeWidth="2"
            className="text-border transition-colors duration-500"
            strokeDasharray="4 4"
          />

          {/* Animated flow particles */}
          {showConnections && (
            <>
              {/* Particle on line 1 */}
              <circle
                r="4"
                fill="currentColor"
                className={`text-primary transition-opacity ${activeAgent === 0 ? "opacity-100" : "opacity-0"}`}
              >
                <animateMotion dur="2s" repeatCount="indefinite">
                  <mpath href="#path1" />
                </animateMotion>
              </circle>

              {/* Particle on line 2 */}
              <circle
                r="4"
                fill="currentColor"
                className={`text-primary transition-opacity ${activeAgent === 1 ? "opacity-100" : "opacity-0"}`}
              >
                <animateMotion dur="2s" repeatCount="indefinite">
                  <mpath href="#path2" />
                </animateMotion>
              </circle>

              {/* Hidden paths for animation */}
              <path id="path1" d="M 25% 50% L 50% 50%" fill="none" />
              <path id="path2" d="M 50% 50% L 75% 50%" fill="none" />
            </>
          )}
        </svg>

        {/* PR Document floating between agents */}
        <div
          className={`
            absolute top-1/2 left-1/4 -translate-y-1/2
            bg-card border-2 border-primary rounded-lg p-3 shadow-xl
            transition-all duration-700 ease-in-out
          `}
          style={{
            transform: `translateX(${activeAgent * 100}%) translateY(-50%)`,
          }}
        >
          <div className="text-xs font-mono text-primary">PR #1234</div>
        </div>
      </div>
    </div>
  )
}
