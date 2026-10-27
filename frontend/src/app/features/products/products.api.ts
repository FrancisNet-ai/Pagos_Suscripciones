import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../../core/services/page';
import { toFormData } from '../settings/settings.api';

export type ProductType = 'SUBSCRIPTION' | 'ONE_TIME';
export type PublicationStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type ProductFilter = 'ACTIVE' | 'PUBLISHED' | 'DRAFT' | 'ARCHIVED' | 'ALL';

export interface Availability {
  capacityEnabled: boolean;
  capacity: number | null;
  occupied: number;
  reserved: number;
  available: number | null;
  soldOut: boolean;
}

export interface Product {
  id: string;
  name: string;
  description: string | null;
  category: string | null;
  productType: ProductType;
  price: number;
  currency: 'PEN';
  durationDays: number | null;
  imageUrl: string | null;
  publicationStatus: PublicationStatus;
  available: boolean;
  availability: Availability;
  createdAt: string;
  updatedAt: string;
}

export interface ProductPayload {
  name: string;
  description: string | null;
  category: string | null;
  productType: ProductType;
  price: number;
  durationDays: number | null;
  capacityEnabled: boolean;
  capacity: number | null;
  available: boolean;
}

const API = '/api/v1/products';

@Injectable({ providedIn: 'root' })
export class ProductsApi {
  private readonly http = inject(HttpClient);

  list(params: { query?: string; filter?: ProductFilter; page?: number; size?: number }): Observable<Page<Product>> {
    const query: Record<string, string> = {};
    if (params.query) query['query'] = params.query;
    if (params.filter) query['filter'] = params.filter;
    if (params.page !== undefined) query['page'] = String(params.page);
    if (params.size !== undefined) query['size'] = String(params.size);
    return this.http.get<Page<Product>>(API, { params: query });
  }

  get(id: string): Observable<Product> {
    return this.http.get<Product>(`${API}/${id}`);
  }

  create(payload: ProductPayload): Observable<Product> {
    return this.http.post<Product>(API, payload);
  }

  update(id: string, payload: ProductPayload): Observable<Product> {
    return this.http.put<Product>(`${API}/${id}`, payload);
  }

  changeStatus(id: string, publicationStatus: PublicationStatus): Observable<Product> {
    return this.http.patch<Product>(`${API}/${id}/status`, { publicationStatus });
  }

  uploadImage(id: string, file: File): Observable<Product> {
    return this.http.post<Product>(`${API}/${id}/image`, toFormData(file));
  }

  deleteImage(id: string): Observable<Product> {
    return this.http.delete<Product>(`${API}/${id}/image`);
  }
}
