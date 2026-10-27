import { availabilityLabel } from './product-labels';
import { Product } from './products.api';

function product(overrides: Omit<Partial<Product>, 'availability'> & { availability?: Partial<Product['availability']> }): Product {
  return {
    id: '1', name: 'P', description: null, category: null, productType: 'SUBSCRIPTION', price: 15, currency: 'PEN',
    durationDays: 30, imageUrl: null, publicationStatus: 'PUBLISHED', available: true, createdAt: '', updatedAt: '',
    ...overrides,
    availability: { capacityEnabled: false, capacity: null, occupied: 0, reserved: 0, available: null, soldOut: false, ...overrides.availability },
  };
}

describe('availabilityLabel', () => {
  it('sin control de cupos no muestra cantidades', () => {
    expect(availabilityLabel(product({})).text).toBe('Disponible');
  });

  it('muestra cupos disponibles y agotado', () => {
    expect(availabilityLabel(product({ availability: { capacityEnabled: true, capacity: 5, available: 2 } })).text).toBe('2 de 5 cupos');
    expect(availabilityLabel(product({ availability: { capacityEnabled: true, capacity: 5, available: 0, soldOut: true } })).text).toBe('Agotado');
  });

  it('un producto pausado aparece como no disponible', () => {
    expect(availabilityLabel(product({ available: false })).text).toBe('No disponible');
  });
});
