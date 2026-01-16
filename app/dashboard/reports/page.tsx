import { ReportPanel } from "@/components/report-panel"
import { recentReports } from "@/lib/mock-data"
import { Button } from "@/components/ui/button"
import { Filter, Download } from "lucide-react"

export default function ReportsPage() {
  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-foreground">AI Analysis Reports</h2>
          <p className="text-muted-foreground mt-1">Review detailed findings and recommendations</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" className="gap-2 bg-transparent">
            <Filter size={18} />
            Filter
          </Button>
          <Button variant="outline" className="gap-2 bg-transparent">
            <Download size={18} />
            Export
          </Button>
        </div>
      </div>

      <div className="space-y-4">
        {recentReports.map((report, idx) => (
          <ReportPanel key={report.id} {...report} index={idx} />
        ))}
      </div>
    </div>
  )
}
