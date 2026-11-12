import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TelegramConnectComponent } from '../../shared/components/telegram-connect.component';

/** Pestaña «Telegram» de Configuración: el titular conecta su chat para recibir los avisos de su tienda. */
@Component({
  selector: 'app-telegram-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TelegramConnectComponent],
  template: `
    <section class="grid gap-6">
      <div>
        <h2 class="font-[family-name:var(--font-editorial)] text-2xl font-semibold">Avisos al instante en tu celular</h2>
        <p class="mt-2 max-w-xl">
          Conecta tu Telegram y te avisaremos de cada pedido nuevo, cada renovación y cada pago que reporten tus clientes, sin tener que abrir Nuvyra.
          Es gratis.
        </p>
      </div>
      <app-telegram-connect class="block" description="Te llegan los pedidos nuevos, las renovaciones y los pagos por revisar de tu tienda." />
    </section>
  `,
})
export class TelegramSettingsPage {}
