export type ApiResponse<T> = {
  success: boolean;
  code: string;
  message: string;
  data: T;
  timestamp: string;
};

export type UserProfile = {
  id: number;
  email: string;
  username: string;
  status: string;
  levelCode?: string;
  roles?: string[];
  permissions?: string[];
};

export type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
  user: UserProfile;
};

export type PresentationSummary = {
  id: number;
  title: string;
  pageWidth: number;
  pageHeight: number;
  status: string;
  currentVersionNumber: number;
  createdAt: string;
  updatedAt: string;
  slideCount: number;
  canEdit: boolean;
};

export type SlideElementType = 'TEXT' | 'IMAGE' | 'SHAPE' | 'CHART_PLACEHOLDER';

export type SlideElement = {
  id: number;
  elementKey: string;
  type: SlideElementType;
  x: number;
  y: number;
  width: number;
  height: number;
  rotation: number;
  zIndex: number;
  pageOrder: number;
  initiallyPresent: boolean;
  content: Record<string, unknown>;
  style: Record<string, unknown>;
  revision: number;
  locked: boolean;
};

export type Slide = {
  id: number;
  pageOrder: number;
  title: string;
  background: Record<string, unknown>;
  speakerNotes?: string;
  elements: SlideElement[];
};

export type Presentation = {
  id: number;
  ownerUserId: number;
  title: string;
  pageWidth: number;
  pageHeight: number;
  theme: Record<string, unknown>;
  status: string;
  currentVersionNumber: number;
  slides: Slide[];
};

export type Checkpoint = {
  id: number;
  order: number;
  title: string;
  objective: string;
  status: string;
  maxIterations: number;
  currentIterations: number;
  failureReason?: string | null;
};

export type AgentJob = {
  id: number;
  presentationId: number;
  status: string;
  prompt: string;
  failureReason?: string | null;
  planMarkdown?: string | null;
  translatedPlanMarkdown?: string | null;
  checkpoints: Checkpoint[];
};

export type FileAsset = {
  id: number;
  fileType: string;
  originalFilename: string;
  contentType: string;
  byteSize: number;
  objectKey: string;
  publicUrl: string;
  status: string;
  uploadMode: string;
  failureReason?: string | null;
};

export type ProgressMessage = {
  messageId: number;
  checkpointId?: number | null;
  roleName: string;
  contentText: string;
  metadataJson?: string;
  createdAt?: string;
};

export type ProgressToolCall = {
  toolCallId: number;
  checkpointId?: number | null;
  toolName: string;
  requestJson?: string;
  responseJson?: string;
  status?: string;
  durationMs?: number;
  errorMessage?: string | null;
  createdAt?: string;
  response?: unknown;
};

export type ProgressArtifact = {
  artifactRefId?: number;
  checkpointId?: number | null;
  artifactType: string;
  artifactId: number;
  actionName: string;
  active?: boolean;
  metadataJson?: string;
  createdAt?: string;
};

export type ProgressCheckpoint = Checkpoint & {
  messages: ProgressMessage[];
  toolCalls: ProgressToolCall[];
  artifacts: ProgressArtifact[];
};

export type ProgressSnapshot = {
  type: 'snapshot';
  timestamp: string;
  job: Omit<AgentJob, 'checkpoints'>;
  messages: ProgressMessage[];
  checkpoints: ProgressCheckpoint[];
};

export type ProgressEvent = {
  type: string;
  jobId: number;
  presentationId: number;
  checkpointId?: number | null;
  timestamp: string;
  data: Record<string, unknown>;
};
