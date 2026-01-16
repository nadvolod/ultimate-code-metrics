import { Github, RefreshCw } from "lucide-react"
import { StatusBadge } from "./status-badge"
import { Button } from "./ui/button"

interface SourceCardProps {
  name: string
  type: string
  status: "connected" | "disconnected" | "error"
  lastSync?: string
  index?: number
}

export function SourceCard({ name, type, status, lastSync, index = 0 }: SourceCardProps) {
  return (
    <div
      className="bg-card border border-border rounded-lg p-5 hover:border-primary/30 transition-all duration-300 hover:shadow-lg hover:shadow-primary/5 animate-fade-in"
      style={{ animationDelay: `${index * 80}ms` }}
    >
      <div className="flex items-start justify-between">
        <div className="flex items-start gap-3">
          <div className="p-2 bg-muted rounded-md">
            <Github size={20} className="text-foreground" />
          </div>
          <div className="space-y-1">
            <h3 className="font-semibold text-foreground">{name}</h3>
            <p className="text-sm text-muted-foreground">{type}</p>
            {lastSync && <p className="text-xs text-muted-foreground">Last sync: {lastSync}</p>}
          </div>
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge status={status} size="sm" />
          {status === "connected" && (
            <Button size="icon" variant="ghost" className="h-8 w-8 hover:bg-accent transition-all duration-200">
              <RefreshCw size={14} />
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}
