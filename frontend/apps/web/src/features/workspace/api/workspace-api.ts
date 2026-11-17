import type {
  CreateProjectRequest,
  CreateWorkspaceRequest,
  MessageResponse,
  Project,
  ProjectPage,
  UpdateProjectRequest,
  UpdateWorkspaceRequest,
  Workspace,
  WorkspacePage,
} from '@posts/api-client';

import { getApiClient, unwrapApi } from '@/shared/api-client';

export async function listWorkspaces(): Promise<WorkspacePage> {
  const client = getApiClient();
  return unwrapApi(await client.GET('/api/v1/workspaces'));
}

export async function createWorkspace(body: CreateWorkspaceRequest): Promise<Workspace> {
  const client = getApiClient();
  return unwrapApi(await client.POST('/api/v1/workspaces', { body }));
}

export async function getWorkspace(workspaceId: string): Promise<Workspace> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/workspaces/{workspaceId}', {
      params: { path: { workspaceId } },
    }),
  );
}

export async function updateWorkspace(
  workspaceId: string,
  body: UpdateWorkspaceRequest,
): Promise<Workspace> {
  const client = getApiClient();
  return unwrapApi(
    await client.PUT('/api/v1/workspaces/{workspaceId}', {
      params: { path: { workspaceId } },
      body,
    }),
  );
}

export async function deleteWorkspace(workspaceId: string): Promise<void> {
  const client = getApiClient();
  const result = await client.DELETE('/api/v1/workspaces/{workspaceId}', {
    params: { path: { workspaceId } },
  });
  if (!result.response.ok) {
    unwrapApi(result);
  }
}

export async function listProjects(workspaceId: string): Promise<ProjectPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/workspaces/{workspaceId}/projects', {
      params: { path: { workspaceId } },
    }),
  );
}

export async function createProject(
  workspaceId: string,
  body: CreateProjectRequest,
): Promise<Project> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/workspaces/{workspaceId}/projects', {
      params: { path: { workspaceId } },
      body,
    }),
  );
}

export async function updateProject(
  projectId: string,
  body: UpdateProjectRequest,
): Promise<Project> {
  const client = getApiClient();
  return unwrapApi(
    await client.PUT('/api/v1/projects/{projectId}', {
      params: { path: { projectId } },
      body,
    }),
  );
}

export async function deleteProject(projectId: string): Promise<void | MessageResponse> {
  const client = getApiClient();
  const result = await client.DELETE('/api/v1/projects/{projectId}', {
    params: { path: { projectId } },
  });
  if (!result.response.ok) {
    unwrapApi(result);
  }
}
