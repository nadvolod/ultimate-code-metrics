import { SourceCard } from "@/components/source-card"
import { dataSources } from "@/lib/mock-data"
import { Button } from "@/components/ui/button"
import { Plus } from "lucide-react"

export default function DataSourcesPage() {
  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-foreground">Data Sources</h2>
          <p className="text-muted-foreground mt-1">Manage your connected GitHub repositories</p>
        </div>
        <Button className="gap-2">
          <Plus size={18} />
          Add Repository
        </Button>
      </div>

      <div className="grid gap-4">
        {dataSources.map((source, idx) => (
          <SourceCard key={source.id} {...source} index={idx} />
        ))}
      </div>

      <div className="bg-card border border-border rounded-lg p-8 text-center animate-fade-in">
        <h3 className="text-lg font-semibold text-foreground mb-2">Need to add more repositories?</h3>
        <p className="text-muted-foreground mb-4">
          Connect additional GitHub repositories to expand your test analysis coverage.
        </p>
        <Button variant="outline">Browse Repositories</Button>
      </div>
    </div>
  )
}
