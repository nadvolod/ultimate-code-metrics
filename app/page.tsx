import Link from "next/link"
import { ArrowRight, CheckCircle2, Zap, Shield, BarChart3 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { AgentHandoffAnimation } from "@/components/agent-handoff-animation"
import { LiveMetricsAnimation } from "@/components/live-metrics-animation"

export default function LandingPage() {
  return (
    <div className="min-h-screen">
      {/* Header */}
      <header className="border-b border-border bg-background/80 backdrop-blur-sm sticky top-0 z-50">
        <div className="container mx-auto px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="h-8 w-8 rounded-lg bg-primary flex items-center justify-center">
              <BarChart3 size={20} className="text-primary-foreground" />
            </div>
            <span className="text-xl font-bold text-foreground">Ultimate Code Metrics</span>
          </div>
          <nav className="flex items-center gap-6">
            <Link href="#features" className="text-sm text-muted-foreground hover:text-foreground transition-colors">
              Features
            </Link>
            <Link
              href="#how-it-works"
              className="text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              How It Works
            </Link>
            <Button asChild>
              <Link href="/dashboard">Go to Dashboard</Link>
            </Button>
          </nav>
        </div>
      </header>

      {/* Hero */}
      <section className="container mx-auto px-6 py-24 text-center animate-fade-in">
        <div className="max-w-3xl mx-auto space-y-6">
          <h1 className="text-5xl font-bold text-balance leading-tight">
            AI-Powered Code Quality Analysis for{" "}
            <span className="bg-gradient-to-r from-primary to-primary/60 bg-clip-text text-transparent">
              Faster PR Approvals
            </span>
          </h1>
          <p className="text-xl text-muted-foreground text-pretty leading-relaxed">
            Automatically analyze code quality, coverage, and risk to make confident approval decisions. Reduce review
            time by 70% while maintaining high quality standards.
          </p>
          <div className="flex items-center justify-center gap-4 pt-4">
            <Button size="lg" asChild className="group">
              <Link href="/dashboard">
                Get Started
                <ArrowRight size={18} className="ml-2 group-hover:translate-x-1 transition-transform" />
              </Link>
            </Button>
            <Button size="lg" variant="outline" asChild>
              <Link href="#how-it-works">Learn More</Link>
            </Button>
          </div>
        </div>
      </section>

      {/* Features */}
      <section id="features" className="container mx-auto px-6 py-24">
        <div className="text-center mb-16">
          <h2 className="text-3xl font-bold text-foreground mb-4">Why Teams Choose Us</h2>
          <p className="text-lg text-muted-foreground max-w-2xl mx-auto">
            Professional-grade code intelligence built for modern engineering teams
          </p>
        </div>
        <div className="grid md:grid-cols-3 gap-8">
          {[
            {
              icon: Zap,
              title: "Instant Analysis",
              description: "AI reviews your code in seconds, providing immediate feedback on quality and coverage.",
            },
            {
              icon: Shield,
              title: "Risk Detection",
              description: "Identify high-risk changes and missing coverage before they reach production.",
            },
            {
              icon: CheckCircle2,
              title: "Smart Recommendations",
              description: "Get clear APPROVE or BLOCK decisions with confidence scores and detailed reasoning.",
            },
          ].map((feature, idx) => (
            <div
              key={idx}
              className="bg-card border border-border rounded-lg p-8 space-y-4 hover:border-primary/30 transition-all duration-300 hover:shadow-lg hover:shadow-primary/5 animate-slide-up"
              style={{ animationDelay: `${idx * 150}ms` }}
            >
              <div className="h-12 w-12 rounded-lg bg-primary/10 flex items-center justify-center">
                <feature.icon size={24} className="text-primary" />
              </div>
              <h3 className="text-xl font-semibold text-foreground">{feature.title}</h3>
              <p className="text-muted-foreground leading-relaxed">{feature.description}</p>
            </div>
          ))}
        </div>
      </section>

      {/* How It Works */}
      <section id="how-it-works" className="container mx-auto px-6 py-24 bg-muted/30">
        <div className="text-center mb-16">
          <h2 className="text-3xl font-bold text-foreground mb-4">How It Works</h2>
          <p className="text-lg text-muted-foreground max-w-2xl mx-auto">
            Three simple steps to automated code intelligence
          </p>
        </div>

        <div className="mb-16">
          <AgentHandoffAnimation />
        </div>

        <div className="grid md:grid-cols-3 gap-12 max-w-5xl mx-auto">
          {[
            {
              step: "01",
              title: "Connect GitHub",
              description: "Link your repositories and let us monitor your pull requests automatically.",
            },
            {
              step: "02",
              title: "AI Analysis",
              description: "Our AI analyzes code quality, coverage, and risk factors for every PR.",
            },
            {
              step: "03",
              title: "Get Recommendations",
              description: "Receive clear APPROVE or BLOCK decisions with detailed reasoning and metrics.",
            },
          ].map((step, idx) => (
            <div
              key={idx}
              className="text-center space-y-4 animate-scale-in"
              style={{ animationDelay: `${idx * 200}ms` }}
            >
              <div className="text-5xl font-bold text-primary/20">{step.step}</div>
              <h3 className="text-xl font-semibold text-foreground">{step.title}</h3>
              <p className="text-muted-foreground leading-relaxed">{step.description}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="container mx-auto px-6 py-24">
        <div className="text-center mb-12">
          <h2 className="text-3xl font-bold text-foreground mb-4">Real Impact, Real Numbers</h2>
          <p className="text-lg text-muted-foreground max-w-2xl mx-auto">
            See how teams are accelerating their workflow with Ultimate Code Metrics
          </p>
        </div>
        <LiveMetricsAnimation />
      </section>

      {/* CTA */}
      <section className="container mx-auto px-6 py-24 text-center">
        <div className="max-w-3xl mx-auto space-y-6 animate-fade-in">
          <h2 className="text-4xl font-bold text-foreground text-balance">Ready to Ship Faster with Confidence?</h2>
          <p className="text-lg text-muted-foreground">
            Join engineering teams using AI to maintain quality while moving fast.
          </p>
          <Button size="lg" asChild className="group">
            <Link href="/dashboard">
              Start Analyzing
              <ArrowRight size={18} className="ml-2 group-hover:translate-x-1 transition-transform" />
            </Link>
          </Button>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-border bg-card">
        <div className="container mx-auto px-6 py-8 text-center text-sm text-muted-foreground">
          <p>&copy; 2026 Ultimate Code Metrics. Built for modern engineering teams.</p>
        </div>
      </footer>
    </div>
  )
}
