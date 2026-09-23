import type {
  AgentJob,
  ApiResponse,
  AuthResponse,
  FileAsset,
  Presentation,
  PresentationSummary,
  Slide,
  SlideElement,
  SlideElementType,
  UserProfile
} from '../types';
import { clearAuth, getAccessToken, getRefreshToken, saveAuth } from './storage';

export const API_BASE = import.meta.env.VITE_API_BASE || '/api';

type RequestOptions = RequestInit & {
  skipAuth?: boolean;
  retry?: boolean;
};

export class ApiError extends Error {
  status: number;
  code?: string;

  constructor(message: string, status: number, code?: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

async function unwrap<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T;
  }
  const body = (await response.json()) as ApiResponse<T>;
  if (!response.ok || body.success === false) {
    throw new ApiError(body.message || response.statusText, response.status, body.code);
  }
  return body.data;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (!(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  const token = getAccessToken();
  if (!options.skipAuth && token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers
  });

  if (response.status === 401 && !options.skipAuth && options.retry !== false && getRefreshToken()) {
    try {
      await refresh();
      return apiRequest<T>(path, { ...options, retry: false });
    } catch {
      clearAuth();
      throw new ApiError('登录已过期', 401);
    }
  }

  return unwrap<T>(response);
}

export async function login(email: string, password: string) {
  const auth = await apiRequest<AuthResponse>('/auth/login', {
    method: 'POST',
    skipAuth: true,
    body: JSON.stringify({ email, password })
  });
  saveAuth(auth);
  return auth;
}

export async function register(email: string, username: string, password: string) {
  const auth = await apiRequest<AuthResponse>('/auth/register', {
    method: 'POST',
    skipAuth: true,
    body: JSON.stringify({ email, username, password })
  });
  saveAuth(auth);
  return auth;
}

export async function refresh() {
  const refreshToken = getRefreshToken();
  const auth = await apiRequest<AuthResponse>('/auth/refresh', {
    method: 'POST',
    skipAuth: true,
    body: JSON.stringify({ refreshToken })
  });
  saveAuth(auth);
  return auth;
}

export function me() {
  return apiRequest<UserProfile>('/auth/me');
}

export function listPresentations() {
  return apiRequest<PresentationSummary[]>('/presentations');
}

export function createPresentation(title: string) {
  return apiRequest<Presentation>('/presentations', {
    method: 'POST',
    body: JSON.stringify({ title, pageWidth: 1280, pageHeight: 720, theme: {} })
  });
}

export function getPresentation(id: number) {
  return apiRequest<Presentation>(`/presentations/${id}`);
}

export function updatePresentation(presentation: Presentation) {
  return apiRequest<Presentation>(`/presentations/${presentation.id}`, {
    method: 'PATCH',
    body: JSON.stringify({
      title: presentation.title,
      pageWidth: presentation.pageWidth,
      pageHeight: presentation.pageHeight,
      theme: presentation.theme || {}
    })
  });
}

export function createSlide(presentationId: number, pageOrder: number, title = '新页面') {
  return apiRequest<Presentation>(`/presentations/${presentationId}/slides`, {
    method: 'POST',
    body: JSON.stringify({ pageOrder, title, background: {}, speakerNotes: '' })
  });
}

export function updateSlide(slide: Slide) {
  return apiRequest<Presentation>(`/slides/${slide.id}`, {
    method: 'PATCH',
    body: JSON.stringify({
      pageOrder: slide.pageOrder,
      title: slide.title,
      background: slide.background || {},
      speakerNotes: slide.speakerNotes || ''
    })
  });
}

export function createElement(slideId: number, type: SlideElementType, pageOrder: number) {
  return apiRequest<SlideElement>(`/slides/${slideId}/elements`, {
    method: 'POST',
    body: JSON.stringify(defaultElementPayload(type, pageOrder))
  });
}

export function updateElement(element: SlideElement) {
  return apiRequest<SlideElement>(`/elements/${element.id}`, {
    method: 'PATCH',
    body: JSON.stringify({
      type: element.type,
      x: element.x,
      y: element.y,
      width: element.width,
      height: element.height,
      rotation: element.rotation,
      zIndex: element.zIndex,
      pageOrder: element.pageOrder,
      initiallyPresent: element.initiallyPresent,
      content: element.content || {},
      style: element.style || {},
      expectedRevision: element.revision
    })
  });
}

export function deleteElement(element: SlideElement) {
  return apiRequest<void>(`/elements/${element.id}`, {
    method: 'DELETE',
    body: JSON.stringify({ expectedRevision: element.revision })
  });
}

export function deleteSlide(slideId: number) {
  return apiRequest<Presentation>(`/slides/${slideId}`, { method: 'DELETE' });
}

export function uploadFile(fileType: string, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return apiRequest<FileAsset>(`/files/upload?fileType=${encodeURIComponent(fileType)}`, {
    method: 'POST',
    body: formData
  });
}

export function createAgentJob(presentationId: number, prompt: string, fileAssetIds: number[] = []) {
  return apiRequest<AgentJob>('/agent/jobs', {
    method: 'POST',
    body: JSON.stringify({ presentationId, prompt, fileAssetIds, imageAssetIds: [] })
  });
}

export function planAgentJob(jobId: number) {
  return apiRequest<AgentJob>(`/agent/jobs/${jobId}/plan`, { method: 'POST' });
}

export function approveAgentPlan(jobId: number) {
  return apiRequest<AgentJob>(`/agent/jobs/${jobId}/approve-plan`, { method: 'POST' });
}

export function executeAgentJob(jobId: number) {
  return apiRequest<AgentJob>(`/agent/jobs/${jobId}/execute`, { method: 'POST' });
}

export function publishAgentJob(jobId: number) {
  return apiRequest<unknown>(`/agent/jobs/${jobId}/publish`, { method: 'POST' });
}

export function sseUrl(path: string) {
  return `${API_BASE}${path}`;
}

function defaultElementPayload(type: SlideElementType, pageOrder: number) {
  if (type === 'TEXT') {
    return {
      type,
      x: 96,
      y: 96,
      width: 520,
      height: 120,
      rotation: 0,
      zIndex: pageOrder,
      pageOrder,
      initiallyPresent: true,
      content: { text: '双击右侧 JSON 修改文本' },
      style: { fontSize: 36, color: '#111827', fontWeight: 700 }
    };
  }
  if (type === 'IMAGE') {
    return {
      type,
      x: 680,
      y: 120,
      width: 420,
      height: 260,
      rotation: 0,
      zIndex: pageOrder,
      pageOrder,
      initiallyPresent: true,
      content: { publicUrl: '', alt: 'Image' },
      style: { objectFit: 'cover', backgroundColor: '#e5e7eb' }
    };
  }
  return {
    type,
    x: 120,
    y: 260,
    width: 360,
    height: 160,
    rotation: 0,
    zIndex: pageOrder,
    pageOrder,
    initiallyPresent: true,
    content: { text: type === 'CHART_PLACEHOLDER' ? 'Chart' : '' },
    style: { fill: '#dbeafe', borderColor: '#2563eb', borderWidth: 2, borderRadius: 8 }
  };
}
