import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { BRAND } from '../../core/brand';
import { BrandMarkComponent } from '../../shared/components/brand-mark.component';

interface LegalDoc {
  title: string;
  points: string[];
}

const DOCS: Record<string, LegalDoc> = {
  condiciones: {
    title: 'Condiciones comerciales',
    points: [
      `${BRAND.name} es un software que los vendedores contratan mediante una suscripción periódica para administrar su negocio.`,
      'Los compradores pagan directamente al vendedor por Yape, Plin u otros medios que el vendedor configure. Nuvyra no recibe, retiene ni intermedia esos pagos.',
      'Cada vendedor es responsable de comercializar únicamente productos y servicios digitales autorizados y de cumplir las condiciones de los proveedores de esos servicios.',
      'La falta de pago de la suscripción puede limitar las funciones comerciales, sin eliminar automáticamente los datos del vendedor.',
      'Nuvyra puede revisar y suspender cuentas que incumplan las políticas de uso.',
    ],
  },
  privacidad: {
    title: 'Política de privacidad',
    points: [
      'Para crear la cuenta de un vendedor tratamos su nombre, correo, celular y los datos de su negocio, con la finalidad de prestar el servicio contratado.',
      'A los compradores solo se les solicita nombre y celular para registrar su pedido con el vendedor elegido.',
      'Los datos de los compradores pertenecen al negocio con el que compraron y no son visibles para otros vendedores.',
      'Las comunicaciones comerciales requieren autorización previa y pueden rechazarse en cualquier momento.',
      'Puedes ejercer tus derechos de acceso, rectificación, cancelación y oposición conforme a la Ley N.º 29733 de Protección de Datos Personales.',
    ],
  },
};

@Component({
  selector: 'app-legal-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BrandMarkComponent],
  template: `
    <header class="border-b border-[var(--line-subtle)]">
      <div class="mx-auto max-w-3xl px-5 py-5 sm:px-8"><app-brand-mark /></div>
    </header>
    <main class="mx-auto max-w-3xl px-5 py-14 sm:px-8">
      @if (doc(); as d) {
        <p class="type-eyebrow">Documento legal</p>
        <h1 class="type-h1 mt-3">{{ d.title }}</h1>
        <p class="notice notice-info mt-8" role="note">Versión preliminar en revisión legal. El texto definitivo se publicará antes del lanzamiento comercial.</p>
        <ol class="mt-10 list-decimal space-y-5 pl-5 marker:font-semibold marker:text-steel">
          @for (point of d.points; track point) { <li class="pl-2">{{ point }}</li> }
        </ol>
      } @else {
        <h1 class="type-h1">Documento no encontrado</h1>
      }
    </main>
  `,
})
export class LegalPage {
  readonly documento = input<string>();
  protected readonly doc = computed(() => DOCS[this.documento() ?? ''] ?? null);
}
