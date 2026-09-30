import { Pipe, PipeTransform } from '@angular/core';

/** 'FULL_TIME' -> 'Full time', 'ID_PROOF' -> 'Id proof'. */
export function humanize(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  const text = value.replace(/_/g, ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

@Pipe({ name: 'humanize', standalone: true })
export class HumanizePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return humanize(value);
  }
}

/** 'Emma Lopez' -> 'EL'. */
@Pipe({ name: 'initials', standalone: true })
export class InitialsPipe implements PipeTransform {
  transform(name: string | null | undefined): string {
    if (!name) {
      return '?';
    }
    return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('');
  }
}

/** 1536 -> '1.5 KB'. */
@Pipe({ name: 'fileSize', standalone: true })
export class FileSizePipe implements PipeTransform {
  transform(bytes: number | null | undefined): string {
    if (bytes == null) {
      return '';
    }
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }
}

/** 3000000 -> '₹30,00,000' (Indian digit grouping via Intl; no Angular locale data needed). */
@Pipe({ name: 'inr', standalone: true })
export class InrPipe implements PipeTransform {
  transform(amount: number | null | undefined): string {
    if (amount == null) {
      return '—';
    }
    return '₹' + Math.round(amount).toLocaleString('en-IN');
  }
}
