import type React from "react"
import { SidebarNav } from "@/components/sidebar-nav"
import { BarChart3, Bell } from "lucide-react"
import { Button } from "@/components/ui/button"

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <div className="min-h-screen flex">
      {/* Sidebar */}
      <aside className="w-64 border-r border-border bg-card flex flex-col">
        <div className="p-6 border-b border-border">
          <div className="flex items-center gap-2">
            <div className="h-8 w-8 rounded-lg bg-primary flex items-center justify-center">
              <BarChart3 size={20} className="text-primary-foreground" />
            </div>
            <span className="font-bold text-foreground">Code Metrics</span>
          </div>
        </div>
        <div className="flex-1 py-6">
          <SidebarNav />
        </div>
      </aside>

      {/* Main Content */}
      <div className="flex-1 flex flex-col">
        {/* Header */}
        <header className="border-b border-border bg-card px-8 py-4 flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-foreground">Dashboard</h1>
            <p className="text-sm text-muted-foreground">Monitor your test metrics and AI insights</p>
          </div>
          <Button size="icon" variant="ghost">
            <Bell size={20} />
          </Button>
        </header>

        {/* Page Content */}
        <main className="flex-1 p-8 bg-background">{children}</main>
      </div>
    </div>
  )
}
