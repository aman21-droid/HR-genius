import { HttpParams, HttpResponse } from '@angular/common/http';

/** Builds HttpParams, skipping null/undefined/empty values so the backend sees only real filters. */
export function toParams(values: object): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value === null || value === undefined || value === '') {
      continue;
    }
    params = params.set(key, String(value));
  }
  return params;
}

/** Extracts the filename from Content-Disposition (RFC 5987 filename* preferred). */
export function filenameFrom(response: HttpResponse<Blob>, fallback: string): string {
  const header = response.headers.get('Content-Disposition') ?? '';
  const star = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (star) {
    return decodeURIComponent(star[1].trim());
  }
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain ? plain[1].trim() : fallback;
}

/** Triggers a browser download for a blob response. */
export function saveBlob(response: HttpResponse<Blob>, fallback: string): void {
  if (!response.body) {
    return;
  }
  const url = URL.createObjectURL(response.body);
  const a = document.createElement('a');
  a.href = url;
  a.download = filenameFrom(response, fallback);
  document.body.appendChild(a);
  a.click();
  a.remove();
  // Revoke after the click has been processed.
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** yyyy-MM-dd for a Date, in local time (what date pickers produce). */
export function toIsoDate(value: Date | string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  if (typeof value === 'string') {
    return value.substring(0, 10);
  }
  const y = value.getFullYear();
  const m = String(value.getMonth() + 1).padStart(2, '0');
  const d = String(value.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** Parses yyyy-MM-dd as a local date (avoids the UTC shift of `new Date('2026-01-05')`). */
export function fromIsoDate(value: string | null | undefined): Date | null {
  if (!value) {
    return null;
  }
  const [y, m, d] = value.substring(0, 10).split('-').map(Number);
  return new Date(y, m - 1, d);
}
