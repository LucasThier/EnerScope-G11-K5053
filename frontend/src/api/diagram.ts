import type { AxiosResponse } from 'axios';
import { client } from './client';
import type { ApiResponse } from '../types/auth';
import type { Diagram, DiagramNode, NodeGraphData, NodeState } from '../types/diagram';

/**
 * Calls against the version-scoped node/connection API (`/version/**`). The
 * diagram of a version is the working set the editor renders.
 */
export const diagramApi = {
  get: (versionId: string): Promise<AxiosResponse<ApiResponse<Diagram>>> =>
    client.get(`/version/${versionId}/diagram`),

  /** Create a node. The payload's type-specific fields let the backend deduce the node subtype. */
  addNode: (
    versionId: string,
    node: Record<string, unknown>,
  ): Promise<AxiosResponse<ApiResponse<DiagramNode>>> =>
    client.post(`/version/${versionId}/node`, node),

  /** Presentation-only move (diagram x/y and/or geographical lng/lat). */
  updatePosition: (
    versionId: string,
    nodeId: string,
    position: NodeGraphData,
  ): Promise<AxiosResponse<ApiResponse<unknown>>> =>
    client.patch(`/version/${versionId}/node/${nodeId}/position`, position),

  /** Update a node's name/state without resending type-specific fields. */
  updateBasics: (
    versionId: string,
    nodeId: string,
    basics: { name?: string; state?: NodeState },
  ): Promise<AxiosResponse<ApiResponse<unknown>>> =>
    client.patch(`/version/${versionId}/node/${nodeId}/basics`, basics),

  /** Structural edit of a node (full node payload). */
  editNode: (
    versionId: string,
    nodeId: string,
    node: Record<string, unknown>,
  ): Promise<AxiosResponse<ApiResponse<DiagramNode>>> =>
    client.patch(`/version/${versionId}/node/${nodeId}`, node),

  deleteNode: (versionId: string, nodeId: string): Promise<AxiosResponse<ApiResponse<void>>> =>
    client.delete(`/version/${versionId}/node/${nodeId}`),

  addConnection: (
    versionId: string,
    data: { fromNodeId: string; toNodeId: string },
  ): Promise<AxiosResponse<ApiResponse<unknown>>> =>
    client.post(`/version/${versionId}/connection`, data),

  deleteConnection: (
    versionId: string,
    connectionId: string,
  ): Promise<AxiosResponse<ApiResponse<void>>> =>
    client.delete(`/version/${versionId}/connection/${connectionId}`),
};
