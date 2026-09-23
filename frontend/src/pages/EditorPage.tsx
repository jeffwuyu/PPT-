import { ChangeEvent, KeyboardEvent, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { BringToFront, FilePlus2, Image, Layers, Save, Sparkles, Square, Trash2, Type } from 'lucide-react';
import SlideCanvas from '../components/SlideCanvas';
import {
  createElement,
  createSlide,
  deleteElement,
  deleteSlide as deleteSlideApi,
  getPresentation,
  updateElement,
  updatePresentation,
  updateSlide,
  uploadFile
} from '../lib/api';
import { CollaborationClient, CollaborationMember, RemoteCursor, connectCollaboration } from '../lib/collaboration';
import type { Presentation, Slide, SlideElement, SlideElementType } from '../types';

export default function EditorPage() {
  const { id } = useParams();
  const presentationId = Number(id);
  const [presentation, setPresentation] = useState<Presentation | null>(null);
  const [activeSlideId, setActiveSlideId] = useState<number | null>(null);
  const [selectedElementId, setSelectedElementId] = useState<number | null>(null);
  const [members, setMembers] = useState<CollaborationMember[]>([]);
  const [cursors, setCursors] = useState<RemoteCursor[]>([]);
  const [showJson, setShowJson] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const collaborationRef = useRef<CollaborationClient | null>(null);

  useEffect(() => {
    void load();
    return () => collaborationRef.current?.close();
  }, [presentationId]);

  useEffect(() => {
    function handleKeyDown(event: globalThis.KeyboardEvent) {
      const target = event.target as HTMLElement | null;
      if (target?.tagName === 'INPUT' || target?.tagName === 'TEXTAREA' || target?.isContentEditable) {
        return;
      }
      if ((event.key === 'Delete' || event.key === 'Backspace') && selectedElementId) {
        event.preventDefault();
        void removeElement();
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [selectedElementId, presentation]);

  const activeSlide = useMemo(() => {
    if (!presentation) {
      return null;
    }
    return presentation.slides.find((slide) => slide.id === activeSlideId) || presentation.slides[0] || null;
  }, [activeSlideId, presentation]);

  const selectedElement = useMemo(() => {
    return activeSlide?.elements.find((element) => element.id === selectedElementId) || null;
  }, [activeSlide, selectedElementId]);

  async function load() {
    setError('');
    setNotice('');
    try {
      const data = await getPresentation(presentationId);
      setPresentation(data);
      setActiveSlideId((current) => current || data.slides[0]?.id || null);
      setupCollaboration(data);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : '加载失败');
    }
  }

  function setupCollaboration(data: Presentation) {
    collaborationRef.current?.close();
    collaborationRef.current = connectCollaboration(presentationId, data, {
      onPresentation: (remote) => {
        setPresentation(remote);
        setActiveSlideId((current) => current || remote.slides[0]?.id || null);
      },
      onPresence: setMembers,
      onCursor: (cursor) => {
        setCursors((current) => [cursor, ...current.filter((item) => item.clientId !== cursor.clientId)].slice(0, 8));
      },
      onError: setError
    });
  }

  function publish(next: Presentation) {
    setPresentation(next);
    collaborationRef.current?.publishPresentation(next);
  }

  async function addSlide() {
    if (!presentation) {
      return;
    }
    const nextOrder = presentation.slides.length + 1;
    const data = await createSlide(presentation.id, nextOrder, `第 ${nextOrder} 页`);
    publish(data);
    setActiveSlideId(data.slides[data.slides.length - 1]?.id || null);
  }

  async function removeSlide() {
    if (!presentation || !activeSlide) {
      return;
    }
    const lockToken = collaborationRef.current?.acquireLock('SLIDE', activeSlide.id, 'DELETE_SLIDE');
    try {
      const currentIndex = presentation.slides.findIndex((slide) => slide.id === activeSlide.id);
      const data = await deleteSlideApi(activeSlide.id);
      publish(data);
      const nextSlide = data.slides[Math.max(0, currentIndex - 1)] || data.slides[0] || null;
      setActiveSlideId(nextSlide?.id || null);
      setSelectedElementId(null);
      setNotice('页面已删除');
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : '删除页面失败');
    } finally {
      if (lockToken) {
        collaborationRef.current?.releaseLock(lockToken);
      }
    }
  }

  async function addElement(type: SlideElementType) {
    if (!activeSlide) {
      return;
    }
    const element = await createElement(activeSlide.id, type, activeSlide.elements.length + 1);
    const data = await getPresentation(presentationId);
    publish(data);
    setSelectedElementId(element.id);
  }

  async function removeElement() {
    if (!selectedElement) {
      return;
    }
    await deleteElement(selectedElement);
    const data = await getPresentation(presentationId);
    publish(data);
    setSelectedElementId(null);
  }

  async function saveTitle() {
    if (!presentation) {
      return;
    }
    const data = await updatePresentation(presentation);
    publish(data);
    setNotice('标题已保存');
  }

  async function commitSlide(slide: Slide) {
    const data = await updateSlide(slide);
    publish(data);
  }

  function changeElement(element: SlideElement) {
    if (!presentation || !activeSlide) {
      return;
    }
    publish({
      ...presentation,
      slides: presentation.slides.map((slide) =>
        slide.id === activeSlide.id
          ? { ...slide, elements: slide.elements.map((item) => (item.id === element.id ? element : item)) }
          : slide
      )
    });
  }

  async function commitElement(element: SlideElement) {
    const saved = await updateElement(element);
    changeElement(saved);
    setSelectedElementId(saved.id);
  }

  function updateSelected(patch: Partial<SlideElement>) {
    if (!selectedElement) {
      return;
    }
    changeElement({ ...selectedElement, ...patch });
  }

  function updateSelectedStyle(key: string, value: unknown) {
    if (!selectedElement) {
      return;
    }
    changeElement({ ...selectedElement, style: { ...selectedElement.style, [key]: value } });
  }

  function updateSelectedContent(key: string, value: unknown) {
    if (!selectedElement) {
      return;
    }
    changeElement({ ...selectedElement, content: { ...selectedElement.content, [key]: value } });
  }

  async function uploadImage(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file || !selectedElement || selectedElement.type !== 'IMAGE') {
      return;
    }
    const uploaded = await uploadFile('USER_IMAGE', file);
    const next = {
      ...selectedElement,
      content: {
        ...selectedElement.content,
        fileAssetId: uploaded.id,
        publicUrl: uploaded.publicUrl,
        altText: uploaded.originalFilename
      }
    };
    await commitElement(next);
    event.target.value = '';
  }

  if (!presentation) {
    return (
      <main className="page">
        {error ? <div className="error">{error}</div> : <div className="muted">加载中...</div>}
      </main>
    );
  }

  return (
    <main className="editor-layout" onKeyDown={(event: KeyboardEvent) => event.stopPropagation()}>
      <aside className="panel">
        <div className="panel-header">
          <strong>页面</strong>
          <div className="inline-actions">
            <button className="icon-button" onClick={addSlide} title="新增页面" aria-label="新增页面">
              <FilePlus2 size={17} />
            </button>
            <button className="icon-button" onClick={removeSlide} disabled={!activeSlide} title="删除页面" aria-label="删除页面">
              <Trash2 size={17} />
            </button>
          </div>
        </div>
        <div className="panel-body slide-list">
          {presentation.slides.map((slide) => (
            <button
              className={`slide-thumb ${activeSlide?.id === slide.id ? 'active' : ''}`}
              key={slide.id}
              onClick={() => {
                setActiveSlideId(slide.id);
                setSelectedElementId(null);
              }}
            >
              <strong>{slide.pageOrder}. {slide.title || 'Untitled'}</strong>
              <div className="muted">{slide.elements.length} 个元素</div>
            </button>
          ))}
          {!presentation.slides.length && <div className="muted">暂无页面</div>}
        </div>
      </aside>

      <section className="panel">
        <div className="panel-header">
          <div className="row">
            <input
              className="input"
              value={presentation.title}
              onChange={(event) => setPresentation({ ...presentation, title: event.target.value })}
            />
            <button className="secondary-button" onClick={saveTitle}>
              <Save size={16} />
              保存
            </button>
          </div>
          <div className="inline-actions">
            <span className="badge">{members.length || 1} 在线</span>
            <Link className="button" to={`/app/presentations/${presentation.id}/agent`}>
              <Sparkles size={16} />
              Agent
            </Link>
          </div>
        </div>
        <div className="canvas-wrap" onClick={() => setSelectedElementId(null)}>
          <SlideCanvas
            presentation={presentation}
            slide={activeSlide}
            selectedElementId={selectedElementId}
            onSelectElement={(element) => setSelectedElementId(element.id)}
            onChangeElement={changeElement}
            onCommitElement={commitElement}
            onCursor={(x, y, elementId) => activeSlide && collaborationRef.current?.sendCursor({ slideId: activeSlide.id, elementId, x, y })}
          />
          <div className="remote-cursors">
            {cursors.filter((cursor) => cursor.slideId === activeSlide?.id).map((cursor) => (
              <span key={cursor.clientId} className="remote-cursor">
                用户 {cursor.actorUserId}
              </span>
            ))}
          </div>
        </div>
      </section>

      <aside className="panel">
        <div className="panel-header">
          <strong>属性</strong>
          <button className="secondary-button" onClick={() => setShowJson((value) => !value)}>高级</button>
        </div>
        <div className="panel-body form">
          {error && <div className="error">{error}</div>}
          {notice && <div className="success">{notice}</div>}
          {activeSlide && (
            <label className="field">
              页面标题
              <input
                className="input"
                value={activeSlide.title || ''}
                onChange={(event) => publish(replaceSlide(presentation, { ...activeSlide, title: event.target.value }))}
                onBlur={() => commitSlide(activeSlide)}
              />
            </label>
          )}
          <div className="inline-actions">
            <button className="secondary-button" onClick={() => addElement('TEXT')} title="文本"><Type size={16} /></button>
            <button className="secondary-button" onClick={() => addElement('IMAGE')} title="图片"><Image size={16} /></button>
            <button className="secondary-button" onClick={() => addElement('SHAPE')} title="形状"><Square size={16} /></button>
            <button className="secondary-button" onClick={() => addElement('CHART_PLACEHOLDER')} title="图表"><Layers size={16} /></button>
          </div>

          {selectedElement ? (
            <ElementProperties
              element={selectedElement}
              onPatch={updateSelected}
              onStyle={updateSelectedStyle}
              onContent={updateSelectedContent}
              onCommit={() => commitElement(selectedElement)}
              onDelete={removeElement}
              onImageUpload={uploadImage}
            />
          ) : (
            <div className="muted">选择画布元素后编辑属性。双击文本可直接改字。</div>
          )}

          {showJson && (
            <details open className="debug-panel">
              <summary>JSON 调试</summary>
              <pre className="code-box">{JSON.stringify(selectedElement || activeSlide || presentation, null, 2)}</pre>
            </details>
          )}
        </div>
      </aside>
    </main>
  );
}

function ElementProperties({
  element,
  onPatch,
  onStyle,
  onContent,
  onCommit,
  onDelete,
  onImageUpload
}: {
  element: SlideElement;
  onPatch: (patch: Partial<SlideElement>) => void;
  onStyle: (key: string, value: unknown) => void;
  onContent: (key: string, value: unknown) => void;
  onCommit: () => void;
  onDelete: () => void;
  onImageUpload: (event: ChangeEvent<HTMLInputElement>) => void;
}) {
  return (
    <>
      <div className="property-grid">
        <NumberField label="X" value={element.x} onChange={(value) => onPatch({ x: value })} onBlur={onCommit} />
        <NumberField label="Y" value={element.y} onChange={(value) => onPatch({ y: value })} onBlur={onCommit} />
        <NumberField label="宽" value={element.width} onChange={(value) => onPatch({ width: value })} onBlur={onCommit} />
        <NumberField label="高" value={element.height} onChange={(value) => onPatch({ height: value })} onBlur={onCommit} />
        <NumberField label="旋转" value={element.rotation || 0} onChange={(value) => onPatch({ rotation: value })} onBlur={onCommit} />
        <NumberField label="层级" value={element.zIndex || 0} onChange={(value) => onPatch({ zIndex: value })} onBlur={onCommit} />
      </div>

      {element.type === 'TEXT' && (
        <>
          <label className="field">
            文本
            <textarea className="textarea" value={String(element.content.text || '')} onChange={(event) => onContent('text', event.target.value)} onBlur={onCommit} />
          </label>
          <div className="property-grid">
            <NumberField label="字号" value={Number(element.style.fontSize || 28)} onChange={(value) => onStyle('fontSize', value)} onBlur={onCommit} />
            <label className="field">颜色<input className="input" type="color" value={String(element.style.color || '#111827')} onChange={(event) => onStyle('color', event.target.value)} onBlur={onCommit} /></label>
            <label className="field">粗体<select className="select" value={String(element.style.fontWeight || 500)} onChange={(event) => onStyle('fontWeight', event.target.value)} onBlur={onCommit}><option value="500">常规</option><option value="700">加粗</option></select></label>
            <label className="field">对齐<select className="select" value={String(element.style.textAlign || 'left')} onChange={(event) => onStyle('textAlign', event.target.value)} onBlur={onCommit}><option value="left">左</option><option value="center">中</option><option value="right">右</option></select></label>
          </div>
        </>
      )}

      {element.type === 'IMAGE' && (
        <>
          <label className="field">图片 URL<input className="input" value={String(element.content.publicUrl || element.content.url || '')} onChange={(event) => onContent('publicUrl', event.target.value)} onBlur={onCommit} /></label>
          <label className="field">上传图片<input className="input" type="file" accept="image/png,image/jpeg,image/webp" onChange={onImageUpload} /></label>
          <label className="field">裁切<select className="select" value={String(element.style.objectFit || 'cover')} onChange={(event) => onStyle('objectFit', event.target.value)} onBlur={onCommit}><option value="cover">填充裁切</option><option value="contain">完整显示</option><option value="fill">拉伸</option></select></label>
        </>
      )}

      {(element.type === 'SHAPE' || element.type === 'CHART_PLACEHOLDER') && (
        <div className="property-grid">
          <label className="field">填充<input className="input" type="color" value={String(element.style.fill || '#dbeafe')} onChange={(event) => onStyle('fill', event.target.value)} onBlur={onCommit} /></label>
          <label className="field">边框<input className="input" type="color" value={String(element.style.borderColor || '#2563eb')} onChange={(event) => onStyle('borderColor', event.target.value)} onBlur={onCommit} /></label>
          <NumberField label="边框宽" value={Number(element.style.borderWidth || 1)} onChange={(value) => onStyle('borderWidth', value)} onBlur={onCommit} />
          <NumberField label="圆角" value={Number(element.style.borderRadius || 8)} onChange={(value) => onStyle('borderRadius', value)} onBlur={onCommit} />
        </div>
      )}

      <div className="inline-actions">
        <button className="button" onClick={onCommit}><Save size={16} />保存元素</button>
        <button className="secondary-button" onClick={() => { onPatch({ zIndex: (element.zIndex || 0) + 1 }); setTimeout(onCommit); }}><BringToFront size={16} />上移</button>
        <button className="danger-button" onClick={onDelete}><Trash2 size={16} />删除</button>
      </div>
    </>
  );
}

function NumberField({ label, value, onChange, onBlur }: { label: string; value: number; onChange: (value: number) => void; onBlur: () => void }) {
  return (
    <label className="field">
      {label}
      <input className="input" type="number" value={Number.isFinite(value) ? value : 0} onChange={(event) => onChange(Number(event.target.value))} onBlur={onBlur} />
    </label>
  );
}

function replaceSlide(presentation: Presentation, slide: Slide): Presentation {
  return {
    ...presentation,
    slides: presentation.slides.map((item) => (item.id === slide.id ? slide : item))
  };
}
