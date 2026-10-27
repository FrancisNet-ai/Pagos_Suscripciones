import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, Subscription as RxSubscription } from 'rxjs';
import { Page } from '../../core/services/page';
import { ApiError } from '../../core/services/api-error';
import { SubscriptionItemComponent } from '../subscriptions/subscription-item.component';
import { Subscription, SubscriptionsApi, shortDate } from '../subscriptions/subscriptions.api';
import { NotificationItem, RenewalBucket, RenewalSummary, RenewalsApi, RenewalsOverview } from './renewals.api';

type Tab = 'NOTIFICATIONS' | RenewalBucket | 'RELEASE';

@Component({
  selector: 'app-renewals-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, SubscriptionItemComponent],
  templateUrl: './renewals.page.html',
})
export class RenewalsPage {
  /** Query param `vista`. */
  readonly vista = input<string>();

  private readonly api = inject(RenewalsApi);
  private readonly subscriptionsApi = inject(SubscriptionsApi);
  private readonly clipboard = inject(Clipboard);
  protected readonly date = shortDate;

  protected readonly tab = signal<Tab>('NOTIFICATIONS');
  protected readonly overview = signal<RenewalsOverview | null>(null);
  protected readonly notifications = signal<NotificationItem[] | null>(null);
  protected readonly renewals = signal<RenewalSummary[] | null>(null);
  protected readonly releases = signal<Subscription[] | null>(null);
  protected readonly opened = signal<Set<string>>(new Set());
  protected readonly copied = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalItems = signal(0);
  protected readonly loading = signal(false);
  protected readonly processing = signal<Set<string>>(new Set());
  private listRequest?: RxSubscription;

  protected readonly tabs = computed(() => {
    const o = this.overview();
    return [
      { value: 'NOTIFICATIONS' as Tab, label: 'Avisos por enviar', count: o?.notificationsDue },
      { value: 'UPCOMING' as Tab, label: 'Próximos vencimientos', count: o?.upcoming },
      { value: 'ACCEPTED' as Tab, label: 'Aceptaron', count: o?.accepted },
      { value: 'DECLINED' as Tab, label: 'Rechazaron', count: o?.declined },
      { value: 'NO_RESPONSE' as Tab, label: 'Sin respuesta', count: o?.noResponse },
      { value: 'COMPLETED' as Tab, label: 'Renovadas', count: o?.completed },
      { value: 'RELEASE' as Tab, label: 'Cupos por liberar', count: o?.capacityReleasePending },
    ];
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.listRequest?.unsubscribe());
    toObservable(this.vista).pipe(takeUntilDestroyed()).subscribe((v) => {
      const valid = ['NOTIFICATIONS', 'UPCOMING', 'ACCEPTED', 'DECLINED', 'NO_RESPONSE', 'COMPLETED', 'RELEASE'];
      this.select(v && valid.includes(v) ? (v as Tab) : 'NOTIFICATIONS');
    });
  }

  protected select(tab: Tab, page = 0): void {
    this.listRequest?.unsubscribe();
    this.tab.set(tab);
    this.page.set(page);
    this.error.set(null);
    this.loading.set(true);
    this.totalPages.set(0);
    this.loadOverview();
    if (tab === 'NOTIFICATIONS') {
      this.notifications.set(null);
      this.listRequest = this.api.notifications(page).subscribe({ next: (p) => { this.notifications.set(p.items); this.loaded(p); }, error: (e) => this.fail(e) });
    } else if (tab === 'RELEASE') {
      this.releases.set(null);
      this.listRequest = this.subscriptionsApi.list('RELEASE_PENDING', '', page).subscribe({ next: (p) => { this.releases.set(p.items); this.loaded(p); }, error: (e) => this.fail(e) });
    } else {
      this.renewals.set(null);
      this.listRequest = this.api.list(tab, page).subscribe({ next: (p) => { this.renewals.set(p.items); this.loaded(p); }, error: (e) => this.fail(e) });
    }
  }

  protected changePage(page: number): void {
    if (!this.loading() && page >= 0 && page < this.totalPages()) this.select(this.tab(), page);
  }

  private loaded(p: Page<unknown>): void {
    this.page.set(p.page);
    this.totalPages.set(p.totalPages);
    this.totalItems.set(p.totalItems);
    this.loading.set(false);
  }

  /** Abrir WhatsApp no envía el mensaje: después el vendedor confirma que lo envió. */
  protected open(n: NotificationItem): void {
    if (n.consent && n.whatsappUrl) this.opened.update((s) => new Set(s).add(n.id));
  }

  protected markSent(n: NotificationItem): void {
    if (!n.consent || !this.opened().has(n.id) || this.processing().has(n.id)) return;
    this.process(n, this.api.markSent(n.id));
  }

  protected skip(n: NotificationItem): void {
    if (!this.processing().has(n.id)) this.process(n, this.api.skip(n.id));
  }

  protected copyLink(r: RenewalSummary): void {
    if (this.clipboard.copy(r.renewalUrl)) {
      this.copied.set(r.id);
      setTimeout(() => this.copied.set(null), 2500);
    }
  }

  protected responseLabel(r: RenewalSummary): string {
    return { NONE: 'Sin respuesta', ACCEPTED: 'Quiere renovar', DECLINED: 'No renovará' }[r.response];
  }

  protected local(phone: string): string {
    return phone.replace(/^\+51/, '');
  }

  protected onReleased(): void {
    this.select('RELEASE', this.releases()?.length === 1 ? Math.max(0, this.page() - 1) : this.page());
  }

  private process(n: NotificationItem, request: Observable<unknown>): void {
    this.processing.update((ids) => new Set(ids).add(n.id));
    request.subscribe({
      next: () => {
        this.processing.update((ids) => { const next = new Set(ids); next.delete(n.id); return next; });
        if (this.tab() === 'NOTIFICATIONS') {
          const page = this.notifications()?.length === 1 ? Math.max(0, this.page() - 1) : this.page();
          this.select('NOTIFICATIONS', page);
        } else this.loadOverview();
      },
      error: (e: unknown) => {
        this.processing.update((ids) => { const next = new Set(ids); next.delete(n.id); return next; });
        this.fail(e);
      },
    });
  }

  private loadOverview(): void {
    this.api.overview().subscribe({ next: (o) => this.overview.set(o), error: (e: unknown) => this.error.set(ApiError.from(e).message) });
  }

  private fail(e: unknown): void {
    this.error.set(ApiError.from(e).message);
    this.loading.set(false);
  }
}
