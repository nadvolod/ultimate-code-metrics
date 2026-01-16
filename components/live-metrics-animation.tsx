"use client"

import type React from "react"

import { useEffect, useState } from "react"
import { TrendingUp, Users, Clock } from "lucide-react"

interface AnimatedMetricProps {
  icon: React.ElementType
  label: string
  value: number
  suffix?: string
  delay: number
  color: string
}

function AnimatedMetric({ icon: Icon, label, value, suffix = "", delay, color }: AnimatedMetricProps) {
  const [count, setCount] = useState(0)
  const [hasAnimated, setHasAnimated] = useState(false)

  useEffect(() => {
    if (hasAnimated) return

    const timeout = setTimeout(() => {
      const duration = 1500
      const steps = 60
      const increment = value / steps
      let current = 0

      const interval = setInterval(() => {
        current += increment
        if (current >= value) {
          setCount(value)
          clearInterval(interval)
          setHasAnimated(true)
        } else {
          setCount(Math.floor(current))
        }
      }, duration / steps)

      return () => clearInterval(interval)
    }, delay)

    return () => clearTimeout(timeout)
  }, [value, delay, hasAnimated])

  return (
    <div
      className="bg-card border border-border rounded-lg p-6 space-y-3 animate-slide-up hover:border-primary/50 hover:shadow-lg hover:shadow-primary/5 transition-all duration-300"
      style={{ animationDelay: `${delay}ms` }}
    >
      <div className="flex items-center justify-between">
        <Icon size={24} className={color} />
        <div className={`text-3xl font-bold ${color}`}>
          {count}
          {suffix}
        </div>
      </div>
      <div className="text-sm text-muted-foreground">{label}</div>

      {/* Progress bar */}
      <div className="h-1 bg-muted rounded-full overflow-hidden">
        <div
          className={`h-full bg-gradient-to-r ${color.replace("text-", "from-")} to-transparent transition-all duration-1500 ease-out`}
          style={{
            width: `${(count / value) * 100}%`,
            transitionDelay: `${delay}ms`,
          }}
        />
      </div>
    </div>
  )
}

export function LiveMetricsAnimation() {
  return (
    <div className="grid md:grid-cols-3 gap-6 max-w-4xl mx-auto">
      <AnimatedMetric icon={TrendingUp} label="PRs Analyzed This Week" value={247} delay={200} color="text-green-500" />
      <AnimatedMetric icon={Users} label="Active Reviewers" value={18} delay={400} color="text-blue-500" />
      <AnimatedMetric
        icon={Clock}
        label="Avg Review Time Saved"
        value={70}
        suffix="%"
        delay={600}
        color="text-purple-500"
      />
    </div>
  )
}
