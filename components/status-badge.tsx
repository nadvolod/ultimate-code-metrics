import { CheckCircle2, XCircle, AlertCircle, Circle } from "lucide-react"

interface StatusBadgeProps {
  status: "APPROVE" | "BLOCK" | "connected" | "disconnected" | "error"
  size?: "sm" | "md" | "lg"
}

export function StatusBadge({ status, size = "md" }: StatusBadgeProps) {
  const sizeClasses = {
    sm: "text-xs px-2 py-0.5 gap-1",
    md: "text-sm px-3 py-1 gap-1.5",
    lg: "text-base px-4 py-1.5 gap-2",
  }

  const iconSize = {
    sm: 12,
    md: 14,
    lg: 16,
  }

  const config = {
    APPROVE: {
      label: "APPROVE",
      icon: CheckCircle2,
      className: "bg-success/10 text-success border border-success/20",
    },
    BLOCK: {
      label: "BLOCK",
      icon: XCircle,
      className: "bg-destructive/10 text-destructive border border-destructive/20",
    },
    connected: {
      label: "Connected",
      icon: CheckCircle2,
      className: "bg-success/10 text-success border border-success/20",
    },
    disconnected: {
      label: "Disconnected",
      icon: Circle,
      className: "bg-muted text-muted-foreground border border-border",
    },
    error: {
      label: "Error",
      icon: AlertCircle,
      className: "bg-destructive/10 text-destructive border border-destructive/20",
    },
  }

  const { label, icon: Icon, className } = config[status]

  return (
    <div
      className={`inline-flex items-center font-medium rounded-md ${sizeClasses[size]} ${className} transition-all duration-200 hover:scale-105`}
    >
      <Icon size={iconSize[size]} />
      <span>{label}</span>
    </div>
  )
}
