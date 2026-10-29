import React, { useState } from 'react';
import { Loader2, Sparkles, SlidersHorizontal, Info, Layers, Bell } from 'lucide-react';
import { ThemeToggle } from '@/components/theme';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Tooltip, TooltipTrigger, TooltipContent, TooltipProvider } from '@/components/ui/tooltip';
import {
  Dialog,
  DialogTrigger,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
  DialogClose,
} from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuCheckboxItem,
} from '@/components/ui/dropdown-menu';
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
  SelectGroup,
  SelectLabel,
} from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { Slider } from '@/components/ui/slider';
import { Separator } from '@/components/ui/separator';
import { Skeleton } from '@/components/ui/skeleton';
import { ScrollArea } from '@/components/ui/scroll-area';
import {
  Sheet,
  SheetTrigger,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
  SheetFooter,
  SheetClose,
} from '@/components/ui/sheet';
import { toast, Toaster } from '@/components/ui/sonner';
import { Logo } from '@/components/brand/Logo';
import { StatCard } from '@/components/brand/StatCard';
import { SourceTypeBadge, type SourceType } from '@/components/brand/SourceTypeBadge';
import { CodeBlock } from '@/components/code/CodeBlock';

const JAVA_SAMPLE = `package io.github.tarunipriya.gitcontext.retrieval;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class HybridSearchService {

    private final VectorStore vectorStore;
    private final LuceneSearchService luceneSearch;
    private final CrossEncoderReranker reranker;

    public HybridSearchService(
        VectorStore vectorStore,
        LuceneSearchService luceneSearch,
        CrossEncoderReranker reranker
    ) {
        this.vectorStore = vectorStore;
        this.luceneSearch = luceneSearch;
        this.reranker = reranker;
    }

    public List<ScoredDocument> search(String query, int topK) {
        var denseHits = vectorStore.findSimilar(query, topK * 2);
        var sparseHits = luceneSearch.searchBM25(query, topK * 2);
        var fused = ReciprocalRankFusion.fuse(denseHits, sparseHits);
        return reranker.rerank(query, fused, topK);
    }
}`;

export const StyleGuidePage: React.FC = () => {
  const [sliderVal, setSliderVal] = useState([75]);
  const [switchChecked, setSwitchChecked] = useState(true);
  const [dropdownChecked, setDropdownChecked] = useState(true);

  const colorTokens = [
    { name: 'background', css: '--background', bg: 'bg-background', text: 'text-foreground' },
    { name: 'foreground', css: '--foreground', bg: 'bg-foreground', text: 'text-background' },
    { name: 'card', css: '--card', bg: 'bg-card', text: 'text-card-foreground', border: true },
    { name: 'card-foreground', css: '--card-foreground', bg: 'bg-card-foreground', text: 'text-card' },
    { name: 'popover', css: '--popover', bg: 'bg-popover', text: 'text-popover-foreground', border: true },
    { name: 'primary', css: '--primary', bg: 'bg-primary', text: 'text-primary-foreground' },
    { name: 'primary-fg', css: '--primary-foreground', bg: 'bg-primary-foreground', text: 'text-primary', border: true },
    { name: 'secondary', css: '--secondary', bg: 'bg-secondary', text: 'text-secondary-foreground' },
    { name: 'muted', css: '--muted', bg: 'bg-muted', text: 'text-muted-foreground' },
    { name: 'muted-fg', css: '--muted-foreground', bg: 'bg-muted-foreground', text: 'text-background' },
    { name: 'accent', css: '--accent', bg: 'bg-accent', text: 'text-accent-foreground' },
    { name: 'destructive', css: '--destructive', bg: 'bg-destructive', text: 'text-destructive-foreground' },
    { name: 'success', css: '--success', bg: 'bg-success', text: 'text-success-foreground' },
    { name: 'warning', css: '--warning', bg: 'bg-warning', text: 'text-warning-foreground' },
    { name: 'border', css: '--border', bg: 'bg-border', text: 'text-foreground' },
    { name: 'input', css: '--input', bg: 'bg-input', text: 'text-foreground' },
    { name: 'ring', css: '--ring', bg: 'bg-ring', text: 'text-primary-foreground' },
  ];

  const typeScaleItems = [
    {
      label: 'text-3xl / 4xl',
      sample: 'Repository Question Answering',
      className: 'text-3xl sm:text-4xl font-extrabold tracking-tight',
    },
    {
      label: 'text-2xl',
      sample: 'Hybrid Retrieval & Rank Fusion',
      className: 'text-2xl font-bold tracking-tight',
    },
    {
      label: 'text-xl',
      sample: 'Semantic Code Search with Tree-Sitter',
      className: 'text-xl font-semibold',
    },
    {
      label: 'text-lg',
      sample: 'AST-aware chunking with reciprocal rank fusion',
      className: 'text-lg font-medium',
    },
    {
      label: 'text-base',
      sample: 'Grounded repository question answering with verified citations.',
      className: 'text-base text-foreground',
    },
    {
      label: 'text-sm',
      sample: 'Detailed metadata including commit SHAs, file paths, and PR links.',
      className: 'text-sm text-muted-foreground',
    },
    {
      label: 'text-xs',
      sample: 'Telemetry: embedMillis=150, upsertMillis=30, points=80',
      className: 'text-xs text-muted-foreground',
    },
    {
      label: 'font-mono text-xs',
      sample: 'gitcontext-backend-0.1.0-exec.jar [SHA: 4f46e5a]',
      className: 'font-mono text-xs text-primary',
    },
  ];

  const sourceTypes: SourceType[] = ['CODE', 'DOC', 'COMMIT', 'PULL_REQUEST', 'ISSUE'];

  return (
    <div className="min-h-screen bg-background text-foreground antialiased selection:bg-primary/25 selection:text-foreground">
      <Toaster />

      {/* Top Header */}
      <header className="sticky top-0 z-40 border-b border-border bg-background/80 backdrop-blur-md">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Logo size="md" />
            <span className="hidden sm:inline-block rounded-full bg-primary/10 border border-primary/20 px-2.5 py-0.5 text-[11px] font-mono text-primary font-semibold">
              Design System v0.1
            </span>
          </div>
          <div className="flex items-center gap-3">
            <ThemeToggle />
            <a
              href="#/ask"
              className="text-xs font-medium text-muted-foreground hover:text-foreground transition-colors"
            >
              Back to App
            </a>
          </div>
        </div>
      </header>

      {/* Main Content Container with centered max-w-6xl and horizontal padding */}
      <main
        className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-10 space-y-16"
        data-testid="styleguide-container"
      >
        {/* Intro Section */}
        <section className="space-y-3">
          <h1 className="text-3xl sm:text-4xl font-extrabold tracking-tight">
            GitContext Design System &amp; Component Library
          </h1>
          <p className="text-muted-foreground text-base max-w-2xl leading-relaxed">
            Developer-dark aesthetic inspired by GitHub and Vercel paired with a calm, modern AI interface.
            Tailwind CSS v4, shadcn/ui primitives, and custom brand &amp; code components.
          </p>
        </section>

        {/* 1. Brand Logo */}
        <section>
          <div className="mb-6">
            <div className="flex items-center gap-2">
              <Sparkles className="h-5 w-5 text-primary shrink-0" />
              <h2 className="text-2xl font-semibold tracking-tight">1. Brand Logo</h2>
            </div>
            <p className="text-sm text-muted-foreground mt-1.5">
              Simple SVG mark featuring an abstract git-branch node motif with wordmark across multiple sizes.
            </p>
          </div>
          <Card>
            <CardContent className="p-6 space-y-6">
              <div className="flex flex-wrap items-center gap-8">
                <div className="space-y-1">
                  <span className="text-xs text-muted-foreground block font-mono">Size SM</span>
                  <Logo size="sm" />
                </div>
                <div className="space-y-1">
                  <span className="text-xs text-muted-foreground block font-mono">Size MD</span>
                  <Logo size="md" />
                </div>
                <div className="space-y-1">
                  <span className="text-xs text-muted-foreground block font-mono">Size LG</span>
                  <Logo size="lg" />
                </div>
                <div className="space-y-1">
                  <span className="text-xs text-muted-foreground block font-mono">Mark Only</span>
                  <Logo size="md" showWordmark={false} />
                </div>
              </div>
            </CardContent>
            <CardFooter className="border-t border-border/50 bg-muted/20 px-6 py-3">
              <span className="text-xs text-muted-foreground font-mono">
                SVG mark with accent node motif and accessible title.
              </span>
            </CardFooter>
          </Card>
        </section>

        {/* 2. Color Tokens */}
        <section>
          <div className="mb-6">
            <div className="flex items-center gap-2">
              <Layers className="h-5 w-5 text-primary shrink-0" />
              <h2 className="text-2xl font-semibold tracking-tight">2. Design Tokens (Colors &amp; Swatches)</h2>
            </div>
            <p className="text-sm text-muted-foreground mt-1.5">
              Design tokens with dark-first values, WCAG AA compliant contrast, and subtle cool slate undertones.
            </p>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
            {colorTokens.map((c) => (
              <div
                key={c.name}
                className="rounded-lg border border-border p-3 space-y-2 bg-card shadow-xs transition-transform hover:-translate-y-0.5"
              >
                <div
                  className={`h-12 w-full rounded-md ${c.bg} ${c.text} flex items-center justify-center text-[10px] font-mono font-medium ${
                    c.border ? 'border border-border' : ''
                  }`}
                >
                  Aa
                </div>
                <div>
                  <div className="text-xs font-semibold truncate text-foreground">{c.name}</div>
                  <div className="text-[11px] font-mono text-muted-foreground truncate">{c.css}</div>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* 3. Typography Scale */}
        <section>
          <div className="mb-6">
            <h2 className="text-2xl font-semibold tracking-tight">3. Typography Scale (Inter &amp; JetBrains Mono)</h2>
            <p className="text-sm text-muted-foreground mt-1.5">
              Two-column scale with left-aligned samples: Inter for UI typography and JetBrains Mono for code.
            </p>
          </div>
          <Card>
            <CardContent className="p-6">
              <div className="space-y-4 font-sans">
                {typeScaleItems.map((item, idx) => (
                  <div
                    key={item.label}
                    className={`grid grid-cols-1 sm:grid-cols-[160px_1fr] items-baseline gap-2 sm:gap-6 ${
                      idx !== typeScaleItems.length - 1 ? 'border-b border-border/50 pb-4' : 'pb-1'
                    }`}
                  >
                    <span className="font-mono text-xs text-muted-foreground select-none shrink-0 text-left">
                      {item.label}
                    </span>
                    <div className={`text-left text-foreground ${item.className}`}>
                      {item.sample}
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </section>

        {/* 4. StatCards & SourceTypeBadges */}
        <section>
          <div className="mb-6">
            <h2 className="text-2xl font-semibold tracking-tight">4. Stat Cards &amp; Source Type Badges</h2>
            <p className="text-sm text-muted-foreground mt-1.5">
              Key repository telemetry metrics and subtle badges representing ingested repository artifacts.
            </p>
          </div>
          <div className="space-y-6">
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <StatCard
                value="1,248"
                label="Indexed Points"
                sublabel="Across 80 code & doc files"
                icon={<Layers className="h-4 w-4" />}
              />
              <StatCard
                value="42 ms"
                label="Search Latency"
                sublabel="Qdrant dense + Lucene BM25"
                icon={<SlidersHorizontal className="h-4 w-4" />}
              />
              <StatCard
                value="98.4%"
                label="Recall @ 10"
                sublabel="Evaluated on 50 questions"
                icon={<Sparkles className="h-4 w-4" />}
              />
            </div>

            <Card>
              <CardHeader className="p-6 pb-3">
                <CardTitle className="text-base font-semibold">Source Type Badges</CardTitle>
                <CardDescription className="text-xs">
                  Distinct subtle tints and icons representing various repository artifacts.
                </CardDescription>
              </CardHeader>
              <CardContent className="p-6 pt-0 flex flex-wrap items-center gap-3">
                {sourceTypes.map((st) => (
                  <SourceTypeBadge key={st} type={st} />
                ))}
              </CardContent>
            </Card>
          </div>
        </section>

        {/* 5. CodeBlock */}
        <section>
          <div className="mb-6">
            <h2 className="text-2xl font-semibold tracking-tight">5. CodeBlock Component</h2>
            <p className="text-sm text-muted-foreground mt-1.5">
              Lazy-loaded syntax highlighter, monospace path header with tooltip truncation, line numbers starting at 42, and copy button.
            </p>
          </div>
          <CodeBlock
            code={JAVA_SAMPLE}
            language="java"
            filePath="backend/src/main/java/io/github/tarunipriya/gitcontext/retrieval/HybridSearchService.java"
            startLine={42}
            showLineNumbers={true}
          />
        </section>

        {/* 6. UI Components & States */}
        <section>
          <div className="mb-6">
            <h2 className="text-2xl font-semibold tracking-tight">6. shadcn/ui Components &amp; States</h2>
            <p className="text-sm text-muted-foreground mt-1.5">
              Accessible primitives demonstrating default, hover, focus, disabled, and active interactive states.
            </p>
          </div>

          <div className="space-y-6">
            {/* Buttons */}
            <Card>
              <CardHeader className="p-6 pb-4">
                <CardTitle className="text-base font-semibold">Buttons (Variants, Sizes &amp; States)</CardTitle>
              </CardHeader>
              <CardContent className="p-6 pt-0 space-y-4">
                <div className="flex flex-wrap items-center gap-3">
                  <Button variant="default">Primary Button</Button>
                  <Button variant="secondary">Secondary</Button>
                  <Button variant="outline">Outline</Button>
                  <Button variant="ghost">Ghost</Button>
                  <Button variant="destructive">Destructive</Button>
                  <Button variant="link">Link Button</Button>
                </div>

                <div className="flex flex-wrap items-center gap-3">
                  <Button size="sm">Small (sm)</Button>
                  <Button size="default">Default</Button>
                  <Button size="lg">Large (lg)</Button>
                  <Button size="icon" variant="outline" aria-label="Settings">
                    <SlidersHorizontal className="h-4 w-4" />
                  </Button>
                  <Button disabled variant="default">Disabled</Button>
                  <Button disabled variant="outline">
                    <Loader2 className="h-4 w-4 animate-spin mr-2" />
                    Loading...
                  </Button>
                </div>
              </CardContent>
            </Card>

            {/* Inputs & Textareas */}
            <Card>
              <CardHeader className="p-6 pb-4">
                <CardTitle className="text-base font-semibold">Form Controls (Input &amp; Textarea)</CardTitle>
              </CardHeader>
              <CardContent className="p-6 pt-0 grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-3">
                  <label className="text-xs font-semibold text-muted-foreground block">Default Input</label>
                  <Input placeholder="e.g. spring-projects/spring-petclinic" />
                  <label className="text-xs font-semibold text-muted-foreground block">Disabled Input</label>
                  <Input disabled value="read-only-repo/gitcontext" />
                </div>
                <div className="space-y-3">
                  <label className="text-xs font-semibold text-muted-foreground block">Textarea</label>
                  <Textarea placeholder="Ask a question about this repository..." rows={3} />
                </div>
              </CardContent>
            </Card>

            {/* Badges */}
            <Card>
              <CardHeader className="p-6 pb-4">
                <CardTitle className="text-base font-semibold">Badges</CardTitle>
              </CardHeader>
              <CardContent className="p-6 pt-0 flex flex-wrap gap-2">
                <Badge variant="default">Default</Badge>
                <Badge variant="secondary">Secondary</Badge>
                <Badge variant="outline">Outline</Badge>
                <Badge variant="destructive">Destructive</Badge>
                <Badge variant="success">Success</Badge>
                <Badge variant="warning">Warning</Badge>
              </CardContent>
            </Card>

            {/* Tabs */}
            <Card>
              <CardHeader className="p-6 pb-4">
                <CardTitle className="text-base font-semibold">Tabs</CardTitle>
              </CardHeader>
              <CardContent className="p-6 pt-0">
                <Tabs defaultValue="ask" className="w-full">
                  <TabsList className="grid w-full grid-cols-2 max-w-xs">
                    <TabsTrigger value="ask">Ask Question</TabsTrigger>
                    <TabsTrigger value="repos">Repositories</TabsTrigger>
                  </TabsList>
                  <TabsContent value="ask" className="p-4 rounded-md border border-border bg-card mt-3">
                    <p className="text-xs text-muted-foreground">Ask interface tab content rendered here.</p>
                  </TabsContent>
                  <TabsContent value="repos" className="p-4 rounded-md border border-border bg-card mt-3">
                    <p className="text-xs text-muted-foreground">Repositories listing tab content rendered here.</p>
                  </TabsContent>
                </Tabs>
              </CardContent>
            </Card>

            {/* Overlays & Interactive */}
            <Card>
              <CardHeader className="p-6 pb-4">
                <CardTitle className="text-base font-semibold">Interactive Primitives &amp; Overlays</CardTitle>
                <CardDescription className="text-xs">
                  Accessible dialogs, dropdowns, selects, sheets, tooltips, and toasts.
                </CardDescription>
              </CardHeader>
              <CardContent className="p-6 pt-0 flex flex-wrap items-center gap-4">
                {/* Dialog */}
                <Dialog>
                  <DialogTrigger asChild>
                    <Button variant="outline">Open Dialog</Button>
                  </DialogTrigger>
                  <DialogContent>
                    <DialogHeader>
                      <DialogTitle>Confirm Re-indexing</DialogTitle>
                      <DialogDescription>
                        This will re-index all modified files in the target repository.
                      </DialogDescription>
                    </DialogHeader>
                    <div className="py-2 text-sm text-muted-foreground">
                      Estimated time: 1.2 seconds for 45 files.
                    </div>
                    <DialogFooter>
                      <DialogClose asChild>
                        <Button variant="outline">Cancel</Button>
                      </DialogClose>
                      <Button variant="default">Confirm</Button>
                    </DialogFooter>
                  </DialogContent>
                </Dialog>

                {/* Sheet */}
                <Sheet>
                  <SheetTrigger asChild>
                    <Button variant="outline">Open Sheet Drawer</Button>
                  </SheetTrigger>
                  <SheetContent side="right">
                    <SheetHeader>
                      <SheetTitle>Repository Details</SheetTitle>
                      <SheetDescription>
                        Inspecting configuration for spring-projects/spring-petclinic
                      </SheetDescription>
                    </SheetHeader>
                    <div className="py-6 space-y-4">
                      <div className="space-y-1">
                        <span className="text-xs text-muted-foreground">Head Commit</span>
                        <p className="font-mono text-xs">1234567890abcdef</p>
                      </div>
                      <div className="space-y-1">
                        <span className="text-xs text-muted-foreground">Indexed Chunks</span>
                        <p className="font-mono text-xs">80 chunks</p>
                      </div>
                    </div>
                    <SheetFooter>
                      <SheetClose asChild>
                        <Button variant="outline">Close</Button>
                      </SheetClose>
                    </SheetFooter>
                  </SheetContent>
                </Sheet>

                {/* Dropdown Menu */}
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button variant="outline">Dropdown Menu</Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent>
                    <DropdownMenuLabel>Actions</DropdownMenuLabel>
                    <DropdownMenuItem onClick={() => toast.info('Synced with origin')}>
                      Sync Repo
                    </DropdownMenuItem>
                    <DropdownMenuItem onClick={() => toast.info('Exported embeddings')}>
                      Export Embeddings
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuCheckboxItem
                      checked={dropdownChecked}
                      onCheckedChange={setDropdownChecked}
                    >
                      Include Docs
                    </DropdownMenuCheckboxItem>
                  </DropdownMenuContent>
                </DropdownMenu>

                {/* Select */}
                <div className="w-[180px]">
                  <Select defaultValue="hybrid">
                    <SelectTrigger aria-label="Search Mode">
                      <SelectValue placeholder="Search Mode" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        <SelectLabel>Retrieval Mode</SelectLabel>
                        <SelectItem value="hybrid">Hybrid (Vector + BM25)</SelectItem>
                        <SelectItem value="dense">Dense Vector Only</SelectItem>
                        <SelectItem value="bm25">Sparse BM25 Only</SelectItem>
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </div>

                {/* Tooltip */}
                <TooltipProvider>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Button variant="outline" size="icon" aria-label="Info Tooltip">
                        <Info className="h-4 w-4" />
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent>
                      <p className="text-xs">Tree-sitter AST Chunking is enabled</p>
                    </TooltipContent>
                  </Tooltip>
                </TooltipProvider>

                {/* Toast Triggers */}
                <Button
                  variant="secondary"
                  onClick={() => toast.success('Repository indexed successfully!')}
                >
                  <Bell className="h-4 w-4 mr-2" />
                  Test Toast
                </Button>
              </CardContent>
            </Card>

            {/* Switch, Slider, Separator, Skeleton, ScrollArea */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Sliders & Switches */}
              <Card>
                <CardHeader className="p-6 pb-4">
                  <CardTitle className="text-base font-semibold">Switch &amp; Slider</CardTitle>
                </CardHeader>
                <CardContent className="p-6 pt-0 space-y-6">
                  <div className="flex items-center justify-between">
                    <div>
                      <label htmlFor="reranker-switch" className="text-sm font-medium">Cross-Encoder Reranker</label>
                      <p className="text-xs text-muted-foreground">Refine top candidates using cross-encoder</p>
                    </div>
                    <Switch
                      id="reranker-switch"
                      checked={switchChecked}
                      onCheckedChange={setSwitchChecked}
                      aria-label="Toggle Cross-Encoder Reranker"
                    />
                  </div>
                  <Separator />
                  <div className="space-y-2">
                    <div className="flex justify-between text-xs font-mono">
                      <span>Similarity Threshold</span>
                      <span>{sliderVal[0]}%</span>
                    </div>
                    <Slider
                      value={sliderVal}
                      onValueChange={setSliderVal}
                      max={100}
                      step={1}
                      aria-label="Similarity Threshold"
                    />
                  </div>
                </CardContent>
              </Card>

              {/* Skeleton & ScrollArea */}
              <Card>
                <CardHeader className="p-6 pb-4">
                  <CardTitle className="text-base font-semibold">Skeleton &amp; Scroll Area</CardTitle>
                </CardHeader>
                <CardContent className="p-6 pt-0 space-y-4">
                  <div className="space-y-2">
                    <div className="flex items-center gap-3">
                      <Skeleton className="h-9 w-9 rounded-full" />
                      <div className="space-y-1.5 flex-1">
                        <Skeleton className="h-3 w-3/4" />
                        <Skeleton className="h-2.5 w-1/2" />
                      </div>
                    </div>
                  </div>
                  <Separator />
                  <div>
                    <span className="text-xs font-mono text-muted-foreground block mb-2">Scrollable Container</span>
                    <ScrollArea className="h-20 w-full rounded-md border border-border p-2 text-xs">
                      <p className="text-muted-foreground">
                        GitContext ingests a GitHub repository (code, README/docs, commits, PRs, incident-labelled issues),
                        chunks code by function/class/module using tree-sitter, embeds chunks into Qdrant, retrieves with
                        hybrid search (vector + BM25 via Apache Lucene) plus a reranker, and generates answers with
                        citations.
                      </p>
                    </ScrollArea>
                  </div>
                </CardContent>
              </Card>
            </div>
          </div>
        </section>
      </main>
    </div>
  );
};

export default StyleGuidePage;
