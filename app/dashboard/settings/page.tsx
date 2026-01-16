import { settingsConfig } from "@/lib/mock-data"
import { Button } from "@/components/ui/button"

export default function SettingsPage() {
  return (
    <div className="space-y-8 max-w-3xl">
      <div>
        <h2 className="text-2xl font-bold text-foreground">Settings</h2>
        <p className="text-muted-foreground mt-1">Configure your test analysis preferences</p>
      </div>

      {/* Auto-Approval Settings */}
      <div className="bg-card border border-border rounded-lg p-6 space-y-4 animate-fade-in">
        <h3 className="text-lg font-semibold text-foreground">Auto-Approval Rules</h3>
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <div>
              <p className="font-medium text-foreground">Enable Auto-Approval</p>
              <p className="text-sm text-muted-foreground">Automatically approve PRs that meet criteria</p>
            </div>
            <div
              className={`px-3 py-1 rounded-md text-sm font-medium ${
                settingsConfig.autoApproval.enabled
                  ? "bg-success/10 text-success border border-success/20"
                  : "bg-muted text-muted-foreground"
              }`}
            >
              {settingsConfig.autoApproval.enabled ? "Enabled" : "Disabled"}
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4 pt-2">
            <div>
              <p className="text-sm text-muted-foreground mb-1">Minimum Coverage</p>
              <p className="text-2xl font-semibold text-foreground">{settingsConfig.autoApproval.minCoverage}%</p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground mb-1">Minimum Confidence</p>
              <p className="text-2xl font-semibold text-foreground">{settingsConfig.autoApproval.minConfidence}%</p>
            </div>
          </div>
        </div>
      </div>

      {/* Notifications */}
      <div
        className="bg-card border border-border rounded-lg p-6 space-y-4 animate-fade-in"
        style={{ animationDelay: "100ms" }}
      >
        <h3 className="text-lg font-semibold text-foreground">Notifications</h3>
        <div className="space-y-3">
          {Object.entries(settingsConfig.notifications).map(([key, value]) => (
            <div key={key} className="flex items-center justify-between">
              <p className="font-medium text-foreground capitalize">{key} Notifications</p>
              <div
                className={`px-3 py-1 rounded-md text-sm font-medium ${
                  value ? "bg-success/10 text-success border border-success/20" : "bg-muted text-muted-foreground"
                }`}
              >
                {value ? "Enabled" : "Disabled"}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* AI Model Configuration */}
      <div
        className="bg-card border border-border rounded-lg p-6 space-y-4 animate-fade-in"
        style={{ animationDelay: "200ms" }}
      >
        <h3 className="text-lg font-semibold text-foreground">AI Model Configuration</h3>
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <p className="text-sm text-muted-foreground mb-1">Provider</p>
              <p className="text-lg font-medium text-foreground">{settingsConfig.aiModel.provider}</p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground mb-1">Model</p>
              <p className="text-lg font-medium text-foreground">{settingsConfig.aiModel.model}</p>
            </div>
          </div>
          <div>
            <p className="text-sm text-muted-foreground mb-1">Temperature</p>
            <p className="text-lg font-medium text-foreground">{settingsConfig.aiModel.temperature}</p>
          </div>
        </div>
      </div>

      <div className="flex gap-3">
        <Button>Save Changes</Button>
        <Button variant="outline">Reset to Defaults</Button>
      </div>
    </div>
  )
}
