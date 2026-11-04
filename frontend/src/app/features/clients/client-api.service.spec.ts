import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import { ClientApiService } from './client-api.service';
import { Client, PageResponse } from './client.models';

describe('ClientApiService', () => {
  let service: ClientApiService;
  let httpMock: HttpTestingController;

  const mockConfig = { apiBaseUrl: 'http://localhost:8080' };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIG, useValue: mockConfig }
      ]
    });
    service = TestBed.inject(ClientApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getClients sends default pagination and sort params', () => {
    const mockPage: PageResponse<Client> = {
      content: [
        { id: 'c-1', firstName: 'Ahmed', lastName: 'Alami', email: 'ahmed@test.ma', phone: '+212600000000', enabled: true }
      ],
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true
    };

    service.getClients().subscribe((res) => {
      expect(res.content.length).toBe(1);
      expect(res.content[0].firstName).toBe('Ahmed');
    });

    const req = httpMock.expectOne((r) => r.url === 'http://localhost:8080/api/v1/clients');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('sort')).toBe('lastName,asc');
    expect(req.request.params.has('q')).toBeFalse();
    req.flush(mockPage);
  });

  it('getClients includes search query param q when provided', () => {
    service.getClients(1, 10, 'Bennani').subscribe();

    const req = httpMock.expectOne((r) => r.url === 'http://localhost:8080/api/v1/clients');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('q')).toBe('Bennani');
    req.flush({ content: [], number: 1, size: 10, totalElements: 0, totalPages: 0, first: false, last: true });
  });

  it('getClient sends GET request to /api/v1/clients/{id}', () => {
    const mockClient: Client = {
      id: 'client-uuid-123',
      firstName: 'Fatima',
      lastName: 'Bennani',
      email: 'fatima@test.ma',
      phone: '+212611223344',
      enabled: true
    };

    service.getClient('client-uuid-123').subscribe((client) => {
      expect(client.id).toBe('client-uuid-123');
      expect(client.phone).toBe('+212611223344');
    });

    const req = httpMock.expectOne('http://localhost:8080/api/v1/clients/client-uuid-123');
    expect(req.request.method).toBe('GET');
    req.flush(mockClient);
  });

  it('getClient propagates 404 error when client is not found', () => {
    service.getClient('unknown-id').subscribe({
      next: () => fail('Should have failed with 404'),
      error: (error) => {
        expect(error.status).toBe(404);
      }
    });

    const req = httpMock.expectOne('http://localhost:8080/api/v1/clients/unknown-id');
    req.flush('Not Found', { status: 404, statusText: 'Not Found' });
  });
});

