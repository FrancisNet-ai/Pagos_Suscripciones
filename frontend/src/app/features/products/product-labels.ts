import { BadgeTone } from '../../shared/components/status-badge.component';
import { Product, ProductFilter, PublicationStatus } from './products.api';

export const STATUS_LABELS: Record<PublicationStatus, { label: string; tone: BadgeTone }> = {
  PUBLISHED: { label: 'Publicado', tone: 'success' },
  DRAFT: { label: 'Borrador', tone: 'neutral' },
  ARCHIVED: { label: 'Archivado', tone: 'warning' },
};

export const FILTERS: { value: ProductFilter; label: string }[] = [
  { value: 'ACTIVE', label: 'Activos' },
  { value: 'PUBLISHED', label: 'Publicados' },
  { value: 'DRAFT', label: 'Borradores' },
  { value: 'ARCHIVED', label: 'Archivados' },
];

export function typeLabel(product: Pick<Product, 'productType'>): string {
  return product.productType === 'SUBSCRIPTION' ? 'Suscripción' : 'Pago único';
}

/** Texto de disponibilidad. Sin control de cupos no se muestran cantidades. */
export function availabilityLabel(product: Product): { text: string; tone: BadgeTone } {
  if (!product.available) return { text: 'No disponible', tone: 'warning' };
  const a = product.availability;
  if (!a.capacityEnabled) return { text: 'Disponible', tone: 'success' };
  if (a.soldOut) return { text: 'Agotado', tone: 'danger' };
  return { text: `${a.available} de ${a.capacity} cupos`, tone: 'info' };
}
