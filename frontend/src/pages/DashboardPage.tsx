import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Edit3, FilePlus2, Sparkles } from 'lucide-react';
import { createPresentation, listPresentations } from '../lib/api';
import type { PresentationSummary } from '../types';

export default function DashboardPage() {
  const navigate = useNavigate();
  const [presentations, setPresentations] = useState<PresentationSummary[]>([]);
  const [title, setTitle] = useState('新的演示文稿');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    void load();
  }, []);

  async function load() {
    setLoading(true);
    setError('');
    try {
      setPresentations(await listPresentations());
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  async function create(event: FormEvent) {
    event.preventDefault();
    setError('');
    try {
      const presentation = await createPresentation(title);
      navigate(`/app/presentations/${presentation.id}/editor`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : '创建失败');
    }
  }

  return (
    <main className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">PPT 工作台</h1>
          <p className="subtitle">创建、编辑或交给 Agent 生成演示文稿</p>
        </div>
        <form className="inline-actions" onSubmit={create}>
          <input className="input" value={title} onChange={(event) => setTitle(event.target.value)} required />
          <button className="button">
            <FilePlus2 size={18} />
            新建
          </button>
        </form>
      </div>
      {error && <div className="error">{error}</div>}
      {loading ? (
        <div className="muted">加载中...</div>
      ) : (
        <div className="grid-list">
          {presentations.map((presentation) => (
            <article className="card" key={presentation.id}>
              <h2 className="card-title">{presentation.title}</h2>
              <div className="muted">更新于 {new Date(presentation.updatedAt).toLocaleString()}</div>
              <div className="stats">
                <div className="stat">
                  <span className="muted">页面</span>
                  <strong>{presentation.slideCount}</strong>
                </div>
                <div className="stat">
                  <span className="muted">版本</span>
                  <strong>{presentation.currentVersionNumber}</strong>
                </div>
                <div className="stat">
                  <span className="muted">状态</span>
                  <strong>{presentation.status}</strong>
                </div>
              </div>
              <div className="inline-actions">
                <Link className="secondary-button" to={`/app/presentations/${presentation.id}/editor`}>
                  <Edit3 size={16} />
                  编辑
                </Link>
                <Link className="button" to={`/app/presentations/${presentation.id}/agent`}>
                  <Sparkles size={16} />
                  Agent
                </Link>
              </div>
            </article>
          ))}
          {!presentations.length && <div className="card muted">暂无 PPT，先新建一个。</div>}
        </div>
      )}
    </main>
  );
}
