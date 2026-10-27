import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
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
    toObservable(this.vista).pipe(takeUntilDestroyed()).subscribe((v) => {
      const valid = ['NOTIFICATIONS', 'UPCOMING', 'ACCEPTED', 'DECLINED', 'NO_RESPONSE', 'COMPLETED', 'RELEASE'];
      this.select(v && valid.includes(v) ? (v as Tab) : 'NOTIFICATIONS');
    });
  }

  protected select(tab: Tab): void {
    this.tab.set(tab);
    this.error.set(null);
    this.loadOverview();
    if (tab === 'NOTIFICATIONS') {
      this.notifications.set(null);
      this.api.notifications().subscribe({ next: (p) => this.notifications.set(p.items), error: (e) => this.fail(e) });
    } else if (tab === 'RELEASE') {
      this.releases.set(null);
      this.subscriptionsApi.list('RELEASE_PENDING', '', 0).subscribe({ next: (p) => this.releases.set(p.items), error: (e) => this.fail(e) });
    } else {
      this.renewals.set(null);
      this.api.list(tab, 0).subscribe({ next: (p) => this.renewals.set(p.items), error: (e) => this.fail(e) });
    }
  }

  /** Abrir WhatsApp no envía el mensaje: después el vendedor confirma que lo envió. */
  protected open(n: NotificationItem): void {
    this.opened.update((s) => new Set(s).add(n.id));
  }

  protected markSent(n: NotificationItem): void {
    this.api.markSent(n.id).subscribe({ next: () => this.removeNotification(n.id), error: (e) => this.fail(e) });
  }

  protected skip(n: NotificationItem): void {
    this.api.skip(n.id).subscribe({ next: () => this.removeNotification(n.id), error: (e) => this.fail(e) });
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
    this.select('RELEASE');
  }

  private removeNotification(id: string): void {
    this.notifications.update((list) => list?.filter((n) => n.id !== id) ?? null);
    this.loadOverview();
  }

  private loadOverview(): void {
    this.api.overview().subscribe((o) => this.overview.set(o));
  }

  private fail(e: unknown): void {
    this.error.set(ApiError.from(e).message);
  }
}
