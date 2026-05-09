import { ChangeEvent, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { Check, Download, Edit3, FileUp, Play, Sparkles, X } from 'lucide-react';
import AgentProgressPanel from '../components/AgentProgressPanel';
import {
  approveAgentPlan,
  createAgentJob,
  executeAgentJob,
  getPresentation,
  planAgentJob,
  publishAgentJob,
  sseUrl,
  uploadFile
} from '../lib/api';
import { getAccessToken } from '../lib/storage';
import type { AgentJob, FileAsset, Presentation, ProgressEvent, ProgressSnapshot } from '../types';

export default function AgentPage() {
  const { id } = useParams();
  const presentationId = Number(id);
  const [presentation, setPresentation] = useState<Presentation | null>(null);
  const [prompt, setPrompt] = useState('请基于上传材料生成一份结构清晰、适合汇报的 PPT。');
  const [sources, setSources] = useState<FileAsset[]>([]);
  const [job, setJob] = useState<AgentJob | null>(null);
  const [snapshot, setSnapshot] = useState<ProgressSnapshot | null>(null);
  const [events, setEvents] = useState<ProgressEvent[]>([]);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [uploading, setUploading] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    void getPresentation(presentationId).then(setPresentation).catch((exception) => {
      setError(exception instanceof Error ? exception.message : '加载失败');
    });
    return () => abortRef.current?.abort();
  }, [presentationId]);

  async function handleFiles(event: ChangeEvent<HTMLInputElement>) {
    const files = [...(event.target.files || [])];
    if (!files.length) {
      return;
    }
    setUploading(true);
    setError('');
    try {
      const uploaded: FileAsset[] = [];
      for (const file of files) {
        uploaded.push(await uploadFile('SOURCE_TEXT', file));
      }
      setSources((current) => [...current, ...uploaded]);
      setNotice('素材已上传，会作为 PPT 制作导向。');
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : '素材上传失败');
    } finally {
      setUploading(false);
      event.target.value = '';
    }
  }

  async function createAndPlan() {
    setError('');
    setNotice('');
    setBusy(true);
    try {
      const sourceIds = sources.map((source) => source.id);
      const created = await createAgentJob(presentationId, prompt, sourceIds);
      setJob(created);
      const planned = await planAgentJob(created.id);
      setJob(planned);
      setNotice('计划已生成，请审核中文译文和原始计划后执行。');
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : '生成计划失败');
    } finally {
      setBusy(false);
    }
  }

  async function approveAndExecute() {
    if (!job) {
      return;
    }
    setError('');
    setNotice('');
    setBusy(true);
    setEvents([]);
    setSnapshot(null);
    startSse(job.id);
    try {
      const approved = await approveAgentPlan(job.id);
      setJob(approved);
      const executed = await executeAgentJob(job.id);
      setJob(executed);
      setNotice('Agent 执行完成，编辑器中可查看生成内容。');
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : '执行失败');
    } finally {
      setBusy(false);
    }
  }

  async function publish() {
    if (!job) {
      return;
    }
    setError('');
    setNotice('');
    try {
      const result = await publishAgentJob(job.id);
      setNotice(`导出已完成：${JSON.stringify(result)}`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : '导出失败');
    }
  }

  function startSse(jobId: number) {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    void fetchEventSource(sseUrl(`/agent/jobs/${jobId}/events`), {
      signal: controller.signal,
      headers: {
        Authorization: `Bearer ${getAccessToken() || ''}`
      },
      onmessage(message) {
        if (!message.data) {
          return;
        }
        const parsed = JSON.parse(message.data);
        if (message.event === 'snapshot') {
          const next = parsed as ProgressSnapshot;
          setSnapshot(next);
          setExpanded(new Set(next.checkpoints.filter((checkpoint) => checkpoint.status === 'RUNNING').map((checkpoint) => checkpoint.id)));
          return;
        }
        const event = parsed as ProgressEvent;
        setEvents((current) => [...current, event]);
        const checkpointId = Number(event.checkpointId || event.data.checkpointId || (event.data.checkpoint as { id?: number } | undefined)?.id);
        if (Number.isFinite(checkpointId) && (event.type === 'checkpoint' || event.type === 'llm-message')) {
          setExpanded((current) => new Set(current).add(checkpointId));
        }
      },
      onerror(exception) {
        setError(exception instanceof Error ? exception.message : 'SSE 连接中断');
        throw exception;
      }
    });
  }

  function toggle(checkpointId: number) {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(checkpointId)) {
        next.delete(checkpointId);
      } else {
        next.add(checkpointId);
      }
      return next;
    });
  }

  return (
    <main className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">{presentation?.title || 'Agent 生成'}</h1>
          <p className="subtitle">上传文本素材，生成计划，审核后执行。</p>
        </div>
        <div className="inline-actions">
          <Link className="secondary-button" to={`/app/presentations/${presentationId}/editor`}>
            <Edit3 size={16} />
            编辑器
          </Link>
          <button className="secondary-button" disabled={!job || job.status !== 'GENERATED'} onClick={publish}>
            <Download size={16} />
            导出
          </button>
        </div>
      </div>

      {error && <div className="error">{error}</div>}
      {notice && <div className="success">{notice}</div>}

      <div className="agent-layout">
        <section className="card form">
          <label className="field">
            生成提示词
            <textarea className="textarea" value={prompt} onChange={(event) => setPrompt(event.target.value)} />
          </label>

          <label className="field">
            文本素材
            <span className="secondary-button file-button">
              <FileUp size={16} />
              {uploading ? '上传中' : '上传 txt/md/pdf/docx'}
              <input type="file" multiple accept=".txt,.md,.pdf,.docx,text/plain,text/markdown,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onChange={handleFiles} />
            </span>
          </label>
          <div className="source-list">
            {sources.map((source) => (
              <span className="source-chip" key={source.id}>
                {source.originalFilename}
                <button
                  className="chip-remove"
                  onClick={() => setSources((current) => current.filter((item) => item.id !== source.id))}
                  aria-label="移除素材"
                >
                  <X size={14} />
                </button>
              </span>
            ))}
          </div>

          <button className="button" disabled={busy || !prompt.trim()} onClick={createAndPlan}>
            <Sparkles size={18} />
            {busy && !job ? '生成中' : '生成计划'}
          </button>

          {job?.planMarkdown && (
            <>
              <div className="row">
                <span className={`badge ${job.status.toLowerCase()}`}>{job.status}</span>
                <strong>计划审核</strong>
              </div>
              <div className="plan-compare">
                <section>
                  <h3>中文译文</h3>
                  <pre className="markdown-box">{job.translatedPlanMarkdown || job.planMarkdown}</pre>
                </section>
                <section>
                  <h3>原始计划</h3>
                  <pre className="markdown-box">{job.planMarkdown}</pre>
                </section>
              </div>
              <button className="button" disabled={busy} onClick={approveAndExecute}>
                {busy ? <Play size={18} /> : <Check size={18} />}
                {busy ? '执行中' : '确认计划并执行'}
              </button>
            </>
          )}
        </section>

        <section className="card">
          <div className="page-header">
            <div>
              <h2 className="card-title">执行进度</h2>
              <p className="subtitle">React 阶段的 LLM 原始输出会显示在对应 checkpoint 下。</p>
            </div>
            {job && <span className={`badge ${job.status.toLowerCase()}`}>{job.status}</span>}
          </div>
          <AgentProgressPanel snapshot={snapshot} events={events} expanded={expanded} onToggle={toggle} />
        </section>
      </div>
    </main>
  );
}
