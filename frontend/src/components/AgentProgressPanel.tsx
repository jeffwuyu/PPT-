import { ChevronDown, ChevronRight, Cpu, FileText, MessageSquare, Wrench } from 'lucide-react';
import type {
  Checkpoint,
  ProgressArtifact,
  ProgressCheckpoint,
  ProgressEvent,
  ProgressMessage,
  ProgressSnapshot,
  ProgressToolCall
} from '../types';

type Props = {
  snapshot: ProgressSnapshot | null;
  events: ProgressEvent[];
  expanded: Set<number>;
  onToggle: (checkpointId: number) => void;
};

export default function AgentProgressPanel({ snapshot, events, expanded, onToggle }: Props) {
  const checkpoints = mergeCheckpoints(snapshot, events);
  if (!checkpoints.length) {
    return <div className="muted">生成计划审核后，这里会显示执行进度。</div>;
  }

  return (
    <div>
      {checkpoints.map((checkpoint) => {
        const isOpen = expanded.has(checkpoint.id) || checkpoint.status === 'RUNNING';
        return (
          <section className="checkpoint" key={checkpoint.id}>
            <button className="checkpoint-summary" onClick={() => onToggle(checkpoint.id)}>
              {isOpen ? <ChevronDown size={18} /> : <ChevronRight size={18} />}
              <div>
                <strong>
                  {checkpoint.order}. {checkpoint.title}
                </strong>
                <div className="muted">{checkpoint.objective}</div>
              </div>
              <span className={`badge ${checkpoint.status.toLowerCase()}`}>{checkpoint.status}</span>
            </button>
            {isOpen && (
              <div className="checkpoint-body">
                <div className="row">
                  <span className="badge">
                    {checkpoint.currentIterations}/{checkpoint.maxIterations}
                  </span>
                  {checkpoint.failureReason && <span className="error">{checkpoint.failureReason}</span>}
                </div>
                <LogSection
                  icon={<MessageSquare size={16} />}
                  title="React LLM 输出"
                  empty="暂无 LLM 输出"
                  items={checkpoint.messages.map((message) => <MessageLog key={message.messageId} message={message} />)}
                />
                <LogSection
                  icon={<Wrench size={16} />}
                  title="工具调用"
                  empty="暂无工具调用"
                  items={checkpoint.toolCalls.map((tool, index) => <ToolLog key={tool.toolCallId || index} tool={tool} />)}
                />
                <LogSection
                  icon={<FileText size={16} />}
                  title="生成产物"
                  empty="暂无产物记录"
                  items={checkpoint.artifacts.map((artifact, index) => (
                    <ArtifactLog key={artifact.artifactRefId || `${artifact.artifactType}-${artifact.artifactId}-${index}`} artifact={artifact} />
                  ))}
                />
              </div>
            )}
          </section>
        );
      })}
    </div>
  );
}

function LogSection({ icon, title, empty, items }: { icon: JSX.Element; title: string; empty: string; items: JSX.Element[] }) {
  return (
    <div className="log-section">
      <div className="row">
        {icon}
        <strong>{title}</strong>
      </div>
      {items.length ? items : <div className="muted">{empty}</div>}
    </div>
  );
}

function MessageLog({ message }: { message: ProgressMessage }) {
  const parsed = parseJson(message.contentText);
  return (
    <div className="log-item">
      <div className="row">
        <Cpu size={16} />
        <strong>{message.roleName}</strong>
        <span className="muted">{message.createdAt ? new Date(message.createdAt).toLocaleTimeString() : ''}</span>
      </div>
      {parsed && (
        <div className="stats">
          <div className="stat">
            <span className="muted">thought</span>
            <strong>{String(parsed.thought || '-')}</strong>
          </div>
          <div className="stat">
            <span className="muted">action</span>
            <strong>{String(parsed.action || '-')}</strong>
          </div>
          <div className="stat">
            <span className="muted">replan</span>
            <strong>{String(Boolean(parsed.needsReplan || parsed.cancelCurrent))}</strong>
          </div>
        </div>
      )}
      <pre className="code-box">{message.contentText}</pre>
    </div>
  );
}

function ToolLog({ tool }: { tool: ProgressToolCall }) {
  return (
    <div className="log-item">
      <div className="row">
        <span className="badge">{tool.status || 'SUCCESS'}</span>
        <strong>{tool.toolName}</strong>
        {typeof tool.durationMs === 'number' && <span className="muted">{tool.durationMs}ms</span>}
      </div>
      <pre className="code-box">{formatJson(tool.response || tool.responseJson || tool.errorMessage || {})}</pre>
    </div>
  );
}

function ArtifactLog({ artifact }: { artifact: ProgressArtifact }) {
  return (
    <div className="log-item">
      <div className="row">
        <span className="badge">{artifact.actionName}</span>
        <strong>{artifact.artifactType}</strong>
        <span className="muted">#{artifact.artifactId}</span>
      </div>
    </div>
  );
}

function mergeCheckpoints(snapshot: ProgressSnapshot | null, events: ProgressEvent[]): ProgressCheckpoint[] {
  const byId = new Map<number, ProgressCheckpoint>();
  for (const checkpoint of snapshot?.checkpoints || []) {
    byId.set(checkpoint.id, {
      ...checkpoint,
      messages: [...(checkpoint.messages || [])],
      toolCalls: [...(checkpoint.toolCalls || [])],
      artifacts: [...(checkpoint.artifacts || [])]
    });
  }

  for (const event of events) {
    const checkpointId = Number(event.checkpointId || event.data.checkpointId || (event.data.checkpoint as Checkpoint | undefined)?.id);
    if (!Number.isFinite(checkpointId)) {
      continue;
    }
    const existing = byId.get(checkpointId);
    if (!existing) {
      const raw = event.data.checkpoint as Checkpoint | undefined;
      if (raw) {
        byId.set(raw.id, { ...raw, messages: [], toolCalls: [], artifacts: [] });
      }
      continue;
    }
    if (event.type === 'checkpoint' && event.data.checkpoint) {
      Object.assign(existing, event.data.checkpoint);
    }
    if (event.type === 'llm-message') {
      const message = event.data as unknown as ProgressMessage;
      if (!existing.messages.some((item) => item.messageId === message.messageId)) {
        existing.messages.push(message);
      }
    }
    if (event.type === 'tool-call') {
      const tool = event.data as unknown as ProgressToolCall;
      if (!existing.toolCalls.some((item) => item.toolCallId === tool.toolCallId)) {
        existing.toolCalls.push(tool);
      }
    }
    if (event.type === 'artifact') {
      existing.artifacts.push(event.data as unknown as ProgressArtifact);
    }
  }

  return [...byId.values()].sort((a, b) => a.order - b.order);
}

function parseJson(value: string): Record<string, unknown> | null {
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

function formatJson(value: unknown) {
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  return JSON.stringify(value, null, 2);
}
