import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'wwt-pagination',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './pagination.component.html',
  styleUrl: './pagination.component.css'
})
export class PaginationComponent {
  @Input() set currentPage(v: number) {
    this._page.set(Math.max(1, v));
  }
  @Input() set totalPages(v: number) {
    this._total.set(Math.max(1, v));
  }
  @Input() totalItems?: number;
  @Input() pageSize = 10;
  @Output() pageChange = new EventEmitter<number>();

  private _page = signal(1);
  private _total = signal(1);

  page = computed(() => this._page());
  total = computed(() => this._total());

  pages = computed(() => {
    const cur = this._page();
    const tot = this._total();
    const result: number[] = [];
    const delta = 2;
    for (let i = Math.max(1, cur - delta); i <= Math.min(tot, cur + delta); i++) {
      result.push(i);
    }
    return result;
  });

  goTo(p: number): void {
    if (p >= 1 && p <= this._total() && p !== this._page()) {
      this.pageChange.emit(p);
    }
  }

  prev(): void {
    this.goTo(this._page() - 1);
  }

  next(): void {
    this.goTo(this._page() + 1);
  }
}
