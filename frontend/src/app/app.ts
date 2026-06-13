import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-root',
  imports: [],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private readonly http = inject(HttpClient);

  protected readonly products = signal<unknown[]>([]);
  protected readonly loading = signal(true);
  protected readonly authenticated = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.loadProducts();
  }

  protected loadProducts(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<unknown[]>('/api/v1/products').subscribe({
      next: (data) => {
        this.products.set(data);
        this.authenticated.set(true);
        this.loading.set(false);
      },
      error: (err) => {
        // Oturum yoksa BFF 302 ile Keycloak'a yönlendirir; tarayıcı fetch'i bunu
        // takip edip CORS/parse hatasıyla buraya düşer → login butonu gösterilir.
        this.authenticated.set(false);
        this.loading.set(false);
        if (err.status !== 0) {
          this.error.set(`İstek başarısız: ${err.status} ${err.statusText ?? ''}`);
        }
      }
    });
  }

  protected login(): void {
    window.location.href = '/oauth2/authorization/keycloak';
  }

  protected logout(): void {
    this.http.post('/logout', null, { observe: 'response' }).subscribe({
      next: (res) => {
        // BFF 302 yerine 200 + Location döner (SecurityConfig); yönlendirmeyi biz yapıyoruz.
        window.location.href = res.headers.get('Location') ?? '/';
      },
      error: () => window.location.href = '/'
    });
  }

  protected stringify(value: unknown): string {
    return JSON.stringify(value, null, 2);
  }
}
