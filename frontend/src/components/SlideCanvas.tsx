import { CSSProperties, PointerEvent, useState } from 'react';
import type { Presentation, Slide, SlideElement } from '../types';

type Props = {
  presentation: Presentation;
  slide: Slide | null;
  selectedElementId?: number | null;
  onSelectElement?: (element: SlideElement) => void;
  onChangeElement?: (element: SlideElement) => void;
  onCommitElement?: (element: SlideElement) => void;
  onCursor?: (x: number, y: number, elementId?: number) => void;
};

type DragState = {
  mode: 'move' | 'resize';
  pointerId: number;
  startX: number;
  startY: number;
  element: SlideElement;
};

export default function SlideCanvas({
  presentation,
  slide,
  selectedElementId,
  onSelectElement,
  onChangeElement,
  onCommitElement,
  onCursor
}: Props) {
  const [drag, setDrag] = useState<DragState | null>(null);
  const [editingTextElementId, setEditingTextElementId] = useState<number | null>(null);

  if (!slide) {
    return <div className="muted">暂无幻灯片</div>;
  }

  const scale = Math.min(1, 980 / presentation.pageWidth, 620 / presentation.pageHeight);
  const background = slide.background || {};

  function startDrag(event: PointerEvent, element: SlideElement, mode: 'move' | 'resize') {
    if (editingTextElementId === element.id) {
      return;
    }
    event.stopPropagation();
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    onSelectElement?.(element);
    setDrag({ mode, pointerId: event.pointerId, startX: event.clientX, startY: event.clientY, element });
  }

  function moveDrag(event: PointerEvent) {
    if (!drag || event.pointerId !== drag.pointerId) {
      return;
    }
    const dx = (event.clientX - drag.startX) / scale;
    const dy = (event.clientY - drag.startY) / scale;
    const next = drag.mode === 'move'
      ? { ...drag.element, x: Math.max(0, drag.element.x + dx), y: Math.max(0, drag.element.y + dy) }
      : { ...drag.element, width: Math.max(20, drag.element.width + dx), height: Math.max(20, drag.element.height + dy) };
    onChangeElement?.(next);
    onCursor?.(next.x, next.y, next.id);
  }

  function endDrag(event: PointerEvent) {
    if (!drag || event.pointerId !== drag.pointerId) {
      return;
    }
    const dx = (event.clientX - drag.startX) / scale;
    const dy = (event.clientY - drag.startY) / scale;
    const next = drag.mode === 'move'
      ? { ...drag.element, x: Math.max(0, drag.element.x + dx), y: Math.max(0, drag.element.y + dy) }
      : { ...drag.element, width: Math.max(20, drag.element.width + dx), height: Math.max(20, drag.element.height + dy) };
    setDrag(null);
    onCommitElement?.(next);
  }

  return (
    <div
      className="slide-canvas"
      onPointerMove={(event) => {
        const rect = event.currentTarget.getBoundingClientRect();
        onCursor?.((event.clientX - rect.left) / scale, (event.clientY - rect.top) / scale);
        moveDrag(event);
      }}
      onPointerUp={endDrag}
      style={{
        width: presentation.pageWidth * scale,
        height: presentation.pageHeight * scale,
        background: cssValue(background, ['color', 'backgroundColor']) || '#ffffff'
      }}
    >
      {[...slide.elements]
        .sort((a, b) => a.zIndex - b.zIndex || a.pageOrder - b.pageOrder)
        .map((element) => (
          <div
            key={element.id}
            className={`slide-element ${element.type === 'TEXT' ? 'text-frame' : ''} ${selectedElementId === element.id ? 'selected' : ''} ${editingTextElementId === element.id ? 'editing' : ''}`}
            onPointerDown={(event) => startDrag(event, element, 'move')}
            onDoubleClick={(event) => {
              if (element.type !== 'TEXT') {
                return;
              }
              event.stopPropagation();
              onSelectElement?.(element);
              setEditingTextElementId(element.id);
            }}
            style={{
              left: element.x * scale,
              top: element.y * scale,
              width: element.width * scale,
              height: element.height * scale,
              zIndex: element.zIndex,
              transform: `rotate(${element.rotation || 0}deg)`,
              transformOrigin: 'center center'
            }}
          >
            <RenderedElement
              element={element}
              scale={scale}
              isEditing={editingTextElementId === element.id}
              onCommitText={(text) => {
                const next = { ...element, content: { ...element.content, text } };
                onChangeElement?.(next);
                onCommitElement?.(next);
                setEditingTextElementId(null);
              }}
            />
            {selectedElementId === element.id && (
              <button
                className="resize-handle"
                onPointerDown={(event) => startDrag(event, element, 'resize')}
                aria-label="缩放元素"
              />
            )}
          </div>
        ))}
    </div>
  );
}

function RenderedElement({
  element,
  scale,
  isEditing,
  onCommitText
}: {
  element: SlideElement;
  scale: number;
  isEditing: boolean;
  onCommitText: (text: string) => void;
}) {
  if (element.type === 'TEXT') {
    const style = element.style || {};
    return (
      <div
        className={`text-box ${isEditing ? 'editing' : ''}`}
        contentEditable={isEditing}
        suppressContentEditableWarning
        onPointerDown={(event) => {
          if (isEditing) {
            event.stopPropagation();
          }
        }}
        onBlur={(event) => onCommitText(event.currentTarget.innerText)}
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          alignItems: cssValue(style, ['alignItems']) || 'flex-start',
          justifyContent: cssValue(style, ['justifyContent']) || 'flex-start',
          color: cssValue(style, ['color']) || '#111827',
          background: cssValue(style, ['backgroundColor', 'fill']) || 'transparent',
          fontSize: numberValue(style.fontSize, 28) * scale,
          fontWeight: String(style.fontWeight || 500),
          fontFamily: cssValue(style, ['fontFamily']) || 'Arial, sans-serif',
          lineHeight: Number(style.lineHeight || 1.18),
          textAlign: (cssValue(style, ['textAlign']) || 'left') as CSSProperties['textAlign'],
          padding: numberValue(style.padding, 0) * scale,
          whiteSpace: 'pre-wrap',
          overflow: 'hidden'
        }}
      >
        {textValue(element.content, ['text', 'title', 'body']) || 'Text'}
      </div>
    );
  }

  if (element.type === 'IMAGE') {
    const url = textValue(element.content, ['publicUrl', 'url', 'src']);
    const fit = cssValue(element.style || {}, ['objectFit']) || 'cover';
    if (!url) {
      return <div className="image-placeholder">Image</div>;
    }
    return (
      <img
        src={url}
        alt={textValue(element.content, ['alt', 'altText']) || 'PPT image'}
        draggable={false}
        style={{ width: '100%', height: '100%', objectFit: fit as CSSProperties['objectFit'] }}
      />
    );
  }

  if (element.type === 'CHART_PLACEHOLDER') {
    return <div className="chart-placeholder">{textValue(element.content, ['text', 'title']) || 'Chart'}</div>;
  }

  const style = element.style || {};
  return (
    <div
      className="shape-placeholder"
      style={{
        background: cssValue(style, ['fill', 'backgroundColor']) || '#dbeafe',
        color: cssValue(style, ['color']) || '#172033',
        border: `${numberValue(style.borderWidth, 1)}px solid ${cssValue(style, ['borderColor']) || '#2563eb'}`,
        borderRadius: numberValue(style.borderRadius, 8) * scale
      }}
    >
      {textValue(element.content, ['text', 'label'])}
    </div>
  );
}

function textValue(source: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = source?.[key];
    if (typeof value === 'string' && value.trim()) {
      return value;
    }
  }
  return '';
}

function cssValue(source: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = source?.[key];
    if (typeof value === 'string' && value.trim()) {
      return value;
    }
  }
  return '';
}

function numberValue(value: unknown, fallback: number) {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}
