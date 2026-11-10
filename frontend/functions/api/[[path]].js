// Cloudflare Pages Function: forwards every /api/* request to the backend on
// AWS Lightsail. The SPA keeps calling relative '/api/v1/...' URLs (exactly as
// it does behind the Vite dev proxy), so the browser only ever talks to its own
// origin: no CORS and no API URL baked into the build.
//
// Set BACKEND_URL in the Pages project (Settings → Variables and Secrets) to the
// container service's public URL, e.g.
//   https://enerscope.abc123xyz.us-east-1.cs.amazonlightsail.com
// Only its origin is used; any path on it is ignored.

// Host must match the backend, and Origin would make Spring treat this
// same-origin call as a cross-origin one and check it against CORS.
const STRIPPED_REQUEST_HEADERS = ['host', 'origin'];

function errorResponse(status, message) {
  // Same envelope as the backend's ApiResponse, so the client handles it alike.
  return Response.json(
    { success: false, message, data: null, timestamp: new Date().toISOString() },
    { status },
  );
}

export async function onRequest({ request, env }) {
  if (!env.BACKEND_URL) {
    return errorResponse(500, 'BACKEND_URL is not configured for this Pages project');
  }

  let backendOrigin;
  try {
    backendOrigin = new URL(env.BACKEND_URL).origin;
  } catch {
    return errorResponse(500, 'BACKEND_URL is not a valid URL');
  }

  const { pathname, search } = new URL(request.url);
  const headers = new Headers(request.headers);
  STRIPPED_REQUEST_HEADERS.forEach((name) => headers.delete(name));

  const init = { method: request.method, headers, redirect: 'manual' };
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    init.body = await request.arrayBuffer();
  }

  try {
    return await fetch(`${backendOrigin}${pathname}${search}`, init);
  } catch {
    return errorResponse(502, 'The backend is unreachable');
  }
}
