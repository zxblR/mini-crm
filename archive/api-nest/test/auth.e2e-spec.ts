/* global afterAll, beforeAll, describe, expect, it, jest */
import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request = require('supertest');
import { AuthController } from '../src/auth/auth.controller';
import { AuthService } from '../src/auth/auth.service';

describe('Auth HTTP contract', () => {
  let app: INestApplication;

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      controllers: [AuthController],
      providers: [{ provide: AuthService, useValue: { login: jest.fn().mockResolvedValue({ accessToken: 'token', expiresIn: 900, user: { id: '1' } }) } }],
    }).compile();
    app = moduleRef.createNestApplication();
    app.useGlobalPipes(new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true, transform: true }));
    await app.init();
  });

  afterAll(async () => app.close());

  it('accepts a valid login request', async () => {
    await request(app.getHttpServer())
      .post('/auth/login')
      .send({ account: 'admin@example.com', password: 'password123' })
      .expect(201)
      .expect(({ body }) => expect(body.accessToken).toBe('token'));
  });

  it('rejects unknown fields', async () => {
    await request(app.getHttpServer())
      .post('/auth/login')
      .send({ account: 'admin@example.com', password: 'password123', extra: true })
      .expect(400);
  });
});
