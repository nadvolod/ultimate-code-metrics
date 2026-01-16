import { ArrowUp, ArrowDown } from "lucide-react"

interface MetricCardProps {
  label: string
  value: string
  change?: string
  trend?: "up" | "down"
  index?: number
}

export function MetricCard({ label, value, change, trend, index = 0 }: MetricCardProps) {
  return (
    <div
      className="bg-card border border-border rounded-lg p-6 hover:border-primary/30 transition-all duration-300 hover:shadow-lg hover:shadow-primary/5 animate-slide-up"
      style={{ animationDelay: `${index * 100}ms` }}
    >
      <div className="flex items-start justify-between">
        <div className="space-y-2">
          <p className="text-sm text-muted-foreground">{label}</p>
          <p className="text-3xl font-semibold text-foreground">{value}</p>
        </div>
        {change && trend && (
          <div
            className={`flex items-center gap-1 text-sm font-medium ${
              trend === "up" ? "text-success" : "text-primary"
            }`}
          >
            {trend === "up" ? <ArrowUp size={16} /> : <ArrowDown size={16} />}
            <span>{change}</span>
          </div>
        )}
      </div>
    </div>
  )
}
